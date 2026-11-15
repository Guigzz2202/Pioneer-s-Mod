package fr.pioneers.core.energy;

import fr.pioneers.core.ContentsListener;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Reserve d'energie du socle Pioneer's, en FE (Forge Energy, l'unite commune de presque tous les mods). Stockee sur un long (les
 * grosses reserves depassent un int), exposee en int comme le veut {@link IEnergyStorage}. Capacite fixe ou variable (ameliorations).
 */
public class EnergyBuffer implements IEnergyStorage, INBTSerializable<CompoundTag> {

    private final LongSupplier capacity;
    private final int maxReceive, maxExtract;
    @Nullable
    private final ContentsListener listener;
    private long stored;

    public EnergyBuffer(LongSupplier capacity, int maxReceive, int maxExtract, @Nullable ContentsListener listener) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.listener = listener;
    }

    /** Machine qui consomme : elle recoit, rien ne s'en extrait par l'exterieur. */
    public static EnergyBuffer input(long capacity, int maxReceive, @Nullable ContentsListener listener) {
        return new EnergyBuffer(() -> capacity, maxReceive, 0, listener);
    }

    public long getStored() {
        return stored;
    }

    public long getCapacity() {
        return Math.max(0, capacity.getAsLong());
    }

    public long getNeeded() {
        return Math.max(0, getCapacity() - stored);
    }

    public void setStored(long energy) {
        long clamped = Math.max(0, Math.min(getCapacity(), energy));
        if (clamped != stored) {
            stored = clamped;
            changed();
        }
    }

    /** Ajout interne (sans limite de debit) ; renvoie ce qui est entre. */
    public long insert(long amount, boolean simulate) {
        long moved = Math.max(0, Math.min(amount, getNeeded()));
        if (!simulate && moved > 0) {
            stored += moved;
            changed();
        }
        return moved;
    }

    /** Consommation interne (sans limite de debit) ; renvoie ce qui est sorti. */
    public long extract(long amount, boolean simulate) {
        long moved = Math.max(0, Math.min(amount, stored));
        if (!simulate && moved > 0) {
            stored -= moved;
            changed();
        }
        return moved;
    }

    public double ratio() {
        long cap = getCapacity();
        return cap <= 0 ? 0 : stored / (double) cap;
    }

    // IEnergyStorage (vu de l'exterieur)

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        return (int) insert(Math.min(amount, maxReceive), simulate);
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        return (int) extract(Math.min(amount, maxExtract), simulate);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, stored);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, getCapacity());
    }

    @Override
    public boolean canExtract() {
        return maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return maxReceive > 0;
    }

    private void changed() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("energy", stored);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        stored = Math.max(0, tag.getLong("energy"));
    }
}
