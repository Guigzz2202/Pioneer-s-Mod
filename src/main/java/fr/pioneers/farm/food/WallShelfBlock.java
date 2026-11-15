package fr.pioneers.farm.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 12, Guillaume : « une murale et une au sol ; la murale se place seulement sur le flanc d'un bloc ; en gros une planche
 * avec 2 equerres (se connecte parfaitement avec les autres horizontalement) ») : etagere MURALE, une par bois
 * ({@link DishShelfBlock#WALL_VARIANTS}, ids {@code <bois>_wall_shelf}, meme tuile {@link DishShelfTile} : 4 places).
 * <ul>
 *   <li>Pose seulement par un clic sur la face LATERALE d'un bloc solide ({@link #FACING} = cette face, dos au mur) ; refusee sur le dessus
 *   et le dessous ; tombe (avec ses objets) si le mur disparait.</li>
 *   <li>Liaisons ({@link ShelfJoints#links}) : murales voisines de meme facade, a la meme hauteur ; planche continue, equerres seulement aux
 *   bouts de la rangee ({@link #LEFT} / {@link #RIGHT} faux), plus une au milieu du bloc dans une longue rangee ({@link #MID},
 *   {@link ShelfJoints#midBracket}).</li>
 *   <li>Objets : comme l'etagere au sol (clic droit avec un plat a la place visee ; main vide : on le reprend).</li>
 * </ul>
 */
public class WallShelfBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LEFT = DishShelfBlock.LEFT;
    public static final BooleanProperty RIGHT = DishShelfBlock.RIGHT;
    /** Equerre au milieu du bloc (longue rangee). */
    public static final BooleanProperty MID = BooleanProperty.create("mid");

    private final String wood;

    public WallShelfBlock(Properties properties, String wood) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false).setValue(MID, false));
    }

    public String wood() {
        return wood;
    }

    /** Murale voisine liee a {@code s} dans la meme rangee (meme facade). */
    private static boolean same(BlockState s, BlockState other) {
        return other.getBlock() instanceof WallShelfBlock && other.getValue(FACING) == s.getValue(FACING);
    }

    /** Liaisons : voisines de gauche et de droite (repere du modele : gauche = sens antihoraire de la facade), equerre du milieu. */
    public static BlockState connect(BlockState s, LevelReader level, BlockPos pos) {
        Direction f = s.getValue(FACING);
        BlockState l = level.getBlockState(pos.relative(f.getCounterClockWise())), r = level.getBlockState(pos.relative(f.getClockWise()));
        int links = ShelfJoints.links(DishShelfBlock.cell(s), null, null, DishShelfBlock.cell(l), DishShelfBlock.cell(r));
        boolean left = (links & ShelfJoints.LEFT) != 0, right = (links & ShelfJoints.RIGHT) != 0;
        int along = f.getAxis() == Direction.Axis.Z ? pos.getX() : pos.getZ();
        boolean mid = ShelfJoints.midBracket(left, right, left && same(s, l) && l.getValue(LEFT), right && same(s, r) && r.getValue(RIGHT), along);
        return s.setValue(LEFT, left).setValue(RIGHT, right).setValue(MID, mid);
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        BlockState now = connect(state, level, pos);
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isHorizontal()) {
            return null;                                           //seulement sur le flanc d'un bloc
        }
        BlockState s = defaultBlockState().setValue(FACING, face);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? connect(s, ctx.getLevel(), ctx.getClickedPos()) : null;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, f);
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return connect(state, level, pos);
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DishShelfTile tile)) {
            return InteractionResult.PASS;
        }
        return DishShelfBlock.interact(tile, DishShelfBlock.boards(state).get(0), DishShelfBlock.along(state.getValue(FACING), pos, hit.getLocation()), level,
              pos, player, hand);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            DishShelfBlock.dropAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ forme : planche, equerres (repere du modele, dos au sud)
    private static final VoxelShape[] SHAPES = new VoxelShape[4 * 8];

    private static VoxelShape bracket(Direction f, double x0) {
        return Shapes.or(DishShelfBlock.box(f, x0, 2, 15, x0 + 1, 6.5, 16), DishShelfBlock.box(f, x0, 5.5, 10, x0 + 1, 6.5, 15));
    }

    private static VoxelShape shape(BlockState state) {
        Direction f = state.getValue(FACING);
        boolean left = state.getValue(LEFT), right = state.getValue(RIGHT), mid = state.getValue(MID);
        int key = f.get2DDataValue() * 8 + (left ? 4 : 0) + (right ? 2 : 0) + (mid ? 1 : 0);
        VoxelShape s = SHAPES[key];
        if (s != null) {
            return s;
        }
        s = Shapes.or(DishShelfBlock.box(f, 0, 6.5, 9, 16, 7.9, 16), DishShelfBlock.box(f, 0, 7.5, 15.3, 16, 9, 16));
        if (!left) {
            s = Shapes.or(s, bracket(f, 2.25));
        }
        if (!right) {
            s = Shapes.or(s, bracket(f, 12.75));
        }
        if (mid) {
            s = Shapes.or(s, bracket(f, 7.5));
        }
        SHAPES[key] = s;
        return s;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shape(state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, MID);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DishShelfTile(pos, state);
    }
}
