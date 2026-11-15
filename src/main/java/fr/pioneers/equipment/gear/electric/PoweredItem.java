package fr.pioneers.equipment.gear.electric;

import net.minecraft.world.item.ItemStack;

/**
 * 1.0.3-beta (lot 9) : objet electrique simple (set Voltaique, jetpack a hydrogene, lance-flammes) : une reserve d'energie FE dans le NBT de
 * la pile (cle {@code Energy}, comme l'equipement modulaire : une piece Graviton faite d'une piece Voltaique garde son energie), et pour le
 * jetpack et le lance-flammes un reservoir d'hydrogene (cle {@code Hydrogen}, mB). Pas de module : ils restent a Graviton et au-dela.
 * Chiffres : {@link ElectricCharge}.
 */
public interface PoweredItem {

    /** Reserve d'energie (FE). */
    long energyCapacity();

    /** Debit de recharge (FE/t) par un buffer d'energie, l'etabli de modulation ou un chargeur. */
    default int energyRate() {
        return (int) Math.min(Integer.MAX_VALUE, energyCapacity() / 100);
    }

    /** Reservoir d'hydrogene (mB) ; 0 sans reservoir. */
    default long hydrogenCapacity() {
        return 0;
    }

    static boolean is(ItemStack stack) {
        return stack.getItem() instanceof PoweredItem;
    }
}
