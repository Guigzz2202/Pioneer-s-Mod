package fr.pioneers.farm.device;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Epouvantail (deux blocs) : tient les corbeaux a {@link #SCARE_RADIUS} blocs (ils n'y visent aucune culture, et fuient s'ils s'en approchent)
 * et empeche de pietiner la terre labouree a {@link #TRAMPLE_RADIUS} blocs. Tete : sac de toile (0), citrouille sculptee (1), citrouille-lanterne
 * (2, eclaire) ; clic droit avec l'une ou l'autre pour la coiffer, accroupi main vide pour la reprendre. Le bas porte la tuile (registre
 * {@link DeviceSites}, rendu qui se balance au vent) ; le haut n'a qu'une forme.
 */
public class ScarecrowBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final IntegerProperty HEAD = IntegerProperty.create("head", 0, 2);
    public static final int SCARE_RADIUS = 12, TRAMPLE_RADIUS = 8;
    private static final VoxelShape LOWER = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    private static final VoxelShape UPPER_NS = Block.box(1, 0, 6, 15, 15, 10), UPPER_EW = Block.box(6, 0, 1, 10, 15, 15);

    public ScarecrowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(HEAD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, HEAD);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (dir == Direction.UP)) {
            //L'autre moitie a disparu : celle-ci aussi ; sinon on recopie la tete
            return other.is(this) && other.getValue(HALF) != half ? state.setValue(HEAD, other.getValue(HEAD)) : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        //Haut casse en creatif : le bas part sans rien lacher
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(level.getBlockState(below)));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return LOWER;
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? UPPER_NS : UPPER_EW;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new ScarecrowTile(pos, state) : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        int head = state.getValue(HEAD);
        int want = held.is(Items.CARVED_PUMPKIN) ? 1 : held.is(Items.JACK_O_LANTERN) ? 2 : -1;
        if (want > 0 && want != head) {
            if (!level.isClientSide) {
                if (head > 0) {
                    Block.popResource(level, pos, new ItemStack(head == 1 ? Items.CARVED_PUMPKIN : Items.JACK_O_LANTERN));
                }
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                setHead(level, pos, state, want);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 0.8F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown() && head > 0) {
            if (!level.isClientSide) {
                ItemStack back = new ItemStack(head == 1 ? Items.CARVED_PUMPKIN : Items.JACK_O_LANTERN);
                if (!player.getInventory().add(back)) {
                    player.drop(back, false);
                }
                setHead(level, pos, state, 0);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private void setHead(Level level, BlockPos pos, BlockState state, int head) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState l = level.getBlockState(lower), u = level.getBlockState(lower.above());
        if (l.is(this)) {
            level.setBlock(lower, l.setValue(HEAD, head), 3);
        }
        if (u.is(this)) {
            level.setBlock(lower.above(), u.setValue(HEAD, head), 3);
        }
    }
}
