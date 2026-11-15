package fr.pioneers.home.bag;

import java.util.function.Supplier;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 45, retour 65 de Guillaume : « On pose le sac dans le slot et l'interface lit le contenu ensuite on peut lier des cables
 * logistiques pour en faire ce qu'on veut » ; « Le port d'entree d'entrepot aura egalement un slot de sac a dos pour extraire le contenu du
 * sac pour que ce dernier parte dans l'entrepot ») : le CONTENU d'un sac pose dans une case, vu comme un inventaire d'objets (insertion et
 * extraction) par les cables logistiques, les entonnoirs et l'unite de l'entree d'entrepot.
 * <ul>
 *     <li>UNE SEULE SOURCE DE VERITE : le NBT du sac (cle {@code Bag} des sacs a dos, registre {@code Store} des sacs de rangement). Chaque
 *     operation relit le sac ACTUELLEMENT dans la case ({@code bag}) et ecrit aussitot dans son NBT ; le miroir analyse n'est garde que tant
 *     que la case tient le meme objet avec le meme NBT (identite) : un sac retire, remplace ou modifie ailleurs est relu, jamais une copie.
 *     Sans sac : 0 case, rien n'entre ni ne sort (un gestionnaire garde par un cable ne touche jamais un sac retire : aucune duplication) ;</li>
 *     <li>sac a dos : ses cases telles quelles (16 ou 32), tout sauf un sac ;</li>
 *     <li>sac de RANGEMENT (graines, pousses, poissons ; registre a grosses cases {@link BagLedger}) : une case par sorte au niveau du sac,
 *     montree en pile d'au plus 64 (variante la plus forte d'abord, celle que l'extraction rend) ; l'insertion suit la sorte du sac et sa
 *     contenance.</li>
 * </ul>
 */
public final class BagView implements IItemHandler {

    /** Pile montree par case au plus (jamais plus qu'une pile vanilla). */
    public static final int SHOWN = 64;

    private final Supplier<ItemStack> bag;
    private final Runnable changed;
    @Nullable
    private ItemStack cachedBag;
    @Nullable
    private Tag cachedTag;
    @Nullable
    private BagKind kind;
    @Nullable
    private ItemStackHandler backpack;
    @Nullable
    private BagLedger ledger;
    /** Lectures completes du NBT (banc : le miroir n'est relu qu'au changement de sac). */
    private long reads;

    /**
     * @param bag     le sac de la case (l'objet meme de la case, vide s'il n'y en a pas), relu a chaque operation
     * @param changed appele apres chaque ecriture dans le sac (tile a sauver, client a prevenir)
     */
    public BagView(Supplier<ItemStack> bag, Runnable changed) {
        this.bag = bag;
        this.changed = changed;
    }

    public long reads() {
        return reads;
    }

    @Nullable
    private static Tag keyTag(ItemStack stack, BagKind kind) {
        return stack.getTag() == null ? null : stack.getTag().get(kind.storage() ? BagLedger.TAG : BagContents.TAG);
    }

    /** Sac de la case (analyse si besoin) ; null s'il n'y en a pas. */
    @Nullable
    private ItemStack resolve() {
        ItemStack stack = bag.get();
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BagItem item)) {
            cachedBag = null;
            cachedTag = null;
            kind = null;
            backpack = null;
            ledger = null;
            return null;
        }
        Tag tag = keyTag(stack, item.kind);
        if (stack != cachedBag || tag != cachedTag || kind != item.kind) {
            cachedBag = stack;
            cachedTag = tag;
            kind = item.kind;
            reads++;
            if (kind.storage()) {
                ledger = BagStore.read(stack, kind);
                backpack = null;
            } else {
                backpack = BagContents.read(stack, kind);
                ledger = null;
            }
        }
        return stack;
    }

    /** Ecrit le miroir dans le NBT du sac (aussitot) et retient le nouveau NBT. */
    private void write(ItemStack stack) {
        if (kind != null && kind.storage() && ledger != null) {
            BagStore.write(stack, ledger);
        } else if (backpack != null) {
            BagContents.save(stack, backpack);
        }
        cachedTag = kind == null ? null : keyTag(stack, kind);
        changed.run();
    }

    /** Sorte du sac de la case (null sans sac). */
    @Nullable
    public BagKind kind() {
        return resolve() == null ? null : kind;
    }

    @Override
    public int getSlots() {
        if (resolve() == null) {
            return 0;
        }
        return ledger != null ? Math.max(kind.cases(ledger.tier), ledger.entries.size()) : backpack.getSlots();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (resolve() == null || slot < 0) {
            return ItemStack.EMPTY;
        }
        if (ledger != null) {
            return slot < ledger.entries.size() ? shown(ledger, slot) : ItemStack.EMPTY;
        }
        return slot < backpack.getSlots() ? backpack.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Case d'un sac de rangement : variante la plus forte, au plus {@link #SHOWN} objets (exactement ce que l'extraction rend). */
    private static ItemStack shown(BagLedger l, int index) {
        BagLedger.Entry e = l.entries.get(index);
        int variant = l.mode == BagLedger.Mode.GENES ? e.best() : 0;
        int have = l.mode == BagLedger.Mode.GENES ? e.variants.getOrDefault(variant, 0) : e.total;
        net.minecraft.world.item.Item it = BagStore.item(e.id);
        if (it == null || have <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(it, Math.min(have, Math.min(SHOWN, it.getMaxStackSize())));
        net.minecraft.nbt.CompoundTag t = e.tagOf(l.mode, variant);
        if (!t.isEmpty()) {
            s.setTag(t);
        }
        return s;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        ItemStack bagStack = resolve();
        if (stack.isEmpty() || bagStack == null || !kind.accepts(stack)) {
            return stack;
        }
        if (ledger != null) {
            String id = BagStore.idOf(stack);
            int room = Math.min(stack.getCount(), ledger.room(id, stack.getTag(), kind.cases(ledger.tier)));
            if (room <= 0) {
                return stack;
            }
            if (!simulate) {
                ledger.insert(id, stack.getTag(), room, kind.cases(ledger.tier), false);
                write(bagStack);
            }
            return room >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - room);
        }
        if (slot < 0 || slot >= backpack.getSlots()) {
            return stack;
        }
        ItemStack rest = backpack.insertItem(slot, stack, simulate);
        if (!simulate && rest.getCount() != stack.getCount()) {
            write(bagStack);
        }
        return rest;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack bagStack = resolve();
        if (amount <= 0 || bagStack == null || slot < 0) {
            return ItemStack.EMPTY;
        }
        if (ledger != null) {
            if (slot >= ledger.entries.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack shown = shown(ledger, slot);
            int n = Math.min(amount, shown.getCount());
            if (n <= 0) {
                return ItemStack.EMPTY;
            }
            if (simulate) {
                return shown.copyWithCount(n);
            }
            BagLedger.Entry e = ledger.entries.get(slot);
            BagLedger.Taken taken = ledger.takeVariant(slot, ledger.mode == BagLedger.Mode.GENES ? e.best() : 0, n);
            ItemStack out = BagStore.toStack(taken);
            write(bagStack);
            return out;
        }
        if (slot >= backpack.getSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = backpack.extractItem(slot, amount, simulate);
        if (!simulate && !out.isEmpty()) {
            write(bagStack);
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return SHOWN;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return resolve() != null && kind.accepts(stack);
    }

    // ------------------------------------------------------------------
    // Resume (interface, infobulles)
    // ------------------------------------------------------------------

    /** Objets dans le sac de la case (0 sans sac). */
    public int count() {
        if (resolve() == null) {
            return 0;
        }
        return ledger != null ? ledger.total() : BagContents.count(backpack);
    }

    /** Cases occupees (sortes pour un sac de rangement). */
    public int used() {
        if (resolve() == null) {
            return 0;
        }
        if (ledger != null) {
            return ledger.entries.size();
        }
        int n = 0;
        for (int i = 0; i < backpack.getSlots(); i++) {
            n += backpack.getStackInSlot(i).isEmpty() ? 0 : 1;
        }
        return n;
    }
}
