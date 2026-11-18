package fr.pioneers.society.village;

import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 3) : manuels des Corps de Metier (un par vocation, vendus par le Bibliothecaire) et carte de ruines. */
public final class VillageItems {

    /** Vocations du bloc 2, dans l'ordre du Carnet. */
    public static final List<String> VOCATIONS = List.of("builder", "miner_smelter", "engineer", "logistician", "farmer", "herder_angler", "scout",
          "salvager", "astronaut", "trader");
    public static final Map<String, RegistryObject<Item>> MANUALS = new LinkedHashMap<>();

    static {
        for (String v : VOCATIONS) {
            MANUALS.put(v, PiCoreRegistries.ITEMS.register("manual_" + v, () -> new ManualItem(v, new Item.Properties().stacksTo(16))));
        }
    }

    public static final RegistryObject<Item> RUIN_MAP = PiCoreRegistries.ITEMS.register("ruin_map", () -> new RuinMapItem(new Item.Properties()
          .stacksTo(1)));

    private VillageItems() {
    }

    static void init() {
    }
}
