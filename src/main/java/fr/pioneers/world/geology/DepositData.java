package fr.pioneers.world.geology;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/** Ce qui a deja ete extrait de chaque gisement d'une dimension (cle = region). */
public class DepositData extends SavedData {

    private static final String NAME = "pioneers_deposits";
    private final Long2IntOpenHashMap mined = new Long2IntOpenHashMap();

    public static DepositData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DepositData::load, DepositData::new, NAME);
    }

    public int mined(long region) {
        return mined.get(region);
    }

    public void mine(long region, int amount) {
        mined.addTo(region, amount);
        setDirty();
    }

    static DepositData load(CompoundTag tag) {
        DepositData data = new DepositData();
        for (Tag t : tag.getList("mined", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.mined.put(c.getLong("r"), c.getInt("n"));
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        mined.long2IntEntrySet().forEach(e -> {
            CompoundTag c = new CompoundTag();
            c.putLong("r", e.getLongKey());
            c.putInt("n", e.getIntValue());
            list.add(c);
        });
        tag.put("mined", list);
        return tag;
    }
}
