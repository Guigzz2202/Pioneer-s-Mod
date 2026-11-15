package fr.pioneers.core.gas;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.gas.IGasTank;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Capacite « gaz » faite d'une liste de reservoirs, vue de l'exterieur (ou de l'interieur selon {@code automation}). */
public class GasTankHandler implements IGasHandler {

    private final List<? extends IGasTank> tanks;
    private final Automation automation;

    public GasTankHandler(List<? extends IGasTank> tanks, Automation automation) {
        this.tanks = tanks;
        this.automation = automation;
    }

    @Override
    public int getGasTanks() {
        return tanks.size();
    }

    @NotNull
    @Override
    public GasStack getGasInTank(int tank) {
        return tank < 0 || tank >= tanks.size() ? GasStack.EMPTY : tanks.get(tank).getStack();
    }

    @Override
    public long getGasTankCapacity(int tank) {
        return tank < 0 || tank >= tanks.size() ? 0 : tanks.get(tank).getCapacity();
    }

    @Override
    public boolean isGasValid(int tank, @NotNull GasStack stack) {
        return tank >= 0 && tank < tanks.size() && tanks.get(tank).isValid(stack);
    }

    @NotNull
    @Override
    public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
        GasStack rest = stack;
        //D'abord les reservoirs qui ont deja ce gaz, puis les vides
        for (boolean empty : new boolean[]{false, true}) {
            for (IGasTank tank : tanks) {
                if (rest.isEmpty()) {
                    return GasStack.EMPTY;
                }
                if (tank.isEmpty() == empty) {
                    rest = tank.insert(rest, action, automation);
                }
            }
        }
        return rest;
    }

    @NotNull
    @Override
    public GasStack extractGas(long amount, @NotNull Action action) {
        for (IGasTank tank : tanks) {
            GasStack out = tank.extract(amount, action, automation);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return GasStack.EMPTY;
    }

    @NotNull
    @Override
    public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
        if (stack.isEmpty()) {
            return GasStack.EMPTY;
        }
        for (IGasTank tank : tanks) {
            if (tank.getStack().isSameGas(stack)) {
                GasStack out = tank.extract(stack.getAmount(), action, automation);
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        return GasStack.EMPTY;
    }
}
