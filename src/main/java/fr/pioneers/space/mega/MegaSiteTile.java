package fr.pioneers.space.mega;

import fr.pioneers.core.registries.PiTileEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : le chantier de megastructure. Aucun inventaire propre : ce qu'on lui livre (entonnoir, cable
 * logistique, cargo, bouton de l'ecran) va directement dans l'etat commun {@link MegaWorld} de la megastructure de son astre ; il ne prend
 * que ce qui manque encore a la liste. Rien a chaque tick.
 */
public class MegaSiteTile extends BlockEntity {

    private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> new Delivery(this));

    public MegaSiteTile(BlockPos pos, BlockState state) {
        super(PiTileEntityTypes.MEGASTRUCTURE_SITE.get(), pos, state);
    }

    /** Megastructure de l'astre du chantier, ou -1. */
    public int mega() {
        return level == null ? -1 : Megastructures.here(level);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    /** Livraison : une case virtuelle, toujours vide, qui n'accepte que ce qui manque au chantier. */
    private record Delivery(MegaSiteTile tile) implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !(tile.level instanceof ServerLevel level)) {
                return stack;
            }
            int taken = MegaWorld.get(level.getServer()).accept(level.getServer(), tile.mega(), stack, simulate);
            if (taken <= 0) {
                return stack;
            }
            ItemStack rest = stack.copy();
            rest.shrink(taken);
            return rest;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (!(tile.level instanceof ServerLevel level)) {
                return false;
            }
            int i = tile.mega();
            int k = i < 0 ? -1 : Megastructures.ingredientOf(i, stack);
            return k >= 0 && MegaWorld.get(level.getServer()).missing(i, k) > 0;
        }
    }
}
