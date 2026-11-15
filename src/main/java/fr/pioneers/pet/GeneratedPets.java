package fr.pioneers.pet;

/**
 * GENERE par tools/gc/pets/pets.py (1.0.5-beta, lot 5) : races de chiens et de chats, aptitudes (0 a 1), echelle, boite de collision
 * (blocs), couleurs de l'oeuf d'apparition ; variantes vanilla des chats errants. Java pur. Ne pas modifier a la main.
 */
public final class GeneratedPets {

    private GeneratedPets() {
    }

    /** Chien : flair, vitesse, force, endurance au froid ; dernier = le chien sauvage (loup vanilla), jamais tire au hasard. */
    public record DogBreed(String id, float scale, float scent, float speed, float strength, float cold, float width, float height, int egg1, int egg2, boolean wild) {
    }

    /** Chat : peche, chasse aux rats, independance. */
    public record CatBreed(String id, float scale, float fishing, float ratting, float independence, float width, float height, int egg1, int egg2) {
    }

    public static final DogBreed[] DOGS = {
        new DogBreed("german_shepherd", 0.86F, 0.85F, 0.75F, 0.8F, 0.6F, 0.484F, 0.914F, 0xC08642, 0x28201B, false),
        new DogBreed("labrador", 0.812F, 0.75F, 0.6F, 0.65F, 0.5F, 0.508F, 0.863F, 0xD8B270, 0xA07846, false),
        new DogBreed("border_collie", 0.799F, 0.6F, 0.9F, 0.5F, 0.65F, 0.449F, 0.799F, 0x1E1E22, 0xE8E6E0, false),
        new DogBreed("husky", 0.763F, 0.6F, 0.85F, 0.6F, 1F, 0.477F, 0.811F, 0x70747E, 0xEAEAE6, false),
        new DogBreed("beagle", 0.799F, 1F, 0.55F, 0.35F, 0.4F, 0.449F, 0.699F, 0xECE8DE, 0xB87436, false),
        new DogBreed("saint_bernard", 0.943F, 0.7F, 0.35F, 1F, 0.95F, 0.707F, 1.12F, 0xECE6DA, 0x9A4A22, false),
        new DogBreed("braque", 0.746F, 0.9F, 0.8F, 0.55F, 0.35F, 0.42F, 0.886F, 0xE8E4DA, 0x683828, false),
        new DogBreed("dachshund", 0.718F, 0.9F, 0.3F, 0.3F, 0.3F, 0.359F, 0.538F, 0xA85226, 0x642E14, false),
        new DogBreed("wild", 0.823F, 0.6F, 0.6F, 0.6F, 0.7F, 0.514F, 0.874F, 0x7A6A56, 0x4A4036, true)
    };

    public static final CatBreed[] CATS = {
        new CatBreed("european_tabby", 0.654F, 0.6F, 0.8F, 0.6F, 0.35F, 0.613F, 0x847258, 0x362D24),
        new CatBreed("siamese", 0.64F, 0.4F, 0.5F, 0.3F, 0.35F, 0.6F, 0xEADEC8, 0x422E24),
        new CatBreed("persian", 0.653F, 0.2F, 0.2F, 0.5F, 0.35F, 0.571F, 0xF0E6D4, 0xD68030),
        new CatBreed("maine_coon", 0.639F, 0.9F, 0.9F, 0.5F, 0.35F, 0.679F, 0x7E603E, 0xE4DAC6),
        new CatBreed("chartreux", 0.698F, 0.5F, 1F, 0.6F, 0.35F, 0.654F, 0x7A8492, 0xE69628),
        new CatBreed("bengal", 0.613F, 1F, 0.7F, 0.7F, 0.35F, 0.613F, 0xD09850, 0x302216),
        new CatBreed("norwegian", 0.634F, 0.8F, 0.7F, 0.8F, 0.35F, 0.634F, 0x9A8C7A, 0xECE8DE),
        new CatBreed("sphynx", 0.559F, 0.3F, 0.4F, 0.2F, 0.35F, 0.559F, 0xD8AC9E, 0x9C8480)
    };

    /** Nombre de races de chiens tirables (sans le chien sauvage). */
    public static final int DOG_BREEDS = 8;

    /** Variante vanilla d'un chat (id du registre) -> race (index dans {@link #CATS}). */
    public static final String[][] CAT_VARIANTS = {
        {"minecraft:tabby", "0"},
        {"minecraft:black", "0"},
        {"minecraft:red", "3"},
        {"minecraft:siamese", "1"},
        {"minecraft:british_shorthair", "4"},
        {"minecraft:calico", "6"},
        {"minecraft:persian", "2"},
        {"minecraft:ragdoll", "6"},
        {"minecraft:white", "7"},
        {"minecraft:jellie", "5"},
        {"minecraft:all_black", "4"}
    };
}
