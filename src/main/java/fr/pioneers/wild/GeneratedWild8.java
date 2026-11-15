package fr.pioneers.wild;

/**
 * GENERE par tools/gc/wild/wild8.py (1.0.5-beta, lot 8) : les six especes de la savane et de la jungle (girafe, lion, rhinoceros,
 * elephant, singe, tigre ; sortes 11 a 16, a la suite des lots 6 et 7 : {@link WildAnimal#speciesOf}) : memes fiches que
 * {@link GeneratedWild.Species}, robes (variantes ; vide : robes par sexe du lion), robe du petit, armure, pas, objets nouveaux,
 * duree de vie de la peau de banane. Java pur. Ne pas modifier a la main.
 */
public final class GeneratedWild8 {

    private GeneratedWild8() {
    }

    /** Premiere sorte du lot 8 (= especes des lots 6 et 7). */
    public static final int FIRST = 11;

    public static final GeneratedWild.Species[] SPECIES = {
        new GeneratedWild.Species("giraffe", 1.361F, 1.77F, 3.3F, 30.0, 0.24, 6.0, 1.8, 10, 1.6, 0, 0xD8A050, 0x6A3E1E, new String[]{"minecraft:acacia_leaves", "minecraft:apple"}),
        new GeneratedWild.Species("lion", 1.01F, 1.11F, 1.31F, 30.0, 0.27, 7.0, 0.6, 0, 1.4, 6, 0xC89850, 0x6A4424, new String[]{"minecraft:beef", "minecraft:mutton", "pioneers:raw_big_game_meat"}),
        new GeneratedWild.Species("rhino", 1.272F, 1.95F, 2.29F, 60.0, 0.22, 9.0, 3.0, 0, 1.2, 8, 0x8C8A84, 0x5C5A56, new String[]{"minecraft:wheat", "minecraft:hay_block"}),
        new GeneratedWild.Species("elephant", 1.599F, 1.95F, 3F, 80.0, 0.2, 10.0, 2.5, 0, 1.2, 0, 0x8E8A86, 0xD8D0C0, new String[]{"minecraft:hay_block", "minecraft:melon_slice", "minecraft:sugar_cane"}),
        new GeneratedWild.Species("monkey", 0.799F, 0.48F, 0.64F, 10.0, 0.3, 0.0, 0.0, 0, 1.6, 0, 0x7A5A3A, 0xE0C8A0, new String[]{"pioneers:banana"}),
        new GeneratedWild.Species("tiger", 0.883F, 0.97F, 1.06F, 32.0, 0.28, 8.0, 0.8, 0, 1.5, 24, 0xE07A20, 0x1E1A16, new String[]{"minecraft:beef", "minecraft:porkchop", "pioneers:raw_big_game_meat"})
    };

    /** Robes de chaque espece (variantes, dans l'ordre de la variante synchronisee ; vide : robes par sexe, comme le gibier du lot 6). */
    public static final String[][] COATS = {
        {"giraffe_reticulated", "giraffe_masai"},
        {},
        {"rhino_white", "rhino_black"},
        {"elephant_gray", "elephant_red"},
        {"monkey_brown", "monkey_golden"},
        {"tiger_bengal", "tiger_white"}
    };

    /** Robe du petit ("" : le petit garde la robe de sa variante). */
    public static final String[] BABY = {"", "", "", "", "", ""};

    /** Armure (points du jeu) ; hauteur franchie sans sauter (blocs : grands animaux = une marche d'un bloc). */
    public static final double[] ARMOR = {2.0, 2.0, 14.0, 8.0, 0.0, 2.0};
    public static final float[] STEP = {1.1F, 0.6F, 1.1F, 1.1F, 0.6F, 0.6F};

    public static final GeneratedWild.Food[] ITEMS = {
        new GeneratedWild.Food("raw_big_game_meat", 3, 0.3F, true, false),
        new GeneratedWild.Food("cooked_big_game_meat", 9, 0.8F, true, false)
    };

    /** Peau de banane jetee par le singe : duree de vie (ticks ; ephemere, jamais sauvee, rien a ramasser). */
    public static final int PEEL_LIFE = 60;
}
