package fr.pioneers.farm.crustacean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : piege a poser dans l'eau. {@link Kind#NET} : filet de peche (entre deux piquets, tendu dans l'eau ; plus il y a d'eau autour, plus il
 * prend de poissons du milieu). {@link Kind#POT} : casier a crabe (pose au fond, appate : prend les crustaces aquatiques du milieu). Clic droit
 * avec un appat : appater le casier ; main vide : recuperer les prises. Entonnoirs et goulottes : prises en sortie, appats en entree.
 * Modeles facade au nord ({@code tools/gc/farm2/art_crustaceans.py}).
 */
public class TrapBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public enum Kind {
        NET, POT
    }

    private static final VoxelShape NET_X = Block.box(7, 0, 0, 9, 16, 16), NET_Z = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape POT_X = Block.box(1, 0, 2, 15, 10, 14), POT_Z = Block.box(2, 0, 1, 14, 10, 15);

    final Kind kind;

    public TrapBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return kind == Kind.NET || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @NotNull
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @NotNull
    @Override
    public BlockState updateShape(BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (dir == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @NotNull
    @Override
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        boolean x = state.getValue(FACING).getAxis() == Direction.Axis.X;
        return kind == Kind.NET ? (x ? NET_X : NET_Z) : (x ? POT_X : POT_Z);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TrapTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiCrustaceans.TRAP_TILE.get(), (l, p, s, t) -> t.serverTick((ServerLevel) l));
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrapTile tile)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (kind == Kind.POT && held.is(PiCrustaceans.BAITS)) {
            ItemStack rest = tile.addBait(held.copy());
            if (!player.getAbilities().instabuild) {
                held.setCount(rest.getCount());
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        //1.0.5-beta (lot 31, retour 42) : panier de peche en main : il APPATE le casier (appat et ver de terre d'abord, puis les autres appats
        //du tag : poissons, crevettes) ; ce qui n'entre pas revient au panier ; casier deja plein d'appat : on releve la prise
        if (kind == Kind.POT && held.getItem() instanceof fr.pioneers.home.bag.BagItem bag && bag.kind == fr.pioneers.home.bag.BagKind.FISH_BAG) {
            ItemStack bait = fr.pioneers.home.bag.BagStore.take(held, bag.kind, s -> s.is(fr.pioneers.farm.fish.PiRodItem.BAIT), 64, false);
            if (bait.isEmpty()) {
                bait = fr.pioneers.home.bag.BagStore.take(held, bag.kind, s -> s.is(PiCrustaceans.BAITS), 64, false);
            }
            if (!bait.isEmpty()) {
                int before = bait.getCount();
                ItemStack rest = tile.addBait(bait);
                if (player.getAbilities().instabuild) {
                    rest = bait.copyWithCount(before);
                }
                fr.pioneers.home.bag.BagStore.giveBack(held, bag.kind, rest);
                if (rest.getCount() < before) {
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
                    return InteractionResult.CONSUME;
                }
            }
        }
        int got = 0;
        for (ItemStack s : tile.takeCatch()) {
            got += s.getCount();
            //1.0.5-beta (lot 31) : panier de peche d'abord (crustaces crus), puis l'inventaire, puis au sol
            fr.pioneers.home.bag.BagEvents.give(player, s);
        }
        if (got > 0) {
            level.playSound(null, pos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.BLOCKS, 0.5F, 1.1F);
        } else {
            player.displayClientMessage(Component.translatable(kind == Kind.NET ? "look.pioneers.net.hint" : "look.pioneers.pot.hint"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TrapTile tile) {
            for (ItemStack s : tile.everything()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
