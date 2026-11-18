package fr.pioneers.building.home.bag;

import fr.pioneers.farm.fish.FishItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 4, Guillaume : « On doit pouvoir empiler les poissons qui n'ont pas les memes stats dans le panier ») : rangement PAR ESPECE
 * dans le panier de peche (sac {@link BagKind#FISH_BAG}) et le panier en osier ({@code J:building/home/basket/BasketTile}). La seule stat d'un poisson
 * est sa taille (NBT {@code Size}, cm) : elle ne change ni le prix (J:society/economy/Values), ni la cuisine, ni la nourriture ; elle sert au record
 * (resume du sac : « le plus grand ») et a la taille du poisson relache vivant. Choix : une pile garde la PLUS GRANDE taille de ses poissons
 * (le record n'est jamais perdu, rien ne vaut moins) ; deux poissons de la meme espece ne different que par la taille, tout le reste du NBT
 * doit etre identique.
 */
public final class FishStacking {

    public static final String SIZE = "Size";

    private FishStacking() {
    }

    /** Poisson du mod avec une taille. */
    public static boolean sized(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof FishItem && s.hasTag() && s.getTag().contains(SIZE);
    }

    public static int size(ItemStack s) {
        return sized(s) ? s.getTag().getInt(SIZE) : -1;
    }

    /** Meme espece, NBT identique hors taille : ces deux poissons s'empilent au panier. */
    public static boolean sameSpecies(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || a.getItem() != b.getItem() || !(a.getItem() instanceof FishItem)) {
            return false;
        }
        return rest(a).equals(rest(b));
    }

    private static CompoundTag rest(ItemStack s) {
        CompoundTag t = s.hasTag() ? s.getTag().copy() : new CompoundTag();
        t.remove(SIZE);
        return t;
    }

    /** Donne la taille {@code cm} a la pile (sur place). */
    public static void setSize(ItemStack s, int cm) {
        if (cm >= 0 && s.getItem() instanceof FishItem) {
            s.getOrCreateTag().putInt(SIZE, cm);
        }
    }

    /** Taille commune de deux piles qui s'empilent : la plus grande. */
    public static int merged(ItemStack a, ItemStack b) {
        return Math.max(size(a), size(b));
    }

    /**
     * Aligne un poisson qui entre dans le panier ({@code incoming}, modifie SUR PLACE) et les poissons de son espece deja dedans sur la plus
     * grande taille : les piles deviennent identiques et s'empilent par les regles ordinaires (menu, entonnoir, ramassage).
     */
    public static void align(IItemHandlerModifiable h, @Nullable ItemStack incoming) {
        if (incoming == null || !sized(incoming)) {
            return;
        }
        int max = size(incoming);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (sameSpecies(s, incoming)) {
                max = Math.max(max, size(s));
            }
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (sameSpecies(s, incoming) && size(s) != max) {
                ItemStack c = s.copy();
                setSize(c, max);
                h.setStackInSlot(i, c);
            }
        }
        setSize(incoming, max);
    }

    /** Toutes les piles d'une meme espece a la meme taille (la plus grande), sans deplacer les piles. */
    public static void unify(IItemHandlerModifiable h) {
        Map<Item, Integer> max = new HashMap<>();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (sized(s)) {
                max.merge(s.getItem(), size(s), Math::max);
            }
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            Integer m = sized(s) ? max.get(s.getItem()) : null;
            if (m != null && size(s) != m && sameSpeciesAsAny(h, s)) {
                ItemStack c = s.copy();
                setSize(c, m);
                h.setStackInSlot(i, c);
            }
        }
    }

    private static boolean sameSpeciesAsAny(IItemHandlerModifiable h, ItemStack s) {
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack o = h.getStackInSlot(i);
            if (o != s && sameSpecies(o, s)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #unify} puis regroupe les piles identiques vers les premieres cases (ouverture du sac, ramassage). */
    public static void compact(IItemHandlerModifiable h) {
        unify(h);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!sized(s)) {
                continue;
            }
            for (int j = 0; j < i && !s.isEmpty(); j++) {
                ItemStack t = h.getStackInSlot(j);
                if (!t.isEmpty() && ItemHandlerHelper.canItemStacksStack(t, s) && t.getCount() < t.getMaxStackSize()) {
                    int move = Math.min(s.getCount(), t.getMaxStackSize() - t.getCount());
                    h.setStackInSlot(j, t.copyWithCount(t.getCount() + move));
                    s = s.copyWithCount(s.getCount() - move);
                    h.setStackInSlot(i, s);
                }
            }
        }
    }
}
