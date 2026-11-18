package fr.pioneers.society.economy.bin;

import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.village.VillageData;
import fr.pioneers.society.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.10.1 (Guillaume : « un bloc pour le centre du village qui sert de bac de vente ») : bac de vente du village, voir {@link SaleBinTile}
 * et {@link SaleBinRules}. Se pose sur la place d'un village (cloche a moins de {@link SaleBinRules#CENTER_RADIUS} blocs), un seul par joueur
 * et par village ({@link SaleBinData}) ; ailleurs, il est rendu au joueur avec un message. Le joueur qui le pose en est le proprietaire ;
 * pose par un robot, le premier joueur qui l'ouvre le prend. Clic droit : le proprietaire (ou un operateur) ouvre le double coffre ; les
 * autres apprennent a qui il est. Seuls le proprietaire et les operateurs le cassent ; casse, son contenu tombe.
 * <p>{@code FACING} = facade (marque peinte) ; {@code FILLED} = garni (sacs et caisses dans le bac), peint par la tuile.
 */
public class SaleBinBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0.5, 2, 0.5, 15.5, 13, 15.5));

    public SaleBinBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SaleBinTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof SaleBinTile tile && lvl instanceof ServerLevel server) {
                tile.serverTick(server);
            }
        };
    }

    // ------------------------------------------------------------------ pose
    /** Village de la place ou se trouve {@code pos} (cloche a moins de {@link SaleBinRules#CENTER_RADIUS} blocs), ou null. */
    @Nullable
    public static VillageData.Village square(ServerLevel level, BlockPos pos) {
        VillageData.Village v = Villages.villageFor(level, pos);
        if (v == null) {
            return null;
        }
        double dx = v.center.getX() - pos.getX(), dz = v.center.getZ() - pos.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= SaleBinRules.CENTER_RADIUS ? v : null;
    }

    /** Cle de langue du refus de pose pour ce joueur ici, ou null si le bac peut etre a lui. */
    @Nullable
    static String refusal(ServerLevel level, BlockPos pos, ServerPlayer player) {
        VillageData.Village v = square(level, pos);
        if (v == null) {
            return "economy.pioneers.bin.no_village";
        }
        if (SaleBinData.get(level).find(level, player.getUUID(), v.id, pos) != null) {
            return "economy.pioneers.bin.one_per_village";
        }
        return null;
    }

    /** Rend le bac a ce joueur (place d'un village, un par village) ; faux avec un message sinon (appele aussi par le GameTest). */
    public static boolean take(ServerLevel level, BlockPos pos, ServerPlayer player, SaleBinTile tile) {
        String no = refusal(level, pos, player);
        if (no != null) {
            player.displayClientMessage(Component.translatable(no).withStyle(ChatFormatting.RED), false);
            return false;
        }
        VillageData.Village v = square(level, pos);
        tile.claim(player, v.id);
        SaleBinData.get(level).put(pos, player.getUUID(), v.id);
        player.displayClientMessage(Component.translatable("economy.pioneers.bin.placed").withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer sp) || sp instanceof FakePlayer
            || !(level.getBlockEntity(pos) instanceof SaleBinTile tile)) {
            return;
        }
        if (!take(server, pos, sp, tile)) {
            //hors d'une place de village, ou deja un bac dans ce village : le bac est rendu
            level.removeBlock(pos, false);
            if (!sp.getAbilities().instabuild) {
                ItemHandlerHelper.giveItemToPlayer(sp, new ItemStack(this));
            }
        }
    }

    // ------------------------------------------------------------------ ouverture
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || !(level.getBlockEntity(pos) instanceof SaleBinTile tile)) {
            return InteractionResult.CONSUME;
        }
        if (!EconomyApi.enabled()) {
            sp.displayClientMessage(Component.translatable("economy.pioneers.disabled").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (tile.owner() == null && !take((ServerLevel) level, pos, sp, tile)) {
            return InteractionResult.CONSUME;
        }
        if (!tile.canManage(sp)) {
            sp.displayClientMessage(Component.translatable("economy.pioneers.bin.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (tile.isOwner(sp)) {
            tile.applyPending((ServerLevel) level, null, sp);
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> menu(id, inv, tile), Component.translatable("block.pioneers.sale_bin")));
        return InteractionResult.CONSUME;
    }

    private static AbstractContainerMenu menu(int id, net.minecraft.world.entity.player.Inventory inv, SaleBinTile tile) {
        return ChestMenu.sixRows(id, inv, tile);
    }

    // ------------------------------------------------------------------ protection et casse
    private static boolean mayBreak(BlockGetter level, BlockPos pos, Player player) {
        return !(level.getBlockEntity(pos) instanceof SaleBinTile tile) || tile.canManage(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return mayBreak(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!mayBreak(level, pos, player)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SaleBinTile tile) {
                player.displayClientMessage(Component.translatable("economy.pioneers.bin.owned_by", tile.ownerName()).withStyle(ChatFormatting.RED),
                      true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) {
            if (level.getBlockEntity(pos) instanceof SaleBinTile tile) {
                Containers.dropContents(level, pos, tile);
            }
            SaleBinData.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SaleBinTile tile ? net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(tile) : 0;
    }
}
