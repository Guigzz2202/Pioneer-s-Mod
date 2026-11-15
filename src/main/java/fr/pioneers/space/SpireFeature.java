package fr.pioneers.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Aiguille effilee posee au sol : buttes de glace erodees de Callisto, lames de glace de methane de Pluton (allongees),
 * pitons de soufre d'Io. {@code elongation} > 1 etire la base dans une direction (lame).
 */
public class SpireFeature extends Feature<SpireFeature.Config> {

    public record Config(BlockState block, BlockState tip, int minHeight, int maxHeight, int radius, float elongation) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("block").forGetter(Config::block),
              BlockState.CODEC.fieldOf("tip").forGetter(Config::tip),
              Codec.intRange(2, 40).fieldOf("min_height").forGetter(Config::minHeight),
              Codec.intRange(2, 40).fieldOf("max_height").forGetter(Config::maxHeight),
              Codec.intRange(1, 6).fieldOf("radius").forGetter(Config::radius),
              Codec.floatRange(1, 4).optionalFieldOf("elongation", 1.0F).forGetter(Config::elongation)
        ).apply(instance, Config::new));
    }

    public SpireFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState ground = level.getBlockState(pos.set(origin.getX(), top, origin.getZ()));
        if (!ground.isSolidRender(level, pos) || !ground.getFluidState().isEmpty()) {
            return false;
        }
        int height = config.minHeight() + random.nextInt(Math.max(1, config.maxHeight() - config.minHeight() + 1));
        double radius = config.radius() + random.nextDouble() * 0.6;
        double angle = random.nextDouble() * Math.PI;
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double lean = (random.nextDouble() - 0.5) * 0.25;
        int reach = (int) Math.ceil(radius * config.elongation() + Math.abs(lean) * height) + 1;
        for (int i = -2; i <= height; i++) {
            double t = Math.max(0, i) / (double) height;
            double r = radius * Math.pow(1 - t, 0.75) + 0.35;
            double cx = origin.getX() + 0.5 + lean * i * ca, cz = origin.getZ() + 0.5 + lean * i * sa;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double x = origin.getX() + dx + 0.5 - cx, z = origin.getZ() + dz + 0.5 - cz;
                    double u = (x * ca + z * sa) / config.elongation(), v = -x * sa + z * ca;
                    if (u * u + v * v <= r * r) {
                        level.setBlock(pos.set(origin.getX() + dx, top + i, origin.getZ() + dz), t > 0.82 ? config.tip() : config.block(), 2);
                    }
                }
            }
        }
        return true;
    }
}
