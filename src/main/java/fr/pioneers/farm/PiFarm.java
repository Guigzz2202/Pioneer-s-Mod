package fr.pioneers.farm;

import com.mojang.serialization.Codec;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.farm.crop.CropDef;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.CropTopBlock;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.GeneticSeedItem;
import fr.pioneers.farm.crop.MushroomCropBlock;
import fr.pioneers.farm.crop.MushroomSubstrateBlock;
import fr.pioneers.farm.crop.WitheredCropBlock;
import fr.pioneers.farm.tree.FruitLeavesBlock;
import fr.pioneers.farm.tree.FruitSaplingBlock;
import fr.pioneers.farm.tree.TreeDef;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.registries.PiTileEntityTypes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre de l'Agriculture 2.0 (1.3.0-alpha, spec {@code docs/specification-agriculture-2.md}) : cultures genetiques (bloc, graine, recolte),
 * arbres fruitiers (pousse, feuilles, fruit). Les definitions viennent de {@link GeneratedFarm} (genere depuis {@code tools/gc/farm2}).
 */
public final class PiFarm {

    private PiFarm() {
    }

    public static void init() {
    }

    // ------------------------------------------------------------------
    // Cultures
    // ------------------------------------------------------------------

    /** Bloc de chaque culture (cle = id de la culture). */
    public static final Map<String, RegistryObject<FarmCropBlock>> CROPS = new LinkedHashMap<>();
    /** Graine de chaque culture (cle = id de la culture). */
    public static final Map<String, RegistryObject<Item>> SEEDS = new LinkedHashMap<>();
    /** Recoltes du mod (cle = id de l'objet). */
    public static final Map<String, RegistryObject<Item>> PRODUCE = new LinkedHashMap<>();

    public static final RegistryObject<CropTopBlock> CROP_TOP = PiCoreRegistries.BLOCKS.register("crop_top",
          () -> new CropTopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().noOcclusion().instabreak()
                .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY).noLootTable()));
    public static final RegistryObject<WitheredCropBlock> WITHERED_CROP = PiCoreRegistries.BLOCKS.register("withered_crop",
          () -> new WitheredCropBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).noCollission().instabreak()
                .sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));

    static {
        for (CropDef def : GeneratedFarm.CROPS) {
            //1.0.3 (lot 13) : champignons cultives (archetype « mushroom ») : a l'ombre, sur leur substrat
            RegistryObject<FarmCropBlock> block = PiCoreRegistries.BLOCKS.register(def.id() + "_crop",
                  () -> "mushroom".equals(def.arch()) ? new MushroomCropBlock(def, BlockBehaviour.Properties.copy(Blocks.WHEAT).noLootTable()
                        .mapColor(MapColor.TERRACOTTA_WHITE).sound(SoundType.FUNGUS)) : new FarmCropBlock(def, BlockBehaviour.Properties.copy(Blocks.WHEAT).noLootTable()));
            CROPS.put(def.id(), block);
            SEEDS.put(def.id(), PiCoreRegistries.ITEMS.register(def.seed(), () -> new GeneticSeedItem(block.get(), new Item.Properties())));
            if (def.newProduce()) {
                String id = ResourceLocation.parse(def.produce()).getPath();
                if (!PRODUCE.containsKey(id)) {
                    PRODUCE.put(id, PiCoreRegistries.ITEMS.register(id, () -> new Item(food(new Item.Properties(), def.nutrition(),
                          def.saturation()))));
                }
            }
        }
    }

    /** 1.0.3 (lot 13) : substrat a champignons (sol des champignons cultives, toujours humide). */
    public static final RegistryObject<MushroomSubstrateBlock> MUSHROOM_SUBSTRATE = PiCoreRegistries.BLOCKS.register("mushroom_substrate",
          () -> new MushroomSubstrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6F).sound(SoundType.ROOTED_DIRT)));

    static {
        PiCoreRegistries.ITEMS.register("mushroom_substrate", () -> new BlockItem(MUSHROOM_SUBSTRATE.get(), new Item.Properties()));
    }

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CropTile>> CROP_TILE = PiTileEntityTypes.VANILLA_TILES.register("farm_crop",
          () -> BlockEntityType.Builder.of(CropTile::new, CROPS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    // ------------------------------------------------------------------
    // Arbres fruitiers
    // ------------------------------------------------------------------

    public static final Map<String, RegistryObject<FruitSaplingBlock>> SAPLINGS = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<FruitLeavesBlock>> LEAVES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Item>> FRUITS = new LinkedHashMap<>();

    static {
        for (TreeDef def : GeneratedFarm.TREES) {
            RegistryObject<FruitSaplingBlock> sapling = PiCoreRegistries.BLOCKS.register(def.id() + "_sapling",
                  () -> new FruitSaplingBlock(def, BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)));
            SAPLINGS.put(def.id(), sapling);
            PiCoreRegistries.ITEMS.register(def.id() + "_sapling", () -> new BlockItem(sapling.get(), new Item.Properties()));
            RegistryObject<FruitLeavesBlock> leaves = PiCoreRegistries.BLOCKS.register(def.id() + "_leaves",
                  () -> new FruitLeavesBlock(def, BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)));
            LEAVES.put(def.id(), leaves);
            PiCoreRegistries.ITEMS.register(def.id() + "_leaves", () -> new BlockItem(leaves.get(), new Item.Properties()));
            if (def.newFruit()) {
                String id = ResourceLocation.parse(def.fruit()).getPath();
                FRUITS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new Item(food(new Item.Properties(), def.nutrition(), def.saturation()))));
            }
        }
    }

    // ------------------------------------------------------------------
    // Butins
    // ------------------------------------------------------------------

    public static final RegistryObject<Codec<FarmLoot.WildSeeds>> WILD_SEEDS = PiMechanics.LOOT_MODIFIERS.register("wild_seeds",
          () -> FarmLoot.WildSeeds.CODEC);
    public static final RegistryObject<Codec<FarmLoot.WildSpores>> WILD_SPORES = PiMechanics.LOOT_MODIFIERS.register("wild_spores",
          () -> FarmLoot.WildSpores.CODEC);
    public static final RegistryObject<Codec<FarmLoot.FruitSaplings>> FRUIT_SAPLINGS_LOOT = PiMechanics.LOOT_MODIFIERS.register("fruit_saplings",
          () -> FarmLoot.FruitSaplings.CODEC);

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    static Item.Properties food(Item.Properties properties, int nutrition, float saturation) {
        return nutrition > 0 ? properties.food(new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation).build()) : properties;
    }

    private static final Map<String, Item> ITEM_CACHE = new HashMap<>();

    /** Objet par id complet ({@code ns:path}) ; air si inconnu. */
    public static Item item(String id) {
        Item cached = ITEM_CACHE.get(id);
        if (cached == null) {
            cached = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
            if (cached == null) {
                cached = Items.AIR;
            }
            if (ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(id))) {
                ITEM_CACHE.put(id, cached);
            }
        }
        return cached;
    }

    /** Graine d'une culture. */
    public static Item seedOf(String crop) {
        RegistryObject<Item> seed = SEEDS.get(crop);
        return seed == null ? Items.WHEAT_SEEDS : seed.get();
    }

    /** Au setup commun (file principale) : compostage. */
    public static void setup() {
        SEEDS.values().forEach(s -> ComposterBlock.COMPOSTABLES.put(s.get(), 0.3F));
        PRODUCE.values().forEach(p -> ComposterBlock.COMPOSTABLES.put(p.get(), 0.65F));
        FRUITS.values().forEach(p -> ComposterBlock.COMPOSTABLES.put(p.get(), 0.65F));
        SAPLINGS.values().forEach(s -> ComposterBlock.COMPOSTABLES.put(s.get().asItem(), 0.3F));
        LEAVES.values().forEach(s -> ComposterBlock.COMPOSTABLES.put(s.get().asItem(), 0.3F));
    }
}
