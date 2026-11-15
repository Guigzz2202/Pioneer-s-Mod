package fr.pioneers.kinetics.rope;

import fr.pioneers.core.Action;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.registries.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WinchTile extends KineticTile {

    public static final int MAX_DEPTH = 48, LOAD = 16;
    public static final double DRUM_RADIUS = 0.25, GRAVITY = 9.81, BUCKET_MASS = 12, ITEM_MASS = 1.5;

    public enum Mode {
        NONE, WELL, LIFT
    }

    private final FluidTank water = new FluidTank(8_000, stack -> stack.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final LazyOptional<IFluidHandler> waterCap = LazyOptional.of(() -> water);
    /** Profondeur de la charge (blocs sous le treuil) ; montee ou descente ; charge transportee. */
    private double depth;
    private boolean rising;
    private int bottom;
    private Mode mode = Mode.NONE;
    private final List<ItemStack> cargo = new ArrayList<>();
    private boolean carryWater;
    private double sentDepth = -1;

    public WinchTile(BlockPos pos, BlockState state) {
        super(PiKinetics.WINCH_TILE.get(), pos, state);
    }

    /** Charge en cours (bloc casse) : rendue au monde. */
    void dropCargo(Consumer<ItemStack> drop) {
        cargo.forEach(stack -> drop.accept(stack.copy()));
        cargo.clear();
    }

    public FluidTank water() {
        return water;
    }

    public double depth() {
        return depth;
    }

    public Mode mode() {
        return mode;
    }

    public boolean loaded() {
        return carryWater || !cargo.isEmpty();
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == AxisKineticBlock.axis(getBlockState()) ? 1 : 0;
    }

    private double mass() {
        double m = 4;
        if (carryWater) {
            m += BUCKET_MASS;
        }
        for (ItemStack s : cargo) {
            m += s.getCount() * ITEM_MASS;
        }
        return m;
    }

    @Override
    public double loadTorque() {
        return rising ? mass() * GRAVITY * DRUM_RADIUS : 2;
    }

    @Override
    public double maxTorque() {
        return 6_000;
    }

    @Override
    public double maxSpeed() {
        return 120;
    }

    @Override
    protected double renderRadius() {
        return Math.max(1, depth + 1);
    }

    // 0.20.46 (audit) : boite de rendu gardee tant que la profondeur ne change pas (demandee a chaque image)
    @Nullable
    private AABB depthBox;
    private double depthBoxDepth;

    @Override
    public AABB getRenderBoundingBox() {
        double d = depth;
        if (depthBox == null || d != depthBoxDepth) {
            depthBox = new AABB(worldPosition).expandTowards(0, -Math.max(1, d + 1), 0).inflate(0.5);
            depthBoxDepth = d;
        }
        return depthBox;
    }

    /** Fond du puits : premier bloc non vide sous le treuil. */
    private void probe(ServerLevel level) {
        bottom = 0;
        mode = Mode.NONE;
        for (int d = 1; d <= MAX_DEPTH; d++) {
            BlockPos p = worldPosition.below(d);
            BlockState state = level.getBlockState(p);
            if (state.getFluidState().is(FluidTags.WATER)) {
                bottom = d;
                mode = Mode.WELL;
                return;
            }
            if (!state.isAir()) {
                bottom = d - 1;
                BlockEntity be = level.getBlockEntity(p);
                mode = be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent() ? Mode.LIFT : Mode.NONE;
                return;
            }
        }
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (level.getGameTime() % 40 == 0 || mode == Mode.NONE && level.getGameTime() % 10 == 0) {
            probe(level);
        }
        //Vitesse de la corde : omega x r (dans les deux sens de rotation : rochet)
        double v = Math.abs(speed) * 2 * Math.PI / 60 * DRUM_RADIUS / 20;     //blocs par tick
        if (mode == Mode.NONE || bottom <= 0) {
            rising = depth > 0;
            depth = Math.max(0, depth - v);
            sync();
            return;
        }
        if (!rising) {
            depth = Math.min(bottom, depth + v * 1.5);                   //la charge vide descend plus vite
            if (depth >= bottom - 1e-3) {
                load(level);
                rising = true;
            }
        } else {
            depth = Math.max(0, depth - v);
            if (depth <= 1e-3) {
                unload(level);
                rising = false;
            }
        }
        sync();
    }

    private void load(ServerLevel level) {
        if (mode == Mode.WELL) {
            carryWater = true;
            level.playSound(null, worldPosition.below(bottom), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, 1F);
        } else if (mode == Mode.LIFT) {
            BlockEntity be = level.getBlockEntity(worldPosition.below(bottom + 1));
            IItemHandler handler = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            if (handler != null) {
                int left = LOAD;
                for (int i = 0; i < handler.getSlots() && left > 0; i++) {
                    ItemStack got = handler.extractItem(i, left, false);
                    if (!got.isEmpty()) {
                        cargo.add(got);
                        left -= got.getCount();
                    }
                }
            }
        }
        setChanged();
    }

    private void unload(ServerLevel level) {
        if (carryWater) {
            int filled = water.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            carryWater = false;
            if (filled > 0) {
                KineticEvents.award(level, worldPosition, "well");
            }
        }
        if (!cargo.isEmpty()) {
            BlockEntity be = level.getBlockEntity(worldPosition.above());
            IItemHandler handler = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack stack : cargo) {
                ItemStack left = stack;
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots() && !left.isEmpty(); i++) {
                        left = handler.insertItem(i, left, false);
                    }
                }
                if (!left.isEmpty()) {
                    rest.add(left);
                }
            }
            cargo.clear();
            for (ItemStack r : rest) {
                Block.popResource(level, worldPosition.above(), r);
            }
            KineticEvents.award(level, worldPosition, "lift");
        }
        level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 0.9F);
        setChanged();
    }

    private void sync() {
        if (Math.abs(depth - sentDepth) > 0.25 || depth == 0 && sentDepth != 0) {
            sentDepth = depth;
            markClientsDirty();
        }
    }

    Component status() {
        return Component.translatable("kinetics.pioneers.winch." + mode.name().toLowerCase(Locale.ROOT), bottom, water.getFluidAmount())
              .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(status());
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.FLUID_HANDLER ? waterCap.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        waterCap.invalidate();
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putDouble("depth", depth);
        tag.putBoolean("rising", rising);
        tag.putInt("mode", mode.ordinal());
        tag.putBoolean("full", loaded());
    }

    private boolean clientFull;

    @Override
    protected void readClient(CompoundTag tag) {
        depth = tag.getDouble("depth");
        rising = tag.getBoolean("rising");
        mode = Mode.values()[Math.max(0, Math.min(2, tag.getInt("mode")))];
        clientFull = tag.getBoolean("full");
    }

    public boolean clientFull() {
        return clientFull;
    }

    /** Profondeur affichee (le client extrapole entre deux envois). */
    public double displayDepth(float partial) {
        double v = Math.abs(speed) * 2 * Math.PI / 60 * DRUM_RADIUS / 20;
        return Math.max(0, depth + (rising ? -v : v * 1.5) * partial);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeClient(tag);
        tag.put("water", water.writeToNBT(new CompoundTag()));
        tag.putBoolean("carryWater", carryWater);
        ListTag list = new ListTag();
        for (ItemStack s : cargo) {
            list.add(s.save(new CompoundTag()));
        }
        tag.put("cargo", list);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        readClient(tag);
        water.readFromNBT(tag.getCompound("water"));
        carryWater = tag.getBoolean("carryWater");
        cargo.clear();
        for (Tag t : tag.getList("cargo", Tag.TAG_COMPOUND)) {
            cargo.add(ItemStack.of((CompoundTag) t));
        }
    }

    Action unused() {
        return Action.EXECUTE;
    }
}
