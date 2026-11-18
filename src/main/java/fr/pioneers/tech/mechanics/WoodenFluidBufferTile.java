package fr.pioneers.tech.mechanics;

import fr.pioneers.core.block.PiBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 2) : tuile du buffer de fluide en bois ({@link WoodenFluidBufferBlock}) : un reservoir de {@link #CAPACITY} mB (liquides
 * froids et chauds jusqu'a 400 K : le bois ne tient pas le metal en fusion), offert par toutes les faces. NBT {@link #KEY} (aussi dans
 * l'objet) ; client : le meme liquide (niveau de la jauge), envoye a chaque changement de 1/32.
 */
public class WoodenFluidBufferTile extends PiBlockEntity {

    /** Petite contenance (un buffer de fluide MKI en tient 64 000). */
    public static final int CAPACITY = 8_000;
    /** Le bois brule ou se fend au-dessus (degres K, comme le seau en argile). */
    public static final int MAX_KELVIN = 400;
    public static final String KEY = "fluid";

    private final FluidTank tank = new FluidTank(CAPACITY, s -> s.getFluid().getFluidType().getTemperature(s) <= MAX_KELVIN) {
        @Override
        protected void onContentsChanged() {
            changed();
        }
    };
    private final LazyOptional<IFluidHandler> handler = LazyOptional.of(() -> tank);
    private int sent = -1;

    public WoodenFluidBufferTile(BlockPos pos, BlockState state) {
        super(PiMechanics.WOODEN_FLUID_BUFFER_TILE.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    /** Remplissage 0..1 (rendu de la jauge). */
    public float fill() {
        return tank.getFluidAmount() / (float) CAPACITY;
    }

    private void changed() {
        setChanged();
        int step = tank.isEmpty() ? -1 : tank.getFluidAmount() * 32 / CAPACITY;
        if (level != null && !level.isClientSide && step != sent) {
            sent = step;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(KEY, tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        tank.setFluid(FluidStack.EMPTY);
        tank.readFromNBT(tag.getCompound(KEY));
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put(KEY, tank.writeToNBT(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        tank.setFluid(FluidStack.EMPTY);
        tank.readFromNBT(tag.getCompound(KEY));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }
}
