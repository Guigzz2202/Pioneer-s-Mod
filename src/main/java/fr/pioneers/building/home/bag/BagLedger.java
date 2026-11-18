package fr.pioneers.building.home.bag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 31, retour 42 « Améliorer SÉVÈREMENT le sac à graines, le sac à pousses et le panier de pêche ») : REGISTRE d'un sac de
 * rangement, PUR (seulement les classes NBT du jeu : le banc {@code tools/bags/BagBench.java} le lit hors jeu).
 * <p>
 * Une CASE = une SORTE : identifiant d'objet + NBT hors de la cle de VARIANTE ; elle compte jusqu'a {@link #capacity(int)} objets (1 024,
 * 4 096, 16 384 selon le niveau du sac) — bien plus qu'une pile de 64, et sans passer par une case de menu vanilla (le nombre d'objets d'une
 * pile est ecrit sur UN OCTET dans les paquets et dans {@code ItemStack.save} : au-dela de 127, il est faux).
 * <ul>
 *   <li>{@link Mode#GENES} (graines, pousses) : chaque graine garde ses genes EXACTS (cle {@code Genes}, entier) : la case tient des
 *   sous-comptes par genes ({@link Entry#variants}) ; sans cela, les graines filles mutees d'un champ (0,15 a 0,45 de chance par gene)
 *   faisaient des dizaines de piles differentes, dans l'inventaire comme dans l'ancien sac ;</li>
 *   <li>{@link Mode#SIZE} (panier de peche) : meme regle que {@link FishStacking} (1.0.4-beta lot 4) : poissons d'une espece ensemble, la
 *   case garde la PLUS GRANDE taille (cle {@code Size}) ;</li>
 *   <li>{@link Mode#NONE} : NBT exact.</li>
 * </ul>
 * Rangement TOUJOURS TRIE ({@link #sort}) : ordre des identifiants (chemin puis espace de noms), puis NBT. Reglages du sac dans le meme NBT :
 * niveau ({@code Tier}), ramassage arrete ({@code NoPickup}), objets bloques au ramassage ({@code Filter}), sorte choisie pour semer ou
 * poser ({@code Sel}). Cle de l'objet : {@link #TAG} ({@code Store}). MIGRATION : un sac d'avant (cle {@link #LEGACY} = format de
 * {@code ItemStackHandler.serializeNBT}) est lu tel quel et range ici sans limite de case ni de nombre (rien ne se perd) ; l'ecriture retire
 * l'ancienne cle.
 */
public final class BagLedger {

    /** Cle du registre dans le NBT de l'objet ; ancienne cle (1.0.3-beta lot 7 a 1.0.5-beta lot 30). */
    public static final String TAG = "Store", LEGACY = "Bag";
    /** Cles internes. */
    public static final String CASES = "Cases", TIER = "Tier", NO_PICKUP = "NoPickup", FILTER = "Filter", SEL = "Sel", TOTAL = "Total",
          ID = "id", NBT = "tag", N = "n", S = "s", V = "v";
    /** Cles de variante. */
    public static final String GENES_KEY = "Genes", SIZE_KEY = "Size";
    /** Objets par case selon le niveau (I, II, III). */
    public static final int[] CAPACITY = {1_024, 4_096, 16_384};
    /** Niveaux. */
    public static final int TIERS = CAPACITY.length;
    /** Objets bloques au ramassage, au plus. */
    public static final int FILTER_MAX = 54;

    public enum Mode { NONE, GENES, SIZE }

    public static int capacity(int tier) {
        return CAPACITY[Math.max(0, Math.min(TIERS - 1, tier))];
    }

    /** Une sorte rangee. */
    public static final class Entry {

        public final String id;
        /** NBT de l'objet sans la cle de variante (jamais null ; vide = sans NBT). */
        public final CompoundTag base;
        public int total;
        /** {@link Mode#SIZE} : plus grande taille (-1 : aucune). */
        public int size = -1;
        /** {@link Mode#GENES} : genes empaquetes (0 = genes de base, sans NBT) -> nombre. */
        public final TreeMap<Integer, Integer> variants = new TreeMap<>();

        Entry(String id, CompoundTag base) {
            this.id = id;
            this.base = base;
        }

        boolean same(String id, CompoundTag base) {
            return this.id.equals(id) && this.base.equals(base);
        }

        /** Variante la plus forte (genes de plus haut total ; egalite : la plus haute valeur), ou 0. */
        public int best() {
            int best = 0, score = -1;
            for (Map.Entry<Integer, Integer> e : variants.entrySet()) {
                int sc = score(e.getKey());
                if (e.getValue() > 0 && (sc > score || sc == score && e.getKey() > best)) {
                    score = sc;
                    best = e.getKey();
                }
            }
            return best;
        }

        /** Variantes de la plus forte a la plus faible. */
        public List<Integer> ranked() {
            List<Integer> l = new ArrayList<>(variants.keySet());
            l.sort(Comparator.comparingInt((Integer v) -> -score(v)).thenComparingInt(v -> -v));
            return l;
        }

        /** NBT complet d'un objet de cette case (variante {@code variant} en mode GENES). */
        public CompoundTag tagOf(Mode mode, int variant) {
            CompoundTag t = base.copy();
            if (mode == Mode.GENES && variant > 0) {
                t.putInt(GENES_KEY, variant);
            } else if (mode == Mode.SIZE && size >= 0) {
                t.putInt(SIZE_KEY, size);
            }
            return t;
        }
    }

    /** Ce qu'on sort du sac : un objet, son NBT complet (null = sans NBT), un nombre. */
    public record Taken(String id, @Nullable CompoundTag tag, int count, int variant) {

        public boolean empty() {
            return count <= 0;
        }
    }

    public final Mode mode;
    public final List<Entry> entries = new ArrayList<>();
    public final Set<String> filter = new LinkedHashSet<>();
    public int tier;
    public boolean pickup = true;
    /** Sorte choisie (identifiant d'objet ; vide = la premiere). */
    public String selected = "";
    /** Vrai si le NBT lu etait l'ancien format (a recopier dans le nouveau). */
    public boolean migrated;

    public BagLedger(Mode mode) {
        this.mode = mode;
    }

    // ------------------------------------------------------------------ genes

    /** Total des genes empaquetes ({@code Genes} : rendement | croissance << 4 | resistance << 8 | fertilite << 12 ; 0 = 1, 1, 1, 1). */
    public static int score(int packed) {
        if (packed <= 0) {
            return 4;
        }
        int s = 0;
        for (int i = 0; i < 4; i++) {
            s += Math.max(1, Math.min(10, packed >> 4 * i & 15));
        }
        return s;
    }

    /** Etoiles (meme bareme que {@code Genes.stars}). */
    public static int stars(int packed) {
        int s = score(packed);
        return s <= 10 ? 1 : s <= 17 ? 2 : s <= 25 ? 3 : s <= 33 ? 4 : 5;
    }

    // ------------------------------------------------------------------ cle d'une sorte

    /** NBT sans la cle de variante, et valeur de la variante (genes, taille ; -1 sinon). */
    public CompoundTag baseOf(@Nullable CompoundTag tag) {
        CompoundTag b = tag == null ? new CompoundTag() : tag.copy();
        if (mode == Mode.GENES) {
            b.remove(GENES_KEY);
        } else if (mode == Mode.SIZE) {
            b.remove(SIZE_KEY);
        }
        return b;
    }

    public int variantOf(@Nullable CompoundTag tag) {
        if (tag == null) {
            return mode == Mode.SIZE ? -1 : 0;
        }
        if (mode == Mode.GENES) {
            return tag.contains(GENES_KEY) ? Math.max(0, tag.getInt(GENES_KEY)) : 0;
        }
        if (mode == Mode.SIZE) {
            return tag.contains(SIZE_KEY) ? tag.getInt(SIZE_KEY) : -1;
        }
        return 0;
    }

    @Nullable
    public Entry find(String id, @Nullable CompoundTag tag) {
        CompoundTag b = baseOf(tag);
        for (Entry e : entries) {
            if (e.same(id, b)) {
                return e;
            }
        }
        return null;
    }

    public int index(Entry e) {
        return entries.indexOf(e);
    }

    // ------------------------------------------------------------------ capacite

    public int perCase() {
        return capacity(tier);
    }

    /** Place restante pour cet objet (case existante, ou nouvelle case si une est libre). */
    public int room(String id, @Nullable CompoundTag tag, int cases) {
        Entry e = find(id, tag);
        if (e != null) {
            return Math.max(0, perCase() - e.total);
        }
        return entries.size() < cases ? perCase() : 0;
    }

    public int total() {
        int n = 0;
        for (Entry e : entries) {
            n += e.total;
        }
        return n;
    }

    // ------------------------------------------------------------------ operations

    /** Range {@code count} objets ; rend le nombre range (0 si plus de place). {@code force} : sans limite (migration). */
    public int insert(String id, @Nullable CompoundTag tag, int count, int cases, boolean force) {
        if (count <= 0 || id.isEmpty()) {
            return 0;
        }
        Entry e = find(id, tag);
        if (e == null) {
            if (!force && entries.size() >= cases) {
                return 0;
            }
            e = new Entry(id, baseOf(tag));
            entries.add(e);
            sort();
        }
        int put = force ? count : Math.min(count, Math.max(0, perCase() - e.total));
        if (put <= 0) {
            return 0;
        }
        e.total += put;
        int v = variantOf(tag);
        if (mode == Mode.GENES) {
            e.variants.merge(v, put, Integer::sum);
        } else if (mode == Mode.SIZE) {
            e.size = Math.max(e.size, v);
        }
        return put;
    }

    /** Sort au plus {@code max} objets de la case {@code index} (variante la plus forte d'abord, une seule variante). */
    public Taken take(int index, int max) {
        if (index < 0 || index >= entries.size() || max <= 0) {
            return new Taken("", null, 0, 0);
        }
        Entry e = entries.get(index);
        int variant = mode == Mode.GENES ? e.best() : 0;
        return takeVariant(index, variant, max);
    }

    /** Sort au plus {@code max} objets d'une variante precise de la case. */
    public Taken takeVariant(int index, int variant, int max) {
        Entry e = entries.get(index);
        int have = mode == Mode.GENES ? e.variants.getOrDefault(variant, 0) : e.total;
        int n = Math.min(have, max);
        if (n <= 0) {
            return new Taken("", null, 0, 0);
        }
        CompoundTag t = e.tagOf(mode, variant);
        e.total -= n;
        if (mode == Mode.GENES) {
            if (have - n <= 0) {
                e.variants.remove(variant);
            } else {
                e.variants.put(variant, have - n);
            }
        }
        if (e.total <= 0) {
            entries.remove(index);
        }
        return new Taken(e.id, t.isEmpty() ? null : t, n, variant);
    }

    /** Rentre ce qui a ete sorti puis pas utilise (toujours range : force). */
    public void giveBack(Taken t, int count, int cases) {
        if (count > 0 && !t.id().isEmpty()) {
            insert(t.id(), t.tag(), count, cases, true);
        }
    }

    /** Index de la sorte choisie (sinon la premiere qui verifie {@code ok}), ou -1. */
    public int selectedIndex(java.util.function.Predicate<Entry> ok) {
        if (!selected.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id.equals(selected) && ok.test(entries.get(i))) {
                    return i;
                }
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            if (ok.test(entries.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Ordre fixe : chemin de l'identifiant, puis espace de noms, puis NBT. */
    public void sort() {
        entries.sort(Comparator.comparing((Entry e) -> path(e.id)).thenComparing(e -> e.id).thenComparing(e -> e.base.toString()));
    }

    static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    /** Piles d'au plus {@code stack} objets (lecture a l'ancienne : infobulles, GameTests d'avant), variantes les plus fortes d'abord. */
    public List<Taken> flatten(int stack) {
        List<Taken> out = new ArrayList<>();
        for (Entry e : entries) {
            if (mode == Mode.GENES) {
                for (int v : e.ranked()) {
                    split(out, e, v, e.variants.get(v), stack);
                }
            } else {
                split(out, e, 0, e.total, stack);
            }
        }
        return out;
    }

    private void split(List<Taken> out, Entry e, int v, int n, int stack) {
        CompoundTag t = e.tagOf(mode, v);
        while (n > 0) {
            int k = Math.min(n, Math.max(1, stack));
            out.add(new Taken(e.id, t.isEmpty() ? null : t.copy(), k, v));
            n -= k;
        }
    }

    // ------------------------------------------------------------------ NBT

    /** Lit le registre dans le NBT d'un objet (nouveau format, sinon l'ancien, sinon vide). */
    public static BagLedger read(@Nullable CompoundTag itemTag, Mode mode) {
        BagLedger l = new BagLedger(mode);
        if (itemTag == null) {
            return l;
        }
        if (itemTag.contains(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag s = itemTag.getCompound(TAG);
            l.tier = Math.max(0, Math.min(TIERS - 1, s.getInt(TIER)));
            l.pickup = !s.getBoolean(NO_PICKUP);
            l.selected = s.getString(SEL);
            ListTag f = s.getList(FILTER, Tag.TAG_STRING);
            for (int i = 0; i < f.size() && l.filter.size() < FILTER_MAX; i++) {
                l.filter.add(f.getString(i));
            }
            ListTag cases = s.getList(CASES, Tag.TAG_COMPOUND);
            for (int i = 0; i < cases.size(); i++) {
                CompoundTag c = cases.getCompound(i);
                String id = c.getString(ID);
                if (id.isEmpty()) {
                    continue;
                }
                Entry e = new Entry(id, c.contains(NBT, Tag.TAG_COMPOUND) ? c.getCompound(NBT).copy() : new CompoundTag());
                if (mode == Mode.GENES) {
                    int[] v = c.getIntArray(V);
                    for (int k = 0; k + 1 < v.length; k += 2) {
                        if (v[k + 1] > 0) {
                            e.variants.merge(Math.max(0, v[k]), v[k + 1], Integer::sum);
                        }
                    }
                    if (e.variants.isEmpty() && c.getInt(N) > 0) {
                        e.variants.put(0, c.getInt(N));
                    }
                    for (int n : e.variants.values()) {
                        e.total += n;
                    }
                } else {
                    e.total = Math.max(0, c.getInt(N));
                    e.size = c.contains(S) ? c.getInt(S) : -1;
                }
                if (e.total > 0) {
                    l.entries.add(e);
                }
            }
            l.sort();
            return l;
        }
        if (itemTag.contains(LEGACY, Tag.TAG_COMPOUND)) {
            //Sac d'avant le lot 31 : cases d'ItemStackHandler (Slot, id, Count, tag) ; tout est range (force : ni plafond ni nombre de cases)
            ListTag items = itemTag.getCompound(LEGACY).getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag it = items.getCompound(i);
                String id = it.getString(ID);
                int count = it.getByte("Count") & 0xFF;
                if (!id.isEmpty() && !"minecraft:air".equals(id) && count > 0) {
                    l.insert(id, it.contains(NBT, Tag.TAG_COMPOUND) ? it.getCompound(NBT) : null, count, Integer.MAX_VALUE, true);
                }
            }
            l.migrated = true;
        }
        return l;
    }

    /** Ecrit le registre dans le NBT d'un objet (retire l'ancienne cle) ; un sac vide aux reglages par defaut n'a plus de NBT de sac. */
    public void write(CompoundTag itemTag) {
        itemTag.remove(LEGACY);
        if (entries.isEmpty() && tier == 0 && pickup && filter.isEmpty() && selected.isEmpty()) {
            itemTag.remove(TAG);
            return;
        }
        CompoundTag s = new CompoundTag();
        if (tier > 0) {
            s.putInt(TIER, tier);
        }
        if (!pickup) {
            s.putBoolean(NO_PICKUP, true);
        }
        if (!selected.isEmpty()) {
            s.putString(SEL, selected);
        }
        if (!filter.isEmpty()) {
            ListTag f = new ListTag();
            for (String id : filter) {
                f.add(StringTag.valueOf(id));
            }
            s.put(FILTER, f);
        }
        ListTag cases = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString(ID, e.id);
            if (!e.base.isEmpty()) {
                c.put(NBT, e.base.copy());
            }
            c.putInt(N, e.total);
            if (mode == Mode.GENES) {
                int[] v = new int[e.variants.size() * 2];
                int k = 0;
                for (Map.Entry<Integer, Integer> x : e.variants.entrySet()) {
                    v[k++] = x.getKey();
                    v[k++] = x.getValue();
                }
                c.putIntArray(V, v);
            } else if (mode == Mode.SIZE && e.size >= 0) {
                c.putInt(S, e.size);
            }
            cases.add(c);
        }
        s.put(CASES, cases);
        s.putInt(TOTAL, total());
        itemTag.put(TAG, s);
    }

    /** Lecture rapide du nombre d'objets et du niveau (barre de l'icone, sans tout relire). */
    public static int[] quick(@Nullable CompoundTag itemTag) {
        if (itemTag == null) {
            return new int[]{0, 0};
        }
        if (itemTag.contains(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag s = itemTag.getCompound(TAG);
            return new int[]{s.getInt(TOTAL), Math.max(0, Math.min(TIERS - 1, s.getInt(TIER)))};
        }
        if (itemTag.contains(LEGACY, Tag.TAG_COMPOUND)) {
            int n = 0;
            ListTag items = itemTag.getCompound(LEGACY).getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                n += items.getCompound(i).getByte("Count") & 0xFF;
            }
            return new int[]{n, 0};
        }
        return new int[]{0, 0};
    }
}
