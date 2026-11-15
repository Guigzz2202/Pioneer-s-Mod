package fr.pioneers.api.data;

import fr.pioneers.api.PioneersAPI;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Une quantite de donnees d'un type donne (en Mo), equivalent du {@code FluidStack} ou du {@code GasStack}.
 * Les piles sont mutables : copier avant de conserver une pile recue d'un autre contenant.
 */
public final class DataStack {

    public static final DataStack EMPTY = new DataStack(DataType.EMPTY, 0);
    private static final String NBT_TYPE = "dataType", NBT_AMOUNT = "amount";

    private final DataType type;
    private long amount;

    private DataStack(DataType type, long amount) {
        this.type = Objects.requireNonNull(type);
        this.amount = Math.max(0, amount);
    }

    public static DataStack of(DataType type, long amount) {
        if (type.isEmptyType() || amount <= 0) {
            return EMPTY;
        }
        return new DataStack(type, amount);
    }

    public boolean isEmpty() {
        return this == EMPTY || type.isEmptyType() || amount <= 0;
    }

    @NotNull
    public DataType getType() {
        return isEmpty() ? DataType.EMPTY : type;
    }

    public long getAmount() {
        return isEmpty() ? 0 : amount;
    }

    public void setAmount(long amount) {
        if (this == EMPTY) {
            throw new IllegalStateException("La pile vide est immuable");
        }
        this.amount = Math.max(0, amount);
    }

    public void grow(long amount) {
        setAmount(this.amount + amount);
    }

    public void shrink(long amount) {
        setAmount(this.amount - amount);
    }

    public DataStack copy() {
        return isEmpty() ? EMPTY : new DataStack(type, amount);
    }

    public DataStack copyWithAmount(long amount) {
        return isEmpty() ? EMPTY : of(type, amount);
    }

    public boolean isTypeEqual(DataStack other) {
        return getType() == other.getType();
    }

    public boolean isTypeEqual(DataType other) {
        return getType() == other;
    }

    // ------------------------------------------------------------------
    // Serialisation
    // ------------------------------------------------------------------

    public CompoundTag write(CompoundTag tag) {
        if (!isEmpty()) {
            ResourceLocation name = type.getRegistryName();
            if (name != null) {
                tag.putString(NBT_TYPE, name.toString());
                tag.putLong(NBT_AMOUNT, amount);
            }
        }
        return tag;
    }

    public static DataStack read(CompoundTag tag) {
        if (!tag.contains(NBT_TYPE)) {
            return EMPTY;
        }
        ResourceLocation name = ResourceLocation.tryParse(tag.getString(NBT_TYPE));
        DataType type = name == null ? null : PioneersAPI.dataTypeRegistry().getValue(name);
        return type == null ? EMPTY : of(type, tag.getLong(NBT_AMOUNT));
    }

    public void writeToPacket(FriendlyByteBuf buffer) {
        if (isEmpty()) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeRegistryIdUnsafe(PioneersAPI.dataTypeRegistry(), type);
            buffer.writeVarLong(amount);
        }
    }

    public static DataStack readFromPacket(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return EMPTY;
        }
        DataType type = buffer.readRegistryIdUnsafe(PioneersAPI.dataTypeRegistry());
        return of(type, buffer.readVarLong());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataStack other && other.getType() == getType() && other.getAmount() == getAmount();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), getAmount());
    }

    @Override
    public String toString() {
        return isEmpty() ? "DataStack[empty]" : "DataStack[" + amount + " Mo " + type + "]";
    }
}
