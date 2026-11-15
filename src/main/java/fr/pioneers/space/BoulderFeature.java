package fr.pioneers.space;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Rocher pose sur le sol : un ellipsoide irregulier fait de la roche locale (lue sous la surface),
 * a moitie enfoui dans le regolithe. Les plus gros s'accompagnent de quelques eclats autour.
 */
public class BoulderFeature extends Feature<NoneFeatureConfiguration> {

    public BoulderFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        BlockState rock = level.getBlockState(new BlockPos(origin.getX(), surface - 5, origin.getZ()));
        BlockState ground = level.getBlockState(new BlockPos(origin.getX(), surface - 1, origin.getZ()));
        if (rock.isAir() || ground.isAir()) {
            return false;
        }
        float rx = 1.2F + random.nextFloat() * 1.6F, ry = 0.9F + random.nextFloat() * 1.2F, rz = 1.2F + random.nextFloat() * 1.6F;
        int cy = surface - 1 + Mth.floor(ry * 0.4F);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ix = Mth.ceil(rx), iy = Mth.ceil(ry), iz = Mth.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    float d = dx * dx / (rx * rx) + dy * dy / (ry * ry) + dz * dz / (rz * rz);
                    if (d <= 1 - random.nextFloat() * 0.15F) {
                        pos.set(origin.getX() + dx, cy + dy, origin.getZ() + dz);
                        level.setBlock(pos, rock, 2);
                    }
                }
            }
        }
        //Eclats autour des gros rochers
        if (rx + rz > 4.5F) {
            for (int i = 0; i < 4; i++) {
                int x = origin.getX() + random.nextInt(9) - 4, z = origin.getZ() + random.nextInt(9) - 4;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                if (!level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
                    level.setBlock(new BlockPos(x, y, z), rock, 2);
                }
            }
        }
        return true;
    }
}
