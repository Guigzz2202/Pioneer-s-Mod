package fr.pioneers.logistics.robotics.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Route robotique : les unites au sol y roulent plus vite et la preferent pour leurs trajets. Elle se raccorde aux routes voisines
 * (ligne centrale continue, virages, carrefours, bouts de route).
 */
public class BlockRoboticRoad extends Block {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH, EAST = BlockStateProperties.EAST,
          WEST = BlockStateProperties.WEST;
    /** Ligne centrale vers chaque cote : seulement au milieu de la chaussee (une seule ligne sur une route de 3 de large). */
    public static final BooleanProperty LINE_NORTH = BooleanProperty.create("line_north"), LINE_SOUTH = BooleanProperty.create("line_south"),
          LINE_EAST = BooleanProperty.create("line_east"), LINE_WEST = BooleanProperty.create("line_west");
    /** Largeur de chaussee au-dela de laquelle on ne trace plus de ligne (place, carrefour large). */
    private static final int MAX_HALF = 3;

    public BlockRoboticRoad(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false)
              .setValue(LINE_NORTH, false).setValue(LINE_SOUTH, false).setValue(LINE_EAST, false).setValue(LINE_WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, LINE_NORTH, LINE_SOUTH, LINE_EAST, LINE_WEST);
    }

    private static BooleanProperty property(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            default -> WEST;
        };
    }

    /** Une route voisine (au meme niveau, ou une marche plus haut ou plus bas) ? */
    public static boolean connects(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        return level.getBlockState(next).getBlock() instanceof BlockRoboticRoad || level.getBlockState(next.above()).getBlock() instanceof BlockRoboticRoad
               || level.getBlockState(next.below()).getBlock() instanceof BlockRoboticRoad;
    }

    private static BooleanProperty line(Direction side) {
        return switch (side) {
            case NORTH -> LINE_NORTH;
            case SOUTH -> LINE_SOUTH;
            case EAST -> LINE_EAST;
            default -> LINE_WEST;
        };
    }

    private static boolean isRoad(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BlockRoboticRoad;
    }

    /**
     * Largeur de chaussee d'un cote ({@code across}) pour une route qui file vers {@code along} : les blocs de route a la suite qui
     * continuent eux aussi vers {@code along} (une route qui tourne ne compte pas), au plus MAX_HALF.
     */
    private static int run(BlockGetter level, BlockPos pos, Direction across, Direction along) {
        int count = 0;
        BlockPos next = pos.relative(across);
        while (count < MAX_HALF && isRoad(level, next) && isRoad(level, next.relative(along))) {
            count++;
            next = next.relative(across);
        }
        return count;
    }

    /**
     * La ligne centrale va vers {@code side} si la chaussee y continue et que ce bloc en est le milieu, en travers : autant de route de
     * chaque cote (ou un de plus a gauche pour une largeur paire), et pas une place trop large.
     */
    private static boolean centered(BlockGetter level, BlockPos pos, Direction side) {
        Direction across = side.getClockWise();
        int a = run(level, pos, across, side), b = run(level, pos, across.getOpposite(), side);
        if (a >= MAX_HALF || b >= MAX_HALF) {
            return false;
        }
        return a == b || a - b == 1;
    }

    BlockState connected(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean joined = connects(level, pos, side);
            state = state.setValue(property(side), joined).setValue(line(side), joined && centered(level, pos, side));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return connected(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        refreshSlopes(level, pos);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!state.is(next.getBlock())) {
            refreshSlopes(level, pos);
        }
    }

    /**
     * Les routes proches (pentes d'une marche, et la largeur de la chaussee qui decide de la ligne centrale) ne sont pas toutes des
     * voisines directes : on les previent nous-memes.
     */
    private void refreshSlopes(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        for (int dx = -MAX_HALF; dx <= MAX_HALF; dx++) {
            for (int dz = -MAX_HALF; dz <= MAX_HALF; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    BlockPos other = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(other);
                    if (state.getBlock() instanceof BlockRoboticRoad road) {
                        BlockState updated = road.connected(level, other, state);
                        if (updated != state) {
                            level.setBlock(other, updated, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return connected(level, pos, state);
    }
}
