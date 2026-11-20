package fr.pioneers.space.mining;

import fr.pioneers.space.SolarSystem.Body;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les metaux exotiques du systeme solaire, un (ou deux) par astre, extraits par les bases minieres planetaires.
 * Chacun donne un minerai brut, une poudre, un lingot, une pepite, un bloc de stockage et un bloc de minerai brut.
 * Les minerais bruts nourrissent aussi le spectrometre geologique (recherche geologique de leur astre).
 * L'ordre sert aux identifiants : on ajoute a la suite.
 */
public enum MiningMaterial {
    /** Lune : violet et cyan, le premier metal spatial. */
    LUNARIUM(Body.MOON, 0x8C78EC),
    /** Mars : rouge cuivre, abondant dans la roche martienne. */
    MARSIUM(Body.MARS, 0xE2703F),
    /** Mars (rare) : cristaux verts des profondeurs volcaniques de Tharsis. */
    THARSIUM(Body.MARS, 0x46E296),
    /** Venus : ambre et soufre, sous l'enfer de la surface. */
    VENUSIUM(Body.VENUS, 0xF6C446),
    /** Mercure : metal liquide argente, au plus pres du Soleil. */
    MERCURIUM(Body.MERCURY, 0xC4D0E2),
    /** Phobite (Phobos) : metal sombre et poreux arrache au sol carbone de Phobos. */
    PHOBITE(Body.PHOBOS, 0x6E6258),
    /** Deimite (Deimos) : alliage pale et lisse, poli par la poussiere de Deimos. */
    DEIMITE(Body.DEIMOS, 0xC8B49A),
    /** Ahunite (Ceres) : cristal salin bleute, ne dans la saumure du mont Ahuna. */
    AHUNITE(Body.CERES, 0x9ED8F0),
    /** Vestalite (Vesta) : metal rose issu des basaltes eucritiques de Vesta. */
    VESTALITE(Body.VESTA, 0xC88A8A),
    /** Pallasite (Pallas) : fer nickele constelle de cristaux d'olivine. */
    PALLASITE(Body.PALLAS, 0x8EC24A),
    /** Hygeite (Hygiea) : metal violace, presque noir, des plaines carbonees d'Hygie. */
    HYGEITE(Body.HYGIEA, 0x5A4A7A),
    /** Lokite (Io) : metal incandescent fige au bord du lac de lave Loki. */
    LOKITE(Body.IO, 0xF06A28),
    /** Conamarite (Europa) : metal cuivre remonte de l'ocean par le chaos de Conamara. */
    CONAMARITE(Body.EUROPA, 0xB89670),
    /** Sulcite (Ganymede) : metal magnetique des sillons clairs de Ganymede. */
    SULCITE(Body.GANYMEDE, 0xA8B4C0),
    /** Valhallite (Callisto) : metal brun et dense des anneaux du bassin de Valhalla. */
    VALHALLITE(Body.CALLISTO, 0x8C6A4C),
    /** Tigrite (Enceladus) : cristal metallique turquoise des rayures de tigre d'Encelade. */
    TIGRITE(Body.ENCELADUS, 0x4ED8E8),
    /** Krakenite (Titan) : metal ambre impregne d'hydrocarbures du Kraken Mare. */
    KRAKENITE(Body.TITAN, 0xD89A3A),
    /** Veronite (Miranda) : metal bleu ardoise des falaises vertigineuses de Verona. */
    VERONITE(Body.MIRANDA, 0x6E86A8),
    /** Messinite (Titania) : metal rose et nacre des canyons de Messina. */
    MESSINITE(Body.TITANIA, 0xC0A0B0),
    /** Ruachite (Triton) : cristal rose pale des plaines gelees de Ruach. */
    RUACHITE(Body.TRITON, 0xE8B4C4),
    /** Spoutnikite (Pluto) : metal creme, presque blanc, du cur de Pluton. */
    SPUTNIKITE(Body.PLUTO, 0xF0E0C0),
    /** Mordorite (Charon) : metal rouge sombre de la calotte de Mordor. */
    MORDORITE(Body.CHARON, 0x9A3A2A),
    /** Meteoritite : fer-pierre des asteroides rocheux, extrait au compresseur d'osmium (sert au rover minier). */
    METEORITITE(Body.ASTEROID_BELT, 0x7E7266),
    // --- 0.10 : exoplanetes ---
    /** Tolimanite (Toliman b) : or blanc des hautes terres vert-de-gris, sous deux soleils. */
    TOLIMANITE(Body.TOLIMAN_B, 0xE0D070),
    /** Terminite (Proxima b) : metal rose ne dans l'eternel crepuscule du terminateur. */
    TERMINITE(Body.PROXIMA_B, 0xC07AB8),
    /** Flarium (Proxima d) : cristal incandescent charge par les eruptions de Proxima. */
    FLARIUM(Body.PROXIMA_D, 0xFFB050),
    /** Pyrrhite (TRAPPIST-1b) : metal rouge sombre des coulees de lave. */
    PYRRHITE(Body.TRAPPIST_1B, 0xB0302A),
    /** Thalassite (TRAPPIST-1d) : cristal bleu profond des fonds oceaniques. */
    THALASSITE(Body.TRAPPIST_1D, 0x3A7ED8),
    /** Edenite (TRAPPIST-1e) : cristal violet des collines d'ardoise. */
    EDENITE(Body.TRAPPIST_1E, 0x9A5AE0),
    /** Borealite (TRAPPIST-1f) : cristal de glace cyan des rides cryovolcaniques. */
    BOREALITE(Body.TRAPPIST_1F, 0x9AF0E8),
    // --- 1.0.7 (lot 11) : systemes lointains ---
    /** Pulsarite (Poltergeist) : metal bleu glacier aimante par le champ du pulsar. */
    PULSARITE(Body.POLTERGEIST, 0x7FD8FF),
    /** Maanenite (Van Maanen b) : cristal blanc ne dans le carbone du monde de diamant. */
    MAANENITE(Body.VAN_MAANEN_B, 0xE6F0FF),
    /** Horizite (Gaia BH1 c) : metal violet sombre fondu par les marees du trou noir. */
    HORIZITE(Body.GAIA_BH1_C, 0x6A3AB0);

    /** Astre d'origine. */
    public final Body body;
    public final int color;

    MiningMaterial(Body body, int color) {
        this.body = body;
        this.color = color;
    }

    /** Minerai naturel genere dans la roche d'un astre lointain (les 17 metaux de ms_src30). */
    public boolean hasWorldOre() {
        return ordinal() >= PHOBITE.ordinal() && this != METEORITITE;
    }

    /** Metal cristallin : son minerai luit faiblement et sonne comme l'amethyste. */
    public boolean isCrystal() {
        return switch (this) {
            case THARSIUM, AHUNITE, LOKITE, TIGRITE, RUACHITE, FLARIUM, THALASSITE, EDENITE, BOREALITE, MAANENITE -> true;
            default -> false;
        };
    }

    /** Durete du minerai naturel (1 a 5, selon l'eloignement de l'astre). */
    public int oreHardness() {
        return switch (this) {
            case LUNARIUM -> 1;
            case MARSIUM, THARSIUM, VENUSIUM, MERCURIUM, PHOBITE, DEIMITE -> 2;
            case AHUNITE, VESTALITE, PALLASITE, HYGEITE -> 3;
            case LOKITE, CONAMARITE, SULCITE, VALHALLITE -> 4;
            case METEORITITE -> 2;
            case TOLIMANITE, TERMINITE, FLARIUM, PYRRHITE, THALASSITE, EDENITE, BOREALITE -> 5;
            default -> 5;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Metaux d'un astre. */
    public static List<MiningMaterial> of(Body body) {
        List<MiningMaterial> list = new ArrayList<>();
        for (MiningMaterial material : values()) {
            if (material.body == body) {
                list.add(material);
            }
        }
        return list;
    }
}
