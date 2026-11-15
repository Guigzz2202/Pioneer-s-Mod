package fr.pioneers.core.side;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.Action;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Vues d'un gestionnaire limitees par le reglage d'une face : on n'insere que par une entree, on n'extrait que par une sortie (les deux
 * pour IO). La face fermee (NONE) n'expose rien : l'appelant ne donne pas la capacite.
 */
public final class Sided {

    private Sided() {
    }

    public static IItemHandler items(IItemHandler handler, SideMode mode) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return handler.getSlots();
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return handler.getStackInSlot(slot);
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return mode.canInsert() ? handler.insertItem(slot, stack, simulate) : stack;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return mode.canExtract() ? handler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return handler.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return mode.canInsert() && handler.isItemValid(slot, stack);
            }
        };
    }

    public static IFluidHandler fluids(IFluidHandler handler, SideMode mode) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return handler.getTanks();
            }

            @NotNull
            @Override
            public FluidStack getFluidInTank(int tank) {
                return handler.getFluidInTank(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return handler.getTankCapacity(tank);
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                return mode.canInsert() && handler.isFluidValid(tank, stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return mode.canInsert() ? handler.fill(resource, action) : 0;
            }

            @NotNull
            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return mode.canExtract() ? handler.drain(resource, action) : FluidStack.EMPTY;
            }

            @NotNull
            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return mode.canExtract() ? handler.drain(maxDrain, action) : FluidStack.EMPTY;
            }
        };
    }

    public static IGasHandler gases(IGasHandler handler, SideMode mode) {
        return new IGasHandler() {
            @Override
            public int getGasTanks() {
                return handler.getGasTanks();
            }

            @NotNull
            @Override
            public GasStack getGasInTank(int tank) {
                return handler.getGasInTank(tank);
            }

            @Override
            public long getGasTankCapacity(int tank) {
                return handler.getGasTankCapacity(tank);
            }

            @Override
            public boolean isGasValid(int tank, @NotNull GasStack stack) {
                return mode.canInsert() && handler.isGasValid(tank, stack);
            }

            @NotNull
            @Override
            public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
                return mode.canInsert() ? handler.insertGas(stack, action) : stack;
            }

            @NotNull
            @Override
            public GasStack extractGas(long amount, @NotNull Action action) {
                return mode.canExtract() ? handler.extractGas(amount, action) : GasStack.EMPTY;
            }

            @NotNull
            @Override
            public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
                return mode.canExtract() ? handler.extractGas(stack, action) : GasStack.EMPTY;
            }
        };
    }

    public static IEnergyStorage energy(IEnergyStorage handler, SideMode mode) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                return mode.canInsert() ? handler.receiveEnergy(amount, simulate) : 0;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                return mode.canExtract() ? handler.extractEnergy(amount, simulate) : 0;
            }

            @Override
            public int getEnergyStored() {
                return handler.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return handler.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return mode.canExtract() && handler.canExtract();
            }

            @Override
            public boolean canReceive() {
                return mode.canInsert() && handler.canReceive();
            }
        };
    }

    /** Air comprime : par une entree il ne fait qu'entrer, par une sortie il ne fait que sortir (clapets). */
    public static IPressureHandler pressure(IPressureHandler handler, SideMode mode) {
        return new IPressureHandler() {
            @Override
            public double getVolume() {
                return handler.getVolume();
            }

            @Override
            public double getAir() {
                return handler.getAir();
            }

            @Override
            public double getMaxPressure() {
                return handler.getMaxPressure();
            }

            @Override
            public double getConductance() {
                return handler.getConductance();
            }

            @Override
            public double insertAir(double liters, boolean simulate) {
                return mode.canInsert() ? handler.insertAir(liters, simulate) : 0;
            }

            @Override
            public double extractAir(double liters, boolean simulate) {
                return mode.canExtract() ? handler.extractAir(liters, simulate) : 0;
            }
        };
    }
}
