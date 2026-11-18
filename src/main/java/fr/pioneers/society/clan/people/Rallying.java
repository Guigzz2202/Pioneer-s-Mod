package fr.pioneers.society.clan.people;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.faction.FactionRules;
import fr.pioneers.society.clan.faction.FactionZones;
import fr.pioneers.society.clan.faction.Factions;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.society.reputation.ReputationApi;
import fr.pioneers.society.village.VillageData;
import fr.pioneers.society.village.VillagerLore;
import fr.pioneers.society.village.Villages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : ralliement de villages a un peuple de joueurs. Conditions : village d'un peuple de villageois a moins de 200 blocs
 * d'un chunk de la faction ; sa reputation (moyenne de l'affinite de ses villageois) envers le Representant federal d'au moins +60 ; peuple
 * d'origine au palier Estime ou mieux envers le peuple de joueurs ; 5 000 jetons du tresor federal ; 3 jours de jeu de pourparlers affiches
 * sur le Tableau des besoins ; un ralliement par 7 jours de jeu, 6 villages au plus. Effet : {@code VillageData.people} change,
 * {@code previousPeople} garde l'origine ; catalogue, repliques et decor suivent la doctrine (bannieres de la place refaites) ; noms, metiers
 * et affinites intacts ; -40 a la jauge du peuple envers l'origine, une fois. A la dissolution du peuple, chaque village rallie revient.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Rallying {

    /** Cles de {@code Faction.extra} : villages rallies ({dim, id}), dimension du ralliement en cours, jours de pourparlers. */
    public static final String RALLIED = "people.rallied", RALLY_DIM = "people.rallyDim", RALLY_FROM = "people.rallyFrom";

    private Rallying() {
    }

    // ------------------------------------------------------------------ lecture
    public static List<CompoundTag> rallied(Faction f) {
        List<CompoundTag> out = new ArrayList<>();
        for (Tag t : f.extra.getList(RALLIED, Tag.TAG_COMPOUND)) {
            out.add((CompoundTag) t);
        }
        return out;
    }

    public static boolean inProgress(Faction f) {
        return f.rallyUntil >= 0 && f.rallyVillage != 0;
    }

    /** Distance (blocs) du village au chunk le plus proche de la faction dans sa dimension (zones federales comprises). */
    static double distance(MinecraftServer server, Faction f, String dim, VillageData.Village v) {
        double best = Double.MAX_VALUE;
        for (Clan c : Factions.clans(server, f)) {
            Map<Long, Clan.Claim> m = c.claims.get(dim);
            if (m == null) {
                continue;
            }
            for (long chunk : m.keySet()) {
                best = Math.min(best, FactionZones.distance(chunk, v.center));
            }
        }
        return best;
    }

    @Nullable
    static VillageData.Village village(MinecraftServer server, String dim, long id) {
        ServerLevel level = ClanTerritory.level(server, dim);
        return level == null ? null : VillageData.get(level).village(id);
    }

    /** Conditions du ralliement de ce village pour cette faction. */
    public static FactionRules.Rally conditions(MinecraftServer server, Faction f, String dim, VillageData.Village v) {
        PeopleApi.People origin = PeopleApi.get(server, v.people);
        long day = PioneerApi.day(server);
        return new FactionRules.Rally(Peoples.people(server, f) != null, origin != null && origin.villagers(), distance(server, f, dim, v),
              f.representative == null ? 0 : v.average(f.representative), ReputationApi.tierOf(Peoples.gauge(f, v.people)).id(), f.treasury,
              inProgress(f), f.lastRally < 0 ? -1 : day - f.lastRally, rallied(f).size(), EconomyApi.enabled());
    }

    /** Le village le plus proche du joueur (cloche a moins de 96 blocs), pour la commande. */
    @Nullable
    public static VillageData.Village near(ServerPlayer p) {
        return VillageData.get(p.serverLevel()).nearest(p.blockPosition(), Villages.DISCOVERY);
    }

    // ------------------------------------------------------------------ decision federale
    @Nullable
    static String check(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag data) {
        String dim = data.getString("dim");
        VillageData.Village v = village(server, dim, data.getLong("village"));
        if (v == null) {
            return "clan.pioneers.rally.error.no_village";
        }
        return FactionRules.rally(FactionRules.get(), conditions(server, f, dim, v));
    }

    /** Ouvre les pourparlers (decision acquise) : 5 000 jetons detruits, 3 jours de jeu affiches sur le Tableau. */
    static boolean start(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag data) {
        if (check(server, f, null, data) != null) {
            return false;
        }
        FactionRules.Values v = FactionRules.get();
        long cost = EconomyApi.enabled() ? v.rallyCost() : 0;
        if (cost > 0 && !Factions.debit(server, f, cost, "#clan.pioneers.rally.reason", Factions.character(server, author))) {
            return false;
        }
        long day = PioneerApi.day(server);
        f.rallyVillage = data.getLong("village");
        f.rallyUntil = day + v.rallyDays();
        f.extra.putString(RALLY_DIM, data.getString("dim"));
        f.extra.putLong(RALLY_FROM, day);
        VillageData.Village vil = village(server, data.getString("dim"), f.rallyVillage);
        String where = vil == null ? "?" : vil.center.getX() + " " + vil.center.getZ();
        Factions.log(server, f, "clan.pioneers.rally.log.started", where, String.valueOf(v.rallyDays()));
        Factions.tell(server, f, Component.translatable("clan.pioneers.rally.started", where, v.rallyDays()));
        ClanData.get(server).changed();
        return true;
    }

    /** Pourparlers abandonnes (village disparu, peuple dissous) : rien n'est rendu, rien ne change au village. */
    static void cancel(MinecraftServer server, Faction f) {
        if (!inProgress(f)) {
            return;
        }
        f.rallyVillage = 0;
        f.rallyUntil = -1;
        f.extra.remove(RALLY_DIM);
        f.extra.remove(RALLY_FROM);
        Factions.log(server, f, "clan.pioneers.rally.log.cancelled");
        ClanData.get(server).changed();
    }

    /** Passage du jour de jeu : pourparlers arrives a leur terme (conditions revues), le village change de peuple. */
    public static void dayChange(MinecraftServer server, long day) {
        for (Faction f : new ArrayList<>(ClanData.get(server).factions())) {
            if (!inProgress(f)) {
                continue;
            }
            String dim = f.extra.getString(RALLY_DIM);
            VillageData.Village v = village(server, dim, f.rallyVillage);
            PeopleApi.People origin = v == null ? null : PeopleApi.get(server, v.people);
            if (v == null || Peoples.people(server, f) == null || origin == null || !origin.villagers()) {
                Factions.tell(server, f, Component.translatable("clan.pioneers.rally.failed"));
                cancel(server, f);
                continue;
            }
            if (day >= f.rallyUntil) {
                complete(server, f, dim, v, day);
            }
        }
    }

    static void complete(MinecraftServer server, Faction f, String dim, VillageData.Village v, long day) {
        PeopleApi.People pe = Peoples.people(server, f);
        String origin = v.people;
        ServerLevel level = ClanTerritory.level(server, dim);
        if (pe == null || level == null) {
            cancel(server, f);
            return;
        }
        String[] next = FactionRules.rallyPeople(origin, pe.id());
        v.people = next[0];
        v.previousPeople = next[1];
        VillageData.get(level).changed();
        Villages.peopleChanged(level, v);
        ListTag l = f.extra.getList(RALLIED, Tag.TAG_COMPOUND);
        CompoundTag e = new CompoundTag();
        e.putString("dim", dim);
        e.putLong("id", v.id);
        e.putString("from", origin);
        e.putLong("day", day);
        l.add(e);
        f.extra.put(RALLIED, l);
        f.lastRally = day;
        f.rallyVillage = 0;
        f.rallyUntil = -1;
        f.extra.remove(RALLY_DIM);
        f.extra.remove(RALLY_FROM);
        //le peuple d'origine perd un village : -40 envers le peuple de joueurs, une fois
        Peoples.add(server, f, origin, FactionRules.get().rallyPenalty(), "rally", true);
        String where = v.center.getX() + " " + v.center.getZ();
        Factions.log(server, f, "clan.pioneers.rally.log.done", where, "#people.pioneers." + origin);
        Factions.announce(server, Component.translatable("clan.pioneers.rally.done", ReputationApi.peopleName(server, origin), pe.displayName(), where));
        ClanAudit.write(server, "people", null, "ralliement du village " + v.id + " (" + where + ") : " + origin + " -> " + pe.id());
        ClanData.get(server).changed();
    }

    /** Dissolution du peuple : chaque village rallie revient a son peuple d'origine (affinites intactes, decor refait). */
    static void returnAll(MinecraftServer server, Faction f) {
        for (CompoundTag e : rallied(f)) {
            String dim = e.getString("dim");
            ServerLevel level = ClanTerritory.level(server, dim);
            VillageData.Village v = level == null ? null : VillageData.get(level).village(e.getLong("id"));
            if (v == null || !v.people.equals(f.people)) {
                continue;
            }
            String back = v.previousPeople.isEmpty() ? e.getString("from") : v.previousPeople;
            PeopleApi.People p = PeopleApi.get(server, back);
            String[] next = FactionRules.returnPeople(v.people, p != null && p.villagers() ? back : "", f.people, PeopleApi.BARE_HANDS);
            v.people = next[0];
            v.previousPeople = next[1];
            VillageData.get(level).changed();
            Villages.peopleChanged(level, v);
        }
        f.extra.remove(RALLIED);
    }

    // ------------------------------------------------------------------ Tableau des besoins, villageois
    /** Avancement des pourparlers d'un village pour le Tableau des besoins ({@code null} : aucun). */
    @Nullable
    public static CompoundTag board(ServerLevel level, VillageData.Village v) {
        MinecraftServer server = level.getServer();
        if (!ClanCore.enabled()) {
            return null;
        }
        String dim = ClanApi.dim(level);
        for (Faction f : ClanData.get(server).factions()) {
            if (!inProgress(f) || f.rallyVillage != v.id || !dim.equals(f.extra.getString(RALLY_DIM))) {
                continue;
            }
            PeopleApi.People pe = Peoples.people(server, f);
            if (pe == null) {
                return null;
            }
            long day = PioneerApi.day(server);
            long total = Math.max(1, f.rallyUntil - f.extra.getLong(RALLY_FROM));
            CompoundTag t = new CompoundTag();
            t.putString("name", pe.displayName());
            t.putInt("color", pe.color());
            t.putLong("left", Math.max(0, f.rallyUntil - day));
            t.putLong("done", Math.max(0, Math.min(total, day - f.extra.getLong(RALLY_FROM))));
            t.putLong("total", total);
            return t;
        }
        return null;
    }

    /** Un villageois qui (re)charge prend le peuple de son village : catalogue et repliques suivent un ralliement fait chunk decharge. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Villager v) || !(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        VillagerLore lore = VillagerLore.get(v);
        if (!lore.identified || lore.village == 0) {
            return;
        }
        VillageData.Village village = VillageData.get(level).village(lore.village);
        if (village != null && !village.people.equals(lore.people)) {
            lore.people = village.people;
        }
    }

    /** Branche l'affichage du Tableau (appele par {@code FactionBootstrap.init}). */
    public static void install() {
        Villages.boardRally = Rallying::board;
    }
}
