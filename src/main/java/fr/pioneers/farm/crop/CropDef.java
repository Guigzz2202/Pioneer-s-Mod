package fr.pioneers.farm.crop;

/**
 * Definition d'une culture (table generee {@code GeneratedFarm.CROPS}, source {@code tools/gc/farm2/crops.py}).
 *
 * @param id         id de la culture (bloc {@code <id>_crop})
 * @param arch       archetype du dessin
 * @param minutes    pousse complete en minutes (genes moyens, climat ideal, terre humide)
 * @param tMin       temperature ideale minimale (valeur de biome)
 * @param tMax       temperature ideale maximale
 * @param hMin       humidite ideale minimale (l'irrigation ajoute {@link Climate#IRRIGATION})
 * @param hMax       humidite ideale maximale
 * @param produce    id de la recolte
 * @param average    recolte moyenne (Rendement 5)
 * @param regrow     age apres cueillette pour une plante qui repousse, -1 sinon
 * @param tall       deux blocs de haut
 * @param paddy      riziere : eau a cote de la terre labouree
 * @param seed       id de la graine
 * @param newProduce la recolte est un objet a creer
 * @param nutrition  nourriture de la recolte crue (0 = ne se mange pas)
 * @param saturation saturation de la recolte crue
 * @param leaf       couleur du feuillage (RGB)
 * @param fruit      couleur du fruit (RGB)
 */
public record CropDef(String id, String arch, float minutes, float tMin, float tMax, float hMin, float hMax, String produce, float average,
                      int regrow, boolean tall, boolean paddy, String seed, boolean newProduce, int nutrition, float saturation, int leaf,
                      int fruit) {

    public boolean regrows() {
        return regrow > 0;
    }
}
