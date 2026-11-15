package fr.pioneers.computing;

import fr.pioneers.registries.PiComputing;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ecran de supervision (0.17.0) : dalle d'affichage qui se raccorde a ses voisines de meme orientation (cadre seulement sur les bords
 * libres, mur de 6 x 4 au plus). Le bloc en bas a gauche du mur (vu de face) porte la liaison et dessine tout le mur
 * ({@link ScreenTile}). Etats : orientation (4) x 4 raccords = 64. 1.0.4-beta (lot 26) : un assemblage non rectangulaire est decoupe en
 * ecrans rectangulaires ({@link ScreenWall}, le plus grand d'abord) ; un raccord = la voisine fait partie du MEME ecran (un seul cadre
 * autour de chaque ecran) ; recalcul de tout l'assemblage a la pose et a la casse d'une dalle.
 */
public class ScreenBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Raccords vus de face : gauche, droite, haut, bas. */
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right"),
          UP = BooleanProperty.create("up"), DOWN = BooleanProperty.create("down");
    public static final int MAX_W = ScreenWall.MAX_W, MAX_H = ScreenWall.MAX_H;

    public ScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false)
              .setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN);
    }

    /** Gauche du spectateur qui regarde l'ecran (l'ecran regarde vers {@code facing}). */
    public static Direction left(Direction facing) {
        return ScreenWall.left(facing);
    }

    public static Direction right(Direction facing) {
        return ScreenWall.right(facing);
    }

    private static boolean same(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof ScreenBlock && s.getValue(FACING) == facing;
    }

    public static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        return connect(state, ScreenWall.partition(level, pos, state.getValue(FACING), pos), pos);
    }

    /** 1.0.4-beta (lot 26) : raccords = voisines du MEME ecran rectangulaire ({@link ScreenWall}). */
    private static BlockState connect(BlockState state, Map<BlockPos, ScreenWall.Rect> walls, BlockPos pos) {
        Direction f = state.getValue(FACING);
        ScreenWall.Rect r = walls.get(pos);
        if (r == null) {
            return state.setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false);
        }
        return state.setValue(LEFT, r.contains(pos.relative(left(f)), f)).setValue(RIGHT, r.contains(pos.relative(right(f)), f))
              .setValue(UP, r.contains(pos.above(), f)).setValue(DOWN, r.contains(pos.below(), f));
    }

    /**
     * 1.0.4-beta (lot 26) : apres une pose ou une casse, raccords de TOUT l'assemblage recalcules (le decoupage en ecrans peut changer loin
     * de la dalle posee) ; sans prevenir les voisins (pas de cascade).
     */
    public static void refresh(Level level, BlockPos pos, Direction f) {
        if (level.isClientSide || !same(level, pos, f)) {
            return;
        }
        Map<BlockPos, ScreenWall.Rect> walls = ScreenWall.partition(level, pos, f, null);
        for (BlockPos p : walls.keySet()) {
            BlockState s = level.getBlockState(p);
            BlockState next = connect(s, walls, p);
            if (next != s) {
                level.setBlock(p, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        return connect(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this) || old.getValue(FACING) != state.getValue(FACING)) {
            refresh(level, pos, state.getValue(FACING));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!next.is(this)) {
            Direction f = state.getValue(FACING);
            for (BlockPos q : new BlockPos[]{pos.relative(left(f)), pos.relative(right(f)), pos.above(), pos.below()}) {
                refresh(level, q, f);
            }
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return connect(state, level, pos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Bloc en bas a gauche de l'ecran auquel appartient {@code pos} (1.0.4-beta lot 26 : son rectangle, {@link ScreenWall}). */
    public static BlockPos master(BlockGetter level, BlockPos pos, Direction facing) {
        ScreenWall.Rect r = ScreenWall.of(level, pos, facing);
        return r == null ? pos : r.master();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ScreenTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || type != PiComputing.SCREEN_TILE.get()) {
            return null;
        }
        return (l, pos, s, tile) -> ((ScreenTile) tile).tickServer();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof DataLinkItem) {
            return InteractionResult.PASS;
        }
        BlockPos master = master(level, pos, state.getValue(FACING));
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(master) instanceof ScreenTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, master);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
