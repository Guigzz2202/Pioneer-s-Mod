// FICHIER GENERE par tools/gc/au_dela/megastructures.py (java) : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.space.mega;

/**
 * 1.0.7 (« Plus loin et au-dela ») : les megastructures, dans l'ordre de progression (source : T:au_dela/megastructures.py).
 * Une ligne par megastructure ; lue par {@link Megastructures} (et par le plan des onglets creatifs : aucune classe Minecraft ici).
 */
public final class GeneratedMegastructures {

    private GeneratedMegastructures() {
    }

    public static final String[] IDS = {"mars_elevator"};
    /** Astre (nom de l'enum SolarSystem.Body). */
    public static final String[] BODIES = {"MARS"};
    /** Cle du multibloc de port (IndustryMachine). */
    public static final String[] PORTS = {"mars_elevator_port"};
    public static final String[][] INGREDIENTS = {{"pioneers:martian_hull_segment", "pioneers:tharsium_truss_beam", "pioneers:phobos_deimos_forged_frame", "pioneers:belt_structural_node", "pioneers:deimos_spine_cable", "pioneers:superalloy_seed_crystal", "pioneers:mercurian_refractory_brick", "pioneers:supercritical_exchanger_core", "pioneers:ember_thermal_cell", "pioneers:industrial_casing_stellar", "pioneers:stellar_alloy_chassis"}};
    public static final int[][] COUNTS = {{96, 192, 48, 64, 128, 24, 96, 24, 48, 64, 12}};
    /** Composants dont la megastructure est la seule source. */
    public static final String[][] GATED = {{"pioneers:colossal_drill_mast", "pioneers:continuous_orbital_beam", "pioneers:interstellar_hull"}};
    /** Geometrie du ciel (index dans SkyScene.MEGA_KINDS : tether) et ses reglages. */
    public static final int[] SKY_KIND = {0};
    public static final double[][] SKY = {{170.0, 84.0, 0.22, 17000.0, 3390.0, 0.85}};
    public static final int[] COLOR = {0xFFD6AA};
}
