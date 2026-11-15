package fr.pioneers.farm.kitchen;

import java.util.Locale;

/**
 * Postes de transformation de l'Agriculture 2.0 : un type de recette {@code pioneers:<id>} par poste, tous au format {@link KitchenRecipe}.
 * {@link #manual} : avance au geste du joueur (coups de couteau, de fouet, tours de vis) plutot qu'au temps.
 */
public enum Station {
    CUTTING(true),
    COOKING(false),
    FRYING(false),
    BAKING(false),
    MIXING(true),
    DRYING(false),
    PRESERVING(false),
    FERMENTING(false),
    AGING(false),
    BREWING(false),
    DISTILLING(false),
    PRESSING(true),
    CHEESE_PRESSING(true),
    CHURNING(true),
    EXTRACTING(true),
    /** 1.6 (lot A2) : fumoir (viandes et poissons seches -> fumes). */
    SMOKING(false);

    public final boolean manual;

    Station(boolean manual) {
        this.manual = manual;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
