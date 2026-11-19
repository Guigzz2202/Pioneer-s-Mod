package fr.pioneers.space.mining;

import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.farm.SpacePlant.Planet;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.tile.TileEntityMiningOutpost;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 6) : la FOREUSE SPATIALE (multibloc MKV {@code space_drill}, Kind DRILL ; brief §7). Posee sur
 * n'importe quel astre, sans autre condition, elle fore les filons PROFONDS de l'astre sous elle : les filons du radar de l'astre
 * ({@link MiningVein}), tires une fois pour toutes par region de 8 x 8 chunks selon le biome (les filons durs pesent plus en profondeur),
 * avec une reserve par region bien plus grande que celle d'un gisement de rover ({@link #RESERVE}). Elle s'ajoute aux rovers sans les
 * remplacer : les rovers prospectent, la foreuse exploite. Sa fabrication demande le mat de forage colossal (Ascenseur de Mars) et le
 * trepan de manteau (Grand Forage). Le travail par tick est dans {@code MultiProcess.tickSpaceDrill}.
 */
public final class SpaceDrill {

    public static final String KEY = "space_drill";
    /** Region de tirage : 8 x 8 chunks. */
    public static final int REGION_SHIFT = 7;
    /** Reserve d'un filon profond (operations). */
    public static final int RESERVE = 40_000;
    /** Duree d'une operation au tier basique (ticks) et minerai par operation. */
    public static final int TIME = 160, YIELD = 2;

    private SpaceDrill() {
    }

    public static boolean is(String machineKey) {
        return KEY.equals(machineKey);
    }

    /** Astre (filons du radar) du monde, ou null. */
    @Nullable
    public static Planet planet(ServerLevel level) {
        Body body = Megastructures.bodyOf.apply(level);
        if (body == null) {
            return null;
        }
        try {
            return Planet.valueOf(body.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static long region(BlockPos pos) {
        return (long) (pos.getX() >> REGION_SHIFT) << 32 | ((pos.getZ() >> REGION_SHIFT) & 0xFFFFFFFFL);
    }

    /** Filon profond sous la position, ou null (astre sans filon). Tirage fixe par region (graine du monde, dimension, region). */
    @Nullable
    public static MiningVein vein(ServerLevel level, BlockPos pos) {
        Planet planet = planet(level);
        if (planet == null) {
            return null;
        }
        List<MiningVein> veins = TileEntityMiningOutpost.veinsOf(planet);
        if (veins.isEmpty()) {
            return null;
        }
        int cx = ((pos.getX() >> REGION_SHIFT) << REGION_SHIFT) + 64, cz = ((pos.getZ() >> REGION_SHIFT) << REGION_SHIFT) + 64;
        String biome = level.getBiome(new BlockPos(cx, level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz), cz)).unwrapKey()
              .map(k -> k.location().toString()).orElse(null);
        long total = 0;
        for (MiningVein v : veins) {
            total += weight(v, biome);
        }
        if (total <= 0) {
            return null;
        }
        long seed = level.getSeed() ^ region(pos) * 0x9E3779B97F4A7C15L ^ level.dimension().location().hashCode() * 341_873_128_712L;
        long roll = Math.floorMod(new java.util.Random(seed).nextLong(), total);
        for (MiningVein v : veins) {
            roll -= weight(v, biome);
            if (roll < 0) {
                return v;
            }
        }
        return veins.get(0);
    }

    /** En profondeur, les filons durs pesent plus (la foreuse atteint ce que les rovers effleurent). */
    private static long weight(MiningVein v, @Nullable String biome) {
        return (long) v.weightIn(biome) * (2 + v.hardness);
    }

    /** Operations restantes dans le filon profond de la region. */
    public static int left(ServerLevel level, BlockPos pos) {
        return Math.max(0, RESERVE - Data.get(level).mined(region(pos)));
    }

    /** Reste a forer sous un controleur de foreuse : filon profond de la foreuse spatiale, sinon gisement du monde (foreuse electrique). */
    public static long left(String machineKey, ServerLevel level, BlockPos pos, @Nullable fr.pioneers.world.geology.Deposits.Deposit deposit) {
        return is(machineKey) ? left(level, pos) : deposit == null ? 0 : deposit.left(level);
    }

    public static void mine(ServerLevel level, BlockPos pos, int ops) {
        Data.get(level).mine(region(pos), ops);
    }

    /** Operations faites par region (sauve avec chaque monde d'astre). */
    public static final class Data extends SavedData {

        private static final String NAME = "pioneers_space_drill";
        private final Map<Long, Integer> mined = new HashMap<>();

        public static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        int mined(long region) {
            return mined.getOrDefault(region, 0);
        }

        void mine(long region, int ops) {
            mined.merge(region, ops, Integer::sum);
            setDirty();
        }

        private static Data load(CompoundTag tag) {
            Data d = new Data();
            ListTag list = tag.getList("regions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                d.mined.put(t.getLong("r"), t.getInt("n"));
            }
            return d;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag list = new ListTag();
            mined.forEach((r, n) -> {
                CompoundTag t = new CompoundTag();
                t.putLong("r", r);
                t.putInt("n", n);
                list.add(t);
            });
            tag.put("regions", list);
            return tag;
        }
    }
}
