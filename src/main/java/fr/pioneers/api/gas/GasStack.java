package fr.pioneers.api.gas;

import fr.pioneers.api.PioneersAPI;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Quantite de gaz (mB, sur un long) : la pile vide {@link #EMPTY} est immuable, les autres se modifient sur place (comme les piles
 * d'objets) ; {@link #copy()} pour en garder une.
 */
public final class GasStack {

    public static final GasStack EMPTY = new GasStack(Gas.EMPTY, 0);
    private static final String NBT_GAS = "gas", NBT_AMOUNT = "amount";

    private final Gas gas;
    private long amount;

    private GasStack(Gas gas, long amount) {
        this.gas = Objects.requireNonNull(gas);
        this.amount = Math.max(0, amount);
    }

    public static GasStack of(Gas gas, long amount) {
        if (gas == null || gas.isEmptyType() || amount <= 0) {
            return EMPTY;
        }
        return new GasStack(gas, amount);
    }

    public boolean isEmpty() {
        return this == EMPTY || gas.isEmptyType() || amount <= 0;
    }

    @NotNull
    public Gas getGas() {
        return isEmpty() ? Gas.EMPTY : gas;
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

    public boolean isSameGas(GasStack other) {
        return getGas().equals(other.getGas()) || isEmpty() && other.isEmpty();
    }

    public GasStack copy() {
        return isEmpty() ? EMPTY : new GasStack(gas, amount);
    }

    public GasStack copyWithAmount(long amount) {
        return of(getGas(), amount);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!isEmpty()) {
            ResourceLocation id = gas.getRegistryName();
            if (id != null) {
                tag.putString(NBT_GAS, id.toString());
                tag.putLong(NBT_AMOUNT, amount);
            }
        }
        return tag;
    }

    public static GasStack load(CompoundTag tag) {
        if (!tag.contains(NBT_GAS)) {
            return EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(NBT_GAS));
        Gas gas = id == null ? null : PioneersAPI.gas(id);
        return gas == null ? EMPTY : of(gas, tag.getLong(NBT_AMOUNT));
    }

    public void write(FriendlyByteBuf buffer) {
        ResourceLocation id = isEmpty() ? null : gas.getRegistryName();
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeResourceLocation(id);
            buffer.writeVarInt(gas.getTint());
            buffer.writeVarLong(amount);
        }
    }

    public static GasStack read(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return EMPTY;
        }
        ResourceLocation id = buffer.readResourceLocation();
        int tint = buffer.readVarInt();
        long amount = buffer.readVarLong();
        Gas gas = PioneersAPI.gas(id);
        return of(gas != null ? gas : Gas.foreign(id, tint, null), amount);
    }

    @Override
    public String toString() {
        return isEmpty() ? "GasStack[vide]" : "GasStack[" + amount + " mB de " + gas + "]";
    }
}
