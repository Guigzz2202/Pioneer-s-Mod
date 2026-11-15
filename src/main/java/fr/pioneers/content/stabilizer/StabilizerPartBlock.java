package fr.pioneers.content.stabilizer;

import fr.pioneers.registries.PiStabilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc de la paroi du Stabilisateur spatial (boitier, verre, port, matrice de focalisation, port d'alimentation). {@code active} : port en
 * sortie ; {@code regime} : teinte (voir {@link StabilizerLook}). Clic droit : interface du noyau si la structure est formee, sinon la raison de l'echec.
 */
public class StabilizerPartBlock extends Block implements EntityBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    public final StabilizerPart part;

    public StabilizerPartBlock(StabilizerPart part, Properties properties) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(StabilizerLook.PROPERTY, StabilizerLook.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, StabilizerLook.PROPERTY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new StabilizerPartTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || part != StabilizerPart.PORT || type != PiStabilizer.PART_TILE.get()) {
            return null;
        }
        return (l, pos, s, tile) -> ((StabilizerPartTile) tile).serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            StabilizerStructure.queue(server, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StabilizerPartTile tile
            && tile.getCore() != null) {
            StabilizerStructure.queue(server, tile.getCore());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level instanceof ServerLevel server) {
            StabilizerStructure.queue(server, pos);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        boolean core = player.getItemInHand(hand).getItem() instanceof BlockItem item && item.getBlock() instanceof StabilizerCoreBlock;
        //Un bloc en main (autre qu'un noyau) se pose normalement : construire le multibloc ne demande jamais Maj + clic
        if (!core && (player.getItemInHand(hand).getItem() instanceof BlockItem
                      || player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof StabilizerPartTile tile) {
            StabilizerStructure.interact(server, tile, serverPlayer, hand);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Verre : laisse passer la lumiere. */
    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return part == StabilizerPart.GLASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return part == StabilizerPart.GLASS ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState neighbor, @NotNull Direction side) {
        return part == StabilizerPart.GLASS && neighbor.is(this) || super.skipRendering(state, neighbor, side);
    }
}
