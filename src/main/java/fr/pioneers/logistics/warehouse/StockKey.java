package fr.pioneers.logistics.warehouse;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 17) : CLE d'un type d'objet de l'entrepot : objet + NBT (meme regle que {@link ItemStack#isSameItemSameTags} et que la
 * cle interne de {@link CrateStorage}). Sert d'index au serveur (liste par differences, demande d'une recette) et au client (stock du
 * comptoir en table : une recherche par case et par image au lieu d'un balayage de toute la liste).
 */
public record StockKey(Item item, @Nullable CompoundTag tag) {

    public static StockKey of(ItemStack s) {
        return new StockKey(s.getItem(), s.getTag());
    }

    /** Pile de 1 de ce type (copie du NBT). */
    public ItemStack stack() {
        ItemStack s = new ItemStack(item);
        if (tag != null) {
            s.setTag(tag.copy());
        }
        return s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StockKey k && k.item == item && Objects.equals(k.tag, tag);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(item) * 31 + (tag == null ? 0 : tag.hashCode());
    }
}
