package fr.pioneers.tech.computing;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.computing.PiComputing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Ordinateur (0.17.1) : machine du socle (FACING + ACTIVE) qui emet de la redstone par ses faces selon le programme. Clic droit avec une
 * disquette : elle entre dans le premier lecteur libre ; avec le configurateur de liaison : liaison ; sinon le terminal s'ouvre.
 */
public class ComputerBlock extends MachineBlock {

    public ComputerBlock(Properties properties) {
        super(properties, () -> PiComputing.COMPUTER_TILE.get(), ComputerTile::new);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction side) {
        return level.getBlockEntity(pos) instanceof ComputerTile tile ? tile.output(side.getOpposite()) : 0;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DataLinkItem) {
            return InteractionResult.PASS;
        }
        if (held.getItem() instanceof DataDiskItem && level.getBlockEntity(pos) instanceof ComputerTile tile) {
            if (!level.isClientSide) {
                if (tile.hasDisk(0) && tile.hasDisk(1)) {
                    player.displayClientMessage(Component.translatable("computing.pioneers.computer.drives_full"), true);
                } else {
                    tile.insertDisk(held);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.4F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ComputerTile tile) {
            tile.drops().forEach(d -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, d));
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
