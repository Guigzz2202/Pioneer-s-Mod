package fr.pioneers.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Derniere passe de generation d'un chunk : aucune etendue de liquide ne doit rester en marche ou suspendue.
 * <ul>
 *     <li>Une nappe posee sur la mer mais plus haute qu'elle (la marche d'un bloc vue sur Venus) est retiree en entier.</li>
 *     <li>Un bloc de liquide qui touche le vide (sur un cote ou dessous) est retire s'il depasse le niveau normal,
 *     ou remplace par le bloc de bordure (il fermait mal un lac).</li>
 *     <li>Les coulees (liquide non source) au-dessus du niveau normal sont retirees.</li>
 * </ul>
 */
public class FluidCleanupFeature extends Feature<FluidCleanupFeature.Config> {

    public record Config(BlockState fluid, BlockState barrier, int minY, int maxLevel) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("fluid").forGetter(Config::fluid),
              BlockState.CODEC.fieldOf("barrier").forGetter(Config::barrier),
              Codec.INT.fieldOf("min_y").forGetter(Config::minY),
              Codec.INT.fieldOf("max_level").forGetter(Config::maxLevel)
        ).apply(instance, Config::new));
    }

    public FluidCleanupFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        Fluid fluid = config.fluid().getFluidState().getType();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        int top = Math.min(level.getMaxBuildHeight() - 1, 255);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
        boolean changed = false;
        //Plusieurs passes : retirer le bord d'une nappe surelevee expose le bloc suivant
        for (int pass = 0; pass < 6; pass++) {
            boolean any = false;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int y = top; y >= config.minY(); y--) {
                        pos.set(x0 + dx, y, z0 + dz);
                        FluidState state = level.getFluidState(pos);
                        if (state.isEmpty() || !state.getType().isSame(fluid)) {
                            continue;
                        }
                        if (!state.isSource()) {
                            if (y > config.maxLevel()) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                any = true;
                            }
                            continue;
                        }
                        boolean above = y > config.maxLevel();
                        //Nappe posee sur la mer (au-dessus de son niveau) : toujours retiree
                        if (above && restsOnSea(level, pos, side, fluid, config.maxLevel())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            any = true;
                            continue;
                        }
                        if (!exposed(level, pos, side)) {
                            continue;
                        }
                        level.setBlock(pos, above ? Blocks.AIR.defaultBlockState() : config.barrier(), 2);
                        any = true;
                    }
                }
            }
            changed |= any;
            if (!any) {
                break;
            }
        }
        return changed;
    }

    /** Sous ce bloc, le liquide continue sans interruption jusqu'au niveau de la mer. */
    private static boolean restsOnSea(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos cursor, Fluid fluid, int seaLevel) {
        cursor.set(pos);
        while (cursor.getY() > seaLevel) {
            cursor.move(0, -1, 0);
            FluidState below = level.getFluidState(cursor);
            if (below.isEmpty() || !below.getType().isSame(fluid)) {
                return false;
            }
        }
        return true;
    }

    /** Touche le vide sur un cote ou dessous (le liquide coulerait). */
    private static boolean exposed(WorldGenLevel level, BlockPos pos, BlockPos.MutableBlockPos side) {
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            side.setWithOffset(pos, direction);
            BlockState neighbor = level.getBlockState(side);
            if (neighbor.getFluidState().isEmpty() && !neighbor.isSolidRender(level, side)) {
                return true;
            }
        }
        return false;
    }
}
