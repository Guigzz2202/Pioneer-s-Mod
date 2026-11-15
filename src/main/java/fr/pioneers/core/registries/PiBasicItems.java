package fr.pioneers.core.registries;

import fr.pioneers.core.item.GasCapsuleItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * Items « simples » du standard Pioneer's (sprites 2D premium generes par tools/gc/core/item_bake.py, 5 niveaux de pile).
 * Ajouter un item = UNE ligne {@code add("id")} ici (les tags forge sont des fichiers data/forge/tags/items, l'apparence et la langue sont generees).
 * Chaque item ajoute ici recoit automatiquement le visuel de pile ({@link PiStackVisuals}) et sa place dans l'onglet Ressources.
 */
public final class PiBasicItems {

    private PiBasicItems() {
    }

    /** Matieres et formes du premier lot (ordre = ordre de l'onglet creatif). */
    public static final List<String> METALS = List.of("iron", "copper", "gold");
    public static final List<String> FORMS = List.of("plate", "gear", "rod", "wire", "spring");

    private static final Map<String, RegistryObject<Item>> ALL = new LinkedHashMap<>();

    /** Enregistre un item simple (pile de 64) et l'inscrit au visuel de pile. */
    public static RegistryObject<Item> add(String id) {
        RegistryObject<Item> item = PiItems.ITEMS.register(id, () -> new Item(new Item.Properties()));
        ALL.put(id, item);
        PiStackVisuals.add(item);
        return item;
    }

    /** Capsule de gaz (1 B, piles de 16) : objet a etat, hors visuel de pile. */
    public static final RegistryObject<GasCapsuleItem> GAS_CAPSULE = PiItems.ITEMS.register("gas_capsule",
          GasCapsuleItem::new);

    public static final List<RegistryObject<Item>> CIRCUITS = new ArrayList<>();

    static {
        for (int tier = 1; tier <= 4; tier++) {
            CIRCUITS.add(add("circuit_mk" + tier));
        }
        for (String metal : METALS) {
            for (String form : FORMS) {
                add(metal + "_" + form);
            }
        }
        // Futurs items : add("iron_ingot"); ...
    }

    public static Item get(String id) {
        return ALL.get(id).get();
    }

    /** Tous les items simples, dans l'ordre d'enregistrement. */
    public static Map<String, RegistryObject<Item>> all() {
        return Collections.unmodifiableMap(ALL);
    }

    /** Force le chargement de la classe (enregistrement) avant PiItems.ITEMS.register(bus). */
    public static void init() {
    }
}
