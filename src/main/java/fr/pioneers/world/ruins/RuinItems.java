package fr.pioneers.world.ruins;

import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/** 1.9 (MAJ RP, bloc 1) : 24 scraps, note de parchemin, page de lore, module de memoire. */
public final class RuinItems {

    /** Scraps par id ({@code scrap_<categorie>_mk<n>}). */
    public static final Map<String, RegistryObject<Item>> SCRAPS = new LinkedHashMap<>();

    static {
        for (int t = 1; t <= RuinBlocks.TIERS; t++) {
            for (ScrapCategory c : ScrapCategory.values()) {
                int tier = t;
                SCRAPS.put(c.scrap(t), PiCoreRegistries.ITEMS.register(c.scrap(t), () -> new ScrapItem(c, tier)));
            }
        }
    }

    public static final RegistryObject<Item> PARCHMENT_NOTE = PiCoreRegistries.ITEMS.register("parchment_note",
          () -> new LoreItem(LoreItem.Kind.NOTE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LORE_PAGE = PiCoreRegistries.ITEMS.register("lore_page",
          () -> new LoreItem(LoreItem.Kind.PAGE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MEMORY_MODULE = PiCoreRegistries.ITEMS.register("memory_module",
          () -> new LoreItem(LoreItem.Kind.MEMORY, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private RuinItems() {
    }

    public static Item scrap(ScrapCategory c, int tier) {
        return SCRAPS.get(c.scrap(Math.max(1, Math.min(RuinBlocks.TIERS, tier)))).get();
    }

    static void init() {
    }
}
