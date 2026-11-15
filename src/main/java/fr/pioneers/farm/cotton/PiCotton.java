package fr.pioneers.farm.cotton;

import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.item.ItemWithDescription;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.4-beta (lot 28, Guillaume : « le coton doit faire des fils ») : le FIL DE COTON. Le coton se file a l'etabli du tanneur (2 fils) ou a la
 * trefileuse (3 fils) ; le fil donne la ficelle (2 fils, grille), la toile (3 fils) et la corde de fibres (4 fils -> 2) a l'etabli du
 * charpentier, et ligature les outils (tag {@code pioneers:bindings}). Donnees : {@code tools/gc/farm2/cotton.py} (icone, recettes, langue).
 */
public final class PiCotton {

    private PiCotton() {
    }

    public static void init() {
    }

    public static final RegistryObject<Item> COTTON_THREAD = PiCoreRegistries.ITEMS.register("cotton_thread",
          () -> new ItemWithDescription(new Item.Properties()));
}
