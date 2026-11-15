package fr.pioneers.industry.recipe;

import net.minecraft.world.item.ItemStack;

/**
 * MAJ Recettes (lot 8a) : outil d'une recette industrielle qui s'USE au lieu d'etre consomme (objectifs de gravure du graveur optique).
 * L'ingredient de la recette compte en points d'usure : une recette qui demande 1 objectif en use un point par operation ; une pile de
 * {@code n} objectifs offre la somme de leurs points restants. Use jusqu'au bout, l'outil devient sa forme usee ({@link #worn}), que la
 * rectifieuse reconditionne.
 */
public interface WearTool {

    /** Points d'usure restants d'UN outil de la pile. */
    int usesLeft(ItemStack stack);

    /** L'outil (une piece) apres {@code uses} points d'usure : le meme, abime d'autant, ou sa forme usee quand il ne lui reste plus rien. */
    ItemStack wear(ItemStack stack, int uses);

    /** Points offerts par une pile entiere (0 si ce n'est pas un outil d'usure : la quantite de la pile compte alors). */
    static int units(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof WearTool tool) {
            return tool.usesLeft(stack) * stack.getCount();
        }
        return stack.getCount();
    }
}
