package fr.pioneers.mechanics;

import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.42 : entonnoir en bois, sans inventaire. Il ne garde rien : toutes les {@value Tile#COOLDOWN} ticks il prend un objet dans le
 * conteneur du dessus (ou un objet pose sur lui) et le met directement dans le conteneur vers lequel pointe son bec ; s'il n'y a pas de
 * place, il ne prend rien. Ce qu'on lui donne (entonnoir, tuyau, machine) passe tout droit vers le conteneur du bec. Un signal redstone
 * l'arrete. Bec vers le bas ou sur un cote, comme l'entonnoir de fer.
 */
public class WoodenHopperBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING_HOPPER;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    private static final VoxelShape TOP = Block.box(0, 10, 0, 16, 16, 16);
    private static final VoxelShape FUNNEL = Block.box(4, 4, 4, 12, 10, 12);
    private static final VoxelShape BOWL = Shapes.join(Shapes.or(FUNNEL, TOP), Block.box(2, 11, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);
    private static final VoxelShape DOWN = Shapes.or(BOWL, Block.box(6, 0, 6, 10, 4, 10));
    private static final VoxelShape NORTH = Shapes.or(BOWL, Block.box(6, 4, 0, 10, 8, 4));
    private static final VoxelShape SOUTH = Shapes.or(BOWL, Block.box(6, 4, 12, 10, 8, 16));
    private static final VoxelShape WEST = Shapes.or(BOWL, Block.box(0, 4, 6, 4, 8, 10));
    private static final VoxelShape EAST = Shapes.or(BOWL, Block.box(12, 4, 6, 16, 8, 10));

    public WoodenHopperBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ENABLED, true));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction face = context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, face.getAxis() == Direction.Axis.Y ? Direction.DOWN : face)
              .setValue(ENABLED, !context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        boolean enabled = !level.hasNeighborSignal(pos);
        if (enabled != state.getValue(ENABLED)) {
            level.setBlock(pos, state.setValue(ENABLED, enabled), Block.UPDATE_CLIENTS);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> DOWN;
        };
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return Shapes.or(getShape(state, level, pos, CollisionContext.empty()), Block.box(2, 11, 2, 14, 16, 14));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
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

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull PathComputationType type) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("block.pioneers.wooden_hopper.desc");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiMechanics.WOODEN_HOPPER_TILE.get(), (l, p, s, tile) -> tile.serverTick((ServerLevel) l, s));
    }

    // ------------------------------------------------------------------

    public static class Tile extends PiBlockEntity {

        /** Un objet toutes les 10 ticks (2 par seconde, un peu moins que l'entonnoir de fer). */
        public static final int COOLDOWN = 10;
        /** Garde contre les boucles d'entonnoirs en bois qui se renverraient un objet sans fin. */
        private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
        private static final int MAX_DEPTH = 16;

        private int cooldown;
        private final LazyOptional<IItemHandler> passThrough = LazyOptional.of(() -> new PassThrough(this));

        public Tile(BlockPos pos, BlockState state) {
            super(PiMechanics.WOODEN_HOPPER_TILE.get(), pos, state);
        }

        Direction facing() {
            return getBlockState().getValue(FACING);
        }

        @Nullable
        IItemHandler target() {
            if (level == null) {
                return null;
            }
            Direction out = facing();
            //1.9 (MAJ RP, bloc 4) : le bec ne verse rien a travers une frontiere de claim refusee
            if (!ClanApi.canTransfer(level, worldPosition, worldPosition.relative(out))) {
                return null;
            }
            BlockEntity next = level.getBlockEntity(worldPosition.relative(out));
            return next == null ? null : next.getCapability(ForgeCapabilities.ITEM_HANDLER, out.getOpposite()).orElse(null);
        }

        void serverTick(ServerLevel level, BlockState state) {
            if (--cooldown > 0 || !state.getValue(ENABLED)) {
                return;
            }
            cooldown = COOLDOWN;
            IItemHandler target = target();
            if (target == null) {
                return;
            }
            //1. Conteneur du dessus
            BlockEntity above = level.getBlockEntity(worldPosition.above());
            IItemHandler source = above == null ? null : above.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
            if (source != null && move(source, target)) {
                return;
            }
            //2. Objets poses dans la cuve
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition.getX(), worldPosition.getY() + 0.6,
                  worldPosition.getZ(), worldPosition.getX() + 1, worldPosition.getY() + 1.5, worldPosition.getZ() + 1))) {
                ItemStack stack = entity.getItem();
                if (stack.isEmpty() || !entity.isAlive()) {
                    continue;
                }
                ItemStack one = stack.copyWithCount(1);
                if (insert(target, one, true).isEmpty()) {
                    insert(target, one, false);
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        entity.discard();
                    } else {
                        entity.setItem(stack);
                    }
                    return;
                }
            }
        }

        /** Un objet de la source vers la cible, s'il y a la place ; vrai si un objet est passe. */
        private static boolean move(IItemHandler source, IItemHandler target) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack peek = source.extractItem(slot, 1, true);
                if (peek.isEmpty() || !insert(target, peek, true).isEmpty()) {
                    continue;
                }
                ItemStack taken = source.extractItem(slot, 1, false);
                ItemStack rest = insert(target, taken, false);
                if (!rest.isEmpty()) {
                    //Ne devrait pas arriver (simulation faite) : rendu a la source
                    source.insertItem(slot, rest, false);
                }
                return true;
            }
            return false;
        }

        /** Insere dans le premier emplacement qui accepte ; renvoie le reste. */
        static ItemStack insert(IItemHandler target, ItemStack stack, boolean simulate) {
            ItemStack rest = stack;
            for (int i = 0; i < target.getSlots() && !rest.isEmpty(); i++) {
                rest = target.insertItem(i, rest, simulate);
            }
            return rest;
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER && side != facing()) {
                return passThrough.cast();
            }
            return super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            passThrough.invalidate();
        }

        /** Vue « passe-plat » : aucune case a soi, ce qu'on y met va directement au conteneur du bec. */
        private record PassThrough(Tile tile) implements IItemHandler {

            @Override
            public int getSlots() {
                return 1;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (stack.isEmpty() || !tile.getBlockState().getValue(ENABLED) || DEPTH.get() >= MAX_DEPTH) {
                    return stack;
                }
                IItemHandler target = tile.target();
                if (target == null) {
                    return stack;
                }
                DEPTH.set(DEPTH.get() + 1);
                try {
                    return insert(target, stack, simulate);
                } finally {
                    DEPTH.set(DEPTH.get() - 1);
                }
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return true;
            }
        }
    }
}
