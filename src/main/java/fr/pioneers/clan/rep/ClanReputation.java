package fr.pioneers.clan.rep;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.reputation.PeopleApi;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.ruins.RuinApi;
import fr.pioneers.ruins.RuinEvents;
import fr.pioneers.ruins.RuinSites;
import fr.pioneers.village.VillageData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.9 (MAJ RP, bloc 4) : reputation de clan, le micro-peuple. Trois jauges (une par peuple du registre, {@link PeopleApi}) de -300 a +300 dans
 * {@code Clan.reputation}, plafond journalier de +30 par peuple ({@code Clan.repDay/repToday}), aucun pardon par le temps. Cascade branchee
 * sur {@link ReputationApi#cascade} : un acte compte 100 % au joueur (bloc 3), 50 % a son clan, 25 % a sa faction (jauges dans
 * {@code Faction.extra}) ou a son peuple de joueurs ({@link #peopleGauge}, branche par l'agent des peuples). Actes collectifs a 100 % :
 * restauration d'une ruine au nom du clan, ouvriers de village finances, accord avec un Colporteur.
 *
 * <p>Effets des paliers : affinite effective ({@link #effective}, prix et dialogue, JAMAIS la moyenne d'un village), ouvriers de village
 * ({@link #workerFactor}), arrets des Colporteurs au Marche du clan ({@link #peddlerStopsToday}). Estime d'un clan pour un village : moyenne,
 * sur ses membres actifs, de la reputation personnelle de chacun aupres de ce village ({@link #villageEsteem}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanReputation {

    /** Part de la faction quand elle est devenue un peuple de joueurs (branchee par l'agent des peuples) : ses propres jauges de peuple. */
    public interface PeopleGauge {

        void apply(MinecraftServer server, Faction faction, String people, int delta, String cause);

        int value(MinecraftServer server, Faction faction, String people);
    }

    @Nullable
    public static PeopleGauge peopleGauge;

    private static final String REM = "rep.rem", ONCE = "rep.once", F_REP = "rep", F_DAY = "repDay", F_TODAY = "repToday", F_REM = "rep.rem";
    private static final int ONCE_MAX = 4096;

    private ClanReputation() {
    }

    /** Branche la cascade dans la reputation du bloc 3. */
    public static void install() {
        ReputationApi.cascade = ClanReputation::cascade;
    }

    // ------------------------------------------------------------------ cascade
    /** Appelee apres chaque changement de reputation d'un joueur (delta effectivement applique au joueur). */
    static void cascade(ServerPlayer player, String people, int delta, String cause) {
        ClanRepRules.Values v = ClanRepRules.get();
        if (!ClanApi.enabled() || delta == 0 || v.skipCascade().contains(cause)) {
            return;
        }
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        Clan clan = data.clanOf(player.getUUID());
        if (clan == null || clan.solo) {
            return;
        }
        CompoundTag rem = clan.extra.getCompound(REM);
        int[] s = ClanRepRules.share(delta, v.share(), rem.getInt(people));
        rem.putInt(people, s[1]);
        clan.extra.put(REM, rem);
        if (s[0] != 0) {
            addClan(server, clan, people, s[0], cause, false);
        }
        Faction f = data.faction(clan.faction);
        if (f != null) {
            CompoundTag fr = f.extra.getCompound(F_REM);
            int[] u = ClanRepRules.share(delta, v.upperShare(), fr.getInt(people));
            fr.putInt(people, u[1]);
            f.extra.put(F_REM, fr);
            if (u[0] != 0) {
                if (peopleGauge != null && f.people != null && !f.people.isEmpty()) {
                    peopleGauge.apply(server, f, people, u[0], cause);
                } else {
                    addFaction(server, f, people, u[0], cause, false);
                }
            }
        }
        data.changed();
    }

    /** Ajoute des points a la jauge du clan (gain plafonne par jour sauf {@code uncapped}, perte jamais). Renvoie le changement effectif. */
    public static int addClan(MinecraftServer server, Clan clan, String people, int delta, String cause, boolean uncapped) {
        ClanRepRules.Values v = ClanRepRules.get();
        long day = PioneerApi.day(server);
        if (clan.repDay != day) {
            clan.repDay = day;
            clan.repToday.clear();
        }
        int d = delta;
        if (d > 0 && !uncapped) {
            int used = clan.repToday.getOrDefault(people, 0);
            d = ClanRepRules.capGain(d, used, v.dailyCap());
            if (d == 0) {
                return 0;
            }
            clan.repToday.put(people, used + d);
        }
        int old = clan.reputation.getOrDefault(people, 0);
        int now = ClanRepRules.clamp(old + d, v.min(), v.max());
        if (now == old) {
            return 0;
        }
        clan.reputation.put(people, now);
        CompoundTag log = new CompoundTag();
        log.putLong("d", day);
        log.putString("p", people);
        log.putInt("v", now - old);
        log.putString("c", cause);
        clan.repLog.addLast(log);
        while (clan.repLog.size() > Clan.REP_LOG) {
            clan.repLog.pollFirst();
        }
        String before = ReputationApi.tierOf(old).id(), after = ReputationApi.tierOf(now).id();
        if (!before.equals(after)) {
            clan.log(day, "clan.pioneers.log.rep_tier", "#people.pioneers." + people, "#reputation.pioneers.tier." + after);
            ClanMessages.members(server, clan, null, ClanMessages.tagged(clan, Component.translatable(now > old ? "clan.pioneers.rep.tier_up"
                  : "clan.pioneers.rep.tier_down", ReputationApi.peopleName(server, people), Component.translatable("reputation.pioneers.tier." + after))));
        }
        ClanData.get(server).changed();
        return now - old;
    }

    /** Jauge de faction (meme regle : plafond journalier, aucune remontee par le temps), rangee dans {@code Faction.extra}. */
    public static int addFaction(MinecraftServer server, Faction f, String people, int delta, String cause, boolean uncapped) {
        ClanRepRules.Values v = ClanRepRules.get();
        long day = PioneerApi.day(server);
        if (f.extra.getLong(F_DAY) != day || !f.extra.contains(F_DAY)) {
            f.extra.putLong(F_DAY, day);
            f.extra.put(F_TODAY, new CompoundTag());
        }
        CompoundTag today = f.extra.getCompound(F_TODAY);
        int d = delta;
        if (d > 0 && !uncapped) {
            d = ClanRepRules.capGain(d, today.getInt(people), v.dailyCap());
            if (d == 0) {
                return 0;
            }
            today.putInt(people, today.getInt(people) + d);
            f.extra.put(F_TODAY, today);
        }
        CompoundTag rep = f.extra.getCompound(F_REP);
        int old = rep.getInt(people);
        int now = ClanRepRules.clamp(old + d, v.min(), v.max());
        rep.putInt(people, now);
        f.extra.put(F_REP, rep);
        ClanData.get(server).changed();
        return now - old;
    }

    public static int clanValue(Clan c, String people) {
        return c.reputation.getOrDefault(people, 0);
    }

    /** Jauge de la faction (ou du peuple de joueurs qu'elle est devenue). */
    public static int factionValue(MinecraftServer server, @Nullable Faction f, String people) {
        if (f == null) {
            return 0;
        }
        if (peopleGauge != null && f.people != null && !f.people.isEmpty()) {
            return peopleGauge.value(server, f, people);
        }
        return f.extra.getCompound(F_REP).getInt(people);
    }

    // ------------------------------------------------------------------ actes collectifs (100 %)
    /**
     * Acte collectif du clan (tableau {@code collective} de {@code reputation.json}) : compte 100 % au clan, une seule fois par cle quand
     * {@code onceKey} n'est pas nul. {@code ownPeople} remplace la colonne {@code own} (peuple du village concerne).
     */
    public static void collective(MinecraftServer server, Clan clan, String act, @Nullable String ownPeople, @Nullable String onceKey) {
        ClanRepRules.Act a = ClanRepRules.get().collective().get(act);
        if (a == null || clan.solo || !ClanApi.enabled()) {
            return;
        }
        if (onceKey != null) {
            ListTag once = clan.extra.getList(ONCE, Tag.TAG_STRING);
            String key = act + ":" + onceKey;
            for (Tag t : once) {
                if (t.getAsString().equals(key)) {
                    return;
                }
            }
            once.add(StringTag.valueOf(key));
            while (once.size() > ONCE_MAX) {
                once.remove(0);
            }
            clan.extra.put(ONCE, once);
        }
        for (Map.Entry<String, Integer> e : a.deltas().entrySet()) {
            String people = "own".equals(e.getKey()) ? ownPeople : e.getKey();
            if (people != null && PeopleApi.get(server, people) != null) {
                addClan(server, clan, people, e.getValue(), act, a.uncapped());
            }
        }
    }

    /** Le clan a finance des ouvriers d'un village (acte collectif). A appeler par le paiement des ouvriers sur le tresor du clan. */
    public static void workersFunded(MinecraftServer server, Clan clan, String villagePeople, long day) {
        collective(server, clan, "fund_workers", villagePeople, "day" + day + ":" + villagePeople);
    }

    /** Le clan a conclu un accord avec un Colporteur (acte collectif). A appeler par les Colporteurs. */
    public static void peddlerAccord(MinecraftServer server, Clan clan, String key) {
        collective(server, clan, "peddler_accord", PeopleApi.PEDDLERS, key);
    }

    /** Restauration d'une ruine : acte collectif une fois par ruine pour chaque clan qui y a contribue (la part de 50 % n'est pas ajoutee). */
    @SubscribeEvent
    public static void onRestored(RuinEvents.Restored e) {
        RuinSites.Site s = RuinApi.site(e.level, e.site);
        if (s == null || !ClanApi.enabled()) {
            return;
        }
        MinecraftServer server = e.level.getServer();
        ClanData data = ClanData.get(server);
        Set<UUID> done = new HashSet<>();
        for (UUID u : s.contributors.keySet()) {
            Clan c = data.clanOf(u);
            if (c != null && !c.solo && done.add(c.id)) {
                collective(server, c, "restore", null, "site" + e.site);
            }
        }
    }

    // ------------------------------------------------------------------ effets des paliers
    private static String tier(int value) {
        return ReputationApi.tierOf(value).id();
    }

    /**
     * Affinite EFFECTIVE d'un joueur aupres d'un villageois du peuple {@code people} en {@code pos} : affinite personnelle + effet des paliers
     * du clan et de la faction (ou du peuple de joueurs) ; Hostile ne compte que dans {@code hostile_radius} (128) blocs autour du territoire.
     * Cumul borne a +-15, resultat a -100..+100. Sert aux prix et au dialogue, jamais a la reputation d'un village.
     */
    public static int effective(ServerPlayer player, int affinity, String people, BlockPos pos) {
        if (!ClanApi.enabled() || people == null) {
            return affinity;
        }
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        Clan clan = data.clanOf(player.getUUID());
        if (clan == null || clan.solo) {
            return affinity;
        }
        ClanRepRules.Values v = ClanRepRules.get();
        String dim = ClanApi.dim(player.level());
        String ct = tier(clanValue(clan, people));
        int cb = "hostile".equals(ct) && !nearTerritory(List.of(clan), dim, pos, v.hostileRadius()) ? 0 : v.effect(ct);
        int fb = 0;
        Faction f = data.faction(clan.faction);
        if (f != null) {
            String ft = tier(factionValue(server, f, people));
            if (!"hostile".equals(ft) || nearTerritory(clansOf(data, f), dim, pos, v.hostileRadius())) {
                fb = v.effect(ft);
            }
        }
        return ClanRepRules.effective(affinity, v.effectiveCap(), cb, fb);
    }

    private static List<Clan> clansOf(ClanData data, Faction f) {
        List<Clan> out = new ArrayList<>();
        for (UUID id : f.clans) {
            Clan c = data.clan(id);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** Un chunk de l'un de ces clans est-il a moins de {@code radius} blocs de la position ? (lectures de l'index, au plus 17 x 17) */
    static boolean nearTerritory(List<Clan> clans, String dim, BlockPos pos, int radius) {
        if (clans.isEmpty()) {
            return false;
        }
        Set<UUID> ids = new HashSet<>();
        clans.forEach(c -> ids.add(c.id));
        int r = (radius + 15) >> 4;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        long r2 = (long) radius * radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                UUID o = ClaimIndex.owner(dim, ChunkPos.asLong(cx + dx, cz + dz));
                if (o == null || !ids.contains(o)) {
                    continue;
                }
                int x0 = (cx + dx) << 4, z0 = (cz + dz) << 4;
                long ddx = Math.max(0, Math.max(x0 - pos.getX(), pos.getX() - (x0 + 15)));
                long ddz = Math.max(0, Math.max(z0 - pos.getZ(), pos.getZ() - (z0 + 15)));
                if (ddx * ddx + ddz * ddz <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Prix des ouvriers de village pour un membre du clan aupres d'un peuple : x0,8 au palier Allie, prix normal sinon. */
    public static double workerFactor(MinecraftServer server, UUID player, String people) {
        if (!ClanApi.enabled() || server == null || people == null) {
            return 1;
        }
        Clan clan = ClanData.get(server).clanOf(player);
        if (clan == null || clan.solo) {
            return 1;
        }
        return ClanRepRules.get().workers().getOrDefault(tier(clanValue(clan, people)), 1.0);
    }

    public static double workerFactor(ServerPlayer player, String people) {
        return workerFactor(player.getServer(), player.getUUID(), people);
    }

    /** Idem pour le village le plus proche du joueur (ouvriers embauches au Tableau des besoins). */
    public static double workerFactor(ServerPlayer player) {
        VillageData.Village v = VillageData.get(player.serverLevel()).nearest(player.blockPosition(), 128);
        return v == null ? 1 : workerFactor(player, v.people);
    }

    /** Idem, serveur courant (pour un code qui n'a que l'identifiant du joueur). */
    public static double workerFactor(UUID player, String people) {
        return workerFactor(ServerLifecycleHooks.getCurrentServer(), player, people);
    }

    /**
     * Les Colporteurs s'arretent-ils aujourd'hui au Marche du clan ? Allie : un jour sur trois ; Estime : un jour sur cinq ; Hostile : jamais
     * (ils ne passent plus) ; sinon pas d'arret particulier. A appeler par la tournee des Colporteurs.
     */
    public static boolean peddlerStopsToday(MinecraftServer server, Clan clan, long day) {
        return ClanRepRules.peddlerDay(day, peddlerInterval(clan), clan.id.getLeastSignificantBits());
    }

    /** Intervalle des arrets des Colporteurs (jours) ; 0 = aucun arret ; -1 = Hostile, ils evitent le Marche du clan. */
    public static int peddlerInterval(Clan clan) {
        if (clan.solo) {
            return 0;
        }
        String t = tier(clanValue(clan, PeopleApi.PEDDLERS));
        if ("hostile".equals(t)) {
            return -1;
        }
        return ClanRepRules.get().peddlers().getOrDefault(t, 0);
    }

    public static boolean peddlersAvoid(Clan clan) {
        return peddlerInterval(clan) < 0;
    }

    // ------------------------------------------------------------------ estime des villages
    /** Estime du clan pour un village : moyenne, sur ses membres actifs, de la reputation personnelle de chacun aupres de ce village. */
    public static int villageEsteem(Clan clan, VillageData.Village v, long now) {
        int n = 0;
        long sum = 0;
        for (Clan.Member m : clan.members.values()) {
            if (m.active(now)) {
                sum += v.average(m.id);
                n++;
            }
        }
        return n == 0 ? 0 : (int) Math.round(sum / (double) n);
    }

    /**
     * Fiche Reputation du clan (membres) : jauges et paliers par peuple (et le gain du jour), jauges de la faction, derniers mouvements,
     * villages connus d'au moins un membre (32 au plus, les plus estimes d'abord) avec leur estime. Hors du clan : les paliers seulement.
     */
    public static CompoundTag sheet(ServerPlayer viewer, Clan clan) {
        MinecraftServer server = viewer.getServer();
        ClanData data = ClanData.get(server);
        Clan mine = data.clanOf(viewer.getUUID());
        boolean member = mine != null && mine.id.equals(clan.id);
        long day = PioneerApi.day(server);
        CompoundTag t = new CompoundTag();
        ListTag gauges = new ListTag();
        Faction f = data.faction(clan.faction);
        for (PeopleApi.People p : PeopleApi.all(server)) {
            CompoundTag g = new CompoundTag();
            g.putString("people", p.id());
            g.putInt("color", p.color());
            int val = clanValue(clan, p.id());
            g.putString("tier", tier(val));
            if (member) {
                g.putInt("value", val);
                g.putInt("today", clan.repDay == day ? clan.repToday.getOrDefault(p.id(), 0) : 0);
                if (f != null) {
                    int fv = factionValue(server, f, p.id());
                    g.putInt("faction", fv);
                    g.putString("factionTier", tier(fv));
                }
            }
            gauges.add(g);
        }
        t.put("gauges", gauges);
        t.putBoolean("member", member);
        if (!member) {
            return t;
        }
        t.putInt("dailyCap", ClanRepRules.get().dailyCap());
        ListTag log = new ListTag();
        clan.repLog.forEach(x -> log.add(x.copy()));
        t.put("log", log);
        t.putInt("peddlers", peddlerInterval(clan));
        ListTag vl = new ListTag();
        ServerLevel level = server.overworld();
        long now = ClanClock.now();
        List<VillageData.Village> known = new ArrayList<>();
        for (VillageData.Village v : VillageData.get(level).all()) {
            for (UUID u : clan.members.keySet()) {
                if (v.register.values().stream().anyMatch(reg -> reg.containsKey(u))) {
                    known.add(v);
                    break;
                }
            }
        }
        known.sort(Comparator.comparingInt((VillageData.Village v) -> -villageEsteem(clan, v, now)));
        for (VillageData.Village v : known.subList(0, Math.min(32, known.size()))) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", v.id);
            c.putString("people", v.people);
            c.putInt("esteem", villageEsteem(clan, v, now));
            c.putInt("x", v.center.getX());
            c.putInt("z", v.center.getZ());
            vl.add(c);
        }
        t.put("villages", vl);
        return t;
    }
}
