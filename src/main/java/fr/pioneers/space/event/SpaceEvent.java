package fr.pioneers.space.event;

import net.minecraft.nbt.CompoundTag;

/**
 * Un evenement en cours : type, fin (temps de jeu) et intensite (0,5 a 1,5).
 */
public record SpaceEvent(SpaceEventType type, long start, long end, float intensity) {

    public int remaining(long now) {
        return (int) Math.max(0, end - now);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.id());
        tag.putLong("start", start);
        tag.putLong("end", end);
        tag.putFloat("intensity", intensity);
        return tag;
    }

    public static SpaceEvent load(CompoundTag tag) {
        SpaceEventType type = SpaceEventType.byId(tag.getString("type"));
        return type == null ? null : new SpaceEvent(type, tag.getLong("start"), tag.getLong("end"), tag.getFloat("intensity"));
    }
}
