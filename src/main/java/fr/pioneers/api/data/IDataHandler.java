package fr.pioneers.api.data;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.Action;
import org.jetbrains.annotations.NotNull;

/**
 * Capacite exposee par les blocs (et objets) qui contiennent des donnees, face par face.
 * C'est le pendant de {@code IFluidHandler} / {@code IGasHandler} : les cables optiques et les machines ne passent que par elle.
 * <p>
 * Obtenue avec {@link PioneersAPI#DATA_HANDLER} ; une face reglee sur « aucun » n'expose pas la capacite,
 * une face en entree refuse l'extraction et une face en sortie refuse l'insertion.
 */
public interface IDataHandler {

    int getDataTanks();

    @NotNull
    DataStack getDataInTank(int tank);

    long getDataTankCapacity(int tank);

    boolean isDataValid(int tank, @NotNull DataStack stack);

    /**
     * Insere des donnees (dans les reservoirs compatibles, dans l'ordre).
     *
     * @return le reste qui n'a pas pu entrer.
     */
    @NotNull
    DataStack insertData(@NotNull DataStack stack, @NotNull Action action);

    /** Extrait jusqu'a {@code amount} Mo du premier reservoir non vide. */
    @NotNull
    DataStack extractData(long amount, @NotNull Action action);

    /** Extrait des donnees du type de {@code stack}, au plus sa quantite. */
    @NotNull
    DataStack extractData(@NotNull DataStack stack, @NotNull Action action);
}
