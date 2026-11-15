package fr.pioneers.space.mining;

import java.util.Locale;

/**
 * Tetes de forage du rover. Chaque metal exotique permet de forger la tete qui ouvre le suivant :
 * acier (Lune) -> lunarium (Mars) -> marsium (Venus) -> venusium (tharsium, Mercure) -> mercurium.
 */
public enum DrillHead {
    STEEL(1, 600, 1.0F),
    LUNARIUM(2, 1_000, 1.25F),
    MARSIUM(3, 1_600, 1.55F),
    VENUSIUM(4, 2_600, 1.9F),
    MERCURIUM(5, 4_200, 2.4F),
    /** Neutronium (Simulateur d'univers) : quasi inusable et tres rapide. */
    NEUTRONIUM(5, 60_000, 4.0F);

    /** Durete maximale des filons que la tete peut forer. */
    public final int tier;
    /** Unites forees avant usure complete. */
    public final int durability;
    /** Multiplicateur de vitesse de forage. */
    public final float speed;

    DrillHead(int tier, int durability, float speed) {
        this.tier = tier;
        this.durability = durability;
        this.speed = speed;
    }

    public String id() {
        return "drill_head_" + name().toLowerCase(Locale.ROOT);
    }
}
