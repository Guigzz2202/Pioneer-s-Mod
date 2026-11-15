package fr.pioneers.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Lac parfaitement plat (lave d'un astre volcanique, methane de Titan...) : la surface est au niveau du point le plus bas du bord,
 * donc rien ne deborde ; tout le liquide est en blocs sources au meme niveau, cale par la roche (ou par le bloc de bordure la ou
 * la roche manque). Pas de lac pres d'un autre liquide ni sur un terrain trop accidente : jamais de marche ni de coulee.
 */
public class FlatPoolFeature extends Feature<FlatPoolFeature.Config> {

    public record Config(BlockState fluid, BlockState barrier, int minRadius, int maxRadius, float scorch) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("fluid").forGetter(Config::fluid),
              BlockState.CODEC.fieldOf("barrier").forGetter(Config::barrier),
              Codec.intRange(2, 9).fieldOf("min_radius").forGetter(Config::minRadius),
              Codec.intRange(2, 9).fieldOf("max_radius").forGetter(Config::maxRadius),
              Codec.floatRange(0, 1).optionalFieldOf("scorch", 0.0F).forGetter(Config::scorch)
        ).apply(instance, Config::new));
    }

    public FlatPoolFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int span = Math.max(config.minRadius(), config.maxRadius());
        double rx = config.minRadius() + random.nextDouble() * (span - config.minRadius());
        double rz = config.minRadius() + random.nextDouble() * (span - config.minRadius());
        double[] wobble = {random.nextDouble() * 6.28, random.nextDouble() * 6.28};
        int size = (int) Math.ceil(span * 1.3) + 2;
        int width = size * 2 + 1;
        boolean[] inside = new boolean[width * width];
        int[] top = new int[width * width];
        int level0 = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        //Hauteur du sol de chaque colonne, interieur du lac et bord
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                int i = (dx + size) * width + dz + size;
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                top[i] = y;
                double angle = Math.atan2(dz, dx);
                double edge = 1 + 0.15 * Math.sin(angle * 3 + wobble[0]) + 0.1 * Math.sin(angle * 5 + wobble[1]);
                double d = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
                inside[i] = d <= edge * edge;
                BlockState surface = level.getBlockState(pos.set(x, y, z));
                if (!surface.getFluidState().isEmpty() || surface.isAir()) {
                    //Deja un liquide (mer de lave, autre lac) ou rien de solide : on ne touche pas
                    if (inside[i] || d <= (edge * 1.6) * (edge * 1.6)) {
                        return false;
                    }
                }
                if (inside[i]) {
                    highest = Math.max(highest, y);
                }
            }
        }
        //Niveau : le point le plus bas du bord immediat (les colonnes voisines de l'interieur)
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                int i = (dx + size) * width + dz + size;
                if (inside[i] || !touchesInside(inside, dx, dz, size, width)) {
                    continue;
                }
                level0 = Math.min(level0, top[i]);
            }
        }
        if (level0 == Integer.MAX_VALUE || highest - level0 > 5 || level0 <= level.getMinBuildHeight() + 4) {
            return false;
        }
        BlockState fluid = config.fluid();
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                int i = (dx + size) * width + dz + size;
                if (!inside[i]) {
                    continue;
                }
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                double d = Math.sqrt((dx * dx) / (rx * rx) + (dz * dz) / (rz * rz));
                int depth = 1 + (int) Math.round((1 - Math.min(1, d)) * 2.4);
                int floor = level0 - depth + 1;
                //On degage au-dessus du niveau
                for (int y = level0 + 1; y <= Math.max(top[i], level0) + 1; y++) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = floor; y <= level0; y++) {
                    level.setBlock(pos.set(x, y, z), fluid, 2);
                }
                seal(level, pos.set(x, floor - 1, z), config.barrier());
                //Parois : partout ou un voisin hors du lac n'est pas solide a hauteur du liquide
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int ndx = dx + n[0], ndz = dz + n[1];
                    int j = (ndx + size) * width + ndz + size;
                    if (j >= 0 && j < inside.length && Math.abs(ndx) <= size && Math.abs(ndz) <= size && inside[j]) {
                        continue;
                    }
                    for (int y = floor; y <= level0; y++) {
                        seal(level, pos.set(x + n[0], y, z + n[1]), config.barrier());
                    }
                }
            }
        }
        //Rivage brule (basalte autour d'un lac de lave)
        if (config.scorch() > 0) {
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    int i = (dx + size) * width + dz + size;
                    if (!inside[i] && touchesInside(inside, dx, dz, size, width) && random.nextFloat() < config.scorch()) {
                        BlockState here = level.getBlockState(pos.set(origin.getX() + dx, top[i], origin.getZ() + dz));
                        if (here.isSolidRender(level, pos) && top[i] >= level0) {
                            level.setBlock(pos, config.barrier(), 2);
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void seal(WorldGenLevel level, BlockPos pos, BlockState barrier) {
        BlockState state = level.getBlockState(pos);
        if (!state.isSolidRender(level, pos) && state.getFluidState().isEmpty() || !state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
            level.setBlock(pos, barrier, 2);
        }
    }

    private static boolean touchesInside(boolean[] inside, int dx, int dz, int size, int width) {
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int ndx = dx + n[0], ndz = dz + n[1];
            if (Math.abs(ndx) > size || Math.abs(ndz) > size) {
                continue;
            }
            if (inside[(ndx + size) * width + ndz + size]) {
                return true;
            }
        }
        return false;
    }
}
