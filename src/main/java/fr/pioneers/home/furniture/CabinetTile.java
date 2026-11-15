package fr.pioneers.home.furniture;

import fr.pioneers.home.PiHome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : inventaire d'un meuble de rangement ({@link CabinetBlock}) : 18 cases (deux rangees, ecran de coffre vanilla 9 × 2),
 * cle de sauvegarde {@code Items} comme les coffres ; entonnoirs et comparateurs comme un coffre ; contenu jete au sol a la casse.
 */
public class CabinetTile extends RandomizableContainerBlockEntity {

    public static final int SLOTS = 18;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public CabinetTile(BlockPos pos, BlockState state) {
        super(PiHome.CABINET_TILE.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @NotNull
    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> list) {
        items = list;
    }

    @NotNull
    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @NotNull
    @Override
    protected AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, 2);
    }

    @Override
    public void startOpen(@NotNull Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.5F, 1.25F);
        }
    }

    @Override
    public void stopOpen(@NotNull Player player) {
        if (!player.isSpectator() && level != null) {
            level.playSound(null, worldPosition, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.5F, 1.25F);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }
}
