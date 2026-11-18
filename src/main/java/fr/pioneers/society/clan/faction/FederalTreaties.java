package fr.pioneers.society.clan.faction;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.territory.BorderSplits;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.society.clan.war.ClanWar;
import fr.pioneers.society.clan.war.War;
import fr.pioneers.society.clan.war.WarRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.9 (MAJ RP, bloc 4) : traites FEDERAUX (non-agression, accord commercial, alliance) entre une faction et un clan ou une autre faction. La
 * faction signe par sa decision federale (ligne « traite » de son regime federal) ; un clan par sa propre decision (ligne « traite » de son
 * regime). Le traite vaut pour TOUS les clans de la faction ; un clan qui la quitte le perd aussitot. Etiquettes dans {@code Faction.treaties}
 * (cle = autre camp, MEME format que les traites de clan : {@code alliance} + {@code end}, {@code trade} + {@code tradeEnd}, {@code nap} +
 * {@code napEnd}, {@code <sorte>Break}, {@code offers}), jamais dans {@code Clan.treaties} : le format des traites de clan ne change pas.
 * Lecture : {@link #active} est consulte par {@code ClanDiplomacy.active} et {@code ClanApi.relation}.
 */
public final class FederalTreaties {

    public static final String FED_TYPE = "treaty", CLAN_TYPE = "faction_treaty";

    private FederalTreaties() {
    }

    // ------------------------------------------------------------------ lecture
    /** Traite federal en cours entre deux clans (serveur courant). */
    public static boolean active(Clan a, Clan b, String kind, long now) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || a == null || b == null || a.faction == null && b.faction == null) {
            return false;
        }
        ClanData data = ClanData.get(server);
        return FactionRules.federalTreaty(data.faction(a.faction), data.faction(b.faction), a, b, kind, now);
    }

    public static boolean alliance(MinecraftServer server, Clan a, Clan b) {
        if (a.faction == null && b.faction == null) {
            return false;
        }
        ClanData data = ClanData.get(server);
        return FactionRules.federalTreaty(data.faction(a.faction), data.faction(b.faction), a, b, "alliance", ClanClock.now());
    }

    /** Un camp : une faction ou un clan. */
    record Side(UUID id, boolean faction, String name, List<Clan> clans) {
    }

    @Nullable
    static Side side(MinecraftServer server, @Nullable UUID id, boolean faction) {
        if (id == null) {
            return null;
        }
        ClanData data = ClanData.get(server);
        if (faction) {
            Faction f = data.faction(id);
            return f == null ? null : new Side(f.id, true, f.name, Factions.clans(server, f));
        }
        Clan c = data.clan(id);
        return c == null ? null : new Side(c.id, false, c.name, List.of(c));
    }

    /** Etiquettes a ecrire pour une paire : chez la faction, et chez l'autre faction s'il y en a une (format identique des deux cotes). */
    private static List<CompoundTag> tags(MinecraftServer server, Faction f, Side other) {
        List<CompoundTag> out = new ArrayList<>();
        out.add(f.treaties.computeIfAbsent(other.id(), k -> new CompoundTag()));
        if (other.faction()) {
            Faction o = ClanData.get(server).faction(other.id());
            if (o != null) {
                out.add(o.treaties.computeIfAbsent(f.id, k -> new CompoundTag()));
            }
        }
        return out;
    }

    @Nullable
    private static CompoundTag offer(@Nullable CompoundTag t, String kind, UUID from) {
        if (t == null) {
            return null;
        }
        CompoundTag o = t.getCompound("offers").getCompound(kind);
        return o.hasUUID("from") && o.getUUID("from").equals(from) && o.getLong("at") + WarRules.get().offerMs() > ClanClock.now() ? o : null;
    }

    /** Une guerre non finie oppose-t-elle les deux camps (guerre federale ou guerre entre deux de leurs clans) ? */
    private static boolean atWar(MinecraftServer server, Side a, Side b) {
        for (War w : ClanWar.wars(server)) {
            if (w.live() && (a.id().equals(w.a()) && b.id().equals(w.b()) || a.id().equals(w.b()) && b.id().equals(w.a()))) {
                return true;
            }
        }
        for (Clan x : a.clans()) {
            for (Clan y : b.clans()) {
                if (ClanWar.live(server, x, y) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ verification commune
    /**
     * Verifie une action ({@code propose}, {@code accept}, {@code refuse}, {@code break}) de la faction {@code f} envers {@code other}, ou du
     * clan {@code other} envers {@code f} quand {@code byClan} est vrai. Cle du refus ou {@code null}.
     */
    @Nullable
    static String check(MinecraftServer server, Faction f, Side other, String action, String kind, boolean byClan) {
        if (!FactionRules.TREATIES.contains(kind)) {
            return "clan.pioneers.treaty.unknown";
        }
        if (other.clans().isEmpty()) {
            return "clan.pioneers.error.no_clan";
        }
        if (other.faction() ? other.id().equals(f.id) : f.clans.contains(other.id())) {
            return "clan.pioneers.treaty.same_faction";
        }
        if (!other.faction() && other.clans().get(0).solo) {
            return "clan.pioneers.treaty.solo";
        }
        CompoundTag t = f.treaties.get(other.id());
        UUID me = byClan ? other.id() : f.id, them = byClan ? f.id : other.id();
        long now = ClanClock.now();
        return switch (action) {
            case "propose" -> {
                if (atWar(server, new Side(f.id, true, f.name, Factions.clans(server, f)), other)) {
                    yield "clan.pioneers.treaty.at_war";
                }
                if (FactionRules.treatyActive(t, kind, now) && !(t != null && t.getBoolean(kind + "Break"))) {
                    yield "clan.pioneers.treaty.already";
                }
                if (offer(t, kind, them) != null) {
                    yield "clan.pioneers.treaty.answer";
                }
                yield offer(t, kind, me) != null ? "clan.pioneers.treaty.pending" : null;
            }
            case "accept" -> {
                if (offer(t, kind, them) == null) {
                    yield "clan.pioneers.treaty.no_offer";
                }
                yield atWar(server, new Side(f.id, true, f.name, Factions.clans(server, f)), other) ? "clan.pioneers.treaty.at_war" : null;
            }
            case "refuse" -> offer(t, kind, them) == null ? "clan.pioneers.treaty.no_offer" : null;
            case "break" -> {
                if (!FactionRules.treatyActive(t, kind, now)) {
                    yield "clan.pioneers.treaty.none";
                }
                yield t.getBoolean(kind + "Break") ? "clan.pioneers.treaty.breaking" : null;
            }
            default -> "clan.pioneers.error.invalid";
        };
    }

    // ------------------------------------------------------------------ effets
    /** Applique une action verifiee (offre, signature, refus, rupture) entre la faction et l'autre camp. */
    static boolean apply(MinecraftServer server, Faction f, Side other, String action, String kind, boolean byClan) {
        if (check(server, f, other, action, kind, byClan) != null) {
            return false;
        }
        long now = ClanClock.now();
        UUID me = byClan ? other.id() : f.id;
        String by = byClan ? other.name() : f.name, to = byClan ? f.name : other.name();
        Component kindName = Component.translatable("clan.pioneers.treaty.kind." + kind);
        Side fSide = new Side(f.id, true, f.name, Factions.clans(server, f));
        Side mine = byClan ? other : fSide, theirs = byClan ? fSide : other;
        switch (action) {
            case "propose" -> {
                for (CompoundTag t : tags(server, f, other)) {
                    CompoundTag o = t.getCompound("offers");
                    CompoundTag x = new CompoundTag();
                    x.putUUID("from", me);
                    x.putLong("at", now);
                    o.put(kind, x);
                    t.put("offers", o);
                }
                tell(server, mine, Component.translatable("clan.pioneers.faction.treaty.offered", kindName, to));
                tell(server, theirs, Component.translatable(theirs.faction() ? "clan.pioneers.faction.treaty.received_faction"
                      : "clan.pioneers.faction.treaty.received_clan", by, kindName, by, kind));
            }
            case "accept", "refuse" -> {
                boolean yes = "accept".equals(action);
                long napEnd = now + WarRules.get().napMs();
                for (CompoundTag t : tags(server, f, other)) {
                    CompoundTag o = t.getCompound("offers");
                    o.remove(kind);
                    t.put("offers", o);
                    if (yes) {
                        t.putBoolean(kind, true);
                        t.remove(kind + "Break");
                        t.putLong(FactionRules.treatyEnd(kind), "nap".equals(kind) ? napEnd : 0);
                    }
                }
                Component msg = Component.translatable(yes ? "clan.pioneers.faction.treaty.signed" : "clan.pioneers.faction.treaty.refused", kindName,
                      f.name, other.name());
                tell(server, mine, msg);
                tell(server, theirs, msg);
                if (yes && "alliance".equals(kind)) {
                    relationsChanged(server, fSide, other);
                }
            }
            case "break" -> {
                long until = now + WarRules.get().ruptureMs();
                for (CompoundTag t : tags(server, f, other)) {
                    t.putBoolean(kind + "Break", true);
                    String k = FactionRules.treatyEnd(kind);
                    long cur = t.getLong(k);
                    t.putLong(k, "nap".equals(kind) ? Math.min(cur, until) : until);
                }
                Component msg = Component.translatable("clan.pioneers.faction.treaty.broken", by, kindName, to,
                      Math.round(WarRules.get().ruptureMs() / (double) WarRules.HOUR));
                tell(server, mine, msg);
                tell(server, theirs, msg);
            }
            default -> {
                return false;
            }
        }
        String logKey = switch (action) {
            case "propose" -> "clan.pioneers.faction.log.treaty_propose";
            case "accept" -> "clan.pioneers.faction.log.treaty_accept";
            case "refuse" -> "clan.pioneers.faction.log.treaty_refuse";
            default -> "clan.pioneers.faction.log.treaty_break";
        };
        Factions.log(server, f, logKey, "#clan.pioneers.treaty.kind." + kind, byClan ? by : to);
        ClanAudit.write(server, "treaty", null, "traite federal " + kind + " (" + action + ") : " + by + " -> " + to);
        ClanData.get(server).changed();
        return true;
    }

    private static void tell(MinecraftServer server, Side s, Component msg) {
        for (Clan c : s.clans()) {
            ClanMessages.members(server, c, null, ClanMessages.tagged(c, msg));
        }
    }

    /** Alliance signee ou finie : les reseaux de cables et les bordures des deux camps sont refaits (transit des allies). */
    private static void relationsChanged(MinecraftServer server, Side a, Side b) {
        for (Clan c : a.clans()) {
            BorderSplits.clanChanged(server, c);
        }
        for (Clan c : b.clans()) {
            BorderSplits.clanChanged(server, c);
        }
    }

    // ------------------------------------------------------------------ decisions
    /** Decision federale « treaty » : {action, kind, target, targetFaction}. */
    @Nullable
    static String checkFederal(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag d) {
        Side o = side(server, d.hasUUID("target") ? d.getUUID("target") : null, d.getBoolean("targetFaction"));
        return o == null ? "clan.pioneers.error.no_clan" : check(server, f, o, d.getString("action"), d.getString("kind"), false);
    }

    static boolean applyFederal(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag d) {
        Side o = side(server, d.hasUUID("target") ? d.getUUID("target") : null, d.getBoolean("targetFaction"));
        return o != null && apply(server, f, o, d.getString("action"), d.getString("kind"), false);
    }

    /** Decision de clan « faction_treaty » (traite avec une faction) : {action, kind, faction}. */
    @Nullable
    static String checkClan(MinecraftServer server, Clan c, @Nullable ServerPlayer actor, CompoundTag d) {
        Faction f = ClanData.get(server).faction(d.hasUUID("faction") ? d.getUUID("faction") : null);
        if (f == null) {
            return "clan.pioneers.faction.error.unknown";
        }
        if (c.solo) {
            return "clan.pioneers.treaty.solo";
        }
        return check(server, f, new Side(c.id, false, c.name, List.of(c)), d.getString("action"), d.getString("kind"), true);
    }

    static boolean applyClan(MinecraftServer server, Clan c, @Nullable UUID author, CompoundTag d) {
        Faction f = ClanData.get(server).faction(d.hasUUID("faction") ? d.getUUID("faction") : null);
        return f != null && apply(server, f, new Side(c.id, false, c.name, List.of(c)), d.getString("action"), d.getString("kind"), true);
    }

    // ------------------------------------------------------------------ termes (une fois par minute)
    /** Termes echus (rupture, non-agression), offres perimees, camps disparus, etiquettes vides retirees. */
    static void tick(MinecraftServer server, Faction f, long now) {
        ClanData data = ClanData.get(server);
        for (Map.Entry<UUID, CompoundTag> e : new ArrayList<>(f.treaties.entrySet())) {
            UUID id = e.getKey();
            boolean isFaction = data.faction(id) != null;
            if (!isFaction && data.clan(id) == null) {
                f.treaties.remove(id);
                continue;
            }
            CompoundTag t = e.getValue();
            for (String kind : FactionRules.TREATIES) {
                long end = t.getLong(FactionRules.treatyEnd(kind));
                if (t.getBoolean(kind) && end > 0 && end <= now) {
                    t.putBoolean(kind, false);
                    t.putLong(FactionRules.treatyEnd(kind), 0);
                    t.remove(kind + "Break");
                    Side o = side(server, id, isFaction);
                    if (o != null) {
                        Component msg = Component.translatable("clan.pioneers.faction.treaty.ended", Component.translatable(
                              "clan.pioneers.treaty.kind." + kind), f.name, o.name());
                        tell(server, new Side(f.id, true, f.name, Factions.clans(server, f)), msg);
                        if (!isFaction) {
                            tell(server, o, msg);
                        }
                        if ("alliance".equals(kind)) {
                            relationsChanged(server, new Side(f.id, true, f.name, Factions.clans(server, f)), o);
                        }
                    }
                }
            }
            CompoundTag o = t.getCompound("offers");
            for (String k : new ArrayList<>(o.getAllKeys())) {
                if (o.getCompound(k).getLong("at") + WarRules.get().offerMs() <= now) {
                    o.remove(k);
                }
            }
            t.put("offers", o);
            boolean any = false;
            for (String kind : FactionRules.TREATIES) {
                any |= t.getBoolean(kind);
            }
            if (!any && o.isEmpty()) {
                f.treaties.remove(id);
            }
        }
    }

    /** Traites et offres de la faction, pour la fiche (sorte, autre camp, fin, rupture annoncee, offres recues et envoyees). */
    static net.minecraft.nbt.ListTag view(MinecraftServer server, Faction f) {
        net.minecraft.nbt.ListTag out = new net.minecraft.nbt.ListTag();
        long now = ClanClock.now();
        f.treaties.forEach((id, t) -> {
            boolean isFaction = ClanData.get(server).faction(id) != null;
            Side o = side(server, id, isFaction);
            if (o == null) {
                return;
            }
            CompoundTag c = new CompoundTag();
            c.putString("name", o.name());
            c.putBoolean("faction", isFaction);
            net.minecraft.nbt.ListTag kinds = new net.minecraft.nbt.ListTag();
            for (String k : FactionRules.TREATIES) {
                if (FactionRules.treatyActive(t, k, now)) {
                    CompoundTag x = new CompoundTag();
                    x.putString("kind", k);
                    x.putLong("end", t.getLong(FactionRules.treatyEnd(k)));
                    x.putBoolean("breaking", t.getBoolean(k + "Break"));
                    kinds.add(x);
                }
            }
            c.put("treaties", kinds);
            net.minecraft.nbt.ListTag offers = new net.minecraft.nbt.ListTag();
            CompoundTag of = t.getCompound("offers");
            for (String k : of.getAllKeys()) {
                CompoundTag x = new CompoundTag();
                x.putString("kind", k);
                x.putBoolean("received", of.getCompound(k).hasUUID("from") && !f.id.equals(of.getCompound(k).getUUID("from")));
                x.putLong("expires", of.getCompound(k).getLong("at") + WarRules.get().offerMs());
                offers.add(x);
            }
            c.put("offers", offers);
            out.add(c);
        });
        return out;
    }
}
