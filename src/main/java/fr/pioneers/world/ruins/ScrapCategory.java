package fr.pioneers.world.ruins;

import java.util.Locale;

/**
 * 1.9 (MAJ RP, bloc 1) : les huit categories de scraps. Chacune vient d'un bloc delabre (casing et port pour la Structure, un bloc de metier
 * pour les autres) et rend au recycleur les composants de ses familles de la nomenclature ({@code tools/gc/components/refonte/catalog.py}).
 */
public enum ScrapCategory {
    STRUCTURE, MOTION, FLUID, THERMAL, ELECTRIC, ELECTRONIC, SENSOR, ROBOTIC;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Id du scrap de cette categorie au tier 1..3. */
    public String scrap(int tier) {
        return "scrap_" + id() + "_mk" + tier;
    }

    /** Categories qui ont un bloc de metier delabre (toutes sauf la Structure). */
    public boolean hasTradeBlock() {
        return this != STRUCTURE;
    }
}
