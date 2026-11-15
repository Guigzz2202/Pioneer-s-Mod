package fr.pioneers.naval;

import fr.pioneers.registries.PiNaval;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.14 : equipement de quai (grue, bras de chargement, borne, mat, passerelle, poste de soutage) : un bloc plein du quai (dalle de beton)
 * surmonte de sa machine, dessinee et animee par {@code HarborEquipmentRenderer}. {@link #FACING} = cote de l'eau (pose : regard du joueur,
 * puis corrige par la capitainerie). Clic droit : a quel port il appartient.
 */
public class HarborEquipmentBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public final HarborEquipment kind;

    public HarborEquipmentBlock(HarborEquipment kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Le dessous est la dalle (modele du bloc) ; la machine est dessinee par le rendu du bloc. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new HarborEquipmentTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (type != PiNaval.EQUIPMENT_TILE.get()) {
            return null;
        }
        return level.isClientSide ? (l, p, s, t) -> ((HarborEquipmentTile) t).clientTick()
              : (l, p, s, t) -> ((HarborEquipmentTile) t).serverTick();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof HarborEquipmentTile tile) {
            BlockPos master = tile.master();
            Harbor h = master == null ? null : NavalWorld.get(sl).harbor(master.asLong());
            player.displayClientMessage(h == null ? Component.translatable("naval.pioneers.equipment.alone")
                  : Component.translatable("naval.pioneers.equipment.linked", h.name), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 0.20.25 : les dalles en diagonale et dessous se reconnectent. */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(state.getBlock())) {
            QuayBlock.refreshAround(level, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!level.isClientSide && !state.is(next.getBlock())) {
            QuayBlock.refreshAround(level, pos);
        }
    }
}
