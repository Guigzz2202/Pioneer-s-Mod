package fr.pioneers.core.side;

import java.util.Locale;

/**
 * Reglage d'une face de machine (comme la configuration des faces de Mekanism) : fermee, entree, sortie, ou les deux. Les couleurs sont
 * celles des collerettes des cables (entree rouge, sortie bleue).
 */
public enum SideMode {
    //0.19.13 : couleurs des ports industriels (bleu = entree, orange = sortie, vert = les deux)
    NONE(0xFF59636E),
    INPUT(0xFF3E86F0),
    OUTPUT(0xFFF08A3A),
    IO(0xFF37C48E);

    /** Couleur dans l'interface (ARGB). */
    public final int color;

    SideMode(int color) {
        this.color = color;
    }

    public boolean canInsert() {
        return this == INPUT || this == IO;
    }

    public boolean canExtract() {
        return this == OUTPUT || this == IO;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static final SideMode[] VALUES = values();

    public static SideMode byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
