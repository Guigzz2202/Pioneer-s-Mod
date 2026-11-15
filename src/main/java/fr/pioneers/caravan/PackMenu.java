package fr.pioneers.caravan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : SACOCHES d'une bete batee (Maj + clic droit main vide) : ses cases utilisables (d'apres sa charge de bat), en
 * rangees de 9, puis l'inventaire du joueur. Le serveur travaille directement sur le contenu de la bete ; l'ecran se ferme si elle s'eloigne
 * (8 blocs), meurt ou perd son bat.
 */
public class PackMenu extends AbstractContainerMenu {

    public static final int WIDTH = 176, TOP = 18, SLOT = 18;

    public final int entityId, count;
    @Nullable
    private final Entity entity;

    /** Serveur : la bete et ses donnees. */
    public PackMenu(int id, Inventory inventory, Entity entity, CaravanData data) {
        this(id, inventory, entity.getId(), data.slots, entity, data.items);
    }

    private PackMenu(int id, Inventory inventory, int entityId, int slots, @Nullable Entity entity, IItemHandler items) {
        super(PiCaravan.PACK_MENU.get(), id);
        this.entityId = entityId;
        this.count = Math.max(1, Math.min(GeneratedCaravan.MAX_SLOTS, slots));
        this.entity = entity;
        for (int i = 0; i < this.count; i++) {
            addSlot(new SlotItemHandler(items, i, 8 + i % 9 * SLOT, TOP + i / 9 * SLOT) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return !(stack.getItem() instanceof PackSaddleItem) && super.mayPlace(stack);
                }
            });
        }
        int iy = inventoryY(this.count);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * SLOT, iy + r * SLOT));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, 8 + c * SLOT, iy + 58));
        }
    }

    public static PackMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        int e = buf.readVarInt(), n = buf.readVarInt();
        return new PackMenu(id, inventory, e, n, inventory.player.level().getEntity(e), new ItemStackHandler(GeneratedCaravan.MAX_SLOTS));
    }

    public static int rows(int slots) {
        return (slots + 8) / 9;
    }

    public static int inventoryY(int slots) {
        return TOP + rows(slots) * SLOT + 14;
    }

    public static int height(int slots) {
        return inventoryY(slots) + 83;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        if (entity == null || !entity.isAlive() || entity.distanceToSqr(p) > 64) {
            return false;
        }
        CaravanData d = PiCaravan.get(entity);
        return d != null && d.pack;
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < count) {
            if (!moveItemStackTo(stack, count, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof PackSaddleItem || !moveItemStackTo(stack, 0, count, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
