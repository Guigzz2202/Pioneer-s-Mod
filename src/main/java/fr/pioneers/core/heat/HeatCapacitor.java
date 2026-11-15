package fr.pioneers.core.heat;

import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.core.ContentsListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/** Corps thermique simple : capacite, conductance, temperature (ambiante au depart). */
public class HeatCapacitor implements IHeatHandler, INBTSerializable<CompoundTag> {

    private final double capacity, conductance;
    private double temperature = HeatExchange.AMBIENT;
    @Nullable
    private final ContentsListener listener;

    public HeatCapacitor(double capacity, double conductance, @Nullable ContentsListener listener) {
        this.capacity = Math.max(1, capacity);
        this.conductance = Math.max(0, conductance);
        this.listener = listener;
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    @Override
    public double getHeatCapacity() {
        return capacity;
    }

    @Override
    public double getConductance() {
        return conductance;
    }

    @Override
    public void addHeat(double joules) {
        if (joules != 0) {
            //Jamais sous le zero absolu
            temperature = Math.max(0, temperature + joules / capacity);
            if (listener != null) {
                listener.onContentsChanged();
            }
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("temperature", temperature);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        temperature = tag.contains("temperature") ? Math.max(0, tag.getDouble("temperature")) : HeatExchange.AMBIENT;
    }
}
