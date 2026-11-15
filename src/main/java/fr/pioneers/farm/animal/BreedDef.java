package fr.pioneers.farm.animal;

/**
 * Race d'une espece d'elevage (table generee {@link GeneratedAnimals}, source {@code tools/gc/farm2/animals.py} et {@code animal_shapes.py}).
 * 1.0.3-beta (lot 8) : chaque race est une entite ({@code pioneers:<race>_<espece>}, {@link PiBreeds}).
 *
 * @param id      id de la race (textures {@code entity/animal/<espece>/<race>_<m|f>}, langue {@code farm.pioneers.breed.<espece>.<race>})
 * @param size    taille de la femelle adulte en blocs (garrot des quadrupedes, tete des poules ; le male est 8 % plus grand)
 * @param scale   echelle du modele de la race (femelle) qui donne cette taille
 * @param width   largeur de la boite de l'entite (blocs ; le petit : moitie)
 * @param height  hauteur de la boite
 * @param milk    lait (frequence de la traite)
 * @param meat    viande (butin)
 * @param eggs    oeufs (cadence de ponte)
 * @param wool    laine (toison plus fournie, repousse plus vite)
 * @param hardy   rusticite (faim et soif baissent moins vite)
 * @param climMin temperature de biome ou la race nait le plus (min)
 * @param climMax idem (max)
 * @param dye     teinte naturelle de la laine (moutons)
 * @param egg1    couleur de fond de l'oeuf d'apparition
 * @param egg2    couleur des taches de l'oeuf d'apparition
 */
public record BreedDef(String id, float size, float scale, float width, float height, float milk, float meat, float eggs, float wool, float hardy,
                       float climMin, float climMax, String dye, int egg1, int egg2) {
}
