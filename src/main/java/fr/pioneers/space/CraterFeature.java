package fr.pioneers.space;

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
 * Cratere d'impact : une cuvette circulaire avec un bourrelet de roche autour.
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {

    public CraterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        //Rayon limite pour rester dans la zone de generation autorisee
        float radius = 2.5F + random.nextFloat() * 4.5F;
        float depth = radius * (0.35F + random.nextFloat() * 0.25F);
        int r = Mth.ceil(radius + 2);
        int centerY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        //Materiaux du terrain local (sable et roche de la Lune ou de Mars)
        BlockState sand = level.getBlockState(new BlockPos(origin.getX(), centerY - 1, origin.getZ()));
        BlockState rock = level.getBlockState(new BlockPos(origin.getX(), centerY - 5, origin.getZ()));
        if (sand.isAir() || rock.isAir()) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                float d = Mth.sqrt(dx * dx + dz * dz);
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (d < radius) {
                    //Fond de la cuvette
                    float t = d / radius;
                    int floor = Mth.floor(centerY - depth * (1 - t * t));
                    for (int y = top; y > floor; y--) {
                        level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    if (floor <= top + 1) {
                        level.setBlock(pos.set(x, floor, z), random.nextInt(4) == 0 ? rock : sand, 2);
                    }
                } else if (d < radius + 1.6F) {
                    //Bourrelet d'ejectas
                    int rim = d < radius + 0.8F ? 2 : 1;
                    for (int i = 1; i <= rim; i++) {
                        level.setBlock(pos.set(x, top + i, z), i == rim ? sand : rock, 2);
                    }
                }
            }
        }
        return true;
    }
}
