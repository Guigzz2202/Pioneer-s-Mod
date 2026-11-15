package fr.pioneers.logistics.warehouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : CONTENU d'une caisse d'entrepot (porte par la trappe). Un INDEX type -> quantite ({@code long}, bien au-dela de 64) :
 * aucune case a balayer, chaque operation touche une seule entree ; ordre des types = ordre d'arrivee. Bornes : {@link #maxTypes} types,
 * {@link #piles} piles de chaque type (x la taille de pile de l'objet : 64 lingots, 16 perles, 1 outil par pile). Une caisse plus petite que
 * son contenu (trappe reposee ailleurs) garde tout : on ne fait que retirer.
 * <p>
 * Sauvegarde sous la cle {@code storage} (liste {@code items} : objet sans quantite + {@code n} long) ; la meme cle va dans l'objet de la
 * trappe cassee ({@code BlockEntityTag}) : rien ne se perd, rien ne se double.
 */
public final class CrateStorage {
    // 1.0.5-beta (lot 22, retour 2 de Guillaume : « un meme objet peut occuper plusieurs types si le sien est plein mais que les autres types
    // sont disponibles ») : CAISSES A DEBORDEMENT. Les « types » sont des CASES DE TYPE ({@link #maxTypes}, de {@link #capacity} objets
    // chacune) ; un type plein continue dans les cases libres ({@link CrateRules}) ; les cases occupees ({@link #used}) se deduisent des
    // quantites : index et sauvegarde inchanges (les caisses d'avant se relisent telles quelles).

    /** Un type et sa quantite. */
    public static final class Entry {

        final ItemStack proto;
        long count;

        Entry(ItemStack proto, long count) {
            this.proto = proto;
            this.count = count;
        }

        public ItemStack proto() {
            return proto;
        }

        public long count() {
            return count;
        }
    }

    /** Cle d'un type : objet + NBT (meme regle qu'{@link ItemStack#isSameItemSameTags}). */
    private record Key(Item item, @Nullable CompoundTag tag) {

        static Key of(ItemStack s) {
            return new Key(s.getItem(), s.getTag());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.item == item && Objects.equals(k.tag, tag);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(item) * 31 + (tag == null ? 0 : tag.hashCode());
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<Key, Entry> index = new HashMap<>();
    private int maxTypes = CrateLayout.Length.SHORT.types(), piles = CrateLayout.Length.SHORT.piles;
    private long total;
    /** 1.0.5-beta (lot 22) : cases de type occupees (somme des {@link CrateRules#slots} de chaque type ; jamais sauvegarde). */
    private int used;
    /** Augmente a chaque changement (listes du comptoir, comparateur, synchro). */
    private int generation;
    @Nullable
    private Runnable listener;

    public void listener(@Nullable Runnable listener) {
        this.listener = listener;
    }

    /** Bornes de la caisse formee. */
    public void limits(CrateLayout.Length length) {
        maxTypes = length.types();
        piles = length.piles;
    }

    public int maxTypes() {
        return maxTypes;
    }

    public int piles() {
        return piles;
    }

    public int types() {
        return entries.size();
    }

    /** 1.0.5-beta (lot 22) : cases de type occupees (un type qui deborde en occupe plusieurs). */
    public int used() {
        return used;
    }

    /** Cases de type occupees par ce type (0 s'il est absent). */
    public int slotsOf(ItemStack stack) {
        Entry e = index.get(Key.of(stack));
        return e == null ? 0 : CrateRules.slots(e.count, capacity(e.proto));
    }

    public long total() {
        return total;
    }

    public int generation() {
        return generation;
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Quantite gardee d'un type. */
    public long count(ItemStack stack) {
        Entry e = index.get(Key.of(stack));
        return e == null ? 0 : e.count;
    }

    /** Contenance d'une CASE de ce type (piles x taille de pile) ; lot 22 : un type peut occuper plusieurs cases. */
    public long capacity(ItemStack stack) {
        return (long) piles * Math.max(1, stack.getMaxStackSize());
    }

    /** Remplissage 0..1 : objets ramenes en cases de type (un type qui deborde compte pour plusieurs), sur les cases de la caisse. */
    public double fill() {
        double sum = 0;
        for (Entry e : entries) {
            sum += e.count / (double) capacity(e.proto);
        }
        return CrateRules.fill(sum, maxTypes);
    }

    /** Signal du comparateur : 0 vide, 1..15 selon le remplissage. */
    public int comparator() {
        return entries.isEmpty() ? 0 : 1 + (int) Math.floor(fill() * 14);
    }

    /**
     * Quantite que la caisse accepterait de ce type : la place de ses cases, plus les CASES LIBRES (lot 22 : il deborde) ; un type absent
     * prend des cases libres s'il en reste.
     */
    public long room(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Entry e = index.get(Key.of(stack));
        return CrateRules.room(e == null ? 0 : e.count, capacity(stack), used, maxTypes);
    }

    /** Range jusqu'a {@code amount} objets du type {@code stack} ; renvoie la quantite rangee. */
    public long insert(ItemStack stack, long amount, boolean simulate) {
        long n = Math.min(amount, room(stack));
        if (n <= 0 || simulate) {
            return Math.max(0, n);
        }
        Key k = Key.of(stack);
        Entry e = index.get(k);
        if (e == null) {
            e = new Entry(stack.copyWithCount(1), 0);
            entries.add(e);
            index.put(k, e);
        }
        long cap = capacity(e.proto);
        used += CrateRules.slots(e.count + n, cap) - CrateRules.slots(e.count, cap);
        e.count += n;
        total += n;
        changed();
        return n;
    }

    /** Retire jusqu'a {@code amount} objets du type {@code stack} ; renvoie la quantite retiree. */
    public long extract(ItemStack stack, long amount, boolean simulate) {
        Key k = Key.of(stack);
        Entry e = index.get(k);
        if (e == null || amount <= 0) {
            return 0;
        }
        long n = Math.min(amount, e.count);
        if (simulate) {
            return n;
        }
        long cap = capacity(e.proto);
        used += CrateRules.slots(e.count - n, cap) - CrateRules.slots(e.count, cap);
        e.count -= n;
        total -= n;
        if (e.count <= 0) {
            entries.remove(e);
            index.remove(k);
        }
        changed();
        return n;
    }

    private void changed() {
        generation++;
        if (listener != null) {
            listener.run();
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = e.proto.save(new CompoundTag());
            t.remove("Count");
            t.putLong("n", e.count);
            list.add(t);
        }
        tag.put("items", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        entries.clear();
        index.clear();
        total = 0;
        used = 0;
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i).copy();
            long n = t.getLong("n");
            t.putByte("Count", (byte) 1);
            ItemStack proto = ItemStack.of(t);
            if (proto.isEmpty() || n <= 0) {
                continue;
            }
            Key k = Key.of(proto);
            Entry e = index.get(k);
            if (e == null) {
                e = new Entry(proto, 0);
                entries.add(e);
                index.put(k, e);
            }
            e.count += n;
            total += n;
        }
        for (Entry e : entries) {
            used += CrateRules.slots(e.count, capacity(e.proto));
        }
        generation++;
    }

    // ------------------------------------------------------------------ capacite d'objets des ports

    /**
     * Vue des ports pour les entonnoirs et le cable logistique : une case par type (pile au plus, a retirer), plus une case libre qui accepte
     * tout objet (range a sa place). Les cases d'un type n'acceptent que ce type.
     */
    public IItemHandler handler() {
        return handler;
    }

    private final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return entries.size() + 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            Entry e = entries.get(slot);
            return e.proto.copyWithCount((int) Math.min(e.count, e.proto.getMaxStackSize()));
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot >= 0 && slot < entries.size() && !ItemStack.isSameItemSameTags(entries.get(slot).proto, stack)) {
                return stack;
            }
            long n = insert(stack, stack.getCount(), simulate);
            return n >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) n);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= entries.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack proto = entries.get(slot).proto;
            long n = extract(proto, Math.min(amount, proto.getMaxStackSize()), simulate);
            return n <= 0 ? ItemStack.EMPTY : proto.copyWithCount((int) n);
        }

        @Override
        public int getSlotLimit(int slot) {
            //Lot 22 : la case d'un type tient ce qu'il a plus ce qui lui reste (ses cases et les cases libres)
            Entry e = slot >= 0 && slot < entries.size() ? entries.get(slot) : null;
            return (int) Math.min(Integer.MAX_VALUE, e == null ? (long) piles * 64 : e.count + room(e.proto));
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return room(stack) > 0;
        }
    };
}
