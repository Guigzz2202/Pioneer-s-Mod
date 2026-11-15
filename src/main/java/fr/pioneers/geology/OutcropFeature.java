package fr.pioneers.geology;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

/** Une fois par chunk : si un gisement est dessous, 2 a 4 affleurements a la surface (au Nether, sur un sol sous un plafond). */
public class OutcropFeature extends Feature<NoneFeatureConfiguration> {

    public OutcropFeature() {
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
        Deposits.Deposit deposit = Deposits.at(server, cx, cz);
        if (deposit == null || deposit.type().outcrop() == null) {
            return false;
        }
        Block outcrop = deposit.type().outcrop();
        RandomSource random = context.random();
        int count = 2 + random.nextInt(3);
        boolean placed = false;
        boolean nether = server.dimension() == Level.NETHER;
        for (int i = 0; i < count; i++) {
            int x = (cx << 4) + random.nextInt(16), z = (cz << 4) + random.nextInt(16);
            BlockPos pos = nether ? netherFloor(level, x, z, random) : new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            if (pos == null) {
                continue;
            }
            BlockState here = level.getBlockState(pos), below = level.getBlockState(pos.below());
            if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty() && below.isFaceSturdy(level, pos.below(), Direction.UP)
                && below.getFluidState().isEmpty()) {
                level.setBlock(pos, outcrop.defaultBlockState(), Block.UPDATE_CLIENTS);
                placed = true;
            }
        }
        return placed;
    }

    private static BlockPos netherFloor(WorldGenLevel level, int x, int z, RandomSource random) {
        for (int y = 100 - random.nextInt(30); y > 32; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir() && level.getBlockState(p.below()).getFluidState().isEmpty()) {
                return p;
            }
        }
        return null;
    }
}
