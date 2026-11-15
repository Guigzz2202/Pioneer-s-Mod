package fr.pioneers.society.economy.contract;

import com.mojang.authlib.GameProfile;
import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.economy.mail.Letters;
import fr.pioneers.society.economy.mail.MailService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 7) : cote serveur des contrats : demandes du Carnet (onglet Contrats, action {@link RpActions#CONTRACT_REQUEST}), vue
 * envoyee au joueur ({@link RpActions#CONTRACT_VIEW}, aussi poussee a l'autre partie apres un changement), banque reelle
 * ({@link EconomyApi} : prelevement {@code contract_out}, versement {@code contract_in}), messages dans le chat, facture imprimee.
 * <p>
 * Temps : horloge reelle bornee du serveur ({@link ClanClock#now()}, fuseau {@link ZoneId#systemDefault()} affiche). Toutes les 10 s, et a
 * chaque connexion, les contrats sont rattrapes jusqu'a maintenant ({@link ContractEngine#settle}) : aucun calcul ne depend des ticks.
 * Surete : le client n'envoie que des intentions (numero de contrat, termes d'un brouillon) ; le serveur verifie que le joueur est la bonne
 * partie et l'etat ; aucun montant n'est jamais lu chez le client.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ContractService {

    /** Rattrapage periodique (ticks). */
    public static final int PERIOD = 200;
    /** Contrats envoyes au plus dans une vue (les plus recents), lignes d'historique par contrat dans la vue. */
    public static final int VIEW_MAX = 60, VIEW_LINES = 24;
    private static final DateTimeFormatter CHAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static int clock;

    private ContractService() {
    }

    public static void init() {
        RpNet.onServer(RpActions.CONTRACT_REQUEST, ContractService::request);
        ContractItems.init();
    }

    /** Heure des contrats (ms). */
    public static long now() {
        return ClanClock.now();
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    // ------------------------------------------------------------------ banque reelle
    /** Banque du serveur : comptes du profil (EconomyApi), messages dans le chat. */
    public static ContractEngine.Bank bank(MinecraftServer server) {
        return new ContractEngine.Bank() {
            @Override
            public boolean online(UUID who) {
                return who != null && server.getPlayerList().getPlayer(who) != null;
            }

            @Override
            public long balance(UUID who) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                return p == null ? 0 : EconomyApi.balance(p);
            }

            @Override
            public boolean debit(UUID who, long amount, Contract c) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                return p != null && EconomyApi.debit(p, amount, EconomyApi.Entry.of("contract_out", c.providerName)).ok();
            }

            @Override
            public boolean credit(UUID who, long amount, Contract c) {
                ServerPlayer p = server.getPlayerList().getPlayer(who);
                return p != null && EconomyApi.credit(p, amount, EconomyApi.Entry.of("contract_in", c.clientName)).ok();
            }

            @Override
            public void notify(UUID who, Contract c, String key, String... args) {
                ServerPlayer p = who == null ? null : server.getPlayerList().getPlayer(who);
                if (p != null) {
                    message(p, key, (Object[]) args);
                }
            }

            @Override
            public void letter(UUID who, Contract c, String kind, long a, long b) {
                if (who == null) {
                    return;
                }
                //expediteur : l'autre partie ; une resiliation est signee de celui qui l'a faite
                String from = "contract_terminated".equals(kind) && c.terminatedBy != null
                      ? (c.terminatedBy.equals(c.provider) ? c.providerName : c.clientName)
                      : who.equals(c.provider) ? c.clientName : c.providerName;
                net.minecraft.world.item.ItemStack letter = Letters.make(kind, from, c.id, c.title, c.pay.key(), a, b, now(), zone());
                //1.0.5-beta (lot 16) : la proposition porte la lettre du prestataire (corps, periode, signature)
                if ("contract_proposed".equals(kind)) {
                    Letters.attachBody(letter, c.body, c.start, c.end, c.providerName);
                }
                MailService.deliver(server, who, letter);
            }
        };
    }

    static void message(ServerPlayer p, String key, Object... args) {
        ChatFormatting color = key.startsWith("short") || key.equals("expired") || key.equals("refused") || key.equals("terminated")
                               || key.equals("suspended")
              ? ChatFormatting.RED : ChatFormatting.GOLD;
        p.sendSystemMessage(Component.translatable("contract.pioneers.msg." + key, args).withStyle(color));
    }

    // ------------------------------------------------------------------ temps qui passe
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock % PERIOD != 0 || !EconomyApi.enabled()) {
            return;
        }
        settleAll(event.getServer(), now());
    }

    /** Rattrape tous les contrats jusqu'a {@code now} ; rend le nombre de contrats changes (pousses aux parties connectees). */
    public static int settleAll(MinecraftServer server, long now) {
        ContractData data = ContractData.get(server);
        ContractEngine.Bank bank = bank(server);
        int n = 0;
        for (Contract c : data.all()) {
            names(server, c);
            if (ContractEngine.settle(c, now, bank)) {
                n++;
                data.setDirty();
                push(server, c);
            }
        }
        if (clock % (PERIOD * 360) == 0) {
            data.prune(now);
        }
        return n;
    }

    /** 1.0.4-beta (lot 29) : au demarrage du serveur, les heures dues pendant l'arret sont rattrapees tout de suite (une seule fois chacune). */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (EconomyApi.enabled()) {
            settleAll(event.getServer(), now());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EconomyApi.enabled()) {
            login(player, now());
        }
    }

    /** A la connexion : contrats du joueur rattrapes (prelevements dus, sommes mises de cote versees : un message par contrat), rappel des propositions en attente. */
    public static void login(ServerPlayer player, long now) {
        MinecraftServer server = player.getServer();
        ContractData data = ContractData.get(server);
        ContractEngine.Bank bank = bank(server);
        UUID me = player.getUUID();
        int pending = 0;
        for (Contract c : data.of(me)) {
            names(server, c);
            if (ContractEngine.settle(c, now, bank)) {
                data.setDirty();
                push(server, c);
            }
            if (me.equals(c.client) && c.state == Contract.State.PROPOSED) {
                pending++;
            }
        }
        if (pending > 0) {
            message(player, "pending", Integer.toString(pending));
        }
    }

    /** Noms de pionnier a jour pour les parties connectees. */
    private static void names(MinecraftServer server, Contract c) {
        ServerPlayer p = c.provider == null ? null : server.getPlayerList().getPlayer(c.provider);
        if (p != null) {
            c.providerName = EconomyApi.name(p);
        }
        ServerPlayer q = c.client == null ? null : server.getPlayerList().getPlayer(c.client);
        if (q != null) {
            c.clientName = EconomyApi.name(q);
        }
    }

    // ------------------------------------------------------------------ demandes du Carnet
    private static void request(ServerPlayer player, CompoundTag t) {
        String op = t.getString("op");
        long now = now();
        if ("list".equals(op)) {
            view(player, null, 0);
            return;
        }
        if (!EconomyApi.enabled()) {
            view(player, "disabled", 0);
            return;
        }
        if ("save".equals(op)) {
            //1.0.4-beta (lot 29) : formule (« total » / « hourly ») et montant (total ou taux) ; sans formule : taux horaire du lot 7
            //1.0.5-beta (lot 16) : corps de la lettre (« body ») ; un client d'avant n'envoie que la description (« note »)
            String body = t.contains("body") ? t.getString("body") : t.getString("note");
            String bp = ContractRules.bodyProblem(body);
            if (bp != null) {
                view(player, bp, t.getInt("id"));
                return;
            }
            Saved s = t.contains("pay") ? save(player, t.getInt("id"), t.getString("client"), t.getString("title"), body,
                  t.getIntArray("s"), t.getIntArray("e"), "hourly".equals(t.getString("pay")), t.getLong("amount"), t.getBoolean("propose"), now)
                  : save(player, t.getInt("id"), t.getString("client"), t.getString("title"), body, t.getIntArray("s"),
                  t.getIntArray("e"), t.getLong("rate"), t.getBoolean("propose"), now);
            view(player, s.error() == null ? (t.getBoolean("propose") ? "ok_proposed" : "ok_saved") : s.error(), s.id());
            return;
        }
        int id = t.getInt("id");
        String err = act(player, id, op, now);
        view(player, err == null ? "ok_" + op : err, id);
    }

    /** Resultat d'un enregistrement : numero du contrat (0 si rien), erreur ou {@code null}. */
    public record Saved(int id, @Nullable String error) {
    }

    /**
     * Cree ou modifie un brouillon (prestataire = {@code player}) avec ses termes, et le propose si {@code propose}. Les dates sont lues dans
     * le fuseau du serveur ({@code {annee, mois, jour, heure, minute}}).
     */
    public static Saved save(ServerPlayer player, int id, String clientName, String title, String note, int[] s, int[] e, long rate,
          boolean propose, long now) {
        return save(player, id, clientName, title, note, s, e, null, rate, propose, now);
    }

    /**
     * 1.0.4-beta (lot 29) : brouillon avec sa formule : {@code hourly} vrai = {@code amount} est le taux par heure reelle ; faux = le montant
     * total de la periode.
     */
    public static Saved save(ServerPlayer player, int id, String clientName, String title, String note, int[] s, int[] e, boolean hourly,
          long amount, boolean propose, long now) {
        return save(player, id, clientName, title, note, s, e, hourly ? Contract.Pay.HOURLY : Contract.Pay.TOTAL, amount, propose, now);
    }

    /** {@code pay} null : taux horaire du lot 7 (formule au montant total calcule au taux, {@code amount} = taux). */
    private static Saved save(ServerPlayer player, int id, String clientName, String title, String note, int[] s, int[] e,
          @Nullable Contract.Pay pay, long amount, boolean propose, long now) {
        MinecraftServer server = player.getServer();
        ContractData data = ContractData.get(server);
        Contract c = null;
        if (id > 0) {
            c = data.get(id);
            if (c == null || !player.getUUID().equals(c.provider)) {
                return new Saved(0, "not_allowed");
            }
            if (c.state != Contract.State.DRAFT) {
                return new Saved(id, "state");
            }
        } else if (data.openAsProvider(player.getUUID()) >= ContractRules.OPEN_MAX) {
            return new Saved(0, "too_many");
        }
        Optional<GameProfile> who = resolve(server, ContractRules.clean(clientName, ContractRules.NAME_MAX));
        if (who.isEmpty()) {
            return new Saved(id, "client_unknown");
        }
        if (who.get().getId().equals(player.getUUID())) {
            return new Saved(id, "client_self");
        }
        long start, end;
        try {
            start = epoch(s);
            end = epoch(e);
        } catch (DateTimeException | ArrayIndexOutOfBoundsException ex) {
            return new Saved(id, "date");
        }
        String cleanTitle = ContractRules.clean(title, ContractRules.TITLE_MAX);
        String err = pay == null ? ContractRules.validate(cleanTitle, start, end, amount, Long.MIN_VALUE)
              : ContractRules.validate(cleanTitle, start, end, pay == Contract.Pay.HOURLY, amount, Long.MIN_VALUE);
        if (err != null) {
            return new Saved(id, err);
        }
        if (c == null) {
            c = data.create();
            c.created = now;
            c.provider = player.getUUID();
            c.line(now, "created", 0, 0);
        }
        c.providerName = EconomyApi.name(player);
        c.client = who.get().getId();
        c.clientAccount = who.get().getName();
        ServerPlayer online = server.getPlayerList().getPlayer(c.client);
        c.clientName = online != null ? EconomyApi.name(online) : who.get().getName();
        c.title = cleanTitle;
        //1.0.5-beta (lot 16) : le texte libre est le corps de la lettre (multiligne) ; la description courte en reste le debut (anciens clients)
        c.body = ContractRules.cleanBody(note);
        c.note = ContractRules.clean(c.body.replace('\n', ' '), ContractRules.NOTE_MAX);
        c.start = start;
        c.end = end;
        c.pay = pay == null ? Contract.Pay.TOTAL : pay;
        c.rate = pay == null || pay == Contract.Pay.HOURLY ? amount : 0;
        c.total = pay == Contract.Pay.TOTAL ? amount : 0;
        data.setDirty();
        if (propose) {
            String p = ContractEngine.propose(c, player.getUUID(), now, bank(server));
            if (p != null) {
                return new Saved(c.id, p);
            }
            push(server, c);
        }
        return new Saved(c.id, null);
    }

    /** Action d'une partie sur un contrat : propose, withdraw, delete, accept, refuse, terminate, pay, print. */
    @Nullable
    public static String act(ServerPlayer player, int id, String op, long now) {
        MinecraftServer server = player.getServer();
        ContractData data = ContractData.get(server);
        Contract c = data.get(id);
        UUID me = player.getUUID();
        if (c == null || !c.party(me) || (c.state == Contract.State.DRAFT && !me.equals(c.provider))) {
            return "not_allowed";
        }
        names(server, c);
        ContractEngine.Bank bank = bank(server);
        String err = switch (op) {
            case "propose" -> ContractEngine.propose(c, me, now, bank);
            case "withdraw" -> ContractEngine.withdraw(c, me, now, bank);
            case "accept" -> ContractEngine.accept(c, me, now, bank);
            case "refuse" -> ContractEngine.refuse(c, me, now, bank);
            case "terminate" -> ContractEngine.terminate(c, me, now, bank);
            case "pay" -> ContractEngine.payNow(c, me, now, bank);
            case "resume" -> ContractEngine.resume(c, me, now, bank);
            case "delete" -> {
                if (!me.equals(c.provider) || c.state != Contract.State.DRAFT) {
                    yield "state";
                }
                data.remove(id);
                yield null;
            }
            case "print" -> print(player, c, now);
            default -> "not_allowed";
        };
        if (err == null && !"delete".equals(op) && !"print".equals(op)) {
            data.setDirty();
            push(server, c);
        }
        return err;
    }

    /** Facture imprimee (contrat en cours ou clos) : une feuille de papier de l'inventaire (sauf en creatif) -> l'objet facture, instantane. */
    @Nullable
    private static String print(ServerPlayer player, Contract c, long now) {
        if (!c.billing()) {
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
        ItemStack invoice = InvoiceItem.make(c, now, zone());
        if (!player.getInventory().add(invoice)) {
            player.drop(invoice, false);
        }
        return null;
    }

    /** Joueur connu du serveur (connecte, ou deja venu : cache des profils), par son nom de compte. */
    public static Optional<GameProfile> resolve(MinecraftServer server, String name) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) {
            return Optional.of(p.getGameProfile());
        }
        //nom de pionnier d'un joueur connecte
        for (ServerPlayer q : server.getPlayerList().getPlayers()) {
            if (EconomyApi.name(q).equalsIgnoreCase(name)) {
                return Optional.of(q.getGameProfile());
            }
        }
        Optional<GameProfile> cached = server.getProfileCache() == null ? Optional.empty() : server.getProfileCache().get(name);
        if (cached.isPresent()) {
            return cached;
        }
        //1.0.5-beta (lot 16) : joueur deja venu, connu du registre du selecteur (cache des profils vide en hors ligne, par exemple)
        fr.pioneers.core.profile.KnownPlayers.Known k = fr.pioneers.core.profile.KnownPlayers.get(server).byAccount(name);
        return k == null ? Optional.empty() : Optional.of(new GameProfile(k.id(), k.account()));
    }

    /** {annee, mois, jour, heure, minute} dans le fuseau du serveur -> ms. */
    public static long epoch(int[] f) {
        return LocalDateTime.of(f[0], f[1], f[2], f[3], f[4]).atZone(zone()).toInstant().toEpochMilli();
    }

    /** Date lisible dans le chat (fuseau du serveur, format neutre). */
    public static String chatDate(long ms) {
        return CHAT.format(Instant.ofEpochMilli(ms).atZone(zone()));
    }

    // ------------------------------------------------------------------ vues
    /** Vue des contrats d'un joueur (resultat de sa derniere demande, contrat a montrer). */
    public static void view(ServerPlayer player, @Nullable String msg, int focus) {
        RpNet.send(player, RpActions.CONTRACT_VIEW, viewTag(player, msg, focus, now()));
    }

    public static CompoundTag viewTag(ServerPlayer player, @Nullable String msg, int focus, long now) {
        CompoundTag t = new CompoundTag();
        t.putLong("now", now);
        ZoneId z = zone();
        t.putString("zone", z.getId());
        ZoneOffset off = z.getRules().getOffset(Instant.ofEpochMilli(now));
        t.putString("offset", off.getId().equals("Z") ? "+00:00" : off.getId());
        t.putUUID("me", player.getUUID());
        t.putBoolean("enabled", EconomyApi.enabled());
        t.putLong("balance", EconomyApi.balance(player));
        if (msg != null) {
            t.putString("msg", msg);
        }
        t.putInt("focus", focus);
        ListTag l = new ListTag();
        List<Contract> mine = ContractData.get(player.getServer()).of(player.getUUID());
        for (int i = 0; i < mine.size() && i < VIEW_MAX; i++) {
            l.add(card(mine.get(i), now));
        }
        t.put("list", l);
        return t;
    }

    /** Contrat tel que le Carnet l'affiche : termes, etat, chiffres calcules par le serveur, dernieres lignes d'historique. */
    public static CompoundTag card(Contract c, long now) {
        CompoundTag t = c.save();
        ListTag h = t.getList("hist", 10);
        while (h.size() > VIEW_LINES) {
            h.remove(0);
        }
        t.putString("shown", c.shownState());
        t.putLong("planned", c.plannedTotal());
        t.putLong("plannedMin", c.plannedMinutes());
        t.putLong("owed", c.owed());
        t.putLong("eff", c.effStart());
        t.putLong("stop", c.stopAt());
        //a ce jour, au prorata a la minute (ce qui sera facture a la prochaine heure entiere)
        long until = Math.min(now, c.stopAt());
        long accrued = 0;
        if (c.billing()) {
            accrued = c.hourly() ? c.billed + (c.state == Contract.State.RUNNING
                  ? ContractRules.amount(ContractRules.minutes(c.clockStart() + c.hours * ContractRules.HOUR, until), c.rate) : 0)
                  : c.dueAt(ContractRules.minutes(c.effStart(), until));
        }
        t.putLong("accrued", accrued);
        //1.0.4-beta (lot 29) : formule, prochaine heure payee et reserve visee (contrat a l'heure)
        t.putBoolean("hourly", c.hourly());
        t.putLong("nextHour", c.hourly() && c.state == Contract.State.RUNNING ? c.nextHourAt() : 0);
        t.putLong("reserveTarget", c.hourly() ? ContractRules.reserveTarget(c.rate, ContractRules.minutes(Math.max(now, c.effStart()), c.end)) : 0);
        return t;
    }

    /** Pousse une vue fraiche aux parties connectees du contrat (leur Carnet se met a jour s'il est ouvert). */
    public static void push(MinecraftServer server, Contract c) {
        List<UUID> to = new ArrayList<>();
        if (c.provider != null) {
            to.add(c.provider);
        }
        if (c.client != null && c.state != Contract.State.DRAFT) {
            to.add(c.client);
        }
        for (UUID u : to) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                CompoundTag t = viewTag(p, null, 0, now());
                t.putBoolean("push", true);
                RpNet.send(p, RpActions.CONTRACT_VIEW, t);
            }
        }
    }
}
