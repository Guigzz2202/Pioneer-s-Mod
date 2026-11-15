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
 * Trainee sombre d'un geyser (Triton) : un event, et un eventail de poussiere couche par le vent, toujours dans la meme direction
 * sur tout l'astre (comme les trainees paralleles de la calotte de Triton).
 */
public class PlumeStreakFeature extends Feature<PlumeStreakFeature.Config> {

    public record Config(BlockState vent, BlockState deposit, int length) implements FeatureConfiguration {

        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
              BlockState.CODEC.fieldOf("vent").forGetter(Config::vent),
              BlockState.CODEC.fieldOf("deposit").forGetter(Config::deposit),
              Codec.intRange(4, 14).fieldOf("length").forGetter(Config::length)
        ).apply(instance, Config::new));
    }

    /** Direction du vent (vers le nord-est). */
    private static final double WIND = Math.toRadians(-38);

    public PlumeStreakFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1;
        if (!level.getBlockState(pos.set(origin.getX(), top, origin.getZ())).isSolidRender(level, pos)) {
            return false;
        }
        level.setBlock(pos, config.vent(), 2);
        double wx = Math.cos(WIND + (random.nextDouble() - 0.5) * 0.3), wz = Math.sin(WIND + (random.nextDouble() - 0.5) * 0.3);
        int length = config.length() - random.nextInt(4);
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double along = dx * wx + dz * wz, across = -dx * wz + dz * wx;
                if (along < 0.5 || along > length) {
                    continue;
                }
                double halfWidth = 0.6 + along * 0.28;
                if (Math.abs(across) > halfWidth || random.nextFloat() > 1.05 - along / (length * 1.3)) {
                    continue;
                }
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (level.getBlockState(pos.set(x, y, z)).isSolidRender(level, pos)) {
                    level.setBlock(pos, config.deposit(), 2);
                }
            }
        }
        return true;
    }
}
