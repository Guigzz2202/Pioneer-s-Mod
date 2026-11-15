package fr.pioneers.core.profile;

import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 2) : objets du pionnier (Carnet du Restaurateur, Carnet vierge). Le Pupitre est dans {@link RestorerDeskBlock}. */
public final class RpItems {

    public static final RegistryObject<Item> NOTEBOOK = PiCoreRegistries.ITEMS.register("restorer_notebook",
          () -> new NotebookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BLANK_NOTEBOOK = PiCoreRegistries.ITEMS.register("blank_notebook",
          () -> new Item(new Item.Properties().stacksTo(16)));

    private RpItems() {
    }

    /** Charge les registres (appele par {@link RpBootstrap}). */
    public static void init() {
        RestorerDeskBlock.init();
    }
}
