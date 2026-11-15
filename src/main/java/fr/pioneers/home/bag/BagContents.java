package fr.pioneers.home.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Patch 1.0.3-beta, lot 7 : contenu d'un sac, dans le NBT de l'objet (cle {@code Bag} = {@link ItemStackHandler#serializeNBT} : cases et
 * piles EXACTES, NBT compris : deux graines de genes differents restent deux piles). Lu et ecrit a chaque changement par {@link BagMenu}, par
 * le ramassage ({@link BagEvents}) et par le semis du sac a graines.
 */
public final class BagContents {

    public static final String TAG = "Bag";

    private BagContents() {
    }

    /**
     * Contenu du sac (copie de travail ; {@link #save} le recopie dans l'objet). 1.0.5-beta (lot 31) : sac de RANGEMENT : vue en piles d'au plus
     * 64 objets du registre {@link BagLedger} (variantes les plus fortes d'abord), en LECTURE seule (infobulles, GameTests d'avant).
     */
    public static ItemStackHandler read(ItemStack bag, BagKind kind) {
        if (kind.storage()) {
            BagLedger l = BagStore.read(bag, kind);
            java.util.List<BagLedger.Taken> piles = l.flatten(64);
            ItemStackHandler v = new ItemStackHandler(Math.max(kind.cases(l.tier), piles.size()));
            for (int i = 0; i < piles.size(); i++) {
                v.setStackInSlot(i, BagStore.toStack(piles.get(i)));
            }
            return v;
        }
        ItemStackHandler h = new ItemStackHandler(kind.slots);
        CompoundTag tag = bag.getTag();
        if (tag != null && tag.contains(TAG)) {
            h.deserializeNBT(tag.getCompound(TAG));
            if (h.getSlots() < kind.slots) {
                //Sac agrandi (amelioration cuir simple -> tanne) : memes cases, les nouvelles vides (jamais rapetisse : rien ne se perd)
                ItemStackHandler grown = new ItemStackHandler(kind.slots);
                for (int i = 0; i < h.getSlots(); i++) {
                    grown.setStackInSlot(i, h.getStackInSlot(i));
                }
                return grown;
            }
        }
        return h;
    }

    public static void save(ItemStack bag, ItemStackHandler h) {
        boolean empty = true;
        for (int i = 0; i < h.getSlots() && empty; i++) {
            empty = h.getStackInSlot(i).isEmpty();
        }
        if (empty) {
            if (bag.getTag() != null) {
                bag.getTag().remove(TAG);
                if (bag.getTag().isEmpty()) {
                    bag.setTag(null);
                }
            }
        } else {
            bag.getOrCreateTag().put(TAG, h.serializeNBT());
        }
    }

    /** Nombre d'objets dans le sac. */
    public static int count(ItemStackHandler h) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            n += h.getStackInSlot(i).getCount();
        }
        return n;
    }

    /**
     * Range une pile dans le sac (piles identiques d'abord, NBT compris, puis cases vides) ; rend ce qui ne rentre pas. Sans effet sur le sac si
     * {@code simulate}.
     */
    public static ItemStack insert(ItemStack bag, BagKind kind, ItemStack stack, boolean simulate) {
        if (kind.storage()) {
            //1.0.5-beta (lot 31) : registre a grosses cases (BagLedger)
            return BagStore.insert(bag, kind, stack, simulate, false);
        }
        if (!kind.accepts(stack)) {
            return stack;
        }
        ItemStackHandler h = read(bag, kind);
        ItemStack in = stack.copy();
        if (kind == BagKind.FISH_BAG) {
            //1.0.4-beta (lot 4) : poissons ranges par espece, la pile garde la plus grande taille (FishStacking)
            FishStacking.compact(h);
            FishStacking.align(h, in);
        }
        ItemStack rest = ItemHandlerHelper.insertItemStacked(h, in, false);
        if (!simulate && rest.getCount() != stack.getCount()) {
            save(bag, h);
        }
        return rest;
    }
}
