package fr.pioneers.space.assembly;

import fr.pioneers.core.common.projector.Projections;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.recipes.AssemblyStation;
import fr.pioneers.space.assembly.AssemblyHall;
import fr.pioneers.space.assembly.AssemblyHallBlueprint;
import fr.pioneers.space.assembly.AssemblyHallControllerBlock;
import fr.pioneers.space.tile.TileEntityAssemblyHall;
import fr.pioneers.space.tile.TileEntityAssemblyTable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiBlockShapes;
import fr.pioneers.core.registries.PiTileEntityTypes;
import fr.pioneers.space.research.PiScienceMachines;

/**
 * Ateliers passes au socle (lot C) : tables d'assemblage de modules et d'equipement (un bloc).
 * <p>
 * 1.9.2 (retours de Guillaume : « les robotics assembly table et rocket assembly table devraient etre des multiblocs ; on supprime le modele
 * 3D pour deux multi beaucoup plus classe et impressionnants ») : l'assembleur de fusees (3x3x4 a blocs fantomes) et la table d'assemblage
 * robotique (plateforme 3x3x2) sont remplaces par deux multiblocs construits ({@link AssemblyHall}) : la baie d'assemblage robotique
 * ({@code robotic_assembly_bay}) et le hall d'integration des fusees ({@code rocket_integration_hall}). Anciens ids remappes vers leurs
 * controleurs ({@code common/PiRemaps}).
 */
public final class PiWorksMachines {

    private PiWorksMachines() {
    }

    public static void init() {
        //1.9.2 : plans projetables des multiblocs d'assemblage
        Projections.addProvider(AssemblyHallBlueprint::forBlock);
    }

    //0.20.34 : le synthetiseur planetaire est devenu le Synthetiseur, multibloc industriel (fr.pioneers.tech.industry)

    /** 1.9.2 : controleurs des multiblocs d'assemblage, par sorte. */
    public static final Map<AssemblyHall.Kind, BlockEntry<AssemblyHallControllerBlock>> HALLS = new EnumMap<>(AssemblyHall.Kind.class);
    public static final Map<AssemblyHall.Kind, RegistryObject<BlockEntityType<TileEntityAssemblyHall>>> HALL_TILES = new EnumMap<>(AssemblyHall.Kind.class);

    public static final String ROBOTIC_BAY_ID = "robotic_assembly_bay", ROCKET_HALL_ID = "rocket_integration_hall";

    static {
        hall(AssemblyHall.Kind.ROBOTICS, ROBOTIC_BAY_ID, MapColor.COLOR_ORANGE);
        hall(AssemblyHall.Kind.ROCKET, ROCKET_HALL_ID, MapColor.COLOR_RED);
    }

    @SuppressWarnings("ConstantConditions")
    private static void hall(AssemblyHall.Kind kind, String id, MapColor color) {
        BlockEntry<AssemblyHallControllerBlock> block = PiCoreRegistries.block(id, () -> new AssemblyHallControllerBlock(kind,
              BlockBehaviour.Properties.of().mapColor(color).strength(3.5F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 6 : 0), () -> HALL_TILES.get(kind).get(),
              kind == AssemblyHall.Kind.ROBOTICS ? TileEntityAssemblyHall::robotics : TileEntityAssemblyHall::rocket));
        HALLS.put(kind, block);
        HALL_TILES.put(kind, PiTileEntityTypes.VANILLA_TILES.register(id, () -> BlockEntityType.Builder.<TileEntityAssemblyHall>of(
              kind == AssemblyHall.Kind.ROBOTICS ? TileEntityAssemblyHall::robotics : TileEntityAssemblyHall::rocket, block.getBlock()).build(null)));
    }

    public static final Map<AssemblyStation, BlockEntry<MachineBlock>> ASSEMBLY_TABLES = new EnumMap<>(AssemblyStation.class);
    public static final Map<AssemblyStation, RegistryObject<BlockEntityType<TileEntityAssemblyTable>>> ASSEMBLY_TABLE_TILES = new EnumMap<>(AssemblyStation.class);

    static {
        for (AssemblyStation station : AssemblyStation.values()) {
            //1.9.2 : fusees et robots passent par les multiblocs d'assemblage
            if (station == AssemblyStation.ROCKET || station == AssemblyStation.ROVER) {
                continue;
            }
            String id = station.id() + "_assembly_table";
            BlockEntry<MachineBlock> block = PiScienceMachines.machine(id, MapColor.METAL, PiBlockShapes.ASSEMBLY_TABLE_SHAPES, List.of(),
                  () -> ASSEMBLY_TABLE_TILES.get(station).get(), (pos, state) -> new TileEntityAssemblyTable(ASSEMBLY_TABLE_TILES.get(station).get(), pos,
                        state, station));
            ASSEMBLY_TABLES.put(station, block);
            ASSEMBLY_TABLE_TILES.put(station, PiTileEntityTypes.VANILLA_TILES.register(id, () -> BlockEntityType.Builder.<TileEntityAssemblyTable>of(
                  (pos, state) -> new TileEntityAssemblyTable(ASSEMBLY_TABLE_TILES.get(station).get(), pos, state, station), block.getBlock()).build(null)));
        }
    }

    /** Bloc (controleur ou table) qui sert ce poste d'assemblage (JEI, guide). */
    public static BlockEntry<? extends MachineBlock> stationBlock(AssemblyStation station) {
        return switch (station) {
            case ROCKET -> HALLS.get(AssemblyHall.Kind.ROCKET);
            case ROVER -> HALLS.get(AssemblyHall.Kind.ROBOTICS);
            default -> ASSEMBLY_TABLES.get(station);
        };
    }
}
