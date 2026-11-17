package fr.pioneers.tech.industry.recipe;

import java.util.List;
import java.util.Map;

/**
 * GENERE par tools/gc/core/p105_lot44.py (NE PAS MODIFIER A LA MAIN) : 1.0.5-beta (lot 44, retour 62) : objets REMANENTS (outillage rendu
 * intact ou a l'usure par sa recette : matrices, filiere et formes d'extrusion, catalyseurs...) de chaque machine a CASE « OUTIL » (cle du
 * catalogue industriel, ou {@code steam:<machine>}). Lue par {@link Remanents}.
 */
public final class GeneratedRemanents {

    /** Machine -> ses remanents (identifiants), dans l'ordre alphabetique ; une machine absente n'a pas de case Outil. */
    public static final Map<String, List<String>> BY_MACHINE = Map.ofEntries(
          Map.entry("assembler", List.of("pioneers:diamond_disc")),
          Map.entry("casting_machine", List.of("pioneers:ceramic_shell_mold", "pioneers:gear_die", "pioneers:ingot_die", "pioneers:plate_die", "pioneers:press_die", "pioneers:rod_die", "pioneers:sand_mold", "pioneers:tube_die", "pioneers:vacuum_casting_mold")),
          Map.entry("chemical_reactor", List.of("pioneers:cobalt_catalyst", "pioneers:iodine", "pioneers:iron_catalyst", "pioneers:nickel_catalyst", "pioneers:platinum_catalyst", "pioneers:vanadium_catalyst", "pioneers:zeolite_catalyst")),
          Map.entry("cracker", List.of("pioneers:nickel_catalyst", "pioneers:zeolite_catalyst")),
          Map.entry("electric_crucible", List.of("pioneers:fiber_laser", "pioneers:iridium_crucible")),
          Map.entry("extruder", List.of("pioneers:extrusion_die", "pioneers:extrusion_shape_pipe", "pioneers:extrusion_shape_rod", "pioneers:rod_die", "pioneers:tube_die")),
          Map.entry("gas_separator", List.of("pioneers:adsorbent")),
          Map.entry("hot_cell", List.of("pioneers:neutron_source")),
          Map.entry("photolithograph", List.of("pioneers:euv_engraving_objective", "pioneers:glass_engraving_objective", "pioneers:uv_engraving_objective")),
          Map.entry("power_hammer", List.of("pioneers:press_die", "pioneers:rod_die")),
          Map.entry("press", List.of("pioneers:plate_die", "pioneers:press_die")),
          Map.entry("rolling_mill", List.of("pioneers:press_die")),
          Map.entry("semiconductor_fab", List.of("pioneers:photomask")),
          Map.entry("steam:press", List.of("pioneers:plate_die")));

    /** Remanent -> sorte (die, extrusion, tool, catalyst ; {@code objective} : s'use, WearTool). */
    public static final Map<String, String> KIND = Map.ofEntries(
          Map.entry("pioneers:adsorbent", "catalyst"),
          Map.entry("pioneers:ceramic_shell_mold", "mold"),
          Map.entry("pioneers:cobalt_catalyst", "catalyst"),
          Map.entry("pioneers:diamond_disc", "tool"),
          Map.entry("pioneers:euv_engraving_objective", "objective"),
          Map.entry("pioneers:extrusion_die", "extrusion"),
          Map.entry("pioneers:extrusion_shape_pipe", "extrusion"),
          Map.entry("pioneers:extrusion_shape_rod", "extrusion"),
          Map.entry("pioneers:fiber_laser", "tool"),
          Map.entry("pioneers:gear_die", "die"),
          Map.entry("pioneers:glass_engraving_objective", "objective"),
          Map.entry("pioneers:ingot_die", "die"),
          Map.entry("pioneers:iodine", "catalyst"),
          Map.entry("pioneers:iridium_crucible", "tool"),
          Map.entry("pioneers:iron_catalyst", "catalyst"),
          Map.entry("pioneers:neutron_source", "tool"),
          Map.entry("pioneers:nickel_catalyst", "catalyst"),
          Map.entry("pioneers:photomask", "tool"),
          Map.entry("pioneers:plate_die", "die"),
          Map.entry("pioneers:platinum_catalyst", "catalyst"),
          Map.entry("pioneers:press_die", "die"),
          Map.entry("pioneers:rod_die", "die"),
          Map.entry("pioneers:sand_mold", "mold"),
          Map.entry("pioneers:tube_die", "die"),
          Map.entry("pioneers:uv_engraving_objective", "objective"),
          Map.entry("pioneers:vacuum_casting_mold", "mold"),
          Map.entry("pioneers:vanadium_catalyst", "catalyst"),
          Map.entry("pioneers:zeolite_catalyst", "catalyst"));

    private GeneratedRemanents() {
    }
}
