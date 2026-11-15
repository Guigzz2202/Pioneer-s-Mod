package fr.pioneers.cosmetic;

/**
 * GENERE par tools/gc/cosmetics/gen.py (ne pas modifier a la main) : les 20 tenues (cle, age du mod : 0 primitif et mecanique,
 * 1 bronze et vapeur, 2 industriel, 3 chimie et vivant, 4 atome et espace). Une tenue = 4 pieces (CosmeticPiece).
 */
public final class CosmeticCatalog {

    private CosmeticCatalog() {
    }

    public static final String[] OUTFITS = {"peasant", "blacksmith", "miner", "railwayman", "sailor", "captain", "steam_engineer", "chemist", "alchemist", "aviator", "retro_astronaut", "nuclear_scientist", "explorer", "merchant", "village_guard", "evening", "winter", "desert", "fisherman", "botanist"};
    public static final int[] AGES = {0, 0, 1, 2, 1, 2, 1, 3, 1, 4, 4, 4, 1, 1, 1, 2, 0, 1, 1, 3};

    /** Identifiant de l'objet d'une piece (cle de la piece : {@code CosmeticPiece.key}). Classe sans dependance : lue aussi par
     * tools/creative/check_tabs.py (plan des onglets hors du jeu). */
    public static String itemId(String outfit, String piece) {
        return "cosmetic_" + outfit + "_" + piece;
    }
}
