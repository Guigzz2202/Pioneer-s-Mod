package fr.pioneers.hunt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.4) : les TROPHEES, blocs decoratifs : tete de cerf (male avec ses bois) et requin, MURAUX sur un ecusson
 * de bois ; tapis d'ours AU SOL. L'ecusson est un modele de bloc ; la bete est dessinee par {@code TrophyRenderer} avec le VRAI modele de
 * l'espece et sa robe (parties choisies et posees). {@link #FACING} : mural = cote vers lequel il regarde (dos au mur) ; tapis = cote de la
 * tete.
 */
public class TrophyBlock extends BaseEntityBlock {

    public enum Kind {
        DEER(true), BEAR(false), SHARK(true);

        public final boolean wall;

        Kind(boolean wall) {
            this.wall = wall;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT) + "_trophy";
        }
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final Kind kind;
    static final VoxelShape RUG = Block.box(0, 0, 0, 16, 2, 16);
    final VoxelShape[] wall = new VoxelShape[4];

    public TrophyBlock(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            wall[d.get2DDataValue()] = kind == Kind.DEER ? GunRackBlock.wall(d, 3, 1, 13, 15, 9) : GunRackBlock.wall(d, 0, 4, 16, 12, 6);
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (!kind.wall) {
            BlockState s = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
            return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
        }
        Direction clicked = ctx.getClickedFace();
        if (!clicked.getAxis().isHorizontal()) {
            return null;
        }
        BlockState s = defaultBlockState().setValue(FACING, clicked);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (!kind.wall) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, f);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return kind.wall ? wall[state.getValue(FACING).get2DDataValue()] : RUG;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TrophyTile(pos, state);
    }
}
