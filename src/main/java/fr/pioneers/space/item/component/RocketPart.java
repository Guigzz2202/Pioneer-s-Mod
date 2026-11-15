package fr.pioneers.space.item.component;

import fr.pioneers.space.RocketTier;
import java.util.Locale;

/**
 * Composants de fusee, declines pour chaque tier : ils s'assemblent dans la table d'assemblage spatiale
 * (chaque case de la grille attend un composant precis du tier de la fusee).
 */
public enum RocketPart {
    /** Aile (empennage). */
    WING,
    // 1.7 (refonte des composants) : la plaque de coque (rocket_plate_tier_*) est retiree ; la coque est faite des panneaux de fuselage
    // (composants de la nomenclature, un par bande de tiers) ; remap dans PiRemaps
    /** Reservoir d'hydrogene. */
    TANK,
    /** Pointe de fusee (coiffe et avionique). */
    NOSE_CONE,
    /** Booster principal (moteur central). */
    BOOSTER,
    /** Booster auxiliaire (propulseur lateral). */
    AUX_BOOSTER;

    public String prefix() {
        return "rocket_" + name().toLowerCase(Locale.ROOT);
    }

    public String id(RocketTier tier) {
        return prefix() + "_tier_" + tier.level;
    }
}
