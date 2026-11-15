package fr.pioneers.robotics.works;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plans de travaux d'une dimension, par ordre (l'ordre lui-meme, leger, est synchronise avec les clients ; le plan, lui, n'est envoye que
 * sur demande).
 */
public class WorksData extends SavedData {

    private static final String NAME = "pioneers_works";

    private final Map<Integer, WorksPlan> plans = new HashMap<>();
    /** Version de chaque plan (change a chaque remplacement : les zones repartent de zero). */
    private final Map<Integer, Integer> versions = new HashMap<>();
    private WorksData() {
    }

    public static WorksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(level, tag), WorksData::new, NAME);
    }

    @Nullable
    public WorksPlan plan(int order) {
        return plans.get(order);
    }

    /** 0.20.6 : tous les plans (wagon de pose). */
    public Collection<WorksPlan> all() {
        return plans.values();
    }

    public int version(int order) {
        return versions.getOrDefault(order, 0);
    }

    public void put(int order, WorksPlan plan) {
        plans.put(order, plan);
        versions.merge(order, 1, Integer::sum);
        setDirty();
    }

    public void remove(int order) {
        if (plans.remove(order) != null) {
            versions.remove(order);
            setDirty();
        }
    }

    /** Ne garde que les plans dont l'ordre existe encore. */
    public void retain(IntPredicate keep) {
        if (plans.keySet().removeIf(id -> !keep.test(id))) {
            versions.keySet().removeIf(id -> !plans.containsKey(id));
            setDirty();
        }
    }

    private static WorksData load(ServerLevel level, CompoundTag tag) {
        WorksData data = new WorksData();
        for (Tag entry : tag.getList("plans", Tag.TAG_COMPOUND)) {
            CompoundTag compound = (CompoundTag) entry;
            data.plans.put(compound.getInt("order"), WorksPlan.load(compound.getCompound("plan"), WorksPlan.blocks(level)));
            data.versions.put(compound.getInt("order"), 1);
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, WorksPlan> entry : plans.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putInt("order", entry.getKey());
            compound.put("plan", entry.getValue().save());
            list.add(compound);
        }
        tag.put("plans", list);
        return tag;
    }
}
