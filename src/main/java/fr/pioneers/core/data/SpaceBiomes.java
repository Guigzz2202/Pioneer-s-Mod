package fr.pioneers.core.data;

import fr.pioneers.space.SolarSystem.Body;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Biomes des astres du mod (ceux de leurs dimensions) et l'astre de chacun : un type de donnees de biome par biome.
 * Genere a partir des fichiers {@code dimension/*.json}.
 */
public final class SpaceBiomes {

    private SpaceBiomes() {
    }

    private static final String[][] BIOMES = {
          {"callisto_cratered_plains", "CALLISTO"},
          {"callisto_knobby_terrain", "CALLISTO"},
          {"valhalla_basin", "CALLISTO"},
          {"ahuna_mons", "CERES"},
          {"ceres_cratered_plains", "CERES"},
          {"ceres_polar_craters", "CERES"},
          {"occator_faculae", "CERES"},
          {"mordor_macula", "CHARON"},
          {"oz_terra", "CHARON"},
          {"serenity_chasma", "CHARON"},
          {"vulcan_planitia", "CHARON"},
          {"deimos_bright_ridges", "DEIMOS"},
          {"deimos_filled_craters", "DEIMOS"},
          {"deimos_smooth_plains", "DEIMOS"},
          {"enceladus_cratered_terrain", "ENCELADUS"},
          {"enceladus_smooth_plains", "ENCELADUS"},
          {"tiger_stripes", "ENCELADUS"},
          {"conamara_chaos", "EUROPA"},
          {"europa_bands", "EUROPA"},
          {"europa_ridged_plains", "EUROPA"},
          {"galileo_regio", "GANYMEDE"},
          {"ganymede_polar_frost", "GANYMEDE"},
          {"uruk_sulcus", "GANYMEDE"},
          {"hygiea_carbon_fields", "HYGIEA"},
          {"hygiea_cratered_highlands", "HYGIEA"},
          {"hygiea_dark_plains", "HYGIEA"},
          {"io_frost_fields", "IO"},
          {"io_mountains", "IO"},
          {"io_paterae", "IO"},
          {"io_sulfur_plains", "IO"},
          {"pele_deposits", "IO"},
          {"arcadia_planitia", "MARS"},
          {"dune_sea", "MARS"},
          {"jezero_delta", "MARS"},
          {"polar_ice_cap", "MARS"},
          {"tharsis_volcanoes", "MARS"},
          {"valles_marineris", "MARS"},
          {"caloris_planitia", "MERCURY"},
          {"lobate_scarps", "MERCURY"},
          {"mercury_highlands", "MERCURY"},
          {"mercury_hollows", "MERCURY"},
          {"mercury_polar_craters", "MERCURY"},
          {"miranda_coronae", "MIRANDA"},
          {"miranda_cratered_terrain", "MIRANDA"},
          {"verona_rupes", "MIRANDA"},
          {"ejecta_fields", "MOON"},
          {"lunar_highlands", "MOON"},
          {"lunar_maria", "MOON"},
          {"shadow_basin", "MOON"},
          {"pallas_bright_plains", "PALLAS"},
          {"pallas_cratered_terrain", "PALLAS"},
          {"pallas_great_basin", "PALLAS"},
          {"phobos_dark_plains", "PHOBOS"},
          {"phobos_grooved_terrain", "PHOBOS"},
          {"phobos_regolith_plains", "PHOBOS"},
          {"stickney_crater", "PHOBOS"},
          {"cthulhu_macula", "PLUTO"},
          {"hillary_montes", "PLUTO"},
          {"pluto_frost_plains", "PLUTO"},
          {"sputnik_planitia", "PLUTO"},
          {"tartarus_dorsa", "PLUTO"},
          {"proxima_dayside_scorch", "PROXIMA_B"},
          {"proxima_highlands", "PROXIMA_B"},
          {"proxima_nightside_glacier", "PROXIMA_B"},
          {"proxima_twilight", "PROXIMA_B"},
          {"proxima_d_flare_fields", "PROXIMA_D"},
          {"proxima_d_iron_ridges", "PROXIMA_D"},
          {"proxima_d_molten_seas", "PROXIMA_D"},
          {"proxima_d_slag_plains", "PROXIMA_D"},
          {"kraken_mare", "TITAN"},
          {"titan_dune_fields", "TITAN"},
          {"titan_lake_district", "TITAN"},
          {"xanadu_highlands", "TITAN"},
          {"messina_chasmata", "TITANIA"},
          {"titania_cratered_plains", "TITANIA"},
          {"titania_frost_plains", "TITANIA"},
          {"toliman_bloom_forest", "TOLIMAN_B"},
          {"toliman_verdant_lowlands", "TOLIMAN_B"},
          {"twin_sun_shallows", "TOLIMAN_B"},
          {"verdigris_highlands", "TOLIMAN_B"},
          {"trappist_1b_ash_plains", "TRAPPIST_1B"},
          {"trappist_1b_lava_rivers", "TRAPPIST_1B"},
          {"trappist_1b_obsidian_flats", "TRAPPIST_1B"},
          {"trappist_1b_volcanic_peaks", "TRAPPIST_1B"},
          {"trappist_1d_archipelago", "TRAPPIST_1D"},
          {"trappist_1d_endless_ocean", "TRAPPIST_1D"},
          {"trappist_1d_tidal_flats", "TRAPPIST_1D"},
          {"trappist_1e_crimson_lakes", "TRAPPIST_1E"},
          {"trappist_1e_glowcap_forest", "TRAPPIST_1E"},
          {"trappist_1e_slate_hills", "TRAPPIST_1E"},
          {"trappist_1e_violet_meadows", "TRAPPIST_1E"},
          {"trappist_1f_crevasse_fields", "TRAPPIST_1F"},
          {"trappist_1f_cryovolcanic_ridges", "TRAPPIST_1F"},
          {"trappist_1f_frozen_sea", "TRAPPIST_1F"},
          {"trappist_1f_ice_sheet", "TRAPPIST_1F"},
          {"cantaloupe_terrain", "TRITON"},
          {"ruach_planitia", "TRITON"},
          {"triton_polar_cap", "TRITON"},
          {"venus_ash_fields", "VENUS"},
          {"venus_coronae", "VENUS"},
          {"venus_lava_channels", "VENUS"},
          {"venus_lava_plains", "VENUS"},
          {"venus_tesserae", "VENUS"},
          {"venus_volcanic_lowlands", "VENUS"},
          {"draugr_beam_scars", "DRAUGR"},
          {"draugr_magnetic_plains", "DRAUGR"},
          {"poltergeist_storm_plains", "POLTERGEIST"},
          {"poltergeist_aurora_glaciers", "POLTERGEIST"},
          {"phobetor_pulse_ice_sheet", "PHOBETOR"},
          {"phobetor_frozen_ridges", "PHOBETOR"},
          {"van_maanen_b_diamond_fields", "VAN_MAANEN_B"},
          {"van_maanen_b_graphite_flats", "VAN_MAANEN_B"},
          {"van_maanen_c_iron_cliffs", "VAN_MAANEN_C"},
          {"van_maanen_c_filing_plains", "VAN_MAANEN_C"},
          {"van_maanen_d_nitrogen_sheets", "VAN_MAANEN_D"},
          {"van_maanen_d_fallen_sky", "VAN_MAANEN_D"},
          {"gaia_bh1_b_disk_glow_meadows", "GAIA_BH1_B"},
          {"gaia_bh1_b_lensed_hills", "GAIA_BH1_B"},
          {"gaia_bh1_c_torn_crust", "GAIA_BH1_C"},
          {"gaia_bh1_c_lava_tides", "GAIA_BH1_C"},
          {"gaia_bh1_d_dark_lens_ocean", "GAIA_BH1_D"},
          {"gaia_bh1_d_rim_archipelago", "GAIA_BH1_D"},
          {"divalia_fossae", "VESTA"},
          {"rheasilvia_basin", "VESTA"},
          {"vesta_dark_terrain", "VESTA"},
          {"vesta_eucrite_plains", "VESTA"}
    };

    /** Chemin du biome (espace de noms pioneers) -> astre. */
    public static final Map<String, Body> BODIES;

    static {
        Map<String, Body> map = new LinkedHashMap<>();
        for (String[] biome : BIOMES) {
            map.put(biome[0], Body.valueOf(biome[1]));
        }
        BODIES = Collections.unmodifiableMap(map);
    }

    /** Astre d'un biome ({@code pioneers:lunar_maria}), null si ce n'est pas un biome d'astre du mod. */
    @Nullable
    public static Body bodyOf(String biome) {
        String prefix = "pioneers:";
        return biome.startsWith(prefix) ? BODIES.get(biome.substring(prefix.length())) : null;
    }
}
