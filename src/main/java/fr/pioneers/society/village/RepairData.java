package fr.pioneers.society.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.1 (retours de Guillaume) : ce que chaque joueur a entrepris pour racheter ses fautes (donnee du monde, monde principal) : amendes
 * payees aujourd'hui par peuple, emissaires Colporteurs en route, tache de reparation en cours, taches deja faites par village et par
 * fenetre, treves rompues. Rien ne s'efface avec le temps sauf les compteurs du jour.
 */
public final class RepairData extends SavedData {

    public static final String NAME = "pioneers_village_repairs";

    /** Un emissaire en route : peuple vise, jour d'arrivee, gain porte. */
    public record Envoy(String people, long arrive, int gain) {
    }

    /** Tache de reparation acceptee. */
    public static final class Task {

        public long village;
        public String type = "";
        public String item = "";
        public int count;
        public long started, until;
        /** Veille : la nuit a commence ; echantillons, presences, monstres tues (champs : parcelles semees par le joueur). */
        public boolean night;
        public int samples, present, kills;
        /** Champs : parcelles vides relevees a l'acceptation. */
        public final List<BlockPos> plots = new ArrayList<>();

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("v", village);
            t.putString("t", type);
            t.putString("i", item);
            t.putInt("c", count);
            t.putLong("s", started);
            t.putLong("u", until);
            t.putBoolean("n", night);
            t.putInt("sa", samples);
            t.putInt("pr", present);
            t.putInt("k", kills);
            ListTag l = new ListTag();
            plots.forEach(p -> l.add(LongTag.valueOf(p.asLong())));
            t.put("pl", l);
            return t;
        }

        static Task load(CompoundTag t) {
            Task k = new Task();
            k.village = t.getLong("v");
            k.type = t.getString("t");
            k.item = t.getString("i");
            k.count = t.getInt("c");
            k.started = t.getLong("s");
            k.until = t.getLong("u");
            k.night = t.getBoolean("n");
            k.samples = t.getInt("sa");
            k.present = t.getInt("pr");
            k.kills = t.getInt("k");
            for (Tag x : t.getList("pl", Tag.TAG_LONG)) {
                k.plots.add(BlockPos.of(((LongTag) x).getAsLong()));
            }
            return k;
        }
    }

    /** Fiche d'un joueur. */
    public static final class Entry {

        public long day = -1;
        /** Amendes payees aujourd'hui, par peuple. */
        public final Map<String, Integer> amends = new HashMap<>();
        public int envoysToday;
        public final List<Envoy> envoys = new ArrayList<>();
        @Nullable
        public Task task;
        /** Taches faites : « village:sorte:fenetre ». */
        public final Set<String> done = new HashSet<>();
        /** Treve rompue jusqu'a ce jour de jeu, par village. */
        public final Map<Long, Long> truceBroken = new HashMap<>();

        /** Remet a zero les compteurs du jour au premier passage d'un nouveau jour. */
        public void today(long d) {
            if (day != d) {
                day = d;
                amends.clear();
                envoysToday = 0;
            }
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("d", day);
            CompoundTag a = new CompoundTag();
            amends.forEach(a::putInt);
            t.put("a", a);
            t.putInt("et", envoysToday);
            ListTag e = new ListTag();
            for (Envoy v : envoys) {
                CompoundTag c = new CompoundTag();
                c.putString("p", v.people());
                c.putLong("a", v.arrive());
                c.putInt("g", v.gain());
                e.add(c);
            }
            t.put("e", e);
            if (task != null) {
                t.put("task", task.save());
            }
            ListTag dn = new ListTag();
            done.forEach(s -> dn.add(StringTag.valueOf(s)));
            t.put("done", dn);
            ListTag tb = new ListTag();
            truceBroken.forEach((v, u) -> {
                CompoundTag c = new CompoundTag();
                c.putLong("v", v);
                c.putLong("u", u);
                tb.add(c);
            });
            t.put("tb", tb);
            return t;
        }

        static Entry load(CompoundTag t) {
            Entry e = new Entry();
            e.day = t.getLong("d");
            CompoundTag a = t.getCompound("a");
            for (String k : a.getAllKeys()) {
                e.amends.put(k, a.getInt(k));
            }
            e.envoysToday = t.getInt("et");
            for (Tag x : t.getList("e", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                e.envoys.add(new Envoy(c.getString("p"), c.getLong("a"), c.getInt("g")));
            }
            if (t.contains("task")) {
                e.task = Task.load(t.getCompound("task"));
            }
            for (Tag x : t.getList("done", Tag.TAG_STRING)) {
                e.done.add(x.getAsString());
            }
            for (Tag x : t.getList("tb", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) x;
                e.truceBroken.put(c.getLong("v"), c.getLong("u"));
            }
            return e;
        }
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static RepairData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RepairData::load, RepairData::new, NAME);
    }

    public Entry of(UUID player) {
        return players.computeIfAbsent(player, k -> new Entry());
    }

    @Nullable
    public Entry peek(UUID player) {
        return players.get(player);
    }

    public Map<UUID, Entry> all() {
        return players;
    }

    public void changed() {
        setDirty();
    }

    private static RepairData load(CompoundTag tag) {
        RepairData d = new RepairData();
        CompoundTag p = tag.getCompound("p");
        for (String k : p.getAllKeys()) {
            try {
                d.players.put(UUID.fromString(k), Entry.load(p.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                //identifiant illisible : la fiche est oubliee
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        CompoundTag p = new CompoundTag();
        players.forEach((u, e) -> p.put(u.toString(), e.save()));
        tag.put("p", p);
        return tag;
    }
}
