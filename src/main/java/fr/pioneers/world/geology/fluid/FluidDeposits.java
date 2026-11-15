package fr.pioneers.world.geology.fluid;

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
 * Gisements fluides virtuels (petrole, gaz, saumure) : le monde est decoupe en regions de 12x12 chunks ; une region dont le biome central
 * accepte un type (oceans et deserts pour le petrole et le gaz) cache 6 fois sur 10 un gisement de 5x5 a 7x7 chunks. Tout est tire de la
 * graine du monde : meme resultat en jeu et a la generation des mares et suintements de petrole (la partie « reelle » du gisement).
 * <p>
 * Pression : un gisement neuf debite a plein ; plus on le vide, plus son debit baisse (25 % a sec, jamais nul tant qu'il reste du fluide).
 * L'injection d'eau ou de vapeur par le puits le remet a pleine pression (recuperation secondaire).
 */
public final class FluidDeposits {

    public static final int REGION = 12;
    public static final float CHANCE = 0.6F;

    /** Un gisement : type, centre (chunk), rayon (2 ou 3 chunks), region, reserve totale (mB). */
    public record Deposit(FluidDepositType type, int centerX, int centerZ, int radius, long region, long reserve) {

        public boolean contains(int chunkX, int chunkZ) {
            return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
        }

        public long left(ServerLevel level) {
            return Math.max(0, reserve - FluidDepositData.get(level).extracted(region));
        }

        /** Pression relative (0,25 a sec .. 1 neuf). */
        public double pressure(ServerLevel level) {
            return 0.25 + 0.75 * left(level) / (double) Math.max(1, reserve);
        }

        /** Retire jusqu'a {@code wanted} mB du gisement ; renvoie ce qui a ete pris. */
        public long extract(ServerLevel level, long wanted) {
            long take = Math.min(wanted, left(level));
            if (take > 0) {
                FluidDepositData.get(level).extract(region, take);
            }
            return take;
        }
    }

    private static final Deposit NONE = new Deposit(null, 0, 0, -1, 0, 0);
    private static final Map<ResourceKey<Level>, Map<Long, Deposit>> CACHE = new ConcurrentHashMap<>();

    private FluidDeposits() {
    }

    static void clearCaches() {
        CACHE.clear();
    }

    /** Le gisement fluide sous ce chunk, ou null. */
    @Nullable
    public static Deposit at(ServerLevel level, int chunkX, int chunkZ) {
        Deposit d = region(level, Math.floorDiv(chunkX, REGION), Math.floorDiv(chunkZ, REGION));
        return d != null && d.contains(chunkX, chunkZ) ? d : null;
    }

    @Nullable
    public static Deposit at(ServerLevel level, BlockPos pos) {
        return at(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Nullable
    public static Deposit region(ServerLevel level, int rx, int rz) {
        Map<Long, Deposit> map = CACHE.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        long key = ChunkPos.asLong(rx, rz);
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
        List<FluidDepositType> types = FluidDepositTypes.all();
        if (types.isEmpty()) {
            return NONE;
        }
        long seed = level.getSeed() ^ rx * 598_237_465_123L ^ rz * 873_461_827_349L ^ 0x5F1D_0D11L ^ level.dimension().location().hashCode() * 0xC2B2AE3D27D4EB4FL;
        Random r = new Random(seed);
        if (r.nextFloat() >= CHANCE) {
            return NONE;
        }
        int radius = 2 + r.nextInt(2);
        int cx = rx * REGION + radius + r.nextInt(REGION - 2 * radius);
        int cz = rz * REGION + radius + r.nextInt(REGION - 2 * radius);
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome((cx << 2) + 2, 16, (cz << 2) + 2,
              level.getChunkSource().randomState().sampler());
        int total = 0;
        int[] weights = new int[types.size()];
        for (int i = 0; i < types.size(); i++) {
            FluidDepositType t = types.get(i);
            weights[i] = t.dimension() == level.dimension() ? t.weight(biome) : 0;
            total += weights[i];
        }
        if (total == 0) {
            return NONE;
        }
        int pick = r.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            pick -= weights[i];
            if (pick < 0) {
                FluidDepositType t = types.get(i);
                long reserve = t.reserveMin() + (long) (r.nextDouble() * (t.reserveMax() - t.reserveMin()));
                return new Deposit(t, cx, cz, radius, key, reserve);
            }
        }
        return NONE;
    }

    /** Le gisement le plus proche dans un rayon de {@code blocks} (point du chunk le plus proche) ; null s'il n'y en a pas. */
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
