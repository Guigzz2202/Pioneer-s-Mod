package fr.pioneers.api.environment;

import net.minecraft.world.item.ItemStack;

/**
 * Piece de combinaison spatiale. Une combinaison complete (4 pieces) protege jusqu'au plus petit tier de ses pieces ;
 * le plastron porte la reserve d'oxygene (gaz {@code mekanism:oxygen}).
 */
public interface ISpaceSuitItem {

    /** 1 = combinaison de base, 2 = thermique, 3 = anti-acide. */
    int getSuitTier(ItemStack stack);
}
