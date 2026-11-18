package fr.pioneers.logistics.pneumatic.network;

import fr.pioneers.core.block.Waterlogging;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Ancien tube en blocs (jusqu'a 0.20.38) : toujours parcouru par les cabines, plus posable ; depuis 0.20.39 l'hypertube est une courbe ({@link HypertubeItem}, {@link HypertubeWorld}).
 * Tube pneumatique : gros tube de verre cercle de laiton ou filent les capsules de voyageurs ; il se raccorde aux tubes et aux stations
 * voisins dans les six directions (on peut monter et descendre). Le chemin d'une capsule est calcule par {@link TubeRoutes} ; poser ou
 * casser un tube change la version du reseau.
 */
public class PneumaticTubeBlock extends Block implements SimpleWaterloggedBlock {

    public static final Map<Direction, BooleanProperty> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, BlockStateProperties.NORTH);
        ARMS.put(Direction.SOUTH, BlockStateProperties.SOUTH);
        ARMS.put(Direction.EAST, BlockStateProperties.EAST);
        ARMS.put(Direction.WEST, BlockStateProperties.WEST);
        ARMS.put(Direction.UP, BlockStateProperties.UP);
        ARMS.put(Direction.DOWN, BlockStateProperties.DOWN);
    }

    private static final VoxelShape CORE = Block.box(2, 2, 2, 14, 14, 14);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = new EnumMap<>(Direction.class);

    static {
        ARM_SHAPES.put(Direction.NORTH, Block.box(2, 2, 0, 14, 14, 2));
        ARM_SHAPES.put(Direction.SOUTH, Block.box(2, 2, 14, 14, 14, 16));
        ARM_SHAPES.put(Direction.WEST, Block.box(0, 2, 2, 2, 14, 14));
        ARM_SHAPES.put(Direction.EAST, Block.box(14, 2, 2, 16, 14, 14));
        ARM_SHAPES.put(Direction.DOWN, Block.box(2, 0, 2, 14, 2, 14));
        ARM_SHAPES.put(Direction.UP, Block.box(2, 14, 2, 14, 16, 14));
    }

    private final Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();

    public PneumaticTubeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) {
            state = state.setValue(arm, false);
        }
        registerDefaultState(state.setValue(Waterlogging.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlogging.WATERLOGGED);
        ARMS.values().forEach(builder::add);
    }

    /** Un tube se raccorde-t-il a ce bloc ? (tube ou station) */
    public static boolean links(BlockState state) {
        return state.getBlock() instanceof PneumaticTubeBlock || state.getBlock() instanceof TubeStationBlock;
    }

    private BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Map.Entry<Direction, BooleanProperty> e : ARMS.entrySet()) {
            state = state.setValue(e.getValue(), links(level.getBlockState(pos.relative(e.getKey()))));
        }
        return state;
    }

    private BlockState placement0(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Waterlogging.tick(state, level, pos);
        return state.setValue(ARMS.get(direction), links(neighbor));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this)) {
            TubeRoutes.changed(level);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replaced, boolean moving) {
        super.onRemove(state, level, pos, replaced, moving);
        if (!replaced.is(this)) {
            TubeRoutes.changed(level);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shapes.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Map.Entry<Direction, BooleanProperty> e : ARMS.entrySet()) {
                if (s.getValue(e.getValue())) {
                    shape = Shapes.or(shape, ARM_SHAPES.get(e.getKey()));
                }
            }
            return shape;
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = placement0(context);
        return state == null ? null : Waterlogging.placed(state, context.getLevel(), context.getClickedPos());
    }

    /** 0.20.22 : etanche : gorge d'eau dans l'eau, jamais emporte par elle. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(@NotNull BlockState state) {
        return Waterlogging.fluid(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull Fluid fluid) {
        return false;
    }
}
