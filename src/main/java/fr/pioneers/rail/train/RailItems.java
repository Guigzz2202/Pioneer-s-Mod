package fr.pioneers.rail.train;

import fr.pioneers.registries.PiRail;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 0.20.0 : objets des vehicules (un par sorte et par tier). */
public final class RailItems {

    private RailItems() {
    }

    /** Reserves d'un vehicule ramasse (fluide, gaz, energie...), rendues a la pose. */
    public static final String STORE_TAG = "RailStore";

    public static ItemStack stack(CarType type, int tier) {
        Item item = PiRail.car(type, tier);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
