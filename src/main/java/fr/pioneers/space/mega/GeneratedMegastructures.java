// FICHIER GENERE par tools/gc/au_dela/megastructures.py (java) : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.space.mega;

/**
 * 1.0.7 (« Plus loin et au-dela ») : les megastructures, dans l'ordre de progression (source : T:au_dela/megastructures.py).
 * Une ligne par megastructure ; lue par {@link Megastructures} (et par le plan des onglets creatifs : aucune classe Minecraft ici).
 */
public final class GeneratedMegastructures {

    private GeneratedMegastructures() {
    }

    public static final String[] IDS = {"mars_elevator", "mercury_diadem", "venus_great_drill", "jupiter_cloud_city"};
    /** Astre (nom de l'enum SolarSystem.Body). */
    public static final String[] BODIES = {"MARS", "MERCURY", "VENUS", "JUPITER"};
    /** Cle du multibloc de port (IndustryMachine). */
    public static final String[] PORTS = {"mars_elevator_port", "mercury_diadem_port", "venus_great_drill_port", "jupiter_cloud_city_port"};
    public static final String[][] INGREDIENTS = {{"pioneers:martian_hull_segment", "pioneers:tharsium_truss_beam", "pioneers:phobos_deimos_forged_frame", "pioneers:belt_structural_node", "pioneers:deimos_spine_cable", "pioneers:isostatic_preform", "pioneers:superalloy_seed_crystal", "pioneers:mercurian_refractory_brick", "pioneers:supercritical_exchanger_core", "pioneers:ember_thermal_cell", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis"}, {"pioneers:superalloy_seed_crystal", "pioneers:heliothermal_absorber", "pioneers:mercurian_refractory_brick", "pioneers:superalloy_melt_charge", "pioneers:martian_hull_segment", "pioneers:isostatic_preform", "pioneers:hts_tape", "pioneers:frost_capacitor_cell", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis", "pioneers:photonic_glass"}, {"pioneers:supercritical_exchanger_core", "pioneers:acidproof_venusium_liner", "pioneers:venusian_spiral_gasket", "pioneers:ember_thermal_cell", "pioneers:phobos_deimos_forged_frame", "pioneers:isostatic_preform", "pioneers:mercurian_refractory_brick", "pioneers:superalloy_melt_charge", "pioneers:continuous_orbital_beam", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis"}, {"pioneers:jovian_pressure_vessel", "pioneers:jovian_catalyst_cartridge", "pioneers:europan_cryo_scrubber", "pioneers:io_sulfide_catalyst", "pioneers:ganymede_molecular_sieve", "pioneers:ember_thermal_cell", "pioneers:tharsium_truss_beam", "pioneers:martian_hull_segment", "pioneers:ice_melt_exchanger", "pioneers:titan_magnetic_bearing", "pioneers:mantle_heat_core", "pioneers:continuous_orbital_beam", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis"}};
    public static final int[][] COUNTS = {{96, 192, 48, 64, 128, 32, 24, 96, 24, 48, 64, 12}, {32, 48, 128, 48, 64, 32, 64, 24, 64, 12, 64}, {48, 96, 192, 64, 32, 32, 96, 24, 6, 64, 12}, {48, 96, 48, 128, 64, 32, 128, 48, 32, 24, 8, 12, 64, 12}};
    /** Composants dont la megastructure est la seule source. */
    public static final String[][] GATED = {{"pioneers:colossal_drill_mast", "pioneers:continuous_orbital_beam", "pioneers:interstellar_hull"}, {"pioneers:machine_scale_monocrystal_blade", "pioneers:active_heliothermal_mirror", "pioneers:ultimate_refractory_crucible"}, {"pioneers:mantle_drill_bit", "pioneers:sealed_mantle_exchanger", "pioneers:mantle_heat_core"}, {"pioneers:stabilized_metallic_hydrogen", "pioneers:exotic_catalyst", "pioneers:capture_membrane"}};
    /** Geometrie du ciel (index dans SkyScene.MEGA_KINDS : tether, ring, plume, lights) et ses reglages. */
    public static final int[] SKY_KIND = {0, 1, 2, 3};
    public static final double[][] SKY = {{170.0, 84.0, 0.22, 17000.0, 3390.0, 0.85}, {180.0, 52.0, 0.35, 4400.0, 2440.0, 0.9, 14.0, 0.45}, {200.0, 14.0, 38.0, 2.6, 0.9}, {28.0, -4.0, 9.0, 0.35, 0.6, 3.0}};
    public static final int[] COLOR = {0xFFD6AA, 0xFFECBE, 0xFF783C, 0xFFC878};
    /** Coeurs MKV des multiblocs de famille (T:au_dela/multiblocs.py CORES) : objet, puis cles des multiblocs qu'il fait passer au MKV. */
    public static final String[] CORE_ITEMS = {"pioneers:forming_mkv_core", "pioneers:thermal_mkv_core", "pioneers:energy_mkv_core", "pioneers:chemistry_mkv_core"};
    public static final String[][] CORE_MACHINES = {{"sawmill", "machining_center", "rolling_mill", "wiremill", "press", "power_hammer", "stoneworks"}, {"furnace", "electric_crucible"}, {"extractor"}, {"mixer"}};
    /** Multiblocs de la MAJ hors ports (inedits, foreuse spatiale) : onglet Espace. */
    public static final String[] MACHINES = {"space_drill", "gas_collector", "solar_concentrator", "isostatic_press"};
    /** Objets des chaines phares (T:au_dela/multiblocs.py ITEMS) : onglet Espace. */
    public static final String[] ITEMS = {"pioneers:superalloy_melt_charge", "pioneers:isostatic_preform"};
    /** Collecteur de gaz (T:au_dela/multiblocs.py GAS_MIX) : geante, gaz captes (ids du registre de gaz) et mB par operation. */
    public static final String[] GAS_BODIES = {"JUPITER", "SATURN", "URANUS", "NEPTUNE"};
    public static final String[][] GAS_IDS = {{"pioneers:hydrogen", "pioneers:ammonia", "pioneers:helium_3"}, {"pioneers:hydrogen", "pioneers:helium_3", "pioneers:ammonia"}, {"pioneers:hydrogen", "pioneers:methane", "pioneers:helium_3"}, {"pioneers:hydrogen", "pioneers:methane", "pioneers:ammonia"}};
    public static final int[][] GAS_AMOUNTS = {{600, 120, 40}, {560, 80, 60}, {420, 200, 30}, {400, 160, 120}};
}
