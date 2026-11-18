package fr.pioneers.tech.forge.smithing;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Qualite "Forge main" (spec forge primaire par. 2) : une piece forgee a l'enclume en peu de coups porte la cle NBT {@link #TAG} ; l'outil
 * monte a l'etabli de l'outilleur avec cette piece la garde et gagne {@link #BONUS} de durabilite. Le marteau-pilon donne la qualite
 * standard (sans cle).
 */
public final class HandForged {

    /** Cle NBT (racine) des pieces et des outils forges a la main. */
    public static final String TAG = "pioneers_hand_forged";
    /** Bonus de durabilite (+10 %). */
    public static final float BONUS = 0.10F;

    private HandForged() {
    }

    public static boolean is(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG);
    }

    public static ItemStack mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(TAG, true);
        }
        return stack;
    }

    /** Durabilite maximale d'un outil : la valeur de base, +10 % s'il est forge main. */
    public static int maxDamage(ItemStack stack, int base) {
        return base > 0 && is(stack) ? Math.round(base * (1 + BONUS)) : base;
    }

    /**
     * Etabli : vrai si une piece forgee main a ete consommee par la fabrication ({@code before} / {@code after} = contenu des cases avant et
     * apres la consommation, meme ordre).
     */
    public static boolean consumed(List<ItemStack> before, List<ItemStack> after) {
        for (int i = 0; i < before.size() && i < after.size(); i++) {
            ItemStack b = before.get(i), a = after.get(i);
            if (is(b) && (a.isEmpty() || a.getCount() < b.getCount() || !ItemStack.isSameItemSameTags(a, b))) {
                return true;
            }
        }
        return false;
    }
}
