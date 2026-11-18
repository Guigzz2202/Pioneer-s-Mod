package fr.pioneers.logistics.rail.block;

import fr.pioneers.logistics.rail.RailNetworking;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.net.PortKey;
import fr.pioneers.logistics.rail.net.RailNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : panneau de gare, pose a droite de la voie au bout du quai : la tete des trains s'arrete a la sortie du bloc de voie voisin ; le
 * quai est la voie derriere. Clic droit : nom (plusieurs gares du meme nom se partagent les trains) et limite de trains.
 */
public class StationBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public StationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
        return RailShapes.outline("station", state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new StationTile(pos, state);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof StationTile tile) {
            tile.rename(sl, stack.getHoverName().getString());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) {
            register(sl, pos, state, null);
        }
    }

    /** Inscrit la gare au reseau (nom par defaut : coordonnees). */
    static RailNetwork.Station register(ServerLevel level, BlockPos pos, BlockState state, @Nullable String name) {
        RailWorld w = RailWorld.get(level);
        RailNetwork.Station old = w.net.station(pos);
        Direction heading = state.getValue(FACING);
        BlockPos track = TrackSide.track(pos, heading);
        String n = name != null ? name : old != null ? old.name : "Station " + pos.getX() + " " + pos.getZ();
        RailNetwork.Station st = new RailNetwork.Station(pos, heading, PortKey.of(track.getX(), track.getY(), track.getZ(), heading, 0), n);
        if (old != null) {
            st.limit = old.limit;
            st.logistics = old.logistics;
        }
        w.net.addStation(st);
        w.setDirty();
        return st;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (level instanceof ServerLevel sl && !next.is(this)) {
            RailWorld.get(sl).net.removeStation(pos);
            RailWorld.get(sl).setDirty();
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            RailNetworking.openStation(sp, sl, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return RailShapes.get("station", state.getValue(FACING));
    }
}
