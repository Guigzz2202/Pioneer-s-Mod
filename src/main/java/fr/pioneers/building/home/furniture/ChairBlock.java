package fr.pioneers.building.home.furniture;

import fr.pioneers.building.home.PiHome;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « chaises (on s'assoit : entite de siege invisible comme les mods de meubles, orientee) ») : chaise d'un
 * bois, tournee vers le joueur a la pose ({@link #FACING} = cote ou l'on est assis face a). Clic droit main vide (ou avec n'importe quoi sans
 * usage) : on s'assoit sur un {@link SeatEntity} invisible, tourne comme la chaise ; Maj pour se lever.
 */
public class ChairBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Dessus de l'assise (px). */
    public static final double SEAT = 8;
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    public ChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        List<SeatEntity> taken = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!taken.isEmpty() || player.distanceToSqr(pos.getCenter()) > 9) {
            return InteractionResult.CONSUME;
        }
        SeatEntity seat = new SeatEntity(PiHome.SEAT.get(), level);
        seat.moveTo(pos.getX() + 0.5, pos.getY() + SeatEntity.HEIGHT, pos.getZ() + 0.5, state.getValue(FACING).toYRot(), 0);
        level.addFreshEntity(seat);
        player.setYRot(state.getValue(FACING).toYRot());
        player.startRiding(seat);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, new AABB(pos))) {
                seat.ejectPassengers();
                seat.discard();
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        //Repere du modele : face (assise vers le joueur) au nord, dossier au sud
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        Direction f = state.getValue(FACING);
        int i = f.get2DDataValue();
        if (SHAPES[i] == null) {
            SHAPES[i] = Shapes.or(box(f, 2, 0, 2, 14, SEAT, 14), box(f, 2, SEAT, 12, 14, 20, 14));
        }
        return SHAPES[i];
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull PathComputationType type) {
        return false;
    }

    @NotNull
    @Override
    public BlockState rotate(@NotNull BlockState state, @NotNull net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
