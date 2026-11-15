package fr.pioneers.space;

import fr.pioneers.registries.PiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Geyser de CO2 des calottes polaires ("araignee martienne") : un event de glace carbonique entoure de
 * rigoles rayonnantes et ramifiees de sable sombre, projete sur la glace par les jets de gaz au printemps.
 */
public class Co2GeyserFeature extends Feature<NoneFeatureConfiguration> {

    public Co2GeyserFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1;
        BlockPos vent = new BlockPos(origin.getX(), y, origin.getZ());
        if (level.getBlockState(vent).isAir()) {
            return false;
        }
        int arms = 5 + random.nextInt(4);
        for (int arm = 0; arm < arms; arm++) {
            double angle = arm * Math.PI * 2 / arms + random.nextDouble() * 0.5;
            int length = 4 + random.nextInt(5);
            trace(level, random, origin.getX() + 0.5, origin.getZ() + 0.5, angle, length, 2);
        }
        level.setBlock(vent, PiBlocks.DRY_ICE.getBlock().defaultBlockState(), 2);
        level.setBlock(vent.above(), PiBlocks.DRY_ICE.getBlock().defaultBlockState(), 2);
        for (var direction : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) {
                level.setBlock(vent.relative(direction), PiBlocks.MARS_DARK_SAND.getBlock().defaultBlockState(), 2);
            }
        }
        return true;
    }

    private void trace(WorldGenLevel level, RandomSource random, double x, double z, double angle, int length, int depth) {
        for (int step = 0; step < length; step++) {
            x += Math.cos(angle);
            z += Math.sin(angle);
            angle += (random.nextDouble() - 0.5) * 0.5;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, bx, bz) - 1;
            BlockPos pos = new BlockPos(bx, y, bz);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, PiBlocks.MARS_DARK_SAND.getBlock().defaultBlockState(), 2);
            }
            if (depth > 0 && step > 1 && random.nextInt(3) == 0) {
                trace(level, random, x, z, angle + (random.nextBoolean() ? 0.7 : -0.7), Math.max(2, (length - step) / 2), depth - 1);
            }
        }
    }
}
