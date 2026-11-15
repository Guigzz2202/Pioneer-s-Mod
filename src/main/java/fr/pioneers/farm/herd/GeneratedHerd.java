package fr.pioneers.farm.herd;

/**
 * GENERE par tools/gc/farm2/herd.py (1.0.4-beta, lot 9) : races de lapins et de chevaux, regles du clapier, aliments. Java pur, sans Minecraft.
 * Ne pas modifier a la main : changer herd.py et le relancer.
 */
public final class GeneratedHerd {

    private GeneratedHerd() {
    }

    /**
     * Race de lapin : echelle du modele, croissance, lapereaux par portee, viande, peau, laine angora (0 = ne se tond pas), climat (temperature
     * du biome ou la race nait 3 fois plus), oreilles tombantes, poil long, calme (ne fuit pas).
     */
    public record RabbitBreed(String id, float scale, float growth, float litter, float meat, float pelt, float wool, float climMin, float climMax,
                              boolean lop, boolean longHair, boolean calm) {
    }

    /**
     * Race de cheval (kind 0 cheval, 1 ane, 2 mulet, 3 chameau de Bactriane, 4 dromadaire, 5 lama) : echelle du modele, bornes des stats vanilla (vitesse = attribut movement_speed,
     * saut = jump_strength, sante), force de trait et charge de bat (kg), climat.
     */
    public record HorseBreed(String id, int kind, float scale, float speedMin, float speedMax, float jumpMin, float jumpMax, float healthMin,
                             float healthMax, int draftMin, int draftMax, int packMin, int packMax, float climMin, float climMax) {
    }

    public static final RabbitBreed[] RABBITS = {
        new RabbitBreed("burgundy_fawn", 1.15F, 1F, 7F, 1.35F, 1F, 0F, 0.3F, 1.3F, false, false, false),
        new RabbitBreed("flemish_giant", 1.55F, 0.7F, 6F, 1.9F, 1.2F, 0F, -0.3F, 0.9F, false, false, false),
        new RabbitBreed("angora", 1.05F, 0.9F, 6F, 0.8F, 0.6F, 1F, -0.4F, 1F, false, true, false),
        new RabbitBreed("rex", 1F, 1F, 7F, 1F, 1.9F, 0F, 0F, 1.2F, false, false, false),
        new RabbitBreed("new_zealand_white", 1.15F, 1.3F, 9F, 1.4F, 1F, 0F, 0.2F, 1.4F, false, false, false),
        new RabbitBreed("lop", 1.1F, 1F, 7F, 1.1F, 1F, 0F, -0.2F, 1.2F, true, false, true)
    };

    public static final HorseBreed[] HORSES = {
        new HorseBreed("percheron", 0, 1.18F, 0.17F, 0.21F, 0.45F, 0.6F, 28F, 32F, 850, 1000, 140, 170, 0F, 1F),
        new HorseBreed("boulonnais", 0, 1.15F, 0.17F, 0.21F, 0.45F, 0.6F, 27F, 31F, 800, 950, 130, 160, -0.2F, 0.9F),
        new HorseBreed("thoroughbred", 0, 1.02F, 0.3F, 0.3375F, 0.65F, 0.85F, 18F, 22F, 250, 330, 70, 90, 0.3F, 1.2F),
        new HorseBreed("arabian", 0, 0.95F, 0.28F, 0.32F, 0.6F, 0.8F, 18F, 23F, 230, 300, 70, 90, 0.8F, 2F),
        new HorseBreed("merens", 0, 0.92F, 0.22F, 0.26F, 0.75F, 0.95F, 24F, 28F, 450, 550, 110, 130, -0.6F, 0.6F),
        new HorseBreed("camargue", 0, 0.9F, 0.23F, 0.27F, 0.75F, 0.95F, 23F, 27F, 420, 520, 100, 120, 0.5F, 1.4F),
        new HorseBreed("friesian", 0, 1.08F, 0.24F, 0.28F, 0.55F, 0.7F, 24F, 28F, 550, 650, 110, 130, 0.1F, 1F),
        new HorseBreed("pony", 0, 0.72F, 0.18F, 0.22F, 0.5F, 0.65F, 16F, 20F, 180, 240, 45, 60, -0.4F, 1.2F),
        new HorseBreed("donkey", 1, 1F, 0.175F, 0.175F, 0.5F, 0.5F, 15F, 30F, 300, 380, 90, 110, 0.5F, 2F),
        new HorseBreed("mule", 2, 1F, 0.175F, 0.175F, 0.5F, 0.5F, 15F, 30F, 500, 600, 130, 160, -2F, 2F),
        new HorseBreed("bactrian", 3, 1F, 0.09F, 0.09F, 0.42F, 0.42F, 32F, 32F, 450, 550, 220, 260, -2F, 2F),
        new HorseBreed("dromedary", 4, 1F, 0.1F, 0.1F, 0.42F, 0.42F, 30F, 30F, 350, 450, 190, 230, -2F, 2F),
        new HorseBreed("poitou_donkey", 1, 1.15F, 0.17F, 0.19F, 0.5F, 0.55F, 22F, 30F, 380, 450, 110, 130, -0.3F, 0.9F),
        new HorseBreed("cotentin_donkey", 1, 0.92F, 0.175F, 0.19F, 0.5F, 0.55F, 16F, 26F, 300, 360, 95, 115, -0.7F, 0.5F),
        new HorseBreed("ccara_llama", 5, 1F, 0.175F, 0.19F, 0.5F, 0.5F, 18F, 30F, 60, 90, 45, 55, 0F, 1.2F),
        new HorseBreed("tampuli_llama", 5, 1.05F, 0.16F, 0.175F, 0.5F, 0.5F, 20F, 30F, 50, 80, 35, 45, -0.8F, 0.4F),
        new HorseBreed("suri_llama", 5, 0.95F, 0.17F, 0.18F, 0.5F, 0.5F, 16F, 26F, 50, 70, 30, 40, -0.4F, 0.8F),
        new HorseBreed("chaku_llama", 5, 1F, 0.175F, 0.19F, 0.5F, 0.5F, 18F, 28F, 60, 85, 40, 50, 0.4F, 2F)
    };

    /**
     * 1.0.4-beta (lot 28) : une ENTITE par race : id du type ({@code pioneers:<id>}), espece (0 lapin, 1 cheval, 2 ane, 3 lama), index de la
     * race (dans {@link #RABBITS} pour les lapins, {@link #HORSES} sinon), couleurs de l'oeuf d'apparition ; 1.0.4-beta (lot 33) : boite de
     * collision (largeur, hauteur en blocs) au gabarit de la race (T:farm2/herd_shapes.py horse_size / llama_size).
     */
    public record HerdType(String id, int species, int breed, int egg1, int egg2, float width, float height) {
    }

    public static final HerdType[] TYPES = {
        new HerdType("burgundy_fawn_rabbit", 0, 0, 0xC67C3C, 0xECC48C, 0.4F, 0.5F),
        new HerdType("flemish_giant_rabbit", 0, 1, 0x7A7064, 0xC4BAAA, 0.4F, 0.5F),
        new HerdType("angora_rabbit", 0, 2, 0xE8E0D2, 0xF0EADE, 0.4F, 0.5F),
        new HerdType("rex_rabbit", 0, 3, 0x764E34, 0xA47A5A, 0.4F, 0.5F),
        new HerdType("new_zealand_white_rabbit", 0, 4, 0xF6F4F0, 0xFAF8F6, 0.4F, 0.5F),
        new HerdType("lop_rabbit", 0, 5, 0x9696A0, 0xE2E0DC, 0.4F, 0.5F),
        new HerdType("percheron_horse", 1, 0, 0x74767E, 0x404046, 1.7797F, 1.8472F),
        new HerdType("boulonnais_horse", 1, 1, 0xC8C8C6, 0x969698, 1.7344F, 1.817F),
        new HerdType("thoroughbred_horse", 1, 2, 0x764020, 0x1A1412, 1.282F, 1.7461F),
        new HerdType("arabian_horse", 1, 3, 0xDEDCD6, 0xC4C2BE, 1.194F, 1.5757F),
        new HerdType("merens_horse", 1, 4, 0x2E2420, 0x181414, 1.3875F, 1.4181F),
        new HerdType("camargue_horse", 1, 5, 0xE6E4DE, 0xF4F2EE, 1.282F, 1.404F),
        new HerdType("friesian_horse", 1, 6, 0x16161E, 0x0A0A0E, 1.5082F, 1.7597F),
        new HerdType("pony_horse", 1, 7, 0x965428, 0xE2CEAA, 1.0859F, 1.0638F),
        new HerdType("provence_donkey", 2, 8, 0x80766C, 0x463E38, 1.3965F, 1.5F),
        new HerdType("poitou_donkey", 2, 12, 0x463428, 0x281E18, 1.3965F, 1.5F),
        new HerdType("cotentin_donkey", 2, 13, 0x808692, 0x464850, 1.3965F, 1.5F),
        new HerdType("ccara_llama", 3, 14, 0x96603C, 0xECE2D0, 0.825F, 2.04F),
        new HerdType("tampuli_llama", 3, 15, 0xE8DEC4, 0xCEC0A0, 1.1025F, 1.904F),
        new HerdType("suri_llama", 3, 16, 0x96969C, 0x6E6E76, 0.9263F, 1.7765F),
        new HerdType("chaku_llama", 3, 17, 0x402E24, 0x281E1A, 0.975F, 1.87F)
    };

    /** Aliments du clapier : id d'objet, unites de nourriture. */
    public static final String[][] RABBIT_FOOD = {{"minecraft:carrot", "10"}, {"minecraft:golden_carrot", "40"}, {"minecraft:dandelion", "8"}, {"minecraft:hay_block", "60"}, {"pioneers:kale", "8"}, {"pioneers:lettuce", "8"}, {"pioneers:red_cabbage", "8"}, {"minecraft:wheat", "4"}, {"pioneers:barley", "4"}, {"pioneers:oats", "5"}, {"pioneers:animal_feed", "30"}};

    /** Clapier : capacite, reserve de nourriture, secondes (manger 1 unite, portee, croissance, peau, laine, fumier), viande a l'abattage. */
    public static final int HUTCH_CAPACITY = 8, HUTCH_FOOD_MAX = 240, HUTCH_EAT_SECONDS = 60, HUTCH_BREED_SECONDS = 600, HUTCH_GROW_SECONDS = 1200,
          HUTCH_PELT_SECONDS = 480, HUTCH_WOOL_SECONDS = 360, HUTCH_MANURE_SECONDS = 300, HUTCH_MEAT = 2;

    /** Attribut movement_speed -> blocs par seconde au galop. */
    public static final float BLOCKS_PER_SPEED = 42.16F;

    /**
     * 1.0.4-beta (lot 33) : ratelier du clapier (x de / a : double, simple ; y ; z) et foin (bas, hauteur pleine, z, retrait des joues), px du
     * repere de la case principale (facade au nord).
     */
    public static final float[] HUTCH_RACK_DOUBLE = {-4.5F, 4.5F}, HUTCH_RACK_SINGLE = {4.5F, 11.5F}, HUTCH_RACK_Y = {8.4F, 12.6F}, HUTCH_RACK_Z = {11.6F, 14.25F};
    public static final float HUTCH_HAY_Y0 = 9F, HUTCH_HAY_H = 3.4F, HUTCH_HAY_Z0 = 12.2F, HUTCH_HAY_Z1 = 14.2F, HUTCH_HAY_INSET = 0.6F;
}
