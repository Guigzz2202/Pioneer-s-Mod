package fr.pioneers.core.menu;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.network.PacketBigSlots;
import fr.pioneers.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Menu d'un bloc du socle : cases du bloc (des {@link ItemSlot}), inventaire du joueur, transfert Maj + clic, et synchronisation de valeurs
 * entieres ou longues (par morceaux de 16 bits, comme le veut le paquet vanilla).
 * <p>
 * 1.12.3 (lot F3) : une case peut garder plusieurs piles normales ({@link ItemSlot#stacked}, buffers d'objets). Le paquet vanilla des cases
 * ecrit la quantite sur un octet : les piles de plus de 127 objets sont renvoyees juste apres par {@link PacketBigSlots}. On ne prend
 * jamais plus d'une pile normale d'une telle case (clic, Maj + clic, touches de la barre d'action), et un echange qui mettrait une grosse
 * pile dans la main ou l'inventaire est refuse.
 */
public abstract class PiTileMenu<T extends BlockEntity> extends AbstractContainerMenu {

    @Nullable
    protected final T tile;
    private int tileSlots;
    /** 1.12.3 : joueur du menu (serveur : destinataire des grosses piles) ; derniere grosse pile envoyee par case du bloc. */
    @Nullable
    private Player owner;
    private final List<ItemStack> bigSent = new ArrayList<>();
    private boolean bigDirty = true;

    protected PiTileMenu(@Nullable MenuType<?> type, int id, @Nullable T tile) {
        super(type, id);
        this.tile = tile;
    }

    @Nullable
    public T getTile() {
        return tile;
    }

    protected void addTileSlots(List<ItemSlot> slots) {
        for (ItemSlot slot : slots) {
            addSlot(new MenuSlot(slot));
            tileSlots++;
            bigSent.add(ItemStack.EMPTY);
        }
    }

    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        owner = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    // ------------------------------------------------------------------
    // Valeurs synchronisees
    // ------------------------------------------------------------------

    /** Entier (deux morceaux de 16 bits). */
    protected void trackInt(IntSupplier getter, IntConsumer setter) {
        int[] client = new int[1];
        for (int part = 0; part < 2; part++) {
            int shift = part * 16;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return getter.getAsInt() >>> shift & 0xFFFF;
                }

                @Override
                public void set(int value) {
                    client[0] = client[0] & ~(0xFFFF << shift) | (value & 0xFFFF) << shift;
                    setter.accept(client[0]);
                }
            });
        }
    }

    /** Entier long (quatre morceaux de 16 bits). */
    protected void trackLong(LongSupplier getter, LongConsumer setter) {
        long[] client = new long[1];
        for (int part = 0; part < 4; part++) {
            int shift = part * 16;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return (int) (getter.getAsLong() >>> shift & 0xFFFF);
                }

                @Override
                public void set(int value) {
                    client[0] = client[0] & ~(0xFFFFL << shift) | (value & 0xFFFFL) << shift;
                    setter.accept(client[0]);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Maj + clic
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < tileSlots) {
            //Du bloc vers l'inventaire (barre d'action en dernier) ; 1.12.3 : une pile normale au plus par Maj + clic
            ItemStack moving = stack.copyWithCount(Math.min(stack.getCount(), Math.max(1, stack.getMaxStackSize())));
            original = moving.copy();
            if (moveItemStackTo(moving, tileSlots, slots.size(), true)) {
                slot.remove(original.getCount() - moving.getCount());
            }
            return ItemStack.EMPTY;
        }
        //De l'inventaire vers les cases du bloc qui l'acceptent
        boolean moved = false;
        for (int i = 0; i < tileSlots && !stack.isEmpty(); i++) {
            Slot target = slots.get(i);
            if (target instanceof MenuSlot menuSlot && target.mayPlace(stack)) {
                ItemStack rest = menuSlot.slot.insert(stack, Action.EXECUTE, Automation.MANUAL);
                if (rest.getCount() != stack.getCount()) {
                    moved = true;
                    stack = rest;
                }
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        slot.set(stack);
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // 1.12.3 : cases a plusieurs piles (buffers d'objets)
    // ------------------------------------------------------------------

    /** Client, dessin des cases : masque la quantite vanilla des grosses piles (l'ecran dessine la sienne, abregee). */
    public void hideBigCounts(boolean hide) {
        for (int i = 0; i < tileSlots; i++) {
            if (slots.get(i) instanceof MenuSlot menuSlot) {
                menuSlot.hideCount = hide;
            }
        }
    }

    /** Case du bloc dont la pile depasse une pile normale. */
    private boolean oversized(int index) {
        if (index < 0 || index >= tileSlots || !(slots.get(index) instanceof MenuSlot menuSlot) || menuSlot.slot.isEmpty()) {
            return false;
        }
        ItemStack stack = menuSlot.slot.getStack();
        return stack.getCount() > stack.getMaxStackSize();
    }

    /**
     * Refuse les gestes vanilla qui mettraient une pile de plus d'une pile normale dans la main ou dans l'inventaire : echange avec un objet
     * different tenu a la souris, echange avec une case de la barre d'action (ou la main secondaire) occupee. Les prises (clic, Maj + clic,
     * jeter) passent par {@link MenuSlot#remove} : une pile normale au plus.
     */
    @Override
    public void clicked(int index, int button, @NotNull ClickType type, @NotNull Player player) {
        if (oversized(index)) {
            ItemStack big = slots.get(index).getItem();
            if (type == ClickType.PICKUP && !getCarried().isEmpty() && !ItemStack.isSameItemSameTags(getCarried(), big)) {
                return;
            }
            if (type == ClickType.SWAP) {
                //Le geste vanilla mettrait toute la pile dans la case de la barre d'action : on n'en prend qu'une pile normale, et seulement
                //vers une case vide (un echange rendrait la grosse pile a l'inventaire)
                Slot slot = slots.get(index);
                boolean target = button >= 0 && button < 9 || button == Inventory.SLOT_OFFHAND;
                if (target && player.getInventory().getItem(button).isEmpty() && slot.mayPickup(player)) {
                    ItemStack taken = slot.remove(big.getMaxStackSize());
                    if (!taken.isEmpty()) {
                        player.getInventory().setItem(button, taken);
                        slot.onTake(player, taken);
                    }
                }
                bigDirty = true;
                return;
            }
        }
        bigDirty = true;
        super.clicked(index, button, type, player);
    }

    /** Le client a annonce sa vue d'une case (paquet de clic, quantite sur un octet) : la vraie pile sera renvoyee. */
    @Override
    public void setRemoteSlot(int index, @NotNull ItemStack stack) {
        super.setRemoteSlot(index, stack);
        bigDirty = true;
    }

    @Override
    public void setRemoteSlotNoCopy(int index, @NotNull ItemStack stack) {
        super.setRemoteSlotNoCopy(index, stack);
        bigDirty = true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        sendBigSlots(false);
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        sendBigSlots(true);
    }

    /** Envoie les piles de plus de 127 objets qui ont change (ou toutes : {@code all}) apres le paquet vanilla des cases. */
    private void sendBigSlots(boolean all) {
        if (!(owner instanceof ServerPlayer player) || player.containerMenu != this && !all) {
            return;
        }
        boolean force = all || bigDirty;
        bigDirty = false;
        List<Integer> index = null;
        List<ItemStack> stacks = null;
        for (int i = 0; i < tileSlots; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.getCount() <= Byte.MAX_VALUE) {
                bigSent.set(i, ItemStack.EMPTY);
                continue;
            }
            if (!force && ItemStack.matches(bigSent.get(i), stack)) {
                continue;
            }
            bigSent.set(i, stack.copy());
            if (index == null) {
                index = new ArrayList<>();
                stacks = new ArrayList<>();
            }
            index.add(i);
            stacks.add(stack.copy());
        }
        if (index != null) {
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PacketBigSlots.of(containerId, index, stacks));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return tile != null && !tile.isRemoved() && tile.getLevel() == player.level()
               && player.distanceToSqr(tile.getBlockPos().getX() + 0.5, tile.getBlockPos().getY() + 0.5, tile.getBlockPos().getZ() + 0.5) <= reach() * reach() && valid();
    }

    /** Portee de l'interface (blocs). */
    protected double reach() {
        return 8;
    }

    /** Condition propre au menu (multibloc toujours forme...). */
    protected boolean valid() {
        return true;
    }
}
