package fr.pioneers.tech.energy;

/**
 * 0.20.45 « Energized » : generateurs d'energie renouvelable, quatre tiers chacun (MKI a MKIV, x4 par tier). Demande de Guillaume : « des
 * moyens de creer de l'energie pour les differentes etapes, de manieres differentes et pour tous les tiers (panneaux solaires, barrage,
 * energie bio...) ».
 * <ul>
 *     <li>panneau solaire : selon la hauteur du soleil, la meteo, l'astre (distance a l'etoile, atmosphere) ; les panneaux voisins mettent
 *     leur energie en commun ;</li>
 *     <li>eolienne : nacelle et rotor (rendu anime), selon l'altitude, le degagement du disque des pales, le vent (rafales, pluie, orage) et
 *     l'atmosphere de l'astre ;</li>
 *     <li>turbine de barrage : prise d'eau a l'arriere, dans une retenue ; selon la hauteur de chute et le volume d'eau (partage entre les
 *     turbines de la meme retenue) ; sortie a l'avant, libre ;</li>
 *     <li>centrale geothermique : selon la profondeur (Nether : toujours au mieux), la lave voisine ; consomme de l'eau ;</li>
 *     <li>pile microbienne : des bacteries digerent la matiere organique (tout ce qui se composte, et les residus du vivant) et rendent du
 *     courant.</li>
 * </ul>
 * Valeurs (FE/t) : {@link #PEAK} = production maximale ; la reserve fait 400 ticks de production maximale.
 */
public enum EnergyKind {
    //1.12.4 (lot F4, audit de l'energie) : solaire x2 (un champ de 16 panneaux MKI = 2 048 FE/t a midi, ~650 en moyenne : 60 % de la courbe
    //du tier au prix, sans combustible), geothermie x4/3 (au niveau du barrage, qu'elle egale en continu au fond du monde), pile microbienne
    //x8 (25 % de la courbe, combustible = dechets organiques ; avant : 3 %, sans vocation)
    //MAJ simplification (03/11/2026, Guillaume : « panneau solaire x4 ») : 128 -> 512 FE/t au MKI (comme l'eolienne et le barrage ; un
    //champ de 8 panneaux MKI = 4 096 FE/t a midi, ~1 200 en moyenne sur la journee)
    SOLAR("solar_panel", new long[]{512, 2_048, 8_192, 32_768}),
    //1.10.1 (Guillaume : « buff de l'energie ») : x2 (une eolienne MKI bien placee rend en moyenne ~430 FE/t, comme la turbine de barrage)
    WIND("wind_turbine", new long[]{512, 2_048, 8_192, 32_768}),
    HYDRO("hydro_turbine", new long[]{512, 2_048, 8_192, 32_768}),
    GEOTHERMAL("geothermal_plant", new long[]{512, 2_048, 8_192, 32_768}),
    MICROBIAL("microbial_fuel_cell", new long[]{256, 1_024, 4_096, 16_384});

    public final String key;
    /** Production maximale par tier (FE/t). */
    public final long[] peak;

    EnergyKind(String key, long[] peak) {
        this.key = key;
        this.peak = peak;
    }

    /** Identifiant du bloc d'un tier ({@code basic_solar_panel}...). */
    public String id(int tier) {
        return TIERS[tier] + "_" + key;
    }

    public long capacity(int tier) {
        return peak[tier] * 400;
    }

    public static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};

    // ------------------------------------------------------------------
    // Equilibrage propre a chaque generateur (index = tier)
    // ------------------------------------------------------------------

    /**
     * Eolienne : rayon du disque des pales (blocs) a garder libre devant la nacelle, et longueur dessinee d'une pale (px). 1.10.1 (Guillaume :
     * « augmentation de la taille du rotor ») : rayons 3/4/5/6 (avant 2/2/3/3), pales de (rayon + 0,5) x 16 - 4 px : le bout de pale tombe
     * dans le dernier anneau verifie.
     */
    public static final int[] WIND_RADIUS = {3, 4, 5, 6};
    public static final float[] WIND_BLADE = {52, 68, 84, 100};
    /** Barrage : sources d'eau de la retenue (par turbine) pour tourner a plein, et hauteur de chute pleine (blocs). */
    public static final int[] HYDRO_VOLUME = {128, 512, 2_048, 8_192};
    public static final int HYDRO_HEAD = 16;
    /** Geothermie : eau consommee a pleine puissance (mB/t) et reservoir (mB). */
    public static final int[] GEO_WATER = {2, 8, 32, 128};
    public static final int[] GEO_TANK = {8_000, 16_000, 32_000, 64_000};
    /** Pile microbienne : energie par unite de matiere organique (un objet qui se composte toujours = 1 unite). */
    /** 1.12.4 (lot F4) : x2 (avant 6 000 x2 par tier) : un objet qui se composte toujours tient 47 ticks a pleine puissance au MKI. */
    public static final long[] MICROBIAL_UNIT = {12_000, 24_000, 48_000, 96_000};
}
