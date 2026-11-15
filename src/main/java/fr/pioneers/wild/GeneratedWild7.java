package fr.pioneers.wild;

/**
 * GENERE par tools/gc/wild/wild7.py (1.0.5-beta, lot 7) : les six especes des predateurs et de la petite faune (loup, ours, pic-vert,
 * rat, pigeon, serpent ; sortes 5 a 10, a la suite du gibier du lot 6 : {@link WildAnimal#speciesOf}) : memes fiches que
 * {@link GeneratedWild.Species}, robes (variantes) et robe du petit, objets nouveaux (aliments), chiffres des comportements. Java pur.
 * Ne pas modifier a la main.
 */
public final class GeneratedWild7 {

    private GeneratedWild7() {
    }

    /** Premiere sorte du lot 7 (= GeneratedWild.SPECIES.length). */
    public static final int FIRST = 5;

    public static final GeneratedWild.Species[] SPECIES = {
        new GeneratedWild.Species("gray_wolf", 1.066F, 0.74F, 1.01F, 22.0, 0.3, 4.0, 0.4, 0, 1.6, 0, 0x7A7670, 0xCFC8BC, new String[]{}),
        new GeneratedWild.Species("bear", 1.066F, 1.39F, 1.49F, 44.0, 0.26, 8.0, 1.4, 0, 1.3, 6, 0x5A4030, 0x2E241C, new String[]{"minecraft:sweet_berries", "minecraft:honey_bottle", "minecraft:salmon"}),
        new GeneratedWild.Species("woodpecker", 0.559F, 0.35F, 0.35F, 4.0, 0.22, 0.0, 0.0, 8, 1.5, 0, 0x5E8A3A, 0xC8302A, new String[]{"minecraft:wheat_seeds", "minecraft:pumpkin_seeds"}),
        new GeneratedWild.Species("rat", 0.572F, 0.5F, 0.5F, 4.0, 0.3, 0.0, 0.0, 6, 1.6, 0, 0x6A5848, 0xC8A8A0, new String[]{"minecraft:wheat_seeds", "minecraft:bread"}),
        new GeneratedWild.Species("pigeon", 0.658F, 0.38F, 0.38F, 4.0, 0.22, 0.0, 0.0, 5, 1.4, 0, 0x8A92A0, 0x5A9070, new String[]{"minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds"}),
        new GeneratedWild.Species("snake", 1.265F, 0.76F, 0.45F, 8.0, 0.2, 2.0, 0.0, 0, 1.2, 2, 0x5C6A2E, 0xD8C25A, new String[]{})
    };

    /** Robes de chaque espece (variantes, dans l'ordre de la variante synchronisee). */
    public static final String[][] COATS = {
        {"gray_wolf_gray", "gray_wolf_white", "gray_wolf_black"},
        {"bear_brown", "bear_black"},
        {"woodpecker_male", "woodpecker_female"},
        {"rat_brown", "rat_black"},
        {"pigeon_blue", "pigeon_checker", "pigeon_red", "pigeon_white"},
        {"snake_grass", "snake_viper", "snake_green", "snake_banded"}
    };

    /** Robe du petit ("" : le petit garde la robe de sa variante). */
    public static final String[] BABY = {"gray_wolf_pup", "bear_cub", "", "", "", ""};

    public static final GeneratedWild.Food[] ITEMS = {
        new GeneratedWild.Food("raw_bear_meat", 3, 0.3F, true, false),
        new GeneratedWild.Food("cooked_bear_meat", 9, 0.8F, true, false),
        new GeneratedWild.Food("raw_pigeon", 2, 0.3F, true, true),
        new GeneratedWild.Food("cooked_pigeon", 6, 0.6F, true, false)
    };

    /** Morsure du serpent : poison leger (ticks, niveau I) ; tambour du pic-vert : volume (portee = 16 x volume). */
    public static final int SNAKE_POISON_TICKS = 80;
    public static final float DRUM_VOLUME = 3F;
}
