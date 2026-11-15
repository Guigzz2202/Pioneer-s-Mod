package fr.pioneers.economy.document;

import com.mojang.authlib.GameProfile;
import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.contract.ContractItems;
import fr.pioneers.economy.contract.ContractService;
import fr.pioneers.economy.mail.Letters;
import fr.pioneers.economy.mail.MailService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 26, retour 14 de Guillaume) : cote serveur des DOCUMENTS du Carnet (onglet Documents, action
 * {@link RpActions#DOCUMENT_REQUEST}) : brouillons, envoi, reponse, reglement des echeances, impression, vue ({@link RpActions#DOCUMENT_VIEW},
 * aussi poussee aux parties connectees apres un changement), banque reelle ({@link EconomyApi} : {@code document_out} / {@code document_in},
 * tresor du clan), messages dans le chat, lettres dans la boite aux lettres.
 * <p>
 * Surete : le client n'envoie que des intentions et les termes d'un brouillon ; le serveur verifie TOUT ({@link DocumentRules#validate} :
 * montants >= 0, total = somme des echeances, jours, longueurs), qui agit (auteur ; destinataire joueur ; pour un clan, un membre qui a le droit
 * de retrait du tresor) et l'etat. Un document ne s'adresse ni a soi-meme ni a son propre clan (le tresor ne se paierait pas lui-meme a
 * l'auteur). Temps : jours de jeu ({@link PioneerApi#day}) ; les echeances sont rattrapees toutes les 10 s, a la connexion et a chaque vue.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class DocumentService {

    public static final int PERIOD = 200;
    /** Documents envoyes au plus dans une vue (les plus recents). */
    public static final int VIEW_MAX = 80;
    private static int clock;

    private DocumentService() {
    }

    public static void init() {
        RpNet.onServer(RpActions.DOCUMENT_REQUEST, DocumentService::request);
    }

    public static long today(MinecraftServer server) {
        return PioneerApi.day(server);
    }

    // ------------------------------------------------------------------ banque reelle
    @Nullable
    static Clan clan(MinecraftServer server, @Nullable UUID id) {
        return id == null ? null : ClanData.get(server).clan(id);
    }

    /** Membres connectes d'un clan qui ont le droit de retrait du tresor (ils signent et paient pour lui). */
    static List<ServerPlayer> officers(MinecraftServer server, Clan c) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && ClanRoles.has(c, u, Permission.WITHDRAW)) {
                out.add(p);
            }
        }
        return out;
    }

    public static DocumentEngine.Bank bank(MinecraftServer server) {
        return new DocumentEngine.Bank() {
            @Override
            public boolean canPay(Document.Party payer, long amount) {
                if (payer.clan()) {
                    Clan c = clan(server, payer.id());
                    return c != null && !c.frozen && c.treasury >= amount;
                }
                ServerPlayer p = payer.id() == null ? null : server.getPlayerList().getPlayer(payer.id());
                return p != null && EconomyApi.balance(p) >= amount;
            }

            @Override
            public boolean transfer(Document.Party payer, Document.Party payee, long amount, Document d) {
                String ref = "#document.pioneers.reason";
                if (payer.clan()) {
                    Clan c = clan(server, payer.id());
                    if (c == null || !ClanTreasury.debit(server, c, amount, ref, payee.name())) {
                        return false;
                    }
                } else {
                    ServerPlayer p = payer.id() == null ? null : server.getPlayerList().getPlayer(payer.id());
                    if (p == null || !EconomyApi.debit(p, amount, EconomyApi.Entry.of("document_out", payee.name())).ok()) {
                        return false;
                    }
                }
                if (payee.clan()) {
                    Clan c = clan(server, payee.id());
                    if (c != null && ClanTreasury.credit(server, c, amount, ref, payer.name())) {
                        return true;
                    }
                    //clan dissous ou tresor plein : la somme revient au payeur (rien ne se perd)
                    refund(payer, amount);
                    return false;
                }
                if (payee.id() != null) {
                    EconomyApi.creditLater(server, payee.id(), amount, EconomyApi.Entry.of("document_in", payer.name()));
                    return true;
                }
                refund(payer, amount);
                return false;
            }

            private void refund(Document.Party payer, long amount) {
                if (payer.clan()) {
                    Clan c = clan(server, payer.id());
                    if (c != null) {
                        ClanTreasury.credit(server, c, amount, "#document.pioneers.reason", "");
                    }
                } else if (payer.id() != null) {
                    EconomyApi.creditLater(server, payer.id(), amount, EconomyApi.Entry.of("document_in", ""));
                }
            }

            @Override
            public void notify(Document.Party who, Document d, String key, String... args) {
                for (ServerPlayer p : players(server, who)) {
                    message(p, key, (Object[]) args);
                }
            }

            @Override
            public void letter(Document.Party who, Document d, String kind, long amount) {
                for (UUID u : recipients(server, who)) {
                    String from = who.equals(d.author) ? d.recipient.name() : d.author.name();
                    ItemStack letter = Letters.make(kind, from, d.id, d.subject, d.type.key(), amount, 0, ContractService.now(), ContractService.zone());
                    if ("document_letter".equals(kind)) {
                        Letters.attachBody(letter, d.body, 0, 0, d.author.name());
                    }
                    MailService.deliver(server, u, letter);
                }
            }
        };
    }

    /** Joueurs connectes qui parlent pour une partie (le joueur ; pour un clan, ses membres au droit de retrait). */
    static List<ServerPlayer> players(MinecraftServer server, Document.Party who) {
        if (who.clan()) {
            Clan c = clan(server, who.id());
            return c == null ? List.of() : officers(server, c);
        }
        ServerPlayer p = who.id() == null ? null : server.getPlayerList().getPlayer(who.id());
        return p == null ? List.of() : List.of(p);
    }

    /** Comptes qui recoivent les lettres d'une partie (le joueur ; pour un clan, ses membres au droit de retrait, 8 au plus). */
    static List<UUID> recipients(MinecraftServer server, Document.Party who) {
        if (!who.clan()) {
            return who.id() == null ? List.of() : List.of(who.id());
        }
        Clan c = clan(server, who.id());
        List<UUID> out = new ArrayList<>();
        if (c != null) {
            for (UUID u : c.members.keySet()) {
                if (out.size() < 8 && ClanRoles.has(c, u, Permission.WITHDRAW)) {
                    out.add(u);
                }
            }
        }
        return out;
    }

    static void message(ServerPlayer p, String key, Object... args) {
        boolean bad = key.equals("overdue_payee") || key.equals("reminder") || key.equals("refused") || key.equals("cancelled");
        p.sendSystemMessage(Component.translatable("document.pioneers.msg." + key, args).withStyle(bad ? ChatFormatting.RED : ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ temps qui passe
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % PERIOD != 0 || !EconomyApi.enabled()) {
            return;
        }
        settleAll(event.getServer());
    }

    /** Rattrape tous les documents au jour de jeu ; rend le nombre de documents changes (pousses aux parties connectees). */
    public static int settleAll(MinecraftServer server) {
        DocumentData data = DocumentData.get(server);
        long today = today(server);
        DocumentEngine.Bank bank = bank(server);
        int n = 0;
        for (Document d : data.all()) {
            if (DocumentEngine.settle(d, today, bank)) {
                n++;
                data.setDirty();
                push(server, d);
            }
        }
        if (clock % (PERIOD * 360) == 0) {
            data.prune(today);
        }
        return n;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EconomyApi.enabled()) {
            MinecraftServer server = player.getServer();
            DocumentData data = DocumentData.get(server);
            long today = today(server);
            int pending = 0;
            for (Document d : data.visible(x -> sees(server, player, x))) {
                if (DocumentEngine.settle(d, today, bank(server))) {
                    data.setDirty();
                    push(server, d);
                }
                if (d.status == DocStatus.SENT && d.type.signed && d.timed == 0 && side(server, player, d) == DocumentEngine.Side.RECIPIENT) {
                    pending++;
                }
            }
            if (pending > 0) {
                message(player, "pending", Integer.toString(pending));
            }
        }
    }

    // ------------------------------------------------------------------ droits
    /** Le joueur voit le document : auteur, destinataire, ou membre du clan destinataire. Un brouillon : l'auteur seul. */
    public static boolean sees(MinecraftServer server, ServerPlayer p, Document d) {
        UUID me = p.getUUID();
        if (d.author.is(me)) {
            return true;
        }
        if (d.status == DocStatus.DRAFT) {
            return false;
        }
        if (d.recipient.clan()) {
            Clan c = clan(server, d.recipient.id());
            return c != null && c.member(me) != null;
        }
        return d.recipient.is(me);
    }

    /** Cote dont le joueur peut agir, ou {@code null} (simple lecteur). */
    @Nullable
    public static DocumentEngine.Side side(MinecraftServer server, ServerPlayer p, Document d) {
        UUID me = p.getUUID();
        if (d.author.is(me)) {
            return DocumentEngine.Side.AUTHOR;
        }
        if (d.status == DocStatus.DRAFT) {
            return null;
        }
        if (d.recipient.clan()) {
            Clan c = clan(server, d.recipient.id());
            return c != null && ClanRoles.has(c, me, Permission.WITHDRAW) ? DocumentEngine.Side.RECIPIENT : null;
        }
        return d.recipient.is(me) ? DocumentEngine.Side.RECIPIENT : null;
    }

    // ------------------------------------------------------------------ demandes du Carnet
    private static void request(ServerPlayer player, CompoundTag t) {
        String op = t.getString("op");
        if ("list".equals(op)) {
            view(player, null, 0, false);
            return;
        }
        int id = t.getInt("id");
        if ("open".equals(op)) {
            view(player, null, id, true);
            return;
        }
        if (!EconomyApi.enabled()) {
            view(player, "disabled", id, false);
            return;
        }
        if ("save".equals(op)) {
            Saved s = save(player, t);
            view(player, s.error() == null ? (t.getBoolean("send") ? "ok_send" : "ok_save") : s.error(), s.id(), s.id() > 0);
            return;
        }
        String err = act(player, id, op);
        view(player, err == null ? "ok_" + op : err, id, err == null && !"delete".equals(op));
    }

    /** Resultat d'un enregistrement : numero du document (0 si rien), erreur ou {@code null}. */
    public record Saved(int id, @Nullable String error) {
    }

    /** Lignes de montants lues dans la demande (bornees : au plus une de plus que permis, pour refuser au lieu de couper). */
    static List<Document.Line> lines(CompoundTag t) {
        List<Document.Line> out = new ArrayList<>();
        for (Tag x : t.getList("lines", Tag.TAG_COMPOUND)) {
            if (out.size() > DocumentRules.LINES_MAX) {
                break;
            }
            CompoundTag c = (CompoundTag) x;
            String raw = c.getString("l");
            String label = DocumentRules.clean(raw, DocumentRules.LABEL_MAX + 1);
            out.add(new Document.Line(label, c.getLong("a")));
        }
        return out;
    }

    static List<Document.Due> dues(CompoundTag t) {
        List<Document.Due> out = new ArrayList<>();
        for (Tag x : t.getList("dues", Tag.TAG_COMPOUND)) {
            if (out.size() > DocumentRules.DUES_MAX) {
                break;
            }
            CompoundTag c = (CompoundTag) x;
            out.add(new Document.Due(c.getLong("a"), c.getLong("d")));
        }
        return DocumentRules.sorted(out);
    }

    /** Cree ou modifie un brouillon (auteur = {@code player}) avec ses termes, et l'envoie si {@code send}. */
    public static Saved save(ServerPlayer player, CompoundTag t) {
        MinecraftServer server = player.getServer();
        DocumentData data = DocumentData.get(server);
        long today = today(server);
        int id = t.getInt("id");
        String typeKey = t.getString("type");
        if (!DocType.known(typeKey)) {
            return new Saved(id, "type");
        }
        DocType type = DocType.of(typeKey);
        Document d = null;
        if (id > 0) {
            d = data.get(id);
            if (d == null || !d.author.is(player.getUUID()) || d.timed > 0) {
                return new Saved(0, "not_allowed");
            }
            if (d.status != DocStatus.DRAFT) {
                return new Saved(id, "state");
            }
        } else if (data.openAsAuthor(player.getUUID()) >= DocumentRules.OPEN_MAX) {
            return new Saved(0, "too_many");
        }
        //destinataire : un joueur deja venu, ou un clan (jamais soi-meme ni son propre clan)
        String toName = DocumentRules.clean(t.getString("to"), DocumentRules.NAME_MAX);
        Document.Party to;
        if ("clan".equals(t.getString("toKind"))) {
            Clan c = toName.isEmpty() ? null : ClanData.get(server).byName(toName);
            if (c == null) {
                return new Saved(id, "clan_unknown");
            }
            if (c.member(player.getUUID()) != null) {
                return new Saved(id, "own_clan");
            }
            to = Document.Party.clan(c.id, c.name);
        } else {
            Optional<GameProfile> who = ContractService.resolve(server, toName);
            if (who.isEmpty()) {
                return new Saved(id, "to_unknown");
            }
            if (who.get().getId().equals(player.getUUID())) {
                return new Saved(id, "to_self");
            }
            ServerPlayer online = server.getPlayerList().getPlayer(who.get().getId());
            to = Document.Party.player(who.get().getId(), online != null ? EconomyApi.name(online) : who.get().getName());
        }
        String raw = t.getString("subject"), body = t.getString("body");
        String subject = DocumentRules.clean(raw, DocumentRules.SUBJECT_MAX + 1);
        List<Document.Line> lines = lines(t);
        List<Document.Due> dues = dues(t);
        String err = DocumentRules.validate(type, subject, body, lines, dues, today, false);
        if (err != null) {
            return new Saved(id, err);
        }
        if (d == null) {
            d = data.create();
            d.created = today;
            d.event(today, "created", 0, EconomyApi.name(player));
        }
        d.type = type;
        d.author = Document.Party.player(player.getUUID(), EconomyApi.name(player));
        d.recipient = to;
        d.recipientAccount = toName;
        d.subject = subject;
        d.body = DocumentRules.cleanBody(body);
        d.lines.clear();
        d.lines.addAll(lines);
        d.dues.clear();
        d.dues.addAll(dues);
        data.setDirty();
        if (t.getBoolean("send")) {
            String e = DocumentEngine.send(d, DocumentEngine.Side.AUTHOR, EconomyApi.name(player), today, bank(server));
            if (e != null) {
                return new Saved(d.id, e);
            }
            push(server, d);
        }
        return new Saved(d.id, null);
    }

    /** Action sur un document : send, withdraw, accept, refuse, cancel, pay, delete, print. */
    @Nullable
    public static String act(ServerPlayer player, int id, String op) {
        MinecraftServer server = player.getServer();
        DocumentData data = DocumentData.get(server);
        Document d = data.get(id);
        if (d == null || !sees(server, player, d)) {
            return "not_allowed";
        }
        if (d.timed > 0 && !"print".equals(op)) {
            return "timed";
        }
        long today = today(server);
        DocumentEngine.Bank bank = bank(server);
        DocumentEngine.Side side = side(server, player, d);
        String who = EconomyApi.name(player);
        if ("print".equals(op)) {
            return print(player, d, today);
        }
        if (side == null) {
            return "not_allowed";
        }
        String err = switch (op) {
            case "send" -> DocumentEngine.send(d, side, who, today, bank);
            case "withdraw" -> DocumentEngine.withdraw(d, side, today, bank);
            case "accept" -> DocumentEngine.accept(d, side, who, today, bank);
            case "refuse" -> DocumentEngine.refuse(d, side, who, today, bank);
            case "cancel" -> DocumentEngine.cancel(d, side, today, bank);
            case "pay" -> DocumentEngine.pay(d, side, today, bank);
            case "delete" -> {
                if (side != DocumentEngine.Side.AUTHOR || d.status != DocStatus.DRAFT) {
                    yield "state";
                }
                data.remove(id);
                yield null;
            }
            default -> "not_allowed";
        };
        if (err == null && !"delete".equals(op)) {
            data.setDirty();
            push(server, d);
        }
        return err;
    }

    /** Document imprime : une feuille de papier de l'inventaire (sauf en creatif) -> l'objet document (instantane). */
    @Nullable
    private static String print(ServerPlayer player, Document d, long today) {
        if (d.status == DocStatus.DRAFT) {
            return "state";
        }
        if (!player.getAbilities().instabuild) {
            int slot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.PAPER)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                return "paper";
            }
            player.getInventory().getItem(slot).shrink(1);
        }
        ItemStack paper = printed(d, today);
        if (!player.getInventory().add(paper)) {
            player.drop(paper, false);
        }
        return null;
    }

    /** Objet document imprime (l'objet des factures d'avant, {@code pioneers:contract_invoice}, NBT {@code document}). */
    public static ItemStack printed(Document d, long today) {
        ItemStack s = new ItemStack(ContractItems.INVOICE.get());
        CompoundTag t = d.save();
        t.putLong("printed", today);
        t.putLong("printedMs", ContractService.now());
        t.putString("zone", ContractService.zone().getId());
        s.getOrCreateTag().put(fr.pioneers.economy.contract.InvoiceItem.DOCUMENT, t);
        return s;
    }

    // ------------------------------------------------------------------ vues
    public static void view(ServerPlayer player, @Nullable String msg, int focus, boolean full) {
        RpNet.send(player, RpActions.DOCUMENT_VIEW, viewTag(player, msg, focus, full));
    }

    public static CompoundTag viewTag(ServerPlayer player, @Nullable String msg, int focus, boolean full) {
        MinecraftServer server = player.getServer();
        DocumentData data = DocumentData.get(server);
        long today = today(server);
        CompoundTag t = new CompoundTag();
        t.putLong("today", today);
        t.putUUID("me", player.getUUID());
        t.putString("myName", EconomyApi.name(player));
        t.putBoolean("enabled", EconomyApi.enabled());
        t.putLong("balance", EconomyApi.balance(player));
        if (msg != null) {
            t.putString("msg", msg);
        }
        t.putInt("focus", focus);
        ListTag l = new ListTag();
        List<Document> mine = data.visible(d -> sees(server, player, d));
        for (int i = 0; i < mine.size() && i < VIEW_MAX; i++) {
            l.add(summary(server, player, mine.get(i)));
        }
        t.put("list", l);
        if (full && focus > 0) {
            Document d = data.get(focus);
            if (d != null && sees(server, player, d)) {
                CompoundTag c = d.save();
                c.put("sum", summary(server, player, d));
                t.put("doc", c);
            }
        }
        //clans connus (choix du destinataire) : noms, 64 au plus
        ListTag clans = new ListTag();
        Set<String> names = new LinkedHashSet<>();
        Clan own = ClanData.get(server).clanOf(player.getUUID());
        for (Clan c : ClanData.get(server).clans()) {
            if (c != own && names.size() < 64) {
                names.add(c.name);
            }
        }
        for (String n : names) {
            CompoundTag c = new CompoundTag();
            c.putString("n", n);
            clans.add(c);
        }
        t.put("clans", clans);
        return t;
    }

    /** Ligne de la liste : termes et chiffres calcules par le serveur, cote du joueur. */
    public static CompoundTag summary(MinecraftServer server, ServerPlayer player, Document d) {
        CompoundTag t = new CompoundTag();
        t.putInt("id", d.id);
        t.putString("type", d.type.key());
        t.putString("status", d.status.key());
        t.putInt("timed", d.timed);
        t.putString("au", d.author.name());
        t.putString("to", d.recipient.name());
        t.putString("tk", d.recipient.kind().key());
        t.putString("sub", d.subject);
        t.putLong("total", d.total());
        t.putLong("paid", d.paid());
        t.putLong("owed", d.owed());
        Document.Due next = d.nextDue();
        t.putLong("nd", next == null ? -1 : next.day);
        t.putLong("na", next == null ? 0 : next.amount);
        t.putLong("sent", d.sent);
        t.putInt("dues", d.dues.size());
        DocumentEngine.Side side = side(server, player, d);
        t.putString("side", side == null ? "" : side == DocumentEngine.Side.AUTHOR ? "a" : "r");
        t.putBoolean("mine", d.author.is(player.getUUID()));
        boolean payerSide = d.type.payable() && side != null && (d.type.authorPays() ? side == DocumentEngine.Side.AUTHOR : side == DocumentEngine.Side.RECIPIENT);
        t.putBoolean("payer", payerSide);
        return t;
    }

    /** Pousse une vue fraiche aux parties connectees (leur Carnet se met a jour s'il est ouvert). */
    public static void push(MinecraftServer server, Document d) {
        Set<ServerPlayer> to = new LinkedHashSet<>(players(server, d.author));
        if (d.status != DocStatus.DRAFT) {
            if (d.recipient.clan()) {
                Clan c = clan(server, d.recipient.id());
                if (c != null) {
                    for (UUID u : c.members.keySet()) {
                        ServerPlayer p = server.getPlayerList().getPlayer(u);
                        if (p != null) {
                            to.add(p);
                        }
                    }
                }
            } else {
                to.addAll(players(server, d.recipient));
            }
        }
        for (ServerPlayer p : to) {
            CompoundTag t = viewTag(p, null, 0, false);
            t.putBoolean("push", true);
            RpNet.send(p, RpActions.DOCUMENT_VIEW, t);
        }
    }
}
