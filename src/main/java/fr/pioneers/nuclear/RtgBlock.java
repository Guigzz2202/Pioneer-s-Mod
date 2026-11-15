package fr.pioneers.nuclear;

import fr.pioneers.core.Texts;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.energy.EnergyPush;
import fr.pioneers.industry.IndustryEvents;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.registries.PiNuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generateur thermoelectrique a radio-isotope (0.17.1) : une source de chaleur au plutonium 238 dedans donne 4 096 FE/t sans rien consommer,
 * un peu moins chaque jour de jeu (-1 %). Il pousse son energie par toutes ses faces. Clic droit avec une source : on la pose ; Maj + clic
 * droit main vide : on la reprend (son age reste dans l'objet) ; sinon il affiche sa puissance. Faible source de radiation quand il est
 * charge. {@link #ACTIVE} : charge.
 */
public class RtgBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Puissance d'une source neuve (FE/t) et perte par jour de jeu. */
    //1.12.4 (lot F4, audit de l'energie) : 4 096 FE/t (avant 400 : 2,5 % de la courbe du MKIII pour une machine du MKIII ; elle reste a 25 %,
    //sa niche etant de n'avoir besoin de rien, nulle part : bases sans soleil, stations, sondes)
    public static final double POWER = 4_096, DAILY_LOSS = 0.01;

    public RtgBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiNuclear.RTG_TILE.get(), (l, p, s, tile) -> tile.serverTick((ServerLevel) l));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Tile tile)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (tile.source.isEmpty() && held.is(PiIndustry.item("rtg_heat_source"))) {
            tile.setSource(held.split(1));
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 1.2F);
        } else if (!tile.source.isEmpty() && held.isEmpty() && player.isShiftKeyDown()) {
            player.setItemInHand(hand, tile.takeSource());
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.2F);
        } else {
            player.displayClientMessage(tile.source.isEmpty() ? Component.translatable("nuclear.pioneers.rtg_empty")
                  : Component.translatable("nuclear.pioneers.rtg_power", Texts.format(Math.round(tile.power())),
                        Math.round(tile.power() / POWER * 100)), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!next.is(state.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Tile tile && !tile.source.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.takeSource());
            }
            RadioactiveBlock.unregister(level, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    /** Tuile : la source, son age (ticks), l'energie en attente de sortie. */
    public static final class Tile extends PiBlockEntity {

        private static final String AGE = "pioneers_age";
        ItemStack source = ItemStack.EMPTY;
        private long age, buffer;
        private boolean awarded;
        private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> new IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                return 0;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                int moved = (int) Math.min(amount, buffer);
                if (!simulate) {
                    buffer -= moved;
                }
                return moved;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, buffer);
            }

            @Override
            public int getMaxEnergyStored() {
                return 40_000;
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return false;
            }
        });
        private final LazyOptional<IItemHandler> items = LazyOptional.of(() -> new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return source;
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!source.isEmpty() || !isItemValid(slot, stack)) {
                    return stack;
                }
                ItemStack rest = stack.copy();
                ItemStack one = rest.split(1);
                if (!simulate) {
                    setSource(one);
                }
                return rest;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return stack.is(PiIndustry.item("rtg_heat_source"));
            }
        });

        public Tile(BlockPos pos, BlockState state) {
            super(PiNuclear.RTG_TILE.get(), pos, state);
        }

        /** Puissance actuelle (FE/t). */
        public double power() {
            return source.isEmpty() ? 0 : POWER * Math.pow(1 - DAILY_LOSS, age / 24_000.0);
        }

        void setSource(ItemStack stack) {
            source = stack;
            age = stack.hasTag() ? stack.getTag().getLong(AGE) : 0;
            if (stack.hasTag()) {
                stack.getTag().remove(AGE);
            }
            refresh();
        }

        ItemStack takeSource() {
            ItemStack out = source;
            if (!out.isEmpty() && age > 0) {
                out.getOrCreateTag().putLong(AGE, age);
            }
            source = ItemStack.EMPTY;
            age = 0;
            refresh();
            return out;
        }

        private void refresh() {
            setChanged();
            if (level != null && !level.isClientSide) {
                BlockState state = getBlockState();
                boolean on = !source.isEmpty();
                if (state.getValue(ACTIVE) != on) {
                    level.setBlock(worldPosition, state.setValue(ACTIVE, on), Block.UPDATE_ALL);
                }
                if (on) {
                    RadioactiveBlock.register(level, worldPosition, RadiationData.RTG);
                } else {
                    RadioactiveBlock.unregister(level, worldPosition);
                }
            }
        }

        void serverTick(ServerLevel level) {
            if (source.isEmpty()) {
                return;
            }
            age++;
            buffer = Math.min(40_000, buffer + Math.round(power()));
            if (!awarded) {
                awarded = true;
                IndustryEvents.award(level, worldPosition, "rtg_powered");
            }
            //Pousse l'energie vers les voisins
            EnergyPush.toNeighbours(level, worldPosition, buffer, (d, be) -> true, given -> {
                buffer -= given;
                return given;
            });
            if (age % 200 == 0) {
                setChanged();
            }
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ENERGY) {
                return energy.cast();
            }
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                return items.cast();
            }
            return super.getCapability(cap, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            energy.invalidate();
            items.invalidate();
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            if (!source.isEmpty()) {
                tag.put("source", source.save(new CompoundTag()));
            }
            tag.putLong("age", age);
            tag.putLong("buffer", buffer);
            tag.putBoolean("awarded", awarded);
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            source = tag.contains("source") ? ItemStack.of(tag.getCompound("source")) : ItemStack.EMPTY;
            age = tag.getLong("age");
            buffer = tag.getLong("buffer");
            awarded = tag.getBoolean("awarded");
        }
    }
}
