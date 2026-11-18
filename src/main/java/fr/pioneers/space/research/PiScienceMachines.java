package fr.pioneers.space.research;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.space.tile.TileEntityAsteroidCrusher;
import fr.pioneers.space.tile.TileEntityBioSequencer;
import fr.pioneers.space.tile.TileEntityBiomeAnalyzer;
import fr.pioneers.space.tile.TileEntityDataAntenna;
import fr.pioneers.space.tile.TileEntityGeoSpectrometer;
import fr.pioneers.space.tile.TileEntityResearchTable;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiBlockShapes;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Machines scientifiques passees au socle (lot B) : broyeur d'asteroides, spectrometre geologique, sequenceur biologique, analyseur de
 * biome, table de recherche ({@link MachineTile}).
 */
public final class PiScienceMachines {

    private PiScienceMachines() {
    }

    public static void init() {
    }

    public static final BlockEntry<MachineBlock> ASTEROID_CRUSHER = machine("asteroid_crusher", MapColor.COLOR_ORANGE, PiBlockShapes.ANALYZER_SHAPES,
          () -> PiScienceMachines.ASTEROID_CRUSHER_TILE.get(), TileEntityAsteroidCrusher::new);
    public static final BlockEntry<MachineBlock> GEO_SPECTROMETER = machine("geo_spectrometer", MapColor.COLOR_CYAN, PiBlockShapes.ANALYZER_SHAPES,
          () -> PiScienceMachines.GEO_SPECTROMETER_TILE.get(), TileEntityGeoSpectrometer::new);
    public static final BlockEntry<MachineBlock> BIO_SEQUENCER = machine("bio_sequencer", MapColor.COLOR_GREEN, PiBlockShapes.ANALYZER_SHAPES,
          () -> PiScienceMachines.BIO_SEQUENCER_TILE.get(), TileEntityBioSequencer::new);

    public static final BlockEntry<MachineBlock> BIOME_ANALYZER = machine("biome_analyzer", MapColor.METAL, PiBlockShapes.BIOME_ANALYZER_SHAPES,
          () -> PiScienceMachines.BIOME_ANALYZER_TILE.get(), TileEntityBiomeAnalyzer::new);

    public static final BlockEntry<MachineBlock> RESEARCH_TABLE = machine("research_table", MapColor.METAL, PiBlockShapes.RESEARCH_TABLE_SHAPES,
          () -> PiScienceMachines.RESEARCH_TABLE_TILE.get(), TileEntityResearchTable::new);

    public static final BlockEntry<MachineBlock> DATA_ANTENNA = machine("data_antenna", MapColor.METAL, PiBlockShapes.DATA_ANTENNA_SHAPES,
          MachineBlock.box(0, 0, 1, 2, 0, 0), () -> PiScienceMachines.DATA_ANTENNA_TILE.get(), TileEntityDataAntenna::new);

    public static final RegistryObject<BlockEntityType<TileEntityDataAntenna>> DATA_ANTENNA_TILE = tile("data_antenna", TileEntityDataAntenna::new,
          DATA_ANTENNA);
    public static final RegistryObject<BlockEntityType<TileEntityResearchTable>> RESEARCH_TABLE_TILE = tile("research_table", TileEntityResearchTable::new,
          RESEARCH_TABLE);
    public static final RegistryObject<BlockEntityType<TileEntityBiomeAnalyzer>> BIOME_ANALYZER_TILE = tile("biome_analyzer", TileEntityBiomeAnalyzer::new,
          BIOME_ANALYZER);
    public static final RegistryObject<BlockEntityType<TileEntityAsteroidCrusher>> ASTEROID_CRUSHER_TILE = tile("asteroid_crusher",
          TileEntityAsteroidCrusher::new, ASTEROID_CRUSHER);
    public static final RegistryObject<BlockEntityType<TileEntityGeoSpectrometer>> GEO_SPECTROMETER_TILE = tile("geo_spectrometer",
          TileEntityGeoSpectrometer::new, GEO_SPECTROMETER);
    public static final RegistryObject<BlockEntityType<TileEntityBioSequencer>> BIO_SEQUENCER_TILE = tile("bio_sequencer", TileEntityBioSequencer::new,
          BIO_SEQUENCER);

    public static BlockEntry<MachineBlock> machine(String name, MapColor color, VoxelShape[] shapes, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        return machine(name, color, shapes, List.of(), type, factory);
    }

    /** Machine du socle a modele 3D ; {@code bounds} : blocs fantomes d'une grande machine. */
    public static BlockEntry<MachineBlock> machine(String name, MapColor color, VoxelShape[] shapes, List<Vec3i> bounds,
          Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        return PiCoreRegistries.block(name, () -> new MachineBlock(BlockBehaviour.Properties.of().mapColor(color).strength(3.5F, 9.0F).sound(SoundType.METAL)
              .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 8 : 0), type, factory)
              .shaped(shapes).bounds(bounds));
    }

    @SuppressWarnings("ConstantConditions")
    public static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> tile(String name, BlockEntityType.BlockEntitySupplier<T> factory,
          BlockEntry<MachineBlock> block) {
        return PiTileEntityTypes.VANILLA_TILES.register(name, () -> BlockEntityType.Builder.of(factory, block.getBlock()).build(null));
    }
}
