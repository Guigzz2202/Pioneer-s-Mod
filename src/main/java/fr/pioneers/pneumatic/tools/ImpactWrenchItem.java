package fr.pioneers.pneumatic.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Cle a chocs : une cle comme les autres (tags {@code forge:tools/wrench}, reglage des faces, des tuyaux, des soupapes) qui en plus devisse
 * en un instant les blocs-machines du mod (ceux qui ont un contenu) : ils tombent en objet avec ce que le bloc garde a la casse. Chaque
 * demontage coute {@value #PER_USE} litres d'air ; sans air, elle demonte a la vitesse d'une main nue.
 */
public class ImpactWrenchItem extends AirToolItem {

    public static final double CAPACITY = 1000, PER_USE = 20;

    public ImpactWrenchItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    protected double perUse() {
        return PER_USE;
    }

    /** Bloc-machine du mod (bloc a contenu) ? */
    public static boolean machine(BlockState state) {
        return state.hasBlockEntity() && "pioneers".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    @Override
    public float getDestroySpeed(@NotNull ItemStack stack, @NotNull BlockState state) {
        return machine(state) && has(stack, PER_USE) ? 80F : 1F;
    }

    @Override
    public boolean isCorrectToolForDrops(@NotNull ItemStack stack, @NotNull BlockState state) {
        return machine(state);
    }

    @Override
    public boolean mineBlock(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull LivingEntity miner) {
        if (!level.isClientSide && machine(state) && use(stack, PER_USE)) {
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        return true;
    }

    /** Maj + clic droit atteint le bloc (fiches, reglages), comme les autres cles. */
    @Override
    public boolean doesSneakBypassUse(@NotNull ItemStack stack, @NotNull LevelReader level, @NotNull BlockPos pos, @NotNull Player player) {
        return true;
    }
}
