package fr.pioneers.equipment.gear;

import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.equipment.gear.electric.FlamethrowerItem;
import fr.pioneers.equipment.gear.electric.HydrogenJetpackItem;
import fr.pioneers.equipment.gear.electric.VoltaicArmorItem;
import fr.pioneers.equipment.gear.electric.VoltaicBatonItem;
import fr.pioneers.equipment.gear.electric.VoltaicToolItem;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.0.3-beta (lot 9) : equipement electrique de l'age MKI / MKII (demandes de Guillaume du 05/11/2026) : le set Voltaique (cellule
 * voltaique, casque, plastron, jambieres, bottes, perceuse, tronconneuse, taille-haie, matraque), le jetpack a hydrogene et le
 * lance-flammes. Donnees (langue, recettes a l'assembleur de composants, tags) : {@code tools/gc/gear/electric.py} ; visuels :
 * {@code tools/gc/core/armor3d.py} (armure, sac dorsal) et {@code tools/gc/gear/electric_items.py} (outils, lance-flammes, cellule).
 */
public final class PiElectricGear {

    private PiElectricGear() {
    }

    public static void init() {
    }

    /** Tous les objets, dans l'ordre de l'onglet. */
    public static final Map<String, RegistryObject<Item>> ITEMS = new LinkedHashMap<>();

    public static final RegistryObject<Item> CELL = add("voltaic_cell", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> HELMET = add("voltaic_helmet", () -> new VoltaicArmorItem(ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> CHESTPLATE = add("voltaic_chestplate", () -> new VoltaicArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> LEGGINGS = add("voltaic_leggings", () -> new VoltaicArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> BOOTS = add("voltaic_boots", () -> new VoltaicArmorItem(ArmorItem.Type.BOOTS, new Item.Properties()));
    public static final RegistryObject<Item> DRILL = add("voltaic_drill", () -> new VoltaicToolItem(VoltaicToolItem.Kind.DRILL, new Item.Properties()));
    public static final RegistryObject<Item> CHAINSAW = add("voltaic_chainsaw", () -> new VoltaicToolItem(VoltaicToolItem.Kind.CHAINSAW, new Item.Properties()));
    public static final RegistryObject<Item> TRIMMER = add("voltaic_trimmer", () -> new VoltaicToolItem(VoltaicToolItem.Kind.TRIMMER, new Item.Properties()));
    public static final RegistryObject<Item> BATON = add("voltaic_baton", () -> new VoltaicBatonItem(new Item.Properties()));
    public static final RegistryObject<Item> JETPACK = add("hydrogen_jetpack", () -> new HydrogenJetpackItem(new Item.Properties()));
    public static final RegistryObject<Item> FLAMETHROWER = add("flamethrower", () -> new FlamethrowerItem(new Item.Properties()));

    private static RegistryObject<Item> add(String id, java.util.function.Supplier<Item> item) {
        RegistryObject<Item> ro = PiCoreRegistries.ITEMS.register(id, item);
        ITEMS.put(id, ro);
        return ro;
    }
}
