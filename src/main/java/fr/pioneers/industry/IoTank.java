package fr.pioneers.industry;

import java.util.function.Predicate;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

/**
 * Reservoir de fluide d'une machine industrielle : un reservoir d'entree se remplit de l'exterieur mais ne se vide que par la machine ; un
 * reservoir de sortie se vide de l'exterieur mais ne se remplit que par la machine ({@link #fillInternal}).
 */
public class IoTank extends FluidTank {

    private final boolean output;
    private final Runnable listener;

    public IoTank(int capacity, boolean output, Predicate<FluidStack> filter, Runnable listener) {
        super(capacity, filter);
        this.output = output;
        this.listener = listener;
    }

    public boolean isOutput() {
        return output;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        //0.20.46 (audit) : reservoir plein ou d'un autre fluide refuse avant le filtre (parcours des recettes a chaque essai des tuyaux)
        if (output || !fluid.isEmpty() && (fluid.getAmount() == capacity || !fluid.isFluidEqual(resource))) {
            return 0;
        }
        return super.fill(resource, action);
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return output ? super.drain(resource, action) : FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return output ? super.drain(maxDrain, action) : FluidStack.EMPTY;
    }

    /** Remplissage par la machine (sortie comprise). */
    public int fillInternal(FluidStack resource, FluidAction action) {
        return super.fill(resource, action);
    }

    /** Vidage par la machine (entree comprise). */
    @NotNull
    public FluidStack drainInternal(int maxDrain, FluidAction action) {
        return super.drain(maxDrain, action);
    }

    @Override
    protected void onContentsChanged() {
        listener.run();
    }
}
