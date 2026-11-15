package fr.pioneers.space.research;

import fr.pioneers.space.RocketTier;
import fr.pioneers.space.SolarSystem.Body;
import org.jetbrains.annotations.Nullable;

/**
 * Destinations des sondes lancees par le pas de tir automatique.
 * <ul>
 *     <li>{@code minTier} : fusee minimale ;</li>
 *     <li>{@link #probeTier()} : tier de sonde minimal ;</li>
 *     <li>{@code hydrogen} : hydrogene liquide consomme par le lancement (mB) ;</li>
 *     <li>{@code dataFailure} : probabilite, a chaque tick, que les sondes de cette destination ne transmettent rien.</li>
 * </ul>
 */
public enum ProbeDestination {
    EARTH_ORBIT(RocketTier.TIER_1, 3_000, 0.0F, Body.EARTH, 0x4FA8FF),
    MOON(RocketTier.TIER_1, 6_000, 0.03F, Body.MOON, 0xD8D8E0),
    DEEP_SPACE(RocketTier.TIER_1, 9_000, 0.08F, null, 0x9B6BFF),
    MARS(RocketTier.TIER_2, 18_000, 0.18F, Body.MARS, 0xE0583A),
    VENUS(RocketTier.TIER_3, 28_000, 0.30F, Body.VENUS, 0xF0B040),
    //Systeme externe (ajoutes a la suite : les sauvegardes restent valides)
    MERCURY(RocketTier.TIER_4, 32_000, 0.32F, Body.MERCURY, 0xA88E7A),
    SUN(RocketTier.TIER_5, 40_000, 0.40F, Body.SUN, 0xFFF06A),
    JUPITER(RocketTier.TIER_6, 48_000, 0.35F, Body.JUPITER, 0xC07848),
    SATURN(RocketTier.TIER_7, 56_000, 0.40F, Body.SATURN, 0xE6CF8C),
    URANUS(RocketTier.TIER_8, 64_000, 0.45F, Body.URANUS, 0x6FE3E8),
    NEPTUNE(RocketTier.TIER_9, 72_000, 0.50F, Body.NEPTUNE, 0x4F62F5),
    PLUTO(RocketTier.TIER_10, 80_000, 0.55F, Body.PLUTO, 0xF09AC0),
    //Systemes voisins (0.10)
    CENTAURI(RocketTier.TIER_12, 120_000, 0.50F, Body.CENTAURI_A, 0xFFD27A),
    TRAPPIST(RocketTier.TIER_13, 160_000, 0.55F, Body.TRAPPIST_1, 0xFF6A4A);

    /** Donnees envoyees par une sonde (mB de donnees par tick), avant les pertes de transmission. */
    public static final double DATA_PER_PROBE_PER_TICK = 0.5;

    public final RocketTier minTier;
    public final int hydrogen;
    public final float dataFailure;
    @Nullable
    public final Body body;
    public final int color;

    ProbeDestination(RocketTier minTier, int hydrogen, float dataFailure, @Nullable Body body, int color) {
        this.minTier = minTier;
        this.hydrogen = hydrogen;
        this.dataFailure = dataFailure;
        this.body = body;
        this.color = color;
    }

    public String getTranslationKey() {
        return "probe.pioneers.destination." + name().toLowerCase();
    }

    public String getDataKey() {
        return "probe.pioneers.data." + name().toLowerCase();
    }

    public String getShortKey() {
        return "probe.pioneers.short." + name().toLowerCase();
    }

    /** Tier de sonde minimal pour cette destination. */
    public int probeTier() {
        return switch (this) {
            case EARTH_ORBIT, MOON, DEEP_SPACE -> 1;
            case MARS, VENUS -> 2;
            case MERCURY, SUN -> 3;
            case JUPITER, SATURN -> 4;
            case URANUS, NEPTUNE, PLUTO, CENTAURI, TRAPPIST -> 5;
        };
    }

    public boolean canLaunchWith(RocketTier tier) {
        return tier.level >= minTier.level;
    }

    /** Debit moyen (mB/s) pour un nombre de sondes donne. */
    public double averageRate(int probes) {
        return probes * DATA_PER_PROBE_PER_TICK * (1 - dataFailure) * 20;
    }

    public static ProbeDestination byIndex(int index) {
        ProbeDestination[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
