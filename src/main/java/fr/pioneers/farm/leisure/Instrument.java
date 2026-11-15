package fr.pioneers.farm.leisure;

import org.jetbrains.annotations.Nullable;

/**
 * 1.5 : instruments de musique. Instruments a notes : 15 notes diatoniques (Do a Do, deux octaves) jouees en changeant la hauteur d'un seul son
 * (Minecraft limite la hauteur a 0,5 .. 2, soit une octave de part et d'autre du son de base). Tambour : 8 frappes, chacune son propre son.
 */
public enum Instrument {
    FLUTE("flute", false, 4),
    GUITAR("guitar", false, 3),
    ACCORDION("accordion", false, 3),
    DRUM("drum", true, 0);

    public static final int NOTES = 15, PADS = 8;
    /** Demi-tons de chaque note par rapport au son de base (Do du milieu). */
    private static final int[] SEMITONES = {-12, -10, -8, -7, -5, -3, -1, 0, 2, 4, 5, 7, 9, 11, 12};
    /** Frappes du tambour (memes noms que les sons leisure.drum_*). */
    public static final String[] PAD_IDS = {"kick", "snare", "tom_high", "tom_low", "hat", "hat_open", "crash", "clap"};

    public final String id;
    public final boolean drum;
    /** Octave affichee de la note la plus grave (Do3, Do4...). */
    public final int octave;

    Instrument(String id, boolean drum, int octave) {
        this.id = id;
        this.drum = drum;
        this.octave = octave;
    }

    public int keys() {
        return drum ? PADS : NOTES;
    }

    public float pitch(int key) {
        return drum ? 1F : (float) Math.pow(2, SEMITONES[key] / 12.0);
    }

    @Nullable
    public static Instrument byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : null;
    }
}
