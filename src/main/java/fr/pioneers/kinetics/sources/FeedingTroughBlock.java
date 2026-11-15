package fr.pioneers.kinetics.sources;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Auge : ble ou foin (une botte = 9 rations) pour les animaux du manege voisin. Clic droit pour remplir ; les entonnoirs la remplissent aussi. */
public class FeedingTroughBlock extends BaseEntityBlock {

    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);
    public static final int CAPACITY = 144;
    private static final VoxelShape SHAPE = Block.box(0, 0, 2, 16, 8, 14);

    public FeedingTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FILL, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Tile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        int value = Tile.value(held);
        if (value > 0) {
            if (!level.isClientSide && tile.food + value <= CAPACITY) {
                tile.add(value);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("kinetics.pioneers.trough.content", tile.food, CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends PiBlockEntity {

        int food;
        private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> new IItemHandler() {
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
                int value = value(stack);
                if (value <= 0 || food + value > CAPACITY) {
                    return stack;
                }
                int n = Math.min(stack.getCount(), (CAPACITY - food) / value);
                if (n <= 0) {
                    return stack;
                }
                if (!simulate) {
                    add(value * n);
                }
                ItemStack rest = stack.copy();
                rest.shrink(n);
                return rest;
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
                return value(stack) > 0;
            }
        });

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.TROUGH_TILE.get(), pos, state);
        }

        static int value(ItemStack stack) {
            return stack.is(Items.HAY_BLOCK) ? 9 : stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.APPLE) ? 1 : 0;
        }

        public boolean hasFood() {
            return food > 0;
        }

        void add(int value) {
            food = Math.min(CAPACITY, food + value);
            refresh();
        }

        /** Une ration mangee. */
        public boolean eat() {
            if (food <= 0) {
                return false;
            }
            food--;
            refresh();
            return true;
        }

        private void refresh() {
            setChanged();
            if (level != null) {
                int fill = food <= 0 ? 0 : food < CAPACITY / 3 ? 1 : food < CAPACITY * 2 / 3 ? 2 : 3;
                BlockState state = getBlockState();
                if (state.hasProperty(FILL) && state.getValue(FILL) != fill) {
                    level.setBlock(worldPosition, state.setValue(FILL, fill), Block.UPDATE_CLIENTS);
                }
            }
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ITEM_HANDLER ? items.cast() : super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            items.invalidate();
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putInt("food", food);
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            food = tag.getInt("food");
        }

        @Nullable
        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
