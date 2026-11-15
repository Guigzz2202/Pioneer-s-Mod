package fr.pioneers.world.weather;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.5-beta (lot 2) : FOURNISSEUR de precipitations d'une dimension ({@link Precipitations#register}). Appele des deux cotes (serveur et
 * client) et souvent (rendu de chaque colonne, {@code isRainingAt} de chaque entite sous la pluie) : il doit etre rapide, sans effet de bord,
 * et ne lire que des donnees connues des deux cotes (date, biome, position).
 */
@FunctionalInterface
public interface PrecipitationProvider {

    /**
     * Precipitation en ce lieu, ou null pour laisser le vanilla decider (aucun changement). Un fournisseur qui ne change rien ici DOIT rendre
     * null : le jeu reste alors exactement vanilla (couches de neige et glace des biomes enneiges comprises).
     *
     * @param biome biome a {@code pos} (deja lu par l'appelant).
     */
    @Nullable
    Precipitation at(Level level, BlockPos pos, Holder<Biome> biome);
}
