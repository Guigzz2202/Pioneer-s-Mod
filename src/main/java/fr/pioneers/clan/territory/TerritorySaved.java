package fr.pioneers.clan.territory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : donnee de monde du territoire (Overworld), a cote de {@code ClanData} : chunks d'un clan dissous qui attendent
 * leur liberation (7 jours, l'index les garde occupes jusque-la) et file d'attente du chunkloading quand le plafond du serveur est atteint
 * (ordre d'arrivee). Le reste du territoire vit dans les clans eux-memes.
 */
public final class TerritorySaved extends SavedData {

    public static final String NAME = "pioneers_clan_territory";

    /** Chunk d'un clan dissous, libre apres {@code until} (ms). */
    public record Release(String dim, long chunk, UUID clan, long until) {
    }

    /** Demande d'emplacement de chunkloading en attente. */
    public record Queued(UUID clan, String dim, long chunk, long at) {

        public String key() {
            return dim + "|" + chunk;
        }
    }

    public final List<Release> releases = new ArrayList<>();
    public final List<Queued> queue = new ArrayList<>();
    /** Dernier jour de jeu traite (cout du chunkloading, points). */
    public long day = -1;

    public static TerritorySaved get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TerritorySaved::load, TerritorySaved::new, NAME);
    }

    public void changed() {
        setDirty();
    }

    private static TerritorySaved load(CompoundTag tag) {
        TerritorySaved d = new TerritorySaved();
        for (Tag x : tag.getList("releases", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            d.releases.add(new Release(t.getString("dim"), t.getLong("c"), t.getUUID("clan"), t.getLong("until")));
        }
        for (Tag x : tag.getList("queue", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) x;
            d.queue.add(new Queued(t.getUUID("clan"), t.getString("dim"), t.getLong("c"), t.getLong("at")));
        }
        d.day = tag.contains("day") ? tag.getLong("day") : -1;
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("version", 1);
        ListTag r = new ListTag();
        for (Release x : releases) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", x.dim());
            t.putLong("c", x.chunk());
            t.putUUID("clan", x.clan());
            t.putLong("until", x.until());
            r.add(t);
        }
        tag.put("releases", r);
        ListTag q = new ListTag();
        for (Queued x : queue) {
            CompoundTag t = new CompoundTag();
            t.putUUID("clan", x.clan());
            t.putString("dim", x.dim());
            t.putLong("c", x.chunk());
            t.putLong("at", x.at());
            q.add(t);
        }
        tag.put("queue", q);
        tag.putLong("day", day);
        return tag;
    }
}
