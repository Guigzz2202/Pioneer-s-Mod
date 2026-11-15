package fr.pioneers.guide;

import java.util.List;

/**
 * GENERE par tools/gc/guide/journey_gen.py (ne pas modifier a la main ; source : le Periple, tools/gc/guide/guide_journey.py). Les etapes du
 * Periple dans l'ordre du guide (1.0.3-beta, lot 2) : progres coche ({@code pioneers:journey/<chapitre>_<nn>}, decerne par
 * {@link JourneyTracker}), chapitre, numero, progres d'evenement exact qui fait l'etape (vide : condition « objet ») et objets du but (l'un
 * d'eux dans l'inventaire fait l'etape quand il n'y a pas de progres d'evenement ; sinon, objets montres par le HUD).
 */
public final class JourneySteps {

    /** Une etape du Periple. */
    public record Step(String advancement, String chapter, int number, String legacy, List<String> goal) {

        /** Vrai si l'etape se fait par un progres d'evenement (machine en marche, multibloc forme, lancement...). */
        public boolean byEvent() {
            return !legacy.isEmpty();
        }
    }

    public static final List<Step> ALL = List.of(
        new Step("pioneers:journey/j_primitive_01", "j_primitive", 1, "", List.of("pioneers:plant_fiber")),
        new Step("pioneers:journey/j_primitive_02", "j_primitive", 2, "", List.of("pioneers:mechanical_sifter")),
        new Step("pioneers:journey/j_primitive_03", "j_primitive", 3, "", List.of("pioneers:flint_knife", "pioneers:flint_pickaxe", "pioneers:flint_axe", "pioneers:flint_saw")),
        new Step("pioneers:journey/j_primitive_04", "j_primitive", 4, "", List.of("minecraft:campfire")),
        new Step("pioneers:journey/j_primitive_05", "j_primitive", 5, "", List.of("pioneers:stone_hammer", "pioneers:stone_mortar")),
        new Step("pioneers:journey/j_primitive_06", "j_primitive", 6, "", List.of("pioneers:raw_copper", "pioneers:raw_tin", "pioneers:raw_iron")),
        new Step("pioneers:journey/j_primitive_07", "j_primitive", 7, "", List.of("pioneers:mason_bench")),
        new Step("pioneers:journey/j_primitive_08", "j_primitive", 8, "", List.of("pioneers:ingot_mold", "pioneers:clay_crucible")),
        new Step("pioneers:journey/j_primitive_09", "j_primitive", 9, "pioneers:bronze/cast", List.of("pioneers:copper_ingot", "pioneers:tin_ingot", "pioneers:iron_ingot", "pioneers:bronze_ingot")),
        new Step("pioneers:journey/j_primitive_10", "j_primitive", 10, "pioneers:bronze/forged", List.of("pioneers:copper_pickaxe", "pioneers:iron_anvil")),
        new Step("pioneers:journey/j_mechanical_01", "j_mechanical", 1, "", List.of("pioneers:iron_shaft", "pioneers:iron_flywheel")),
        new Step("pioneers:journey/j_mechanical_02", "j_mechanical", 2, "", List.of("pioneers:windmill_hub", "pioneers:gearbox")),
        new Step("pioneers:journey/j_mechanical_03", "j_mechanical", 3, "", List.of("pioneers:wooden_fluid_buffer")),
        new Step("pioneers:journey/j_mechanical_04", "j_mechanical", 4, "", List.of("pioneers:millstone", "pioneers:jaw_crusher", "pioneers:mechanical_saw", "pioneers:mechanical_drill")),
        new Step("pioneers:journey/j_mechanical_05", "j_mechanical", 5, "", List.of("pioneers:wooden_logistic_cable")),
        new Step("pioneers:journey/j_mechanical_06", "j_mechanical", 6, "pioneers:mechanics/beam_engine", List.of("pioneers:beam_engine")),
        new Step("pioneers:journey/j_bronze_01", "j_bronze", 1, "", List.of("pioneers:crucible_furnace", "pioneers:casting_table")),
        new Step("pioneers:journey/j_bronze_02", "j_bronze", 2, "pioneers:bronze/brass", List.of("pioneers:brass_ingot", "pioneers:solder_ingot")),
        new Step("pioneers:journey/j_bronze_03", "j_bronze", 3, "pioneers:bronze/boiler", List.of("pioneers:steam_dome", "pioneers:copper_boiler_shell")),
        new Step("pioneers:journey/j_bronze_04", "j_bronze", 4, "pioneers:bronze/steam_engine", List.of("pioneers:steam_engine")),
        new Step("pioneers:journey/j_bronze_05", "j_bronze", 5, "pioneers:bronze/steam_macerator", List.of("pioneers:steam_macerator", "pioneers:steam_furnace", "pioneers:steam_rolling_mill", "pioneers:steam_press", "pioneers:steam_sifter")),
        new Step("pioneers:journey/j_bronze_06", "j_bronze", 6, "pioneers:bronze/drill_formed", List.of("pioneers:steam_drill")),
        new Step("pioneers:journey/j_bronze_07", "j_bronze", 7, "pioneers:bronze/steel", List.of("pioneers:steel_ingot")),
        new Step("pioneers:journey/j_bronze_08", "j_bronze", 8, "", List.of("pioneers:primitive_plastic")),
        new Step("pioneers:journey/j_industrial_01", "j_industrial", 1, "", List.of("pioneers:assembly_bench", "pioneers:advanced_components_bench")),
        new Step("pioneers:journey/j_industrial_02", "j_industrial", 2, "", List.of("pioneers:coke")),
        new Step("pioneers:journey/j_industrial_03", "j_industrial", 3, "", List.of("pioneers:machine_hull_basic")),
        new Step("pioneers:journey/j_industrial_04", "j_industrial", 4, "", List.of("pioneers:basic_dynamo", "pioneers:basic_solid_combustion_generator")),
        new Step("pioneers:journey/j_industrial_05", "j_industrial", 5, "", List.of("pioneers:basic_casting_machine")),
        new Step("pioneers:journey/j_industrial_06", "j_industrial", 6, "pioneers:industry/ball_mill", List.of("pioneers:basic_crusher")),
        new Step("pioneers:journey/j_industrial_07", "j_industrial", 7, "pioneers:industry/formed", List.of("pioneers:industrial_crusher", "pioneers:industrial_casing_basic")),
        new Step("pioneers:journey/j_industrial_08", "j_industrial", 8, "pioneers:industry/drill", List.of("pioneers:drill")),
        new Step("pioneers:journey/j_industrial_09", "j_industrial", 9, "", List.of("pioneers:basic_ore_separator")),
        new Step("pioneers:journey/j_industrial_10", "j_industrial", 10, "", List.of("pioneers:silver_ingot")),
        new Step("pioneers:journey/j_chemistry_01", "j_chemistry", 1, "pioneers:chemistry/pumpjack", List.of("pioneers:industrial_pumpjack", "pioneers:offshore_platform")),
        new Step("pioneers:journey/j_chemistry_02", "j_chemistry", 2, "pioneers:chemistry/cracker", List.of("pioneers:distillation_column", "pioneers:industrial_cracker")),
        new Step("pioneers:journey/j_chemistry_03", "j_chemistry", 3, "", List.of("pioneers:plastic_sheet")),
        new Step("pioneers:journey/j_chemistry_04", "j_chemistry", 4, "pioneers:agri/hydroponics", List.of("pioneers:hydroponic_farm", "pioneers:basic_extractor")),
        new Step("pioneers:journey/j_chemistry_05", "j_chemistry", 5, "pioneers:industry/aluminium", List.of("pioneers:aluminium_ingot")),
        new Step("pioneers:journey/j_chemistry_06", "j_chemistry", 6, "", List.of("pioneers:gasket")),
        new Step("pioneers:journey/j_chemistry_07", "j_chemistry", 7, "", List.of("pioneers:basic_electrolyzer")),
        new Step("pioneers:journey/j_chemistry_08", "j_chemistry", 8, "", List.of("pioneers:voltaic_chestplate", "pioneers:voltaic_drill", "pioneers:hydrogen_jetpack", "pioneers:flamethrower")),
        new Step("pioneers:journey/j_chemistry_09", "j_chemistry", 9, "", List.of("pioneers:microchip", "pioneers:circuit_mk2")),
        new Step("pioneers:journey/j_chemistry_10", "j_chemistry", 10, "", List.of("pioneers:machine_frame_advanced", "pioneers:industrial_casing_advanced")),
        new Step("pioneers:journey/j_atom_01", "j_atom", 1, "", List.of("pioneers:yellowcake")),
        new Step("pioneers:journey/j_atom_02", "j_atom", 2, "pioneers:industry/titanium", List.of("pioneers:stainless_steel_ingot", "pioneers:titanium_ingot")),
        new Step("pioneers:journey/j_atom_03", "j_atom", 3, "", List.of("pioneers:processor")),
        new Step("pioneers:journey/j_atom_04", "j_atom", 4, "", List.of("pioneers:computer")),
        new Step("pioneers:journey/j_atom_05", "j_atom", 5, "", List.of("pioneers:programmed_flight_computer")),
        new Step("pioneers:journey/j_atom_06", "j_atom", 6, "pioneers:nuclear/data_center", List.of("pioneers:data_center")),
        new Step("pioneers:journey/j_atom_07", "j_atom", 7, "", List.of("pioneers:research_table")),
        new Step("pioneers:journey/j_atom_08", "j_atom", 8, "pioneers:space/integration_hall", List.of("pioneers:launch_pad_core", "pioneers:rocket_integration_hall")),
        new Step("pioneers:journey/j_atom_09", "j_atom", 9, "pioneers:space/first_probe", List.of("pioneers:rocket_tier_1", "pioneers:rocket_module_probe")),
        new Step("pioneers:journey/j_atom_10", "j_atom", 10, "pioneers:space/first_flight", List.of("pioneers:space_suit_helmet", "pioneers:rocket_module_passenger")),
        new Step("pioneers:journey/j_atom_11", "j_atom", 11, "", List.of("pioneers:fuel_pellet", "pioneers:fuel_rod", "pioneers:fuel_assembly")),
        new Step("pioneers:journey/j_atom_12", "j_atom", 12, "pioneers:nuclear/criticality", List.of("pioneers:fission_reactor")),
        new Step("pioneers:journey/j_space_01", "j_space", 1, "", List.of("pioneers:lunar_alloy_ingot")),
        new Step("pioneers:journey/j_space_02", "j_space", 2, "pioneers:space/robotic_bay", List.of("pioneers:robot_mk1")),
        new Step("pioneers:journey/j_space_03", "j_space", 3, "", List.of("pioneers:tharsium_magnet")),
        new Step("pioneers:journey/j_endgame_01", "j_endgame", 1, "", List.of("pioneers:stellar_core")),
        new Step("pioneers:journey/j_endgame_02", "j_endgame", 2, "", List.of("pioneers:empty_simulated_earth", "pioneers:empty_simulated_mars", "pioneers:empty_simulated_venus")),
        new Step("pioneers:journey/j_endgame_03", "j_endgame", 3, "", List.of("pioneers:aerostat_kit", "pioneers:drone_bay")),
        new Step("pioneers:journey/j_endgame_04", "j_endgame", 4, "", List.of("pioneers:space_stabilizer_controller")),
        new Step("pioneers:journey/j_endgame_05", "j_endgame", 5, "", List.of("pioneers:singularity_core")),
        new Step("pioneers:journey/j_endgame_06", "j_endgame", 6, "", List.of("pioneers:space_simulator_controller"))
    );

    private JourneySteps() {
    }
}
