package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.ClanData;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.society.economy.EconomyApi;
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
 * 1.9 (MAJ RP, bloc 4) : logique serveur de la Charte. Le client n'envoie que des intentions (rediger, annuler) : le serveur verifie tout
 * (fondateur libre de tout clan ordinaire et hors delai, nom unique et filtre, etiquette, couleur, devise, regime, plafond de clans, cout de
 * 1 000 jetons quand l'economie est active) a la redaction PUIS au scellement. Deux signatures dans les 10 minutes, a moins de 8 blocs du
 * fondateur ; la Charte expiree redevient vierge.
 */
public final class Charter {

    public static final String KEY = "charter";

    private Charter() {
    }

    // ------------------------------------------------------------------ brouillon
    @Nullable
    public static CompoundTag draft(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains(KEY, Tag.TAG_COMPOUND) && t.getCompound(KEY).hasUUID("founder") ? t.getCompound(KEY) : null;
    }

    public static boolean isOpen(ItemStack stack) {
        return draft(stack) != null;
    }

    public static List<CompoundTag> signatures(CompoundTag d) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag t : d.getList("signers", Tag.TAG_COMPOUND)) {
            out.add((CompoundTag) t);
        }
        return out;
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

    private static boolean expired(CompoundTag d, long now) {
        ClanDefs.Rules r = ClanCore.rules();
        return r == null || now - d.getLong("opened") > r.charterMinutes() * ClanDefs.MINUTE;
    }

    // ------------------------------------------------------------------ utilisation
    /** Clic droit avec la Charte : rediger (vierge), voir l'etat (fondateur), ou signer (autre joueur a qui le fondateur l'a passee). */
    public static void use(ServerPlayer p, InteractionHand hand) {
        if (!ClanCore.enabled()) {
            ClanMessages.refuse(p, ClanDefs.get().ready() ? "clan.pioneers.error.disabled" : "clan.pioneers.error.defs");
            return;
        }
        ItemStack stack = p.getItemInHand(hand);
        CompoundTag d = draft(stack);
        long now = ClanClock.now();
        if (d != null && expired(d, now)) {
            clear(stack);
            ClanMessages.refuse(p, "clan.pioneers.charter.expired");
            d = null;
        }
        if (d == null || d.getUUID("founder").equals(p.getUUID())) {
            view(p, hand, stack);
            return;
        }
        ServerPlayer founder = p.getServer().getPlayerList().getPlayer(d.getUUID("founder"));
        sign(p, stack, founder);
    }

    /** Envoie l'ecran de la Charte (redaction ou etat des signatures). */
    public static void view(ServerPlayer p, InteractionHand hand, ItemStack stack) {
        ClanDefs defs = ClanDefs.get();
        ClanDefs.Rules r = defs.rules;
        CompoundTag t = new CompoundTag();
        t.putInt("hand", hand.ordinal());
        CompoundTag d = draft(stack);
        if (d != null) {
            t.put("draft", d.copy());
            t.putLong("left", Math.max(0, r.charterMinutes() * ClanDefs.MINUTE - (ClanClock.now() - d.getLong("opened"))));
        }
        t.putLong("cost", EconomyApi.enabled() ? r.charterCost() : 0);
        t.putLong("balance", EconomyApi.enabled() ? EconomyApi.balance(p) : 0);
        t.putInt("need", r.charterSigners());
        t.putInt("radius", r.charterRadius());
        t.putInt("minutes", r.charterMinutes());
        t.putInt("nameMin", r.nameMin());
        t.putInt("nameMax", r.nameMax());
        t.putInt("tagMin", r.tagMin());
        t.putInt("tagMax", r.tagMax());
        t.putInt("mottoMax", r.mottoMax());
        String blocked = ClanCore.canEnter(p);
        if (blocked != null) {
            t.putString("blocked", blocked);
        }
        ListTag regimes = new ListTag();
        for (ClanDefs.Constitution k : defs.regimes.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", k.id());
            c.putInt("tax", k.activityTaxMax());
            c.putInt("dues", k.duesMax());
            c.putString("rep", k.repMode());
            c.putInt("term", k.termDays());
            regimes.add(c);
        }
        t.put("regimes", regimes);
        RpNet.send(p, RpActions.CHARTER_VIEW, t);
    }

    /** Intention du client ({@code CHARTER_EDIT}) : {@code op} = open (rediger et ouvrir aux signatures) ou cancel (redevenir vierge). */
    public static void edit(ServerPlayer p, CompoundTag t) {
        if (!ClanCore.enabled()) {
            ClanMessages.refuse(p, "clan.pioneers.error.disabled");
            return;
        }
        InteractionHand hand = t.getInt("hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = p.getItemInHand(hand);
        if (!stack.is(ClanItems.CHARTER.get())) {
            return;
        }
        CompoundTag d = draft(stack);
        if ("cancel".equals(t.getString("op"))) {
            if (d != null && d.getUUID("founder").equals(p.getUUID())) {
                clear(stack);
                ClanMessages.tell(p, "clan.pioneers.charter.cancelled");
                RpNet.send(p, RpActions.CHARTER_VIEW, notice("close"));
            }
            return;
        }
        if (!"open".equals(t.getString("op")) || d != null) {
            return;
        }
        String name = ClanNames.cleanName(t.getString("name")), tag = ClanNames.cleanTag(t.getString("tag")),
              motto = ClanNames.cleanMotto(t.getString("motto")), regime = t.getString("regime");
        int color = t.getInt("color");
        String err = check(p, name, tag, color, motto, regime);
        if (err != null) {
            ClanMessages.refuse(p, err);
            RpNet.send(p, RpActions.CHARTER_VIEW, error(err));
            return;
        }
        CompoundTag n = new CompoundTag();
        n.putString("name", name);
        n.putString("tag", tag);
        n.putInt("color", color);
        n.putString("motto", motto);
        n.putString("regime", regime);
        n.putUUID("founder", p.getUUID());
        n.putString("founderName", ClanMessages.character(p).startsWith("#") ? "?" : ClanMessages.character(p));
        n.putLong("opened", ClanClock.now());
        n.putInt("need", ClanCore.rules().charterSigners());
        n.put("signers", new ListTag());
        stack.getOrCreateTag().put(KEY, n);
        p.level().playSound(null, p.blockPosition(), RpSounds.QUILL.get(), SoundSource.PLAYERS, 0.8F, 1F);
        ClanMessages.tell(p, "clan.pioneers.charter.opened", ClanCore.rules().charterSigners(), ClanCore.rules().charterMinutes(),
              ClanCore.rules().charterRadius());
        view(p, hand, stack);
    }

    /** Verifie la fondation (a la redaction et au scellement) : cle du refus ou {@code null}. */
    @Nullable
    static String check(ServerPlayer founder, String name, String tag, int color, String motto, String regime) {
        ClanDefs.Rules r = ClanCore.rules();
        MinecraftServer server = founder.getServer();
        String err = ClanCore.canEnter(founder);
        if (err != null) {
            return err;
        }
        if (ClanCore.ordinaryClans(server) >= PiCommonConfig.get(RpConfig.MAX_CLANS, 200)) {
            return "clan.pioneers.error.max_clans";
        }
        err = ClanNames.checkName(name, r);
        if (err == null) {
            err = ClanNames.checkTag(tag, r);
        }
        if (err == null) {
            err = ClanNames.checkColor(color);
        }
        if (err == null) {
            err = ClanNames.checkMotto(motto, r);
        }
        if (err != null) {
            return err;
        }
        if (!ClanDefs.REGIMES.contains(regime) || ClanDefs.get().regime(regime) == null) {
            return "clan.pioneers.error.regime";
        }
        if (ClanCore.nameTaken(server, name, ClanData.get(server).clanOf(founder.getUUID()) == null ? null
              : ClanData.get(server).clanOf(founder.getUUID()).id)) {
            return "clan.pioneers.error.name_taken";
        }
        if (EconomyApi.enabled() && EconomyApi.balance(founder) < r.charterCost()) {
            return "clan.pioneers.error.charter_cost";
        }
        return null;
    }

    // ------------------------------------------------------------------ signatures
    /**
     * Un joueur signe la Charte ouverte {@code stack} (tenue par lui ou par le fondateur). Le fondateur doit etre connecte, a moins de 8
     * blocs. La derniere signature scelle la Charte : elle est consommee et le clan nait.
     */
    public static void sign(ServerPlayer signer, ItemStack stack, @Nullable ServerPlayer founder) {
        CompoundTag d = draft(stack);
        ClanDefs.Rules r = ClanCore.rules();
        if (d == null || r == null) {
            return;
        }
        long now = ClanClock.now();
        if (expired(d, now)) {
            clear(stack);
            ClanMessages.refuse(signer, "clan.pioneers.charter.expired");
            return;
        }
        if (founder == null || founder.isRemoved()) {
            ClanMessages.refuse(signer, "clan.pioneers.charter.founder_away", r.charterRadius());
            return;
        }
        if (founder == signer) {
            return;
        }
        if (founder.level() != signer.level() || founder.distanceToSqr(signer) > (double) r.charterRadius() * r.charterRadius()) {
            ClanMessages.refuse(signer, "clan.pioneers.charter.founder_away", r.charterRadius());
            return;
        }
        List<CompoundTag> sigs = signatures(d);
        for (CompoundTag s : sigs) {
            if (s.getUUID("u").equals(signer.getUUID())) {
                ClanMessages.refuse(signer, "clan.pioneers.charter.already_signed");
                return;
            }
        }
        String err = ClanCore.canEnter(signer);
        if (err != null) {
            ClanMessages.refuse(signer, err);
            return;
        }
        boolean last = sigs.size() + 1 >= d.getInt("need");
        if (last) {
            //tout revoir avant la derniere signature : rien n'est ecrit si la fondation echoue
            String f = check(founder, d.getString("name"), d.getString("tag"), d.getInt("color"), d.getString("motto"), d.getString("regime"));
            List<ServerPlayer> signers = new ArrayList<>();
            for (CompoundTag s : sigs) {
                ServerPlayer sp = founder.getServer().getPlayerList().getPlayer(s.getUUID("u"));
                if (sp == null || ClanCore.canEnter(sp) != null) {
                    f = "clan.pioneers.charter.signer_away";
                } else {
                    signers.add(sp);
                }
            }
            if (f != null) {
                ClanMessages.refuse(signer, f);
                ClanMessages.refuse(founder, f);
                return;
            }
            signers.add(signer);
            seal(founder, signers, stack, d);
            return;
        }
        CompoundTag s = new CompoundTag();
        s.putUUID("u", signer.getUUID());
        s.putString("n", ClanMessages.character(signer).startsWith("#") ? "?" : ClanMessages.character(signer));
        ListTag l = d.getList("signers", Tag.TAG_COMPOUND);
        l.add(s);
        d.put("signers", l);
        signer.level().playSound(null, signer.blockPosition(), RpSounds.QUILL.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
        Component msg = Component.translatable("clan.pioneers.charter.signed", s.getString("n"), sigs.size() + 1, d.getInt("need"));
        signer.sendSystemMessage(msg);
        founder.sendSystemMessage(msg);
    }

    private static void seal(ServerPlayer founder, List<ServerPlayer> signers, ItemStack stack, CompoundTag d) {
        ClanDefs.Rules r = ClanCore.rules();
        if (EconomyApi.enabled() && !EconomyApi.debit(founder, r.charterCost(), EconomyApi.Entry.of("clan_charter", d.getString("name"))).ok()) {
            ClanMessages.refuse(founder, "clan.pioneers.error.charter_cost");
            return;
        }
        String name = d.getString("name"), tag = d.getString("tag"), motto = d.getString("motto"), regime = d.getString("regime");
        int color = d.getInt("color");
        stack.shrink(1);
        ClanCore.found(founder.getServer(), founder, signers, name, tag, color, motto, regime);
        founder.level().playSound(null, founder.blockPosition(), RpSounds.SEAL.get(), SoundSource.PLAYERS, 1F, 1F);
        Component text = Component.translatable("clan.pioneers.charter.text").withStyle(ChatFormatting.ITALIC);
        List<ServerPlayer> all = new ArrayList<>(signers);
        all.add(founder);
        for (ServerPlayer p : all) {
            p.sendSystemMessage(Component.translatable("clan.pioneers.charter.sealed", Component.literal(name).withStyle(ClanMessages.dye(color))));
            p.sendSystemMessage(text);
            RpNet.send(p, RpActions.CHARTER_VIEW, notice("close"));
        }
    }

    /** Signature en visant le fondateur (clic droit sur lui pendant qu'il tient la Charte ouverte). */
    public static boolean signOnFounder(ServerPlayer signer, ServerPlayer founder) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = founder.getItemInHand(h);
            CompoundTag d = s.is(ClanItems.CHARTER.get()) ? draft(s) : null;
            if (d != null && d.getUUID("founder").equals(founder.getUUID())) {
                sign(signer, s, founder);
                return true;
            }
        }
        return false;
    }

    /** {@code /clan sign <fondateur>} : signer la Charte que le fondateur tient (a moins de 8 blocs). */
    @Nullable
    public static String signCommand(ServerPlayer signer, ServerPlayer founder) {
        return signOnFounder(signer, founder) ? null : "clan.pioneers.charter.none_open";
    }

    /** Message a l'ecran de la Charte ({@code CHARTER_VIEW} sans brouillon) : {@code close} (fermer) ou {@code error} (cle du refus). */
    static CompoundTag notice(String kind) {
        CompoundTag t = new CompoundTag();
        t.putBoolean(kind, true);
        return t;
    }

    static CompoundTag error(String key) {
        CompoundTag t = new CompoundTag();
        t.putString("error", key);
        return t;
    }

    /** Pour le banc et l'audit : identifiant du fondateur d'un brouillon. */
    @Nullable
    public static UUID founder(ItemStack stack) {
        CompoundTag d = draft(stack);
        return d == null ? null : d.getUUID("founder");
    }
}
