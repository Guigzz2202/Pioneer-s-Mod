package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.registries.PiRobotics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc du hub robotique (multibloc du socle). Toute pose, tout retrait ou changement voisin demande une verification de la structure ; un
 * clic droit ouvre le hub forme, ou dit ce qui manque.
 */
public class BlockRoboticHubPart extends Block implements EntityBlock {

    public final HubPart part;

    public BlockRoboticHubPart(HubPart part, Properties properties) {
        super(properties);
        this.part = part;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return switch (part) {
            case CASING -> new TileEntityRoboticCasing(pos, state);
            case PORT -> new TileEntityRoboticPort(pos, state);
            case BAY -> new TileEntityRoboticBay(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || part != HubPart.CASING) {
            //Seul un boitier peut etre maitre (le coin bas)
            return null;
        }
        return type == PiRobotics.CASING_TILE.get() ? (l, pos, s, tile) -> ((TileEntityRoboticCasing) tile).serverTick() : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            HubStructure.queue(server, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TileEntityRoboticCasing tile) {
            HubStructure.removed(server, tile);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level instanceof ServerLevel server) {
            //Le voisin peut etre du verre de la paroi (pose ou retire)
            HubStructure.queue(server, from);
            HubStructure.queue(server, pos);
        }
    }

    /** Evenements de bloc (ouverture des volets d'une porte) : transmis a la piece. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean triggerEvent(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity tile = level.getBlockEntity(pos);
        return tile != null && tile.triggerEvent(id, param);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TileEntityRoboticCasing tile)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            //Maj + objet en main : on laisse poser
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            HubStructure.interact(server, tile, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
