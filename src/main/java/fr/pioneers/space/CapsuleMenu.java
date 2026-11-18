package fr.pioneers.space;

import fr.pioneers.core.registries.PiMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Soute de la capsule : un seul emplacement, en sortie uniquement (la fusee repliee).
 */
public class CapsuleMenu extends AbstractContainerMenu {

    public static final int SLOT_X = 80, SLOT_Y = 26;
    private final Container cargo;
    @Nullable
    private final CapsuleEntity capsule;

    /** Cote client. */
    public CapsuleMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainer(1), null);
    }

    public CapsuleMenu(int id, Inventory inventory, Container cargo, @Nullable CapsuleEntity capsule) {
        super(PiMenus.CAPSULE.get(), id);
        this.cargo = cargo;
        this.capsule = capsule;
        addSlot(new Slot(cargo, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 66 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 124));
        }
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        //Seul le slot de la soute peut etre vide d'un shift-clic vers l'inventaire
        if (index != 0) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(0);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, 1, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return capsule == null || capsule.isAlive() && player.distanceToSqr(capsule) < 64;
    }
}
