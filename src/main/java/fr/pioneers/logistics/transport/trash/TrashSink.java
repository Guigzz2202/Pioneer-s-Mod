package fr.pioneers.logistics.transport.trash;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 22, retour 1 de Guillaume : « une poubelle pour tous types de ressources, sauf la data : un seul bloc commun a tout, qui
 * accepte toutes les ressources ») : les PUITS de la poubelle, un par ressource, SANS ETAT (rien n'est garde, aucun tick) : tout ce qu'on
 * leur donne disparait, rien n'en sort. Partages par toutes les poubelles (une seule instance par ressource).
 * <ul>
 *     <li>objets : une case vide qui accepte tout (simulation comprise : un entonnoir, un buffer, le cable logistique, la goulotte d'une
 *     interface de sortie y versent sans fin) ;</li>
 *     <li>fluides, gaz (vapeur comprise), energie : tout est accepte, rien n'est rendu ;</li>
 * </ul>
 * Jamais de donnees : aucune capacite {@code DATA_HANDLER} (le cable ne s'y raccorde pas en donnees). 1.0.5-beta (lot 27, decision de
 * Guillaume) : plus de puits de CHALEUR ni d'AIR COMPRIME (la chaleur n'est pas un dechet, l'air n'est pas une ressource).
 */
public final class TrashSink {

    /** Contenances annoncees (assez grandes pour qu'aucun emetteur ne se bride, sans debordement d'entier). */
    public static final int FLUID_CAPACITY = Integer.MAX_VALUE, ENERGY_CAPACITY = Integer.MAX_VALUE;
    public static final long GAS_CAPACITY = Long.MAX_VALUE / 4;

    private TrashSink() {
    }

    public static final IItemHandler ITEMS = new IItemHandler() {
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
            return ItemStack.EMPTY;
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
    };

    public static final IFluidHandler FLUIDS = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return FLUID_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource == null || resource.isEmpty() ? 0 : resource.getAmount();
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public static final IGasHandler GASES = new IGasHandler() {
        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            return GasStack.EMPTY;
        }

        @Override
        public long getGasTankCapacity(int tank) {
            return GAS_CAPACITY;
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack stack) {
            return true;
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            return GasStack.EMPTY;
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            return GasStack.EMPTY;
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
            return GasStack.EMPTY;
        }
    };

    public static final IEnergyStorage ENERGY = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return Math.max(0, maxReceive);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };
}
