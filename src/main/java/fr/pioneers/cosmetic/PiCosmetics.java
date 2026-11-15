package fr.pioneers.cosmetic;

import fr.pioneers.registries.PiItems;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.12.0 (lot E4) : les 80 pieces cosmetiques (20 tenues x 4 pieces, {@link CosmeticCatalog}), dans l'ordre des tenues puis des pieces.
 * Fabrication : etabli du tanneur ({@code BenchType.TANNER}) ; rangement : onglet Equipement, section « cosmetiques » ({@code CreativeSort}).
 */
public final class PiCosmetics {

    private static final Map<String, RegistryObject<CosmeticItem>> ITEMS = new LinkedHashMap<>();

    static {
        for (String outfit : CosmeticCatalog.OUTFITS) {
            for (CosmeticPiece piece : CosmeticPiece.ALL) {
                String id = CosmeticCatalog.itemId(outfit, piece.key);
                ITEMS.put(id, PiItems.ITEMS.register(id, () -> new CosmeticItem(outfit, piece)));
            }
        }
    }

    private PiCosmetics() {
    }

    /** Charge la classe (enregistrement differe des objets). */
    public static void init() {
    }

    public static Map<String, RegistryObject<CosmeticItem>> all() {
        return Collections.unmodifiableMap(ITEMS);
    }
}
