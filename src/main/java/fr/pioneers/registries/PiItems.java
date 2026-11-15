package fr.pioneers.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.data.ItemBiomeDataCell;
import fr.pioneers.item.ItemSpaceSuit;
import fr.pioneers.item.ItemWithDescription;
import fr.pioneers.item.component.CraftPart;
import fr.pioneers.item.component.ItemCraftPart;
import fr.pioneers.item.component.ItemRocketPart;
import fr.pioneers.item.component.RocketPart;
import fr.pioneers.simulation.ItemSimulatedPlanet;
import fr.pioneers.simulation.SimulatedPlanet;
import fr.pioneers.space.ItemRocket;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.farm.ItemSpaceFood;
import fr.pioneers.space.farm.ItemSpaceSeeds;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.giant.ItemAerostatKit;
import fr.pioneers.space.giant.ItemGasScanner;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.space.mining.ItemDrillHead;
import fr.pioneers.space.mining.ItemRoverModule;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.mining.RoverModule;
import fr.pioneers.space.module.ItemRocketModule;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.space.research.ItemRocketBlueprint;
import fr.pioneers.space.research.ItemSurfaceScanner;
import fr.pioneers.space.station.ItemStationKit;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Objets du mod (hors blocs).
 */
public final class PiItems {

    private PiItems() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Pioneers.MODID);

    /** Coeur stellaire : produit par le Gravity Compressor, consomme a l'allumage de la Chambre stellaire. */
    /** Fusees habitees, tiers 1 a 10 (posees comme des entites). */
    public static final Map<RocketTier, RegistryObject<ItemRocket>> ROCKETS = new EnumMap<>(RocketTier.class);
    /** Plans de fusee, tiers 2 a 10 (le tier 1 n'en demande pas). */
    public static final Map<RocketTier, RegistryObject<ItemRocketBlueprint>> BLUEPRINTS =
          new EnumMap<>(RocketTier.class);

    static {
        for (RocketTier tier : RocketTier.values()) {
            ROCKETS.put(tier, ITEMS.register(tier.getName(), () -> new ItemRocket(tier)));
            if (tier != RocketTier.TIER_1) {
                BLUEPRINTS.put(tier, ITEMS.register("rocket_blueprint_tier_" + tier.level, () -> new ItemRocketBlueprint(tier)));
            }
        }
    }

    public static final RegistryObject<ItemRocket> ROCKET_TIER_1 = ROCKETS.get(RocketTier.TIER_1);
    public static final RegistryObject<ItemRocket> ROCKET_TIER_2 = ROCKETS.get(RocketTier.TIER_2);
    public static final RegistryObject<ItemRocket> ROCKET_TIER_3 = ROCKETS.get(RocketTier.TIER_3);

    // --- Modules de fusee ---
    public static final Map<RocketModule, RegistryObject<ItemRocketModule>> ROCKET_MODULES =
          new EnumMap<>(RocketModule.class);

    static {
        for (RocketModule module : RocketModule.values()) {
            ROCKET_MODULES.put(module, ITEMS.register(module.id(), () -> new ItemRocketModule(module)));
        }
    }

    public static Item module(RocketModule module) {
        return ROCKET_MODULES.get(module).get();
    }

    /** Cellule de donnees de biome (vierge ou enregistree par le scanner de surface), a placer dans un analyseur de biomes. */
    public static final RegistryObject<ItemBiomeDataCell> BIOME_DATA_CELL = ITEMS.register("biome_data_cell",
          ItemBiomeDataCell::new);
    /** Scanner de surface : analyse les biomes d'un astre (recherche de surface). */
    public static final RegistryObject<ItemSurfaceScanner> SURFACE_SCANNER = ITEMS.register("surface_scanner",
          ItemSurfaceScanner::new);
    public static final RegistryObject<ItemRocketBlueprint> BLUEPRINT_TIER_2 = BLUEPRINTS.get(RocketTier.TIER_2);
    public static final RegistryObject<ItemRocketBlueprint> BLUEPRINT_TIER_3 = BLUEPRINTS.get(RocketTier.TIER_3);
    /** Lunarium brut (minerai de la Lune). */
    public static final RegistryObject<Item> RAW_LUNARIUM = ITEMS.register("raw_lunarium", () -> new Item(new Item.Properties()));

    // --- Plantes spatiales : graines et recoltes ---
    public static final Map<SpacePlant, RegistryObject<ItemSpaceSeeds>> SEEDS =
          new EnumMap<>(SpacePlant.class);
    public static final Map<SpacePlant, RegistryObject<Item>> PRODUCE =
          new EnumMap<>(SpacePlant.class);

    static {
        for (SpacePlant plant : SpacePlant.values()) {
            SEEDS.put(plant, ITEMS.register(plant.seedName(), () -> new ItemSpaceSeeds(plant)));
            PRODUCE.put(plant, ITEMS.register(plant.produceName, () -> plant.isFood() ? new ItemSpaceFood(plant)
                                                                                      : new Item(new Item.Properties())));
        }
    }

    /** Plan des modules logistiques (soutes), donne par la recherche « Logistique spatiale ». */
    public static final RegistryObject<ItemRocketBlueprint> BLUEPRINT_CARGO = ITEMS.register("rocket_blueprint_cargo",
          () -> new ItemRocketBlueprint(ItemRocketBlueprint.Special.LOGISTICS));

    // --- Minage planetaire ---
    /** Minerais bruts, poudres et lingots des metaux exotiques (le lunarium brut existait deja). */
    public static final Map<MiningMaterial, RegistryObject<Item>> RAW =
          new EnumMap<>(MiningMaterial.class);
    public static final Map<MiningMaterial, RegistryObject<Item>> DUSTS =
          new EnumMap<>(MiningMaterial.class);
    public static final Map<MiningMaterial, RegistryObject<Item>> INGOTS =
          new EnumMap<>(MiningMaterial.class);
    public static final Map<MiningMaterial, RegistryObject<Item>> NUGGETS =
          new EnumMap<>(MiningMaterial.class);
    public static final Map<DrillHead, RegistryObject<ItemDrillHead>> DRILL_HEADS =
          new EnumMap<>(DrillHead.class);
    public static final Map<RoverModule, RegistryObject<ItemRoverModule>> ROVER_MODULES =
          new EnumMap<>(RoverModule.class);

    static {
        for (MiningMaterial material : MiningMaterial.values()) {
            RAW.put(material, material == MiningMaterial.LUNARIUM ? RAW_LUNARIUM
                                                                                                 : ITEMS.register("raw_" + material.id(), () -> new Item(new Item.Properties())));
            DUSTS.put(material, ITEMS.register(material.id() + "_dust", () -> new Item(new Item.Properties())));
            INGOTS.put(material, ITEMS.register(material.id() + "_ingot", () -> new Item(new Item.Properties())));
            NUGGETS.put(material, ITEMS.register(material.id() + "_nugget", () -> new Item(new Item.Properties())));
        }
        for (DrillHead head : DrillHead.values()) {
            DRILL_HEADS.put(head, ITEMS.register(head.id(), () -> new ItemDrillHead(head)));
        }
        for (RoverModule module : RoverModule.values()) {
            ROVER_MODULES.put(module, ITEMS.register(module.id(), () -> new ItemRoverModule(module)));
        }
    }

    // --- Composants de fusee (6 par tier) ---
    public static final Map<RocketPart, Map<RocketTier, RegistryObject<ItemRocketPart>>> ROCKET_PARTS =
          new EnumMap<>(RocketPart.class);

    static {
        for (RocketPart part : RocketPart.values()) {
            Map<RocketTier, RegistryObject<ItemRocketPart>> byTier = new EnumMap<>(RocketTier.class);
            for (RocketTier tier : RocketTier.values()) {
                byTier.put(tier, ITEMS.register(part.id(tier), () -> new ItemRocketPart(part, tier)));
            }
            ROCKET_PARTS.put(part, byTier);
        }
    }

    // --- Composants du robot agricole et du rover minier ---
    public static final Map<CraftPart, RegistryObject<ItemCraftPart>> CRAFT_PARTS =
          new EnumMap<>(CraftPart.class);

    static {
        for (CraftPart part : CraftPart.values()) {
            CRAFT_PARTS.put(part, ITEMS.register(part.id(), () -> new ItemCraftPart(part)));
        }
    }

    /** Stellar Core : desormais un bloc (noyau du Stabilisateur spatial), voir {@link PiStabilizer#STELLAR_CORE}. */
    public static final RegistryObject<? extends BlockItem> STELLAR_CORE = PiStabilizer.STELLAR_CORE.item();

    // ------------------------------------------------------------------
    // Survie spatiale
    // ------------------------------------------------------------------

    /** Combinaisons spatiales : tier (1 a 3) -> piece. */
    public static final Map<ItemSpaceSuit.Tier, Map<ArmorItem.Type,
          RegistryObject<ItemSpaceSuit>>> SUITS = new EnumMap<>(ItemSpaceSuit.Tier.class);

    static {
        for (ItemSpaceSuit.Tier tier : ItemSpaceSuit.Tier.values()) {
            Map<ArmorItem.Type, RegistryObject<ItemSpaceSuit>> pieces =
                  new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                pieces.put(type, ITEMS.register(tier.id + "_" + ItemSpaceSuit.pieceId(type),
                      () -> new ItemSpaceSuit(tier, type)));
            }
            SUITS.put(tier, pieces);
        }
    }

    /** Piece de combinaison d'un tier (1 a 3) pour un emplacement d'armure. */
    public static Item suit(int tier, EquipmentSlot slot) {
        ArmorItem.Type type = switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
            default -> ArmorItem.Type.CHESTPLATE;
        };
        return SUITS.get(ItemSpaceSuit.Tier.byLevel(tier)).get(type).get();
    }

    /** Kit de station orbitale : deploie une station autour de l'astre survole. */
    public static final RegistryObject<ItemStationKit> STATION_KIT = ITEMS.register("orbital_station_kit",
          ItemStationKit::new);

    /** Unite de support de vie : composant des machines de survie et des stations (table d'equipement, recherche Support de vie). */
    public static final RegistryObject<Item> LIFE_SUPPORT_UNIT = ITEMS.register("life_support_unit",
          () -> new ItemWithDescription(new Item.Properties()));

    // ------------------------------------------------------------------
    // Simulation planetaire
    // ------------------------------------------------------------------

    /** 0.20.34 : les quatre etapes de la chaine des planetes simulees (matiere compactee, vide, proto-planete, complete). */
    public static final Map<SimulatedPlanet, RegistryObject<ItemSimulatedPlanet>> COMPACTED_PLANETS = new EnumMap<>(SimulatedPlanet.class);
    public static final Map<SimulatedPlanet, RegistryObject<ItemSimulatedPlanet>> EMPTY_SIMULATED_PLANETS = new EnumMap<>(SimulatedPlanet.class);
    public static final Map<SimulatedPlanet, RegistryObject<ItemSimulatedPlanet>> PROTO_SIMULATED_PLANETS = new EnumMap<>(SimulatedPlanet.class);
    public static final Map<SimulatedPlanet, RegistryObject<ItemSimulatedPlanet>> SIMULATED_PLANETS = new EnumMap<>(SimulatedPlanet.class);

    static {
        for (SimulatedPlanet planet : SimulatedPlanet.values()) {
            COMPACTED_PLANETS.put(planet, simulated(planet, ItemSimulatedPlanet.Stage.COMPACTED));
            EMPTY_SIMULATED_PLANETS.put(planet, simulated(planet, ItemSimulatedPlanet.Stage.EMPTY));
            PROTO_SIMULATED_PLANETS.put(planet, simulated(planet, ItemSimulatedPlanet.Stage.PROTO));
            SIMULATED_PLANETS.put(planet, simulated(planet, ItemSimulatedPlanet.Stage.FILLED));
        }
    }

    private static RegistryObject<ItemSimulatedPlanet> simulated(SimulatedPlanet planet, ItemSimulatedPlanet.Stage stage) {
        return ITEMS.register(stage.prefix + planet.id(), () -> new ItemSimulatedPlanet(planet, stage));
    }

    // ------------------------------------------------------------------
    // 0.10 : geantes gazeuses, propulsion interstellaire, simulation de l'univers
    // ------------------------------------------------------------------

    /** Hydrogene metallique : arrache aux poches profondes de Jupiter et de Saturne par les drones de tempete. */
    public static final RegistryObject<Item> METALLIC_HYDROGEN = ITEMS.register("metallic_hydrogen",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** Cellule de singularite : du gaz de singularite du vide comprime dans de l'hydrogene metallique (Gravity Compressor). */
    public static final RegistryObject<Item> VOID_SINGULARITY_CELL = ITEMS.register("void_singularity_cell",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.EPIC)));
    /** Kit d'aerostat : deploie une plateforme flottante dans les nuages d'une geante gazeuse. */
    public static final RegistryObject<ItemAerostatKit> AEROSTAT_KIT = ITEMS.register("aerostat_kit", ItemAerostatKit::new);
    /** Blindage de drone : un drone blinde affronte les tempetes (couche profonde, foudre). */
    public static final RegistryObject<Item> DRONE_ARMOR_PLATING = ITEMS.register("drone_armor_plating",
          () -> new ItemWithDescription(new Item.Properties().stacksTo(16)));
    /** Scanner de gaz : revele les poches de gaz alentour et les designe aux drones. */
    public static final RegistryObject<ItemGasScanner> GAS_SCANNER = ITEMS.register("gas_scanner", ItemGasScanner::new);
    /** Diamant de pluie : les pluies de diamants de Neptune et d'Uranus, recoltees dans les poches profondes. */
    public static final RegistryObject<Item> RAIN_DIAMOND = ITEMS.register("rain_diamond",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** Poussiere d'etoiles (simulateur d'univers). */
    public static final RegistryObject<Item> STARDUST = ITEMS.register("stardust", () -> new ItemWithDescription(new Item.Properties()));
    /** Matiere noire (simulateur d'univers). */
    public static final RegistryObject<Item> DARK_MATTER = ITEMS.register("dark_matter",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.RARE)));
    /** Lingot de neutronium : coeur d'etoile a neutrons simule. */
    public static final RegistryObject<Item> NEUTRONIUM_INGOT = ITEMS.register("neutronium_ingot",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.RARE)));
    /** Cristal de quasar : la lumiere d'un noyau galactique actif, cristallisee. */
    public static final RegistryObject<Item> QUASAR_CRYSTAL = ITEMS.register("quasar_crystal",
          () -> new ItemWithDescription(new Item.Properties().rarity(Rarity.EPIC)));

    /** Disques de juke-box des musiques du mod. */
    public static final Map<String, RegistryObject<Item>> MUSIC_DISCS = new LinkedHashMap<>();

    static {
        for (int i = 0; i < PiSounds.DISC_TRACKS.length; i++) {
            String track = PiSounds.DISC_TRACKS[i];
            int comparator = i + 1, ticks = PiSounds.DISC_SECONDS[i] * 20;
            MUSIC_DISCS.put(track, ITEMS.register("music_disc_" + track, () -> new RecordItem(comparator,
                  PiSounds.DISCS.get(track), new Item.Properties().stacksTo(1).rarity(Rarity.RARE), ticks)));
        }
    }
}
