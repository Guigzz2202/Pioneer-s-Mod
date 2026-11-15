package fr.pioneers.music;

import static fr.pioneers.music.MusicTrack.DAWN;
import static fr.pioneers.music.MusicTrack.DAY;
import static fr.pioneers.music.MusicTrack.DUSK;
import static fr.pioneers.music.MusicTrack.NIGHT;

import java.util.List;

/**
 * Catalogue des musiques de la Terre (GENERE par tools/gc/music/pioneers_tracks.py : ne pas modifier a la main). Ordre = progression ;
 * le comparateur pose contre le juke-box donne le rang (1..10).
 */
@SuppressWarnings("PointlessBitwiseExpression")
public final class GeneratedMusic {

    private GeneratedMusic() {
    }

    public static final List<MusicTrack> TRACKS = List.of(
          new MusicTrack("aube_sur_les_labours", 155, 1, List.of(), DAWN | DAY, DAWN, false, 1.00F),
          new MusicTrack("le_chant_des_bles", 146, 2, List.of(), DAY | DUSK, DAY, false, 1.00F),
          new MusicTrack("les_ailes_du_moulin", 143, 3, List.of("mechanics/first_crank", "mechanics/windmill", "mechanics/water_wheel"), DAY | DUSK, 0, false, 1.00F),
          new MusicTrack("l_enclume_et_le_soufflet", 158, 4, List.of("bronze/bronze", "bronze/forged", "bronze/cast"), DAY | DUSK | NIGHT, 0, false, 1.00F),
          new MusicTrack("le_souffle_de_la_vapeur", 162, 5, List.of("bronze/boiler", "bronze/steam_engine"), DAY | DUSK, 0, false, 1.00F),
          new MusicTrack("les_hautes_cheminees", 155, 6, List.of("industry/root", "industry/first_machine"), DAY | DUSK, 0, false, 1.00F),
          new MusicTrack("cornues_et_alambics", 150, 7, List.of("chemistry/root", "agri/primitive_plastic", "agri/pyrolysis"), DAY | DUSK | NIGHT, 0, false, 1.00F),
          new MusicTrack("le_train_de_la_cote", 143, 8, List.of("bronze/steam_engine", "industry/root"), DAWN | DAY | DUSK, 0, true, 1.00F),
          new MusicTrack("lueur_de_tcherenkov", 174, 9, List.of("nuclear/root"), DAWN | DUSK | NIGHT, NIGHT, false, 0.80F),
          new MusicTrack("veillee_aux_etoiles", 156, 10, List.of(), DUSK | NIGHT, NIGHT, false, 1.00F));
}
