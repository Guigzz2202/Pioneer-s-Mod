package fr.pioneers.api.data;

import java.util.List;
import net.minecraft.core.Direction;

/**
 * Machine dont les faces se reglent pour les donnees (interface de configuration, configurateur de Mekanism).
 */
public interface IDataConfigurable {

    /** Mode de la face (direction absolue dans le monde). */
    DataSideMode getDataMode(Direction side);

    /**
     * Change le mode d'une face (direction absolue). Ignore si la face ou le mode ne sont pas pris en charge.
     *
     * @return vrai si le mode a change.
     */
    boolean setDataMode(Direction side, DataSideMode mode);

    /** Modes proposes par la machine, dans l'ordre du cycle (le premier est {@link DataSideMode#NONE}). */
    List<DataSideMode> getSupportedDataModes();

    /** La face peut-elle recevoir une prise ? (certaines faces portent l'ecran ou la parabole) */
    default boolean isDataSideConfigurable(Direction side) {
        return true;
    }

    /** Passe au mode suivant (ou precedent) de la face. */
    default DataSideMode cycleDataMode(Direction side, boolean reverse) {
        List<DataSideMode> modes = getSupportedDataModes();
        if (modes.isEmpty() || !isDataSideConfigurable(side)) {
            return getDataMode(side);
        }
        int index = modes.indexOf(getDataMode(side));
        DataSideMode next = modes.get(Math.floorMod(index + (reverse ? -1 : 1), modes.size()));
        setDataMode(side, next);
        return getDataMode(side);
    }
}
