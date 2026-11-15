package fr.pioneers.farm.animal;

/**
 * GENERE par tools/gc/farm2/gen_java.py depuis animals.py (races, par espece, ordre de AnimalSpecies ; aliments) et animal_shapes.py
 * (1.0.3-beta lot 8 : echelle du modele et boite de l'entite de chaque race, mesurees sur sa geometrie). Java pur, sans Minecraft.
 */
public final class GeneratedAnimals {

    private GeneratedAnimals() {
    }

    /** Races : id, taille cible (blocs), echelle du modele, boite (largeur, hauteur), lait, viande, oeufs, laine, rusticite, climat min,
     *  climat max, teinte de laine, couleurs de l'oeuf d'apparition. */
    public static final BreedDef[][] BREEDS = {
        {
            new BreedDef("holstein", 1.450F, 1.0667F, 0.950F, 1.520F, 1.700F, 0.850F, 1.000F, 1.000F, 1.000F, 0.200F, 0.950F, "white", 0x1E1E22, 0xEEEEEA),
            new BreedDef("jersey", 1.220F, 0.9946F, 0.950F, 1.300F, 1.300F, 0.700F, 1.000F, 1.000F, 1.000F, 0.400F, 1.400F, "white", 0xAA6E3E, 0x784C2C),
            new BreedDef("charolais", 1.420F, 1.0506F, 0.950F, 1.500F, 0.800F, 1.550F, 1.000F, 1.000F, 1.000F, 0.200F, 1.000F, "white", 0xECE2C8, 0xF4ECD8),
            new BreedDef("highland", 1.150F, 0.9558F, 0.950F, 1.340F, 0.900F, 1.150F, 1.000F, 1.000F, 1.600F, -0.700F, 0.350F, "white", 0xAA5826, 0xCE8042),
            new BreedDef("angus", 1.320F, 1.0057F, 0.950F, 1.380F, 0.900F, 1.350F, 1.000F, 1.000F, 1.000F, 0.000F, 1.000F, "white", 0x1C1A1A, 0x2E2A2A)
        },
        {
            new BreedDef("merino", 0.780F, 0.8758F, 0.830F, 1.000F, 1.000F, 0.800F, 1.000F, 1.900F, 1.000F, 0.300F, 1.600F, "white", 0xECDEBE, 0x8D8572),
            new BreedDef("suffolk", 0.860F, 0.8668F, 0.850F, 1.060F, 1.000F, 1.400F, 1.000F, 1.000F, 1.000F, 0.100F, 1.000F, "white", 0xECE6D6, 0x242020),
            new BreedDef("texel", 0.820F, 0.8674F, 0.820F, 1.020F, 1.000F, 1.300F, 1.000F, 1.150F, 1.000F, 0.100F, 1.000F, "white", 0xF0ECE2, 0xF4F0E8),
            new BreedDef("ouessant", 0.500F, 0.5714F, 0.520F, 0.620F, 1.000F, 0.600F, 1.000F, 0.800F, 1.600F, -0.400F, 0.800F, "black", 0x282220, 0x221E1C),
            new BreedDef("jacob", 0.760F, 0.8315F, 0.790F, 1.100F, 1.000F, 1.000F, 1.000F, 1.100F, 1.250F, 0.000F, 1.000F, "white", 0xECE6D6, 0x463228)
        },
        {
            new BreedDef("large_white", 0.900F, 1.0286F, 0.950F, 1.030F, 1.000F, 1.300F, 1.000F, 1.000F, 1.000F, 0.200F, 1.200F, "white", 0xF6CEC4, 0x937B75),
            new BreedDef("gascon", 0.800F, 0.9225F, 0.890F, 0.810F, 1.000F, 1.100F, 1.000F, 1.000F, 1.400F, 0.300F, 1.400F, "white", 0x282224, 0x181415),
            new BreedDef("duroc", 0.880F, 0.9627F, 0.950F, 0.950F, 1.000F, 1.450F, 1.000F, 1.000F, 1.000F, 0.500F, 1.800F, "white", 0x964828, 0x5A2B18),
            new BreedDef("pietrain", 0.820F, 0.8895F, 0.860F, 0.930F, 1.000F, 1.550F, 1.000F, 1.000F, 0.800F, 0.200F, 1.000F, "white", 0xF0D6CC, 0x322C2E),
            new BreedDef("kunekune", 0.580F, 0.6749F, 0.570F, 0.620F, 1.000F, 0.800F, 1.000F, 1.000F, 1.400F, 0.400F, 1.600F, "white", 0xD8A060, 0x463228)
        },
        {
            new BreedDef("leghorn", 0.500F, 0.5517F, 0.350F, 0.570F, 1.000F, 0.800F, 1.700F, 1.000F, 1.000F, 0.200F, 1.500F, "white", 0xF6F6F0, 0x939390),
            new BreedDef("marans", 0.540F, 0.6171F, 0.350F, 0.600F, 1.000F, 1.100F, 1.150F, 1.000F, 1.000F, 0.000F, 1.000F, "white", 0x342C28, 0xAA602E),
            new BreedDef("brahma", 0.680F, 0.7134F, 0.400F, 0.700F, 1.000F, 1.600F, 0.900F, 1.000F, 1.400F, -0.500F, 0.800F, "white", 0xF0EEE6, 0x282628),
            new BreedDef("gauloise", 0.500F, 0.5614F, 0.350F, 0.560F, 1.000F, 1.000F, 1.250F, 1.000F, 1.200F, 0.200F, 1.200F, "white", 0xD6963C, 0x783C1E),
            new BreedDef("silkie", 0.360F, 0.4518F, 0.350F, 0.400F, 1.000F, 0.700F, 0.800F, 1.000F, 1.000F, 0.300F, 1.500F, "white", 0xFAF8F4, 0x969492)
        },
        {
            new BreedDef("alpine", 0.800F, 0.8258F, 0.740F, 1.300F, 1.450F, 0.900F, 1.000F, 1.000F, 1.200F, -0.200F, 0.800F, "white", 0x966034, 0x28201E),
            new BreedDef("saanen", 0.860F, 0.8668F, 0.800F, 1.230F, 1.700F, 0.850F, 1.000F, 1.000F, 1.000F, 0.000F, 1.000F, "white", 0xF4F2EC, 0x92918D),
            new BreedDef("boer", 0.760F, 0.7782F, 0.700F, 1.180F, 0.800F, 1.550F, 1.000F, 1.000F, 1.000F, 0.600F, 2.000F, "white", 0xF0ECE4, 0x964628),
            new BreedDef("angora", 0.620F, 0.6841F, 0.590F, 1.000F, 0.800F, 0.800F, 1.000F, 1.500F, 1.000F, 0.100F, 1.200F, "white", 0xF6F4F0, 0x939290),
            new BreedDef("poitevine", 0.760F, 0.8040F, 0.720F, 1.150F, 1.250F, 0.900F, 1.000F, 1.000F, 1.300F, 0.100F, 1.000F, "white", 0x3C2C24, 0xE6E2D6)
        }
    };

    /** Aliments par espece : id d'objet ou #tag, valeur nourrissante (1..100). */
    public static final String[][][] FEEDS = {
        {{"minecraft:wheat", "15"}, {"pioneers:barley", "15"}, {"pioneers:oats", "18"}, {"pioneers:rye", "15"}, {"pioneers:corn", "18"}, {"minecraft:hay_block", "100"}, {"pioneers:animal_feed", "45"}, {"pioneers:soybean", "15"}, {"pioneers:kale", "15"}, {"pioneers:red_cabbage", "15"}},
        {{"minecraft:wheat", "15"}, {"pioneers:barley", "15"}, {"pioneers:oats", "18"}, {"pioneers:rye", "15"}, {"pioneers:corn", "18"}, {"minecraft:hay_block", "100"}, {"pioneers:animal_feed", "45"}, {"pioneers:kale", "15"}, {"pioneers:lettuce", "12"}},
        {{"minecraft:carrot", "18"}, {"minecraft:potato", "18"}, {"minecraft:beetroot", "15"}, {"pioneers:corn", "18"}, {"pioneers:sweet_potato", "18"}, {"minecraft:apple", "12"}, {"#forge:vegetables", "15"}, {"pioneers:animal_feed", "45"}},
        {{"#forge:seeds", "12"}, {"pioneers:corn", "18"}, {"minecraft:wheat", "12"}, {"pioneers:barley", "12"}, {"pioneers:oats", "12"}, {"pioneers:rye", "12"}, {"pioneers:animal_feed", "45"}},
        {{"minecraft:wheat", "15"}, {"pioneers:barley", "15"}, {"pioneers:oats", "18"}, {"pioneers:rye", "15"}, {"pioneers:corn", "18"}, {"minecraft:hay_block", "100"}, {"pioneers:animal_feed", "45"}, {"pioneers:kale", "15"}, {"pioneers:red_cabbage", "15"}, {"#minecraft:leaves", "8"}}
    };

    /** Couleur de la coquille des oeufs fecondes, par race de poule (ordre des races). */
    public static final int[] EGG_COLOURS = {0xF4F2EA, 0x7A462C, 0xD6B288, 0xE8D6B6, 0xEEDEC8};
}
