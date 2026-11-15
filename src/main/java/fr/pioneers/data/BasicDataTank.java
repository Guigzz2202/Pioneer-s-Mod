package fr.pioneers.data;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.data.IDataTank;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reservoir de donnees standard : une capacite, un filtre de types, et des regles d'insertion / d'extraction
 * selon l'origine de l'operation (interne, manuelle, automatisation).
 */
public class BasicDataTank implements IDataTank {

    private static final BiPredicate<DataType, Automation> ALWAYS = (type, automation) -> true;
    private static final BiPredicate<DataType, Automation> INTERNAL_ONLY = (type, automation) -> automation != Automation.EXTERNAL;

    private final long capacity;
    private final Predicate<DataType> validator;
    private final BiPredicate<DataType, Automation> canInsert;
    private final BiPredicate<DataType, Automation> canExtract;
    @Nullable
    private final ContentsListener listener;
    private DataStack stored = DataStack.EMPTY;

    /**
     * 0.20.34 (deplace de ControllerTile au lot 44 de la 1.0.5) : entree des donnees dans une liste de reservoirs (port de donnees d'un
     * multibloc) : un reservoir par type (celui qui en contient deja, sinon un vide qui l'accepte). Rend le reste.
     */
    public static DataStack insertInto(java.util.List<BasicDataTank> tanks, DataStack stack, Action action) {
        if (stack.isEmpty() || tanks.isEmpty()) {
            return stack;
        }
        BasicDataTank target = null;
        for (BasicDataTank t : tanks) {
            if (t.getStack().isTypeEqual(stack)) {
                target = t;
                break;
            }
        }
        if (target == null) {
            for (BasicDataTank t : tanks) {
                if (t.isEmpty() && t.isValid(stack)) {
                    target = t;
                    break;
                }
            }
        }
        return target == null ? stack : target.insert(stack, action, Automation.EXTERNAL);
    }

    public BasicDataTank(long capacity, Predicate<DataType> validator, BiPredicate<DataType, Automation> canInsert,
          BiPredicate<DataType, Automation> canExtract, @Nullable ContentsListener listener) {
        this.capacity = capacity;
        this.validator = validator;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
        this.listener = listener;
    }

    /** Tous types, entree et sortie libres. */
    public static BasicDataTank create(long capacity, @Nullable ContentsListener listener) {
        return new BasicDataTank(capacity, type -> true, ALWAYS, ALWAYS, listener);
    }

    /** Un seul type, entree libre, sortie interne uniquement (memoire d'un laboratoire). */
    public static BasicDataTank input(long capacity, DataType only, @Nullable ContentsListener listener) {
        return new BasicDataTank(capacity, type -> type == only, ALWAYS, INTERNAL_ONLY, listener);
    }

    /** Tous types, entree interne uniquement, sortie libre (antenne). */
    public static BasicDataTank output(long capacity, @Nullable ContentsListener listener) {
        return new BasicDataTank(capacity, type -> true, INTERNAL_ONLY, ALWAYS, listener);
    }

    /** Filtre et regles au choix. */
    public static BasicDataTank of(long capacity, Predicate<DataType> validator, BiPredicate<DataType, Automation> canInsert,
          BiPredicate<DataType, Automation> canExtract, @Nullable ContentsListener listener) {
        return new BasicDataTank(capacity, validator, canInsert, canExtract, listener);
    }

    @NotNull
    @Override
    public DataStack getStack() {
        return stored;
    }

    @Override
    public void setStack(@NotNull DataStack stack) {
        stored = stack.isEmpty() ? DataStack.EMPTY : stack.copyWithAmount(Math.min(stack.getAmount(), capacity));
        onContentsChanged();
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public boolean isValid(@NotNull DataStack stack) {
        return !stack.isEmpty() && validator.test(stack.getType());
    }

    @NotNull
    @Override
    public DataStack insert(@NotNull DataStack stack, @NotNull Action action, @NotNull Automation automationType) {
        if (stack.isEmpty() || !isValid(stack) || !canInsert.test(stack.getType(), automationType)) {
            return stack;
        }
        if (!stored.isEmpty() && !stored.isTypeEqual(stack)) {
            return stack;
        }
        long toAdd = Math.min(stack.getAmount(), getNeeded());
        if (toAdd <= 0) {
            return stack;
        }
        if (action.execute()) {
            if (stored.isEmpty()) {
                stored = stack.copyWithAmount(toAdd);
            } else {
                stored.grow(toAdd);
            }
            onContentsChanged();
        }
        return stack.copyWithAmount(stack.getAmount() - toAdd);
    }

    @NotNull
    @Override
    public DataStack extract(long amount, @NotNull Action action, @NotNull Automation automationType) {
        if (stored.isEmpty() || amount <= 0 || !canExtract.test(stored.getType(), automationType)) {
            return DataStack.EMPTY;
        }
        long taken = Math.min(amount, stored.getAmount());
        DataStack result = stored.copyWithAmount(taken);
        if (action.execute()) {
            if (taken >= stored.getAmount()) {
                stored = DataStack.EMPTY;
            } else {
                stored.shrink(taken);
            }
            onContentsChanged();
        }
        return result;
    }

    public boolean canInsert(DataType type, Automation automationType) {
        return validator.test(type) && canInsert.test(type, automationType);
    }

    public boolean canExtract(Automation automationType) {
        return !stored.isEmpty() && canExtract.test(stored.getType(), automationType);
    }

    protected void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        return stored.write(new CompoundTag());
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        DataStack read = DataStack.read(tag);
        stored = read.isEmpty() ? DataStack.EMPTY : read.copyWithAmount(Math.min(read.getAmount(), capacity));
    }
}
