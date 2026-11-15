package fr.pioneers.logistics.caravan;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : DROMADAIRE (une bosse, plus elance, robe sable clair) : un chameau vanilla (cerveau, monte a deux, sprint, assis)
 * de son propre type ; nait dans les deserts et les savanes ; se nourrit de cactus, de foin et de ble (le chameau vanilla, devenu chameau de
 * Bactriane a deux bosses, prend aussi le foin et le ble : {@link Caravans#feed}). Ne se croise qu'avec un dromadaire.
 */
public class DromedaryEntity extends Camel {

    public DromedaryEntity(EntityType<? extends Camel> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) {
        return super.isFood(stack) || stack.is(Items.HAY_BLOCK) || stack.is(Items.WHEAT);
    }

    @Override
    public boolean canMate(@NotNull Animal other) {
        return other instanceof DromedaryEntity && super.canMate(other);
    }

    @Nullable
    @Override
    public Camel getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob mate) {
        return PiCaravan.DROMEDARY.get().create(level);
    }
}
