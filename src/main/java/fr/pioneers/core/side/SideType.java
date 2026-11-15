package fr.pioneers.core.side;

import java.util.Locale;

/**
 * Ressource dont on regle les faces : une machine qui manipule plusieurs ressources a une configuration de faces par ressource (choisie par
 * les pastilles du panneau des faces). Couleurs = celles des familles de cables.
 */
public enum SideType {
    ITEMS(0xFFB07A3E),
    FLUIDS(0xFF3F76E4),
    GASES(0xFFE0E6EE),
    ENERGY(0xFF3FB6C4),
    HEAT(0xFFFF5A1F),
    PRESSURE(0xFF9FB8C8),
    DATA(0xFFA460D9);

    /** Couleur dans l'interface (ARGB). */
    public final int color;

    SideType(int color) {
        this.color = color;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static final SideType[] VALUES = values();

    public static SideType byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
