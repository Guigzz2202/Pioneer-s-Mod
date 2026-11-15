package fr.pioneers.cosmetic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.12.0 (lot E4) : garde-robe d'un joueur (capacite {@link CosmeticCap}) : une piece cosmetique par emplacement (coiffe, haut, bas,
 * chaussures) et, pour chacun, l'interrupteur « montrer le cosmetique » ou « montrer l'armure » (par defaut le cosmetique). Aucune
 * protection : l'armure garde la sienne dans tous les cas. Les cosmetiques ne tombent jamais a la mort (copies a la reapparition).
 */
public final class Wardrobe {

    public static final int SIZE = CosmeticPiece.ALL.length;
    private final ItemStack[] items = new ItemStack[SIZE];
    /** Bit i : la piece i montre l'ARMURE (0 = le cosmetique, s'il y en a un). */
    private int armorShown;

    public Wardrobe() {
        java.util.Arrays.fill(items, ItemStack.EMPTY);
    }

    public ItemStack get(int i) {
        return i >= 0 && i < SIZE ? items[i] : ItemStack.EMPTY;
    }

    public void set(int i, ItemStack stack) {
        if (i >= 0 && i < SIZE) {
            items[i] = stack == null ? ItemStack.EMPTY : stack;
        }
    }

    /** Vrai si la piece i doit etre DESSINEE a la place de l'armure (cosmetique present et interrupteur sur « cosmetique »). */
    public boolean shows(int i) {
        return !get(i).isEmpty() && !armorShown(i);
    }

    public boolean armorShown(int i) {
        return (armorShown >> i & 1) != 0;
    }

    public void toggle(int i) {
        if (i >= 0 && i < SIZE) {
            armorShown ^= 1 << i;
        }
    }

    public int mask() {
        return armorShown;
    }

    public void setMask(int mask) {
        armorShown = mask & ((1 << SIZE) - 1);
    }

    /** La pile a-t-elle sa place dans l'emplacement i (vide, ou la piece cosmetique de cet emplacement) ? */
    public static boolean fits(int i, ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof CosmeticItem c && c.piece.ordinal() == i;
    }

    /** Les quatre pieces d'une meme tenue (progres « Tire a quatre epingles »). */
    public boolean fullOutfit() {
        String outfit = null;
        for (ItemStack s : items) {
            if (!(s.getItem() instanceof CosmeticItem c) || outfit != null && !outfit.equals(c.outfit)) {
                return false;
            }
            outfit = c.outfit;
        }
        return true;
    }

    public void copyFrom(Wardrobe other) {
        for (int i = 0; i < SIZE; i++) {
            items[i] = other.items[i].copy();
        }
        armorShown = other.armorShown;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            if (!items[i].isEmpty()) {
                CompoundTag e = items[i].save(new CompoundTag());
                e.putByte("Slot", (byte) i);
                list.add(e);
            }
        }
        t.put("Items", list);
        t.putByte("Armor", (byte) armorShown);
        return t;
    }

    /** Relecture tolerante : une piece inconnue (objet retire) ou a la mauvaise place est ignoree, jamais de plantage. */
    public void load(CompoundTag t) {
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        ListTag list = t.getList("Items", Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            CompoundTag e = list.getCompound(k);
            int i = e.getByte("Slot");
            ItemStack s = ItemStack.of(e);
            if (i >= 0 && i < SIZE && fits(i, s)) {
                items[i] = s;
            }
        }
        setMask(t.getByte("Armor"));
    }
}
