package fr.pioneers.robotics;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Familles de ressources que les unites transportent : objets, fluides, gaz, energie (FE, en cellules), donnees et pression (air comprime,
 * en litres d'air, dans une bouteille).
 */
public enum Resource {
    ITEMS(Items.CHEST),
    FLUIDS(Items.WATER_BUCKET),
    GAS(Items.GLASS_BOTTLE),
    ENERGY(Items.REDSTONE),
    DATA(Items.COMPARATOR),
    PRESSURE(Items.PISTON);

    public final Item icon;

    Resource(Item icon) {
        this.icon = icon;
    }

    public String getTranslationKey() {
        return "robotics.pioneers.resource." + name().toLowerCase(Locale.ROOT);
    }

    /** Ressource sans sortes (une simple quantite) : energie, air comprime. */
    public boolean isQuantity() {
        return this == ENERGY || this == PRESSURE;
    }

    public static Resource byIndex(int index) {
        Resource[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
