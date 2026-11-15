package fr.pioneers.chemistry;

import com.mojang.serialization.Codec;
import fr.pioneers.geology.fluid.FluidDeposits;
import fr.pioneers.registries.PiChemistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Partie « reelle » des gisements de petrole : au-dessus d'un gisement virtuel ({@link FluidDeposits}), dans les deserts, une mare de brut
 * cernee de sable bitumineux ; sous la mer, des suintements de bitume et des plaques de sable bitumineux sur le fond. Le chunk central du
 * gisement en porte toujours, les autres une fois sur six : de quoi reperer un gisement sans sismographe.
 */
public class OilFeature extends Feature<NoneFeatureConfiguration> {

    public OilFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    public static Codec<NoneFeatureConfiguration> codec() {
        return NoneFeatureConfiguration.CODEC;
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        BlockPos origin = context.origin();
        int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
        FluidDeposits.Deposit deposit = FluidDeposits.at(server, cx, cz);
        if (deposit == null || !"oil".equals(deposit.type().category())) {
            return false;
        }
        RandomSource random = context.random();
        boolean center = cx == deposit.centerX() && cz == deposit.centerZ();
        if (!center && random.nextInt(6) != 0) {
            return false;
        }
        int x = (cx << 4) + 4 + random.nextInt(8), z = (cz << 4) + 4 + random.nextInt(8);
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos top = new BlockPos(x, floor, z);
        if (level.getFluidState(top).is(FluidTags.WATER)) {
            return seeps(level, top, random);
        }
        return surface == floor && pool(level, new BlockPos(x, floor - 1, z), random, deposit);
    }

    /** Fond de la mer : 2 a 4 suintements et des plaques de sable bitumineux. */
    private boolean seeps(WorldGenLevel level, BlockPos center, RandomSource random) {
        BlockState seep = PiChemistry.OIL_SEEP.getBlock().defaultBlockState().setValue(OilSeepBlock.WATERLOGGED, true);
        BlockState sand = PiChemistry.TAR_SAND.getBlock().defaultBlockState();
        boolean placed = false;
        for (int i = 0; i < 10; i++) {
            int x = center.getX() + random.nextInt(7) - 3, z = center.getZ() + random.nextInt(7) - 3;
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            BlockPos pos = new BlockPos(x, y, z), below = pos.below();
            if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(below).isSolid()) {
                continue;
            }
            level.setBlock(below, sand, Block.UPDATE_CLIENTS);
            if (i < 4) {
                level.setBlock(pos, seep, Block.UPDATE_CLIENTS);
            }
            placed = true;
        }
        return placed;
    }

    /** Desert : une petite mare de brut dans un creux, bordee et tapissee de sable bitumineux. */
    private boolean pool(WorldGenLevel level, BlockPos ground, RandomSource random, FluidDeposits.Deposit deposit) {
        if (!level.getBlockState(ground).isSolid() || deposit.type().fluid() == null) {
            return false;
        }
        BlockState oil = deposit.type().fluid().defaultFluidState().createLegacyBlock();
        BlockState sand = PiChemistry.TAR_SAND.getBlock().defaultBlockState();
        int r = 1 + random.nextInt(2);
        //Le bord doit etre plein tout autour (sinon le brut s'ecoulerait)
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                if (!level.getBlockState(ground.offset(dx, 0, dz)).isSolid() || !level.getBlockState(ground.offset(dx, -1, dz)).isSolid()) {
                    return false;
                }
            }
        }
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                BlockPos p = ground.offset(dx, 0, dz);
                if (d <= r + 0.3 && !oil.isAir()) {
                    level.setBlock(p, oil, Block.UPDATE_CLIENTS);
                    level.setBlock(p.below(), d <= r - 0.7 ? oil : sand, Block.UPDATE_CLIENTS);
                    level.setBlock(p.below(2), sand, Block.UPDATE_CLIENTS);
                    level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (d <= r + 1.6) {
                    level.setBlock(p, sand, Block.UPDATE_CLIENTS);
                }
            }
        }
        if (!oil.isAir()) {
            BlockState seep = PiChemistry.OIL_SEEP.getBlock().defaultBlockState();
            BlockPos s = ground.offset(r + 1, 1, 0);
            if (level.getBlockState(s).isAir()) {
                level.setBlock(s, seep, Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }
}
