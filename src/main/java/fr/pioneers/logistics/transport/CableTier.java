package fr.pioneers.logistics.transport;

import java.util.Locale;

/** Tiers des cables et des buffers (couleurs ternes partagees avec les cables de donnees). Chaque tier multiplie le debit par 4. */
public enum CableTier {
    BASIC(0x4F8A55),
    ADVANCED(0x4468A0),
    ELITE(0xB5413A),
    ULTIMATE(0x6B2340),
    /**
     * 1.0.7 (« Plus loin et au-dela ») : tier MKV (T6 global), materiau l'alliage stellaire. Seuls les registres qui l'ouvrent le prennent
     * (blocs de structure des multiblocs au lot 1) ; les autres bouclent sur {@link #MK} (MKI a MKIV).
     */
    STELLAR(0x7A4FA0);

    /** Les quatre tiers MKI a MKIV (registres qui n'ont pas de MKV). */
    public static final java.util.List<CableTier> MK = java.util.List.of(BASIC, ADVANCED, ELITE, ULTIMATE);

    /** Couleur des bagues du tier (RGB). */
    public final int color;

    CableTier(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
