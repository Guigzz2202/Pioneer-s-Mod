package fr.pioneers.world.hunt;

import fr.pioneers.core.common.PiCommonConfig;

/**
 * 1.0.5-beta (lot 10) : reglages serveur de la chasse, lus d'un coup : troupeaux (section {@code fauna} : {@code herds}, {@code herdMinDays},
 * {@code herdMaxDays}, {@code herdMinDistance}, {@code herdMaxDistance}, {@code herdMinSize}, {@code herdMaxSize}) et armes a feu (section
 * {@code hunt} : {@code firearmPvpFactor}). Valeurs par defaut = {@link HuntRules} (aussi quand le fichier n'est pas encore charge).
 */
public record HuntSettings(boolean herds, double minDays, double maxDays, int minDistance, int maxDistance, int minSize, int maxSize,
                           double pvpFactor) {

    public static final HuntSettings DEFAULT = new HuntSettings(true, HuntRules.HERD_MIN_DAYS, HuntRules.HERD_MAX_DAYS, HuntRules.HERD_MIN_DISTANCE,
          HuntRules.HERD_MAX_DISTANCE, HuntRules.HERD_MIN_SIZE, HuntRules.HERD_MAX_SIZE, HuntRules.PVP_FACTOR);

    /** GameTests : reglages forces (null = la config). */
    public static volatile HuntSettings forced;

    public static HuntSettings current() {
        HuntSettings f = forced;
        if (f != null) {
            return f;
        }
        return new HuntSettings(PiCommonConfig.get(PiCommonConfig.FAUNA_HERDS, true),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MIN_DAYS, HuntRules.HERD_MIN_DAYS),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MAX_DAYS, HuntRules.HERD_MAX_DAYS),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MIN_DISTANCE, HuntRules.HERD_MIN_DISTANCE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MAX_DISTANCE, HuntRules.HERD_MAX_DISTANCE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MIN_SIZE, HuntRules.HERD_MIN_SIZE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_HERD_MAX_SIZE, HuntRules.HERD_MAX_SIZE),
              PiCommonConfig.get(PiCommonConfig.HUNT_PVP_FACTOR, HuntRules.PVP_FACTOR));
    }

    public HuntSettings withPvp(double factor) {
        return new HuntSettings(herds, minDays, maxDays, minDistance, maxDistance, minSize, maxSize, factor);
    }
}
