package fr.pioneers.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 10) : memoire des troupeaux (SavedData {@code pioneers_herds} de l'Overworld) : heure du prochain troupeau de chaque
 * joueur, troupeaux actifs (joueur, espece, centre, heure d'apparition, heure de la decouverte, betes, joueurs qui l'ont trouve).
 */
public final class HerdData extends SavedData {

    public static final String NAME = "pioneers_herds";

    /** Un troupeau. {@code found} = -1 tant que personne ne l'a approche ; {@code disperse} = -1 tant qu'il ne se disperse pas. */
    public static final class Herd {
        public final int id;
        public final UUID player;
        public final String species;
        public final BlockPos center;
        public final long start;
        public long found = -1, disperse = -1, nextCall;
        public final List<UUID> members = new ArrayList<>();
        public final Set<UUID> finders = new HashSet<>();

        public Herd(int id, UUID player, String species, BlockPos center, long start) {
            this.id = id;
            this.player = player;
            this.species = species;
            this.center = center;
            this.start = start;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putUUID("player", player);
            t.putString("species", species);
            t.putLong("center", center.asLong());
            t.putLong("start", start);
            t.putLong("found", found);
            t.putLong("disperse", disperse);
            ListTag m = new ListTag();
            members.forEach(u -> m.add(NbtUtils.createUUID(u)));
            t.put("members", m);
            ListTag f = new ListTag();
            finders.forEach(u -> f.add(NbtUtils.createUUID(u)));
            t.put("finders", f);
            return t;
        }

        static Herd load(CompoundTag t) {
            Herd h = new Herd(t.getInt("id"), t.getUUID("player"), t.getString("species"), BlockPos.of(t.getLong("center")), t.getLong("start"));
            h.found = t.getLong("found");
            h.disperse = t.getLong("disperse");
            for (Tag u : t.getList("members", Tag.TAG_INT_ARRAY)) {
                h.members.add(NbtUtils.loadUUID(u));
            }
            for (Tag u : t.getList("finders", Tag.TAG_INT_ARRAY)) {
                h.finders.add(NbtUtils.loadUUID(u));
            }
            return h;
        }
    }

    final Map<UUID, Long> next = new HashMap<>();
    final Map<Integer, Herd> herds = new LinkedHashMap<>();
    int nextId = 1;

    public static HerdData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HerdData::load, HerdData::new, NAME);
    }

    public static HerdData load(CompoundTag tag) {
        HerdData d = new HerdData();
        d.nextId = Math.max(1, tag.getInt("next_id"));
        CompoundTag n = tag.getCompound("next");
        for (String k : n.getAllKeys()) {
            try {
                d.next.put(UUID.fromString(k), n.getLong(k));
            } catch (IllegalArgumentException ignored) {
                //cle illisible : ignoree
            }
        }
        for (Tag t : tag.getList("herds", Tag.TAG_COMPOUND)) {
            Herd h = Herd.load((CompoundTag) t);
            d.herds.put(h.id, h);
        }
        return d;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("next_id", nextId);
        CompoundTag n = new CompoundTag();
        next.forEach((k, v) -> n.putLong(k.toString(), v));
        tag.put("next", n);
        ListTag l = new ListTag();
        herds.values().forEach(h -> l.add(h.save()));
        tag.put("herds", l);
        return tag;
    }

    public Herd add(UUID player, String species, BlockPos center, long start) {
        Herd h = new Herd(nextId++, player, species, center, start);
        herds.put(h.id, h);
        setDirty();
        return h;
    }

    public java.util.Collection<Herd> herds() {
        return herds.values();
    }

    public Herd herd(int id) {
        return herds.get(id);
    }

    /** 1.0.5-beta (lot 31) : remet un troupeau retire (GameTests : troupeaux du serveur mis de cote le temps d'un test). */
    public void put(Herd h) {
        herds.put(h.id, h);
        setDirty();
    }

    public void remove(int id) {
        if (herds.remove(id) != null) {
            setDirty();
        }
    }

    public long next(UUID player) {
        return next.getOrDefault(player, Long.MIN_VALUE);
    }

    public void setNext(UUID player, long when) {
        next.put(player, when);
        setDirty();
    }

    /** Troupeau en cours d'un joueur (le premier), ou null. */
    public Herd of(UUID player) {
        for (Herd h : herds.values()) {
            if (h.player.equals(player)) {
                return h;
            }
        }
        return null;
    }
}
