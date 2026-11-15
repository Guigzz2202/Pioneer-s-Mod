package fr.pioneers.economy.stall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : Etal (9 annonces) et Comptoir (27 annonces), blocs qui vendent pour leur proprietaire contre des jetons.
 * {@code FACING} = cote client (facade). Plateau a 13 px (etal) ou 14 px (comptoir) : les objets des annonces y sont poses par le rendu.
 *
 * <p>Clic droit : le proprietaire configure (Maj : ouvre le stock), un operateur configure avec Maj, les autres achetent. Protection
 * ({@code stallProtected}) : seuls le proprietaire et les operateurs le cassent ; il resiste a toute explosion et aux creatures. Casse par
 * son proprietaire, le stock revient dans son inventaire (ou a ses pieds) ; casse autrement, le stock tombe sur place. Pose limitee par
 * joueur et par monde ({@link StallRegistry}).
 */
public class StallBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 13, 16), Block.box(0, 13, 12, 16, 16, 16));
    private final boolean counter;

    public StallBlock(boolean counter, Properties properties) {
        super(properties);
        this.counter = counter;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Comptoir (27 annonces, six objets montres) plutot qu'etal (9 annonces, trois objets). */
    public boolean counter() {
        return counter;
    }

    public int listings() {
        return counter ? 27 : 9;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Refusee cote serveur au-dela de la limite du joueur (message) : rien n'est pose, l'objet reste en main. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && !StallService.withinLimit(sp, counter)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        //pose par un robot (faux joueur) : etal sans proprietaire, pris par le premier joueur qui l'ouvre
        if (placer instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && level.getBlockEntity(pos) instanceof StallTile tile) {
            StallService.placed(sp, tile);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new StallTile(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && level.getBlockEntity(main(state, pos)) instanceof StallTile tile) {
            StallService.use(sp, tile);
        }
        return InteractionResult.CONSUME;
    }

    /** 1.0.3-beta (lot 11) : case qui porte la tuile (l'etal de 2 blocs : son bas ; {@link TallStallBlock}). */
    protected BlockPos main(BlockState state, BlockPos pos) {
        return pos;
    }

    // ------------------------------------------------------------------ protection
    protected boolean mayBreak(BlockGetter level, BlockState state, BlockPos pos, Player player) {
        return !(level.getBlockEntity(main(state, pos)) instanceof StallTile tile) || tile.owner() == null || tile.canManage(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return mayBreak(level, state, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!mayBreak(level, state, pos, player)) {
            if (!level.isClientSide && level.getBlockEntity(main(state, pos)) instanceof StallTile tile) {
                player.displayClientMessage(Component.translatable("stall.pioneers.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED), true);
                tile.markChanged(true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Casse par son proprietaire : le stock revient dans son inventaire, ou a ses pieds s'il n'a plus de place. */
    @Override
    public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StallTile tile && tile.isOwner(player)) {
            StallTile.Stock stock = tile.stock();
            for (int i = 0; i < stock.getContainerSize(); i++) {
                ItemStack s = stock.getItem(i);
                if (!s.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(player, s.copy());
                }
            }
            stock.clearContent();
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof StallTile tile) {
                Containers.dropContents(level, pos, tile.stock());
            }
            if (level.getServer() != null) {
                StallRegistry.get(level.getServer()).remove(level, pos);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 3_600_000F;
    }

    /** Aucune explosion ne le detruit (celles du mod comprises). */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return entity instanceof Player p && mayBreak(level, state, pos, p);
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }
}
