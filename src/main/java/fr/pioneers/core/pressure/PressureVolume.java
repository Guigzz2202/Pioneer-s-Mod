package fr.pioneers.core.pressure;

import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.ContentsListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/** Volume d'air simple : volume, pression maximale, conductance ; vide (pression ambiante) au depart. */
public class PressureVolume implements IPressureHandler, INBTSerializable<CompoundTag> {

    private final double volume, maxPressure, conductance;
    private double air;
    @Nullable
    private final ContentsListener listener;

    public PressureVolume(double volume, double maxPressure, double conductance, @Nullable ContentsListener listener) {
        this.volume = Math.max(1, volume);
        this.maxPressure = Math.max(0, maxPressure);
        this.conductance = Math.max(0, conductance);
        this.listener = listener;
    }

    @Override
    public double getVolume() {
        return volume;
    }

    @Override
    public double getAir() {
        return air;
    }

    @Override
    public double getMaxPressure() {
        return maxPressure;
    }

    @Override
    public double getConductance() {
        return conductance;
    }

    @Override
    public double insertAir(double liters, boolean simulate) {
        double moved = Math.max(0, Math.min(liters, getRoom()));
        if (!simulate && moved > 0) {
            air += moved;
            changed();
        }
        return moved;
    }

    @Override
    public double extractAir(double liters, boolean simulate) {
        double moved = Math.max(0, Math.min(liters, air));
        if (!simulate && moved > 0) {
            air -= moved;
            if (air < 1e-6) {
                air = 0;
            }
            changed();
        }
        return moved;
    }

    /** Remplissage (0 a 1) : pression rapportee a la pression maximale. */
    public double ratio() {
        return maxPressure <= 0 ? 0 : Math.min(1, getPressure() / maxPressure);
    }

    private void changed() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("air", air);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        air = Math.max(0, tag.getDouble("air"));
    }
}
