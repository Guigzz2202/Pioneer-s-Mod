package fr.pioneers.building.home.line;

import fr.pioneers.tech.kinetics.rope.RopeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patch 1.0.3-beta, lot 7 (Guillaume : « On peut placer le crochet dans toutes les directions ») : crochet de bois a corde, pose sur n'importe
 * quelle face ({@link #FACING} = sens ou il sort de son support). Avec une corde du systeme mecanique (fibre, goudronnee, courroie de cuir :
 * {@link RopeItem}) : clic sur un premier crochet, puis sur un second a {@link Clothesline#MAX_LENGTH} blocs au plus, sans obstacle : la corde
 * est tendue ({@link HookTile}). Avec un objet qui s'accroche : il va a la place la plus proche du crochet ; main vide : on reprend la piece la
 * plus proche ; Maj + main vide : on detend la corde (rendue avec ce qui pendait). Casser un crochet rend la corde et les pieces.
 */
public class HookBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    public HookBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /** Oeil du crochet (ou passe la corde), en coordonnees du monde : 6 px devant le support, dans l'axe (haut de la tige, modele home/rope_hook). */
    public static Vec3 eye(BlockPos pos, Direction facing) {
        return Vec3.atCenterOf(pos).subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(8 / 16D - 6 / 16D));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState().setValue(FACING, ctx.getClickedFace());
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        Direction f = state.getValue(FACING);
        BlockPos support = pos.relative(f.getOpposite());
        return Block.canSupportCenter(level, support, f);
    }

    @NotNull
    @Override
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level, @NotNull BlockPos pos,
          @NotNull BlockPos otherPos) {
        return dir == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HookTile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RopeItem rope) {
            if (!level.isClientSide) {
                tile.ropeClick(player, held, rope.type);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        HookTile master = tile.master();
        if (master == null) {
            return InteractionResult.PASS;
        }
        double t = master == tile ? 0 : 1;
        if (held.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    master.unlink(player);
                } else {
                    master.take(player, t);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (Clothesline.hangable(level, held)) {
            if (!level.isClientSide) {
                master.hang(player, held, t);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HookTile tile && !level.isClientSide) {
            tile.broken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        Direction f = state.getValue(FACING);
        int i = f.get3DDataValue();
        if (SHAPES[i] == null) {
            //Platine contre le support + tige et oeil ; repere : crochet vers +y, support dessous
            SHAPES[i] = switch (f) {
                case UP -> Shapes.box(5 / 16D, 0, 5 / 16D, 11 / 16D, 8 / 16D, 11 / 16D);
                case DOWN -> Shapes.box(5 / 16D, 8 / 16D, 5 / 16D, 11 / 16D, 1, 11 / 16D);
                case NORTH -> Shapes.box(5 / 16D, 5 / 16D, 8 / 16D, 11 / 16D, 11 / 16D, 1);
                case SOUTH -> Shapes.box(5 / 16D, 5 / 16D, 0, 11 / 16D, 11 / 16D, 8 / 16D);
                case WEST -> Shapes.box(8 / 16D, 5 / 16D, 5 / 16D, 1, 11 / 16D, 11 / 16D);
                case EAST -> Shapes.box(0, 5 / 16D, 5 / 16D, 8 / 16D, 11 / 16D, 11 / 16D);
            };
        }
        return SHAPES[i];
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new HookTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof HookTile tile) {
                tile.serverTick();
            }
        };
    }
}
