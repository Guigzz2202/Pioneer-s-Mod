package fr.pioneers.transport;

import java.util.Locale;

/** Tiers des cables et des buffers (couleurs ternes partagees avec les cables de donnees). Chaque tier multiplie le debit par 4. */
public enum CableTier {
    BASIC(0x4F8A55),
    ADVANCED(0x4468A0),
    ELITE(0xB5413A),
    ULTIMATE(0x6B2340);

    /** Couleur des bagues du tier (RGB). */
    public final int color;

    CableTier(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
