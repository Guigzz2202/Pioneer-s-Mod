package fr.pioneers.home;

import java.util.List;

/**
 * Patch 1.0.3-beta, lot 7 : les bois des meubles et des etageres (bois vanilla qui ont des planches ; le mod n'a pas de planches a lui), dans
 * l'ordre du menu creatif. Meme liste et meme ordre que {@code WOODS} de {@code tools/gc/home/woods.py} (le generateur le verifie). Classe a
 * part, sans registre : lue par {@code DishShelfBlock} pendant son initialisation.
 */
public final class HomeWoods {

    private HomeWoods() {
    }

    public static final List<String> ALL = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo",
          "crimson", "warped");
}
