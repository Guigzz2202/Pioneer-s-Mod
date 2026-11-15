package fr.pioneers.home.bag;

import fr.pioneers.home.PiHome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : ecran d'un sac tenu en main. Le contenu est une copie de travail du NBT de l'objet, recopiee dans l'objet a CHAQUE
 * changement de case et a la fermeture (cote serveur). Garde-fous contre les duplications : la case de la barre d'action qui tient le sac est
 * verrouillee (ni prise, ni depot, ni echange par les touches 1-9 / F) ; l'ecran se ferme des que l'objet tenu n'est plus CE sac
 * ({@link #stillValid}, verifie a chaque tick par le serveur) ; pas de sac dans un sac ({@link BagKind#accepts}).
 */
public class BagMenu extends AbstractContainerMenu {

    public static final int WIDTH = 176, TOP = 18, SLOT = 18;

    public final BagKind kind;
    public final InteractionHand hand;
    private final ItemStack bag;
    private final ItemStackHandler items;
    private final Player player;
    /** Case de l'inventaire du joueur qui tient le sac (barre d'action), -1 pour la main secondaire. */
    private final int lockedSlot;
    private final int inventoryY;

    /** Serveur : le sac tenu dans {@code hand}. */
    public BagMenu(int id, Inventory inventory, InteractionHand hand, BagKind kind) {
        this(id, inventory, hand, kind, inventory.player.getItemInHand(hand));
    }

    private BagMenu(int id, Inventory inventory, InteractionHand hand, BagKind kind, ItemStack bag) {
        super(PiHome.BAG_MENU.get(), id);
        this.kind = kind;
        this.hand = hand;
        this.bag = bag;
        this.player = inventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        boolean server = !inventory.player.level().isClientSide;
        this.items = server ? new ItemStackHandler(kind.slots) {
            @Override
            protected void onContentsChanged(int slot) {
                BagContents.save(BagMenu.this.bag, this);
            }
        } : new ItemStackHandler(kind.slots);
        if (server) {
            ItemStackHandler stored = BagContents.read(bag, kind);
            for (int i = 0; i < Math.min(stored.getSlots(), kind.slots); i++) {
                items.setStackInSlot(i, stored.getStackInSlot(i));
            }
            if (kind == BagKind.FISH_BAG) {
                //1.0.4-beta (lot 4) : un panier d'avant (poissons de tailles differentes) se range par espece a l'ouverture
                FishStacking.compact(items);
            }
        }
        int x0 = (WIDTH - kind.columns * SLOT) / 2 + 1;
        for (int i = 0; i < kind.slots; i++) {
            addSlot(new SlotItemHandler(items, i, x0 + i % kind.columns * SLOT, TOP + i / kind.columns * SLOT) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return BagMenu.this.kind.accepts(stack);
                }
            });
        }
        inventoryY = inventoryY(kind);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * SLOT, inventoryY + r * SLOT));
            }
        }
        for (int c = 0; c < 9; c++) {
            if (c == lockedSlot) {
                addSlot(new Slot(inventory, c, 8 + c * SLOT, inventoryY + 58) {
                    @Override
                    public boolean mayPickup(@NotNull Player p) {
                        return false;
                    }

                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return false;
                    }
                });
            } else {
                addSlot(new Slot(inventory, c, 8 + c * SLOT, inventoryY + 58));
            }
        }
    }

    /** Client : sac et main lus dans le tampon d'ouverture. */
    public static BagMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        BagKind kind = BagKind.byOrdinal(buf.readByte());
        return new BagMenu(id, inventory, hand, kind, inventory.player.getItemInHand(hand));
    }

    /** Haut de l'inventaire du joueur (sous les rangees du sac et le libelle). */
    public static int inventoryY(BagKind kind) {
        return TOP + kind.rows() * SLOT + 14;
    }

    /** Hauteur de l'ecran. */
    public static int height(BagKind kind) {
        return inventoryY(kind) + 83;
    }

    public int inventoryTop() {
        return inventoryY;
    }

    /** Case verrouillee (index dans le menu) ou -1. */
    public int lockedMenuSlot() {
        return lockedSlot < 0 ? -1 : kind.slots + 27 + lockedSlot;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        //L'objet tenu doit rester CE sac (meme pile, pas une copie) : sinon fermeture, rien n'est recopie ailleurs
        return p.isAlive() && p.getItemInHand(hand) == bag && bag.getItem() instanceof BagItem b && b.kind == kind;
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType type, @NotNull Player p) {
        //Touches 1-9 et F : aucun echange avec la case qui tient le sac, ni avec la main secondaire si c'est elle
        if (type == ClickType.SWAP && (button == lockedSlot || lockedSlot < 0 && button == Inventory.SLOT_OFFHAND)) {
            return;
        }
        if (slotId >= 0 && slotId == lockedMenuSlot()) {
            return;
        }
        //1.0.4-beta (lot 4) : un poisson pose sur le panier de peche prend la taille commune de son espece (FishStacking) : il s'empile
        boolean fish = kind == BagKind.FISH_BAG;
        if (fish && slotId >= 0 && slotId < kind.slots && (type == ClickType.PICKUP || type == ClickType.QUICK_CRAFT)) {
            FishStacking.align(items, getCarried());
        }
        super.clicked(slotId, button, type, p);
        if (fish) {
            FishStacking.unify(items);
        }
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == lockedMenuSlot()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < kind.slots) {
            if (!moveItemStackTo(stack, kind.slots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (kind == BagKind.FISH_BAG && kind.accepts(stack)) {
                FishStacking.align(items, stack);
                copy = stack.copy();
            }
            if (!kind.accepts(stack) || !moveItemStackTo(stack, 0, kind.slots, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(@NotNull Player p) {
        super.removed(p);
        if (!p.level().isClientSide && p.getItemInHand(hand) == bag) {
            BagContents.save(bag, items);
        }
    }

    /** Le sac ouvert est-il {@code stack} (meme pile) ? Le ramassage automatique laisse alors faire l'ecran. */
    public boolean holds(ItemStack stack) {
        return stack == bag;
    }

    /** Range une pile dans le sac ouvert (ramassage pendant que l'ecran est ouvert) ; rend le reste. */
    public ItemStack insert(ItemStack stack) {
        if (kind == BagKind.FISH_BAG) {
            FishStacking.align(items, stack);
        }
        return net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(items, stack, false);
    }

    public Player player() {
        return player;
    }
}
