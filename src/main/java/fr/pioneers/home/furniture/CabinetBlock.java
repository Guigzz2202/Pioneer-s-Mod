package fr.pioneers.home.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « meubles de rangement (textures connectees pour les angles, detection du bloc au-dessus ou en dessous
 * pour determiner s'il s'agit d'un rangement en hauteur ou d'un comptoir au sol) ») : meuble d'un bois a 18 cases ({@link CabinetTile}).
 * <ul>
 *   <li>REGLE : pose sur un bloc qui le porte (face du dessus pleine) ou sur un autre meuble bas = COMPTOIR (caisson a portes, plinthe en
 *   retrait, plan de travail) ; pose sans rien dessous (accroche au mur) = PLACARD HAUT ({@link #UPPER}, moins profond).</li>
 *   <li>Voisins du meme mode relies ({@link #LEFT}, {@link #RIGHT}) : pas de joue entre eux, plan de travail continu ; angles comme les
 *   escaliers ({@link #SHAPE}) : angle rentrant = caisson d'angle, angle sortant = portes sur les deux faces visibles.</li>
 *   <li>Tout se recalcule quand un voisin change (y compris le bloc du dessous : un comptoir dont on retire le support devient placard).</li>
 * </ul>
 * Repere des modeles : facade au NORD, mur au sud ; gauche = ouest du modele.
 */
public class CabinetBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    /** Profondeur du placard haut (px, depuis le mur). */
    public static final double UPPER_DEPTH = 10;

    public CabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UPPER, false).setValue(LEFT, false).setValue(RIGHT, false)
              .setValue(SHAPE, StairsShape.STRAIGHT));
    }

    // ------------------------------------------------------------------ regle comptoir / placard, liaisons
    /** Comptoir si le bloc du dessous le porte (dessus plein) ou est un comptoir ; sinon placard haut (logique lue par les GameTests). */
    public static boolean upper(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.getBlock() instanceof CabinetBlock) {
            return below.getValue(UPPER);
        }
        return !below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean cabinet(BlockState s, boolean upper) {
        return s.getBlock() instanceof CabinetBlock && s.getValue(UPPER) == upper;
    }

    /** Etat complet : mode, liaisons de cote, forme d'angle. */
    public static BlockState connect(BlockState s, BlockGetter level, BlockPos pos) {
        boolean up = upper(level, pos);
        s = s.setValue(UPPER, up);
        Direction f = s.getValue(FACING);
        //Gauche du modele (facade au nord) = ouest = sens antihoraire de la facade
        BlockState l = level.getBlockState(pos.relative(f.getCounterClockWise())), r = level.getBlockState(pos.relative(f.getClockWise()));
        s = s.setValue(LEFT, cabinet(l, up)).setValue(RIGHT, cabinet(r, up));
        return s.setValue(SHAPE, shape(s, level, pos));
    }

    /**
     * Angle, comme {@code StairBlock.getStairsShape} avec « dos » = mur : un meuble perpendiculaire DERRIERE = angle sortant (le caisson montre
     * deux faces), DEVANT = angle rentrant (caisson d'angle) ; pas d'angle si le voisin de cote du meme alignement est deja la.
     */
    static StairsShape shape(BlockState s, BlockGetter level, BlockPos pos) {
        boolean up = s.getValue(UPPER);
        Direction f = s.getValue(FACING), back = f.getOpposite();
        BlockState behind = level.getBlockState(pos.relative(back));
        if (cabinet(behind, up)) {
            Direction b = behind.getValue(FACING);
            if (b.getAxis() != f.getAxis() && !sameRow(s, level, pos.relative(b))) {
                return b == f.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState front = level.getBlockState(pos.relative(f));
        if (cabinet(front, up)) {
            Direction b = front.getValue(FACING);
            if (b.getAxis() != f.getAxis() && !sameRow(s, level, pos.relative(b.getOpposite()))) {
                return b == f.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean sameRow(BlockState s, BlockGetter level, BlockPos other) {
        BlockState o = level.getBlockState(other);
        return cabinet(o, s.getValue(UPPER)) && o.getValue(FACING) == s.getValue(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connect(defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()), ctx.getLevel(), ctx.getClickedPos());
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        return connect(state, level, pos);
    }

    @NotNull
    @Override
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------ inventaire
    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CabinetTile tile) {
            player.openMenu(tile);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CabinetTile tile) {
                Containers.dropContents(level, pos, tile);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CabinetTile(pos, state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    // ------------------------------------------------------------------ formes
    private static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
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
        if (!state.getValue(UPPER)) {
            return Shapes.block();
        }
        //Placard haut : moins profond, sauf l'angle rentrant (caisson plein dans l'angle)
        StairsShape sh = state.getValue(SHAPE);
        Direction f = state.getValue(FACING);
        if (sh == StairsShape.INNER_LEFT || sh == StairsShape.INNER_RIGHT) {
            return Shapes.block();
        }
        return box(f, 0, 0, 16 - UPPER_DEPTH, 16, 16, 16);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPPER, LEFT, RIGHT, SHAPE);
    }
}
