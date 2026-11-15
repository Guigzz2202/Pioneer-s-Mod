package fr.pioneers.world.wild;

/**
 * GENERE par tools/gc/wild/wild9.py (1.0.5-beta, lot 9) : les quinze especes de la mer et des cotes (baleines bleue, a bosse, orque,
 * cachalot ; requins blanc, marteau, tigre, baleine, gris ; raie manta ; phoque, otarie ; mouette ; manchot, pingouin ; sortes 17 a 31,
 * a la suite des lots 6 a 8 : {@link WildAnimal#speciesOf}) : memes fiches que {@link GeneratedWild.Species} (flee : alerte ; charge :
 * portee de l'agression des requins), robes, robe du petit, armure, NAGE (acceleration par tick), souffle (ticks ; 0 : branchies),
 * profondeur d'eau a l'apparition, longueur du corps (blocs), vie seulement dans l'eau, objets nouveaux. Java pur. Ne pas modifier a
 * la main.
 */
public final class GeneratedWild9 {

    private GeneratedWild9() {
    }

    /** Premiere sorte du lot 9 (= especes des lots 6 a 8). */
    public static final int FIRST = 17;

    public static final GeneratedWild.Species[] SPECIES = {
        new GeneratedWild.Species("blue_whale", 6.669F, 4.5F, 3.5F, 220.0, 0.2, 0.0, 0.0, 0, 1.4, 0, 0x4A6A8A, 0xB8C8D4, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("humpback_whale", 4.9F, 3.64F, 3.36F, 170.0, 0.2, 0.0, 0.0, 0, 1.4, 0, 0x2C3036, 0xE4E4E0, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("orca", 3.598F, 2.7F, 2.34F, 90.0, 0.2, 8.0, 0.6, 0, 1.4, 0, 0x18181C, 0xF0F0F0, new String[]{"minecraft:cod", "minecraft:salmon", "pioneers:raw_seal_meat"}),
        new GeneratedWild.Species("sperm_whale", 5.111F, 3.8F, 3.5F, 190.0, 0.2, 10.0, 2.0, 0, 1.4, 0, 0x4A4440, 0x8C8480, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("great_white_shark", 2.736F, 2.19F, 1.65F, 40.0, 0.2, 8.0, 0.0, 0, 1.4, 16, 0x707A84, 0xF0F0EE, new String[]{"minecraft:cod", "minecraft:salmon", "pioneers:raw_seal_meat"}),
        new GeneratedWild.Species("hammerhead_shark", 2.101F, 1.55F, 1.12F, 30.0, 0.2, 6.0, 0.0, 0, 1.4, 12, 0x8A8270, 0xE8E4D8, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("tiger_shark", 2.263F, 1.81F, 1.36F, 36.0, 0.2, 7.0, 0.0, 0, 1.4, 16, 0x6A6A5A, 0x3A3A30, new String[]{"minecraft:cod", "minecraft:salmon", "pioneers:raw_seal_meat"}),
        new GeneratedWild.Species("whale_shark", 4.467F, 3.28F, 2.84F, 120.0, 0.2, 0.0, 0.0, 0, 1.4, 0, 0x3A4A5E, 0xE8ECF0, new String[]{"minecraft:kelp", "minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("gray_reef_shark", 1.234F, 1.11F, 0.73F, 20.0, 0.2, 5.0, 0.0, 0, 1.4, 12, 0x7C848C, 0x1E2024, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("manta_ray", 2.443F, 2.93F, 0.73F, 30.0, 0.2, 0.0, 0.0, 6, 1.4, 0, 0x202428, 0xF0F0F0, new String[]{"minecraft:kelp"}),
        new GeneratedWild.Species("seal", 1.111F, 1F, 0.78F, 16.0, 0.11, 0.0, 0.0, 8, 1.5, 0, 0x7A8088, 0x3A3E44, new String[]{"minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish"}),
        new GeneratedWild.Species("sea_lion", 1.21F, 1.2F, 1.09F, 20.0, 0.14, 0.0, 0.0, 7, 1.5, 0, 0x7A5A3A, 0xC8A070, new String[]{"minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish"}),
        new GeneratedWild.Species("seagull", 0.736F, 0.37F, 0.44F, 6.0, 0.25, 0.0, 0.0, 5, 1.6, 0, 0xF0F0EE, 0x9AA4AC, new String[]{"minecraft:cod", "minecraft:salmon", "minecraft:bread"}),
        new GeneratedWild.Species("penguin", 0.857F, 0.51F, 1.03F, 10.0, 0.12, 0.0, 0.0, 4, 1.3, 0, 0x1E2228, 0xF0C850, new String[]{"minecraft:cod", "minecraft:salmon"}),
        new GeneratedWild.Species("razorbill", 0.622F, 0.34F, 0.37F, 6.0, 0.15, 0.0, 0.0, 5, 1.5, 0, 0x16181C, 0xEEEEEA, new String[]{"minecraft:cod", "minecraft:salmon"})
    };

    /** Robes de chaque espece (variantes, dans l'ordre de la variante synchronisee). */
    public static final String[][] COATS = {
        {"blue_whale"},
        {"humpback_whale"},
        {"orca"},
        {"sperm_whale", "sperm_whale_white"},
        {"great_white_shark"},
        {"hammerhead_shark"},
        {"tiger_shark"},
        {"whale_shark"},
        {"gray_reef_shark"},
        {"manta_ray", "manta_ray_black"},
        {"seal_harbor", "seal_gray"},
        {"sea_lion"},
        {"seagull_herring", "seagull_blackheaded"},
        {"penguin"},
        {"razorbill"}
    };

    /** Robe du petit ("" : le petit garde la robe de sa variante). */
    public static final String[] BABY = {"", "", "", "", "", "", "", "", "", "", "seal_pup", "", "", "penguin_chick", ""};

    /** Armure (points du jeu). */
    public static final double[] ARMOR = {4.0, 3.0, 2.0, 4.0, 2.0, 1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    /** Acceleration de nage par tick (vitesse de croisiere ~ 10 x, l'eau freine de 10 %% par tick). */
    public static final double[] SWIM = {0.013, 0.015, 0.03, 0.015, 0.028, 0.024, 0.026, 0.012, 0.026, 0.018, 0.028, 0.032, 0.02, 0.032, 0.03};
    /** Souffle (ticks sous l'eau avant de devoir remonter ; 0 : branchies, respire sous l'eau). */
    public static final int[] AIR = {3600, 3000, 2400, 6000, 0, 0, 0, 0, 0, 0, 2400, 2400, 600, 1800, 1200};
    /** Profondeur d'eau minimale a l'apparition (blocs). */
    public static final int[] DEPTH = {10, 8, 5, 12, 5, 4, 4, 7, 3, 4, 2, 2, 0, 2, 2};
    /** Longueur du corps de l'adulte (blocs : rendu a distance, parties de la boite des geants). */
    public static final float[] LENGTH = {25F, 15F, 9F, 16F, 8F, 6F, 6.5F, 14F, 3F, 5.5F, 2F, 2.5F, 0.82F, 0.6F, 0.69F};
    /** Portee du rendu de l'adulte : plus grande distance d'un point du modele a l'origine de la bete (blocs ; boite de rendu). */
    public static final float[] REACH = {14F, 8.8F, 5.5F, 8.9F, 5.3F, 4.2F, 4.5F, 9.7F, 2F, 2.9F, 1.3F, 1.8F, 0.7F, 1.2F, 0.6F};
    /** Vit seulement dans l'eau (cetaces, requins, raie) ; sinon amphibie (phoques, manchots) ou oiseau. */
    public static final boolean[] AQUATIC = {true, true, true, true, true, true, true, true, true, true, false, false, false, false, false};

    public static final GeneratedWild.Food[] ITEMS = {
        new GeneratedWild.Food("raw_whale_meat", 3, 0.3F, true, false),
        new GeneratedWild.Food("cooked_whale_meat", 8, 0.8F, true, false),
        new GeneratedWild.Food("whale_blubber", 0, 0F, false, false),
        new GeneratedWild.Food("raw_shark_meat", 2, 0.3F, true, false),
        new GeneratedWild.Food("cooked_shark_meat", 7, 0.7F, true, false),
        new GeneratedWild.Food("raw_ray_wing", 2, 0.3F, true, false),
        new GeneratedWild.Food("cooked_ray_wing", 6, 0.7F, true, false),
        new GeneratedWild.Food("raw_seal_meat", 3, 0.3F, true, false),
        new GeneratedWild.Food("cooked_seal_meat", 8, 0.8F, true, false)
    };
}
