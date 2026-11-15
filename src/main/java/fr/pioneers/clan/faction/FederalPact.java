package fr.pioneers.clan.faction;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanCore;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanNames;
import fr.pioneers.clan.vote.ClanGov;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9 (MAJ RP, bloc 4) : logique serveur du Pacte federal (meme systeme que la Charte de clan). Vierge : le Representant d'un clan de niveau
 * 2 au moins, libre de toute faction, le redige (nom, etiquette de 3 a 5 lettres, couleur, devise, regime federal). Ouvert : d'autres clans
 * le signent, chacun selon son propre regime (ligne « traite » : le Representant en Empire, un vote ailleurs) ; le brouillon vit dans
 * {@link FactionSaved} (une signature votee arrive meme quand le Pacte est range). Le fondateur le scelle des qu'un clan au moins a signe :
 * 3 000 jetons preleves au tresor du clan fondateur, le Pacte est consomme et la faction nait. Le client n'envoie que des intentions.
 */
public final class FederalPact {

    public static final String KEY = "pact";

    private FederalPact() {
    }

    // ------------------------------------------------------------------ brouillon
    @Nullable
    public static CompoundTag draft(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(KEY, Tag.TAG_COMPOUND) && t.getCompound(KEY).hasUUID("id") ? t.getCompound(KEY) : null;
    }

    public static boolean isOpen(ItemStack stack) {
        return draft(stack) != null;
    }

    private static void clear(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t != null) {
            t.remove(KEY);
            if (t.isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    /** Brouillon du serveur pour ce Pacte (null : expire, scelle ou annule). */
    @Nullable
    static CompoundTag record(MinecraftServer server, ItemStack stack) {
        CompoundTag d = draft(stack);
        return d == null ? null : FactionSaved.get(server).pact(d.getUUID("id"));
    }

    static List<UUID> signers(CompoundTag rec) {
        List<UUID> out = new ArrayList<>();
        for (Tag t : rec.getList("signers", Tag.TAG_COMPOUND)) {
            out.add(((CompoundTag) t).getUUID("clan"));
        }
        return out;
    }

    // ------------------------------------------------------------------ utilisation
    /** Clic droit avec le Pacte : ecran de redaction (vierge), ou etat et actions (ouvert). */
    public static void use(ServerPlayer p, InteractionHand hand) {
        if (!Factions.enabled()) {
            ClanMessages.refuse(p, ClanDefs.get().ready() ? "clan.pioneers.error.disabled" : "clan.pioneers.error.defs");
            return;
        }
        ItemStack stack = p.getItemInHand(hand);
        if (draft(stack) != null && record(p.getServer(), stack) == null) {
            clear(stack);
            ClanMessages.refuse(p, "clan.pioneers.pact.expired");
        }
        view(p, hand, stack);
    }

    /** Etat envoye a l'ecran du Pacte ({@code FACTION_VIEW}, sorte « pact »). */
    public static void view(ServerPlayer p, InteractionHand hand, ItemStack stack) {
        MinecraftServer server = p.getServer();
        FactionRules.Values v = FactionRules.get();
        ClanDefs.Rules r = ClanCore.rules();
        Clan mine = ClanData.get(server).clanOf(p.getUUID());
        CompoundTag t = new CompoundTag();
        t.putString("kind", "pact");
        t.putInt("hand", hand.ordinal());
        CompoundTag rec = record(server, stack);
        if (rec != null) {
            CompoundTag d = rec.copy();
            ListTag names = new ListTag();
            for (UUID id : signers(rec)) {
                Clan c = ClanData.get(server).clan(id);
                CompoundTag n = new CompoundTag();
                n.putString("n", c == null ? "?" : c.name);
                names.add(n);
            }
            d.put("signerNames", names);
            d.remove("signers");
            //jamais d'identifiant de compte chez le client
            d.remove("by");
            t.put("draft", d);
            t.putLong("left", Math.max(0, v.pactExpireMs() - (ClanClock.now() - rec.getLong("opened"))));
            boolean founder = mine != null && mine.id.equals(rec.getUUID("founderClan")) && p.getUUID().equals(mine.representative);
            t.putBoolean("founder", founder);
            t.putBoolean("canSeal", founder && !signers(rec).isEmpty());
            if (!founder && mine != null) {
                String err = checkSign(server, mine, p, signData(rec));
                if (err != null) {
                    t.putString("blocked", err);
                }
            }
            //le Pacte en main garde un resume pour l'infobulle
            CompoundTag show = stack.getOrCreateTag().getCompound(KEY);
            show.putInt("signed", signers(rec).size());
            stack.getOrCreateTag().put(KEY, show);
        } else {
            String err = canFound(server, p, mine);
            if (err != null) {
                t.putString("blocked", err);
            }
        }
        t.putLong("cost", EconomyApi.enabled() ? v.pactCost() : 0);
        t.putLong("treasury", mine == null ? 0 : mine.treasury);
        t.putInt("nameMin", r == null ? 3 : r.nameMin());
        t.putInt("nameMax", r == null ? 24 : r.nameMax());
        t.putInt("tagMin", v.tagMin());
        t.putInt("tagMax", v.tagMax());
        t.putInt("mottoMax", r == null ? 60 : r.mottoMax());
        t.putInt("minLevel", v.pactMinClanLevel());
        t.putInt("maxClans", v.maxClans());
        t.putLong("expireDays", Math.round(v.pactExpireMs() / (double) FactionRules.DAY));
        ListTag regimes = new ListTag();
        for (String id : ClanDefs.REGIMES) {
            ClanDefs.Constitution k = ClanDefs.get().regime(id);
            if (k == null) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putString("id", id);
            c.putInt("contribution", k.federalContributionMax());
            regimes.add(c);
        }
        t.put("regimes", regimes);
        RpNet.send(p, RpActions.FACTION_VIEW, t);
    }

    /** Le joueur peut-il ouvrir un Pacte (Representant d'un clan de niveau suffisant, libre de toute faction) ? Cle du refus ou null. */
    @Nullable
    static String canFound(MinecraftServer server, ServerPlayer p, @Nullable Clan c) {
        FactionRules.Values v = FactionRules.get();
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (!p.getUUID().equals(c.representative)) {
            return "clan.pioneers.error.representative_only";
        }
        if (c.level < v.pactMinClanLevel()) {
            return "clan.pioneers.pact.error.level";
        }
        String err = FactionRules.canEnter(v, c, null, ClanClock.now());
        if (err != null) {
            return err;
        }
        if (EconomyApi.enabled() && (c.frozen || c.treasury < v.pactCost())) {
            return "clan.pioneers.pact.error.cost";
        }
        return null;
    }

    /** Intention du client ({@code FACTION_ACTION}, op pact_open / pact_cancel / pact_sign / pact_seal). */
    public static void edit(ServerPlayer p, CompoundTag t) {
        if (!Factions.enabled()) {
            ClanMessages.refuse(p, "clan.pioneers.error.disabled");
            return;
        }
        InteractionHand hand = t.getInt("hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = p.getItemInHand(hand);
        if (!stack.is(FactionItems.PACT.get())) {
            return;
        }
        String err = switch (t.getString("op")) {
            case "pact_open" -> open(p, stack, t);
            case "pact_cancel" -> cancel(p, stack);
            case "pact_sign" -> sign(p, stack);
            case "pact_seal" -> seal(p, stack);
            default -> null;
        };
        if (err != null) {
            ClanMessages.refuse(p, err);
            CompoundTag e = new CompoundTag();
            e.putString("kind", "pact");
            e.putString("error", err);
            RpNet.send(p, RpActions.FACTION_VIEW, e);
            return;
        }
        if (stack.isEmpty() || "pact_cancel".equals(t.getString("op"))) {
            close(p);
        } else {
            view(p, hand, stack);
        }
    }

    private static void close(ServerPlayer p) {
        CompoundTag c = new CompoundTag();
        c.putString("kind", "pact");
        c.putBoolean("close", true);
        RpNet.send(p, RpActions.FACTION_VIEW, c);
    }

    /** Verifie l'identite d'une faction : cle du refus ou null. */
    @Nullable
    static String checkIdentity(MinecraftServer server, String name, String tag, int color, String motto, String regime, @Nullable UUID except) {
        ClanDefs.Rules r = ClanCore.rules();
        FactionRules.Values v = FactionRules.get();
        if (r == null) {
            return "clan.pioneers.error.defs";
        }
        String err = ClanNames.checkName(name, r);
        if (err != null) {
            return err;
        }
        int n = tag.codePointCount(0, tag.length());
        if (n < v.tagMin() || n > v.tagMax() || !tag.codePoints().allMatch(Character::isLetter)) {
            return "clan.pioneers.pact.error.tag";
        }
        if (ClanNames.banned(tag, r)) {
            return "clan.pioneers.error.name_banned";
        }
        err = ClanNames.checkColor(color);
        if (err == null) {
            err = ClanNames.checkMotto(motto, r);
        }
        if (err != null) {
            return err;
        }
        if (!ClanDefs.REGIMES.contains(regime) || ClanDefs.get().regime(regime) == null) {
            return "clan.pioneers.error.regime";
        }
        for (Faction f : ClanData.get(server).factions()) {
            if (f.name.equalsIgnoreCase(name) || f.tag.equalsIgnoreCase(tag)) {
                return "clan.pioneers.pact.error.name_taken";
            }
        }
        for (CompoundTag pt : FactionSaved.get(server).pacts.values()) {
            if (!pt.getUUID("id").equals(except) && (pt.getString("name").equalsIgnoreCase(name) || pt.getString("tag").equalsIgnoreCase(tag))) {
                return "clan.pioneers.pact.error.name_taken";
            }
        }
        return null;
    }

    @Nullable
    private static String open(ServerPlayer p, ItemStack stack, CompoundTag t) {
        MinecraftServer server = p.getServer();
        if (draft(stack) != null) {
            return null;
        }
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        String err = canFound(server, p, c);
        if (err != null) {
            return err;
        }
        String name = ClanNames.cleanName(t.getString("name")), tag = ClanNames.cleanTag(t.getString("tag")),
              motto = ClanNames.cleanMotto(t.getString("motto")), regime = t.getString("regime");
        int color = t.getInt("color");
        err = checkIdentity(server, name, tag, color, motto, regime, null);
        if (err != null) {
            return err;
        }
        UUID id = UUID.randomUUID();
        CompoundTag rec = new CompoundTag();
        rec.putUUID("id", id);
        rec.putString("name", name);
        rec.putString("tag", tag);
        rec.putInt("color", color);
        rec.putString("motto", motto);
        rec.putString("regime", regime);
        rec.putUUID("founderClan", c.id);
        rec.putString("founderName", c.name);
        rec.putUUID("by", p.getUUID());
        rec.putLong("opened", ClanClock.now());
        rec.put("signers", new ListTag());
        FactionSaved saved = FactionSaved.get(server);
        saved.pacts.put(id, rec);
        saved.changed();
        CompoundTag show = rec.copy();
        show.remove("signers");
        show.remove("by");
        stack.getOrCreateTag().put(KEY, show);
        p.level().playSound(null, p.blockPosition(), RpSounds.QUILL.get(), SoundSource.PLAYERS, 0.8F, 1F);
        ClanMessages.tell(p, "clan.pioneers.pact.opened", Math.round(FactionRules.get().pactExpireMs() / (double) FactionRules.DAY));
        c.log(fr.pioneers.core.profile.PioneerApi.day(server), "clan.pioneers.faction.log.pact_opened", name);
        return null;
    }

    @Nullable
    private static String cancel(ServerPlayer p, ItemStack stack) {
        MinecraftServer server = p.getServer();
        CompoundTag rec = record(server, stack);
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (rec == null) {
            clear(stack);
            return null;
        }
        if (c == null || !c.id.equals(rec.getUUID("founderClan")) || !p.getUUID().equals(c.representative)) {
            return "clan.pioneers.pact.error.founder_only";
        }
        FactionSaved saved = FactionSaved.get(server);
        saved.pacts.remove(rec.getUUID("id"));
        saved.changed();
        clear(stack);
        ClanMessages.tell(p, "clan.pioneers.pact.cancelled");
        return null;
    }

    private static CompoundTag signData(CompoundTag rec) {
        CompoundTag d = new CompoundTag();
        d.putUUID("pact", rec.getUUID("id"));
        return d;
    }

    @Nullable
    private static String sign(ServerPlayer p, ItemStack stack) {
        MinecraftServer server = p.getServer();
        CompoundTag rec = record(server, stack);
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (rec == null) {
            return "clan.pioneers.pact.expired";
        }
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        ClanGov.Result r = FactionGov.clanDecide(p, c, FactionGov.SIGN, signData(rec), rec.getString("name"));
        if (!r.ok()) {
            return r.error();
        }
        ClanMessages.tell(p, r.outcome() == ClanGov.Outcome.PROPOSED ? "clan.pioneers.pact.sign_proposed" : "clan.pioneers.pact.signed_now",
              rec.getString("name"));
        return null;
    }

    /** Verification d'une signature (au lancement du vote et a son effet). */
    @Nullable
    static String checkSign(MinecraftServer server, Clan c, @Nullable ServerPlayer actor, CompoundTag data) {
        CompoundTag rec = FactionSaved.get(server).pact(data.hasUUID("pact") ? data.getUUID("pact") : null);
        if (rec == null) {
            return "clan.pioneers.pact.expired";
        }
        if (c.id.equals(rec.getUUID("founderClan"))) {
            return "clan.pioneers.pact.error.own";
        }
        List<UUID> s = signers(rec);
        if (s.contains(c.id)) {
            return "clan.pioneers.pact.error.signed";
        }
        if (s.size() + 2 > FactionRules.get().maxClans()) {
            return "clan.pioneers.faction.error.full";
        }
        return FactionRules.canEnter(FactionRules.get(), c, null, ClanClock.now());
    }

    /** Effet d'une signature acquise : le clan est inscrit au brouillon du serveur. */
    static boolean applySign(MinecraftServer server, Clan c, @Nullable UUID author, CompoundTag data) {
        if (checkSign(server, c, null, data) != null) {
            return false;
        }
        FactionSaved saved = FactionSaved.get(server);
        CompoundTag rec = saved.pact(data.getUUID("pact"));
        ListTag l = rec.getList("signers", Tag.TAG_COMPOUND);
        CompoundTag s = new CompoundTag();
        s.putUUID("clan", c.id);
        l.add(s);
        rec.put("signers", l);
        saved.changed();
        c.log(fr.pioneers.core.profile.PioneerApi.day(server), "clan.pioneers.faction.log.pact_signed", rec.getString("name"));
        Component msg = Component.translatable("clan.pioneers.pact.signed", c.name, rec.getString("name"));
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, msg));
        Clan founder = ClanData.get(server).clan(rec.getUUID("founderClan"));
        if (founder != null) {
            ClanMessages.members(server, founder, null, ClanMessages.tagged(founder, msg));
        }
        return true;
    }

    @Nullable
    private static String seal(ServerPlayer p, ItemStack stack) {
        MinecraftServer server = p.getServer();
        CompoundTag rec = record(server, stack);
        if (rec == null) {
            clear(stack);
            return "clan.pioneers.pact.expired";
        }
        Clan founder = ClanData.get(server).clanOf(p.getUUID());
        if (founder == null || !founder.id.equals(rec.getUUID("founderClan"))) {
            return "clan.pioneers.pact.error.founder_only";
        }
        //tout revoir avant de sceller : rien n'est ecrit si la fondation echoue
        String err = canFound(server, p, founder);
        if (err == null) {
            err = checkIdentity(server, rec.getString("name"), rec.getString("tag"), rec.getInt("color"), rec.getString("motto"),
                  rec.getString("regime"), rec.getUUID("id"));
        }
        if (err != null) {
            return err;
        }
        List<Clan> signers = new ArrayList<>();
        long now = ClanClock.now();
        for (UUID id : signers(rec)) {
            Clan c = ClanData.get(server).clan(id);
            if (c != null && FactionRules.canEnter(FactionRules.get(), c, null, now) == null && signers.size() + 2 <= FactionRules.get().maxClans()) {
                signers.add(c);
            }
        }
        if (signers.isEmpty()) {
            return "clan.pioneers.pact.error.no_signer";
        }
        long cost = EconomyApi.enabled() ? FactionRules.get().pactCost() : 0;
        if (cost > 0 && !ClanTreasury.debit(server, founder, cost, "#clan.pioneers.pact.reason", ClanMessages.character(p))) {
            return "clan.pioneers.pact.error.cost";
        }
        FactionSaved saved = FactionSaved.get(server);
        saved.pacts.remove(rec.getUUID("id"));
        saved.changed();
        stack.shrink(1);
        Faction f = Factions.create(server, rec, founder, signers, p.getUUID());
        p.level().playSound(null, p.blockPosition(), RpSounds.SEAL.get(), SoundSource.PLAYERS, 1F, 0.9F);
        Component text = Component.translatable("clan.pioneers.pact.text").withStyle(ChatFormatting.ITALIC);
        Factions.tell(server, f, Component.translatable("clan.pioneers.pact.sealed", f.name));
        Factions.tell(server, f, text);
        return null;
    }
}
