package fr.pioneers.farm.leisure;

import java.util.List;

/**
 * GENERE par tools/gc/farm2/leisure.py : NE PAS MODIFIER A LA MAIN. Airs (instruments a notes) et rythmes (tambour) : masque des notes
 * (bit i = note i, Do3..Do5 ; frappe i du tambour) et duree en ticks de chaque pas.
 */
public final class GeneratedLeisure {

    private GeneratedLeisure() {
    }

    public record Tune(String id, int[] notes, int[] ticks) {
    }

    public static final List<Tune> MELODIES = List.of(
        new Tune("clair_de_lune", new int[]{128, 128, 128, 256, 512, 256, 128, 512, 256, 256, 128, 128, 128, 128, 256, 512, 256, 128, 512, 256, 256, 128, 256, 256, 256, 256, 32, 32, 256, 128, 64, 32, 16, 128, 128, 128, 256, 512, 256, 128, 512, 256, 256, 128},
              new int[]{8, 8, 8, 8, 16, 16, 8, 8, 8, 8, 32, 8, 8, 8, 8, 16, 16, 8, 8, 8, 8, 32, 8, 8, 8, 8, 16, 16, 8, 8, 8, 8, 32, 8, 8, 8, 8, 16, 16, 8, 8, 8, 8, 32}),
        new Tune("frere_jacques", new int[]{128, 256, 512, 128, 128, 256, 512, 128, 512, 1024, 2048, 512, 1024, 2048, 2048, 4096, 2048, 1024, 512, 128, 2048, 4096, 2048, 1024, 512, 128, 128, 16, 128, 128, 16, 128},
              new int[]{8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 16, 8, 8, 16, 4, 4, 4, 4, 8, 8, 4, 4, 4, 4, 8, 8, 8, 8, 16, 8, 8, 16}),
        new Tune("ode_joie", new int[]{512, 512, 1024, 2048, 2048, 1024, 512, 256, 128, 128, 256, 512, 512, 256, 256, 512, 512, 1024, 2048, 2048, 1024, 512, 256, 128, 128, 256, 512, 256, 128, 128},
              new int[]{8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 12, 4, 16, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 12, 4, 16}),
        new Tune("vous_dirai_je", new int[]{128, 128, 2048, 2048, 4096, 4096, 2048, 1024, 1024, 512, 512, 256, 256, 128, 2048, 2048, 1024, 1024, 512, 512, 256, 2048, 2048, 1024, 1024, 512, 512, 256, 128, 128, 2048, 2048, 4096, 4096, 2048, 1024, 1024, 512, 512, 256, 256, 128},
              new int[]{8, 8, 8, 8, 8, 8, 16, 8, 8, 8, 8, 8, 8, 16, 8, 8, 8, 8, 8, 8, 16, 8, 8, 8, 8, 8, 8, 16, 8, 8, 8, 8, 8, 8, 16, 8, 8, 8, 8, 8, 8, 16}),
        new Tune("anniversaire", new int[]{16, 16, 32, 16, 128, 64, 16, 16, 32, 16, 256, 128, 16, 16, 2048, 512, 128, 64, 32, 1024, 1024, 512, 128, 256, 128},
              new int[]{4, 4, 8, 8, 8, 16, 4, 4, 8, 8, 8, 16, 4, 4, 8, 8, 8, 8, 16, 4, 4, 8, 8, 8, 16}),
        new Tune("vive_le_vent", new int[]{512, 512, 512, 512, 512, 512, 512, 2048, 128, 256, 512, 1024, 1024, 1024, 1024, 1024, 512, 512, 512, 512, 512, 256, 256, 512, 256, 2048, 512, 512, 512, 512, 512, 512, 512, 2048, 128, 256, 512, 1024, 1024, 1024, 1024, 1024, 512, 512, 512, 512, 2048, 2048, 1024, 256, 128},
              new int[]{8, 8, 16, 8, 8, 16, 8, 8, 12, 4, 32, 8, 8, 12, 4, 8, 8, 8, 4, 4, 8, 8, 8, 8, 16, 16, 8, 8, 16, 8, 8, 16, 8, 8, 12, 4, 32, 8, 8, 12, 4, 8, 8, 8, 4, 4, 8, 8, 8, 8, 32}));

    public static final List<Tune> RHYTHMS = List.of(
        new Tune("rock", new int[]{17, 16, 18, 16, 17, 17, 18, 16, 17, 16, 18, 16, 17, 17, 18, 16, 17, 16, 18, 16, 17, 17, 18, 16, 17, 16, 18, 16, 4, 4, 8, 65},
              new int[]{4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4}),
        new Tune("marche", new int[]{1, 2, 2, 1, 2, 1, 2, 2, 1, 2, 1, 2, 2, 1, 2, 2, 2, 2, 2, 65},
              new int[]{8, 4, 4, 8, 8, 8, 4, 4, 8, 8, 8, 4, 4, 8, 8, 4, 4, 4, 4, 16}),
        new Tune("valse", new int[]{1, 16, 16, 1, 16, 16, 1, 16, 16, 1, 16, 16, 1, 16, 16, 1, 16, 16, 1, 16, 16, 65},
              new int[]{8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 24}),
        new Tune("fanfare", new int[]{2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 65, 4, 4, 8, 129, 128, 65},
              new int[]{4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 16, 4, 4, 8, 8, 8, 16}));

    /** 1.6 : une note jouee il y a moins de PLAYING_TICKS : l'instrument joue ; GRIP : la main tient la baguette a 4 px du centre. */
    public static final float PLAYING_TICKS = 40.0F, GRIP = 0.177F;
    /** Contre le corps (la tete pour la flute), apres XP 180 : +x gauche du joueur, +y haut, +z devant (InstrumentLayer). */
    public static final float[][] LAYER = {
          {0.0F, -0.068F, 0.4814F, -51.52F, 0.0F, -135.0F, 0.6F},  // flute
          {0.02F, -0.46F, 0.3F, -2.08F, -8.54F, -20.47F, 0.95F},  // guitar
          {0.0F, -0.36F, 0.3F, 0.0F, 180.0F, 0.0F, 0.7F},  // accordion
          {0.0F, -0.7F, 0.36F, 0.0F, 0.0F, 0.0F, 0.78F},  // drum
    };
    /** Premiere personne, repere de la camera (LeisureClient.onRenderHand). */
    public static final float[][] FIRST_PERSON = {
          {0.02F, -0.44F, -0.72F, -71.38F, 0.0F, 41.57F, 0.85F},  // flute
          {0.1F, -0.5F, -0.78F, -36.43F, -0.66F, 114.06F, 0.95F},  // guitar
          {0.0F, -0.46F, -0.75F, -20.0F, 180.0F, 0.0F, 0.7F},  // accordion
          {0.0F, -0.52F, -0.82F, 32.0F, 0.0F, 0.0F, 0.75F},  // drum
    };
    /** Bras droit (radians, ordre de ModelPart), relatif a la tete pour la flute. */
    public static final float[][] ARM_RIGHT = {
          {-1.43F, -0.42F, 0.0F},  // flute
          {-0.6F, -0.5F, 0.1F},  // guitar
          {-0.95F, -0.15F, 0.0F},  // accordion
          {-0.8F, -0.32F, 0.0F},  // drum
    };
    /** Bras gauche. */
    public static final float[][] ARM_LEFT = {
          {-1.62F, 0.51F, 0.0F},  // flute
          {-1.15F, 0.12F, -0.1F},  // guitar
          {-0.95F, 0.15F, 0.0F},  // accordion
          {-0.8F, 0.32F, 0.0F},  // drum
    };
    /** Instrument tenu devant la bouche (repere de la tete) plutot que contre le corps. */
    public static final boolean[] ON_HEAD = {true, false, false, false};
    /** Baguettes du tambour : dans le repere de chaque bras, et en premiere personne (main droite ; gauche en miroir). */
    public static final float[] STICK_RIGHT = {-0.0625F, 0.5938F, 0.0F, 134.25F, 34.01F, -42.17F, 0.55F},
          STICK_LEFT = {0.0625F, 0.5938F, 0.0F, 134.25F, -34.01F, 132.17F, 0.55F},
          FP_STICK = {0.2F, -0.32F, -0.62F, -142.1F, 0.52F, 71.82F, 0.6F};
}
