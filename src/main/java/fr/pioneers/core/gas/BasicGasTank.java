package fr.pioneers.core.gas;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasTank;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reservoir de gaz simple : une capacite, un filtre, et qui peut entrer ou sortir selon l'origine du transfert (une sortie de machine
 * n'accepte rien de l'exterieur, une entree ne se vide pas par l'exterieur...).
 */
public class BasicGasTank implements IGasTank {

    public static final Predicate<Automation> ALWAYS = automation -> true;
    public static final Predicate<Automation> INTERNAL_ONLY = automation -> !automation.external();

    private long capacity;
    private final Predicate<GasStack> filter;
    private final Predicate<Automation> canInsert, canExtract;
    @Nullable
    private final ContentsListener listener;
    private GasStack stored = GasStack.EMPTY;

    public BasicGasTank(long capacity, Predicate<GasStack> filter, Predicate<Automation> canInsert, Predicate<Automation> canExtract,
          @Nullable ContentsListener listener) {
        this.capacity = capacity;
        this.filter = filter;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
        this.listener = listener;
    }

    /** Nouvelle contenance (reservoirs des multiblocs : selon la structure formee). Le contenu n'est pas tronque. */
    public void setCapacity(long capacity) {
        this.capacity = Math.max(0, capacity);
    }

    public static BasicGasTank of(long capacity, @Nullable ContentsListener listener) {
        return new BasicGasTank(capacity, stack -> true, ALWAYS, ALWAYS, listener);
    }

    /** Sortie de machine : rien n'y entre de l'exterieur. */
    public static BasicGasTank output(long capacity, @Nullable ContentsListener listener) {
        return new BasicGasTank(capacity, stack -> true, INTERNAL_ONLY, ALWAYS, listener);
    }

    /** Entree de machine filtree : rien n'en sort par l'exterieur. */
    public static BasicGasTank input(long capacity, Predicate<GasStack> filter, @Nullable ContentsListener listener) {
        return new BasicGasTank(capacity, filter, ALWAYS, INTERNAL_ONLY, listener);
    }

    @NotNull
    @Override
    public GasStack getStack() {
        return stored;
    }

    @Override
    public void setStack(@NotNull GasStack stack) {
        stored = stack.isEmpty() ? GasStack.EMPTY : stack.copy();
        changed();
    }

    @Override
    public long getCapacity() {
        return capacity;
    }

    @Override
    public boolean isValid(@NotNull GasStack stack) {
        return filter.test(stack);
    }

    @NotNull
    @Override
    public GasStack insert(@NotNull GasStack stack, @NotNull Action action, @NotNull Automation automation) {
        if (stack.isEmpty() || !canInsert.test(automation) || !isValid(stack) || !stored.isEmpty() && !stored.isSameGas(stack)) {
            return stack;
        }
        long moved = Math.min(stack.getAmount(), getNeeded());
        if (moved <= 0) {
            return stack;
        }
        if (action.execute()) {
            if (stored.isEmpty()) {
                stored = stack.copyWithAmount(moved);
            } else {
                stored.grow(moved);
            }
            changed();
        }
        return stack.copyWithAmount(stack.getAmount() - moved);
    }

    @NotNull
    @Override
    public GasStack extract(long amount, @NotNull Action action, @NotNull Automation automation) {
        if (stored.isEmpty() || amount <= 0 || !canExtract.test(automation)) {
            return GasStack.EMPTY;
        }
        long moved = Math.min(amount, stored.getAmount());
        GasStack out = stored.copyWithAmount(moved);
        if (action.execute()) {
            stored.shrink(moved);
            if (stored.isEmpty()) {
                stored = GasStack.EMPTY;
            }
            changed();
        }
        return out;
    }

    private void changed() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        return stored.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        stored = GasStack.load(tag);
    }
}
