package fr.pioneers.rail.track;

import java.util.Locale;

/**
 * 0.20.0 : qualite d'une voie (son bloc) : vitesse maximale. Bois (age mecanique) puis MKI a MKIV (ids {@code basic}...{@code ultimate}).
 */
public enum TrackQuality {
    WOOD(4), BASIC(8), ADVANCED(14), ELITE(22), ULTIMATE(34);

    /** Vitesse maximale en blocs par seconde. */
    public final int blocksPerSecond;

    TrackQuality(int blocksPerSecond) {
        this.blocksPerSecond = blocksPerSecond;
    }

    /** Vitesse maximale en px par tick. */
    public float maxSpeed() {
        return blocksPerSecond * 0.8F;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Identifiant du bloc de voie : {@code wooden_track}, {@code basic_track}... */
    public String blockId() {
        return this == WOOD ? "wooden_track" : id() + "_track";
    }

    public static TrackQuality byId(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
    }
}
