package fr.pioneers.logistics.warehouse;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : TRAPPE de la caisse d'entrepot (coeur du multibloc, sur la ligne du milieu du dessus). Elle garde le contenu
 * ({@link CrateHatchTile}) ; cassee, elle part AVEC lui (table de butin : copie de {@code storage} dans {@code BlockEntityTag}, relu a la
 * pose) : ni perte ni duplication. Maj + clic droit (main vide) : le plan projete de la caisse.
 */
public class CrateHatchBlock extends CrateBlock {

    public CrateHatchBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CrateHatchTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (l, p, s, tile) -> {
            if (tile instanceof CrateHatchTile hatch && l instanceof ServerLevel server) {
                hatch.serverTick(server);
            }
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrateHatchTile tile) {
            tile.broken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Objet de trappe portant ce contenu (GameTests, commandes). */
    public static ItemStack withContents(CrateStorage storage) {
        ItemStack stack = new ItemStack(PiWarehouse.CRATE_HATCH.get());
        if (!storage.isEmpty()) {
            CompoundTag bet = new CompoundTag();
            bet.put("storage", storage.save());
            BlockItem.setBlockEntityData(stack, PiWarehouse.HATCH_TILE.get(), bet);
        }
        return stack;
    }

    /** Infobulle de l'objet : contenu emporte (nombre d'objets et de types). */
    public static void contentsTooltip(ItemStack stack, List<Component> tooltip) {
        CompoundTag bet = BlockItem.getBlockEntityData(stack);
        if (bet == null || !bet.contains("storage")) {
            return;
        }
        CrateStorage s = new CrateStorage();
        s.load(bet.getCompound("storage"));
        if (!s.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.pioneers.warehouse_crate_hatch.contents", fr.pioneers.core.Texts.format(s.total()), s.types())
                  .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        contentsTooltip(stack, tooltip);
    }

    /** Pas de comparateur « libre » : la trappe lit la caisse formee. */
    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    static boolean isHatch(Block block) {
        return block instanceof CrateHatchBlock;
    }
}
