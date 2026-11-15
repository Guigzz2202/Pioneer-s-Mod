package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import mezz.jei.api.recipe.RecipeType;

/**
 * 1.0.3-beta (lot 6, Guillaume : « reorganise l'ordre des pages JEI pour les trier par age, niveau de faisabilite surtout ») : ORDRE des
 * categories du mod dans JEI.
 * <p>
 * JEI range les categories (onglets d'une recherche R / U, liste des categories) par identifiant, dans l'ordre alphabetique (Minecraft
 * d'abord) ; l'API n'a pas d'autre levier. Chaque categorie du mod recoit donc un identifiant {@code pioneers:o<rang>_<nom>} ou le rang (3
 * chiffres) vient de la liste FIGEE {@link GeneratedJeiOrder} (ecrite par {@code tools/jei/jei_order.py} : vague de la progression reelle a
 * laquelle le premier catalyseur est atteint, puis tier, puis nom ; controle dans {@code tools/jei/check_jei.py}). Le NOM logique
 * ({@code pioneers:<nom>}) reste celui des fiches de l'API Machine, des alias et des instantanes du banc : {@link #uid} le rend.
 */
public final class JeiOrder {

    private JeiOrder() {
    }

    /** Type de recette du mod au nom logique {@code name}, classe par son rang (999 : nom absent de la liste, en dernier). */
    public static <T> RecipeType<T> create(String name, Class<? extends T> type) {
        return RecipeType.create(Pioneers.MODID, prefix(name) + name, type);
    }

    /** Prefixe de rang d'un nom logique. */
    public static String prefix(String name) {
        int rank = Math.max(0, Math.min(999, GeneratedJeiOrder.RANK.getOrDefault(name, 999)));
        return "o" + (rank < 100 ? "0" : "") + (rank < 10 ? "0" : "") + rank + "_";
    }

    /** Identifiant LOGIQUE d'un type ({@code pioneers:grinding}, sans le rang ; {@code minecraft:...} tel quel). */
    public static String uid(RecipeType<?> type) {
        return type.getUid().getNamespace() + ":" + name(type);
    }

    /** Nom logique (chemin sans le rang). */
    public static String name(RecipeType<?> type) {
        String path = type.getUid().getPath();
        boolean ranked = path.length() > 5 && path.charAt(0) == 'o' && path.charAt(4) == '_' && Character.isDigit(path.charAt(1))
                         && Character.isDigit(path.charAt(2)) && Character.isDigit(path.charAt(3));
        return type.getUid().getNamespace().equals(Pioneers.MODID) && ranked ? path.substring(5) : path;
    }
}
