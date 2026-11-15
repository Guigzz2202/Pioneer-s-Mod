package fr.pioneers.farm.fish;

import fr.pioneers.farm.PiFarm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Objet pris pour une espece (poisson du mod ou objet vanilla), avec sa taille (NBT {@code Size}, cm) et son espece. */
public final class FishItems {

    private FishItems() {
    }

    public static Item item(FishDef def) {
        return PiFarm.item(def.item());
    }

    public static ItemStack catchOf(PiFish fish) {
        return of(fish.species(), fish.centimetres());
    }

    public static ItemStack of(int species, int cm) {
        FishDef def = GeneratedFish.SPECIES[species];
        ItemStack s = new ItemStack(item(def));
        if (def.item().startsWith("pioneers:")) {
            s.getOrCreateTag().putInt("Size", cm);
        }
        return s;
    }

    public static int index(String id) {
        FishDef[] all = GeneratedFish.SPECIES;
        for (int i = 0; i < all.length; i++) {
            if (all[i].id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
