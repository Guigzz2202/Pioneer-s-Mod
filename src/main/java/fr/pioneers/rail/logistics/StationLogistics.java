package fr.pioneers.rail.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 0.20.5 : role logistique d'une gare (sauve avec la gare dans le reseau) : depot (les trains logistiques y attendent), fournisseur (offre ce
 * qu'il y a sur son quai ; les robots y apportent ses ressources), demandeur (garde un niveau ; les robots vident son quai), ou les deux.
 */
public final class StationLogistics {

    public enum Role {
        NONE, DEPOT, SUPPLY, DEMAND, BOTH;

        public boolean supplies() {
            return this == SUPPLY || this == BOTH;
        }

        public boolean demands() {
            return this == DEMAND || this == BOTH;
        }

        public static Role byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    /** Ressource : quantite a garder sur le quai (fournisseur) ou niveau vise (demandeur). */
    public record Entry(Cargo cargo, long amount) {}

    public static final int MAX_ENTRIES = 9;

    public Role role = Role.NONE;
    public final List<Entry> entries = new ArrayList<>();
    /** Plus petite livraison (objets ou mB, FE, Mo, L selon la ressource) ; 0 : une pile ou 1 000 mB. */
    public long threshold;
    /** -1 basse, 0 normale, 1 haute. */
    public int priority;
    /** Groupe de trains autorise ("" : tous les trains logistiques). */
    public String group = "";
    /** Demandeur : demande aussi ce qui manque au reseau robotique pour les commandes de ses terminaux. */
    public boolean terminal;

    public boolean active() {
        return role != Role.NONE;
    }

    /** Seuil effectif pour une ressource. */
    public long threshold(Cargo c) {
        if (threshold > 0) {
            return threshold;
        }
        return switch (c.kind()) {
            case ITEM -> 64;
            case FLUID, GAS -> 1_000;
            case ENERGY -> 1_000_000;
            case DATA -> 10_000;
            case AIR -> 500;
        };
    }

    public long amount(Cargo c) {
        for (Entry e : entries) {
            if (e.cargo().covers(c)) {
                return e.amount();
            }
        }
        return 0;
    }

    public boolean lists(Cargo c) {
        for (Entry e : entries) {
            if (e.cargo().covers(c)) {
                return true;
            }
        }
        return false;
    }

    public StationLogistics copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("role", (byte) role.ordinal());
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = e.cargo().save();
            c.putLong("n", e.amount());
            list.add(c);
        }
        t.put("entries", list);
        t.putLong("threshold", threshold);
        t.putByte("priority", (byte) priority);
        t.putString("group", group);
        t.putBoolean("terminal", terminal);
        return t;
    }

    public static StationLogistics load(CompoundTag t) {
        StationLogistics l = new StationLogistics();
        l.role = Role.byId(t.getByte("role"));
        for (Tag e : t.getList("entries", Tag.TAG_COMPOUND)) {
            if (l.entries.size() < MAX_ENTRIES) {
                CompoundTag c = (CompoundTag) e;
                l.entries.add(new Entry(Cargo.load(c), Math.max(0, c.getLong("n"))));
            }
        }
        l.threshold = Math.max(0, t.getLong("threshold"));
        l.priority = Math.max(-1, Math.min(1, t.getByte("priority")));
        String g = t.getString("group");
        l.group = g.length() > RailLibrary.NAME ? g.substring(0, RailLibrary.NAME) : g;
        l.terminal = t.getBoolean("terminal");
        return l;
    }
}
