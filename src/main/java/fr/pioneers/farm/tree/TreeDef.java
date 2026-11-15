package fr.pioneers.farm.tree;

/**
 * Arbre fruitier (table generee {@code GeneratedFarm.TREES}, source {@code tools/gc/farm2/fruits.py}).
 *
 * @param id         id (pousse {@code <id>_sapling}, feuilles {@code <id>_leaves})
 * @param fruit      id du fruit
 * @param log        bois vanilla du tronc ({@code oak}, {@code jungle}...)
 * @param shape      forme : round, tall, wide, palm
 * @param tMin       temperature ideale minimale
 * @param tMax       temperature ideale maximale
 * @param hMin       humidite ideale minimale
 * @param hMax       humidite ideale maximale
 * @param minutes    duree moyenne fleur -> fruit mur
 * @param sources    feuilles vanilla qui donnent rarement la pousse
 * @param newFruit   le fruit est un objet a creer
 * @param nutrition  nourriture du fruit
 * @param saturation saturation du fruit
 */
public record TreeDef(String id, String fruit, String log, String shape, float tMin, float tMax, float hMin, float hMax, float minutes,
                      String[] sources, boolean newFruit, int nutrition, float saturation) {
}
