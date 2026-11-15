package fr.pioneers.farm.fish;

/**
 * Espece de poisson (table generee {@link GeneratedFish}, source {@code tools/gc/farm2/fish.py}).
 *
 * @param id        id de l'espece (langue {@code farm.pioneers.fish.<id>}, robe {@code entity/fish/<id>})
 * @param item      objet pris (du mod, ou vanilla : morue, saumon, poisson-globe, poisson tropical)
 * @param shape     silhouette (index de {@link GeneratedFish#SHAPES})
 * @param size      echelle du modele (1.0.3-beta lot 8 : tiree de la longueur reelle, {@code fish_shapes.length_blocks})
 * @param width     largeur de la boite de l'entite de l'espece (individu moyen ; {@code pioneers:fish_<id>})
 * @param height    hauteur de la boite
 * @param strength  force au combat (1..10)
 * @param behaviour comportement au combat (index de {@link GeneratedFish#BEHAVIOURS})
 * @param rarity    poids dans l'habitat (100 = commun)
 * @param habitats  bits des habitats ({@link GeneratedFish#HABITATS})
 * @param prop      propriete (venom, nightvision, electric, roe, strength, legendary ; vide)
 * @param food      nourriture
 * @param cmMin     taille reelle (cm)
 * @param cmMax     idem
 * @param colour    couleur du corps
 * @param accent    couleur des nageoires
 * @param length    1.0.5-beta (lot 28) : longueur VISIBLE du modele a l'echelle {@code size} (blocs, individu moyen ; {@code fish_shapes.length_blocks}) :
 *                  l'aquarium cadre le poisson sur elle (avant : sur {@code width}, 0,55 fois la longueur : le silure s'y voyait a 1,6 bloc pour
 *                  0,88 annonce)
 */
public record FishDef(String id, String item, int shape, float size, float width, float height, int strength, int behaviour, int rarity, int habitats, String prop, int food,
                      int cmMin, int cmMax, int colour, int accent, float length) {

    public boolean in(int habitat) {
        return (habitats & (1 << habitat)) != 0;
    }

    public boolean legendary() {
        return "legendary".equals(prop);
    }
}
