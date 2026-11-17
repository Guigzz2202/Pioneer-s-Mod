package fr.pioneers.tech.industry;

import fr.pioneers.Pioneers;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.tech.industry.IndustryContent;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.IndustryModule;
import fr.pioneers.tech.industry.Upgrades;
import fr.pioneers.tech.industry.atelier.AtelierBlock;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.atelier.DynamoBlock;
import fr.pioneers.tech.industry.atelier.DynamoTile;
import fr.pioneers.tech.industry.bronze.CokeOvenBlock;
import fr.pioneers.tech.industry.bronze.CokeOvenTile;
import fr.pioneers.tech.industry.engraving.EngravingObjectiveItem;
import fr.pioneers.tech.industry.multi.CasingBlock;
import fr.pioneers.tech.industry.multi.ConcreteShellBlock;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustrialGlassBlock;
import fr.pioneers.tech.industry.multi.ModuleBlock;
import fr.pioneers.tech.industry.multi.PartBlock;
import fr.pioneers.tech.industry.multi.PortBlock;
import fr.pioneers.tech.industry.multi.PortTile;
import fr.pioneers.tech.industry.multi.RadiatorBlock;
import fr.pioneers.tech.industry.multi.RadiatorTile;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.multi.StructureFrameBlock;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.space.item.ItemWithDescription;
import fr.pioneers.tech.mechanics.FuelItem;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Age industriel (0.14) : machines d'atelier (4 tiers), multiblocs industriels (controleurs, carcasses, verre, ports, modules), radiateurs,
 * forge au charbon et four a coke, dynamos ; recette {@code pioneers:industrial} ; fluides, gaz, matieres intermediaires et minerais.
 * Toutes les listes viennent de {@code tools/gc/industry/catalog.py} (enums generes {@link IndustryMachine}, {@link IndustryContent}).
 */
public final class PiIndustry {

    private PiIndustry() {
    }

    public static void init() {
        //Lot 5 : plans tires de la forme des fiches de multiblocs (API Multibloc)
        Projections.addProvider(fr.pioneers.api.multiblock.SpecBlueprint::forBlock);
    }

    /** Blocs et objets de l'onglet « Pioneer's Industry », dans l'ordre. */
    public static final List<Supplier<? extends ItemLike>> TAB = new ArrayList<>();
    /** Matieres (onglet Core). */
    public static final List<Supplier<? extends ItemLike>> MATERIALS = new ArrayList<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        BlockEntry<B> entry = PiCoreRegistries.block(name, block);
        TAB.add(() -> entry);
        return entry;
    }

    private static RegistryObject<Item> item(String name, Supplier<? extends Item> item, List<Supplier<? extends ItemLike>> tab) {
        RegistryObject<Item> registered = PiCoreRegistries.ITEMS.register(name, item);
        tab.add(registered);
        return registered;
    }

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    public static final RegistryObject<RecipeType<IndustrialRecipe>> INDUSTRIAL = PiRecipes.RECIPE_TYPES.register("industrial",
          () -> RecipeType.simple(Pioneers.rl("industrial")));
    public static final RegistryObject<RecipeSerializer<IndustrialRecipe>> INDUSTRIAL_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register(
          "industrial", IndustrialRecipe.Serializer::new);

    // ------------------------------------------------------------------
    // Machines d'atelier (4 tiers)
    // ------------------------------------------------------------------

    public static final Map<IndustryMachine, Map<CableTier, BlockEntry<AtelierBlock>>> ATELIERS = new EnumMap<>(IndustryMachine.class);
    public static final Map<CableTier, BlockEntry<DynamoBlock>> DYNAMOS = new EnumMap<>(CableTier.class);

    static {
        for (IndustryMachine m : IndustryMachine.values()) {
            if (m.atelier != IndustryMachine.Atelier.NEW) {
                continue;
            }
            if (m == IndustryMachine.DYNAMO) {
                for (CableTier tier : CableTier.values()) {
                    DYNAMOS.put(tier, block(m.atelierId(tier), () -> new DynamoBlock(tier, metal().noOcclusion())));
                }
                continue;
            }
            Map<CableTier, BlockEntry<AtelierBlock>> tiers = new EnumMap<>(CableTier.class);
            for (CableTier tier : CableTier.values()) {
                tiers.put(tier, block(m.atelierId(tier), () -> new AtelierBlock(m, tier, metal().noOcclusion()
                      .lightLevel(state -> state.getValue(AtelierBlock.ACTIVE) ? 8 : 0))));
            }
            ATELIERS.put(m, tiers);
        }
    }

    // ------------------------------------------------------------------
    // Avant l'electricite : four a coke
    // ------------------------------------------------------------------

    //1.0.2-beta (lot 2) : la forge au charbon est retiree (bloc remappe vers le foyer de forge, qui chauffe les lopins d'acier)
    public static final BlockEntry<CokeOvenBlock> COKE_OVEN = block("coke_oven", () -> new CokeOvenBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
          .lightLevel(state -> state.getValue(CokeOvenBlock.LIT) ? 10 : 0)));

    // ------------------------------------------------------------------
    // Multiblocs : controleurs, carcasses, verre, ports, modules, radiateurs
    // ------------------------------------------------------------------

    public static final Map<IndustryMachine, BlockEntry<ControllerBlock>> CONTROLLERS = new EnumMap<>(IndustryMachine.class);
    public static final Map<CableTier, BlockEntry<CasingBlock>> CASINGS = new EnumMap<>(CableTier.class);
    public static final Map<IndustryModule, BlockEntry<ModuleBlock>> MODULES = new EnumMap<>(IndustryModule.class);
    public static final Map<PortBlock.Type, BlockEntry<PortBlock>> PORTS = new EnumMap<>(PortBlock.Type.class);
    public static final Map<CableTier, BlockEntry<RadiatorBlock>> RADIATORS = new EnumMap<>(CableTier.class);

    static {
        for (IndustryMachine m : IndustryMachine.values()) {
            if (m.industrial) {
                CONTROLLERS.put(m, block(m.multiId(), () -> new ControllerBlock(m, metal().lightLevel(state -> state.getValue(ControllerBlock.ACTIVE) ? 6 : 0))));
            }
        }
        for (CableTier tier : CableTier.values()) {
            CASINGS.put(tier, block("industrial_casing_" + tier.id(), () -> new CasingBlock(tier.ordinal(), metal())));
        }
    }

    /** 1.0 : cadres de structure par tier (acier, aluminium, titane, tungstene). */
    public static final Map<CableTier, BlockEntry<StructureFrameBlock>> FRAMES = new EnumMap<>(CableTier.class);
    public static final String[] FRAME_IDS = {"steel_frame", "aluminium_frame", "titanium_frame", "tungsten_frame"};

    static {
        for (CableTier tier : CableTier.values()) {
            FRAMES.put(tier, block(FRAME_IDS[tier.ordinal()], () -> new StructureFrameBlock(tier.ordinal(), metal().noOcclusion()
                  .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).isValidSpawn((s, l, p, e) -> false))));
        }
    }

    /** 1.0 : toles de structure par tier (parois des contenants). */
    public static final Map<CableTier, BlockEntry<SheetMetalBlock>> SHEETS = new EnumMap<>(CableTier.class);
    public static final String[] SHEET_IDS = {"steel_sheet_metal", "aluminium_sheet_metal", "titanium_sheet_metal", "tungsten_sheet_metal"};

    static {
        for (CableTier tier : CableTier.values()) {
            SHEETS.put(tier, block(SHEET_IDS[tier.ordinal()], () -> new SheetMetalBlock(tier.ordinal(), metal())));
        }
    }

    /** 0.20.26 : voile de beton (coque des tours de refroidissement). */
    public static final BlockEntry<ConcreteShellBlock> CONCRETE_SHELL = block("concrete_shell_panel", () -> new ConcreteShellBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.STONE).strength(3.0F, 12.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    /**
     * Verre industriel. 0.20.28 : un par tier (MKI = {@code industrial_glass}, puis {@code industrial_glass_advanced|elite|ultimate}), fait
     * d'une carcasse du tier (une pour une) ; comme une carcasse, la vitre la plus faible donne son tier au multibloc.
     */
    public static final Map<CableTier, BlockEntry<IndustrialGlassBlock>> GLASSES = new EnumMap<>(CableTier.class);
    public static final String[] GLASS_IDS = {"industrial_glass", "industrial_glass_advanced", "industrial_glass_elite", "industrial_glass_ultimate"};

    static {
        for (CableTier tier : CableTier.values()) {
            GLASSES.put(tier, block(GLASS_IDS[tier.ordinal()], () -> new IndustrialGlassBlock(tier.ordinal(), BlockBehaviour.Properties.of()
                  .mapColor(MapColor.NONE).strength(3.0F + tier.ordinal(), 9.0F + 3 * tier.ordinal()).sound(SoundType.GLASS).noOcclusion()
                  .requiresCorrectToolForDrops().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                  .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false))));
        }
    }

    /** Vitre MKI (plans projetes, bases spatiales). */
    public static final BlockEntry<IndustrialGlassBlock> GLASS = GLASSES.get(CableTier.BASIC);
    /** Pieces signature (0.16.1) ; 1.0.4-beta (lot 15) : seul reste le refrigerateur a dilution. */
    public static final Map<PartBlock.Kind, BlockEntry<PartBlock>> PARTS =
          new EnumMap<>(PartBlock.Kind.class);

    static {
        for (PartBlock.Kind kind : PartBlock.Kind.values()) {
            PARTS.put(kind, block(kind.id(), () -> new PartBlock(kind, metal().noOcclusion()
                        .lightLevel(state -> kind.glows() && state.getValue(PartBlock.ACTIVE) ? 12 : 0))));
        }
        for (PortBlock.Type type : PortBlock.Type.values()) {
            PORTS.put(type, block("industrial_" + type.key() + "_port", () -> new PortBlock(type, metal())));
        }
        for (IndustryModule module : IndustryModule.values()) {
            //1.0.4-beta (lot 23) : plus de modules metiers (sauf la baie de serveurs) : les autres metiers n'ont plus de bloc
            if (!module.block) {
                continue;
            }
            MODULES.put(module, block(module.id(), () -> new ModuleBlock(module, metal().noOcclusion()
                  .lightLevel(state -> module == IndustryModule.HEATING ? 6
                        //0.17.0 : lueur Tcherenkov des canaux de combustible, piscine eclairee
                        : (module.name().equals("FUEL_CHANNEL") || module.name().equals("FUEL_RACK")) && state.getValue(
                              PartBlock.ACTIVE) ? 9 : 0))));
        }
        for (CableTier tier : CableTier.values()) {
            RADIATORS.put(tier, block(tier.id() + "_radiator", () -> new RadiatorBlock(tier, metal().noOcclusion())));
        }
    }

    public static final BlockEntry<CasingBlock> SCORCHED_CASING = block("industrial_casing_scorched", () -> new CasingBlock(-1, metal()));

    // ------------------------------------------------------------------
    // Blocs-entites
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockEntityType<AtelierTile>> ATELIER_TILE = tile("industry_atelier", AtelierTile::create,
          () -> ATELIERS.values().stream().flatMap(m -> m.values().stream()).map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<ControllerTile>> CONTROLLER_TILE = tile("industry_controller", ControllerTile::new,
          () -> CONTROLLERS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<PortTile>> PORT_TILE = tile("industry_port", PortTile::new,
          () -> PORTS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<RadiatorTile>> RADIATOR_TILE = tile("industry_radiator", RadiatorTile::new,
          () -> RADIATORS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<DynamoTile>> DYNAMO_TILE = tile("industry_dynamo", DynamoTile::new,
          () -> DYNAMOS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<CokeOvenTile>> COKE_OVEN_TILE = tile("coke_oven", CokeOvenTile::new,
          () -> new Block[]{COKE_OVEN.getBlock()});

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          Supplier<Block[]> blocks) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.get()).build(null));
    }

    // ------------------------------------------------------------------
    // Fluides, metaux liquides, gaz
    // ------------------------------------------------------------------

    /**
     * 1.4 (demande de Guillaume : « augmenter la viscosite des liquides type petrole et autres fluides coherents ») : viscosite des fluides
     * epais (unites Forge : eau 1 000, lave 6 000) ; les autres gardent max(800, densite). Un fluide visqueux coule lentement et s'etale peu.
     */
    private static final Map<String, Integer> VISCOSITY = Map.ofEntries(
          Map.entry("bitumen", 50_000), Map.entry("vacuum_residue", 30_000), Map.entry("atmospheric_residue", 12_000),
          Map.entry("heavy_crude_oil", 9_000), Map.entry("sour_crude_oil", 4_500), Map.entry("desalted_crude", 4_000), Map.entry("synthetic_crude", 3_000),
          Map.entry("fuel_oil", 5_000), Map.entry("vacuum_gas_oil", 4_000), Map.entry("base_oil", 3_500), Map.entry("lubricant", 4_000),
          Map.entry("quench_oil", 3_000), Map.entry("coal_tar", 8_000), Map.entry("wood_tar", 7_000), Map.entry("bio_oil", 3_500),
          Map.entry("molasses", 9_000), Map.entry("glycerol", 8_000), Map.entry("epoxy_resin", 9_000), Map.entry("latex", 4_000),
          Map.entry("drilling_mud", 5_000), Map.entry("red_mud", 6_000), Map.entry("plant_oil", 3_000), Map.entry("animal_fat", 4_000),
          Map.entry("fish_oil", 3_000), Map.entry("algae_oil", 3_000), Map.entry("molten_polyethylene", 20_000), Map.entry("molten_polypropylene", 20_000),
          Map.entry("molten_pvc", 20_000), Map.entry("molten_polystyrene", 20_000), Map.entry("sulfuric_acid", 2_500), Map.entry("oleum", 3_000),
          Map.entry("phosphoric_acid", 2_500), Map.entry("ethylene_glycol", 1_800), Map.entry("photoresist", 2_000), Map.entry("digestate", 2_000),
          Map.entry("fermentation_mash", 2_000), Map.entry("vinasse", 1_500));

    public static final Map<String, PiFluids.SimpleFluid> FLUIDS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Gas>> GASES = new LinkedHashMap<>();

    static {
        for (IndustryContent.FluidDef f : IndustryContent.FLUIDS) {
            int density = f.gaseous() ? -Math.abs(f.density()) : f.density();
            int viscosity = f.gaseous() ? 200 : VISCOSITY.getOrDefault(f.id(), Math.max(800, f.density()));
            FLUIDS.put(f.id(), PiFluids.SimpleFluid.create(f.id(), density, viscosity, MapColor.COLOR_LIGHT_GRAY, f.color(), f.temperature()));
        }
        for (IndustryContent.MoltenDef m : IndustryContent.MOLTEN) {
            String id = "molten_" + m.metal();
            //MAJ Recettes (lot 4b) : temperature de coulee du metal (fusion + 100 K) ; au-dela de 1 473 K, conduite refractaire seulement
            FLUIDS.put(id, PiFluids.SimpleFluid.create(id, 6_000, 6_000, MapColor.COLOR_ORANGE, m.color(), m.temperature()));
        }
        FLUIDS.values().forEach(f -> MATERIALS.add(f.bucket));
        for (IndustryContent.GasDef g : IndustryContent.GASES) {
            GASES.put(g.id(), PiGasTypes.GASES.register(g.id(), () -> {
                Gas.Properties properties = new Gas.Properties(g.color());
                if (g.fuel() > 0) {
                    properties.fuel(g.fuel());
                }
                return new Gas(properties);
            }));
        }
    }

    /** Gaz de l'age industriel par son id ; 1.0 (0.19.12) : la vapeur d'eau ({@code steam}) est un gaz. */
    public static Gas gas(String id) {
        RegistryObject<Gas> g = GASES.get(id);
        return g == null ? Gas.EMPTY : g.get();
    }

    /** Vapeur d'eau (gaz) : {@code amount} mB. */
    public static GasStack steam(long amount) {
        return GasStack.of(gas("steam"), amount);
    }

    /** Fluide source de l'age industriel par son id ({@code coolant}, {@code molten_iron}...). */
    public static Fluid fluid(String id) {
        PiFluids.SimpleFluid f = FLUIDS.get(id);
        return f == null ? Fluids.EMPTY : f.source.get();
    }

    // ------------------------------------------------------------------
    // Objets : matieres intermediaires, minerais, kits d'amelioration
    // ------------------------------------------------------------------

    /** Duree de combustion du coke (ticks ; charbon 1 600). */
    public static final int COKE_BURN = 3_200;
    /** Combustibles de la biomasse (ticks de combustion ; le charbon de bois en vaut 1 600). */
    private static final Map<String, Integer> BIOMASS_BURN = Map.of("wood_pellets", 1_200, "biochar", 1_600, "wood_chips", 200, "bagasse", 200,
          "press_cake", 300, "miscanthus", 300, "wood_briquette", 1_600, "coal_briquette", 2_000, "bagasse_bale", 900);
    /** MAJ Recettes (lot 8a) : gravures d'un objectif de gravure neuf (spec « Le graveur optique »). */
    private static final Map<String, Integer> OBJECTIVE_USES = Map.of("glass_engraving_objective", 64, "uv_engraving_objective", 256,
          "euv_engraving_objective", 1_024);
    public static final Map<String, RegistryObject<Item>> ITEMS = new LinkedHashMap<>();
    public static final Map<CableTier, RegistryObject<Item>> UPGRADE_KITS = new EnumMap<>(CableTier.class);

    static {
        for (String id : IndustryContent.ORES) {
            ITEMS.put(id, item(id, () -> new Item(new Item.Properties()), MATERIALS));
        }
        for (String id : IndustryContent.ITEMS) {
            if (id.startsWith("upgrade_kit_")) {
                CableTier tier = CableTier.valueOf(id.substring("upgrade_kit_".length()).toUpperCase(Locale.ROOT));
                RegistryObject<Item> kit = item(id, () -> new Upgrades.KitItem(tier, new Item.Properties()), TAB);
                ITEMS.put(id, kit);
                UPGRADE_KITS.put(tier, kit);
            } else if (id.equals("fertilizer")) {
                //Engrais : fait pousser comme la poudre d'os
                ITEMS.put(id, item(id, () -> new BoneMealItem(new Item.Properties()), MATERIALS));
            } else if (id.equals("petroleum_coke") || id.equals("paraffin_wax") || id.equals("asphalt")) {
                ITEMS.put(id, item(id, () -> new FuelItem(id.equals("petroleum_coke") ? COKE_BURN : 800, new Item.Properties()),
                      MATERIALS));
            } else if (BIOMASS_BURN.containsKey(id)) {
                //Biomasse (age du vivant) : granules, biochar, copeaux, bagasse, tourteau, miscanthus brulent comme du bois
                int burn = BIOMASS_BURN.get(id);
                ITEMS.put(id, item(id, () -> new FuelItem(burn, new Item.Properties()), MATERIALS));
            } else if (id.equals("coke")) {
                //Coke : combustible deux fois plus riche que le charbon (four, generateurs, chaudieres)
                ITEMS.put(id, item(id, () -> new FuelItem(COKE_BURN, new Item.Properties()), MATERIALS));
            } else if (id.startsWith("machine_frame_")) {
                ITEMS.put(id, item(id, () -> new Item(new Item.Properties()), TAB));
            } else if (id.startsWith("machine_hull_")) {
                //1.0.4-beta (lot 16) : machines de base MKI a MKIV (chassis + circuit + metal secondaire du tier), a cote des chassis
                ITEMS.put(id, item(id, () -> new ItemWithDescription(new Item.Properties()), TAB));
            } else if (id.endsWith("_engraving_objective") && !id.startsWith("worn_")) {
                //MAJ Recettes (lot 8a) : objectifs de gravure du graveur optique (s'usent d'un point par gravure ; uses -> objectif use)
                int uses = OBJECTIVE_USES.getOrDefault(id, 64);
                String worn = "worn_" + id;
                ITEMS.put(id, item(id, () -> new EngravingObjectiveItem(uses, () -> item(worn), new Item.Properties()), MATERIALS));
            } else if (id.startsWith("extrusion_shape_") || id.endsWith("_die")) {
                //1.0.6 (demandes de Guillaume du 17/11/2026) : formes d'extrusion et matrices (presse, tube) non empilables (un outil, une
                //case Outil)
                ITEMS.put(id, item(id, () -> new ItemWithDescription(new Item.Properties().stacksTo(1)), MATERIALS));
            } else if (IndustryContent.DESCRIBED.contains(id)) {
                //MAJ Recettes (lot 4b) : moules et sables de la fonderie : une ligne d'infobulle (a quoi ils servent)
                ITEMS.put(id, item(id, () -> new ItemWithDescription(new Item.Properties()), MATERIALS));
            } else {
                ITEMS.put(id, item(id, () -> new Item(new Item.Properties()), MATERIALS));
            }
        }
    }

    public static Item item(String id) {
        RegistryObject<Item> item = ITEMS.get(id);
        return item == null ? Items.AIR : item.get();
    }

    public static BlockEntry<ControllerBlock> controller(IndustryMachine machine) {
        return CONTROLLERS.get(machine);
    }
}
