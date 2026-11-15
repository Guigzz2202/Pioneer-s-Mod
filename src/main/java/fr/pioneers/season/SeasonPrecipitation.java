package fr.pioneers.season;

import fr.pioneers.weather.Precipitation;
import fr.pioneers.weather.PrecipitationProvider;
import fr.pioneers.weather.Precipitations;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.5-beta (lot 2) : les SAISONS sont le premier fournisseur de l'API des precipitations ({@link Precipitations}), pour l'Overworld.
 * <ul>
 *     <li>hiver des biomes temperes et froids : VRAIE neige ({@link Precipitation#SNOW_NO_LAYER}) : le jeu entier la voit comme la neige
 *     d'un biome enneige (rendu, bruit, chaudrons de neige poudreuse, ni feu eteint ni joueur mouille), mais elle ne laisse aucune couche et
 *     ne gele pas l'eau (regle de Guillaume) ;</li>
 *     <li>savane : pluie au printemps et en ete, rien le reste de l'annee ; desert et badlands : jamais rien (vanilla) ;</li>
 *     <li>partout ou la colonne neige DEJA en vanilla (biomes enneiges, sommets), ou sans calendrier : null, donc exactement vanilla
 *     (couches et glace comprises).</li>
 * </ul>
 * La decision par colonne est {@link SeasonWeather#precipitation} (pure, testee par {@code tools/seasons/SeasonBench.java}).
 */
public final class SeasonPrecipitation implements PrecipitationProvider {

    public static final SeasonPrecipitation INSTANCE = new SeasonPrecipitation();
    /** Priorite de l'inscription (un fournisseur plus prioritaire, par exemple un evenement, passe devant). */
    public static final int PRIORITY = 0;

    private static boolean registered;

    private SeasonPrecipitation() {
    }

    /** Inscrit les saisons pour l'Overworld (une fois ; appele par {@link PiSeasons#init}). */
    public static synchronized void register() {
        if (!registered) {
            registered = true;
            Precipitations.register(Level.OVERWORLD, PRIORITY, INSTANCE);
        }
    }

    @Nullable
    @Override
    public Precipitation at(Level level, BlockPos pos, Holder<Biome> biome) {
        Seasons.Date date = Seasons.now(level);
        return date == null ? null : decide(biome.value().getPrecipitationAt(pos), BiomeClimates.of(biome), date);
    }

    /** Decision pure (banc) : null si la saison ne change rien a la precipitation vanilla de la colonne. */
    @Nullable
    public static Precipitation decide(Biome.Precipitation vanilla, BiomeClimates.Info info, Seasons.Date date) {
        if (vanilla == Biome.Precipitation.SNOW) {
            return null;
        }
        Biome.Precipitation p = SeasonWeather.precipitation(vanilla, info.family(), info.savanna(), date);
        if (p == vanilla) {
            return null;
        }
        return p == Biome.Precipitation.SNOW ? Precipitation.SNOW_NO_LAYER : p == Biome.Precipitation.RAIN ? Precipitation.RAIN : Precipitation.NONE;
    }
}
