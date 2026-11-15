package fr.pioneers.core.registries;

import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.tech.kinetics.KineticBlockItem;
import fr.pioneers.tech.kinetics.KineticMaterial;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineBlock;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.tech.kinetics.parts.CogBlock;
import fr.pioneers.tech.kinetics.parts.CogTile;
import fr.pioneers.tech.kinetics.parts.FlywheelBlock;
import fr.pioneers.tech.kinetics.parts.GaugeBlock;
import fr.pioneers.tech.kinetics.parts.GearboxBlock;
import fr.pioneers.tech.kinetics.parts.GovernorBlock;
import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.parts.InlineBlock;
import fr.pioneers.tech.kinetics.parts.ShaftBlock;
import fr.pioneers.tech.kinetics.parts.ShaftTile;
import fr.pioneers.tech.kinetics.rope.LinkType;
import fr.pioneers.tech.kinetics.rope.PulleyBlock;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.tech.kinetics.rope.RopeItem;
import fr.pioneers.tech.kinetics.rope.WinchBlock;
import fr.pioneers.tech.kinetics.rope.WinchTile;
import fr.pioneers.tech.kinetics.sources.BeamEngineBlock;
import fr.pioneers.tech.kinetics.sources.BeamEngineTile;
import fr.pioneers.tech.kinetics.sources.FeedingTroughBlock;
import fr.pioneers.tech.kinetics.sources.HorseMillBlock;
import fr.pioneers.tech.kinetics.sources.HorseMillTile;
import fr.pioneers.tech.kinetics.sources.SailItem;
import fr.pioneers.tech.kinetics.sources.WaterWheelBlock;
import fr.pioneers.tech.kinetics.sources.WaterWheelTile;
import fr.pioneers.tech.kinetics.sources.WeatherVaneBlock;
import fr.pioneers.tech.kinetics.sources.WindmillBlock;
import fr.pioneers.tech.kinetics.sources.WindmillTile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** Age mecanique : pieces du reseau de force rotative (transmission, mesure, stockage, premiere source). */
public final class PiKinetics {

    private PiKinetics() {
    }

    public static void init() {
    }

    /** Toutes les pieces, dans l'ordre de l'onglet creatif. */
    public static final List<BlockEntry<? extends Block>> ALL = new ArrayList<>();
    /** Types de blocs-entites du reseau (rendu des pieces tournantes). */
    public static final List<RegistryObject<? extends BlockEntityType<? extends KineticTile>>> TILES = new ArrayList<>();

    public static <T extends KineticTile> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          Supplier<Block[]> blocks) {
        @SuppressWarnings("ConstantConditions")
        RegistryObject<BlockEntityType<T>> type = PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, blocks.get()).build(null));
        TILES.add(type);
        return type;
    }

    static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion();
    }

    static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
    }

    static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).sound(SoundType.STONE).noOcclusion();
    }

    public static <B extends Block> BlockEntry<B> part(String name, Supplier<B> block) {
        BlockEntry<B> entry = PiCoreRegistries.block(name, block, b -> new KineticBlockItem(b, new Item.Properties()));
        ALL.add(entry);
        return entry;
    }

    public static final BlockEntry<HandCrankBlock> HAND_CRANK = part("hand_crank", () -> new HandCrankBlock(wood()));
    public static final BlockEntry<ShaftBlock> WOODEN_SHAFT = part("wooden_shaft", () -> new ShaftBlock(KineticMaterial.WOOD, wood()));
    public static final BlockEntry<ShaftBlock> IRON_SHAFT = part("iron_shaft", () -> new ShaftBlock(KineticMaterial.IRON, metal()));
    public static final BlockEntry<CogBlock> WOODEN_COG = part("wooden_cog", () -> new CogBlock(KineticMaterial.WOOD, false, wood()));
    public static final BlockEntry<CogBlock> WOODEN_COGWHEEL = part("wooden_cogwheel", () -> new CogBlock(KineticMaterial.WOOD, true, wood()));
    public static final BlockEntry<CogBlock> IRON_COG = part("iron_cog", () -> new CogBlock(KineticMaterial.IRON, false, metal()));
    public static final BlockEntry<CogBlock> IRON_COGWHEEL = part("iron_cogwheel", () -> new CogBlock(KineticMaterial.IRON, true, metal()));
    public static final BlockEntry<GearboxBlock> GEARBOX = part("gearbox", () -> new GearboxBlock(wood()));
    public static final BlockEntry<InlineBlock> GEARSHIFT = part("gearshift", () -> new InlineBlock(InlineBlock.Kind.GEARSHIFT, wood()));
    public static final BlockEntry<InlineBlock> CLUTCH = part("clutch", () -> new InlineBlock(InlineBlock.Kind.CLUTCH, wood()));
    public static final BlockEntry<InlineBlock> REVERSER = part("reverser", () -> new InlineBlock(InlineBlock.Kind.REVERSER, wood()));
    public static final BlockEntry<FlywheelBlock> STONE_FLYWHEEL = part("stone_flywheel", () -> new FlywheelBlock(false, stone()));
    public static final BlockEntry<FlywheelBlock> IRON_FLYWHEEL = part("iron_flywheel", () -> new FlywheelBlock(true, metal()));
    public static final BlockEntry<GaugeBlock> TACHOMETER = part("tachometer", () -> new GaugeBlock(false, wood()));
    public static final BlockEntry<GaugeBlock> DYNAMOMETER = part("dynamometer", () -> new GaugeBlock(true, wood()));
    public static final BlockEntry<GovernorBlock> GOVERNOR = part("governor", () -> new GovernorBlock(metal()));

    // Sources (lot M2)
    public static final BlockEntry<HorseMillBlock> HORSE_MILL = part("horse_mill", () -> new HorseMillBlock(wood()));
    public static final BlockEntry<FeedingTroughBlock> FEEDING_TROUGH = PiCoreRegistries.block("feeding_trough", () -> new FeedingTroughBlock(wood()),
          b -> new BlockItem(b, new Item.Properties()));
    public static final BlockEntry<WaterWheelBlock> WATER_WHEEL = part("water_wheel", () -> new WaterWheelBlock(false, wood()));
    public static final BlockEntry<WaterWheelBlock> LARGE_WATER_WHEEL = part("large_water_wheel", () -> new WaterWheelBlock(true, wood()));
    public static final BlockEntry<WindmillBlock> WINDMILL = part("windmill_hub", () -> new WindmillBlock(wood()));
    public static final BlockEntry<BeamEngineBlock> BEAM_ENGINE = part("beam_engine", () -> new BeamEngineBlock(stone()
          .lightLevel(state -> state.getValue(BeamEngineBlock.LIT) ? 10 : 0)));
    public static final BlockEntry<WeatherVaneBlock> WEATHER_VANE = part("weather_vane", () -> new WeatherVaneBlock(metal()));
    public static final RegistryObject<Item> CANVAS_SAIL = PiCoreRegistries.ITEMS.register("canvas_sail", () -> new SailItem(1, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PLANK_SAIL = PiCoreRegistries.ITEMS.register("plank_sail", () -> new SailItem(2, new Item.Properties().stacksTo(16)));

    // Cordages (lot M3)
    public static final BlockEntry<PulleyBlock> SMALL_PULLEY = part("small_pulley", () -> new PulleyBlock(1, wood()));
    public static final BlockEntry<PulleyBlock> PULLEY = part("pulley", () -> new PulleyBlock(2, wood()));
    public static final BlockEntry<PulleyBlock> LARGE_PULLEY = part("large_pulley", () -> new PulleyBlock(3, wood()));
    public static final BlockEntry<WinchBlock> WINCH = part("winch", () -> new WinchBlock(wood()));
    public static final RegistryObject<Item> FIBER_ROPE = PiCoreRegistries.ITEMS.register("fiber_rope", () -> new RopeItem(LinkType.FIBER_ROPE, new Item.Properties()));
    public static final RegistryObject<Item> TARRED_ROPE = PiCoreRegistries.ITEMS.register("tarred_rope", () -> new RopeItem(LinkType.TARRED_ROPE, new Item.Properties()));
    public static final RegistryObject<Item> LEATHER_BELT = PiCoreRegistries.ITEMS.register("leather_belt", () -> new RopeItem(LinkType.LEATHER_BELT, new Item.Properties()));

    // Machines mecaniques (lot M4)
    public static final Map<MachineType, BlockEntry<MechanicalMachineBlock>> MACHINES = new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            MACHINES.put(type, part(type.id(), () -> new MechanicalMachineBlock(type, type == MachineType.MILLSTONE || type == MachineType.JAW_CRUSHER || type == MachineType.DRILL
                                                                                       ? stone() : wood())));
        }
    }

    public static BlockEntry<MechanicalMachineBlock> machine(MachineType type) {
        return MACHINES.get(type);
    }

    public static final RegistryObject<BlockEntityType<ShaftTile>> SHAFT_TILE = tile("shaft", ShaftTile::new, () -> new Block[]{WOODEN_SHAFT.getBlock(), IRON_SHAFT.getBlock()});
    public static final RegistryObject<BlockEntityType<CogTile>> COG_TILE = tile("cog", CogTile::new, () -> new Block[]{WOODEN_COG.getBlock(), WOODEN_COGWHEEL.getBlock(), IRON_COG.getBlock(), IRON_COGWHEEL.getBlock()});
    public static final RegistryObject<BlockEntityType<GearboxBlock.Tile>> GEARBOX_TILE = tile("gearbox", GearboxBlock.Tile::new, () -> new Block[]{GEARBOX.getBlock()});
    public static final RegistryObject<BlockEntityType<InlineBlock.Tile>> INLINE_TILE = tile("inline_box", InlineBlock.Tile::new, () -> new Block[]{GEARSHIFT.getBlock(), CLUTCH.getBlock(), REVERSER.getBlock()});
    public static final RegistryObject<BlockEntityType<FlywheelBlock.Tile>> FLYWHEEL_TILE = tile("flywheel", FlywheelBlock.Tile::new, () -> new Block[]{STONE_FLYWHEEL.getBlock(), IRON_FLYWHEEL.getBlock()});
    public static final RegistryObject<BlockEntityType<GaugeBlock.Tile>> GAUGE_TILE = tile("kinetic_gauge", GaugeBlock.Tile::new, () -> new Block[]{TACHOMETER.getBlock(), DYNAMOMETER.getBlock()});
    public static final RegistryObject<BlockEntityType<GovernorBlock.Tile>> GOVERNOR_TILE = tile("governor", GovernorBlock.Tile::new, () -> new Block[]{GOVERNOR.getBlock()});
    public static final RegistryObject<BlockEntityType<HorseMillTile>> HORSE_MILL_TILE = tile("horse_mill", HorseMillTile::new, () -> new Block[]{HORSE_MILL.getBlock()});
    public static final RegistryObject<BlockEntityType<WaterWheelTile>> WATER_WHEEL_TILE = tile("water_wheel", WaterWheelTile::new,
          () -> new Block[]{WATER_WHEEL.getBlock(), LARGE_WATER_WHEEL.getBlock()});
    public static final RegistryObject<BlockEntityType<WindmillTile>> WINDMILL_TILE = tile("windmill_hub", WindmillTile::new, () -> new Block[]{WINDMILL.getBlock()});
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<FeedingTroughBlock.Tile>> TROUGH_TILE = PiTileEntityTypes.VANILLA_TILES.register("feeding_trough",
          () -> BlockEntityType.Builder.of(FeedingTroughBlock.Tile::new, FEEDING_TROUGH.getBlock()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<WeatherVaneBlock.Tile>> VANE_TILE = PiTileEntityTypes.VANILLA_TILES.register("weather_vane",
          () -> BlockEntityType.Builder.of(WeatherVaneBlock.Tile::new, WEATHER_VANE.getBlock()).build(null));
    public static final RegistryObject<BlockEntityType<PulleyTile>> PULLEY_TILE = tile("pulley", PulleyTile::new,
          () -> new Block[]{SMALL_PULLEY.getBlock(), PULLEY.getBlock(), LARGE_PULLEY.getBlock()});
    public static final RegistryObject<BlockEntityType<WinchTile>> WINCH_TILE = tile("winch", WinchTile::new, () -> new Block[]{WINCH.getBlock()});
    public static final RegistryObject<BlockEntityType<BeamEngineTile>> BEAM_ENGINE_TILE = tile("beam_engine", BeamEngineTile::new,
          () -> new Block[]{BEAM_ENGINE.getBlock()});
    public static final RegistryObject<BlockEntityType<MechanicalMachineTile>> MACHINE_TILE = tile("mechanical_machine", MechanicalMachineTile::new,
          () -> MACHINES.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new));
    public static final RegistryObject<BlockEntityType<HandCrankBlock.Tile>> HAND_CRANK_TILE = tile("hand_crank", HandCrankBlock.Tile::new, () -> new Block[]{HAND_CRANK.getBlock()});
}
