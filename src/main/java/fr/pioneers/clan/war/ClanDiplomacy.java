package fr.pioneers.clan.war;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.clan.territory.BorderSplits;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : traites entre clans. Trois sortes : non-agression (7 jours, renouvelable : aucune declaration de guerre entre les
 * deux), alliance (zones Ouvertes, transit, tir allie coupe, cartes et personnages partages) et accord commercial (aucune taxe de marche entre
 * les deux). Propose par un membre qui a la permission de diplomatie, accepte selon le regime de l'autre clan (decision {@code treaty}),
 * rompu selon le regime avec un preavis de 24 heures reelles.
 *
 * <p>Format (dans {@code Clan.treaties}, MEME etiquette des deux cotes) : {@code alliance} + {@code end} (lus tels quels par
 * {@code ClanApi.relation} : fin de l'alliance apres une rupture, 0 = sans fin), {@code trade} + {@code tradeEnd}, {@code nap} +
 * {@code napEnd}, {@code <sorte>Break} (rupture annoncee), {@code offers} (sorte -&gt; {from, at} : offres en attente). Le champ {@code end}
 * n'est non nul que pendant le preavis d'une rupture d'alliance et repasse a 0 a son terme : les lecteurs de {@code trade} + {@code end}
 * restent justes. Lire de preference {@link #tradeAgreement}, {@link #nonAggression}.
 */
public final class ClanDiplomacy {

    public static final String NAP = "nap", ALLIANCE = "alliance", TRADE = "trade";
    public static final List<String> KINDS = List.of(NAP, ALLIANCE, TRADE);

    private ClanDiplomacy() {
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    private static CompoundTag tag(Clan a, Clan b) {
        return a.treaties.get(b.id);
    }

    private static String endKey(String kind) {
        return switch (kind) {
            case ALLIANCE -> "end";
            case TRADE -> "tradeEnd";
            default -> "napEnd";
        };
    }

    /** Le traite court-il (signe, pas encore arrive au terme de son preavis ou de ses 7 jours) ? */
    public static boolean active(Clan a, Clan b, String kind, long now) {
        CompoundTag t = tag(a, b);
        if (t == null || !t.getBoolean(kind)) {
            return false;
        }
        long end = t.getLong(endKey(kind));
        return NAP.equals(kind) ? end > now : end <= 0 || end > now;
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : traite en vigueur entre deux clans, signe par eux ({@link #active}) OU par la faction de l'un ou de l'autre
     * (traite federal, etiquettes dans {@code Faction.treaties}). Les trois lecteurs ci-dessous l'utilisent ; {@link #active} reste celui des
     * traites de clan (liste, rupture).
     */
    public static boolean effective(Clan a, Clan b, String kind, long now) {
        return active(a, b, kind, now) || fr.pioneers.clan.faction.FederalTreaties.active(a, b, kind, now);
    }

    /** Accord commercial : aucune taxe de marche entre les deux clans. */
    public static boolean tradeAgreement(@Nullable Clan a, @Nullable Clan b) {
        return a != null && b != null && !a.id.equals(b.id) && effective(a, b, TRADE, ClanClock.now());
    }

    /** Pacte de non-agression en cours : aucune declaration de guerre possible. */
    public static boolean nonAggression(@Nullable Clan a, @Nullable Clan b) {
        return a != null && b != null && effective(a, b, NAP, ClanClock.now());
    }

    /** Alliance signee (hors meme faction). */
    public static boolean alliance(@Nullable Clan a, @Nullable Clan b) {
        return a != null && b != null && effective(a, b, ALLIANCE, ClanClock.now());
    }

    @Nullable
    private static CompoundTag offer(Clan receiver, Clan sender, String kind) {
        CompoundTag t = tag(receiver, sender);
        if (t == null) {
            return null;
        }
        CompoundTag o = t.getCompound("offers").getCompound(kind);
        return o.hasUUID("from") && o.getUUID("from").equals(sender.id) ? o : null;
    }

    // ------------------------------------------------------------------ ecritures (les deux cotes ensemble)
    private static void both(Clan a, Clan b, java.util.function.Consumer<CompoundTag> f) {
        f.accept(a.treaties.computeIfAbsent(b.id, k -> new CompoundTag()));
        f.accept(b.treaties.computeIfAbsent(a.id, k -> new CompoundTag()));
    }

    private static void clearOffer(Clan a, Clan b, String kind) {
        both(a, b, t -> {
            CompoundTag o = t.getCompound("offers");
            o.remove(kind);
            t.put("offers", o);
        });
    }

    // ------------------------------------------------------------------ actions des joueurs
    /** Propose un traite (permission de diplomatie). Cle de refus ou null. */
    @Nullable
    public static String propose(ServerPlayer by, Clan from, Clan to, String kind) {
        MinecraftServer server = by.getServer();
        long now = ClanClock.now();
        WarRules.Values v = WarRules.get();
        if (!KINDS.contains(kind)) {
            return "clan.pioneers.treaty.unknown";
        }
        if (!ClanRoles.has(from, by.getUUID(), Permission.DIPLOMACY)) {
            return "clan.pioneers.error.permission";
        }
        if (from.solo || to.solo) {
            return "clan.pioneers.treaty.solo";
        }
        if (from.id.equals(to.id)) {
            return "clan.pioneers.treaty.self";
        }
        if (ClanWar.live(server, from, to) != null) {
            return "clan.pioneers.treaty.at_war";
        }
        if (ALLIANCE.equals(kind) && from.faction != null && from.faction.equals(to.faction)) {
            return "clan.pioneers.treaty.same_faction";
        }
        if (!NAP.equals(kind) && active(from, to, kind, now)) {
            return "clan.pioneers.treaty.already";
        }
        if (offer(to, from, kind) != null && offer(to, from, kind).getLong("at") + v.offerMs() > now) {
            return "clan.pioneers.treaty.pending";
        }
        if (offer(from, to, kind) != null && offer(from, to, kind).getLong("at") + v.offerMs() > now) {
            //l'autre clan a deja propose ce traite : il suffit d'accepter
            return "clan.pioneers.treaty.answer";
        }
        both(from, to, t -> {
            CompoundTag o = t.getCompound("offers");
            CompoundTag x = new CompoundTag();
            x.putUUID("from", from.id);
            x.putLong("at", now);
            o.put(kind, x);
            t.put("offers", o);
        });
        long day = PioneerApi.day(server);
        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
        from.log(day, "clan.pioneers.log.treaty_offered", "#clan.pioneers.treaty.kind." + kind, to.name);
        to.log(day, "clan.pioneers.log.treaty_received", "#clan.pioneers.treaty.kind." + kind, from.name);
        ClanMessages.members(server, from, Permission.DIPLOMACY, ClanMessages.tagged(from, Component.translatable("clan.pioneers.treaty.offered",
              kindName, to.name)));
        ClanMessages.members(server, to, Permission.DIPLOMACY, ClanMessages.tagged(to, Component.translatable("clan.pioneers.treaty.received",
              from.name, kindName, from.name, kind)));
        ClanData.get(server).changed();
        return null;
    }

    /** Accepte une offre (decision {@code treaty} du clan qui la recoit). */
    public static void accept(ServerPlayer by, Clan clan, Clan from, String kind) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.ACCEPT);
        d.putUUID("other", from.id);
        d.putString("kind", kind);
        WarDecisions.submit(by, clan, d, from.name);
    }

    /** Refuse une offre (permission de diplomatie, sans vote). */
    @Nullable
    public static String refuse(ServerPlayer by, Clan clan, Clan from, String kind) {
        if (!ClanRoles.has(clan, by.getUUID(), Permission.DIPLOMACY)) {
            return "clan.pioneers.error.permission";
        }
        if (offer(clan, from, kind) == null) {
            return "clan.pioneers.treaty.no_offer";
        }
        clearOffer(clan, from, kind);
        MinecraftServer server = by.getServer();
        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
        ClanMessages.members(server, from, Permission.DIPLOMACY, ClanMessages.tagged(from, Component.translatable("clan.pioneers.treaty.refused",
              clan.name, kindName)));
        ClanData.get(server).changed();
        return null;
    }

    /** Rompt un traite (decision {@code treaty}) : preavis de 24 heures reelles. */
    public static void breakTreaty(ServerPlayer by, Clan clan, Clan other, String kind) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.BREAK);
        d.putUUID("other", other.id);
        d.putString("kind", kind);
        WarDecisions.submit(by, clan, d, other.name);
    }

    // ------------------------------------------------------------------ decisions (verification puis effet)
    @Nullable
    private static Clan other(MinecraftServer server, CompoundTag data) {
        return data.hasUUID("other") ? ClanData.get(server).clan(data.getUUID("other")) : null;
    }

    @Nullable
    static String checkAccept(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan from = other(server, data);
        String kind = data.getString("kind");
        if (from == null || !KINDS.contains(kind)) {
            return "clan.pioneers.treaty.no_offer";
        }
        CompoundTag o = offer(clan, from, kind);
        if (o == null || o.getLong("at") + WarRules.get().offerMs() <= ClanClock.now()) {
            return "clan.pioneers.treaty.no_offer";
        }
        if (ClanWar.live(server, clan, from) != null) {
            return "clan.pioneers.treaty.at_war";
        }
        return null;
    }

    static boolean applyAccept(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan from = other(server, data);
        String kind = data.getString("kind");
        if (from == null) {
            return false;
        }
        long now = ClanClock.now();
        long napEnd = now + WarRules.get().napMs();
        both(clan, from, t -> {
            CompoundTag o = t.getCompound("offers");
            o.remove(kind);
            t.put("offers", o);
            t.putBoolean(kind, true);
            t.remove(kind + "Break");
            switch (kind) {
                case ALLIANCE -> t.putLong("end", 0);
                case TRADE -> t.putLong("tradeEnd", 0);
                default -> t.putLong("napEnd", napEnd);
            }
        });
        long day = PioneerApi.day(server);
        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
        for (Clan[] p : new Clan[][]{{clan, from}, {from, clan}}) {
            p[0].log(day, "clan.pioneers.log.treaty_signed", "#clan.pioneers.treaty.kind." + kind, p[1].name);
            ClanMessages.members(server, p[0], null, ClanMessages.tagged(p[0], Component.translatable("clan.pioneers.treaty.signed", kindName,
                  p[1].name)));
        }
        ClanAudit.write(server, "treaty", clan, "traite " + kind + " signe avec " + from.name + " (" + from.id + ")");
        ClanData.get(server).changed();
        if (ALLIANCE.equals(kind)) {
            //1.9 (MAJ RP, GameTests) : une alliance ouvre les frontieres des deux clans : leurs reseaux (cables, donnees, arbres) se refont
            //(avant, ils restaient coupes a la frontiere jusqu'au prochain changement de claim)
            BorderSplits.clanChanged(server, clan);
            BorderSplits.clanChanged(server, from);
        }
        return true;
    }

    @Nullable
    static String checkBreak(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = other(server, data);
        String kind = data.getString("kind");
        if (other == null || !KINDS.contains(kind) || !active(clan, other, kind, ClanClock.now())) {
            return "clan.pioneers.treaty.none";
        }
        CompoundTag t = tag(clan, other);
        if (t != null && t.getBoolean(kind + "Break")) {
            return "clan.pioneers.treaty.breaking";
        }
        return null;
    }

    static boolean applyBreak(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = other(server, data);
        String kind = data.getString("kind");
        if (other == null) {
            return false;
        }
        long now = ClanClock.now();
        long until = now + WarRules.get().ruptureMs();
        both(clan, other, t -> {
            t.putBoolean(kind + "Break", true);
            String k = endKey(kind);
            long cur = t.getLong(k);
            t.putLong(k, NAP.equals(kind) ? Math.min(cur, until) : until);
        });
        long day = PioneerApi.day(server);
        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
        for (Clan[] p : new Clan[][]{{clan, other}, {other, clan}}) {
            p[0].log(day, "clan.pioneers.log.treaty_broken", "#clan.pioneers.treaty.kind." + kind, p[1].name, clan.name);
            ClanMessages.members(server, p[0], null, ClanMessages.tagged(p[0], Component.translatable("clan.pioneers.treaty.broken", clan.name,
                  kindName, other.name, Math.round(WarRules.get().ruptureMs() / (double) WarRules.HOUR))));
        }
        ClanAudit.write(server, "treaty", clan, "rupture du traite " + kind + " avec " + other.name + " (preavis)");
        ClanData.get(server).changed();
        return true;
    }

    /** Une declaration de guerre met fin tout de suite a l'accord commercial et aux offres entre les deux clans. */
    static void warDeclared(MinecraftServer server, Clan a, Clan b) {
        both(a, b, t -> {
            t.putBoolean(TRADE, false);
            t.putLong("tradeEnd", 0);
            t.remove(TRADE + "Break");
            t.put("offers", new CompoundTag());
        });
        ClanData.get(server).changed();
    }

    // ------------------------------------------------------------------ fins de preavis et de traites (controle espace)
    /** Termes echus : alliance, accord, pacte arrives a leur fin ; offres perimees ; etiquettes vides retirees. */
    public static void tick(MinecraftServer server, long now) {
        ClanData data = ClanData.get(server);
        boolean changed = false;
        long day = PioneerApi.day(server);
        for (Clan a : new ArrayList<>(data.clans())) {
            for (Map.Entry<UUID, CompoundTag> e : new ArrayList<>(a.treaties.entrySet())) {
                Clan b = data.clan(e.getKey());
                if (b == null) {
                    a.treaties.remove(e.getKey());
                    changed = true;
                    continue;
                }
                if (a.id.compareTo(b.id) > 0) {
                    continue;
                }
                for (String kind : KINDS) {
                    CompoundTag t = e.getValue();
                    if (!t.getBoolean(kind)) {
                        continue;
                    }
                    long end = t.getLong(endKey(kind));
                    if (end > 0 && end <= now) {
                        both(a, b, x -> {
                            x.putBoolean(kind, false);
                            x.putLong(endKey(kind), 0);
                            x.remove(kind + "Break");
                        });
                        if (ALLIANCE.equals(kind)) {
                            //1.9 (MAJ RP, GameTests) : fin d'alliance : les reseaux qui passaient la frontiere se coupent
                            BorderSplits.clanChanged(server, a);
                            BorderSplits.clanChanged(server, b);
                        }
                        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
                        for (Clan[] p : new Clan[][]{{a, b}, {b, a}}) {
                            p[0].log(day, "clan.pioneers.log.treaty_ended", "#clan.pioneers.treaty.kind." + kind, p[1].name);
                            ClanMessages.members(server, p[0], null, ClanMessages.tagged(p[0], Component.translatable("clan.pioneers.treaty.ended",
                                  kindName, p[1].name)));
                        }
                        changed = true;
                    }
                }
                for (Clan[] p : new Clan[][]{{a, b}, {b, a}}) {
                    CompoundTag t = p[0].treaties.get(p[1].id);
                    if (t == null) {
                        continue;
                    }
                    CompoundTag o = t.getCompound("offers");
                    for (String k : new ArrayList<>(o.getAllKeys())) {
                        if (o.getCompound(k).getLong("at") + WarRules.get().offerMs() <= now) {
                            o.remove(k);
                            changed = true;
                        }
                    }
                    t.put("offers", o);
                    if (!t.getBoolean(NAP) && !t.getBoolean(ALLIANCE) && !t.getBoolean(TRADE) && o.isEmpty()) {
                        p[0].treaties.remove(p[1].id);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            data.changed();
        }
    }

    // ------------------------------------------------------------------ vue (onglet Clan, fiche Diplomatie)
    /** Traites et offres d'un clan (membres) : sorte, autre clan, fin, rupture annoncee, offres recues et envoyees. */
    public static ListTag view(MinecraftServer server, Clan clan) {
        ListTag out = new ListTag();
        ClanData data = ClanData.get(server);
        long now = ClanClock.now();
        clan.treaties.forEach((id, t) -> {
            Clan o = data.clan(id);
            if (o == null) {
                return;
            }
            CompoundTag c = new CompoundTag();
            c.putString("clan", o.name);
            c.putString("tag", o.tag);
            c.putInt("color", o.color);
            ListTag kinds = new ListTag();
            for (String k : KINDS) {
                if (active(clan, o, k, now)) {
                    CompoundTag x = new CompoundTag();
                    x.putString("kind", k);
                    x.putLong("end", t.getLong(endKey(k)));
                    x.putBoolean("breaking", t.getBoolean(k + "Break"));
                    kinds.add(x);
                }
            }
            c.put("treaties", kinds);
            ListTag offers = new ListTag();
            CompoundTag of = t.getCompound("offers");
            for (String k : of.getAllKeys()) {
                CompoundTag x = new CompoundTag();
                x.putString("kind", k);
                x.putBoolean("received", !clan.id.equals(of.getCompound(k).hasUUID("from") ? of.getCompound(k).getUUID("from") : null));
                x.putLong("expires", of.getCompound(k).getLong("at") + WarRules.get().offerMs());
                offers.add(x);
            }
            c.put("offers", offers);
            c.putString("relation", ClanApi.relation(server, clan, o).name().toLowerCase(java.util.Locale.ROOT));
            out.add(c);
        });
        return out;
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : intention de traite venue de l'onglet Clan (proposer, accepter, refuser, rompre), avec les memes verifications
     * que la commande {@code /clan treaty}. Renvoie la cle du refus ou {@code null}.
     */
    @Nullable
    public static String treatyIntent(ServerPlayer p, Clan clan, Clan other, String verb, String kind) {
        if (!KINDS.contains(kind)) {
            return "clan.pioneers.treaty.unknown";
        }
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.ACCEPT);
        d.putUUID("other", other.id);
        d.putString("kind", kind);
        switch (verb) {
            case "propose" -> {
                return propose(p, clan, other, kind);
            }
            case "refuse" -> {
                return refuse(p, clan, other, kind);
            }
            case "accept" -> {
                String err = checkAccept(p.getServer(), clan, d);
                if (err == null) {
                    accept(p, clan, other, kind);
                }
                return err;
            }
            case "break" -> {
                d.putString("action", WarDecisions.BREAK);
                String err = checkBreak(p.getServer(), clan, d);
                if (err == null) {
                    breakTreaty(p, clan, other, kind);
                }
                return err;
            }
            default -> {
                return "clan.pioneers.error.invalid";
            }
        }
    }

    /** Liste lisible des traites, pour la commande {@code /clan treaty list}. */
    public static void list(ServerPlayer p, Clan clan) {
        MinecraftServer server = p.getServer();
        ListTag l = view(server, clan);
        if (l.isEmpty()) {
            p.sendSystemMessage(Component.translatable("clan.pioneers.treaty.list_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        p.sendSystemMessage(Component.translatable("clan.pioneers.treaty.list_head").withStyle(ChatFormatting.GOLD));
        long now = ClanClock.now();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            ListTag ks = c.getList("treaties", 10);
            for (int j = 0; j < ks.size(); j++) {
                CompoundTag x = ks.getCompound(j);
                long end = x.getLong("end");
                Component until = end > 0 ? Component.translatable("clan.pioneers.treaty.until", Math.max(1, (end - now + WarRules.HOUR - 1)
                                                                                                                 / WarRules.HOUR))
                      : Component.translatable("clan.pioneers.treaty.no_end");
                p.sendSystemMessage(Component.translatable(x.getBoolean("breaking") ? "clan.pioneers.treaty.line_breaking"
                      : "clan.pioneers.treaty.line", Component.translatable("clan.pioneers.treaty.kind." + x.getString("kind")), c.getString("clan"),
                      until));
            }
            ListTag os = c.getList("offers", 10);
            for (int j = 0; j < os.size(); j++) {
                CompoundTag x = os.getCompound(j);
                p.sendSystemMessage(Component.translatable(x.getBoolean("received") ? "clan.pioneers.treaty.line_received"
                      : "clan.pioneers.treaty.line_sent", Component.translatable("clan.pioneers.treaty.kind." + x.getString("kind")),
                      c.getString("clan")).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
