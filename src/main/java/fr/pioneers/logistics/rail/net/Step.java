package fr.pioneers.logistics.rail.net;

import net.minecraft.nbt.CompoundTag;

/**
 * 0.20.0 : un chemin de piece parcouru dans un sens (avant = du bout A vers le bout B).
 */
public record Step(long anchor, int path, boolean forward) {

    public Step reverse() {
        return new Step(anchor, path, !forward);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("a", anchor);
        tag.putByte("p", (byte) path);
        tag.putBoolean("f", forward);
        return tag;
    }

    public static Step load(CompoundTag tag) {
        return new Step(tag.getLong("a"), tag.getByte("p"), tag.getBoolean("f"));
    }
}
