package fr.pioneers.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Glace carbonique des calottes martiennes : elle sublime en fines volutes blanches qui rampent au ras du sol.
 */
public class DryIceBlock extends Block {

    public DryIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (random.nextInt(5) == 0 && level.getBlockState(pos.above()).isAir()) {
            double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.02, z, (random.nextDouble() - 0.5) * 0.02, 0.004, (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
