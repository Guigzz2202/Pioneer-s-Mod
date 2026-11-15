package fr.pioneers.robotics.logistics;

import fr.pioneers.robotics.LogisticResource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Reglages d'un point logistique : role, face d'acces (independante de l'endroit ou la balise est posee), priorite, nom, ressources
 * (filtre ou quantites demandees), condition sur les stocks du reseau, et mode « machine » pour un demandeur.
 */
public class PointConfig {

    public static final int MAX_ENTRIES = 9, NAME_LENGTH = 24;

    /**
     * Ressource et quantite (demande : niveau a maintenir ; filtre : ignoree), et face par laquelle elle entre (ou sort) du bloc ; null :
     * la face d'acces du point. Ex. infuseur metallurgique : la redstone par l'arriere, le fer par le dessus.
     */
    public record Entry(LogisticResource res, long amount, @Nullable Direction side) {

        public Entry(LogisticResource res, long amount) {
            this(res, amount, null);
        }

        public Entry withAmount(long value) {
            return new Entry(res, value, side);
        }

        public Entry withSide(@Nullable Direction value) {
            return new Entry(res, amount, value);
        }
    }

    /** « Seulement si le reseau a moins (ou plus) de N de cette ressource ». */
    public record Condition(LogisticResource res, boolean below, long threshold) {

        public boolean test(long stock) {
            return below ? stock < threshold : stock > threshold;
        }
    }

    public MarkerRole role = MarkerRole.PROVIDER;
    /** Face par laquelle on accede au bloc ; null : celle de la balise. */
    @Nullable
    public Direction accessSide;
    /** -1 basse, 0 normale, 1 haute. */
    public int priority;
    public String name = "";
    public final List<Entry> entries = new ArrayList<>();
    @Nullable
    public Condition condition;
    /** Demandeur : garder pleine l'entree de la machine avec ce qu'elle contient deja. */
    public boolean machineMode;

    public boolean accepts(LogisticResource res) {
        if (entries.isEmpty()) {
            return true;
        }
        for (Entry entry : entries) {
            if (entry.res().covers(res)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFiltered() {
        return !entries.isEmpty();
    }

    public PointConfig copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("role", (byte) role.ordinal());
        tag.putByte("side", (byte) (accessSide == null ? -1 : accessSide.get3DDataValue()));
        tag.putByte("priority", (byte) priority);
        tag.putString("name", name);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = entry.res().save();
            e.putLong("amount", entry.amount());
            if (entry.side() != null) {
                e.putByte("face", (byte) entry.side().get3DDataValue());
            }
            list.add(e);
        }
        tag.put("entries", list);
        if (condition != null) {
            CompoundTag c = condition.res().save();
            c.putBoolean("below", condition.below());
            c.putLong("threshold", condition.threshold());
            tag.put("condition", c);
        }
        tag.putBoolean("machine", machineMode);
        return tag;
    }

    public static PointConfig load(CompoundTag tag) {
        PointConfig config = new PointConfig();
        config.role = MarkerRole.byIndex(tag.getByte("role"));
        int side = tag.contains("side") ? tag.getByte("side") : -1;
        config.accessSide = side < 0 || side > 5 ? null : Direction.from3DDataValue(side);
        config.priority = Math.max(-1, Math.min(1, tag.getByte("priority")));
        String name = tag.getString("name");
        config.name = name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            if (config.entries.size() >= MAX_ENTRIES) {
                break;
            }
            CompoundTag e = (CompoundTag) t;
            int face = e.contains("face") ? e.getByte("face") : -1;
            config.entries.add(new Entry(LogisticResource.load(e), Math.max(0, e.getLong("amount")),
                  face < 0 || face > 5 ? null : Direction.from3DDataValue(face)));
        }
        if (tag.contains("condition")) {
            CompoundTag c = tag.getCompound("condition");
            config.condition = new Condition(LogisticResource.load(c), c.getBoolean("below"), Math.max(0, c.getLong("threshold")));
        }
        config.machineMode = tag.getBoolean("machine");
        return config;
    }
}
