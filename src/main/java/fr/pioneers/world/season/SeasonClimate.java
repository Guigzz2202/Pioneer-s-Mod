package fr.pioneers.world.season;

import fr.pioneers.world.season.Seasons.Date;
import fr.pioneers.world.season.Seasons.Season;
import java.util.Locale;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * 1.0.5-beta (lot 1, specification §1.4 et §2.1) : le CLIMAT SAISONNIER, pur (aucun etat du jeu ; teste hors jeu par
 * {@code tools/seasons/SeasonBench.java}, table recopiee par {@code tools/gc/seasons/seasons.py} et controlee par {@code tools/check_p105_1.py}).
 * <ul>
 *     <li>decalage de TEMPERATURE par famille de biome et par saison ({@link #TEMPERATURE}, en °C de la minimap ; echelle du jeu :
 *     {@link #internal}), fondu entre deux saisons ({@link Seasons#blend}) ;</li>
 *     <li>decalage d'HUMIDITE ({@link #HUMIDITY}, ete des biomes secs {@link #DRY_SUMMER}, saison des pluies de la savane {@link #SAVANNA}) ;</li>
 *     <li>regle SOUTERRAINE : sans acces au ciel et sous la couche {@link #UNDERGROUND_Y}, climat fixe {@link #UNDERGROUND_CELSIUS} °C,
 *     humidite {@link #UNDERGROUND_HUMIDITY}, sans saison.</li>
 * </ul>
 */
public final class SeasonClimate {

    private SeasonClimate() {
    }

    /** Familles de biomes (spec §1.4). */
    public enum Family {
        TEMPERATE, COLD, DRY, TROPICAL, OCEAN, SNOWY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Decalages de temperature (°C) : [famille][printemps, ete, automne, hiver] = la table de la specification. */
    public static final double[][] TEMPERATURE = {
          {0, 8, -2, -14},          // tempere : plaines, forets, collines
          {-2, 6, -4, -12},         // froid : taiga, montagnes
          {0, 6, 0, -8},            // sec : desert, badlands
          {0, 3, 0, -3},            // tropical : jungle, savane, mangrove
          {0, 3, 1, -4},            // oceans
          {-2, 4, -4, -6}};         // enneige (vanilla)
    /** Humidite saisonniere (printemps, ete, automne, hiver). */
    public static final double[] HUMIDITY = {0.1, -0.1, 0.1, 0};
    /** Ete des biomes secs. */
    public static final double DRY_SUMMER = -0.2;
    /** Savane : saison des pluies au printemps et en ete (+0,25), seche le reste de l'annee (0). */
    public static final double[] SAVANNA = {0.25, 0.25, 0, 0};
    /** Regle souterraine (decision 3). */
    public static final int UNDERGROUND_Y = 50;
    public static final double UNDERGROUND_CELSIUS = 8;
    public static final float UNDERGROUND_HUMIDITY = 0.5F;
    /** Temperature de jeu sous laquelle il neige en vanilla (biome « enneige »). */
    public static final float SNOW_BELOW = 0.15F;

    /** °C de la minimap -> temperature de jeu (inverse de {@code EnvironmentSync.celsius} : t * 25 - 3). */
    public static float internal(double celsius) {
        return (float) ((celsius + 3) / 25.0);
    }

    /** Ecart en °C -> ecart de temperature de jeu. */
    public static float internalDelta(double celsius) {
        return (float) (celsius / 25.0);
    }

    public static double celsius(float internal) {
        return internal * 25.0 - 3.0;
    }

    /** Decalage de temperature (°C) de la famille ce jour-la (fondu). */
    public static double temperatureCelsius(Family f, Date d) {
        return Seasons.mix(TEMPERATURE[f.ordinal()], d);
    }

    /** Decalage de temperature de jeu de la famille ce jour-la (fondu). */
    public static float temperatureOffset(Family f, Date d) {
        return internalDelta(temperatureCelsius(f, d));
    }

    /** Table d'humidite d'une famille (savane a part). */
    public static double[] humidityTable(Family f, boolean savanna) {
        if (savanna) {
            return SAVANNA;
        }
        return f == Family.DRY ? new double[]{HUMIDITY[0], DRY_SUMMER, HUMIDITY[2], HUMIDITY[3]} : HUMIDITY;
    }

    /** Decalage d'humidite ce jour-la (fondu). */
    public static float humidityOffset(Family f, boolean savanna, Date d) {
        return (float) Seasons.mix(humidityTable(f, savanna), d);
    }

    /**
     * Famille d'un biome, d'apres sa temperature de base, son id et ses tags (fonction pure : le jeu passe {@code tag} = « le biome porte ce
     * tag », le banc une table). Ordre : enneige (temperature de base &lt; {@link #SNOW_BELOW} : il y neige en vanilla), oceans et plages,
     * sec (desert, badlands), tropical (jungle, savane, mangrove), froid (taiga, montagnes), tempere sinon.
     */
    public static Family classify(String path, Predicate<String> tag, float baseTemperature) {
        if (baseTemperature < SNOW_BELOW) {
            return Family.SNOWY;
        }
        if (tag.test("minecraft:is_ocean") || tag.test("minecraft:is_beach") || path.contains("ocean") || path.contains("beach")) {
            return Family.OCEAN;
        }
        if (tag.test("minecraft:is_badlands") || tag.test("forge:is_desert") || path.contains("desert") || path.contains("badlands")) {
            return Family.DRY;
        }
        if (savanna(path, tag) || tag.test("minecraft:is_jungle") || path.contains("jungle") || path.contains("mangrove")) {
            return Family.TROPICAL;
        }
        if (tag.test("minecraft:is_taiga") || tag.test("minecraft:is_mountain") || path.contains("taiga")) {
            return Family.COLD;
        }
        return Family.TEMPERATE;
    }

    public static boolean savanna(String path, Predicate<String> tag) {
        return tag.test("minecraft:is_savanna") || path.contains("savanna");
    }

    /** Ce qui suffit a calculer le climat d'un lieu a toute date (base du biome lue une fois). */
    public record Place(float baseTemperature, float baseHumidity, Family family, boolean savanna, boolean seasonal) {

        public float temperature(@Nullable Date d) {
            return seasonal && d != null ? baseTemperature + temperatureOffset(family, d) : baseTemperature;
        }

        public float humidity(@Nullable Date d) {
            float h = seasonal && d != null ? baseHumidity + humidityOffset(family, savanna, d) : baseHumidity;
            return Math.max(0F, Math.min(1F, h));
        }

        /** Lieu souterrain (sous la couche 50, sans ciel) : climat fixe, sans saison. */
        public static Place underground() {
            return new Place(internal(UNDERGROUND_CELSIUS), UNDERGROUND_HUMIDITY, Family.TEMPERATE, false, false);
        }
    }

    /** Saison dont la FIN est la plus proche d'une date (le changement de climat a cheval sur deux saisons « finit » la premiere). */
    public static Season endingSeason(Date d) {
        return d.inSeason() < d.length() / 2.0 ? d.season().prev() : d.season();
    }
}
