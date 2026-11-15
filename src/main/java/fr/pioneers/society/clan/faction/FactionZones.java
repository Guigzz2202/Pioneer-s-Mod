package fr.pioneers.society.clan.faction;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.territory.ClanChunkloading;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.territory.TerritoryDefs;
import fr.pioneers.society.clan.territory.TerritoryRules;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.world.ruins.RuinSites;
import fr.pioneers.society.village.VillageData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * 1.9 (MAJ RP, bloc 4) : zones federales et chunkloading federal. Le Representant federal reclame des zones dans un budget de 20 chunks par
 * niveau de faction ; une zone touche le territoire de la faction (cote ou coin), jamais celui d'un autre clan, ni l'apparition, ni une
 * cloche, ni une ruine intacte (memes regles que les claims). Elle est inscrite chez le clan du Representant ({@code Claim.federal}, hors de
 * son budget) par l'API du territoire ({@link ClanTerritory#put}) : tous les clans de la faction y font tout. Emplacements de chunkloading
 * federaux (1 + niveau / 2) dans les zones federales, payes chaque jour de jeu par le tresor federal (tickets au nom de la faction, remis au
 * demarrage du serveur).
 */
public final class FactionZones {

    /** Cles de {@code Faction.extra} : zones (« dimension|chunk »), emplacements charges, chunkloading suspendu faute de paiement. */
    public static final String ZONES = "fed.zones", LOADED = "fed.load", SUSPENDED = "fed.loadOff";

    private FactionZones() {
    }

    // ------------------------------------------------------------------ lecture
    public static List<String> zones(Faction f) {
        List<String> out = new ArrayList<>();
        for (Tag t : f.extra.getList(ZONES, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    public static List<String> loaded(Faction f) {
        List<String> out = new ArrayList<>();
        for (Tag t : f.extra.getList(LOADED, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    private static void write(Faction f, String key, List<String> list) {
        ListTag l = new ListTag();
        list.forEach(s -> l.add(StringTag.valueOf(s)));
        f.extra.put(key, l);
    }

    /**
     * Retire de la liste les zones qui ne sont plus tenues par un clan de la faction (chunk capture, clan dissous dont les claims sont partis
     * avant que la faction n'ait pu les reprendre) ; leurs emplacements de chunkloading sont leves. Renvoie la liste a jour.
     */
    public static List<String> prune(MinecraftServer server, Faction f) {
        List<String> zones = zones(f);
        boolean changed = zones.removeIf(k -> {
            int bar = k.indexOf('|');
            return bar < 0 || holder(server, f, k.substring(0, bar), Long.parseLong(k.substring(bar + 1))) == null;
        });
        if (changed) {
            write(f, ZONES, zones);
            for (String key : loaded(f)) {
                if (!zones.contains(key)) {
                    unload(server, f, key);
                }
            }
            ClanData.get(server).changed();
        }
        return zones;
    }

    public static int budget(MinecraftServer server, Faction f) {
        return FactionRules.zoneBudget(FactionRules.get(), Factions.level(server, f));
    }

    public static int slots(MinecraftServer server, Faction f) {
        return FactionRules.chunkloadSlots(FactionRules.get(), Factions.level(server, f));
    }

    /** Clan qui tient la zone federale (son claim marque federal), ou {@code null}. */
    @Nullable
    static Clan holder(MinecraftServer server, Faction f, String dim, long chunk) {
        for (Clan c : Factions.clans(server, f)) {
            Clan.Claim x = c.claim(dim, chunk);
            if (x != null && x.federal) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ reclamer, abandonner
    /** {@code /faction zone claim} : le chunk ou se tient le Representant federal. Cle du refus ou {@code null}. */
    @Nullable
    public static String claim(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        Clan mine = ClanData.get(server).clanOf(p.getUUID());
        Faction f = Factions.of(server, mine);
        if (f == null) {
            return "clan.pioneers.faction.error.none";
        }
        if (!p.getUUID().equals(f.representative)) {
            return "clan.pioneers.faction.error.representative_only";
        }
        ServerLevel level = p.serverLevel();
        String dim = ClanApi.dim(level);
        long chunk = p.chunkPosition().toLong();
        List<String> zones = prune(server, f);
        int budget = budget(server, f);
        Set<UUID> ours = new HashSet<>(f.clans);
        Object group = f.id;
        TerritoryDefs d = TerritoryDefs.get();
        TerritoryRules.Verdict v = TerritoryRules.canClaim(chunk, group, c -> {
                  UUID o = ClaimIndex.owner(dim, c);
                  return o == null ? null : ours.contains(o) ? group : o;
              }, c -> ours.contains(ClaimIndex.owner(dim, c)) ? Boolean.FALSE : null, true, 0, 0, 2, zones.size(), budget,
              nearSpawn(level, chunk, d.spawnChunks), nearBell(level, chunk, d.bellBlocks), ruin(level, chunk));
        return switch (v) {
            case OK -> {
                Clan.Claim c = new Clan.Claim();
                c.federal = true;
                c.zone = Clan.Zone.MEMBERS;
                ClanTerritory.put(server, mine, dim, chunk, c);
                zones.add(ClanTerritory.key(dim, chunk));
                write(f, ZONES, zones);
                Factions.log(server, f, "clan.pioneers.faction.log.zone", ClanMessages.character(p), ClanTerritory.coords(chunk));
                ClanMessages.tell(p, "clan.pioneers.faction.zone_claimed", zones.size(), budget);
                ClanData.get(server).changed();
                yield null;
            }
            case BUDGET -> "clan.pioneers.faction.zone.budget";
            case TAKEN, OWN -> "clan.pioneers.faction.zone.taken";
            case TOUCHES_OTHER -> "clan.pioneers.faction.zone.touches_other";
            case NEAR_SPAWN -> "clan.pioneers.faction.zone.spawn";
            case NEAR_BELL -> "clan.pioneers.faction.zone.bell";
            case RUIN -> "clan.pioneers.faction.zone.ruin";
            default -> "clan.pioneers.faction.zone.adjacent";
        };
    }

    /** {@code /faction zone unclaim} : abandonne la zone federale ou se tient le Representant federal. */
    @Nullable
    public static String unclaim(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        Faction f = Factions.ofPlayer(server, p.getUUID());
        if (f == null) {
            return "clan.pioneers.faction.error.none";
        }
        if (!p.getUUID().equals(f.representative)) {
            return "clan.pioneers.faction.error.representative_only";
        }
        String dim = ClanApi.dim(p.level());
        long chunk = p.chunkPosition().toLong();
        Clan h = holder(server, f, dim, chunk);
        if (h == null) {
            return "clan.pioneers.faction.zone.not_ours";
        }
        drop(server, f, h, dim, chunk);
        Factions.log(server, f, "clan.pioneers.faction.log.unzone", ClanMessages.character(p), ClanTerritory.coords(chunk));
        ClanMessages.tell(p, "clan.pioneers.faction.zone_released", zones(f).size(), budget(server, f));
        return null;
    }

    private static void drop(MinecraftServer server, Faction f, Clan holder, String dim, long chunk) {
        String key = ClanTerritory.key(dim, chunk);
        unload(server, f, key);
        ClanTerritory.drop(server, holder, dim, chunk, true);
        List<String> zones = zones(f);
        zones.remove(key);
        write(f, ZONES, zones);
        ClanData.get(server).changed();
    }

    /**
     * Guerre federale : un chunk pris hors du budget du clan qui l'a capture va a la reserve des zones federales de la faction attaquante,
     * si elle a la place ({@link FactionRules#capture}). Vrai : la zone est inscrite (le siege pose le claim federal chez ce clan).
     */
    public static boolean reserve(MinecraftServer server, Clan taker, @Nullable UUID attacker, boolean federalWar, String dim, long chunk) {
        Faction f = Factions.of(server, taker);
        boolean federal = federalWar && f != null && f.id.equals(attacker);
        List<String> zones = f == null ? List.of() : prune(server, f);
        if (FactionRules.capture(false, federal, zones.size(), f == null ? 0 : budget(server, f)) != FactionRules.Capture.RESERVE) {
            return false;
        }
        List<String> l = new ArrayList<>(zones);
        l.add(ClanTerritory.key(dim, chunk));
        write(f, ZONES, l);
        Factions.log(server, f, "clan.pioneers.faction.log.reserve", taker.name, ClanTerritory.coords(chunk));
        ClanData.get(server).changed();
        return true;
    }

    /** Le clan qui tenait des zones federales quitte la faction : elles passent au clan du Representant federal (sinon au premier clan). */
    static void holderLeft(MinecraftServer server, Faction f, Clan left) {
        List<Clan> rest = Factions.clans(server, f);
        rest.removeIf(c -> c.id.equals(left.id));
        Clan next = null;
        for (Clan c : rest) {
            if (f.representative != null && c.members.containsKey(f.representative)) {
                next = c;
            }
        }
        if (next == null && !rest.isEmpty()) {
            next = rest.get(0);
        }
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : new ArrayList<>(left.claims.entrySet())) {
            for (Map.Entry<Long, Clan.Claim> x : new ArrayList<>(e.getValue().entrySet())) {
                if (!x.getValue().federal) {
                    continue;
                }
                String dim = e.getKey();
                long chunk = x.getKey();
                if (next == null) {
                    drop(server, f, left, dim, chunk);
                } else {
                    Clan.Claim moved = x.getValue();
                    ClanTerritory.drop(server, left, dim, chunk, true);
                    ClanTerritory.put(server, next, dim, chunk, moved);
                }
            }
        }
    }

    /** Faction dissoute : toutes ses zones et ses emplacements federaux sont leves. */
    static void releaseAll(MinecraftServer server, Faction f) {
        for (String key : loaded(f)) {
            force(server, f.id, key, false);
        }
        write(f, LOADED, List.of());
        for (String key : zones(f)) {
            int bar = key.indexOf('|');
            String dim = key.substring(0, bar);
            long chunk = Long.parseLong(key.substring(bar + 1));
            Clan h = holder(server, f, dim, chunk);
            if (h != null) {
                ClanTerritory.drop(server, h, dim, chunk, true);
            }
        }
        write(f, ZONES, List.of());
    }

    // ------------------------------------------------------------------ regles reprises du territoire (memes valeurs)
    private static boolean nearSpawn(ServerLevel level, long chunk, int chunks) {
        if (level.dimension() != Level.OVERWORLD || chunks <= 0) {
            return false;
        }
        BlockPos s = level.getSharedSpawnPos();
        int dx = Math.abs(TerritoryRules.cx(chunk) - (s.getX() >> 4)), dz = Math.abs(TerritoryRules.cz(chunk) - (s.getZ() >> 4));
        return Math.max(dx, dz) < chunks;
    }

    /** Distance horizontale d'un point au carre du chunk (0 dedans). */
    public static double distance(long chunk, BlockPos p) {
        int x0 = TerritoryRules.cx(chunk) << 4, z0 = TerritoryRules.cz(chunk) << 4;
        double dx = Math.max(0, Math.max(x0 - p.getX(), p.getX() - (x0 + 15)));
        double dz = Math.max(0, Math.max(z0 - p.getZ(), p.getZ() - (z0 + 15)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean nearBell(ServerLevel level, long chunk, int blocks) {
        if (blocks <= 0) {
            return false;
        }
        BlockPos center = new BlockPos((TerritoryRules.cx(chunk) << 4) + 8, 64, (TerritoryRules.cz(chunk) << 4) + 8);
        for (VillageData.Village v : VillageData.get(level).within(center, blocks + 12)) {
            if (distance(chunk, v.center) < blocks) {
                return true;
            }
        }
        return level.getPoiManager().getInSquare(t -> t.is(PoiTypes.MEETING), center, blocks + 12, PoiManager.Occupancy.ANY)
              .anyMatch(r -> distance(chunk, r.getPos()) < blocks);
    }

    private static boolean ruin(ServerLevel level, long chunk) {
        int x0 = TerritoryRules.cx(chunk) << 4, z0 = TerritoryRules.cz(chunk) << 4;
        for (RuinSites.Site s : RuinSites.get(level).all()) {
            if (s.state != RuinSites.State.INTACT && s.state != RuinSites.State.RESTORING) {
                continue;
            }
            if (s.min.getX() <= x0 + 15 && s.max.getX() >= x0 && s.min.getZ() <= z0 + 15 && s.max.getZ() >= z0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ chunkloading federal
    /** Chunks charges par toutes les factions (hors chunkloading suspendu). */
    public static int globalActive(MinecraftServer server) {
        int n = 0;
        for (Faction f : ClanData.get(server).factions()) {
            if (!f.extra.getBoolean(SUSPENDED)) {
                n += loaded(f).size();
            }
        }
        return n;
    }

    /** {@code /faction zone load} : garde la zone federale ou se tient le Representant federal chargee en permanence. */
    @Nullable
    public static String load(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        Faction f = Factions.ofPlayer(server, p.getUUID());
        if (f == null) {
            return "clan.pioneers.faction.error.none";
        }
        if (!p.getUUID().equals(f.representative)) {
            return "clan.pioneers.faction.error.representative_only";
        }
        String dim = ClanApi.dim(p.level());
        long chunk = p.chunkPosition().toLong();
        String key = ClanTerritory.key(dim, chunk);
        if (holder(server, f, dim, chunk) == null) {
            return "clan.pioneers.faction.zone.not_ours";
        }
        if (!ClanChunkloading.dimensionAllowed(dim)) {
            return "clan.pioneers.chunkload.dimension";
        }
        List<String> l = loaded(f);
        if (l.contains(key)) {
            return "clan.pioneers.chunkload.already";
        }
        int slots = slots(server, f);
        if (l.size() >= slots) {
            return "clan.pioneers.faction.zone.no_slot";
        }
        long cost = EconomyApi.enabled() ? TerritoryDefs.get().chunkloadCost : 0;
        if (cost > 0 && f.treasury < cost) {
            return "clan.pioneers.faction.zone.no_funds";
        }
        if (ClanChunkloading.globalActive(server) + globalActive(server) >= ClanChunkloading.globalMax()) {
            return "clan.pioneers.faction.zone.server_full";
        }
        l.add(key);
        write(f, LOADED, l);
        if (!f.extra.getBoolean(SUSPENDED)) {
            force(server, f.id, key, true);
        }
        Factions.log(server, f, "clan.pioneers.faction.log.load", ClanMessages.character(p), ClanTerritory.coords(chunk));
        ClanMessages.tell(p, "clan.pioneers.faction.zone_loaded", l.size(), slots, cost);
        ClanData.get(server).changed();
        return null;
    }

    @Nullable
    public static String unload(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        Faction f = Factions.ofPlayer(server, p.getUUID());
        if (f == null) {
            return "clan.pioneers.faction.error.none";
        }
        if (!p.getUUID().equals(f.representative)) {
            return "clan.pioneers.faction.error.representative_only";
        }
        String key = ClanTerritory.key(ClanApi.dim(p.level()), p.chunkPosition().toLong());
        if (!unload(server, f, key)) {
            return "clan.pioneers.chunkload.not_loaded";
        }
        ClanMessages.tell(p, "clan.pioneers.chunkload.unloaded");
        return null;
    }

    static boolean unload(MinecraftServer server, Faction f, String key) {
        List<String> l = loaded(f);
        if (!l.remove(key)) {
            return false;
        }
        write(f, LOADED, l);
        if (!f.extra.getBoolean(SUSPENDED)) {
            force(server, f.id, key, false);
        }
        ClanData.get(server).changed();
        return true;
    }

    /** Passage du jour de jeu : chaque emplacement federal coute 200 jetons au tresor federal ; impaye, le chunkloading s'arrete jusqu'au jour paye. */
    public static void dayChange(MinecraftServer server) {
        long per = EconomyApi.enabled() ? TerritoryDefs.get().chunkloadCost : 0;
        for (Faction f : ClanData.get(server).factions()) {
            List<String> l = loaded(f);
            if (l.isEmpty()) {
                continue;
            }
            long cost = per * l.size();
            boolean paid = cost <= 0 || Factions.debit(server, f, cost, "#clan.pioneers.faction.reason.chunkload", "#clan.pioneers.vote.system");
            boolean off = f.extra.getBoolean(SUSPENDED);
            if (paid && off) {
                f.extra.remove(SUSPENDED);
                l.forEach(k -> force(server, f.id, k, true));
                Factions.tell(server, f, Component.translatable("clan.pioneers.faction.chunkload_resumed"));
            } else if (!paid && !off) {
                f.extra.putBoolean(SUSPENDED, true);
                l.forEach(k -> force(server, f.id, k, false));
                Factions.tell(server, f, Component.translatable("clan.pioneers.faction.chunkload_stopped", cost));
            }
        }
        ClanData.get(server).changed();
    }

    /** Demarrage du serveur : les tickets federaux (ecartes par la validation des clans au chargement du monde) sont remis. */
    public static void restore(MinecraftServer server) {
        for (Faction f : ClanData.get(server).factions()) {
            if (f.extra.getBoolean(SUSPENDED)) {
                continue;
            }
            List<String> l = loaded(f);
            l.removeIf(k -> {
                int bar = k.indexOf('|');
                return bar < 0 || holder(server, f, k.substring(0, bar), Long.parseLong(k.substring(bar + 1))) == null;
            });
            write(f, LOADED, l);
            l.forEach(k -> force(server, f.id, k, true));
        }
    }

    static void force(MinecraftServer server, UUID owner, String key, boolean add) {
        int bar = key.indexOf('|');
        if (bar < 0) {
            return;
        }
        ServerLevel level = ClanTerritory.level(server, key.substring(0, bar));
        if (level == null) {
            return;
        }
        long chunk = Long.parseLong(key.substring(bar + 1));
        ForgeChunkManager.forceChunk(level, Pioneers.MODID, owner, TerritoryRules.cx(chunk), TerritoryRules.cz(chunk), add, true);
    }
}
