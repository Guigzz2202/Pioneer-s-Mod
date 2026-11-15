package fr.pioneers.tech.bronze;

import fr.pioneers.core.block.ClientTickers;
import fr.pioneers.core.recipe.RecipeLock;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc oriente de l'age du bronze porteur d'un {@link BronzeTile} : pose face au joueur, clic droit = interface (sauf si {@link #use} est
 * surcharge), contenu lache a la casse, tick serveur et client.
 */
public class BronzeBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory;
    private final VoxelShape shape;

    public BronzeBlock(Properties properties, BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory, @Nullable VoxelShape shape) {
        super(properties);
        this.factory = factory;
        this.shape = shape == null ? Shapes.block() : shape;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return shape;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return factory.apply(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BronzeTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BronzeTile tile) {
            tile.dropContents(stack -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            onBroken(level, pos, tile);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Juste avant la disparition du bloc (structure a dissoudre...). */
    protected void onBroken(Level level, BlockPos pos, BronzeTile tile) {
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            //1.0.5-beta (lot 29) : pas de ticker client si la tile ne redefinit pas clientTick
            if (!ClientTickers.needed(type, state, BronzeTile.class, "clientTick")) {
                return null;
            }
            return (l, p, s, tile) -> {
                if (tile instanceof BronzeTile bronze) {
                    bronze.clientTick();
                }
            };
        }
        return (l, p, s, tile) -> {
            if (tile instanceof BronzeTile bronze && l instanceof ServerLevel server) {
                bronze.serverTick(server);
            }
        };
    }

    /** MAJ Recettes (lot 7) : la machine cassee garde sa recette verrouillee (dans l'objet, {@code BlockEntityTag}, relu a la pose). */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        return RecipeLock.stampDrops(super.getDrops(state, builder), state, builder);
    }
}
