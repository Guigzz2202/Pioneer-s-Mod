package fr.pioneers.tech.nuclear;

import fr.pioneers.tech.nuclear.PiNuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Corium (0.17.0) : magma du coeur fondu. Brule ce qui le touche, eclaire, fume ; se refroidit en {@code cooled_corium} au hasard des ticks
 * aleatoires (environ un jour de jeu). Source de radiation forte.
 */
public class CoriumBlock extends RadioactiveBlock {

    /** Chance de refroidir a chaque tick aleatoire (~ 1 365 ticks entre deux : environ 24 000 ticks en moyenne). */
    private static final float COOLING = 1 / 18F;

    public CoriumBlock(Properties properties) {
        super(RadiationData.CORIUM, properties);
    }

    @Override
    public void stepOn(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Entity entity) {
        if (!entity.isSteppingCarefully() || entity.getRemainingFireTicks() <= 0) {
            entity.hurt(level.damageSources().hotFloor(), 4.0F);
            entity.setSecondsOnFire(4);
        }
        super.stepOn(level, pos, state, entity);
    }

    /** 0.19.15 : faces cachees entre deux blocs de corium (0.20.33 : le corium est opaque, gardee par surete). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacent, @NotNull Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    public boolean isRandomlyTicking(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (random.nextFloat() < COOLING) {
            level.setBlock(pos, PiNuclear.COOLED_CORIUM.getBlock().defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.04, 0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        }
    }
}
