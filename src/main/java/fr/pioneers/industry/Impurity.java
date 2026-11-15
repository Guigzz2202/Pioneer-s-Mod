package fr.pioneers.industry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Purete par symbole (decision de Guillaume) : un objet impur porte un symbole sur son icone. 2 = symbole plein (tres impur), 1 = symbole
 * incomplet (legerement impur), 0 = pas de symbole (pur). Stocke dans l'etiquette {@code PioneersImpurity} : deux piles du meme objet ne
 * s'empilent que si elles ont la meme purete (trois piles au plus par metal).
 */
public final class Impurity {

    public static final String KEY = "PioneersImpurity";
    public static final int MAX = 2;

    private Impurity() {
    }

    public static int of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(MAX, tag.getInt(KEY)));
    }

    public static boolean isPure(ItemStack stack) {
        return of(stack) == 0;
    }

    /** Donne la purete a la pile (0 retire l'etiquette, et l'etiquette vide). */
    public static ItemStack set(ItemStack stack, int level) {
        level = Math.max(0, Math.min(MAX, level));
        if (level == 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(KEY);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
        } else {
            stack.getOrCreateTag().putInt(KEY, level);
        }
        return stack;
    }

    /**
     * Purete d'un produit : celle de la recette au tier minimal, diminuee d'un cran par tier d'ecart et d'un cran dans un multibloc
     * industriel.
     */
    public static int result(int recipeImpurity, int machineTier, int recipeTier, boolean industrial) {
        return Math.max(0, recipeImpurity - Math.max(0, machineTier - recipeTier) - (industrial ? 1 : 0));
    }
}
