package fr.pioneers.tech.kinetics.rope;

import java.util.Locale;

/** Lien entre deux poulies : couple maximal (au-dela il patine et s'use), rendement, usure (secondes de patinage), texture. */
public enum LinkType {
    FIBER_ROPE(800, 0.92, 600, "rope"),
    TARRED_ROPE(2_000, 0.93, 1_800, "tarred_rope"),
    LEATHER_BELT(4_000, 0.95, 3_000, "leather");

    public final double maxTorque, efficiency;
    /** Ticks de patinage a pleine surcharge avant rupture. */
    public final int life;
    public final String texture;

    LinkType(double maxTorque, double efficiency, int life, String texture) {
        this.maxTorque = maxTorque;
        this.efficiency = efficiency;
        this.life = life;
        this.texture = texture;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LinkType byOrdinal(int i) {
        LinkType[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, i))];
    }
}
