package fr.pioneers.wild;

/**
 * GENERE par tools/gc/wild/wild.py (1.0.5-beta, lot 6) : les cinq especes du gibier des forets et des plaines (sanglier, cerf, dindon,
 * lievre, bison) : echelle du rendu, boite de collision (blocs, adulte), sante, vitesse, degats et recul (0 : jamais d'attaque),
 * distance et vitesse de fuite, distance de charge, couleurs de l'oeuf, nourriture de l'elevage ; objets nouveaux (aliments). Java pur.
 * Ne pas modifier a la main.
 */
public final class GeneratedWild {

    private GeneratedWild() {
    }

    public record Species(String id, float scale, float width, float height, double health, double speed, double damage, double knockback,
                          int flee, double fleeSpeed, int charge, int egg1, int egg2, String[] food) {
    }

    /** Objet nouveau : nourriture (points, saturation ; 0 = pas un aliment), viande, faim (cru de volaille). */
    public record Food(String id, int nutrition, float saturation, boolean meat, boolean hunger) {
    }

    public static final Species[] SPECIES = {
        new Species("boar", 1.106F, 1F, 1.05F, 16.0, 0.25, 4.0, 1.2, 8, 1.6, 4, 0x3B3129, 0x8C7A62, new String[]{"minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:apple"}),
        new Species("deer", 1.11F, 1F, 1.78F, 14.0, 0.24, 0.0, 0.0, 16, 2.1, 0, 0x8A5A34, 0xE8DCC4, new String[]{"minecraft:wheat", "minecraft:apple", "minecraft:sweet_berries"}),
        new Species("turkey", 0.982F, 0.69F, 1.1F, 8.0, 0.24, 0.0, 0.0, 10, 1.7, 0, 0x4A3A2A, 0xB0402C, new String[]{"minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds"}),
        new Species("hare", 0.798F, 0.45F, 0.52F, 6.0, 0.3, 0.0, 0.0, 10, 2.4, 0, 0x8E7458, 0xF2F0EA, new String[]{"minecraft:carrot", "minecraft:dandelion", "minecraft:golden_carrot"}),
        new Species("bison", 1.408F, 1.95F, 2.39F, 34.0, 0.22, 6.0, 1.6, 5, 1.2, 0, 0x3A2A1E, 0x8A6A48, new String[]{"minecraft:wheat", "minecraft:hay_block"})
    };

    public static final Food[] ITEMS = {
        new Food("raw_venison", 3, 0.3F, true, false),
        new Food("cooked_venison", 8, 0.8F, true, false),
        new Food("raw_boar_meat", 3, 0.3F, true, false),
        new Food("cooked_boar_meat", 8, 0.8F, true, false),
        new Food("raw_bison_meat", 3, 0.3F, true, false),
        new Food("cooked_bison_meat", 9, 0.8F, true, false),
        new Food("raw_turkey", 2, 0.3F, true, true),
        new Food("cooked_turkey", 7, 0.7F, true, false),
        new Food("deer_antler", 0, 0F, false, false)
    };

    /** Bois de cerf : chance de le trouver au sol a la mue (par cerf et par an) ; a la mort d'un cerf qui les porte : 1 + chance du second. */
    public static final double ANTLER_SHED_CHANCE = 0.5, ANTLER_SECOND = 0.5;
}
