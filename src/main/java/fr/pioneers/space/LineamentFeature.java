package fr.pioneers.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
 * Lineament sinueux trace a la surface : ligne de glace coloree (lignes d'Europe), tranchee (graben, chasma),
 * ou crete (doubles cretes d'Europe, sillons de Ganymede, rubans de Miranda). Reste dans la zone de generation autorisee.
 * Modes : "fill" (remplace la surface, a plat), "trench" (creuse, fond tapisse), "ridge" (eleve une crete, sillon central optionnel).
 */
public class LineamentFeature extends Feature<LineamentFeature.Config> {

    public record Config(BlockState fill, Optional<BlockState> center, String mode, int minLength, int maxLength, int width, int depth, float curl)
          implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("fill").forGetter(Config::fill),
              BlockState.CODEC.optionalFieldOf("center").forGetter(Config::center),
              Codec.STRING.fieldOf("mode").forGetter(Config::mode),
              Codec.intRange(4, 30).fieldOf("min_length").forGetter(Config::minLength),
              Codec.intRange(4, 30).fieldOf("max_length").forGetter(Config::maxLength),
              Codec.intRange(1, 7).fieldOf("width").forGetter(Config::width),
              Codec.intRange(1, 12).fieldOf("depth").forGetter(Config::depth),
              Codec.floatRange(0, 2).optionalFieldOf("curl", 0.3F).forGetter(Config::curl)
        ).apply(instance, Config::new));
    }

    public LineamentFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int length = config.minLength() + random.nextInt(Math.max(1, config.maxLength() - config.minLength() + 1));
        double angle = random.nextDouble() * Math.PI * 2;
        int half = config.width() / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        Set<Long> done = new HashSet<>();
        for (int dir = -1; dir <= 1; dir += 2) {
            double x = origin.getX() + 0.5, z = origin.getZ() + 0.5, a = dir < 0 ? angle + Math.PI : angle;
            for (int step = 0; step < length / 2; step++) {
                a += (random.nextDouble() - 0.5) * config.curl();
                x += Math.cos(a);
                z += Math.sin(a);
                double px = -Math.sin(a), pz = Math.cos(a);
                for (int off = -half; off <= half; off++) {
                    int bx = (int) Math.floor(x + px * off), bz = (int) Math.floor(z + pz * off);
                    if (Math.abs(bx - origin.getX()) > 14 || Math.abs(bz - origin.getZ()) > 14 || !done.add(BlockPos.asLong(bx, 0, bz))) {
                        continue;
                    }
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, bx, bz) - 1;
                    BlockState surface = level.getBlockState(pos.set(bx, top, bz));
                    if (!surface.getFluidState().isEmpty() || !surface.isSolidRender(level, pos)) {
                        continue;
                    }
                    placed |= column(level, pos, bx, bz, top, off, half, config);
                }
            }
        }
        return placed;
    }

    private static boolean column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int off, int half, Config config) {
        switch (config.mode()) {
            case "trench" -> {
                int d = Math.max(1, config.depth() - (Math.abs(off) == half && half > 0 ? config.depth() / 2 : 0));
                for (int y = top; y > top - d; y--) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                if (level.getBlockState(pos.set(x, top - d, z)).isSolidRender(level, pos)) {
                    level.setBlock(pos, config.fill(), 2);
                }
            }
            case "ridge" -> {
                if (off == 0 && config.center().isPresent() && half > 0) {
                    level.setBlock(pos.set(x, top, z), config.center().get(), 2);
                    return true;
                }
                double t = half == 0 ? 1 : 1 - Math.abs(Math.abs(off) - (config.center().isPresent() ? half / 2.0 + 0.5 : 0)) / (half + 1.0);
                int h = Math.max(1, (int) Math.round(config.depth() * Math.max(0.3, t)));
                for (int y = top + 1; y <= top + h; y++) {
                    level.setBlock(pos.set(x, y, z), config.fill(), 2);
                }
            }
            default -> {
                for (int y = top; y > top - config.depth(); y--) {
                    if (level.getBlockState(pos.set(x, y, z)).isSolidRender(level, pos)) {
                        level.setBlock(pos, config.fill(), 2);
                    }
                }
            }
        }
        return true;
    }
}
