package fr.pioneers.society.clan.seal;

import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 4) : le Sceau de fonction (cire, cuir, plume), seul objet des Sceaux. */
public final class SealItems {

    public static final RegistryObject<Item> FUNCTION_SEAL = PiCoreRegistries.ITEMS.register("function_seal",
          () -> new SealItem(new Item.Properties().stacksTo(16)));

    private SealItems() {
    }

    /** Charge le registre (appele par le demarrage du territoire). */
    public static void init() {
    }
}
