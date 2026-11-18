package fr.pioneers.space.content.absorber;

import fr.pioneers.space.content.PiCosmicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
 * Bloc de l'absorbeur d'energie solaire (boitier, port, capteur). {@code enabled} : port en sortie du caloporteur chaud, ou capteur
 * illumine (absorbeur en marche). Clic droit main vide : interface du controleur (structure formee) ou la raison de l'echec ; un bloc en
 * main se pose normalement.
 */
public class AbsorberPartBlock extends Block implements EntityBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    public final AbsorberPart part;

    public AbsorberPartBlock(AbsorberPart part, Properties properties) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AbsorberPartTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || part != AbsorberPart.PORT || type != PiCosmicBlocks.ABSORBER_PART_TILE.get()) {
            return null;
        }
        return (l, pos, s, tile) -> ((AbsorberPartTile) tile).serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            AbsorberStructure.queue(server, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof AbsorberPartTile tile
            && tile.getController() != null) {
            AbsorberStructure.queue(server, tile.getController());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        //Un bloc en main se pose normalement : construire le multibloc ne demande jamais Maj + clic
        if (player.getItemInHand(hand).getItem() instanceof BlockItem || player.isShiftKeyDown() && !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AbsorberPartTile tile) {
            AbsorberStructure.interact(server, tile.getController(), pos, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
