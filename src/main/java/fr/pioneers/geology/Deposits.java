package fr.pioneers.geology;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Gisements virtuels : le monde est decoupe en regions de 8x8 chunks ; 7 sur 10 cachent un gisement de 3x3 ou 5x5 chunks, dont le type depend
 * du biome a son centre (lu dans la source de biomes, sans charger de chunk). Tout est tire de la graine du monde : meme resultat a chaque
 * appel, en jeu comme a la generation des affleurements.
 */
public final class Deposits {

    public static final int REGION = 8;
    public static final float CHANCE = 0.7F;

    /** Un gisement : son type, son centre (chunk), son rayon (1 ou 2 chunks), sa region et sa reserve totale. */
    public record Deposit(DepositType type, int centerX, int centerZ, int radius, long region, int reserve) {

        public boolean contains(int chunkX, int chunkZ) {
            return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
        }

        public int left(ServerLevel level) {
            return Math.max(0, reserve - DepositData.get(level).mined(region));
        }
    }

    private static final Deposit NONE = new Deposit(null, 0, 0, -1, 0, 0);
    private static final Map<ResourceKey<Level>, Map<Long, Deposit>> CACHE = new ConcurrentHashMap<>();

    private Deposits() {
    }

    static void clearCaches() {
        CACHE.clear();
    }

    public static long regionKey(int rx, int rz) {
        return ChunkPos.asLong(rx, rz);
    }

    /** Le gisement sous ce chunk, ou null. */
    @Nullable
    public static Deposit at(ServerLevel level, int chunkX, int chunkZ) {
        Deposit d = region(level, Math.floorDiv(chunkX, REGION), Math.floorDiv(chunkZ, REGION));
        return d != null && d.contains(chunkX, chunkZ) ? d : null;
    }

    @Nullable
    public static Deposit region(ServerLevel level, int rx, int rz) {
        Map<Long, Deposit> map = CACHE.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        long key = regionKey(rx, rz);
        Deposit d = map.get(key);
        if (d == null) {
            d = compute(level, rx, rz, key);
            if (map.size() > 20_000) {
                map.clear();
            }
            map.put(key, d);
        }
        return d == NONE ? null : d;
    }

    private static Deposit compute(ServerLevel level, int rx, int rz, long key) {
        List<DepositType> types = DepositTypes.all();
        if (types.isEmpty()) {
            return NONE;
        }
        long seed = level.getSeed() ^ rx * 341_873_128_712L ^ rz * 132_897_987_541L ^ level.dimension().location().hashCode() * 0x9E3779B97F4A7C15L;
        Random r = new Random(seed);
        if (r.nextFloat() >= CHANCE) {
            return NONE;
        }
        int radius = 1 + r.nextInt(2);
        int cx = rx * REGION + radius + r.nextInt(REGION - 2 * radius);
        int cz = rz * REGION + radius + r.nextInt(REGION - 2 * radius);
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome((cx << 2) + 2, 16, (cz << 2) + 2,
              level.getChunkSource().randomState().sampler());
        int total = 0;
        int[] weights = new int[types.size()];
        for (int i = 0; i < types.size(); i++) {
            DepositType t = types.get(i);
            weights[i] = t.dimension() == level.dimension() ? t.weight(biome) : 0;
            total += weights[i];
        }
        if (total == 0) {
            for (int i = 0; i < types.size(); i++) {
                DepositType t = types.get(i);
                weights[i] = t.dimension() == level.dimension() ? t.fallback() : 0;
                total += weights[i];
            }
        }
        if (total == 0) {
            return NONE;
        }
        int pick = r.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            pick -= weights[i];
            if (pick < 0) {
                DepositType t = types.get(i);
                int reserve = t.reserveMin() + r.nextInt(t.reserveMax() - t.reserveMin() + 1);
                return new Deposit(t, cx, cz, radius, key, reserve);
            }
        }
        return NONE;
    }

    /** Le gisement le plus proche (chunk le plus proche d'un gisement) dans un rayon de {@code blocks} ; null s'il n'y en a pas. */
    @Nullable
    public static Nearest nearest(ServerLevel level, BlockPos pos, int blocks) {
        int range = (blocks >> 4) + 1;
        int pcx = pos.getX() >> 4, pcz = pos.getZ() >> 4;
        Nearest best = null;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                Deposit d = at(level, pcx + dx, pcz + dz);
                if (d == null) {
                    continue;
                }
                //Point du chunk le plus proche du joueur
                int x0 = (pcx + dx) << 4, z0 = (pcz + dz) << 4;
                int nx = Math.max(x0, Math.min(x0 + 15, pos.getX())), nz = Math.max(z0, Math.min(z0 + 15, pos.getZ()));
                double dist = Math.sqrt((nx - pos.getX()) * (double) (nx - pos.getX()) + (nz - pos.getZ()) * (double) (nz - pos.getZ()));
                if (dist <= blocks && (best == null || dist < best.distance)) {
                    best = new Nearest(d, nx, nz, dist);
                }
            }
        }
        return best;
    }

    public record Nearest(Deposit deposit, int x, int z, double distance) {
    }
}
