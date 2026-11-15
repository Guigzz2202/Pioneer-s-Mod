package fr.pioneers.farm.leisure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.5 : cendrier en terre vernissee. Clic droit avec un cigare (allume ou non) : il s'y pose ; un cigare allume y fume et s'y consume deux fois
 * moins vite ({@link AshtrayTile}) ; clic droit avec de quoi allumer : allume le cigare pose ; main vide : le reprendre. {@code cigar} : 0 vide,
 * 1 cigare eteint, 2 cigare allume ; le bout du cigare (volute) est du cote oppose a {@code facing} ({@link #TIP}).
 */
public class AshtrayBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty CIGAR = IntegerProperty.create("cigar", 0, 2);
    /** Distance du bout du cigare au centre du bloc, a l'oppose de {@code facing} (modele : x = 5,5 / 16). */
    public static final double TIP = 2.5 / 16;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 2.5, 12);

    public AshtrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(CIGAR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CIGAR);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AshtrayTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(CIGAR) != 2 ? null
              : createTickerHelper(type, PiLeisure.ASHTRAY_TILE.get(), (l, p, s, tile) -> tile.serverTick());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        int cigar = state.getValue(CIGAR);
        if (!(level.getBlockEntity(pos) instanceof AshtrayTile tile)) {
            return InteractionResult.PASS;
        }
        long now = level.getGameTime();
        if (cigar == 0 && (held.is(PiLeisure.LIT_CIGAR.get()) || held.is(PiLeisure.CIGAR.get()))) {
            if (!level.isClientSide) {
                boolean lit = held.is(PiLeisure.LIT_CIGAR.get());
                tile.setRemaining(lit ? LitCigarItem.remaining(held, now) : 0);
                held.shrink(1);
                level.setBlock(pos, state.setValue(CIGAR, lit ? 2 : 1), 3);
                level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 0.4F, 1.4F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (cigar == 1 && held.is(PiLeisure.LIGHTERS)) {
            if (!level.isClientSide && Smoking.strike(player, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5)) {
                tile.setRemaining(LitCigarItem.BURN);
                level.setBlock(pos, state.setValue(CIGAR, 2), 3);
                player.displayClientMessage(Component.translatable("leisure.pioneers.cigar.lit"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (cigar > 0 && held.isEmpty()) {
            if (!level.isClientSide) {
                ItemStack back = cigar == 2 ? LitCigarItem.make(now + tile.remaining()) : new ItemStack(PiLeisure.CIGAR.get());
                player.setItemInHand(hand, back);
                tile.setRemaining(0);
                level.setBlock(pos, state.setValue(CIGAR, 0), 3);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moved) {
        if (!next.is(this) && state.getValue(CIGAR) > 0 && level.getBlockEntity(pos) instanceof AshtrayTile tile) {
            popResource(level, pos, state.getValue(CIGAR) == 2 ? LitCigarItem.make(level.getGameTime() + tile.remaining())
                  : new ItemStack(PiLeisure.CIGAR.get()));
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /** Volute du cigare allume, depuis son bout. */
    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (state.getValue(CIGAR) != 2) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 - facing.getStepX() * TIP, y = pos.getY() + 2.2 / 16, z = pos.getZ() + 0.5 - facing.getStepZ() * TIP;
        for (int i = 0; i < 2; i++) {
            level.addParticle(PiLeisure.PIPE_SMOKE.get(), x, y, z, (random.nextDouble() - 0.5) * 0.004, 0.012 + random.nextDouble() * 0.006,
                  (random.nextDouble() - 0.5) * 0.004);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.001, 0);
        }
    }
}
