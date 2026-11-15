package fr.pioneers.farm.crustacean;

/**
 * 1.4 : une espece de crustace ({@link GeneratedCrustaceans}, source {@code tools/gc/farm2/crustaceans.py}) : silhouette, taille du modele,
 * aquatique (sinon de rivage), habitats (bits des habitats des poissons, {@code FishSpawns}), taille reelle en cm, couleur, chair crue (id sans
 * espace de noms, vide si aucune), points de vie, rarete, marche de cote (crabes), valeurs nutritives crue et cuite.
 */
public record CrustaceanDef(String id, int shape, float size, boolean aquatic, int habitats, int cmMin, int cmMax, int colour, String meat, int health,
                            int rarity, boolean sideways, int rawFood, float rawSaturation, int cookedFood, float cookedSaturation) {

    public boolean in(int habitat) {
        return habitat >= 0 && (habitats & (1 << habitat)) != 0;
    }
}
