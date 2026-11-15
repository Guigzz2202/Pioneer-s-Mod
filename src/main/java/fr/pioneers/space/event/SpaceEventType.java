package fr.pioneers.space.event;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.space.SolarSystem.Body;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Evenements spatiaux : ils frappent une dimension (ou, pour les eruptions, tout un systeme stellaire) pendant quelques minutes.
 */
public enum SpaceEventType {
    /** Pluie de meteores : des meteorites tombent autour des joueurs (astres sans atmosphere et Mars). */
    METEOR_SHOWER(0xFFB060, 2400, 6000),
    /** Eruption solaire : rayonnement intense a ciel ouvert (combinaison thermique, ou blindage anti-eruption) ; l'absorbeur solaire s'emballe. */
    SOLAR_FLARE(0xFFE070, 1800, 3600),
    /** Tempete de poussiere martienne : brouillard ocre, vent. */
    DUST_STORM(0xD08850, 3600, 9600),
    /** Aurores : voiles de lumiere dans le ciel nocturne. */
    AURORA(0x60FFB0, 3600, 9000),
    /** Supertempete d'une geante gazeuse : foudre partout, poches riches plus nombreuses. */
    GIANT_STORM(0x8090FF, 2400, 6000);

    public final int color;
    public final int minDuration, maxDuration;

    SpaceEventType(int color, int minDuration, int maxDuration) {
        this.color = color;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "event.pioneers." + id();
    }

    @Nullable
    public static SpaceEventType byId(String id) {
        for (SpaceEventType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }

    /** L'evenement peut-il toucher cet astre ? (null : la Terre) */
    public boolean canAffect(@Nullable Body body) {
        if (body == null || body == Body.EARTH) {
            return this == AURORA || this == SOLAR_FLARE;
        }
        return switch (this) {
            case METEOR_SHOWER -> !body.isGasGiant() && !body.isStar() && body != Body.VENUS && body != Body.TITAN && body != Body.TOLIMAN_B
                                  && body != Body.TRAPPIST_1D && body != Body.TRAPPIST_1E;
            case SOLAR_FLARE -> !body.isGasGiant();
            case DUST_STORM -> body == Body.MARS;
            case AURORA -> body == Body.GANYMEDE || body == Body.TOLIMAN_B || body == Body.PROXIMA_B || body == Body.TRAPPIST_1E
                           || body == Body.TRAPPIST_1F || body == Body.TITAN;
            case GIANT_STORM -> body.isGasGiant();
        };
    }

    /** Type active dans la configuration. */
    public boolean enabled() {
        return switch (this) {
            case METEOR_SHOWER -> PiCommonConfig.get(PiCommonConfig.EVENT_METEOR_SHOWER, true);
            case SOLAR_FLARE -> PiCommonConfig.get(PiCommonConfig.EVENT_SOLAR_FLARE, true);
            case DUST_STORM -> PiCommonConfig.get(PiCommonConfig.EVENT_DUST_STORM, true);
            case AURORA -> PiCommonConfig.get(PiCommonConfig.EVENT_AURORA, true);
            case GIANT_STORM -> PiCommonConfig.get(PiCommonConfig.EVENT_GIANT_STORM, true);
        };
    }

    /** Evenement dangereux : sa probabilite suit la difficulte locale. */
    public boolean isHarmful() {
        return this != AURORA;
    }

    /** Chance (par minute de jeu avec un joueur present) que l'evenement commence. */
    public float chancePerMinute() {
        return switch (this) {
            case METEOR_SHOWER -> 0.02F;
            case SOLAR_FLARE -> 0.012F;
            case DUST_STORM -> 0.03F;
            case AURORA -> 0.04F;
            case GIANT_STORM -> 0.035F;
        };
    }
}
