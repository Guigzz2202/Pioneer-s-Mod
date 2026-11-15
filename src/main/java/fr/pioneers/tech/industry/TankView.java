package fr.pioneers.tech.industry;

import java.util.List;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

/** Plusieurs reservoirs vus comme un seul gestionnaire de fluides (ejection, ports des multiblocs). */
public record TankView(List<? extends FluidTank> tanks) implements IFluidHandler {

    @Override
    public int getTanks() {
        return tanks.size();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return tanks.get(tank).getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tanks.get(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return tanks.get(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        //Un meme fluide va d'abord dans le reservoir qui en contient deja
        for (FluidTank tank : tanks) {
            if (!tank.isEmpty() && tank.getFluid().isFluidEqual(resource)) {
                int n = tank.fill(resource, action);
                if (n > 0) {
                    return n;
                }
            }
        }
        for (FluidTank tank : tanks) {
            if (tank.isEmpty()) {
                int n = tank.fill(resource, action);
                if (n > 0) {
                    return n;
                }
            }
        }
        return 0;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (FluidTank tank : tanks) {
            FluidStack out = tank.drain(resource, action);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (FluidTank tank : tanks) {
            FluidStack out = tank.drain(maxDrain, action);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return FluidStack.EMPTY;
    }

    /** Remplissage interne des reservoirs de sortie (restitution de ce qu'un voisin n'a pas pris). */
    public record Refill(List<? extends FluidTank> tanks) implements IFluidHandler {

        @Override
        public int getTanks() {
            return tanks.size();
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks.get(tank).getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks.get(tank).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int done = 0;
            for (int pass = 0; pass < 2 && done < resource.getAmount(); pass++) {
                for (FluidTank tank : tanks) {
                    boolean match = pass == 0 ? !tank.isEmpty() && tank.getFluid().isFluidEqual(resource) : tank.isEmpty();
                    if (match) {
                        FluidStack part = new FluidStack(resource, resource.getAmount() - done);
                        done += tank instanceof IoTank io ? io.fillInternal(part, action) : tank.fill(part, action);
                        if (done >= resource.getAmount()) {
                            break;
                        }
                    }
                }
            }
            return done;
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
    }
}
