package fr.pioneers.core.data;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.ContentsListener;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reservoir creatif, comme ceux de Mekanism : une fois un type de donnees recu, il en fournit sans fin et engloutit tout ce qu'on lui
 * envoie de ce type. Vide, il adopte le premier type qu'on lui donne.
 */
public class CreativeDataTank extends BasicDataTank {

    /** Quantite affichee (« infinie ») : assez grande, sans risque de depassement dans les sommes. */
    public static final long INFINITE = Long.MAX_VALUE / 8;

    public CreativeDataTank(@Nullable ContentsListener listener) {
        super(INFINITE, type -> true, (type, automation) -> true, (type, automation) -> true, listener);
    }

    @Override
    public void setStack(@NotNull DataStack stack) {
        super.setStack(stack.isEmpty() ? DataStack.EMPTY : stack.copyWithAmount(INFINITE));
    }

    @NotNull
    @Override
    public DataStack insert(@NotNull DataStack stack, @NotNull Action action, @NotNull Automation automationType) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (isEmpty()) {
            if (action.execute()) {
                setStack(stack);
            }
            return DataStack.EMPTY;
        }
        return getStack().isTypeEqual(stack) ? DataStack.EMPTY : stack;
    }

    @NotNull
    @Override
    public DataStack extract(long amount, @NotNull Action action, @NotNull Automation automationType) {
        return isEmpty() || amount <= 0 ? DataStack.EMPTY : getStack().copyWithAmount(amount);
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag);
        if (!isEmpty()) {
            setStack(getStack());
        }
    }
}
