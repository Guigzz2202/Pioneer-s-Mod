package fr.pioneers.logistics.transport.buffer;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Buffer du socle (objets, fluides, gaz, chaleur, energie, pression), en quatre tiers. Ouvert sur toutes ses faces : les cables y deposent (fiche) ou y
 * puisent (extraction). Il garde son contenu quand on le casse (comme une boite de Shulker) ; la comparaison redstone donne son
 * remplissage. Clic droit : interface (objets) ou etat (autres).
 */
public class BufferBlock extends Block implements EntityBlock {

    public final BufferType type;
    public final CableTier tier;
    /** 1.0 : buffer creatif (contenu inepuisable : il se remplit a chaque tick de ce qu'on y a mis ; energie et air toujours pleins). */
    private boolean creative;

    public BufferBlock(BufferType type, CableTier tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public BufferBlock asCreative() {
        creative = true;
        return this;
    }

    public boolean isCreative() {
        return creative;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BufferTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        //Objets : rien a faire ; les autres tiennent leur etat visible a jour (chaleur, charge, jauges)
        if (level.isClientSide || type != PiTransport.BUFFER_TILE.get()) {
            return null;
        }
        return (l, pos, s, tile) -> ((BufferTile) tile).tickServer();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BufferTile tile)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Les cases d'entree / sortie ne suivent pas l'objet : elles tombent au sol quand on casse le bloc. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BufferTile tile) {
            for (ItemSlot slot : tile.getIoSlots()) {
                if (!slot.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, slot.getStack().copy());
                    slot.setStack(ItemStack.EMPTY);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BufferTile tile ? tile.comparator() : 0;
    }

    // API Machine (lot 1) : l'infobulle de l'objet est tiree de la fiche de la machine (api.machine.MachineTooltips, tools/gc/api).
}
