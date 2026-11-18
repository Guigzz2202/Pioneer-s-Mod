package fr.pioneers.core.common;

import fr.pioneers.Pioneers;
import fr.pioneers.space.content.spacesim.SpaceSimPart;
import fr.pioneers.space.content.stabilizer.StabilizerPart;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.logistics.rail.PiRail;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.content.PiStabilizer;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.space.assembly.PiWorksMachines;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.space.assembly.AssemblyHall;
import fr.pioneers.logistics.transport.legacy.LegacyCableBlock;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Anciens identifiants des mondes existants : le cable optique d'avant les tiers devient le cable basique, le data drive devient
 * l'analyseur de biomes ; le coprocesseur quantique (supprime) disparait sans erreur ;
 * les anciens robots des bases (rover minier, robot agricole, drone collecteur) deviennent des unites robotiques Mk1.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PiRemaps {

    private PiRemaps() {
    }

    /** 0.11 : Chambre de stabilisation et Chambre stellaire reunies dans le Stabilisateur spatial. */
    private static final Map<String, StabilizerPart> OLD_CHAMBER = Map.ofEntries(
          Map.entry("stabilization_chamber_casing", StabilizerPart.CASING), Map.entry("stellar_chamber_casing", StabilizerPart.CASING),
          Map.entry("stabilization_chamber_glass", StabilizerPart.GLASS), Map.entry("stellar_chamber_glass", StabilizerPart.GLASS),
          Map.entry("stabilization_chamber_port", StabilizerPart.PORT), Map.entry("stellar_chamber_port", StabilizerPart.PORT),
          //Plus de matrice de focalisation : l'allumage puise dans les ports d'alimentation
          Map.entry("stabilization_chamber_laser_focus_matrix", StabilizerPart.CASING), Map.entry("stellar_chamber_laser_focus_matrix", StabilizerPart.CASING),
          Map.entry("space_stabilizer_focus_matrix", StabilizerPart.CASING),
          Map.entry("stabilizer_power_port", StabilizerPart.POWER_PORT), Map.entry("stellar_power_port", StabilizerPart.POWER_PORT),
          Map.entry("stabilizer", StabilizerPart.EMITTER), Map.entry("stellar_stabilizer", StabilizerPart.EMITTER));

    /** 0.11 : Simulateur du systeme solaire et Simulateur d'univers reunis dans le Simulateur spatial (les anneaux decoratifs deviennent du boitier). */
    private static final Map<String, SpaceSimPart> OLD_SIMULATOR = Map.ofEntries(
          Map.entry("solar_simulator_casing", SpaceSimPart.CASING),
          Map.entry("universe_simulator_casing", SpaceSimPart.CASING),
          Map.entry("solar_simulator_glass", SpaceSimPart.GLASS),
          Map.entry("universe_simulator_glass", SpaceSimPart.GLASS),
          Map.entry("solar_simulator_port", SpaceSimPart.PORT),
          Map.entry("universe_simulator_port", SpaceSimPart.PORT),
          Map.entry("solar_simulator_emitter", SpaceSimPart.PROJECTOR),
          Map.entry("universe_simulator_projector", SpaceSimPart.PROJECTOR),
          Map.entry("solar_simulator_ring", SpaceSimPart.CASING),
          Map.entry("universe_simulator_ring", SpaceSimPart.CASING));

    /**
     * 1.0 : machines fusionnees ou remplacees -> leur remplacant (meme tier) : generateur thermique du kit et groupe electrogene -> generateur
     * a combustion ; collecteur de gaz -> unite de separation de l'air ; doublons de la chimie (voir MERGED_MACHINES).
     */
    @Nullable
    private static ResourceLocation merged(String path) {
        Matcher m = Pattern.compile("^(basic|advanced|elite|ultimate|industrial)_([a-z_]+)$").matcher(path);
        if (m.matches() && MERGED_MACHINES.containsKey(m.group(2))) {
            return Pioneers.rl(m.group(1) + "_" + MERGED_MACHINES.get(m.group(2)));
        }
        //1.7 (refonte des composants) : vis et boulons des metaux hors tier -> visserie du tier de leur age (acier, aluminium, titane)
        Matcher f = Pattern.compile("^([a-z_]+)_(bolt|screw)$").matcher(path);
        if (f.matches() && RETIRED_FASTENERS.containsKey(f.group(1))) {
            return Pioneers.rl(RETIRED_FASTENERS.get(f.group(1)) + "_" + f.group(2));
        }
        return switch (path) {
            case "gas_collector" -> Pioneers.rl("basic_electrolyzer");
            //1.0 (lot 4) : bases construites en carcasses, cadres et toles : les anciennes coques deviennent carcasses et verre industriels
            case "greenhouse_casing", "outpost_casing", "outpost_plating" -> Pioneers.rl("industrial_casing_basic");
            case "greenhouse_glass", "outpost_glass" -> Pioneers.rl("industrial_glass");
            case "industrial_steam_port" -> Pioneers.rl("industrial_fluid_port");
            //0.20.28 : le hublot est retire ; il devient une vitre industrielle (MKI)
            case "industrial_porthole" -> Pioneers.rl("industrial_glass");
            //MAJ Recettes (lot 6) : poutre, charpente et fondation industrielles des formes fixes (0.19.2, refusees par Guillaume, retirees
            //sans remap) : aucun multibloc ne s'en sert, elles deviennent le cadre du MKI
            case "industrial_beam", "industrial_frame", "industrial_foundation" -> Pioneers.rl("steel_frame");
            //1.2.0 : un seul tamis (toile de fibre) ; MAJ Recettes (lot 4a, spec « Les 89 formes sans usage ») : l'ancienne grille de cuivre
            //devient le grillage de cuivre (meme matiere ; les piles deja converties en toile de fibre le restent)
            case "copper_mesh" -> Pioneers.rl("copper_wire_mesh");
            //1.0 (lot 4) : petites machines spatiales en 4 tiers, l'ancienne devient le MKI
            case "chunk_loader", "force_field_generator", "lightning_collector", "oxygen_diffuser" -> Pioneers.rl("basic_" + path);
            //0.20.34 : compresseur gravitationnel et synthetiseur planetaire devenus multiblocs : leur controleur
            case "basic_gravity_compressor", "advanced_gravity_compressor", "elite_gravity_compressor", "ultimate_gravity_compressor" ->
                  Pioneers.rl("gravity_compressor");
            case "planet_synthesizer" -> Pioneers.rl("synthesizer");
            //1.0.4-beta (lot 12) : l'electrolyseur a membrane est fondu dans la cellule d'electrolyse (GeneratedFusions.REMAP)
            case "chlor_alkali_plant" -> Pioneers.rl("industrial_electrolyzer");
            //1.7 (refonte des composants) : les 13 plaques de fusee deviennent le panneau de fuselage de leur bande de tiers
            case "rocket_plate_tier_1", "rocket_plate_tier_2" -> Pioneers.rl("aluminium_fuselage_panel");
            case "rocket_plate_tier_3", "rocket_plate_tier_4", "rocket_plate_tier_5", "rocket_plate_tier_6", "rocket_plate_tier_7" ->
                  Pioneers.rl("carbon_fuselage_panel");
            case "rocket_plate_tier_8", "rocket_plate_tier_9", "rocket_plate_tier_10" -> Pioneers.rl("ceramic_fuselage_panel");
            case "rocket_plate_tier_11", "rocket_plate_tier_12", "rocket_plate_tier_13" -> Pioneers.rl("stellar_fuselage_panel");
            //1.0.4-beta (lot 12) : le polymeriseur est fondu dans le reacteur chimique (GeneratedFusions.REMAP)
            case "polymerization_plant" -> Pioneers.rl("industrial_chemical_reactor");
            //1.9.2 (retours de Guillaume) : assembleur de fusees et table robotique devenus multiblocs : leur controleur
            case "rocket_assembler" -> Pioneers.rl("rocket_integration_hall");
            case "rover_assembly_table" -> Pioneers.rl("robotic_assembly_bay");
            default -> retiredMold(path);
        };
    }

    /** Forge primaire (0.21) : moules de tetes d'outils retires -> moule a lingot (cru ou cuit). */
    private static ResourceLocation retiredMold(String path) {
        for (String shape : PiBronze.RETIRED_MOLD_SHAPES) {
            if (path.equals(shape + "_mold")) {
                return Pioneers.rl("ingot_mold");
            }
            if (path.equals("unfired_" + shape + "_mold")) {
                return Pioneers.rl("unfired_ingot_mold");
            }
        }
        return null;
    }

    /** 1.7 : metal dont les vis et boulons sont retires -> metal de tier dont la visserie les remplace. */
    private static final Map<String, String> RETIRED_FASTENERS = Map.ofEntries(Map.entry("iron", "steel"), Map.entry("copper", "steel"),
          Map.entry("gold", "steel"), Map.entry("tin", "steel"), Map.entry("bronze", "steel"), Map.entry("brass", "steel"), Map.entry("nickel", "steel"),
          Map.entry("silver", "steel"), Map.entry("invar", "steel"), Map.entry("electrum", "steel"), Map.entry("constantan", "steel"),
          Map.entry("galvanized_steel", "steel"), Map.entry("vanadium_steel", "steel"), Map.entry("duralumin", "aluminium"),
          Map.entry("nichrome", "titanium"), Map.entry("stainless_steel", "titanium"));

    /** 1.0 : machines reunies (plus de doublons) -> machine qui reste. */
    private static final Map<String, String> MERGED_MACHINES = Map.of("heat_generator", "solid_combustion_generator", "petrol_generator",
          "solid_combustion_generator", "stirred_reactor", "chemical_reactor", "condenser_unit", "cryogenic_unit", "vacuum_evaporator", "dryer",
          "plastic_extruder", "extruder", "fuel_blender", "mixer", "rubber_mixer", "mixer");

    /**
     * 0.20.40 : ancien cable qui remplace un tuyau de vapeur ou un organe de robinetterie retire (conduite en cuivre, cable a gaz MKI), ou
     * null ; 1.11 : c'est un bloc herite, converti ensuite en cable logistique (bronze, MKI).
     */
    @Nullable
    private static String retiredSteam(String path) {
        return switch (path) {
            case "copper_steam_pipe", "steam_valve", "check_valve", "pressure_regulator" -> "primitive_gas_cable";
            case "steel_steam_pipe" -> "basic_gas_cable";
            default -> null;
        };
    }

    /** 1.11 (spec §8.2) : objet d'un ancien cable (ou tuyau de vapeur retire, ou tres ancien {@code data_cable}) -> cable logistique. */
    @Nullable
    private static Item logisticItem(String path) {
        String old = retiredSteam(path);
        if (path.equals("data_cable")) {
            old = "basic_data_cable";
        }
        LegacyCableBlock legacy = PiTransport.legacy(old != null ? old : path);
        return legacy == null ? null : legacy.replacement().asItem();
    }

    /**
     * MAJ simplification (03/11/2026, demande de Guillaume) : blocs retires -> remplacant pose a la meme place (null : air). Machines de chaleur
     * (accumulateurs, pompes a chaleur, generateurs thermoelectriques, sonde geothermique) -> buffer thermique du tier (meme tile : l'ancienne
     * tile {@code thermal_machine} devient la tile des buffers, NBT etranger ignore) ; cables transparents -> cable opaque du meme niveau (meme
     * classe de bloc et meme tile) ; moteur Stirling et machines pneumatiques (tiles propres, sans equivalent sur) -> air.
     */
    /**
     * Patch 1.0.4-beta, lot 23 (demande de Guillaume du 08/11/2026, mise a jour finale des multiblocs) : blocs RETIRES -> remplacant le plus
     * proche (bloc pose et objet) : modules metiers (sauf la baie de serveurs) -> carcasse MKI, ou le bloc qui en reprend le role (bobine de
     * chauffe, paroi de foyer, rotor de turbine, train de forage, pompe a colonne, plafond filtrant) ; barre de commande, pompe primaire a
     * sodium, echangeur intermediaire, injecteur de neutres -> carcasse MKI (integres aux reacteurs) ; porte de salle blanche -> paroi.
     * Table = T:core/p104_lot23.py REMAP (verifiee par tools/check_p104_23.py). Une structure batie avec eux se defait (« Non formé ») :
     * les interieurs doivent etre vides.
     */
    public static final Map<String, String> P104_LOT23 = Map.ofEntries(
          Map.entry("aquatic_module", "industrial_casing_basic"),
          Map.entry("beam_injector", "industrial_casing_basic"),
          Map.entry("boiler_module", "firebox_basic"),
          Map.entry("cleanroom_door", "cleanroom_casing"),
          Map.entry("cleanroom_module", "cleanroom_filter"),
          Map.entry("control_rod_assembly", "industrial_casing_basic"),
          Map.entry("cooling_module", "industrial_casing_basic"),
          Map.entry("drilling_module", "drill_string_basic"),
          Map.entry("electrode_module", "industrial_casing_basic"),
          Map.entry("fuel_rack_module", "industrial_casing_basic"),
          Map.entry("gas_centrifuge_module", "industrial_casing_basic"),
          Map.entry("graviton_coil_module", "industrial_casing_basic"),
          Map.entry("grinding_module", "industrial_casing_basic"),
          Map.entry("growing_module", "industrial_casing_basic"),
          Map.entry("heating_module", "heating_coil_basic"),
          Map.entry("hot_cell_module", "industrial_casing_basic"),
          Map.entry("machining_module", "industrial_casing_basic"),
          Map.entry("membrane_module", "industrial_casing_basic"),
          Map.entry("pressing_module", "industrial_casing_basic"),
          Map.entry("primary_sodium_pump", "industrial_casing_basic"),
          Map.entry("propellant_sphere_module", "industrial_casing_basic"),
          Map.entry("pump_module", "column_pump_basic"),
          Map.entry("qubit_array_module", "industrial_casing_basic"),
          Map.entry("reactor_module", "industrial_casing_basic"),
          Map.entry("riser_module", "column_pump_basic"),
          Map.entry("rolling_module", "industrial_casing_basic"),
          Map.entry("rotor_module", "turbine_rotor_basic"),
          Map.entry("separation_module", "industrial_casing_basic"),
          Map.entry("sodium_heat_exchanger", "industrial_casing_basic"),
          Map.entry("synthesis_matrix_module", "industrial_casing_basic"),
          Map.entry("tray_module", "industrial_casing_basic"),
          Map.entry("vessel_module", "industrial_casing_basic"));

    /**
     * 1.0.4-beta (lot 23, Guillaume : « certains noms / id ne sont pas 100 % adequats avec la machine et pas assez generiques ») : CLES DE
     * MACHINE renommees (T:core/p104_lot23.py RENAMES) : compacts {@code <tier>_<cle>}, controleur {@code industrial_<cle>} ou {@code <cle>}
     * -> la nouvelle cle (meme tier, meme tile : le contenu reste) ; recettes verrouillees : {@link #recipeId}.
     */
    public static final Map<String, String> P104_LOT23_KEYS = Map.ofEntries(
          Map.entry("atmospheric_column", "distillation_column"),
          Map.entry("fired_heater", "cracker"),
          Map.entry("galvanizer", "chemical_bath"),
          Map.entry("electric_furnace", "furnace"),
          Map.entry("hydraulic_press", "press"),
          Map.entry("gas_compressor", "cryogenic_unit"),
          Map.entry("membrane_separator", "gas_separator"),
          Map.entry("sifter", "ore_separator"),
          Map.entry("lathe", "machining_center"),
          Map.entry("combustion_generator", "solid_combustion_generator"));
    /** 1.0.4-beta (lot 23) : autres ids renommes (bloc et objet). */
    public static final Map<String, String> P104_LOT23_IDS = Map.of(
          //age du bronze : foyer de la fonderie automatique, nom et id generiques (« Foyer en briques refractaires »)
          "foundry_firebox", "fire_brick_firebox");
    private static final Pattern P104_LOT23_ID = Pattern.compile("^(basic_|advanced_|elite_|ultimate_|industrial_)?([a-z0-9_]+)$");

    /** 1.0.4-beta (lot 23) : nouvel id d'un bloc / objet renomme au lot 23 (ou null). */
    @Nullable
    public static String p104Lot23Renamed(String path) {
        Matcher m = P104_LOT23_ID.matcher(path);
        if (m.matches()) {
            String key = P104_LOT23_KEYS.get(m.group(2));
            if (key != null) {
                return (m.group(1) == null ? "" : m.group(1)) + key;
            }
        }
        return P104_LOT23_IDS.get(path);
    }

    /** 1.0.4-beta (lot 23) : remplacant d'un bloc retire ou renomme au lot 23 (ou null). */
    @Nullable
    public static String p104Lot23Block(String path) {
        String removed = P104_LOT23.get(path);
        return removed != null ? removed : p104Lot23Renamed(path);
    }

    /**
     * 1.0.5-beta (lot 35) : copies automatisees des recettes d'etabli DEPLACEES au lot 34 (regle des etablis : chaque copie suit son nouvel
     * etabli, dossier {@code recipes/automation/<etabli>/}) : ancien chemin -> nouveau ; un verrou pose sur l'ancienne copie (assembleur,
     * presse) suit la recette au chargement ({@link #recipeId}) ; si la copie a change de machine, le verrou est ensuite efface proprement
     * ({@code RecipeLockable.dropMissingLock}). Verifiee par tools/check_p105_35.py
     * (ancien fichier absent, nouveau present).
     */
    public static final Map<String, String> P105_LOT34_RECIPES = Map.ofEntries(
          Map.entry("automation/metalworker/warehouse_warehouse_crate_port", "automation/carpenter/warehouse_warehouse_crate_port"),
          Map.entry("automation/metalworker/economy_market_counter", "automation/carpenter/economy_market_counter"),
          Map.entry("automation/metalworker/aquarium_aquarium", "automation/potter/aquarium_aquarium"),
          Map.entry("automation/transporter/vanilla_iron_horse_armor", "automation/metalworker/vanilla_iron_horse_armor"),
          Map.entry("automation/transporter/vanilla_diamond_horse_armor", "automation/metalworker/vanilla_diamond_horse_armor"),
          Map.entry("automation/transporter/vanilla_leather_horse_armor", "automation/tanner/vanilla_leather_horse_armor"),
          Map.entry("automation/transporter/vanilla_saddle", "automation/tanner/vanilla_saddle"),
          Map.entry("automation/transporter/vanilla_golden_horse_armor", "automation/metalworker/vanilla_golden_horse_armor"),
          Map.entry("automation/carpenter/leisure_cigar", "automation/agricultural/leisure_cigar"),
          Map.entry("automation/carpenter/bronze_bench_canvas", "automation/tanner/bronze_bench_canvas"),
          Map.entry("automation/carpenter/cotton_cotton_canvas", "automation/tanner/cotton_cotton_canvas"),
          Map.entry("automation/carpenter/agri_bench_hemp_canvas", "automation/tanner/agri_bench_hemp_canvas"),
          Map.entry("automation/carpenter/bronze_bench_canvas_string", "automation/tanner/bronze_bench_canvas_string"),
          Map.entry("automation/toolmaker/vanilla_lead", "automation/tanner/vanilla_lead"));

    /**
     * 1.0.5-beta (lot 38) : recettes retirees (doublons a vapeur, copies « steam_* » du kit, de la scierie et de l'extracteur) -> recette qui les
     * remplace (= T:core/p105_lot38.RECIPE_REMAP) : verrous suivis ({@link #recipeId}) et lot en cours d'un multibloc repris
     * ({@code IndustryRecipes.successor}, si les entrees sont les memes : {@code BatchRefund}) : aucune perte sur un monde existant.
     */
    public static final Map<String, String> P105_LOT38_RECIPES = Map.ofEntries(
          Map.entry("industry/sawmill/steam_planks_acacia", "mechanical/sawing/acacia_log"),
          Map.entry("industry/sawmill/steam_planks_bamboo", "mechanical/sawing/bamboo_block"),
          Map.entry("industry/sawmill/steam_planks_birch", "mechanical/sawing/birch_log"),
          Map.entry("industry/sawmill/steam_planks_cherry", "mechanical/sawing/cherry_log"),
          Map.entry("industry/sawmill/steam_planks_crimson", "mechanical/sawing/crimson_stem"),
          Map.entry("industry/sawmill/steam_planks_dark_oak", "mechanical/sawing/dark_oak_log"),
          Map.entry("industry/sawmill/steam_planks_jungle", "mechanical/sawing/jungle_log"),
          Map.entry("industry/sawmill/steam_planks_mangrove", "mechanical/sawing/mangrove_log"),
          Map.entry("industry/sawmill/steam_planks_oak", "mechanical/sawing/oak_log"),
          Map.entry("industry/sawmill/steam_planks_spruce", "mechanical/sawing/spruce_log"),
          Map.entry("industry/sawmill/steam_planks_warped", "mechanical/sawing/warped_stem"),
          Map.entry("industry/sawmill/steam_sticks", "bronze/steam/saw/sticks"),
          Map.entry("industry/extractor/steam_blue_dye", "bronze/steam/extractor/blue_dye"),
          Map.entry("industry/extractor/steam_green_dye", "bronze/steam/extractor/green_dye"),
          Map.entry("industry/extractor/steam_red_dye", "bronze/steam/extractor/red_dye"),
          Map.entry("industry/extractor/steam_red_dye_poppy", "bronze/steam/extractor/red_dye_poppy"),
          Map.entry("industry/extractor/steam_sugar", "bronze/steam/extractor/sugar"),
          Map.entry("industry/extractor/steam_yellow_dye", "bronze/steam/extractor/yellow_dye"),
          Map.entry("crushing/steam_dust_from_crushed_copper", "materials/milling_copper_dust"),
          Map.entry("crushing/steam_dust_from_crushed_iron", "materials/milling_iron_dust"),
          Map.entry("crushing/steam_dust_from_crushed_lead", "materials/milling_lead_dust"),
          Map.entry("crushing/steam_dust_from_crushed_tin", "materials/milling_tin_dust"),
          Map.entry("crushing/steam_dust_from_crushed_zinc", "materials/milling_zinc_dust"),
          Map.entry("crushing/steam_flour", "mechanical/milling/flour"),
          Map.entry("crushing/steam_gravel", "mechanical/jaw_crushing/stone_to_gravel"),
          Map.entry("bronze/steam/saw/planks_acacia", "mechanical/sawing/acacia_log"),
          Map.entry("bronze/steam/saw/planks_bamboo", "mechanical/sawing/bamboo_block"),
          Map.entry("bronze/steam/saw/planks_birch", "mechanical/sawing/birch_log"),
          Map.entry("bronze/steam/saw/planks_cherry", "mechanical/sawing/cherry_log"),
          Map.entry("bronze/steam/saw/planks_crimson", "mechanical/sawing/crimson_stem"),
          Map.entry("bronze/steam/saw/planks_dark_oak", "mechanical/sawing/dark_oak_log"),
          Map.entry("bronze/steam/saw/planks_jungle", "mechanical/sawing/jungle_log"),
          Map.entry("bronze/steam/saw/planks_mangrove", "mechanical/sawing/mangrove_log"),
          Map.entry("bronze/steam/saw/planks_oak", "mechanical/sawing/oak_log"),
          Map.entry("bronze/steam/saw/planks_spruce", "mechanical/sawing/spruce_log"),
          Map.entry("bronze/steam/saw/planks_warped", "mechanical/sawing/warped_stem"),
          Map.entry("bronze/steam/macerator/crushed_copper", "materials/jaw_crushing_crushed_copper"),
          Map.entry("bronze/steam/macerator/crushed_gold", "materials/jaw_crushing_crushed_gold"),
          Map.entry("bronze/steam/macerator/crushed_iron", "materials/jaw_crushing_crushed_iron"),
          Map.entry("bronze/steam/macerator/crushed_lead", "materials/jaw_crushing_crushed_lead"),
          Map.entry("bronze/steam/macerator/crushed_tin", "materials/jaw_crushing_crushed_tin"),
          Map.entry("bronze/steam/macerator/crushed_zinc", "materials/jaw_crushing_crushed_zinc"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_copper", "materials/milling_copper_dust"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_gold", "materials/milling_gold_dust"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_iron", "materials/milling_iron_dust"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_lead", "materials/milling_lead_dust"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_tin", "materials/milling_tin_dust"),
          Map.entry("bronze/steam/macerator/dust_from_crushed_zinc", "materials/milling_zinc_dust"),
          Map.entry("bronze/steam/macerator/gravel", "mechanical/jaw_crushing/stone_to_gravel"),
          Map.entry("bronze/steam/macerator/sand", "mechanical/jaw_crushing/gravel_to_sand"),
          Map.entry("bronze/steam/macerator/tuff_alluvial_gravel", "mechanical/jaw_crushing/tuff_to_alluvial_gravel"),
          Map.entry("bronze/steam/macerator/bone_meal", "mechanical/milling/bone_meal"));

    /**
     * 1.0.5-beta (lot 39, retour 51) : recettes DEPLACEES dont l'identifiant change (= T:core/p105_lot39.RECIPE_REMAP) : alliages sous argon
     * du creuset rangés dans la famille « sous vide », carters en fonte descendus au four a induction a vapeur (T1) : verrous suivis
     * ({@link #recipeId}), lot en cours d'un multibloc repris ({@code IndustryRecipes.successor}).
     */
    public static final Map<String, String> P105_LOT39_RECIPES = Map.ofEntries(
          Map.entry("industry/induction_furnace/alloy_nichrome", "industry/vacuum_furnace/alloy_nichrome"),
          Map.entry("industry/induction_furnace/rc_cast_iron_housing_1", "bronze/steam/induction_furnace/rc_cast_iron_housing"),
          Map.entry("industry/induction_furnace/rc_magnesium_housing", "industry/vacuum_furnace/rc_magnesium_housing"),
          Map.entry("industry/induction_furnace/rc_pump_housing_cast_iron", "bronze/steam/induction_furnace/rc_pump_housing_cast_iron"));

    /*
     * 1.0.5-beta (lot 42) : les tables P105_LOT39_REFUND (lots en cours du creuset parti au four a induction ou retire) et P105_LOT40_REFUND
     * (copie retiree de la brique crue a la presse) sont passees dans la table GENEREE fr.pioneers.tech.industry.multi.GeneratedBatchRefund (un seul
     * mecanisme : BatchRefund ; sources figees : T:core/p105_lot39.REFUND et p105_lot40.REFUND, lues par T:core/p105_lot42.py).
     */

    /**
     * 1.0.4-beta (lot 23) : identifiant d'une recette d'avant les renommages (verrou sauve dans une machine ou un objet) -> l'identifiant
     * actuel ; memes regles que T:core/p104_lot23.rename_text sur un chemin (dossier ou nom a la cle, prefixe de tier, industrial_ ou
     * industry_) ; jamais les machines de l'age mecanique (mechanics/sifter, mechanics/lathe).
     */
    public static ResourceLocation recipeId(ResourceLocation id) {
        if (!id.getNamespace().equals(Pioneers.MODID)) {
            return id;
        }
        String path = id.getPath();
        String moved = P105_LOT34_RECIPES.get(path);
        if (moved != null) {
            return Pioneers.rl(moved);
        }
        String replaced = P105_LOT38_RECIPES.get(path);
        if (replaced != null) {
            return Pioneers.rl(replaced);
        }
        String lot39 = P105_LOT39_RECIPES.get(path);
        if (lot39 != null) {
            return Pioneers.rl(lot39);
        }
        //1.0.6 : recette de machine desactivee (doublon d'une autre recette de la meme machine) -> la recette gardee
        String kept = GeneratedRecipeRemaps.DUPLICATES.get(path);
        if (kept != null) {
            return Pioneers.rl(kept);
        }
        String next = path;
        for (Map.Entry<String, String> e : P104_LOT23_IDS.entrySet()) {
            //id entier, aussi en fin d'un nom compose (copie d'atelier « bronze_bench_<id> »)
            next = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(e.getKey()) + "(?![A-Za-z0-9])").matcher(next)
                  .replaceAll(Matcher.quoteReplacement(e.getValue()));
        }
        for (Map.Entry<String, String> e : P104_LOT23_KEYS.entrySet()) {
            next = Pattern.compile("(?<![A-Za-z0-9_])((?:basic|advanced|elite|ultimate|industrial|industry)_)" + Pattern.quote(e.getKey()) + "(?![A-Za-z0-9])")
                  .matcher(next).replaceAll("$1" + Matcher.quoteReplacement(e.getValue()));
            Matcher bare = Pattern.compile("(?<=[/:.])" + Pattern.quote(e.getKey()) + "(?=[/:._]|$)").matcher(next);
            StringBuilder sb = new StringBuilder();
            while (bare.find()) {
                boolean mech = next.substring(0, bare.start()).endsWith("mechanics/");
                bare.appendReplacement(sb, Matcher.quoteReplacement(mech ? e.getKey() : e.getValue()));
            }
            bare.appendTail(sb);
            next = sb.toString();
            if (next.startsWith(e.getKey() + "/") || next.startsWith(e.getKey() + "_") || next.equals(e.getKey())) {
                next = e.getValue() + next.substring(e.getKey().length());
            }
        }
        return next.equals(path) ? id : Pioneers.rl(next);
    }

    /**
     * Patch 1.0.4-beta, lot 27 (retours de Guillaume du 09/11/2026) : blocs RETIRES -> bloc pose a leur place ("" : air) : injecteur -> cable
     * logistique en bronze (la ligne d'eau et de vapeur reste raccordee), condenseur -> reservoir en bois ; caisson pneumatique, vanne de
     * caverne, turbine de detente -> buffer de pression MKI ; ancrage d'hypertube -> air (le tube garde son bout et sa hitbox) ; poulie
     * d'entrepot -> PETITE poulie (axe vertical : la station ; sa tile est relue par {@code PulleyTile}, cordes gardees) ; poteau d'entrepot
     * -> air ; fantomes du reacteur -> le bloc qui a repris leur role. Table = T:core/p104_lot27.py REMAP (tools/check_p104_27.py).
     */
    public static final Map<String, String> P104_LOT27 = Map.ofEntries(
          Map.entry("steam_injector", "bronze_logistic_cable"),
          Map.entry("condenser", "wooden_fluid_buffer"),
          Map.entry("pneumatic_caisson", "basic_pressure_buffer"),
          Map.entry("cavern_valve", "basic_pressure_buffer"),
          Map.entry("expansion_turbine", "basic_pressure_buffer"),
          Map.entry("hypertube_anchor", ""),
          Map.entry("warehouse_pulley", "small_pulley"),
          Map.entry("warehouse_post", ""),
          Map.entry("diamond_reflector_module", "diamond_reflector"),
          Map.entry("fast_core_module", "fast_fuel_channel"));
    /** 1.0.4-beta (lot 27) : objet retire -> objet, quand il differe du bloc pose (T:core/p104_lot27.py ITEM_REMAP). */
    public static final Map<String, String> P104_LOT27_ITEMS = Map.of("hypertube_anchor", "pneumatic_tube", "warehouse_post", "treated_wood");
    /** 1.0.4-beta (lot 27) : types de tile retires relus par la tile du remplacant (T:core/p104_lot27.py TILE_REMAP), les autres ignores. */
    public static final Map<String, String> P104_LOT27_TILES = Map.of("warehouse_pulley", "pulley");
    public static final java.util.List<String> P104_LOT27_TILES_IGNORED = java.util.List.of("steam_injector", "condenser", "pneumatic_caisson",
          "cavern_valve", "expansion_turbine", "hypertube_anchor");

    /**
     * Bloc retire ou renomme -> remplacant. 1.0.4-beta (lot 23) : le lot 23 s'applique aussi AU BOUT d'un remap d'avant (un module absorbe
     * au lot 12 allait au module de cuve, retire a son tour). 1.0.4-beta (lot 27) : {@link #P104_LOT27} d'abord.
     */
    /**
     * 1.0.5-beta (lot 38, retour 52 : « suppression du marteau-pilon a vapeur » ; specification age du bronze §3.3) : bloc et objet RETIRES ->
     * remplacant : marteau-pilon a vapeur -> scie a vapeur (la taille de pierre, l'essentiel de ses recettes, passe a la scie). Table =
     * T:core/p105_lot38.py REMAP (tools/check_p105_38.py).
     */
    public static final Map<String, String> P105_LOT38 = Map.of("steam_hammer", "steam_saw");
    /**
     * 1.0.5-beta (lot 38) : type de tile retire -> type relu : marteau-pilon a vapeur -> {@code steam_process} (la scie a vapeur posee a sa
     * place relit l'ancien NBT : objets poses au sol, verrou efface, vapeur gardee : {@code SteamProcessTile.load}).
     */
    public static final Map<String, String> P105_LOT38_TILES = Map.of("steam_hammer", "steam_process");

    @Nullable
    public static String simplifiedBlock(String path) {
        String lot38 = P105_LOT38.get(path);
        if (lot38 != null) {
            return lot38;
        }
        String lot27 = P104_LOT27.get(path);
        if (lot27 != null) {
            return lot27;
        }
        String to = simplifiedBlock0(path);
        String next = p104Lot23Block(to == null ? path : to);
        return next != null ? next : to;
    }

    @Nullable
    private static String simplifiedBlock0(String path) {
        Matcher c = Pattern.compile("^(wooden|bronze|basic|advanced|elite|ultimate)_logistic_cable_clear$").matcher(path);
        if (c.matches()) {
            return c.group(1) + "_logistic_cable";
        }
        Matcher t = Pattern.compile("^(basic|advanced|elite|ultimate)_(heat_pump|thermoelectric_generator)$").matcher(path);
        if (t.matches()) {
            return t.group(1) + "_heat_buffer";
        }
        String p102 = p102Block(path);
        if (p102 != null) {
            return p102;
        }
        //1.0.4-beta (lot 15) : blocs de multibloc retires -> carcasse industrielle MKI
        String lot15 = p104Lot15Block(path);
        if (lot15 != null) {
            return lot15;
        }
        //1.0.4-beta (lot 11) : avant le lot 5 de la 1.0.3 (compacts du four a pyrolyse -> son controleur, absorbe a son tour)
        String p104 = p104Block(path);
        if (p104 != null) {
            return p104;
        }
        String p103 = p103Block(path);
        if (p103 != null) {
            return p103;
        }
        return switch (path) {
            case "sand_heat_accumulator", "geothermal_probe" -> "basic_heat_buffer";
            case "molten_salt_accumulator" -> "advanced_heat_buffer";
            case "stirling_engine", "pneumatic_press", "pneumatic_hammer", "air_sorter", "piston_compressor" -> "";
            default -> null;
        };
    }

    /**
     * Patch 1.0.2-beta, lot 2 (notes de Guillaume, « Suppressions ») : bloc retire -> son remplacant le plus proche, pose a la meme place (les
     * objets suivent leur bloc) : barillet a ressort -> volant en fer ; frein a sabots et regulateur de rotation -> regulateur a boules (il
     * freine au-dessus de sa visee) ; forge au charbon -> foyer de forge (il chauffe les lopins d'acier) ; enclume en pierre et (lot 5) enclume
     * en bronze -> enclume en fer (la premiere enclume, coulee au moule ; meme tile {@code forge_anvil}, la piece posee reste) ; martinet -> tour a bois (qui reprend son cintrage). Les tiles retirees sont remappees
     * vers celle du remplacant ({@link #p102Tile}, NBT etranger ignore). La batee (objet seul) devient le tamis a main.
     */
    @Nullable
    public static String p102Block(String path) {
        return switch (path) {
            case "spring_barrel" -> "iron_flywheel";
            case "mechanical_brake", "rotation_regulator" -> "governor";
            case "coal_forge" -> "forge_hearth";
            //lot 5 : plus d'enclume en bronze non plus : l'enclume en fer est la premiere enclume (meme tile forge_anvil : la piece en cours reste)
            case "stone_anvil", "bronze_anvil" -> "iron_anvil";
            case "trip_hammer" -> "wood_lathe";
            default -> null;
        };
    }

    /**
     * Patch 1.0.3-beta, lot 5 (decisions de Guillaume, « machines sur la sellette ») : bloc retire -> son remplacant le plus proche, pose a la
     * meme place (les objets suivent leur bloc) :
     * <ul>
     *     <li>chevalet de pompage, pompe a fluide, tete de puits de gaz, puits de saumure, four a pyrolyse : plus de version compacte (multibloc
     *     seul) : les compacts MKI a MKIV deviennent le CONTROLEUR de leur multibloc (il montre son plan ; la tile d'atelier n'y est pas relue :
     *     {@code AtelierTile.create}) ;</li>
     *     <li>dechiqueteuse et granuleuse fondues dans le broyeur et le compacteur : compacts -> broyeur / compacteur du meme tier (objets de
     *     l'ancienne machine rendus au premier tick), controleurs -> controleur du broyeur / compacteur industriel (meme tile, meme NBT) ;</li>
     *     <li>turbine a vapeur compacte -> generateur a combustion solide MKI (le premier courant apres la dynamo, meme place, memes faces
     *     d'energie) ; echappement compact -> buffer de gaz MKI (il prend la vapeur detendue par toutes ses faces, sans la rejeter).</li>
     * </ul>
     */
    @Nullable
    public static String p103Block(String path) {
        Matcher m = Pattern.compile("^(basic|advanced|elite|ultimate)_(pumpjack|fluid_pump|gas_wellhead|brine_well|pyrolysis_oven)$").matcher(path);
        if (m.matches()) {
            return "industrial_" + m.group(2);
        }
        Matcher merged = Pattern.compile("^(basic|advanced|elite|ultimate|industrial)_(wood_chipper|pellet_mill)$").matcher(path);
        if (merged.matches()) {
            return merged.group(1) + (merged.group(2).equals("wood_chipper") ? "_crusher" : "_compressor");
        }
        return switch (path) {
            case "compact_steam_turbine" -> "basic_solid_combustion_generator";
            case "compact_exhaust" -> "basic_gas_buffer";
            default -> null;
        };
    }

    /**
     * Patch 1.0.4-beta, lots 11 a 14 (arbitrage de Guillaume du 07/11/2026 : fusion des machines industrielles) : bloc d'une machine
     * ABSORBEE -> bloc de sa base au MEME tier (compacts MKI a MKIV -> compacts de la base, controleur -> controleur de la base ; absorbee
     * multibloc seul ou base sans compact : le controleur) ; table generee {@link fr.pioneers.tech.industry.GeneratedFusions#REMAP}
     * (tools/gc/industry/fusion.py). Meme tile (atelier ou controleur) : le NBT est relu par la base ({@code IoMigration} reconnait la
     * forme des cases de l'absorbee ; kit : {@code IoMigration.kit}). Une structure de multibloc batie pour l'absorbee ne se reforme que si
     * elle convient au gabarit de la base (sinon « Non formé » : le plan de la base s'affiche, Maj + clic droit) ; ses blocs restent poses.
     * Lot 11 aussi : le fondeur d'alliages a vapeur -> fourneau a creuset (ses trois alliages y sont deja ; NBT de la tile a vapeur ignore :
     * {@code SteamProcessTile.create}).
     */
    @Nullable
    public static String p104Block(String path) {
        return fr.pioneers.tech.industry.GeneratedFusions.REMAP.get(path);
    }

    /** 1.0.4-beta (lot 15) : les neuf blocs de multibloc retires (decision de Guillaume du 07/11/2026). */
    public static final java.util.List<String> P104_LOT15_PARTS = java.util.List.of("industrial_burner", "industrial_tuyere", "industrial_chimney",
          "industrial_flare", "industrial_vent", "industrial_fan", "industrial_cooling_unit", "industrial_coil", "industrial_engineering");

    /**
     * Patch 1.0.4-beta, lot 15 (decision de Guillaume du 07/11/2026 : « On va supprimer les blocks des multiblocks ») : brûleur, tuyère,
     * cheminée, torchère, évent, ventilateur, groupe froid, bobine de chauffe et bloc d'ingénierie -> carcasse industrielle MKI (bloc pose et
     * objet ; ces pieces n'avaient pas de tier). Un multibloc forme garde son tier : son controleur remet ces carcasses au tier peint au
     * premier controle ({@code industry.multi.PartMigration}).
     */
    @Nullable
    public static String p104Lot15Block(String path) {
        return P104_LOT15_PARTS.contains(path) ? "industrial_casing_basic" : null;
    }

    /** 1.0.2-beta (lot 2) : type de tile retire -> type de tile du bloc remplacant ({@link #p102Block}), ou null. */
    @Nullable
    static String p102Tile(String path) {
        return switch (path) {
            case "spring_barrel" -> "flywheel";
            case "mechanical_brake", "rotation_regulator" -> "governor";
            case "coal_forge" -> "forge_hearth";
            default -> null;
        };
    }

    /**
     * 1.0.5-beta (lot 28, retour 36 : « le requin gris a deux oeufs d'apparition ») : l'oeuf du POISSON requin gris (espece de peche seulement)
     * est retire ; celui d'un monde existant devient l'oeuf du vrai requin gris.
     */
    public static final Map<String, String> P105_LOT28_ITEMS = Map.of("fish_gray_reef_shark_spawn_egg", "gray_reef_shark_spawn_egg");

    /**
     * 1.0.6 : objets d'extrusion retires (demandes de Guillaume des 16 et 17/11/2026). La forme de l'anneau (Cintreuse) et la filiere
     * d'extrusion vierge (plus aucun usage) deviennent la forme tige ; la forme de l'engrenage devient celle de l'ebauche d'engrenage (5
     * lingots). Les formes de la plaque, du fil et de l'ebauche sont celles de la voie generique de l'extrudeuse.
     */
    public static final Map<String, String> P106_ITEMS = Map.of("extrusion_shape_ring", "extrusion_shape_rod", "extrusion_die", "extrusion_shape_rod",
          "extrusion_shape_gear", "extrusion_shape_gear_blank");

    /** MAJ simplification : objet retire -> objet qui le remplace (machines de chaleur et cables : le bloc pose ; les autres : leur remplacant). */
    @Nullable
    public static String simplifiedItem(String path) {
        String p106 = P106_ITEMS.get(path);
        if (p106 != null) {
            return p106;
        }
        String lot28 = P105_LOT28_ITEMS.get(path);
        if (lot28 != null) {
            return lot28;
        }
        String lot27 = P104_LOT27_ITEMS.get(path);
        if (lot27 != null) {
            return lot27;
        }
        return switch (path) {
            case "stirling_engine" -> "steam_engine";
            case "pneumatic_press" -> "steam_press";
            //1.0.5-beta (lot 38) : le marteau-pilon a vapeur est retire a son tour : au bout de la chaine, la scie a vapeur
            case "pneumatic_hammer" -> P105_LOT38.get("steam_hammer");
            case "air_sorter" -> "wooden_hopper";
            case "piston_compressor" -> "air_compressor";
            //1.0.2-beta (lot 2) : la batee devient le tamis a main (le tamisage a la main reste)
            case "gold_pan" -> "hand_sieve";
            //1.0.2-beta (lot 2) : plus de lingot impur : il devient une scorie
            case "impure_ingot" -> "slag";
            default -> {
                String block = simplifiedBlock(path);
                yield block == null || block.isEmpty() ? null : block;
            }
        };
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        //MAJ simplification (03/11/2026) : voir simplifiedBlock / simplifiedItem
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            String to = simplifiedBlock(mapping.getKey().getPath());
            if (to != null) {
                Block block = to.isEmpty() ? Blocks.AIR : ForgeRegistries.BLOCKS.getValue(Pioneers.rl(to));
                mapping.remap(block == null ? Blocks.AIR : block);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            String to = simplifiedItem(mapping.getKey().getPath());
            Item item = to == null ? null : ForgeRegistries.ITEMS.getValue(Pioneers.rl(to));
            if (item != null && item != Items.AIR) {
                mapping.remap(item);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, Pioneers.MODID)) {
            //1.0.5-beta (lot 38) : marteau-pilon a vapeur -> tile des machines a vapeur (relue par la scie posee a sa place)
            String lot38 = P105_LOT38_TILES.get(mapping.getKey().getPath());
            if (lot38 != null) {
                BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(Pioneers.rl(lot38));
                if (type != null) {
                    mapping.remap(type);
                } else {
                    mapping.ignore();
                }
                continue;
            }
            //1.0.4-beta (lot 27) : poulie d'entrepot -> tile des poulies (cordes relues) ; les autres tiles retirees : ignorees
            String lot27 = P104_LOT27_TILES.get(mapping.getKey().getPath());
            if (lot27 != null) {
                BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(Pioneers.rl(lot27));
                if (type != null) {
                    mapping.remap(type);
                } else {
                    mapping.ignore();
                }
                continue;
            }
            if (P104_LOT27_TILES_IGNORED.contains(mapping.getKey().getPath())) {
                mapping.ignore();
                continue;
            }
            String tile = p102Tile(mapping.getKey().getPath());
            if (tile != null) {
                BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(Pioneers.rl(tile));
                if (type != null) {
                    mapping.remap(type);
                } else {
                    mapping.ignore();
                }
                continue;
            }
            switch (mapping.getKey().getPath()) {
                case "thermal_machine" -> mapping.remap(PiTransport.BUFFER_TILE.get());
                case "stirling_engine", "pneumatic_press", "pneumatic_hammer", "air_sorter", "piston_compressor" -> mapping.ignore();
                //1.0.3-beta (lot 5) : tiles de la turbine a vapeur et de l'echappement compacts : leur NBT n'est pas relu (le bloc remplacant cree la sienne)
                case "compact_steam_turbine", "compact_exhaust" -> mapping.ignore();
                default -> {
                }
            }
        }
        //1.9.2 (retours de Guillaume) : plaque tournante, pont transbordeur (centre, cases de fosse, tuile) et abri de quai supprimes :
        //blocs poses -> air (le pont des tables est retire du reseau par RailWorld.dropTables), objets -> voie MKII (5 dans la recette) /
        //garde-corps
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            switch (mapping.getKey().getPath()) {
                case "turntable", "transfer_table", "table_pit", "platform_shelter" -> mapping.remap(Blocks.AIR);
                default -> {
                }
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            switch (mapping.getKey().getPath()) {
                case "turntable", "transfer_table" -> mapping.remap(PiRail.TRACKS.get(TrackQuality.ADVANCED).asItem());
                case "platform_shelter" -> mapping.remap(PiRail.RAILING.asItem());
                default -> {
                }
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("rail_table")) {
                mapping.ignore();
            }
        }
        //0.20.40 : tuyaux de vapeur et robinetterie retires ; 1.11 : blocs herites (convertis au chargement), objets -> cable logistique
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            String old = retiredSteam(mapping.getKey().getPath());
            LegacyCableBlock legacy = old == null ? null : PiTransport.legacy(old);
            if (legacy != null) {
                mapping.remap(legacy);
            }
        }
        //1.11 (spec §8.2) : objets des 60 anciens cables (en main, en coffre) -> cable logistique du meme niveau (meme nombre)
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            Item item = logisticItem(mapping.getKey().getPath());
            if (item != null) {
                mapping.remap(item);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("steam_pipe") || mapping.getKey().getPath().equals("steam_valve")) {
                mapping.remap(PiTransport.LEGACY_CABLE_TILE.get());
            }
        }
        //1.0 (0.19.12) : la vapeur est un gaz : plus de fluide, de bloc ni de seau de vapeur (seau rendu vide)
        for (var mapping : event.getMappings(ForgeRegistries.Keys.FLUIDS, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("steam") || mapping.getKey().getPath().equals("flowing_steam")) {
                mapping.remap(Fluids.EMPTY);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("steam")) {
                mapping.remap(Blocks.AIR);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("steam_bucket")) {
                mapping.remap(Items.BUCKET);
            }
        }
        //0.20.3 : grands virages retires : l'objet redevient une voie de la meme qualite
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            for (TrackQuality q : TrackQuality.values()) {
                if (mapping.getKey().getPath().equals(q.blockId() + "_curve")) {
                    mapping.remap(PiRail.TRACKS.get(q).asItem());
                }
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            ResourceLocation to = merged(mapping.getKey().getPath());
            Block block = to == null ? null : ForgeRegistries.BLOCKS.getValue(to);
            if (block != null) {
                mapping.remap(block);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            ResourceLocation to = merged(mapping.getKey().getPath());
            Item item = to == null ? null : ForgeRegistries.ITEMS.getValue(to);
            if (item != null) {
                mapping.remap(item);
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, Pioneers.MODID)) {
            if (mapping.getKey().getPath().equals("data_drive")) {
                mapping.remap(PiScienceMachines.BIOME_ANALYZER_TILE.get());
            } else if (mapping.getKey().getPath().equals("rocket_assembler")) {
                //1.9.2 : l'ancienne table posee devient le controleur, grille, plan et produit gardes (meme sauvegarde)
                mapping.remap(PiWorksMachines.HALL_TILES.get(AssemblyHall.Kind.ROCKET).get());
            } else if (mapping.getKey().getPath().equals("rover_assembly_table")) {
                mapping.remap(PiWorksMachines.HALL_TILES.get(AssemblyHall.Kind.ROBOTICS).get());
            } else if (OLD_CHAMBER.containsKey(mapping.getKey().getPath())) {
                //0.11 : pieces des anciennes chambres -> pieces du Stabilisateur spatial
                mapping.remap(PiStabilizer.PART_TILE.get());
            } else if (OLD_SIMULATOR.containsKey(mapping.getKey().getPath())) {
                mapping.remap(PiSpaceSim.PART_TILE.get());
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.BLOCKS, Pioneers.MODID)) {
            switch (mapping.getKey().getPath()) {
                case "data_cable" -> mapping.remap(PiTransport.legacy("basic_data_cable"));
                case "data_drive" -> mapping.remap(PiScienceMachines.BIOME_ANALYZER.getBlock());
                case "quantum_coprocessor" -> mapping.ignore();
                default -> {
                    StabilizerPart part = OLD_CHAMBER.get(mapping.getKey().getPath());
                    SpaceSimPart simulator = OLD_SIMULATOR.get(mapping.getKey().getPath());
                    if (part != null) {
                        mapping.remap(PiStabilizer.PARTS.get(part).getBlock());
                    } else if (simulator != null) {
                        mapping.remap(PiSpaceSim.PARTS.get(simulator).getBlock());
                    }
                }
            }
        }
        for (var mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Pioneers.MODID)) {
            switch (mapping.getKey().getPath()) {
                case "data_drive" -> mapping.remap(PiScienceMachines.BIOME_ANALYZER.asItem());
                //1.4.2 : le guide est une touche (H), plus un objet
                case "quantum_coprocessor", "rover_blueprint", "guide_book" -> mapping.ignore();
                //0.11 : les anciens robots des bases deviennent des unites robotiques Mk1
                case "mining_rover" -> mapping.remap(PiRobotics.UNITS.get(UnitKind.ROVER).get(UnitTier.T1).get());
                case "farm_robot" -> mapping.remap(PiRobotics.UNITS.get(UnitKind.TURTLE).get(UnitTier.T1).get());
                case "harvester_drone" -> mapping.remap(PiRobotics.UNITS.get(UnitKind.DRONE).get(UnitTier.T1).get());
                default -> {
                    StabilizerPart part = OLD_CHAMBER.get(mapping.getKey().getPath());
                    SpaceSimPart simulator = OLD_SIMULATOR.get(mapping.getKey().getPath());
                    if (part != null) {
                        mapping.remap(PiStabilizer.PARTS.get(part).asItem());
                    } else if (simulator != null) {
                        mapping.remap(PiSpaceSim.PARTS.get(simulator).asItem());
                    }
                }
            }
        }
    }
}
