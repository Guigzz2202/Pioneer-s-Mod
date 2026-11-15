package fr.pioneers.space.content.spacesim;

import fr.pioneers.core.registries.PiSpaceSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc du Simulateur spatial (coque, verre, port, projecteur de faille, bobine supreme). {@code enabled} : port en sortie, ou bobine
 * allumee (le rendu l'englobe d'un torrent d'energie mortel) ; le projecteur est tourne vers le centre ({@code facing}).
 * Clic droit main vide : l'interface (structure formee) ou la raison de l'echec ; un bloc en main se pose normalement.
 */
public class SpaceSimPartBlock extends Block implements EntityBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public final SpaceSimPart part;

    public SpaceSimPartBlock(SpaceSimPart part, Properties properties) {
        super(properties);
        this.part = part;
        BlockState state = stateDefinition.any().setValue(ACTIVE, false);
        if (part == SpaceSimPart.PROJECTOR) {
            state = state.setValue(FACING, Direction.UP);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /** Projecteur de faille : la meme piece, tournee vers le centre du globe. */
    public static class Projector extends SpaceSimPartBlock {

        public Projector(Properties properties) {
            super(SpaceSimPart.PROJECTOR, properties);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(FACING);
        }
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.hasProperty(FACING) ? state.setValue(FACING, context.getNearestLookingDirection().getOpposite()) : state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SpaceSimPartTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide || part != SpaceSimPart.PORT || type != PiSpaceSim.PART_TILE.get()) {
            return null;
        }
        return (l, pos, s, tile) -> ((SpaceSimPartTile) tile).serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            SpaceSimStructure.queue(server, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SpaceSimPartTile tile
            && tile.getController() != null) {
            SpaceSimStructure.queue(server, tile.getController());
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
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SpaceSimPartTile tile) {
            SpaceSimStructure.interact(server, tile.getController(), pos, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return part == SpaceSimPart.GLASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return part == SpaceSimPart.GLASS ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState neighbor, @NotNull Direction side) {
        return part == SpaceSimPart.GLASS && neighbor.is(this) || super.skipRendering(state, neighbor, side);
    }
}
