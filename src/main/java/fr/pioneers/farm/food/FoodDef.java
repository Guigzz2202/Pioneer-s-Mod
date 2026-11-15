package fr.pioneers.farm.food;

/**
 * Objet de cuisine (table generee {@code GeneratedFarm.FOODS}, source {@code tools/gc/farm2/foods.py}).
 *
 * @param id         id de l'objet
 * @param kind       ing, raw, dish, drink, cont
 * @param nutrition  nourriture
 * @param saturation saturation
 * @param effects    effets (ids vanilla ou du mod : well_fed, caffeine)
 * @param seconds    duree de chaque effet
 * @param amplifiers niveau de chaque effet (0 = I)
 * @param container  contenant rendu (vide = aucun)
 * @param stack      taille de pile
 * @param fast       se mange vite
 * @param alcohol    unites d'alcool
 * @param bites      plat a poser : nombre de parts (0 = objet)
 * @param colour     couleur (contenu, etiquette)
 */
public record FoodDef(String id, String kind, int nutrition, float saturation, String[] effects, int[] seconds, int[] amplifiers, String container,
                      int stack, boolean fast, int alcohol, int bites, int colour) {

    public boolean drink() {
        return "drink".equals(kind);
    }

    public boolean edible() {
        return nutrition > 0 || effects.length > 0 || "dish".equals(kind) || drink();
    }
}
