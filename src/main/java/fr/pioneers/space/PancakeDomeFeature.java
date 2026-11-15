package fr.pioneers.space;

import fr.pioneers.registries.PiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Dome en galette de Venus : une coulee de lave tres visqueuse figee en disque, au sommet plat, aux flancs abrupts,
 * avec une petite depression centrale et des fissures radiales.
 */
public class PancakeDomeFeature extends Feature<NoneFeatureConfiguration> {

    public PancakeDomeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        if (ground <= level.getMinBuildHeight() + 2 || !level.getFluidState(new BlockPos(origin.getX(), ground - 1, origin.getZ())).isEmpty()) {
            return false;
        }
        float radius = 5 + random.nextFloat() * 6;
        int height = 3 + random.nextInt(4);
        BlockState basalt = PiBlocks.VENUS_BASALT.getBlock().defaultBlockState();
        BlockState rock = PiBlocks.VENUS_ROCK.getBlock().defaultBlockState();
        int cracks = 3 + random.nextInt(3);
        float crackAngle = random.nextFloat() * Mth.TWO_PI;
        int r = Mth.ceil(radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                float d = Mth.sqrt(dx * dx + dz * dz) / radius;
                if (d > 1) {
                    continue;
                }
                int top = ground + Math.round(height * (1 - (float) Math.pow(d, 6)));
                if (d < 0.18F) {
                    top -= 1;
                }
                //Fissures radiales sur le sommet
                float angle = (float) Math.atan2(dz, dx) - crackAngle;
                boolean crack = d > 0.25F && d < 0.85F && Math.abs(Mth.sin(angle * cracks / 2F)) < 0.08F;
                int column = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz);
                for (int y = Math.min(column, ground) - 2; y < top; y++) {
                    pos.set(origin.getX() + dx, y, origin.getZ() + dz);
                    level.setBlock(pos, y == top - 1 && (crack || random.nextFloat() < 0.15F) ? rock : basalt, 2);
                }
                if (crack) {
                    level.setBlock(pos.set(origin.getX() + dx, top - 1, origin.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }
}
