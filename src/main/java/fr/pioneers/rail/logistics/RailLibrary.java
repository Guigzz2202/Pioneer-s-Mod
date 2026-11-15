package fr.pioneers.rail.logistics;

import fr.pioneers.rail.train.Schedule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : bibliotheque du reseau ferre d'une dimension : groupes de trains (un horaire partage, ses interruptions) et interruptions
 * (conditions de declenchement, arrets a inserer). Les noms ne distinguent pas les majuscules.
 */
public final class RailLibrary {

    public static final int MAX_GROUPS = 64, MAX_INTERRUPTS = 64, NAME = 32;

    /** Groupe : horaire commun a ses trains, interruptions activees. */
    public static final class Group {

        public String name;
        public Schedule schedule = new Schedule();
        public final List<String> interrupts = new ArrayList<>();

        public Group(String name) {
            this.name = name;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.put("schedule", schedule.save());
            t.put("interrupts", names(interrupts));
            return t;
        }

        static Group load(CompoundTag t) {
            Group g = new Group(clip(t.getString("name")));
            g.schedule = Schedule.load(t.getCompound("schedule"));
            g.interrupts.addAll(names(t.getList("interrupts", Tag.TAG_STRING)));
            return g;
        }
    }

    /** Interruption : quand ses conditions sont vraies au depart d'un arret, le train fait d'abord ses arrets a elle. */
    public static final class Interrupt {

        public String name;
        public final List<Schedule.Condition> when = new ArrayList<>();
        public final List<Schedule.Stop> stops = new ArrayList<>();
        /** Peut se declencher pendant une autre interruption. */
        public boolean inside;

        public Interrupt(String name) {
            this.name = name;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.put("when", Schedule.saveConditions(when));
            t.put("stops", Schedule.saveStops(stops));
            t.putBoolean("inside", inside);
            return t;
        }

        public static Interrupt load(CompoundTag t) {
            Interrupt i = new Interrupt(clip(t.getString("name")));
            i.when.addAll(Schedule.loadConditions(t.getList("when", Tag.TAG_COMPOUND)));
            i.stops.addAll(Schedule.loadStops(t.getList("stops", Tag.TAG_COMPOUND), 8));
            i.inside = t.getBoolean("inside");
            return i;
        }
    }

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Interrupt> interrupts = new LinkedHashMap<>();

    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public static String clip(String name) {
        String n = name.trim();
        return n.length() > NAME ? n.substring(0, NAME) : n;
    }

    @Nullable
    public Group group(String name) {
        return name.isBlank() ? null : groups.get(key(name));
    }

    /** Groupe de ce nom, cree au besoin (null : nom vide ou trop de groupes). */
    @Nullable
    public Group groupOrCreate(String name) {
        if (name.isBlank()) {
            return null;
        }
        Group g = groups.get(key(name));
        if (g == null && groups.size() < MAX_GROUPS) {
            g = new Group(clip(name));
            groups.put(key(name), g);
        }
        return g;
    }

    public void removeGroup(String name) {
        groups.remove(key(name));
    }

    public Collection<Group> groups() {
        return groups.values();
    }

    @Nullable
    public Interrupt interrupt(String name) {
        return name.isBlank() ? null : interrupts.get(key(name));
    }

    /** Remplace (ou ajoute) une interruption ; faux si la bibliotheque est pleine. */
    public boolean putInterrupt(Interrupt i) {
        if (i.name.isBlank() || !interrupts.containsKey(key(i.name)) && interrupts.size() >= MAX_INTERRUPTS) {
            return false;
        }
        interrupts.put(key(i.name), i);
        return true;
    }

    public void removeInterrupt(String name) {
        interrupts.remove(key(name));
    }

    public Collection<Interrupt> interrupts() {
        return interrupts.values();
    }

    static ListTag names(List<String> list) {
        ListTag out = new ListTag();
        list.forEach(n -> out.add(StringTag.valueOf(n)));
        return out;
    }

    static List<String> names(ListTag list) {
        List<String> out = new ArrayList<>();
        for (Tag t : list) {
            if (out.size() < 16) {
                out.add(clip(t.getAsString()));
            }
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag gl = new ListTag();
        groups.values().forEach(g -> gl.add(g.save()));
        tag.put("groups", gl);
        ListTag il = new ListTag();
        interrupts.values().forEach(i -> il.add(i.save()));
        tag.put("interrupts", il);
        return tag;
    }

    public void load(CompoundTag tag) {
        groups.clear();
        interrupts.clear();
        for (Tag t : tag.getList("groups", Tag.TAG_COMPOUND)) {
            Group g = Group.load((CompoundTag) t);
            if (!g.name.isBlank()) {
                groups.put(key(g.name), g);
            }
        }
        for (Tag t : tag.getList("interrupts", Tag.TAG_COMPOUND)) {
            Interrupt i = Interrupt.load((CompoundTag) t);
            if (!i.name.isBlank()) {
                interrupts.put(key(i.name), i);
            }
        }
    }
}
