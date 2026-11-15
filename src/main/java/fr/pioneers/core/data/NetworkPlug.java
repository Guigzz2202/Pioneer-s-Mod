package fr.pioneers.core.data;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.core.Action;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.46 : prise de fibre SANS réservoir, pour les blocs de supervision (terminal, automates, écrans, ordinateurs) : la fibre optique
 * s'y branche visuellement (ils font partie du réseau numérique) mais aucune donnée n'y entre.
 */
public final class NetworkPlug implements IDataHandler {

    public static final NetworkPlug INSTANCE = new NetworkPlug();
    private static final LazyOptional<IDataHandler> OPTIONAL = LazyOptional.of(() -> INSTANCE);

    private NetworkPlug() {
    }

    /** La capacité si {@code cap} est celle des données, sinon null. */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> of(Capability<T> cap) {
        return cap == PioneersAPI.DATA_HANDLER ? (LazyOptional<T>) (LazyOptional<?>) OPTIONAL : null;
    }

    @Override
    public int getDataTanks() {
        return 0;
    }

    @NotNull
    @Override
    public DataStack getDataInTank(int tank) {
        return DataStack.EMPTY;
    }

    @Override
    public long getDataTankCapacity(int tank) {
        return 0;
    }

    @Override
    public boolean isDataValid(int tank, @NotNull DataStack stack) {
        return false;
    }

    @NotNull
    @Override
    public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
        return stack;
    }

    @NotNull
    @Override
    public DataStack extractData(long amount, @NotNull Action action) {
        return DataStack.EMPTY;
    }

    @NotNull
    @Override
    public DataStack extractData(@NotNull DataStack stack, @NotNull Action action) {
        return DataStack.EMPTY;
    }
}
