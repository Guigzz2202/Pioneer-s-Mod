package fr.pioneers.world.weather;

/**
 * 1.0.5-beta (lot 2) : ce qui tombe EN UN LIEU quand le temps de la dimension est a la pluie (meme sens que
 * {@code Biome#getPrecipitationAt} : la reponse ne depend pas de la meteo du moment), et ce que cela fait au sol.
 *
 * @param type         sorte de precipitation (neige, pluie, rien, ou une sorte ajoutee plus tard).
 * @param leavesLayer  la neige forme des couches au sol ({@code SnowLayerBlock} pose par la meteo), comme dans un biome enneige vanilla.
 * @param freezesWater l'eau de surface gele (glace), comme dans un biome enneige vanilla (a toute heure, comme le vanilla).
 * @param intensity    force de 0 a 1 (reservee aux astres et aux rendus futurs ; le rendu vanilla ne la lit pas).
 */
public record Precipitation(PrecipitationType type, boolean leavesLayer, boolean freezesWater, float intensity) {

    /** Rien ne tombe (saison seche, desert d'un astre...). */
    public static final Precipitation NONE = new Precipitation(PrecipitationType.NONE, false, false, 0F);
    /** Pluie ordinaire. */
    public static final Precipitation RAIN = new Precipitation(PrecipitationType.RAIN, false, false, 1F);
    /** Neige comme dans un biome enneige vanilla : couches et glace. */
    public static final Precipitation SNOW = new Precipitation(PrecipitationType.SNOW, true, true, 1F);
    /** Neige qui tombe SANS laisser de couche ni geler l'eau (regle de Guillaume : hiver des biomes ou il ne neige pas en vanilla). */
    public static final Precipitation SNOW_NO_LAYER = new Precipitation(PrecipitationType.SNOW, false, false, 1F);

    public Precipitation {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        intensity = Math.max(0F, Math.min(1F, intensity));
    }
}
