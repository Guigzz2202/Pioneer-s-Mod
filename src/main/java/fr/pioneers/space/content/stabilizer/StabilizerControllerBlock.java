package fr.pioneers.space.content.stabilizer;

import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.registries.PiStabilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Controleur du Stabilisateur spatial : piece de paroi qui porte la case du noyau et l'interface ({@link StabilizerControllerTile}). */
public class StabilizerControllerBlock extends StabilizerPartBlock {

    public StabilizerControllerBlock(Properties properties) {
        super(StabilizerPart.CONTROLLER, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new StabilizerControllerTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (type != PiStabilizer.CONTROLLER_TILE.get()) {
            return null;
        }
        if (level.isClientSide) {
            //1.0.5-beta (lot 29) : pas de ticker client si la tile ne redefinit pas clientTick
            return fr.pioneers.core.block.ClientTickers.needed(type, state, MachineTile.class, "clientTick") ? (l, pos, s, tile) -> ((MachineTile) tile).tickClient()
                                                                                                         : null;
        }
        return (l, pos, s, tile) -> ((MachineTile) tile).tickServer();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof BlockItem item && !(item.getBlock() instanceof StabilizerCoreBlock)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StabilizerControllerTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof StabilizerControllerTile tile && !tile.coreSlot.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.coreSlot.getStack().copy());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
