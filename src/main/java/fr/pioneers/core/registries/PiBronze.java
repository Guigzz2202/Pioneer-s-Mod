package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.BronzeBlockItem;
import fr.pioneers.tech.bronze.HouseControllerBlock;
import fr.pioneers.tech.bronze.LitBronzeBlock;
import fr.pioneers.tech.bronze.SteamBronzeBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoFoundryTile;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.tech.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelBlock;
import fr.pioneers.tech.bronze.autofoundry.CastingChannelTile;
import fr.pioneers.tech.bronze.autofoundry.FoundryPartBlock;
import fr.pioneers.tech.bronze.autofoundry.FoundryPartTile;
import fr.pioneers.tech.bronze.autofoundry.FoundryVatBlock;
import fr.pioneers.tech.bronze.bench.BenchBlock;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.bronze.blast.BlastFurnaceTile;
import fr.pioneers.tech.bronze.blast.FireBricksBlock;
import fr.pioneers.tech.bronze.blast.TuyereBlock;
import fr.pioneers.tech.bronze.blast.TuyereTile;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.CastingTableBlock;
import fr.pioneers.tech.bronze.foundry.CastingTableTile;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.KilnRecipe;
import fr.pioneers.tech.bronze.foundry.KilnTile;
import fr.pioneers.tech.bronze.foundry.MoldItem;
import fr.pioneers.tech.bronze.machines.SteamBlowerTile;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessBlock;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.tech.bronze.steam.AccessoryBlock;
import fr.pioneers.tech.bronze.steam.AccessoryTile;
import fr.pioneers.tech.bronze.steam.BoilerPart;
import fr.pioneers.tech.bronze.steam.BoilerPartBlock;
import fr.pioneers.tech.bronze.steam.BoilerPartTile;
import fr.pioneers.tech.bronze.steam.FireboxTile;
import fr.pioneers.tech.bronze.steam.SteamAccessory;
import fr.pioneers.tech.bronze.steam.SteamEngineBlock;
import fr.pioneers.tech.bronze.steam.SteamEngineTile;
import fr.pioneers.core.item.WrenchItem;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.world.geology.OutcropBlock;
import fr.pioneers.world.geology.drill.DrillPortBlock;
import fr.pioneers.world.geology.drill.DrillPortTile;
import fr.pioneers.world.geology.drill.SteamDrillTile;
import fr.pioneers.tech.kinetics.KineticBlockItem;
import fr.pioneers.core.recipes.PiRecipes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registre de l'age du bronze : ateliers specialises, fonderie, vapeur, geologie, haut fourneau. Ordre de l'onglet creatif : {@link #ALL}.
 */
public final class PiBronze {

    private PiBronze() {
    }

    public static void init() {
    }

    /** Blocs et objets, dans l'ordre de l'onglet creatif. */
    public static final List<Supplier<? extends ItemLike>> ALL = new ArrayList<>();

    static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion();
    }

    static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops();
    }

    static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops();
    }

    public static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        BlockEntry<B> entry = PiCoreRegistries.block(name, block, b -> new BronzeBlockItem(b, new Item.Properties()));
        ALL.add(() -> entry);
        return entry;
    }

    public static RegistryObject<Item> item(String name, Supplier<? extends Item> item) {
        RegistryObject<Item> registered = PiCoreRegistries.ITEMS.register(name, item);
        ALL.add(registered);
        return registered;
    }

    @SuppressWarnings("ConstantConditions")
    static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name,
          BlockEntityType.BlockEntitySupplier<T> factory, Supplier<Block[]> blocks) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.get()).build(null));
    }

    // ------------------------------------------------------------------
    // Sons (synthetises par tools/gc/bronze/sounds.py)
    // ------------------------------------------------------------------

    private static RegistryObject<SoundEvent> sound(String name) {
        return PiSounds.SOUNDS.register("bronze." + name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl("bronze." + name)));
    }

    public static final RegistryObject<SoundEvent> SOUND_HISS = sound("steam_hiss");
    public static final RegistryObject<SoundEvent> SOUND_WHISTLE = sound("steam_whistle");
    public static final RegistryObject<SoundEvent> SOUND_CHUFF = sound("steam_chuff");
    public static final RegistryObject<SoundEvent> SOUND_HAMMER = sound("steam_hammer");
    public static final RegistryObject<SoundEvent> SOUND_DRILL = sound("drill");
    public static final RegistryObject<SoundEvent> SOUND_ROAR = sound("blast_roar");
    /** 1.0.5-beta (lot 23) : crepitements des fours primitifs (T:core/p105_lot23.py sounds : haut fourneau fulgurant, charbon du four a coke). */
    public static final RegistryObject<SoundEvent> SOUND_BLAST_CRACKLE = sound("blast_crackle");
    public static final RegistryObject<SoundEvent> SOUND_COKE_CRACKLE = sound("coke_crackle");

    // ------------------------------------------------------------------
    // Recettes
    // ------------------------------------------------------------------

    public static final RegistryObject<RecipeType<BenchRecipe>> BENCH = PiRecipes.RECIPE_TYPES.register("bench",
          () -> RecipeType.simple(Pioneers.rl("bench")));
    public static final RegistryObject<RecipeSerializer<BenchRecipe>> BENCH_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register("bench",
          BenchRecipe.Serializer::new);

    // ------------------------------------------------------------------
    // Ateliers
    // ------------------------------------------------------------------

    public static final Map<BenchType, BlockEntry<BenchBlock>> BENCHES = new EnumMap<>(BenchType.class);

    static {
        for (BenchType type : BenchType.values()) {
            BENCHES.put(type, block(type.id(), () -> new BenchBlock(type, type == BenchType.MASON ? stone() : type == BenchType.ASSEMBLY || type == BenchType.COMPONENTS
                  || type == BenchType.ADVANCED_COMPONENTS || type == BenchType.LOGISTICS ? metal() : wood())));
        }
    }

    // ------------------------------------------------------------------
    // Fonderie
    // ------------------------------------------------------------------

    public static final RegistryObject<RecipeType<CastingRecipe>> CASTING = PiRecipes.RECIPE_TYPES.register("casting",
          () -> RecipeType.simple(Pioneers.rl("casting")));
    public static final RegistryObject<RecipeSerializer<CastingRecipe>> CASTING_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register(
          "casting", CastingRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<KilnRecipe>> KILN = PiRecipes.RECIPE_TYPES.register("kiln",
          () -> RecipeType.simple(Pioneers.rl("kiln")));
    public static final RegistryObject<RecipeSerializer<KilnRecipe>> KILN_SERIALIZER = PiRecipes.RECIPE_SERIALIZERS.register(
          "kiln", KilnRecipe.Serializer::new);

    /**
     * Formes des moules (meme liste que tools/gc/materials/gen.py et tools/gc/bronze/assets.py). Forge primaire (0.21) : les 11 moules de
     * tetes d'outils sont retires (les outils se forgent) ; leurs anciens objets deviennent le moule a lingot ({@code PiRemaps}).
     */
    public static final List<String> MOLD_SHAPES = List.of("ingot", "nugget", "plate", "gear", "rod");
    /** Formes retirees en 0.21 (remap vers le moule a lingot, cru ou cuit). */
    public static final List<String> RETIRED_MOLD_SHAPES = List.of("pickaxe", "axe", "shovel", "hoe", "sword", "prospector", "hammer", "file", "saw",
          "knife", "mortar");
    public static final Map<String, RegistryObject<Item>> MOLDS = new LinkedHashMap<>(), UNFIRED_MOLDS = new LinkedHashMap<>();

    //1.0.5-beta (lot 23) : controleur a etats formed + heat (bouche finie, chauffe homogene : HouseControllerBlock, KilnTile)
    public static final BlockEntry<HouseControllerBlock> POTTERY_KILN = block("pottery_kiln", () -> new HouseControllerBlock(
          stone(), KilnTile::new, null, 0));   //1.0.4-beta (lot 25) : fumee en haut de la cheminee (KilnTile.clientTick)
    /**
     * 1.0.5-beta (lot 23, Guillaume : « donner un modele fini au four de potier et une cheminee ») : maconnerie finie du four de potier forme
     * (briques, escaliers et dalle de briques remplaces a la formation, rendus a la rupture) ; sans objet : on ne la pose pas, elle rend le bloc
     * d'origine.
     */
    public static final net.minecraftforge.registries.RegistryObject<fr.pioneers.tech.bronze.foundry.KilnMasonryBlock> KILN_MASONRY =
          fr.pioneers.core.registry.PiCoreRegistries.BLOCKS.register("kiln_masonry", () -> new fr.pioneers.tech.bronze.foundry.KilnMasonryBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final BlockEntry<LitBronzeBlock> CRUCIBLE_FURNACE = block("crucible_furnace", () -> new LitBronzeBlock(
          stone(), CrucibleFurnaceTile::new, null, 1.1));
    public static final BlockEntry<CastingTableBlock> CASTING_TABLE = block("casting_table",
          () -> new CastingTableBlock(stone()));
    public static final RegistryObject<Item> FIRE_CLAY = item("fire_clay", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UNFIRED_FIRE_BRICK = item("unfired_fire_brick", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> FIRE_BRICK = item("fire_brick", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UNFIRED_CRUCIBLE = item("unfired_crucible", () -> new Item(new Item.Properties().stacksTo(16)));
    /** 1.0.2-beta (Guillaume : « augmenter la duree de vie des moules, 128 utilisations ») : coulees d'un moule (avant 40). */
    public static final int MOLD_USES = 128;
    /** 1.0.2-beta (Guillaume : « creusets 256 utilisations ») : 40 -> 256. */
    public static final RegistryObject<Item> CRUCIBLE = item("crucible", () -> new MoldItem("crucible",
          new Item.Properties().durability(256)));

    static {
        for (String shape : MOLD_SHAPES) {
            UNFIRED_MOLDS.put(shape, item("unfired_" + shape + "_mold", () -> new Item(new Item.Properties().stacksTo(16))));
        }
        for (String shape : MOLD_SHAPES) {
            MOLDS.put(shape, item(shape + "_mold", () -> new MoldItem(shape, new Item.Properties().durability(MOLD_USES))));
        }
    }

    /**
     * 1.0.2-beta (lot 2) : moule d'enclume (cru, puis cuit au four de potier ou au feu de camp) : se pose en bloc complet
     * ({@code AnvilMoldBlock}) et recoit 1 440 mB (10 lingots) de bronze ou de fer, en plusieurs coulees ; s'use comme les autres moules.
     */
    public static final RegistryObject<Item> UNFIRED_ANVIL_MOLD = item("unfired_anvil_mold", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> ANVIL_MOLD = item("anvil_mold", () -> new MoldItem("anvil", new Item.Properties().durability(MOLD_USES)));

    public static final RegistryObject<Item> QUICKLIME = item("quicklime", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SLAG = item("slag", () -> new Item(new Item.Properties()));
    /**
     * 1.0.5-beta (lot 39, specification age du bronze §4.3) : boule de charbon, 4 charbons (de terre ou de bois) compactes au compacteur
     * (T1 et plus) : carbone de l'acier rouge au four a induction, resistance au carbone (lot 40) ; combustible = 4 charbons de bois (6 400).
     */
    public static final int COAL_BALL_BURN = 6_400;
    public static final RegistryObject<Item> COAL_BALL = item("coal_ball", () -> new fr.pioneers.tech.mechanics.FuelItem(COAL_BALL_BURN, new Item.Properties()));
    //1.0.2-beta (lot 2) : plus de lingot impur (remappe en scorie : la fonderie ne coule plus que des metaux purs et des alliages)

    public static final RegistryObject<BlockEntityType<KilnTile>> KILN_TILE = tile("pottery_kiln",
          KilnTile::new, () -> new Block[]{POTTERY_KILN.getBlock()});
    public static final RegistryObject<BlockEntityType<CrucibleFurnaceTile>> CRUCIBLE_FURNACE_TILE = tile("crucible_furnace",
          CrucibleFurnaceTile::new, () -> new Block[]{CRUCIBLE_FURNACE.getBlock()});
    public static final RegistryObject<BlockEntityType<CastingTableTile>> CASTING_TABLE_TILE = tile("casting_table",
          CastingTableTile::new, () -> new Block[]{CASTING_TABLE.getBlock()});

    public static final RegistryObject<BlockEntityType<BenchTile>> BENCH_TILE = tile("bench", BenchTile::new,
          () -> BENCHES.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));

    // ------------------------------------------------------------------
    // Vapeur (lot B3 ; 0.20.40 : tuyaux et robinetterie retires, la vapeur passe par les cables a gaz)
    // ------------------------------------------------------------------

    public static final Map<BoilerPart, BlockEntry<BoilerPartBlock>> BOILER_PARTS =
          new EnumMap<>(BoilerPart.class);
    public static final Map<SteamAccessory, BlockEntry<AccessoryBlock>> ACCESSORIES =
          new EnumMap<>(SteamAccessory.class);

    public static final BlockEntry<LitBronzeBlock> FIREBOX = block("firebox", () -> new LitBronzeBlock(
          stone(), FireboxTile::new, null, 0));   //1.0.4-beta (lot 25) : vapeur au dome (FireboxTile.clientTick) ; fumee a la cheminee (BoilerPartBlock)

    static {
        for (var part : BoilerPart.values()) {
            BOILER_PARTS.put(part, block(part.id(), () -> new BoilerPartBlock(part,
                  part == BoilerPart.CHIMNEY ? stone() : metal())));
        }
        for (var kind : SteamAccessory.values()) {
            ACCESSORIES.put(kind, block(kind.id(), () -> new AccessoryBlock(kind, metal())));
        }
    }

    public static final BlockEntry<SteamEngineBlock> STEAM_ENGINE = steamEngine();

    private static BlockEntry<SteamEngineBlock> steamEngine() {
        BlockEntry<SteamEngineBlock> entry = PiCoreRegistries.block("steam_engine",
              () -> new SteamEngineBlock(metal()), b -> new KineticBlockItem(b, new Item.Properties()));
        ALL.add(() -> entry);
        return entry;
    }

    private static Block[] blocks(Collection<? extends BlockEntry<? extends Block>> entries) {
        return entries.stream().map(BlockEntry::getBlock).toArray(Block[]::new);
    }

    public static final RegistryObject<BlockEntityType<BoilerPartTile>> BOILER_PART_TILE = tile("boiler_part",
          BoilerPartTile::new, () -> blocks(BOILER_PARTS.values()));
    public static final RegistryObject<BlockEntityType<FireboxTile>> FIREBOX_TILE = tile("firebox",
          FireboxTile::new, () -> new Block[]{FIREBOX.getBlock()});
    public static final RegistryObject<BlockEntityType<AccessoryTile>> ACCESSORY_TILE = tile("steam_accessory",
          AccessoryTile::new, () -> blocks(ACCESSORIES.values()));
    public static final RegistryObject<BlockEntityType<SteamEngineTile>> STEAM_ENGINE_TILE = PiKinetics.tile("steam_engine",
          SteamEngineTile::new, () -> new Block[]{STEAM_ENGINE.getBlock()});

    // ------------------------------------------------------------------
    // Machines a vapeur (lot B4)
    // ------------------------------------------------------------------

    //1.0.5-beta (lot 38, retour 52) : marteau-pilon a vapeur SUPPRIME (bloc, objet -> scie a vapeur : PiRemaps.P105_LOT38 ; type de tile
    //steam_hammer relu par SteamProcessTile) ; la presse a vapeur devient une machine de traitement (STEAM_MACHINES, SteamMachine.PRESS)
    public static final BlockEntry<SteamBronzeBlock> STEAM_BLOWER = block("steam_blower", () -> new SteamBronzeBlock(metal(),
          SteamBlowerTile::new, null, (s, side) -> side != s.getValue(BronzeBlock.FACING)));

    public static final RegistryObject<BlockEntityType<SteamBlowerTile>> STEAM_BLOWER_TILE = tile("steam_blower",
          SteamBlowerTile::new, () -> new Block[]{STEAM_BLOWER.getBlock()});

    // ------------------------------------------------------------------
    // Geologie (lot B5) : affleurements, foreuse a vapeur
    // ------------------------------------------------------------------

    /** Affleurements (meme liste que tools/gc/bronze/geology.py). */
    public static final List<String> OUTCROPS = List.of("magnetite", "hematite", "malachite", "gold", "cassiterite", "galena", "sphalerite", "coal");
    public static final Map<String, BlockEntry<OutcropBlock>> OUTCROP_BLOCKS = new LinkedHashMap<>();

    static {
        for (String name : OUTCROPS) {
            OUTCROP_BLOCKS.put(name, block(name + "_outcrop", () -> new OutcropBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                  .strength(1.2F, 3.0F).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                  .pushReaction(PushReaction.DESTROY))));
        }
    }

    public static final BlockEntry<BronzeBlock> STEAM_DRILL = block("steam_drill", () -> new BronzeBlock(metal(),
          SteamDrillTile::new, null));
    public static final BlockEntry<Block> DRILL_BASE = block("drill_base", () -> new Block(metal()));
    public static final BlockEntry<DrillPortBlock> DRILL_PORT = block("drill_port",
          () -> new DrillPortBlock(metal()));
    public static final BlockEntry<Block> DRILL_FRAME = block("drill_frame", () -> new Block(metal()));
    public static final BlockEntry<Block> DRILL_CROWN = block("drill_crown", () -> new Block(metal()));

    public static final RegistryObject<BlockEntityType<SteamDrillTile>> STEAM_DRILL_TILE = tile("steam_drill",
          SteamDrillTile::new, () -> new Block[]{STEAM_DRILL.getBlock()});
    public static final RegistryObject<BlockEntityType<DrillPortTile>> DRILL_PORT_TILE = tile("drill_port",
          DrillPortTile::new, () -> new Block[]{DRILL_PORT.getBlock()});

    // ------------------------------------------------------------------
    // Haut fourneau primitif (lot B6)
    // ------------------------------------------------------------------

    public static final BlockEntry<FireBricksBlock> FIRE_BRICKS = block("fire_bricks",
          () -> new FireBricksBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(3.0F, 9.0F)
                .sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final BlockEntry<TuyereBlock> TUYERE = block("tuyere", () -> new TuyereBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(3.0F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final BlockEntry<HouseControllerBlock> BLAST_FURNACE = block("blast_furnace", () -> new HouseControllerBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(3.5F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops(),
          BlastFurnaceTile::new, null, 0));

    public static final RegistryObject<BlockEntityType<TuyereTile>> TUYERE_TILE = tile("tuyere",
          TuyereTile::new, () -> new Block[]{TUYERE.getBlock()});
    public static final RegistryObject<BlockEntityType<BlastFurnaceTile>> BLAST_FURNACE_TILE = tile("blast_furnace",
          BlastFurnaceTile::new, () -> new Block[]{BLAST_FURNACE.getBlock()});

    // ------------------------------------------------------------------
    // 1.0.2-beta (lot 4) : fonderie automatique (multibloc foyer + cuve, conduits de coulee, moules automatiques)
    // ------------------------------------------------------------------

    public static final BlockEntry<HouseControllerBlock> AUTO_FOUNDRY = block("auto_foundry", () -> new HouseControllerBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.5F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops(),
          AutoFoundryTile::new, null, 0));   //1.0.4-beta (lot 25) : fumee au-dessus du bain (AutoFoundryTile.clientTick)
    public static final BlockEntry<FoundryPartBlock> FIRE_BRICK_FIREBOX = block("fire_brick_firebox", () -> new FoundryPartBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.0F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops(), false));
    //1.0.4-beta (lot 23) : cuve OUVERTE et connectee (parois vers l'exterieur seulement, bain sous le rebord) : plus un cube plein a l'oeil
    public static final BlockEntry<FoundryVatBlock> FOUNDRY_VAT = block("foundry_vat", () -> new FoundryVatBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(3.0F, 9.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()
                .noOcclusion()));
    public static final BlockEntry<CastingChannelBlock> CASTING_CHANNEL = block("casting_channel", () -> new CastingChannelBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion()));
    public static final BlockEntry<AutoMoldBlock> AUTO_MOLD = block("auto_mold", () -> new AutoMoldBlock(metal()));

    public static final RegistryObject<BlockEntityType<AutoFoundryTile>> AUTO_FOUNDRY_TILE = tile("auto_foundry",
          AutoFoundryTile::new, () -> new Block[]{AUTO_FOUNDRY.getBlock()});
    public static final RegistryObject<BlockEntityType<FoundryPartTile>> FOUNDRY_PART_TILE = tile("foundry_part",
          FoundryPartTile::new, () -> new Block[]{FIRE_BRICK_FIREBOX.getBlock(), FOUNDRY_VAT.getBlock()});
    public static final RegistryObject<BlockEntityType<CastingChannelTile>> CASTING_CHANNEL_TILE = tile("casting_channel",
          CastingChannelTile::new, () -> new Block[]{CASTING_CHANNEL.getBlock()});
    public static final RegistryObject<BlockEntityType<AutoMoldTile>> AUTO_MOLD_TILE = tile("auto_mold",
          AutoMoldTile::new, () -> new Block[]{AUTO_MOLD.getBlock()});

    /** Matieres de l'age du bronze, rangees dans l'onglet Core (et non dans l'onglet de l'age). */
    public static List<RegistryObject<Item>> materials() {
        return List.of(FIRE_CLAY, UNFIRED_FIRE_BRICK, FIRE_BRICK, QUICKLIME, SLAG, COAL_BALL);
    }

    // ------------------------------------------------------------------
    // Cles a molette de l'age du bronze (onglet Equipement)
    // ------------------------------------------------------------------

    /** Outils de l'age du bronze ranges dans l'onglet Equipement. */
    public static final List<RegistryObject<Item>> TOOLS = new ArrayList<>();
    public static final RegistryObject<Item> BRONZE_WRENCH = wrench("bronze_wrench");
    public static final RegistryObject<Item> IRON_WRENCH = wrench("iron_wrench");

    private static RegistryObject<Item> wrench(String name) {
        RegistryObject<Item> item = PiCoreRegistries.ITEMS.register(name, () -> new WrenchItem(new Item.Properties().stacksTo(1), WrenchItem.PIPES));
        TOOLS.add(item);
        return item;
    }

    // ------------------------------------------------------------------
    // Machines de traitement a la vapeur (compacteur, extracteur, broyeur, four, trefileuse, laminoir, scie, melangeur ; 1.0.5-beta lot 38 :
    // crible, tour, presse ; T1 de leurs familles de tiers)
    // ------------------------------------------------------------------

    public static final RegistryObject<RecipeType<SteamRecipe>> STEAM_PROCESSING = PiRecipes.RECIPE_TYPES.register(
          "steam_processing", () -> RecipeType.simple(Pioneers.rl("steam_processing")));
    public static final RegistryObject<RecipeSerializer<SteamRecipe>> STEAM_PROCESSING_SERIALIZER =
          PiRecipes.RECIPE_SERIALIZERS.register("steam_processing", SteamRecipe.Serializer::new);
    public static final Map<SteamMachine, BlockEntry<SteamProcessBlock>> STEAM_MACHINES =
          new EnumMap<>(SteamMachine.class);

    static {
        for (var machine : SteamMachine.values()) {
            STEAM_MACHINES.put(machine, block(machine.id(), () -> new SteamProcessBlock(machine, metal())));
        }
    }

    public static final RegistryObject<BlockEntityType<SteamProcessTile>> STEAM_PROCESS_TILE = tile("steam_process",
          SteamProcessTile::create, () -> STEAM_MACHINES.entrySet().stream().filter(e -> e.getKey() != SteamMachine.PRESS)
                .map(e -> e.getValue().getBlock()).toArray(Block[]::new));
    /**
     * 1.0.5-beta (lot 38) : la presse a vapeur est une machine de traitement (SteamProcessTile) ; son type de tile {@code steam_press} est
     * GARDE (mondes existants : l'ancienne presse d'etabli se relit, ses objets sont poses au sol, SteamProcessTile.load).
     */
    public static final RegistryObject<BlockEntityType<SteamProcessTile>> STEAM_PRESS_TILE = tile("steam_press",
          SteamProcessTile::create, () -> new Block[]{STEAM_MACHINES.get(SteamMachine.PRESS).getBlock()});
}
