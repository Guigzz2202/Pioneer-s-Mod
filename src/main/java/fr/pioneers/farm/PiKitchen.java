package fr.pioneers.farm;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.food.CannedFoodItem;
import fr.pioneers.farm.food.CheeseWheelBlock;
import fr.pioneers.farm.food.FarmFoodItem;
import fr.pioneers.farm.food.FoodDef;
import fr.pioneers.farm.food.PiEffects;
import fr.pioneers.farm.food.PlaceableFoodBlock;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.farm.kitchen.StationBlock;
import fr.pioneers.farm.kitchen.StationPartTile;
import fr.pioneers.farm.kitchen.StationTile;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.registries.PiFluids;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre de la cuisine de l'Agriculture 2.0 : types de recette par poste ({@link Station}), blocs des postes et leur tuile commune,
 * objets de cuisine ({@link GeneratedFarm#FOODS} : ingredients, plats, boissons, contenants ; plats a poser ; conserve), meules de fromage,
 * fluides de la cuisine (mout, jus, mouts de brasserie, alcools), effets.
 */
public final class PiKitchen {

    private PiKitchen() {
    }

    /** Appele par le constructeur du mod : effets sur le bus du mod, lait fluide de Forge (seaux de lait dans les postes). */
    public static void init(IEventBus modBus) {
        PiEffects.EFFECTS.register(modBus);
        ForgeMod.enableMilkFluid();
    }

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    public static final Map<Station, RegistryObject<RecipeType<KitchenRecipe>>> TYPES = new EnumMap<>(Station.class);
    public static final Map<Station, RegistryObject<RecipeSerializer<KitchenRecipe>>> SERIALIZERS = new EnumMap<>(Station.class);

    static {
        for (Station s : Station.values()) {
            TYPES.put(s, PiRecipes.RECIPE_TYPES.register(s.id(), () -> RecipeType.simple(Pioneers.rl(s.id()))));
            SERIALIZERS.put(s, PiRecipes.RECIPE_SERIALIZERS.register(s.id(), () -> new KitchenRecipe.Serializer(s)));
        }
    }

    // ------------------------------------------------------------------
    // Postes
    // ------------------------------------------------------------------

    public static final Map<StationBlock.Kind, RegistryObject<StationBlock>> STATIONS = new EnumMap<>(StationBlock.Kind.class);

    static {
        for (StationBlock.Kind kind : StationBlock.Kind.values()) {
            STATIONS.put(kind, PiCoreRegistries.block(kind.id(), () -> StationBlock.create(kind, properties(kind))).block());
        }
    }

    private static BlockBehaviour.Properties properties(StationBlock.Kind kind) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.BLOCK);
        return switch (kind) {
            case KITCHEN_STOVE, CAN_SEALER, COOKING_POT, FRYING_PAN, HONEY_EXTRACTOR -> p.mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)
                  .lightLevel(s -> s.getValue(StationBlock.LIT) ? 13 : 0);
            case BREW_KETTLE, COPPER_STILL -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.5F).sound(SoundType.COPPER);
            //1.6 (lot A2) : fumoir (foyer de briques, armoire de bois) ; seule la case principale porte le feu (lit)
            case SMOKEHOUSE -> p.mapColor(MapColor.COLOR_BROWN).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()
                  .lightLevel(s -> s.getValue(StationBlock.LIT) ? 10 : 0);
            case BREAD_OVEN -> p.mapColor(MapColor.COLOR_RED).strength(2.5F).sound(SoundType.STONE)
                  .lightLevel(s -> s.getValue(StationBlock.LIT) ? 13 : 0);
            case PRESERVES_JAR -> p.mapColor(MapColor.NONE).strength(0.6F).sound(SoundType.GLASS);
            case WATER_TROUGH -> p.mapColor(MapColor.STONE).strength(1.5F).sound(SoundType.STONE);
            default -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).ignitedByLava();
        };
    }

    //1.0.5-beta (lot 41, retour 60) : le menu propre de l'extracteur de miel (lot 32) est retire : interface standard de la ferme (MachineMenu)

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<StationTile>> STATION_TILE = PiTileEntityTypes.VANILLA_TILES.register("kitchen_station",
          () -> BlockEntityType.Builder.<StationTile>of((pos, state) -> (StationTile) ((StationBlock) state.getBlock()).newBlockEntity(pos, state),
                STATIONS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    /** 1.6 (lot A2) : cases secondaires des grands postes (renvoient capacites et fiche a la case principale). */
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<StationPartTile>> STATION_PART_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "kitchen_station_part", () -> BlockEntityType.Builder.of(StationPartTile::new, STATIONS.entrySet().stream().filter(e -> e.getKey().big())
                .map(e -> e.getValue().get()).toArray(Block[]::new)).build(null));

    // ------------------------------------------------------------------
    // Objets de cuisine
    // ------------------------------------------------------------------

    /** Objets de cuisine (cle = id). */
    public static final Map<String, RegistryObject<? extends Item>> FOODS = new LinkedHashMap<>();
    /** Plats a poser (cle = id). */
    public static final Map<String, RegistryObject<PlaceableFoodBlock>> PLACED = new LinkedHashMap<>();
    public static final RegistryObject<CheeseWheelBlock> AGED_CHEESE_WHEEL = PiCoreRegistries.BLOCKS.register("aged_cheese_wheel",
          () -> new CheeseWheelBlock(null, cheese()));
    public static final RegistryObject<CheeseWheelBlock> FRESH_CHEESE_WHEEL = PiCoreRegistries.BLOCKS.register("fresh_cheese_wheel",
          () -> new CheeseWheelBlock(AGED_CHEESE_WHEEL, cheese()));

    private static BlockBehaviour.Properties cheese() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.WOOL).noOcclusion()
              .pushReaction(PushReaction.DESTROY);
    }

    static {
        for (FoodDef def : GeneratedFarm.FOODS) {
            String id = def.id();
            if (id.equals("fresh_cheese_wheel")) {
                FOODS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new BlockItem(FRESH_CHEESE_WHEEL.get(), new Item.Properties().stacksTo(16))));
            } else if (id.equals("aged_cheese_wheel")) {
                FOODS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new BlockItem(AGED_CHEESE_WHEEL.get(), new Item.Properties().stacksTo(16))));
            } else if (id.equals("canned_food")) {
                FOODS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new CannedFoodItem(def, new Item.Properties())));
            } else if (def.bites() > 0) {
                RegistryObject<PlaceableFoodBlock> block = PiCoreRegistries.BLOCKS.register(id, () -> new PlaceableFoodBlock(def,
                      BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5F).sound(SoundType.WOOL).noOcclusion()
                            .pushReaction(PushReaction.DESTROY)));
                PLACED.put(id, block);
                FOODS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().stacksTo(1))));
            } else {
                FOODS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new FarmFoodItem(def, new Item.Properties())));
            }
        }
    }

    // ------------------------------------------------------------------
    // Fluides
    // ------------------------------------------------------------------

    public static final Map<String, PiFluids.SimpleFluid> FLUIDS = new LinkedHashMap<>();

    static {
        for (Object[] f : GeneratedFarm.FLUIDS) {
            String id = (String) f[0];
            int colour = (Integer) f[1], density = (Integer) f[2], temperature = (Integer) f[3];
            int viscosity = id.equals("honey") ? 6000 : id.endsWith("mash") || id.contains("wort") || id.equals("honey_water") ? 1600 : 1000;
            FLUIDS.put(id, PiFluids.SimpleFluid.create(id, density, viscosity, MapColor.COLOR_ORANGE, colour, temperature));
        }
    }
}
