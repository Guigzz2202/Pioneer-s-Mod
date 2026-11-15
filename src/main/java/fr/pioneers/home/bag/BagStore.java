package fr.pioneers.home.bag;

import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 31, retour 42) : le registre {@link BagLedger} d'un sac de RANGEMENT lu et ecrit dans le NBT de l'objet (le meme objet que
 * celui de l'inventaire : jamais de copie de travail, donc rien a recopier a la fermeture d'un ecran, a la mort ou dans un coffre). Le client
 * lit le meme NBT, synchronise par l'inventaire vanilla : l'ecran et l'infobulle affichent toujours ce que le serveur a ecrit.
 */
public final class BagStore {

    private BagStore() {
    }

    public static BagLedger read(ItemStack bag, BagKind kind) {
        return BagLedger.read(bag.getTag(), kind.mode());
    }

    public static void write(ItemStack bag, BagLedger l) {
        CompoundTag t = bag.getOrCreateTag();
        l.write(t);
        if (t.isEmpty()) {
            bag.setTag(null);
        }
    }

    /** Cases (sortes) du sac a son niveau. */
    public static int cases(BagKind kind, BagLedger l) {
        return kind.cases(l.tier);
    }

    public static String idOf(ItemStack stack) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl == null ? "" : rl.toString();
    }

    @Nullable
    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    /** Objet sorti du sac (vide si l'objet n'existe plus dans le jeu : la case reste, rien ne se perd). */
    public static ItemStack toStack(BagLedger.Taken t) {
        Item it = t.empty() ? null : item(t.id());
        if (it == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(it, t.count());
        if (t.tag() != null && !t.tag().isEmpty()) {
            s.setTag(t.tag().copy());
        }
        return s;
    }

    /** Un objet de la case (variante la plus forte), pour l'affichage. */
    public static ItemStack display(BagLedger l, BagLedger.Entry e) {
        Item it = item(e.id);
        if (it == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(it);
        CompoundTag t = e.tagOf(l.mode, l.mode == BagLedger.Mode.GENES ? e.best() : 0);
        if (!t.isEmpty()) {
            s.setTag(t);
        }
        return s;
    }

    /**
     * Range une pile (rangement a la main : ecran, Maj + clic) ; rend le reste. {@code pickup} : ramassage automatique, refuse si le sac a
     * le ramassage arrete ou si l'objet est bloque par son filtre.
     */
    public static ItemStack insert(ItemStack bag, BagKind kind, ItemStack stack, boolean simulate, boolean pickup) {
        if (stack.isEmpty() || !kind.storage() || !kind.accepts(stack)) {
            return stack;
        }
        BagLedger l = read(bag, kind);
        String id = idOf(stack);
        if (pickup && (!l.pickup || l.filter.contains(id))) {
            return stack;
        }
        int put = l.insert(id, stack.getTag(), stack.getCount(), cases(kind, l), false);
        if (put <= 0) {
            if (!simulate && l.migrated) {
                write(bag, l);
            }
            return stack;
        }
        if (!simulate) {
            write(bag, l);
        }
        return put >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - put);
    }

    /** Sort un objet qui verifie {@code ok} (la sorte choisie d'abord, variante la plus forte) ; vide si aucun. */
    public static ItemStack takeOne(ItemStack bag, BagKind kind, Predicate<ItemStack> ok, boolean simulate) {
        return take(bag, kind, ok, 1, simulate);
    }

    /** Sort au plus {@code max} objets d'une sorte qui verifie {@code ok} (la sorte choisie d'abord). */
    public static ItemStack take(ItemStack bag, BagKind kind, Predicate<ItemStack> ok, int max, boolean simulate) {
        BagLedger l = read(bag, kind);
        int i = l.selectedIndex(e -> ok.test(display(l, e)));
        if (i < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = toStack(l.take(i, max));
        if (!simulate && !out.isEmpty()) {
            write(bag, l);
        }
        return out;
    }

    /** Rentre des objets sortis puis pas utilises (toujours ranges). */
    public static void giveBack(ItemStack bag, BagKind kind, ItemStack rest) {
        if (rest.isEmpty()) {
            return;
        }
        BagLedger l = read(bag, kind);
        l.insert(idOf(rest), rest.getTag(), rest.getCount(), Integer.MAX_VALUE, true);
        write(bag, l);
    }
}
