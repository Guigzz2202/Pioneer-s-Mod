package fr.pioneers.api.gas;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/** Reservoir de gaz d'une machine. */
public interface IGasTank extends INBTSerializable<CompoundTag> {

    @NotNull
    GasStack getStack();

    /** Remplace le contenu (sans verification : usage interne et synchronisation). */
    void setStack(@NotNull GasStack stack);

    long getCapacity();

    boolean isValid(@NotNull GasStack stack);

    /** Renvoie le reste non insere. */
    @NotNull
    GasStack insert(@NotNull GasStack stack, @NotNull Action action, @NotNull Automation automation);

    @NotNull
    GasStack extract(long amount, @NotNull Action action, @NotNull Automation automation);

    default boolean isEmpty() {
        return getStack().isEmpty();
    }

    default long getStored() {
        return getStack().getAmount();
    }

    default long getNeeded() {
        return Math.max(0, getCapacity() - getStored());
    }

    default void setEmpty() {
        setStack(GasStack.EMPTY);
    }
}
