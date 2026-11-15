package fr.pioneers.mechanics;

import com.mojang.serialization.Codec;
import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.bronze.BronzeBlockItem;
import fr.pioneers.kinetics.machines.MeshItem;
import fr.pioneers.mechanics.charcoal.ClayDrainBlock;
import fr.pioneers.registries.PiTileEntityTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GravelBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Age mecanique, hors pieces du reseau ({@code PiKinetics}) : fibres et cordes, toile, mailles et batee, tamis a main, sediments du monde,
 * produits des machines (farine, sciure, poudre de silex), cendre et pique de charbonnier. Chaque contenu s'inscrit dans {@link #TAB}.
 */
public final class PiMechanics {

    private PiMechanics() {
    }

    public static final List<Supplier<? extends ItemLike>> TAB = new ArrayList<>();
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
          DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Pioneers.MODID);
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIERS =
          DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Pioneers.MODID);

    public static void init() {
    }

    public static void register(IEventBus bus) {
        LOOT_MODIFIERS.register(bus);
        BIOME_MODIFIERS.register(bus);
    }

    private static RegistryObject<Item> item(String name, Supplier<Item> item) {
        RegistryObject<Item> registered = PiCoreRegistries.ITEMS.register(name, item);
        TAB.add(registered);
        return registered;
    }

    private static <B extends Block> BlockEntry<B> block(String name, Supplier<B> block) {
        BlockEntry<B> entry = PiCoreRegistries.block(name, block, b -> new BlockItem(b, new Item.Properties()));
        TAB.add(entry::getBlock);
        return entry;
    }

    // Orpaillage
    //1.0.2-beta (lot 2) : la batee est retiree (objet remappe vers le tamis a main, qui garde le tamisage a la main)
    public static final BlockEntry<HandSieveBlock> HAND_SIEVE = block("hand_sieve", () -> new HandSieveBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    /** 1.2.0 (retour alpha) : une seule maille, 8 fois plus durable (la grille de cuivre est retiree, remappee). */
    public static final RegistryObject<Item> FIBER_MESH = item("fiber_mesh", () -> new MeshItem(1, new Item.Properties().durability(512)));
    public static final BlockEntry<GravelBlock> ALLUVIAL_GRAVEL = block("alluvial_gravel", () -> new GravelBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_BROWN).strength(0.6F).sound(SoundType.GRAVEL)));
    public static final BlockEntry<SandBlock> BLACK_SAND = block("black_sand", () -> new SandBlock(0x24221F, BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND)));
    // Fibres et cordages
    public static final RegistryObject<Item> PLANT_FIBER = item("plant_fiber", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CANVAS = item("canvas", () -> new Item(new Item.Properties()));
    // Produits des machines
    public static final RegistryObject<Item> FLOUR = item("flour", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SAWDUST = item("sawdust", () -> new FuelItem(100, new Item.Properties()));
    public static final RegistryObject<Item> FLINT_DUST = item("flint_dust", () -> new Item(new Item.Properties()));
    // Charbon de bois
    public static final RegistryObject<Item> WOOD_ASH = item("wood_ash", () -> new WoodAshItem(new Item.Properties()));
    public static final RegistryObject<Item> COLLIER_POLE = item("collier_pole", () -> new CollierPoleItem(new Item.Properties().stacksTo(1)));

    public static final BlockEntry<Block> CHARRED_LOG_PILE = block("charred_log_pile", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.BASALT)));
    public static final BlockEntry<AshLayerBlock> ASH_LAYER = block("ash_layer", () -> new AshLayerBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.SAND).noOcclusion().replaceable()));
    public static final BlockEntry<ClayDrainBlock> CLAY_DRAIN = block("clay_drain",
          () -> new ClayDrainBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 4F)
                .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<ClayDrainBlock.Tile>> CLAY_DRAIN_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "clay_drain", () -> BlockEntityType.Builder.of(ClayDrainBlock.Tile::new, CLAY_DRAIN.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<HandSieveBlock.Tile>> HAND_SIEVE_TILE = PiTileEntityTypes.VANILLA_TILES.register("hand_sieve",
          () -> BlockEntityType.Builder.of(HandSieveBlock.Tile::new, HAND_SIEVE.getBlock()).build(null));

    /** 0.20.42 : entonnoir en bois, sans inventaire (passe-plat). */
    public static final BlockEntry<WoodenHopperBlock> WOODEN_HOPPER = block("wooden_hopper", () -> new WoodenHopperBlock(BlockBehaviour.Properties.of()
          .mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WoodenHopperBlock.Tile>> WOODEN_HOPPER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "wooden_hopper", () -> BlockEntityType.Builder.of(WoodenHopperBlock.Tile::new, WOODEN_HOPPER.getBlock()).build(null));

    /** 1.0.2-beta (lot 2) : buffer de fluide en bois (8 000 mB), raccorde a la noria quand il est pose dessus. */
    public static final BlockEntry<WoodenFluidBufferBlock> WOODEN_FLUID_BUFFER = registerTabbed(PiCoreRegistries.block("wooden_fluid_buffer",
          () -> new WoodenFluidBufferBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()),
          b -> new BronzeBlockItem(b, new Item.Properties())));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WoodenFluidBufferTile>> WOODEN_FLUID_BUFFER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "wooden_fluid_buffer", () -> BlockEntityType.Builder.of(WoodenFluidBufferTile::new, WOODEN_FLUID_BUFFER.getBlock()).build(null));

    private static <B extends Block> BlockEntry<B> registerTabbed(BlockEntry<B> entry) {
        TAB.add(entry::getBlock);
        return entry;
    }

    public static final RegistryObject<Codec<AddItemModifier>> ADD_ITEM = LOOT_MODIFIERS.register("add_item", () -> AddItemModifier.CODEC);
    public static final RegistryObject<Codec<RemoveOresModifier>> REMOVE_ORES = BIOME_MODIFIERS.register("remove_vanilla_ores", () -> RemoveOresModifier.CODEC);

    /** Onglet de l'age mecanique : tout sauf les matieres (onglet Core) et les outils (onglet Equipement). */
    public static void creative(CreativeModeTab.Output output) {
        Set<Item> elsewhere = new HashSet<>();
        materials().forEach(m -> elsewhere.add(m.get()));
        tools().forEach(t -> elsewhere.add(t.get()));
        for (Supplier<? extends ItemLike> item : TAB) {
            if (!elsewhere.contains(item.get().asItem())) {
                output.accept(item.get());
            }
        }
    }

    /** Matieres de l'age mecanique (fibres, toile, farine, sciure, poudre de silex, cendre). */
    public static List<RegistryObject<Item>> materials() {
        return List.of(PLANT_FIBER, CANVAS, FLOUR, SAWDUST, FLINT_DUST, WOOD_ASH);
    }

    /** Outils de l'age mecanique (perche de charbonnier ; 1.0.2-beta : la batee est retiree). */
    public static List<RegistryObject<Item>> tools() {
        return List.of(COLLIER_POLE);
    }
}
