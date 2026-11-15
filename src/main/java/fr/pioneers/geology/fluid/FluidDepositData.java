package fr.pioneers.geology.fluid;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** Ce qui a deja ete extrait de chaque gisement fluide d'une dimension (mB, cle = region). */
public class FluidDepositData extends SavedData {

    private static final String NAME = "pioneers_fluid_deposits";
    private final Long2LongOpenHashMap extracted = new Long2LongOpenHashMap();

    public static FluidDepositData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FluidDepositData::load, FluidDepositData::new, NAME);
    }

    public long extracted(long region) {
        return extracted.get(region);
    }

    public void extract(long region, long amount) {
        extracted.addTo(region, amount);
        setDirty();
    }

    static FluidDepositData load(CompoundTag tag) {
        FluidDepositData data = new FluidDepositData();
        for (Tag t : tag.getList("extracted", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.extracted.put(c.getLong("r"), c.getLong("n"));
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        extracted.long2LongEntrySet().forEach(e -> {
            CompoundTag c = new CompoundTag();
            c.putLong("r", e.getLongKey());
            c.putLong("n", e.getLongValue());
            list.add(c);
        });
        tag.put("extracted", list);
        return tag;
    }
}
