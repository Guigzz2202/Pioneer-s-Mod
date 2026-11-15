package fr.pioneers.logistics.rail.block;

import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.net.Electrification;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.2 : poteau de catenaire ou troisieme rail, pose a cote de la voie (en cliquant dessus : {@link TrackSideItem}) ; {@code facing}
 * regarde a l'oppose de la voie. Il s'inscrit comme conducteur de l'electrification ({@link Electrification}) a la pose et s'en retire a la
 * casse ; les conducteurs relies a une sous-station alimentent les locomotives electriques qu'ils couvrent.
 */
public class ConductorBlock extends HorizontalDirectionalBlock {

    private static final VoxelShape POLE = Block.box(5, 0, 5, 11, 16, 11);
    public final Electrification.Kind kind;

    public ConductorBlock(Electrification.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Direction du bloc vers la voie. */
    public static Direction toTrack(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return RailShapes.outline(kind == Electrification.Kind.POLE ? "pole" : "third", toTrack(state));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) {
            RailWorld.get(sl).power.add(new Electrification.Conductor(pos.immutable(), kind, 0));
            RailWorld.get(sl).setDirty();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (level instanceof ServerLevel sl && !next.is(this)) {
            RailWorld.get(sl).power.remove(pos);
            RailWorld.get(sl).setDirty();
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return RailShapes.get(kind == Electrification.Kind.POLE ? "pole" : "third", toTrack(state));
    }
}
