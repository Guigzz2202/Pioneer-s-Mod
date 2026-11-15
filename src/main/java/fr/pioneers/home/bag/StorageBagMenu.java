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
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 31, retour 42) : ecran d'un sac de RANGEMENT tenu en main (graines, pousses, poissons). Les cases du sac ne sont PAS des
 * cases de menu vanilla (une pile de menu ne passe le reseau qu'avec un compte sur un octet) : elles sont dessinees par l'ecran d'apres le NBT
 * du sac (synchronise par l'inventaire) et chaque geste part en {@link PacketBag} ({@link #act}). Le serveur ecrit dans le sac tenu (meme
 * objet, jamais une copie) : pas de duplication possible, ramassage automatique compris pendant que l'ecran est ouvert.
 * <p>
 * Garde-fous (comme {@link BagMenu}) : la case de la barre d'action qui tient le sac est verrouillee (ni prise, ni depot, ni touches 1-9 / F) ;
 * l'ecran se ferme des que l'objet tenu n'est plus CE sac ({@link #stillValid}) ; sac en main secondaire : sa case est ajoutee hors de
 * l'ecran (verrouillee) pour que l'inventaire envoie au client chaque changement du sac.
 */
public class StorageBagMenu extends AbstractContainerMenu {

    /** Mise en page (relative au panneau ; = maquette {@code tools/bags/bag_preview.py}). */
    public static final int WIDTH = 176, SLOT = 18, COLUMNS = 9, GRID_X = 8, TOOL_Y = 16, TOOL_H = 11, GRID_TOP = 31;

    /** Gestes (paquet {@link PacketBag}). */
    public static final int TAKE = 0, TO_INVENTORY = 1, DEPOSIT = 2, SELECT = 3, PICKUP = 4, FILTER = 5, STORE_ALL = 6, DUMP_OPEN = 7;

    public final BagKind kind;
    public final InteractionHand hand;
    public final int tier;
    private final ItemStack bag;
    private final Player player;
    private final int lockedSlot;
    private final int inventoryY;

    public StorageBagMenu(int id, Inventory inventory, InteractionHand hand, BagKind kind) {
        this(id, inventory, hand, kind, inventory.player.getItemInHand(hand), BagStore.read(inventory.player.getItemInHand(hand), kind).tier);
    }

    private StorageBagMenu(int id, Inventory inventory, InteractionHand hand, BagKind kind, ItemStack bag, int tier) {
        super(PiHome.STORAGE_BAG_MENU.get(), id);
        this.kind = kind;
        this.hand = hand;
        this.bag = bag;
        this.tier = tier;
        this.player = inventory.player;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        this.inventoryY = inventoryY(kind, tier);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, 8 + c * SLOT, inventoryY + r * SLOT));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(c == lockedSlot ? locked(inventory, c, 8 + c * SLOT, inventoryY + 58) : new Slot(inventory, c, 8 + c * SLOT, inventoryY + 58));
        }
        if (hand == InteractionHand.OFF_HAND) {
            //case de la main secondaire hors de l'ecran : l'inventaire envoie au client chaque changement du sac
            addSlot(locked(inventory, Inventory.SLOT_OFFHAND, -2000, -2000));
        }
    }

    private static Slot locked(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(@NotNull Player p) {
                return false;
            }

            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }

            @Override
            public boolean isActive() {
                return x > -1000;
            }
        };
    }

    public static StorageBagMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        BagKind kind = BagKind.byOrdinal(buf.readByte());
        int tier = buf.readByte();
        return new StorageBagMenu(id, inventory, hand, kind, inventory.player.getItemInHand(hand), tier);
    }

    // ------------------------------------------------------------------ mise en page

    public static int rows(BagKind kind, int tier) {
        return (kind.cases(tier) + COLUMNS - 1) / COLUMNS;
    }

    /** Ligne d'etat sous la grille. */
    public static int infoY(BagKind kind, int tier) {
        return GRID_TOP + rows(kind, tier) * SLOT + 3;
    }

    /** Haut de l'inventaire du joueur. */
    public static int inventoryY(BagKind kind, int tier) {
        return infoY(kind, tier) + 23;
    }

    public static int height(BagKind kind, int tier) {
        return inventoryY(kind, tier) + 83;
    }

    public int inventoryTop() {
        return inventoryY;
    }

    /** Index dans le menu de la case verrouillee (-1 : aucune). */
    public int lockedMenuSlot() {
        return lockedSlot < 0 ? -1 : 27 + lockedSlot;
    }

    /** Le sac tenu (client : la pile a jour de la main, remplacee a chaque envoi de l'inventaire). */
    public ItemStack bag() {
        return player.level().isClientSide ? player.getItemInHand(hand) : bag;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return p.isAlive() && p.getItemInHand(hand) == bag && bag.getItem() instanceof BagItem b && b.kind == kind;
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType type, @NotNull Player p) {
        if (type == ClickType.SWAP && (button == lockedSlot || lockedSlot < 0 && button == Inventory.SLOT_OFFHAND)) {
            return;
        }
        if (slotId >= 0 && slotId == lockedMenuSlot()) {
            return;
        }
        super.clicked(slotId, button, type, p);
    }

    /** Maj + clic sur une case de l'inventaire : la pile va dans le sac (rangement a la main : filtre ignore). */
    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player p, int index) {
        if (index < 0 || index >= slots.size() || index == lockedMenuSlot()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(p) || p.level().isClientSide) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack rest = BagStore.insert(bag, kind, stack, false, false);
        if (rest.getCount() == stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack before = stack.copy();
        slot.setByPlayer(rest);
        return rest.isEmpty() ? ItemStack.EMPTY : before;
    }

    // ------------------------------------------------------------------ gestes (serveur)

    /** Case du registre visee par le client : l'index s'il porte bien l'objet {@code id}, sinon la premiere case de cet objet. */
    static int resolve(BagLedger l, int index, String id) {
        if (index >= 0 && index < l.entries.size() && l.entries.get(index).id.equals(id)) {
            return index;
        }
        for (int i = 0; i < l.entries.size(); i++) {
            if (l.entries.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Un geste de l'ecran (serveur ; le paquet a deja verifie que CE menu est ouvert et valide). */
    public void act(Player p, int op, int index, String id, int arg) {
        if (p.level().isClientSide || !stillValid(p)) {
            return;
        }
        BagLedger l = BagStore.read(bag, kind);
        int cases = kind.cases(l.tier);
        switch (op) {
            case TAKE -> {
                int i = resolve(l, index, id);
                if (i < 0) {
                    return;
                }
                ItemStack carried = getCarried();
                BagLedger.Taken t;
                if (carried.isEmpty()) {
                    ItemStack proto = BagStore.display(l, l.entries.get(i));
                    if (proto.isEmpty()) {
                        return;
                    }
                    int max = arg == 1 ? Math.max(1, (Math.min(proto.getMaxStackSize(), l.entries.get(i).total) + 1) / 2) : proto.getMaxStackSize();
                    t = l.take(i, max);
                    ItemStack out = BagStore.toStack(t);
                    if (out.isEmpty()) {
                        l.giveBack(t, t.count(), cases);
                        return;
                    }
                    setCarried(out);
                } else {
                    //meme objet en main : on complete la pile tenue (meme variante)
                    if (!BagStore.idOf(carried).equals(l.entries.get(i).id) || l.find(id, carried.getTag()) != l.entries.get(i)) {
                        return;
                    }
                    int room = carried.getMaxStackSize() - carried.getCount();
                    if (room <= 0) {
                        return;
                    }
                    int variant = l.variantOf(carried.getTag());
                    if (l.mode == BagLedger.Mode.SIZE) {
                        //poissons : la pile tenue prend la plus grande taille (comme au panier)
                        t = l.take(i, room);
                        ItemStack got = BagStore.toStack(t);
                        if (got.isEmpty()) {
                            return;
                        }
                        got.grow(carried.getCount());
                        setCarried(got);
                    } else {
                        t = l.takeVariant(i, Math.max(0, variant), room);
                        carried.grow(t.count());
                    }
                }
            }
            case TO_INVENTORY -> {
                int i = resolve(l, index, id);
                if (i < 0) {
                    return;
                }
                ItemStack proto = BagStore.display(l, l.entries.get(i));
                if (proto.isEmpty()) {
                    return;
                }
                BagLedger.Taken t = l.take(i, proto.getMaxStackSize());
                ItemStack out = BagStore.toStack(t);
                moveItemStackTo(out, 0, 36, true);
                if (!out.isEmpty()) {
                    l.giveBack(t, out.getCount(), cases);
                }
            }
            case DEPOSIT -> {
                ItemStack carried = getCarried();
                if (carried.isEmpty() || !kind.accepts(carried)) {
                    return;
                }
                if (arg == 1) {
                    ItemStack rest = BagStore.insert(bag, kind, carried.copyWithCount(1), false, false);
                    if (rest.isEmpty()) {
                        carried.shrink(1);
                    }
                } else {
                    setCarried(BagStore.insert(bag, kind, carried, false, false));
                }
                broadcastChanges();
                return;
            }
            case SELECT -> {
                int i = resolve(l, index, id);
                if (i < 0) {
                    return;
                }
                String sel = l.entries.get(i).id;
                l.selected = sel.equals(l.selected) ? "" : sel;
            }
            case PICKUP -> l.pickup = !l.pickup;
            case FILTER -> {
                String f = index < 0 ? (getCarried().isEmpty() ? "" : BagStore.idOf(getCarried())) : id;
                if (f.isEmpty() || index >= 0 && resolve(l, index, id) < 0) {
                    return;
                }
                if (!l.filter.remove(f) && l.filter.size() < BagLedger.FILTER_MAX) {
                    l.filter.add(f);
                }
            }
            case STORE_ALL -> {
                //inventaire principal seulement (pas la barre d'action : on y garde ce qu'on plante a la main) ; filtre respecte
                BagStore.write(bag, l);
                for (int s = 0; s < 27; s++) {
                    Slot slot = slots.get(s);
                    ItemStack st = slot.getItem();
                    if (!st.isEmpty() && kind.accepts(st) && !l.filter.contains(BagStore.idOf(st))) {
                        ItemStack rest = BagStore.insert(bag, kind, st, false, false);
                        if (rest.getCount() != st.getCount()) {
                            slot.setByPlayer(rest);
                        }
                    }
                }
                broadcastChanges();
                return;
            }
            default -> {
                return;
            }
        }
        BagStore.write(bag, l);
        broadcastChanges();
    }

    public Player player() {
        return player;
    }
}
