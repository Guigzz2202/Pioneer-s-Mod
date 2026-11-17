package fr.pioneers.tech.forge;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.BronzeBlockItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.HeatedMetalItem;
import fr.pioneers.tech.forge.SmithingRecipe;
import fr.pioneers.tech.forge.automation.DieItem;
import fr.pioneers.tech.forge.automation.QuenchBasinTile;
import fr.pioneers.tech.forge.casting.AnvilMoldBlock;
import fr.pioneers.tech.forge.casting.AnvilMoldTile;
import fr.pioneers.tech.forge.casting.ClayBucketItem;
import fr.pioneers.tech.forge.casting.ClayCrucibleBlock;
import fr.pioneers.tech.forge.casting.ClayCrucibleTile;
import fr.pioneers.tech.forge.casting.CrucibleTongsItem;
import fr.pioneers.tech.forge.casting.FireStarterItem;
import fr.pioneers.tech.forge.casting.HandBellowsBlock;
import fr.pioneers.tech.forge.casting.HandBellowsTile;
import fr.pioneers.tech.forge.casting.PlacedMoldBlock;
import fr.pioneers.tech.forge.casting.PlacedMoldTile;
import fr.pioneers.tech.forge.smithing.AnvilBlock;
import fr.pioneers.tech.forge.smithing.AnvilTile;
import fr.pioneers.tech.forge.smithing.ForgeHearthBlock;
import fr.pioneers.tech.forge.smithing.ForgeHearthTile;
import fr.pioneers.tech.forge.smithing.QuenchBasinBlock;
import fr.pioneers.tech.forge.smithing.ToolRackBlock;
import fr.pioneers.tech.forge.smithing.ToolRackTile;
import fr.pioneers.core.recipes.PiRecipes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Registre de la forge primaire (0.21, spec {@code docs/specification-forge-primaire.md}) : allume-feu, creuset d'argile, pince, soufflet a
 * main, moule pose, foyer de forge, enclumes, bac de trempe, metal chauffe, matrices en acier ; recette {@code pioneers:smithing}. 1.0.2-beta
 * (lot 2) : moule d'enclume pose, porte-outils ; l'enclume en pierre est retiree.
 * Contrat entre les equipes : {@code scratchpad/forge/CONTRACT.md}. Les ids ci-dessous ne changent plus.
 */
public final class PiForge {

    private PiForge() {
    }

    public static void init() {
    }

    // ------------------------------------------------------------------
    // Proprietes
    // ------------------------------------------------------------------

    private static BlockBehaviour.Properties clay() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F, 1.0F).sound(SoundType.DECORATED_POT).noOcclusion()
              .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 2.0F).sound(SoundType.WOOD).noOcclusion();
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).noOcclusion()
              .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties metal(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(5.0F, 1200.0F).sound(SoundType.ANVIL).noOcclusion()
              .requiresCorrectToolForDrops();
    }

    private static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        return PiCoreRegistries.block(name, block, b -> new BronzeBlockItem(b, new Item.Properties()));
    }

    private static RegistryObject<Item> item(String name, Supplier<? extends Item> item) {
        return PiCoreRegistries.ITEMS.register(name, item);
    }

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          Supplier<Block[]> blocks) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.get()).build(null));
    }

    // ------------------------------------------------------------------
    // Recette d'enclume
    // ------------------------------------------------------------------

    public static final RegistryObject<RecipeType<SmithingRecipe>> SMITHING = PiRecipes.RECIPE_TYPES.register("smithing",
          () -> RecipeType.simple(Pioneers.rl("smithing")));
    public static final RegistryObject<RecipeSerializer<SmithingRecipe>> SMITHING_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register("smithing",
          SmithingRecipe.Serializer::new);

    // ------------------------------------------------------------------
    // Objets
    // ------------------------------------------------------------------

    public static final RegistryObject<Item> FIRE_STARTER = item("fire_starter", () -> new FireStarterItem(new Item.Properties().durability(32)));
    /** 1.2.0 : seau en argile (liquides froids seulement) et sa version crue. */
    public static final RegistryObject<Item> CLAY_BUCKET = item("clay_bucket", () -> new ClayBucketItem(new Item.Properties()));
    public static final RegistryObject<Item> UNFIRED_CLAY_BUCKET = item("unfired_clay_bucket", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CRUCIBLE_TONGS = item("crucible_tongs",
          () -> new CrucibleTongsItem(new Item.Properties().durability(128)));
    public static final RegistryObject<Item> HEATED_METAL = item("heated_metal",
          () -> new HeatedMetalItem(new Item.Properties().stacksTo(1).fireResistant()));

    /**
     * Matrices en acier de la machine de coulee, par forme (ingot, plate, rod, gear = ebauche d'engrenage ; 1.0.6 : nugget, block). Non
     * empilables (un outil, une case Outil).
     */
    public static final List<String> DIE_SHAPES = List.of("ingot", "plate", "rod", "gear", "nugget", "block");
    public static final Map<String, RegistryObject<Item>> DIES = new LinkedHashMap<>();

    static {
        for (String shape : DIE_SHAPES) {
            DIES.put(shape, item(shape + "_die", () -> new DieItem(shape, new Item.Properties().stacksTo(1))));
        }
    }

    // ------------------------------------------------------------------
    // Blocs
    // ------------------------------------------------------------------

    /** Creuset d'argile pose (1.0.2-beta : 256 fusions, avant 24 ; usure portee par l'objet). */
    public static final BlockEntry<ClayCrucibleBlock> CLAY_CRUCIBLE = PiCoreRegistries.block("clay_crucible",
          () -> new ClayCrucibleBlock(clay()), b -> new BronzeBlockItem(b, new Item.Properties().durability(256)));
    public static final BlockEntry<HandBellowsBlock> HAND_BELLOWS = block("hand_bellows", () -> new HandBellowsBlock(wood()));
    public static final BlockEntry<ForgeHearthBlock> FORGE_HEARTH = block("forge_hearth", () -> new ForgeHearthBlock(stone()));
    //1.0.2-beta : enclume en pierre (lot 2) puis enclume en bronze (lot 5) retirees, remappees vers l'enclume en fer : la PREMIERE et seule
    //enclume, coulee au moule d'enclume (10 lingots de fer) ; elle forge tous les metaux forgeables a la main (tier <= ANVIL_IRON)
    public static final BlockEntry<AnvilBlock> IRON_ANVIL = block("iron_anvil", () -> new AnvilBlock(ForgeMetals.ANVIL_IRON, metal(MapColor.METAL)));
    public static final BlockEntry<QuenchBasinBlock> QUENCH_BASIN = block("quench_basin", () -> new QuenchBasinBlock(wood()));
    /** Moule pose : bloc SANS objet (pose par un moule cuit). */
    public static final RegistryObject<PlacedMoldBlock> PLACED_MOLD = PiCoreRegistries.BLOCKS.register("placed_mold",
          () -> new PlacedMoldBlock(clay().strength(0.3F).noLootTable()));

    /** 1.0.2-beta (lot 2) : moule d'enclume pose (bloc complet SANS objet, pose par le moule d'enclume cuit). */
    public static final RegistryObject<AnvilMoldBlock> PLACED_ANVIL_MOLD = PiCoreRegistries.BLOCKS.register("placed_anvil_mold",
          () -> new AnvilMoldBlock(clay().strength(0.8F).noLootTable()));
    /** 1.0.2-beta (lot 2) : porte-outils (3 outils visibles, clic pour poser / prendre). */
    public static final BlockEntry<ToolRackBlock> TOOL_RACK = block("tool_rack", () -> new ToolRackBlock(wood()));
    /**
     * 1.0.3-beta (lot 11, Guillaume : « une variante par bois ») : un porte-outils par bois de {@link fr.pioneers.building.home.HomeWoods#ALL} ; le
     * chene garde l'id d'origine {@code tool_rack} (porte-outils deja poses : meme bloc, memes outils), les autres {@code <bois>_tool_rack}.
     * Une seule tuile pour tous.
     */
    public static final Map<String, BlockEntry<ToolRackBlock>> TOOL_RACKS = new LinkedHashMap<>();

    static {
        for (String w : fr.pioneers.building.home.HomeWoods.ALL) {
            TOOL_RACKS.put(w, w.equals("oak") ? TOOL_RACK : block(w + "_tool_rack", () -> new ToolRackBlock(rackWood(w))));
        }
    }

    /** Proprietes d'un porte-outils : celles du bois (son de bois, de bambou, du cerisier ; bois du Nether ininflammables). */
    private static BlockBehaviour.Properties rackWood(String w) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 2.0F).noOcclusion()
              .sound(w.equals("bamboo") ? SoundType.BAMBOO_WOOD : w.equals("cherry") ? SoundType.CHERRY_WOOD
                    : w.equals("crimson") || w.equals("warped") ? SoundType.NETHER_WOOD : SoundType.WOOD);
        return w.equals("crimson") || w.equals("warped") ? p : p.ignitedByLava();
    }

    public static final List<BlockEntry<AnvilBlock>> ANVILS = List.of(IRON_ANVIL);

    // ------------------------------------------------------------------
    // Tuiles
    // ------------------------------------------------------------------

    public static final RegistryObject<BlockEntityType<ClayCrucibleTile>> CLAY_CRUCIBLE_TILE = tile("clay_crucible", ClayCrucibleTile::new,
          () -> new Block[]{CLAY_CRUCIBLE.getBlock()});
    public static final RegistryObject<BlockEntityType<HandBellowsTile>> HAND_BELLOWS_TILE = tile("hand_bellows", HandBellowsTile::new,
          () -> new Block[]{HAND_BELLOWS.getBlock()});
    public static final RegistryObject<BlockEntityType<PlacedMoldTile>> PLACED_MOLD_TILE = tile("placed_mold", PlacedMoldTile::new,
          () -> new Block[]{PLACED_MOLD.get()});
    public static final RegistryObject<BlockEntityType<ForgeHearthTile>> FORGE_HEARTH_TILE = tile("forge_hearth", ForgeHearthTile::new,
          () -> new Block[]{FORGE_HEARTH.getBlock()});
    public static final RegistryObject<BlockEntityType<AnvilTile>> ANVIL_TILE = tile("forge_anvil", AnvilTile::new,
          () -> ANVILS.stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<AnvilMoldTile>> PLACED_ANVIL_MOLD_TILE = tile("placed_anvil_mold", AnvilMoldTile::new,
          () -> new Block[]{PLACED_ANVIL_MOLD.get()});
    public static final RegistryObject<BlockEntityType<ToolRackTile>> TOOL_RACK_TILE = tile("tool_rack", ToolRackTile::new,
          () -> TOOL_RACKS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<QuenchBasinTile>> QUENCH_BASIN_TILE = tile("quench_basin", QuenchBasinTile::new,
          () -> new Block[]{QUENCH_BASIN.getBlock()});
}
