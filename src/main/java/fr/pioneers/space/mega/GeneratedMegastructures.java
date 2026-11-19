// FICHIER GENERE par tools/gc/au_dela/megastructures.py (java) : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.space.mega;

/**
 * 1.0.7 (« Plus loin et au-dela ») : les megastructures, dans l'ordre de progression (source : T:au_dela/megastructures.py).
 * Une ligne par megastructure ; lue par {@link Megastructures} (et par le plan des onglets creatifs : aucune classe Minecraft ici).
 */
public final class GeneratedMegastructures {

    private GeneratedMegastructures() {
    }

    public static final String[] IDS = {"mars_elevator", "mercury_diadem"};
    /** Astre (nom de l'enum SolarSystem.Body). */
    public static final String[] BODIES = {"MARS", "MERCURY"};
    /** Cle du multibloc de port (IndustryMachine). */
    public static final String[] PORTS = {"mars_elevator_port", "mercury_diadem_port"};
    public static final String[][] INGREDIENTS = {{"pioneers:martian_hull_segment", "pioneers:tharsium_truss_beam", "pioneers:phobos_deimos_forged_frame", "pioneers:belt_structural_node", "pioneers:deimos_spine_cable", "pioneers:isostatic_preform", "pioneers:superalloy_seed_crystal", "pioneers:mercurian_refractory_brick", "pioneers:supercritical_exchanger_core", "pioneers:ember_thermal_cell", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis"}, {"pioneers:superalloy_seed_crystal", "pioneers:heliothermal_absorber", "pioneers:mercurian_refractory_brick", "pioneers:superalloy_melt_charge", "pioneers:martian_hull_segment", "pioneers:isostatic_preform", "pioneers:hts_tape", "pioneers:frost_capacitor_cell", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis", "pioneers:photonic_glass"}};
    public static final int[][] COUNTS = {{96, 192, 48, 64, 128, 32, 24, 96, 24, 48, 64, 12}, {32, 48, 128, 48, 64, 32, 64, 24, 64, 12, 64}};
    /** Composants dont la megastructure est la seule source. */
    public static final String[][] GATED = {{"pioneers:colossal_drill_mast", "pioneers:continuous_orbital_beam", "pioneers:interstellar_hull"}, {"pioneers:machine_scale_monocrystal_blade", "pioneers:active_heliothermal_mirror", "pioneers:ultimate_refractory_crucible"}};
    /** Geometrie du ciel (index dans SkyScene.MEGA_KINDS : tether, ring) et ses reglages. */
    public static final int[] SKY_KIND = {0, 1};
    public static final double[][] SKY = {{170.0, 84.0, 0.22, 17000.0, 3390.0, 0.85}, {180.0, 52.0, 0.35, 4400.0, 2440.0, 0.9, 14.0, 0.45}};
    public static final int[] COLOR = {0xFFD6AA, 0xFFECBE};
    /** Coeurs MKV des multiblocs de famille (T:au_dela/multiblocs.py CORES) : objet, puis cles des multiblocs qu'il fait passer au MKV. */
    public static final String[] CORE_ITEMS = {"pioneers:forming_mkv_core", "pioneers:thermal_mkv_core"};
    public static final String[][] CORE_MACHINES = {{"sawmill", "machining_center", "rolling_mill", "wiremill", "press", "power_hammer", "stoneworks"}, {"furnace", "electric_crucible"}};
}
