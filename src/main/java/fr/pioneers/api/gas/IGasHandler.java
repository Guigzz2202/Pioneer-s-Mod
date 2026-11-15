package fr.pioneers.api.gas;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.Action;
import org.jetbrains.annotations.NotNull;

/**
 * Capacite « gaz » d'un bloc ou d'un objet (obtenue avec {@link PioneersAPI#GAS_HANDLER}) : ses reservoirs,
 * et l'insertion ou l'extraction, reelle ou simulee.
 */
public interface IGasHandler {

    int getGasTanks();

    @NotNull
    GasStack getGasInTank(int tank);

    long getGasTankCapacity(int tank);

    boolean isGasValid(int tank, @NotNull GasStack stack);

    /** Insere du gaz (dans les reservoirs compatibles, dans l'ordre) ; renvoie le reste. */
    @NotNull
    GasStack insertGas(@NotNull GasStack stack, @NotNull Action action);

    /** Extrait jusqu'a {@code amount} mB du premier reservoir non vide. */
    @NotNull
    GasStack extractGas(long amount, @NotNull Action action);

    /** Extrait du gaz de ce type, au plus la quantite de {@code stack}. */
    @NotNull
    GasStack extractGas(@NotNull GasStack stack, @NotNull Action action);
}
