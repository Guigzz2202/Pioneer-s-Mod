package fr.pioneers.core.machine;

import fr.pioneers.core.block.ClientTickers;
import fr.pioneers.core.recipe.RecipeLock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc d'une machine du socle ({@link MachineTile}) : etats {@code facing} (facade vers le joueur a la pose) et {@code active} (elle
 * travaille) ; clic droit = interface standard ; les objets des cases tombent quand on le casse.
 */
public class MachineBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
    private final Supplier<? extends BlockEntityType<?>> type;
    /** Formes par orientation (index = ordinal de la facade - 2 : nord, sud, ouest, est), null = cube plein. */
    @Nullable
    private VoxelShape[] shapes;

    public MachineBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties);
        this.type = type;
        this.factory = factory;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    /** Blocs fantomes de la machine (decalages depuis le bloc principal), vide : un seul bloc. */
    private List<Vec3i> bounds = List.of();

    /** Grande machine : blocs fantomes poses avec elle (place libre exigee), retires avec elle. */
    public MachineBlock bounds(List<Vec3i> offsets) {
        this.bounds = offsets;
        return this;
    }

    /** Blocs fantomes manquants (air) d'une grande machine : remis (appele au premier tick de la machine). */
    void repairBounds(Level level, BlockPos pos) {
        for (Vec3i offset : bounds) {
            BlockPos at = pos.offset(offset);
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, MachineBounds.BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(at) instanceof BoundingTile tile) {
                    tile.setMain(pos);
                }
            }
        }
    }

    /** Toutes les cases d'un pave (dx, dy, dz) autour du bloc principal (hors lui). */
    public static List<Vec3i> box(int x0, int x1, int y0, int y1, int z0, int z1) {
        List<Vec3i> list = new ArrayList<>();
        for (int dy = y0; dy <= y1; dy++) {
            for (int dx = x0; dx <= x1; dx++) {
                for (int dz = z0; dz <= z1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        list.add(new Vec3i(dx, dy, dz));
                    }
                }
            }
        }
        return list;
    }

    /** Forme sur mesure (modele 3D non plein), tournee selon la facade. */
    public MachineBlock shaped(VoxelShape[] shapes) {
        this.shapes = shapes;
        return this;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (shapes == null) {
            return super.getShape(state, level, pos, context);
        }
        int index = state.getValue(FACING).ordinal() - 2;
        return index >= 0 && index < shapes.length && shapes[index] != null ? shapes[index] : super.getShape(state, level, pos, context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Vec3i offset : bounds) {
            BlockPos at = context.getClickedPos().offset(offset);
            if (!context.getLevel().isInWorldBounds(at) || !context.getLevel().getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        for (Vec3i offset : bounds) {
            BlockPos at = pos.offset(offset);
            level.setBlock(at, MachineBounds.BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof BoundingTile tile) {
                tile.setMain(pos);
            }
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return factory.apply(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        if (blockEntityType != type.get()) {
            return null;
        }
        if (level.isClientSide) {
            //1.0.5-beta (lot 29) : pas de ticker client si la tile ne redefinit pas clientTick (la plupart des machines)
            return ClientTickers.needed(blockEntityType, state, MachineTile.class, "clientTick") ? (l, pos, s, tile) -> ((MachineTile) tile).tickClient()
                                                                                              : null;
        }
        return (l, pos, s, tile) -> ((MachineTile) tile).tickServer();
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineTile tile)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            //1.9 (MAJ RP) : machine restauree reservee a ses contributeurs (reglage ruins.restoredOwnerOnly)
            if (!fr.pioneers.ruins.RuinApi.mayUse(serverPlayer, tile)) {
                serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable("ruins.pioneers.restore.reserved"), true);
                return InteractionResult.CONSUME;
            }
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            for (Vec3i offset : bounds) {
                BlockPos at = pos.offset(offset);
                if (level.getBlockEntity(at) instanceof BoundingTile bound && pos.equals(bound.getMain())) {
                    level.removeBlock(at, false);
                }
            }
        }
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MachineTile tile) {
            tile.guiSlots().forEach(slot -> {
                if (!slot.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, slot.getStack().copy());
                }
            });
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineTile tile) {
            tile.updatePower();
        }
    }

    /** Bloc non plein : il laisse passer la lumiere (sans quoi le modele et ses voisins ressortent noirs). */
    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return shapes != null || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return shapes != null ? 0 : super.getLightBlock(state, level, pos);
    }

    /** Pas d'ombrage ambiant d'un bloc plein (le modele 3D deborde sur les blocs voisins). */
    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return shapes != null ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    /** MAJ Recettes (lot 7) : la machine cassee garde sa recette verrouillee (dans l'objet, {@code BlockEntityTag}, relu a la pose). */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        return RecipeLock.stampDrops(super.getDrops(state, builder), state, builder);
    }
}
