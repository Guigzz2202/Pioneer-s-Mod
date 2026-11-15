package fr.pioneers.api.data;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * Reservoir de donnees d'une machine : un seul type a la fois, une capacite en Mo.
 */
public interface IDataTank extends INBTSerializable<CompoundTag> {

    @NotNull
    DataStack getStack();

    /** Remplace le contenu (sans verification, usage interne et synchronisation). */
    void setStack(@NotNull DataStack stack);

    long getCapacity();

    /** Le reservoir accepte-t-il ce type de donnees ? */
    boolean isValid(@NotNull DataStack stack);

    /** @return le reste non insere. */
    @NotNull
    DataStack insert(@NotNull DataStack stack, @NotNull Action action, @NotNull Automation automationType);

    @NotNull
    DataStack extract(long amount, @NotNull Action action, @NotNull Automation automationType);

    default boolean isEmpty() {
        return getStack().isEmpty();
    }

    default long getStored() {
        return getStack().getAmount();
    }

    default long getNeeded() {
        return Math.max(0, getCapacity() - getStored());
    }

    @NotNull
    default DataType getType() {
        return getStack().getType();
    }

    default void setEmpty() {
        setStack(DataStack.EMPTY);
    }
}
