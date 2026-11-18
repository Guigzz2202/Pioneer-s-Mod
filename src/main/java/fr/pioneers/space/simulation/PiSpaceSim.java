package fr.pioneers.space.simulation;

import fr.pioneers.space.content.spacesim.SpaceSimControllerBlock;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.space.content.spacesim.SpaceSimPart;
import fr.pioneers.space.content.spacesim.SpaceSimPartBlock;
import fr.pioneers.space.content.spacesim.SpaceSimPartTile;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.core.registry.PiCoreRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import fr.pioneers.core.registries.PiTileEntityTypes;

/**
 * Simulateur spatial (socle, lot E) : un seul multibloc pour le systeme solaire et la galaxie, ce qui est simule suit l'objet du coeur.
 * Remplace le Simulateur du systeme solaire et le Simulateur d'univers (anciens blocs remappes par {@code PiRemaps}).
 */
public final class PiSpaceSim {

    private PiSpaceSim() {
    }

    public static void init() {
    }

    public static final Map<SpaceSimPart, BlockEntry<? extends SpaceSimPartBlock>> PARTS = new EnumMap<>(SpaceSimPart.class);

    private static BlockBehaviour.Properties wall(SpaceSimPart part) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(part == SpaceSimPart.COIL ? MapColor.COLOR_CYAN : MapColor.COLOR_GRAY)
              .strength(5.0F, 12.0F).requiresCorrectToolForDrops().sound(part == SpaceSimPart.GLASS ? SoundType.GLASS : SoundType.METAL);
        if (part == SpaceSimPart.GLASS) {
            properties = properties.noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false);
        } else if (part == SpaceSimPart.COIL) {
            //Bobine allumee : invisible et lumineuse
            properties = properties.noOcclusion().lightLevel(state -> state.getValue(SpaceSimPartBlock.ACTIVE) ? 15 : 4);
        } else if (part == SpaceSimPart.PROJECTOR) {
            properties = properties.noOcclusion().lightLevel(state -> 10);
        }
        return properties;
    }

    static {
        for (SpaceSimPart part : SpaceSimPart.values()) {
            PARTS.put(part, part == SpaceSimPart.PROJECTOR ? PiCoreRegistries.block(part.id, () -> new SpaceSimPartBlock.Projector(wall(part)))
                                                           : PiCoreRegistries.block(part.id, () -> new SpaceSimPartBlock(part, wall(part))));
        }
    }

    public static final BlockEntry<? extends SpaceSimPartBlock> CASING = PARTS.get(SpaceSimPart.CASING);
    public static final BlockEntry<? extends SpaceSimPartBlock> GLASS = PARTS.get(SpaceSimPart.GLASS);
    public static final BlockEntry<? extends SpaceSimPartBlock> PORT = PARTS.get(SpaceSimPart.PORT);
    public static final BlockEntry<? extends SpaceSimPartBlock> PROJECTOR = PARTS.get(SpaceSimPart.PROJECTOR);
    public static final BlockEntry<? extends SpaceSimPartBlock> COIL = PARTS.get(SpaceSimPart.COIL);
    public static final BlockEntry<SpaceSimControllerBlock> CONTROLLER = PiCoreRegistries.block("space_simulator_controller",
          () -> new SpaceSimControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0F, 12.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 12 : 2)));

    public static final RegistryObject<BlockEntityType<SpaceSimPartTile>> PART_TILE = PiTileEntityTypes.VANILLA_TILES.register("space_simulator_part",
          () -> BlockEntityType.Builder.of(SpaceSimPartTile::new, PARTS.values().stream().map(BlockEntry::getBlock).toArray(Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<SpaceSimControllerTile>> CONTROLLER_TILE = PiTileEntityTypes.VANILLA_TILES.register(
          "space_simulator_controller", () -> BlockEntityType.Builder.of(SpaceSimControllerTile::new, CONTROLLER.getBlock()).build(null));
}
