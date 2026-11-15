package fr.pioneers.caravan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 10) : caravanes ACTIVES du serveur (SavedData {@code pioneers_caravans} du monde principal) : tete -> dimension et chunk,
 * pour reposer les tickets de chunks au redemarrage ({@link CaravanTickets#restore}).
 */
public final class CaravanWorld extends SavedData {

    public record Entry(String dim, long chunk) {
    }

    private final Map<UUID, Entry> active = new HashMap<>();

    public static CaravanWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CaravanWorld::load, CaravanWorld::new, "pioneers_caravans");
    }

    public Map<UUID, Entry> entries() {
        return Map.copyOf(active);
    }

    public void note(UUID head, String dim, long chunk) {
        Entry e = new Entry(dim, chunk);
        if (!e.equals(active.put(head, e))) {
            setDirty();
        }
    }

    public void forget(UUID head) {
        if (active.remove(head) != null) {
            setDirty();
        }
    }

    public static CaravanWorld load(CompoundTag tag) {
        CaravanWorld w = new CaravanWorld();
        ListTag ls = tag.getList("caravans", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag t = ls.getCompound(i);
            if (t.hasUUID("head")) {
                w.active.put(t.getUUID("head"), new Entry(t.getString("dim"), t.getLong("chunk")));
            }
        }
        return w;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag ls = new ListTag();
        active.forEach((k, v) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("head", k);
            t.putString("dim", v.dim());
            t.putLong("chunk", v.chunk());
            ls.add(t);
        });
        tag.put("caravans", ls);
        return tag;
    }
}
