package fr.pioneers.core.inventory;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/** Capacite d'objets Forge sur une liste d'emplacements, vue depuis une origine (tuyaux : EXTERNAL). Et la sauvegarde des listes. */
public class ItemSlots implements IItemHandlerModifiable {

    private final List<ItemSlot> slots;
    private final Automation automation;

    public ItemSlots(List<ItemSlot> slots, Automation automation) {
        this.slots = slots;
        this.automation = automation;
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return valid(slot) ? slots.get(slot).getStack() : ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return valid(slot) ? slots.get(slot).insert(stack, Action.get(!simulate), automation) : stack;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return valid(slot) ? slots.get(slot).extract(amount, Action.get(!simulate), automation) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return valid(slot) ? slots.get(slot).getLimit(ItemStack.EMPTY) : 0;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return valid(slot) && slots.get(slot).isItemValid(stack);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (valid(slot)) {
            slots.get(slot).setStack(stack);
        }
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < slots.size();
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public static ListTag save(List<ItemSlot> slots) {
        ListTag list = new ListTag();
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) {
                CompoundTag entry = slots.get(i).save();
                entry.putShort("Slot", (short) i);
                list.add(entry);
            }
        }
        return list;
    }

    public static void load(List<ItemSlot> slots, ListTag list) {
        for (ItemSlot slot : slots) {
            slot.load(new CompoundTag());
        }
        for (Tag tag : list) {
            CompoundTag entry = (CompoundTag) tag;
            int index = entry.getShort("Slot");
            if (index >= 0 && index < slots.size()) {
                slots.get(index).load(entry);
            }
        }
    }
}
