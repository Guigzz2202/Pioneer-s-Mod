package fr.pioneers.season;

import fr.pioneers.season.SeasonClimate.Family;
import fr.pioneers.season.Seasons.Date;
import fr.pioneers.season.Seasons.Season;
import javax.annotation.Nullable;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.5-beta (lot 1, specification §1.3) : les INTEMPERIES selon la saison, pur (teste par {@code tools/seasons/SeasonBench.java}).
 * <ul>
 *     <li>durees tirees par le serveur vanilla multipliees selon la saison ({@link #scale}) : printemps, pluies frequentes et courtes ; ete,
 *     temps sec, orages rares ; automne, pluies longues ; hiver, neige (orages tres rares) ; {@code /weather} reste maitre ;</li>
 *     <li>PRECIPITATION par colonne ({@link #precipitation}) : neige a la place de la pluie l'hiver dans les biomes temperes et froids,
 *     pluie dans la savane au printemps et en ete (saison des pluies), jamais rien au desert ni aux badlands ; lot 2 : c'est la VRAIE
 *     precipitation du jeu, serveur et client ({@link SeasonPrecipitation}, fournisseur de {@code fr.pioneers.weather.Precipitations}),
 *     neige sans couche ni glace hors des biomes deja enneiges (regle de Guillaume) ;</li>
 *     <li>orages d'ete plus violents : foudre en plus autour des joueurs ({@link #SUMMER_STRIKE_CHANCE}) ;</li>
 *     <li>brouillard du matin d'automne : fond de ciel plus pale ({@link #autumnMist}), aucun effet de jeu.</li>
 * </ul>
 */
public final class SeasonWeather {

    private SeasonWeather() {
    }

    /** Multiplicateurs des durees vanilla (printemps, ete, automne, hiver). */
    public static final double[] RAIN_DURATION = {0.5, 0.7, 1.6, 1.2};
    public static final double[] RAIN_DELAY = {0.45, 1.8, 0.8, 1.0};
    public static final double[] THUNDER_DURATION = {0.6, 1.0, 0.9, 0.5};
    public static final double[] THUNDER_DELAY = {1.2, 1.6, 1.0, 3.0};
    /** Ete, pendant un orage : chance par tick et par joueur d'une foudre en plus (a 48 blocs au plus, la ou il pleut). */
    public static final float SUMMER_STRIKE_CHANCE = 1F / 900F;
    public static final int SUMMER_STRIKE_RADIUS = 48;
    /** Brouillard d'automne : de 4 h a 9 h (temps du jour 22 000 a 3 000), pale jusqu'a {@link #MIST_STRENGTH}. */
    public static final float MIST_STRENGTH = 0.38F;
    public static final float[] MIST_COLOUR = {0.80F, 0.82F, 0.84F};

    /** Duree vanilla fraichement tiree -> duree de la saison (au moins 1 tick). */
    public static int scale(int vanilla, double[] table, Season season) {
        return (int) Math.max(1, Math.round(vanilla * table[season.ordinal()]));
    }

    /** Saison dominante ce jour-la (celle qui pese le plus dans le fondu). */
    public static Season dominant(Date d) {
        Seasons.Blend b = Seasons.blend(d);
        return b.t() > 0.5 ? b.to() : b.from();
    }

    /** Vrai si l'hiver domine. */
    public static boolean winter(Date d) {
        return Seasons.blend(d).weight(Season.WINTER) >= 0.5;
    }

    /** Vrai pendant la saison des pluies de la savane (printemps et ete). */
    public static boolean rainySeason(Date d) {
        Seasons.Blend b = Seasons.blend(d);
        return b.weight(Season.SPRING) + b.weight(Season.SUMMER) >= 0.5;
    }

    /**
     * Precipitation d'une colonne quand il pleut sur le monde (lot 2 : pour tout le jeu, via {@link SeasonPrecipitation}).
     *
     * @param vanilla precipitation vanilla du biome a cet endroit (neige deja la ou il neige en vanilla).
     * @param date    null : pas de saison (vanilla).
     */
    public static Biome.Precipitation precipitation(Biome.Precipitation vanilla, Family family, boolean savanna, @Nullable Date date) {
        if (date == null || family == Family.SNOWY) {
            return vanilla;
        }
        if (savanna) {
            return rainySeason(date) ? vanilla == Biome.Precipitation.SNOW ? vanilla : Biome.Precipitation.RAIN : Biome.Precipitation.NONE;
        }
        if (family == Family.DRY) {
            return Biome.Precipitation.NONE;
        }
        if (vanilla == Biome.Precipitation.RAIN && (family == Family.TEMPERATE || family == Family.COLD) && winter(date)) {
            return Biome.Precipitation.SNOW;
        }
        return vanilla;
    }

    /** Force du brouillard d'automne (0 a {@link #MIST_STRENGTH}) a cette heure ({@code dayTime} modulo 24 000 : 0 = 6 h). */
    public static float autumnMist(Date d, long dayTime) {
        double autumn = Seasons.blend(d).weight(Season.AUTUMN);
        if (autumn <= 0) {
            return 0;
        }
        long t = Math.floorMod(dayTime + 2_000, 24_000L);       // 0 = 4 h ; brume de 4 h a 9 h, au plus fort vers 6 h 30
        if (t >= 5_000) {
            return 0;
        }
        double k = t < 2_500 ? t / 2_500.0 : (5_000 - t) / 2_500.0;
        return (float) (MIST_STRENGTH * autumn * Math.sin(k * Math.PI / 2));
    }
}
