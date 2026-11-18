package fr.pioneers.space.station;

import fr.pioneers.core.PiTags;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registries.PiMachines;
import fr.pioneers.space.tile.PiSpaceMachines;
import fr.pioneers.space.LaunchPadStructure;
import fr.pioneers.space.tile.TileEntityLaunchPad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Construit une station orbitale autour de son pont (coordonnees relatives au centre du pont) :
 * <pre>
 *   z
 *   ^   [ module d'habitation 9x7 : diffuseur d'oxygene ]   z = 9..15
 *   |   [ sas : porte etanche / sas / porte etanche ]         z = 5..8
 *   |        (pas de tir) --- tour de lancement (x+2)          z = -2..2
 *   +----> x            pont circulaire de rayon 18
 * </pre>
 * Le reservoir du pas de tir est livre plein d'hydrogene, pour un premier retour.
 */
public final class StationBuilder {

    private StationBuilder() {
    }

    private static final int DECK_RADIUS = 18;
    private static final int PAD_RADIUS = LaunchPadStructure.Size.SMALL.radius;
    private static final int TOWER_HEIGHT = LaunchPadStructure.Size.SMALL.towerHeight;
    private static final int ROOM_X = 4, ROOM_Z0 = 9, ROOM_Z1 = 15, ROOM_HEIGHT = 4;
    private static final int SAS_OUTER = 5, SAS_INNER = 8;

    public static void build(ServerLevel level, BlockPos center) {
        BlockState hull = block("pioneers:steel_block", Blocks.IRON_BLOCK);
        BlockState trim = block("pioneers:osmium_block", Blocks.LIGHT_GRAY_CONCRETE);
        BlockState glass = block("mekanism:structural_glass", Blocks.GLASS);
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        //Pont circulaire, bord en osmium, balises lumineuses
        for (int dx = -DECK_RADIUS; dx <= DECK_RADIUS; dx++) {
            for (int dz = -DECK_RADIUS; dz <= DECK_RADIUS; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > DECK_RADIUS * DECK_RADIUS + 1) {
                    continue;
                }
                boolean edge = d2 > (DECK_RADIUS - 1) * (DECK_RADIUS - 1);
                boolean beacon = edge && (dx == 0 || dz == 0);
                set(level, center.offset(dx, 0, dz), beacon ? light : edge ? trim : hull);
                set(level, center.offset(dx, -1, dz), trim);
            }
        }
        //Pas de tir (petit) et sa tour
        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                if ((dx != 0 || dz != 0) && !LaunchPadStructure.isOutside(dx, dz, PAD_RADIUS)) {
                    set(level, center.offset(dx, 0, dz), PiBlocks.LAUNCH_PAD.getBlock().defaultBlockState());
                }
            }
        }
        set(level, center, PiBaseMachines.LAUNCH_PAD_CORE.getBlock().defaultBlockState());
        for (int y = 1; y <= TOWER_HEIGHT; y++) {
            set(level, center.offset(PAD_RADIUS, y, 0), PiBlocks.LAUNCH_TOWER.getBlock().defaultBlockState());
        }
        LaunchPadStructure.formation(level, center);
        if (level.getBlockEntity(center) instanceof TileEntityLaunchPad pad) {
            //Plein de depart : le premier carburant connu (tag pioneers:rocket_fuel)
            ForgeRegistries.FLUIDS.tags().getTag(PiTags.Fluids.ROCKET_FUEL).stream().findFirst()
                  .ifPresent(fuel -> pad.fuelTank.fill(new FluidStack(fuel, TileEntityLaunchPad.TANK_CAPACITY), IFluidHandler.FluidAction.EXECUTE));
        }
        //Sas : deux portes etanches et une chambre entre elles
        for (int z = SAS_OUTER; z <= SAS_INNER; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = 1; y <= 3; y++) {
                    boolean wall = x == -2 || x == 2 || z == SAS_OUTER || z == SAS_INNER || y == 3;
                    set(level, center.offset(x, y, z), wall ? hull : Blocks.AIR.defaultBlockState());
                }
            }
        }
        //Module d'habitation
        for (int z = ROOM_Z0 - 1; z <= ROOM_Z1 + 1; z++) {
            for (int x = -ROOM_X - 1; x <= ROOM_X + 1; x++) {
                for (int y = 1; y <= ROOM_HEIGHT + 1; y++) {
                    boolean side = x == -ROOM_X - 1 || x == ROOM_X + 1 || z == ROOM_Z0 - 1 || z == ROOM_Z1 + 1;
                    boolean ceiling = y == ROOM_HEIGHT + 1;
                    BlockState state;
                    if (ceiling) {
                        state = (x == 0 || Math.abs(x) == 3) && (z - ROOM_Z0) % 3 == 1 ? light : hull;
                    } else if (side) {
                        boolean window = (y == 2 || y == 3) && (Math.abs(x) <= 2 && z == ROOM_Z1 + 1 || Math.abs(z - 12) <= 1 && x != 0);
                        state = window ? glass : hull;
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(level, center.offset(x, y, z), state);
                }
            }
        }
        //Portes etanches (mur exterieur du sas, puis mur commun avec le module)
        door(level, center.offset(0, 1, SAS_OUTER), Direction.SOUTH);
        door(level, center.offset(0, 1, SAS_INNER), Direction.SOUTH);
        //Diffuseur d'oxygene au fond du module
        BlockPos diffuser = center.offset(0, 1, ROOM_Z1);
        set(level, diffuser, PiSpaceMachines.OXYGEN_DIFFUSER.getBlock().defaultBlockState()
              .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void door(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState lower = PiMachines.AIRLOCK_DOOR.getBlock().defaultBlockState().setValue(DoorBlock.FACING, facing)
              .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, Block.UPDATE_ALL);
        level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static BlockState block(String id, Block fallback) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(id));
        return (block == null || block == Blocks.AIR ? fallback : block).defaultBlockState();
    }
}
