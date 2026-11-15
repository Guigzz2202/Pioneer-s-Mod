package fr.pioneers.logistics.rail.block;

import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.net.PortKey;
import fr.pioneers.logistics.rail.net.RailNetwork;
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
 * 0.20.0 : signal (de canton ou d'entree), pose a droite de la voie dans le sens qu'il commande ; {@code facing} = sens des trains
 * commandes, {@code aspect} pose par le reseau (vert, orange, rouge, bleu). Il coupe la voie en cantons a la sortie du bloc de voie voisin.
 */
public class RailSignalBlock extends HorizontalDirectionalBlock {

    public final RailNetwork.SignalType type;

    public RailSignalBlock(RailNetwork.SignalType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TrackBlocks.ASPECT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TrackBlocks.ASPECT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        Direction heading = TrackSide.heading(ctx.getLevel(), ctx.getClickedPos(), ctx.getHorizontalDirection());
        return defaultBlockState().setValue(FACING, heading == null ? ctx.getHorizontalDirection() : heading);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return RailShapes.outline(type == RailNetwork.SignalType.CHAIN ? "light" : "semaphore", state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) {
            Direction heading = state.getValue(FACING);
            BlockPos track = TrackSide.track(pos, heading);
            RailWorld.get(sl).net.addSignal(new RailNetwork.Signal(pos, type, heading,
                  PortKey.of(track.getX(), track.getY(), track.getZ(), heading, 0)));
            RailWorld.get(sl).setDirty();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (level instanceof ServerLevel sl && !next.is(this)) {
            RailWorld.get(sl).net.removeSignal(pos);
            RailWorld.get(sl).setDirty();
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return RailShapes.get(type == RailNetwork.SignalType.CHAIN ? "light" : "semaphore", state.getValue(FACING));
    }
}
