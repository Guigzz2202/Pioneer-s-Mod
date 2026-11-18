package fr.pioneers.world.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5) : tuile du PANIER du chat : 3 cases ({@link PetRules#BASKET_SLOTS}) ou le chat depose les poissons qu'il rapporte ;
 * le joueur les prend au clic droit main vide ; capacite d'objets (entonnoir dessous : sortie ; dessus et cotes : poissons seulement) ;
 * comparateur (0 a 15 selon le remplissage).
 */
public class CatBasketTile extends BlockEntity {

    public final ItemStackHandler items = new ItemStackHandler(PetRules.BASKET_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return fr.pioneers.farm.animal.PetFood.catFish(stack);
        }
    };
    private final LazyOptional<IItemHandler> cap = LazyOptional.of(() -> items);

    public CatBasketTile(BlockPos pos, BlockState state) {
        super(PiPets.BASKET_TILE.get(), pos, state);
    }

    public boolean empty() {
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean full() {
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    public int comparator() {
        float f = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) {
                f += s.getCount() / (float) s.getMaxStackSize();
            }
        }
        return f <= 0 ? 0 : 1 + (int) (f / items.getSlots() * 14);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("items")) {
            items.deserializeNBT(tag.getCompound("items"));
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> c, @Nullable Direction side) {
        return c == ForgeCapabilities.ITEM_HANDLER ? cap.cast() : super.getCapability(c, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }
}
