package fr.pioneers.music;

import fr.pioneers.Pioneers;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.6 : une musique de la Terre (composee pour le mod) et son disque. Catalogue : {@link GeneratedMusic} (genere par
 * tools/gc/music/pioneers_tracks.py, qui ecrit aussi les fichiers sonores, les textures des disques, les recettes et la langue).
 *
 * @param id         nom de la piste (fichiers {@code sounds/music/earth/<id>.ogg} en stereo pour le fond, {@code sounds/records/<id>.ogg}
 *                   en mono pour le juke-box)
 * @param seconds    duree reelle du fichier (le juke-box s'arrete au bout de {@code seconds * 20} ticks)
 * @param comparator signal du comparateur pose contre le juke-box (1..15)
 * @param unlocks    progres du mod qui debloquent la piste dans la rotation de fond (l'un d'eux suffit ; vide = toujours)
 * @param moments    moments de la journee ou la piste peut passer (masque {@link #DAWN}, {@link #DAY}, {@link #DUSK}, {@link #NIGHT})
 * @param favourite  moments ou elle passe de preference (poids x4)
 * @param sea        piste du bord de mer (poids x4 pres de l'eau : ocean, plage, riviere)
 * @param weight     poids de base dans le tirage
 */
public record MusicTrack(String id, int seconds, int comparator, List<String> unlocks, int moments, int favourite, boolean sea, float weight) {

    public static final int DAWN = 1, DAY = 2, DUSK = 4, NIGHT = 8, ANY = 15;

    /** Evenement sonore de la musique de fond (categorie « musique »). */
    public ResourceLocation background() {
        return Pioneers.rl("music.earth." + id);
    }

    /** Id de l'objet disque. */
    public String disc() {
        return "music_disc_" + id;
    }
}
