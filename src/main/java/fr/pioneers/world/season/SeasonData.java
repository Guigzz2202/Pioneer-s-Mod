package fr.pioneers.world.season;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 1.0.5-beta (lot 1) : le DECALAGE du calendrier d'un monde (donnee de l'Overworld, fichier {@code data/pioneers_seasons.dat}). Cree a la
 * premiere ouverture en 1.0.5 : decalage = debut du jour en cours, donc aujourd'hui = 1er jour du printemps (monde neuf : temps 0, meme
 * chose). Change seulement par {@code /pioneer season set}.
 */
public class SeasonData extends SavedData {

    public static final String NAME = "pioneers_seasons";
    private long offset;

    private SeasonData(long offset) {
        this.offset = offset;
    }

    public static SeasonData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(SeasonData::load,
              () -> new SeasonData(Math.floorDiv(overworld.getDayTime(), Seasons.DAY) * Seasons.DAY), NAME);
    }

    public long offset() {
        return offset;
    }

    public void setOffset(long offset) {
        if (offset != this.offset) {
            this.offset = offset;
            setDirty();
        }
    }

    private static SeasonData load(CompoundTag tag) {
        return new SeasonData(tag.getLong("offset"));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLong("offset", offset);
        tag.putInt("version", 1);
        return tag;
    }
}
