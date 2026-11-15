package fr.pioneers.world.fauna;

import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.world.season.Seasons;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 4, spec §3, §8, decision 15) : peuplement des troncons neufs et repeuplement des zones (Overworld).
 * <ul>
 *     <li><b>generation</b> : un troncon neuf ({@code ChunkEvent.Load} « nouveau ») est mis en file ; au tick suivant, pour chaque lieu
 *     (terre, eau, grottes), tirages « tant que hasard &lt; p » (terre : probabilite de creatures du biome, comme le vanilla) d'une bande
 *     d'une espece du biome (poids x saison) : toutes les especes inscrites se PARTAGENT ce budget ({@link #populate}) ;</li>
 *     <li><b>passage</b> (toutes les {@link FaunaSettings#period} ticks) : les zones ENTIEREMENT chargees (64 troncons, entites comprises)
 *     autour des joueurs ; premiere fois : densite de depart = max(compte, estimation du biome) bornee au plafond (zone d'un monde existant :
 *     drapeau « ancienne ») ; puis {@link FaunaRules#plan} et une bande par groupe sous le seuil ({@link FaunaSpawner#repopulate}).</li>
 * </ul>
 * Les zones d'un passage sont traitees quatre par tick (aucun pic).
 */
public final class FaunaZones {

    private FaunaZones() {
    }

    /** Troncons neufs traites par tick, zones d'un passage traitees par tick. */
    public static final int CHUNKS_PER_TICK = 8, ZONES_PER_TICK = 4;
    /** Bandes au plus par lieu et par troncon a la generation. */
    public static final int MAX_BANDS = 4;

    private static final LongArrayFIFOQueue NEW_CHUNKS = new LongArrayFIFOQueue();
    private static final LongArrayFIFOQueue PASS = new LongArrayFIFOQueue();
    /** Dernier passage de chaque zone (commande de diagnostic) : heure et betes posees par groupe. */
    private static final Long2ObjectOpenHashMap<long[]> LAST = new Long2ObjectOpenHashMap<>();

    static void clear() {
        NEW_CHUNKS.clear();
        PASS.clear();
        LAST.clear();
    }

    static void chunkGenerated(ServerLevel level, ChunkPos pos) {
        NEW_CHUNKS.enqueue(pos.toLong());
        FaunaData.get(level).freshChunk(FaunaRules.zoneOfChunk(pos.x, pos.z));
    }

    /** Tick de l'Overworld (fin de tick). */
    static void tick(ServerLevel level, FaunaSettings s) {
        for (int i = 0; i < CHUNKS_PER_TICK && !NEW_CHUNKS.isEmpty(); i++) {
            ChunkPos c = new ChunkPos(NEW_CHUNKS.dequeueLong());
            if (level.hasChunk(c.x, c.z)) {
                populate(level, c.x, c.z, level.random);
            }
        }
        FaunaData data = FaunaData.get(level);
        long now = level.getGameTime();
        if (data.nextPass() == Long.MIN_VALUE || data.nextPass() > now + s.period()) {
            data.setNextPass(now + s.period());
        }
        if (now >= data.nextPass()) {
            data.setNextPass(now + s.period());
            FaunaCounter.of(level).reconcile();
            for (long z : loadedZones(level)) {
                PASS.enqueue(z);
            }
        }
        for (int i = 0; i < ZONES_PER_TICK && !PASS.isEmpty(); i++) {
            long z = PASS.dequeueLong();
            if (fullyLoaded(level, z)) {
                pass(level, z, s, Limits.REAL, null);
            }
        }
    }

    // ------------------------------------------------------------------ generation

    /** Peuplement d'un troncon neuf (rend les betes posees). */
    public static int populate(ServerLevel level, int cx, int cz, RandomSource r) {
        FaunaSpawner.Area area = FaunaSpawner.Area.chunk(cx, cz);
        int x = (cx << 4) + 8, z = (cz << 4) + 8, season = season(level), placed = 0;
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z));
        for (FaunaSpecies.Where where : FaunaSpecies.Where.values()) {
            List<FaunaPools.Entry> pool = FaunaPools.of(biome, where);
            if (pool.isEmpty()) {
                continue;
            }
            double p = FaunaPools.chance(biome, where);
            for (int b = 0; b < MAX_BANDS && r.nextFloat() < p; b++) {
                FaunaPools.Entry e = FaunaSpawner.pick(pool, season, false, r);
                if (e == null) {
                    break;
                }
                int bx = area.randomX(r), bz = area.randomZ(r);
                BlockPos at = FaunaSpawner.spot(level, e.where(), e.type(), bx, bz, r);
                if (at == null) {
                    continue;
                }
                int n = e.min() + r.nextInt(e.max() - e.min() + 1);
                placed += FaunaSpawner.spawnBand(level, e, area, at, n, FaunaSpawner.Limits.NONE, MobSpawnType.CHUNK_GENERATION, r, null);
            }
        }
        return placed;
    }

    // ------------------------------------------------------------------ passage

    /** Les regards a prendre en compte : les vrais joueurs, ou (GameTests) des regards donnes. */
    public record Limits(@Nullable List<FaunaSpawner.Viewer> viewers, @Nullable FaunaSpawner.Area area) {

        public static final Limits REAL = new Limits(null, null);
    }

    /** Resultat d'un passage sur une zone. */
    public record Pass(long zone, boolean initialised, boolean legacy, int[] counts, int[] starts, List<FaunaGroup> planned, int[] placed,
                       List<Entity> spawned, int[] born) {
    }

    /**
     * Passage sur une zone : densite de depart a la premiere fois, plan, une bande par groupe sous le seuil. {@code limits} : regards et
     * aire factices (GameTests) ou REAL (joueurs du monde, zone entiere).
     */
    public static Pass pass(ServerLevel level, long zone, FaunaSettings s, Limits limits, @Nullable int[] forcedStarts) {
        FaunaData data = FaunaData.get(level);
        FaunaCounter counter = FaunaCounter.of(level);
        int[] counts = counter.groups(zone);
        boolean init = false;
        if (forcedStarts != null) {
            data.put(zone, forcedStarts, data.legacy(zone));
        } else if (!data.known(zone)) {
            double[] est = estimate(level, zone);
            int[] starts = new int[FaunaGroup.COUNT];
            for (FaunaGroup g : FaunaGroup.ALL) {
                starts[g.ordinal()] = FaunaRules.start(counts[g.ordinal()], est[g.ordinal()], s.cap(g));
            }
            data.put(zone, starts, data.fresh(zone) < FaunaRules.FRESH_CHUNKS);
            init = true;
        }
        int[] starts = data.starts(zone);
        boolean legacy = data.legacy(zone);
        int season = season(level);
        List<FaunaGroup> plan = FaunaRules.plan(counts, starts, s.caps(), season, legacy, s.threshold());
        int[] placed = new int[FaunaGroup.COUNT];
        List<Entity> spawned = new ArrayList<>();
        FaunaSpawner.Limits l = limits.viewers() == null ? FaunaSpawner.Limits.repopulation(level, s)
                                                         : new FaunaSpawner.Limits(limits.viewers(), s.playerDistance(), s.villageDistance(), true,
                                                               FaunaSpawner.viewRange(level));
        FaunaSpawner.Area area = limits.area() != null ? limits.area() : FaunaSpawner.Area.zone(zone);
        for (FaunaGroup g : plan) {
            boolean fields = g == FaunaGroup.BIRD && hasFields(level, area);
            int target = FaunaRules.target(starts[g.ordinal()], g, season);
            placed[g.ordinal()] = FaunaSpawner.repopulate(level, g, area, l, season, fields, counts[g.ordinal()], target, s.cap(g), level.random,
                  spawned);
        }
        //1.0.5-beta (lot 6) : reproduction sauvage lente des groupes entre le seuil et leur densite de depart (pas ceux repeuples ce passage)
        int[] born = new int[FaunaGroup.COUNT];
        if (s.wildBreeding() && s.wildBreedChance() > 0) {
            FaunaSpawner.Limits bl = new FaunaSpawner.Limits(l.viewers(), s.wildBreedDistance(), -1, true, l.viewRange());
            for (FaunaGroup g : FaunaGroup.ALL) {
                int i = g.ordinal();
                if (plan.contains(g)) {
                    continue;
                }
                int room = FaunaRules.breedRoom(counts[i], starts[i], FaunaRules.target(starts[i], g, season), s.cap(g), s.threshold());
                int n = room <= 0 ? 0 : FaunaRules.births(counts[i], room, s.wildBreedChance(), level.random::nextDouble);
                if (n > 0) {
                    born[i] = FaunaSpawner.breed(level, zone, area, g, n, bl, level.random, spawned);
                }
            }
        }
        long[] last = new long[1 + FaunaGroup.COUNT];
        last[0] = level.getGameTime();
        for (int i = 0; i < FaunaGroup.COUNT; i++) {
            last[1 + i] = placed[i] + born[i];
        }
        LAST.put(zone, last);
        return new Pass(zone, init, legacy, counts, starts, plan, placed, spawned, born);
    }

    /** Dernier passage (heure, betes posees par groupe) ; null : aucun depuis le demarrage. */
    @Nullable
    public static long[] last(long zone) {
        return LAST.get(zone);
    }

    /** Saison du monde (0 printemps .. 3 hiver ; printemps hors calendrier). */
    public static int season(ServerLevel level) {
        Seasons.Date d = Seasons.calendar(level);
        return d == null ? 0 : d.season().ordinal();
    }

    /** Zones autour des joueurs (distance de vue), sans doublon. */
    static long[] loadedZones(ServerLevel level) {
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        int reach = FaunaSpawner.viewRange(level);
        for (ServerPlayer p : level.players()) {
            int x0 = (p.getBlockX() - reach) >> FaunaRules.ZONE_SHIFT, x1 = (p.getBlockX() + reach) >> FaunaRules.ZONE_SHIFT;
            int z0 = (p.getBlockZ() - reach) >> FaunaRules.ZONE_SHIFT, z1 = (p.getBlockZ() + reach) >> FaunaRules.ZONE_SHIFT;
            for (int zx = x0; zx <= x1; zx++) {
                for (int zz = z0; zz <= z1; zz++) {
                    out.add(FaunaRules.pack(zx, zz));
                }
            }
        }
        return out.toLongArray();
    }

    /** Les 64 troncons de la zone sont charges, entites comprises (sinon le compte serait faux). */
    public static boolean fullyLoaded(ServerLevel level, long zone) {
        int cx0 = FaunaRules.zoneX(zone) * FaunaRules.ZONE_CHUNKS, cz0 = FaunaRules.zoneZ(zone) * FaunaRules.ZONE_CHUNKS;
        for (int dx = 0; dx < FaunaRules.ZONE_CHUNKS; dx++) {
            for (int dz = 0; dz < FaunaRules.ZONE_CHUNKS; dz++) {
                if (!level.hasChunk(cx0 + dx, cz0 + dz) || !level.areEntitiesLoaded(ChunkPos.asLong(cx0 + dx, cz0 + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Estimation de la densite d'une zone deja exploree (spec §3 « estimee a la premiere visite ») : 16 colonnes (une par carre de 2 x 2
     * troncons), pour chaque lieu, bandes attendues x part de chaque espece x bande moyenne x part placee ({@link FaunaRules#expected}) ;
     * l'eau et les grottes ne comptent que si la colonne a de l'eau.
     */
    public static double[] estimate(ServerLevel level, long zone) {
        double[] out = new double[FaunaGroup.COUNT];
        int ox = FaunaRules.origin(FaunaRules.zoneX(zone)), oz = FaunaRules.origin(FaunaRules.zoneZ(zone)), season = season(level);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int x = ox + 16 + i * 32, z = oz + 16 + j * 32;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                Holder<Biome> biome = level.getBiome(new BlockPos(x, top, z));
                boolean water = level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER);
                for (FaunaSpecies.Where where : FaunaSpecies.Where.values()) {
                    if (where == FaunaSpecies.Where.WATER && !water) {
                        continue;
                    }
                    List<FaunaPools.Entry> pool = FaunaPools.of(biome, where);
                    double total = 0;
                    for (FaunaPools.Entry e : pool) {
                        total += e.weight() * e.species().season(season);
                    }
                    double p = FaunaPools.chance(biome, where), success = where == FaunaSpecies.Where.LAND ? FaunaRules.SUCCESS_LAND
                                                                                                         : where == FaunaSpecies.Where.WATER
                                                                                                           ? FaunaRules.SUCCESS_WATER
                                                                                                           : FaunaRules.SUCCESS_CAVE;
                    for (FaunaPools.Entry e : pool) {
                        out[e.species().group.ordinal()] += 4 * FaunaRules.expected(p, e.weight() * e.species().season(season), total, e.min(),
                              e.max(), success);
                    }
                }
            }
        }
        return out;
    }

    /** L'aire contient-elle au moins {@link fr.pioneers.farm.crow.CrowSpawner#MIN_CROPS} cultures du mod (tuiles des troncons charges) ? */
    public static boolean hasFields(ServerLevel level, FaunaSpawner.Area area) {
        int n = 0;
        for (int cx = area.x0() >> 4; cx <= area.x1() >> 4; cx++) {
            for (int cz = area.z0() >> 4; cz <= area.z1() >> 4; cz++) {
                LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
                if (c == null) {
                    continue;
                }
                for (BlockEntity be : c.getBlockEntities().values()) {
                    if (be instanceof CropTile && ++n >= fr.pioneers.farm.crow.CrowSpawner.MIN_CROPS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
