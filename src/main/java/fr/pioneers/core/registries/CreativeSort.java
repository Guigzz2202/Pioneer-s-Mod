package fr.pioneers.core.registries;

import fr.pioneers.farm.FarmIds;
import fr.pioneers.tech.industry.IndustryContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 1.0 (alpha) : PLAN UNIQUE de l'inventaire creatif (demande de Guillaume : « reorganise parfaitement l'inventaire creatif »).
 * <p>
 * Ce fichier ne depend que de Java (aucune classe Minecraft) : le banc {@code tools/creative/check_tabs.py} l'execute hors du jeu sur
 * tous les identifiants du mod et verifie que chaque objet est range exactement une fois. {@link PiCreativeTabs} ne fait qu'enregistrer
 * les onglets et y verser les objets dans l'ordre calcule ici.
 * <ul>
 * <li>{@link Tab} : les onglets, dans l'ordre de la progression, avec leur icone.</li>
 * <li>{@link #LAYOUT} : les sections de chaque onglet, dans l'ordre d'affichage. Une section liste des motifs d'identifiants : id exact,
 * motif a etoiles ({@code *_cable}), {@code B:} = blocs seulement, {@code I:} = objets seulement (aussi devant un id exact : une liste
 * generee se partage ainsi entre blocs et objets), {@code ~} = motif faible (passe apres tous les autres).</li>
 * <li>Un id exact l'emporte toujours ; entre motifs, le plus precis (le plus de lettres fixes) gagne, puis le premier declare.</li>
 * <li>Les machines de l'industrie viennent du catalogue ({@link Machine}, ordre de {@code IndustryMachine}) : les tiers d'une machine
 * puis son multibloc, cote a cote.</li>
 * <li>Ce qu'aucun motif ne prend est range par sa categorie ({@link Cat}, devinee d'apres l'id) dans les sections « par categorie » des
 * Ressources, Composants et Fluides : un objet nouveau n'est jamais perdu.</li>
 * </ul>
 * Ordre dans une section : rang du motif, puis famille (id sans son tier) et tier (MKI..MKIV, basique..ultime, T1..T13) ; voir {@link Order}.
 */
public final class CreativeSort {

    private CreativeSort() {
    }

    // ------------------------------------------------------------------
    // Onglets
    // ------------------------------------------------------------------

    /**
     * Les onglets, dans l'ordre d'affichage : id du registre (cle de langue itemGroup.pioneers.&lt;id&gt;) et icone.
     * <p>
     * 1.9.2 (retours de Guillaume : « reorganiser l'inventaire, limiter le nombre d'onglets, organiser par progression ; les derniers seront
     * Components, Building, Worlds, Core, Fluids and Gas, Equipments ») : 9 onglets. Energie, mecanique et ferme/cuisine (machines) rejoignent
     * l'industrie dans Tech ; rail et marine rejoignent la logistique ; matieres et nourriture forment Core ; le monde habite est reparti
     * (carnet et clans a l'Equipement, mobilier au village a la Construction, ruines aux Mondes, scraps au Core). JEI suit cet ordre (il trie
     * les objets d'un mod dans l'ordre du menu creatif : ordre d'enregistrement des onglets, puis place dans l'onglet).
     */
    public enum Tab {
        TECH("tech", "wooden_cogwheel"),
        LOGISTICS("logistics", "advanced_logistic_cable"),
        SPACE("space", "rocket_tier_1"),
        COMPONENTS("components", "circuit_mk1"),
        BUILDING("building", "moon_rock_bricks"),
        WORLDS("worlds", "moon_rock"),
        CORE("core", "raw_copper"),
        FLUIDS("fluids", "gas_capsule"),
        EQUIPMENT("equipment", "stellaris_chestplate");

        public final String id;
        public final String icon;

        Tab(String id, String icon) {
            this.id = id;
            this.icon = icon;
        }
    }

    /** Ordre dans une section. */
    public enum Order {
        /** Rang du motif, puis famille et tier (defaut). */
        LIST,
        /** Tier d'abord (tout le T1, puis tout le T2...), puis rang du motif : une fusee a cote de ses pieces. */
        TIER,
        /** Astre (du plus proche au plus lointain), puis nature du bloc (sol, roche, glace, nuages, vivant, minerai). */
        WORLD,
        /** Astre, puis pierre de base, puis variante (brute, pavee, polie, lisse, taillee, briques, fissurees, ciselees) et forme. */
        BUILD,
        /** Ordre du catalogue de l'industrie (= progression), puis id. */
        CATALOG,
        /** Categorie, puis groupe (piece et ses tiers cote a cote, metal, forme) dans l'ordre du catalogue de l'industrie. */
        CATEGORY
    }

    /**
     * Machine du catalogue de l'industrie, dans l'ordre du catalogue : cle, genre ({@code IndustryMachine.Kind}) et atelier
     * ({@code IndustryMachine.Atelier} : NEW / KIT = 4 tiers + multibloc industrial_, BRONZE = bloc du bronze + multibloc, NONE = multibloc seul),
     * module de l'interieur du multibloc (id sans « _module », vide s'il n'y en a pas).
     */
    //1.0.3-beta (lot 5) : multiId = id du controleur du multibloc (IndustryMachine.multiId : hors de la regle pour les multiblocs seuls a id
    //industrial_<cle> et la turbine a gaz)
    public record Machine(String key, String kind, String atelier, String module, String multiId) {
    }

    /** Plante spatiale ({@code SpacePlant}, dans l'ordre des astres) : bloc de la plante (graine = bloc + _seeds) et recolte. */
    public record Plant(String block, String product) {
    }

    /** Section : onglet (null = objets techniques caches), nom (pour le banc), ordre, motifs ou categories, machines. */
    public static final class Section {

        public final Tab tab;
        public final String name;
        final Order order;
        final List<String> patterns = new ArrayList<>();
        final List<Cat> cats = new ArrayList<>();
        Predicate<Machine> machines;
        Function<Plant, List<String>> plants;
        /** Recoit les modules des machines du catalogue, dans l'ordre de la premiere machine qui s'en sert. */
        boolean modules;
        /** Liste tiree d'un catalogue : ne prend que les ids qu'aucun autre motif (meme a etoiles) ne prend ; pas de doublon signale. */
        boolean soft;
        /** Section d'une famille de la refonte des composants (GeneratedComponents) : ses ids exacts passent avant ceux des autres sections. */
        boolean refonte;
        /** 1.0.4-beta (lot 25) : ids des machines pris par la section : tiers et multibloc ({@link #PARTS_ALL}), compacts ou controleurs. */
        int parts = PARTS_ALL;

        Section(Tab tab, String name, Order order) {
            this.tab = tab;
            this.name = name;
            this.order = order;
        }

        Section add(String... ids) {
            patterns.addAll(List.of(ids));
            return this;
        }

        Section add(Collection<String> ids) {
            patterns.addAll(ids);
            return this;
        }

        @Override
        public String toString() {
            return (tab == null ? "hidden" : tab.id) + "/" + name;
        }
    }

    /** Place d'un objet : section et rang du motif qui l'a pris. */
    public record Placement(Section section, int rank) {
    }

    /** 1.0.4-beta (lot 25) : ids d'une machine du catalogue pris par une section : tout, les compacts (tiers) seuls, le controleur seul. */
    static final int PARTS_ALL = 0, PARTS_COMPACTS = 1, PARTS_CONTROLLERS = 2;

    // ------------------------------------------------------------------
    // Donnees : metaux, astres, sortes
    // ------------------------------------------------------------------

    /** Metaux terrestres dans l'ordre de la progression (age du cuivre, bronze, fer, acier, electricite, alliages, metaux rares). */
    /** Ids des composants de la refonte : rangees seulement dans leur section de famille. */
    static final Set<String> REFONTE_IDS = GeneratedComponents.FAMILIES.stream().flatMap(f -> f.ids().stream()).collect(Collectors.toSet());

    static final List<String> METALS = List.of("copper", "tin", "zinc", "lead", "bronze", "brass", "gold", "iron", "solder", "steel",
          "hardened_steel", "tempered_steel", "galvanized_steel", "nickel", "silver", "invar", "constantan", "electrum", "aluminium", "duralumin",
          "magnesium", "chrome", "nichrome", "cobalt", "stainless_steel", "vanadium", "vanadium_steel", "babbitt", "titanium", "titanium_aluminide",
          "tungsten", "tungsten_carbide", "zirconium", "zircaloy", "niobium", "niobium_titanium", "platinum", "iridium", "osmium", "osmiridium",
          "stellite", "nitinol", "netherite", "depleted_uranium", "lunar_alloy");

    /** Metaux des astres, du plus proche au plus lointain (ordre de {@code materials.py SPACE}). */
    static final List<String> SPACE_METALS = List.of("lunarium", "mercurium", "venusium", "marsium", "tharsium", "phobite", "deimite",
          "meteoritite", "ahunite", "vestalite", "pallasite", "hygeite", "lokite", "conamarite", "sulcite", "valhallite", "tigrite", "krakenite",
          "veronite", "messinite", "ruachite", "sputnikite", "mordorite", "tolimanite", "terminite", "flarium", "pyrrhite", "thalassite", "edenite",
          "borealite");

    /** Gemmes et mineraux a forme de gemme. */
    static final List<String> GEMS = List.of("quartz", "amethyst", "lapis", "zircon", "peridot", "topaz", "ruby", "sapphire",
          "tanzanite", "emerald", "diamond");

    /** Sortes d'outils (outils de travail puis outils d'atelier) et pieces d'armure, dans l'ordre d'affichage. */
    static final List<String> TOOLS = List.of("pickaxe", "mining_hammer", "axe", "shovel", "excavator", "hoe", "scythe", "sword", "prospector",
          "hammer", "file", "saw", "knife", "mortar", "wrench");
    static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");
    /** Formes de metal (composants), dans l'ordre d'affichage. */
    static final List<String> FORMS = List.of("plate", "sheet", "foil", "rod", "wire", "bolt", "screw", "ring", "spring", "gear", "pipe", "tube",
          "beam", "wire_mesh", "wire_rope", "magnet");

    /** Familles de cables et de buffers. */
    static final List<String> CABLES = List.of("item", "fluid", "gas", "energy", "heat", "pressure");
    static final List<String> TIERS = List.of("basic", "advanced", "elite", "ultimate");

    /** Astres dans l'ordre du joueur (noms de {@code SolarSystem.Body} en minuscules). */
    static final List<String> BODIES = List.of("earth", "moon", "mercury", "venus", "mars", "phobos", "deimos", "asteroid_belt", "ceres", "vesta",
          "pallas", "hygiea", "jupiter", "io", "europa", "ganymede", "callisto", "saturn", "enceladus", "titan", "uranus", "miranda", "titania",
          "neptune", "triton", "pluto", "charon", "sun", "centauri_a", "centauri_b", "toliman_b", "proxima", "proxima_b", "proxima_d", "trappist_1",
          "trappist_1b", "trappist_1c", "trappist_1d", "trappist_1e", "trappist_1f", "trappist_1g", "trappist_1h");

    /** Mots qui designent un astre sans le nommer (metaux des astres compris). */
    private static final Map<String, String> BODY_WORDS = new LinkedHashMap<>();

    static {
        String[] metalBodies = {"moon", "mercury", "venus", "mars", "mars", "phobos", "deimos", "asteroid_belt", "ceres", "vesta", "pallas",
              "hygiea", "io", "europa", "ganymede", "callisto", "enceladus", "titan", "miranda", "titania", "triton", "pluto", "charon",
              "toliman_b", "proxima_b", "proxima_d", "trappist_1b", "trappist_1d", "trappist_1e", "trappist_1f"};
        for (int i = 0; i < SPACE_METALS.size(); i++) {
            BODY_WORDS.put(SPACE_METALS.get(i), metalBodies[i]);
        }
        String[][] words = {{"lunar", "moon"}, {"mare_", "moon"}, {"frozen_regolith", "moon"}, {"hollow_crust", "mercury"},
              {"sulfur_crust", "venus"}, {"tessera", "venus"}, {"stromatolite", "mars"}, {"dry_ice", "mars"}, {"hematite_ore", "mars"},
              {"diogenite", "vesta"}, {"toliman", "toliman_b"}, {"verdigris", "toliman_b"}, {"bloomstone", "toliman_b"},
              {"flare_crystal", "proxima_d"}, {"glowcap", "trappist_1e"}, {"storm_cloud", "neptune"}, {"deep_haze", "neptune"}};
        for (String[] w : words) {
            BODY_WORDS.put(w[0], w[1]);
        }
    }

    // ------------------------------------------------------------------
    // Le plan
    // ------------------------------------------------------------------

    /** Toutes les sections, dans l'ordre d'affichage (onglet par onglet). */
    public static final List<Section> LAYOUT = new ArrayList<>();

    private static Section section(Tab tab, String name) {
        return section(tab, name, Order.LIST);
    }

    private static Section section(Tab tab, String name, Order order) {
        Section s = new Section(tab, name, order);
        LAYOUT.add(s);
        return s;
    }

    /** Produit croise : pour chaque nom, chaque gabarit ({@code %s} = le nom), dans cet ordre. */
    static List<String> each(List<String> names, String... templates) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            for (String t : templates) {
                out.add(t.replace("%s", name));
            }
        }
        return out;
    }

    /** Les quatre tiers d'un gabarit ({@code %s} = basic..ultimate). */
    static List<String> tiers(String template) {
        return each(TIERS, template);
    }

    /** 1.9.2 : ids d'une liste generee qui verifient un motif (rangement par motif d'id, sans liste en dur). */
    static List<String> only(List<String> ids, Predicate<String> keep) {
        return ids.stream().filter(keep).toList();
    }

    /** 1.9.2 : oeufs d'apparition (faune, onglet Mondes). */
    static boolean fauna(String id) {
        return id.endsWith("_spawn_egg");
    }

    /** 1.9.2 : mobilier et decoration (onglet Construction). */
    static boolean furniture(String id) {
        return id.endsWith("_shelf") || id.endsWith("_cupboard") || id.endsWith("_table_decor") || id.equals("ashtray");
    }

    /** 1.9.2 : objets de la ferme ranges ailleurs que par leur liste (faune, mobilier, cannes a peche a l'Equipement). */
    static boolean farmElsewhere(String id) {
        return fauna(id) || furniture(id) || id.endsWith("_fishing_rod");
    }

    // ---------------------------------------------------------------- 1. Tech

    /**
     * 1.9.2 : Tech = age mecanique, bronze et vapeur, machines de la ferme et de la cuisine, industrie, energie, informatique, atome. Dans
     * l'ordre de la progression ; une machine et ses tiers cote a cote, puis son multibloc.
     * <p>
     * 1.0.4-beta (lot 25, Guillaume : « reorganiser l'onglet Pioneer's Tech pour que ce soit trie de maniere tres logique ; ce qui semble
     * isole / incoherent / suspect en bas, je regarderai ») : PAR AGE PUIS PAR FAMILLE.
     * <ol>
     *     <li>age mecanique : sources, transmission, machines et postes mecaniques, recolte, forge primaire, etablis ;</li>
     *     <li>age du bronze : cuisine, ferme, fonderie, haut fourneau et four a coke, chaudiere et machines a vapeur, foreuse a vapeur (les
     *     blocs d'un multibloc puis son controleur) ;</li>
     *     <li>age industriel : etablis de montage ; MULTIBLOCS : blocs de construction, carcasses / verres / cadres / toles (chaque famille
     *     MKI a MKIV), ports, puis les CONTROLEURS par domaine ({@link #DOMAINS}) ; COMPACTS par domaine, tier par tier (tous les MKI, puis
     *     les MKII...) ; chaleur et renouvelables avec l'energie ;</li>
     *     <li>age de l'atome : informatique (et ses multiblocs), intelligence artificielle, fission (le reflecteur en diamant du Gen III avec
     *     les coeurs de fission), fusion.</li>
     *     <li>1.0.4-beta (lot 27, retours de Guillaume) : la section « a verifier » est videe et retiree : sismographe -> Equipement (divers,
     *     avec les scanners), tube de production -> Composants (pieces de l'industrie), fantomes {@code diamond_reflector_module} et
     *     {@code fast_core_module} supprimes.</li>
     * </ol>
     */
    private static void layoutTech() {
        Tab t = Tab.TECH;
        // ===== Age mecanique : sources, transmission, cordages, mesure, machines, recolte
        section(t, "sources").add("hand_crank", "water_wheel", "large_water_wheel", "windmill_hub", "plank_sail", "canvas_sail", "horse_mill",
              "feeding_trough", "weather_vane", "winch");
        section(t, "transmission").add("wooden_shaft", "iron_shaft", "wooden_cog", "wooden_cogwheel", "iron_cog", "iron_cogwheel", "gearbox",
              "gearshift", "reverser", "clutch", "stone_flywheel", "iron_flywheel", "small_pulley", "pulley", "large_pulley",
              "fiber_rope", "tarred_rope", "leather_belt", "governor", "tachometer", "dynamometer");
        section(t, "machines mecaniques").add("millstone", "jaw_crusher", "mechanical_sifter", "mechanical_saw", "wood_lathe",
              "bellows", "noria", "wooden_fluid_buffer", "mechanical_drill", "beam_engine");
        //Postes mecaniques de la cuisine (entraines par les arbres) : tout « mechanical_* » qu'aucune autre section ne prend
        section(t, "postes mecaniques").add("mechanical_press", "mechanical_churn", "mechanical_chopper", "mechanical_kneader",
              "mechanical_honey_extractor", "~B:mechanical_*");
        //1.0.4-beta (lot 25) : la saignee en bronze rejoint la saignee en bois (elle etait seule dans « air comprime et resine »)
        section(t, "orpaillage et recolte").add("hand_sieve", "fiber_mesh", "clay_drain", "wooden_hopper", "wooden_resin_tap", "bronze_resin_tap");
        //Forge primaire (0.21) : feu, creuset sur feu de camp, coulee en moule pose, foyer, enclumes, trempe
        section(t, "forge primaire").add("fire_starter", "clay_crucible", "hand_bellows", "crucible_tongs", "unfired_clay_bucket", "clay_bucket",
              "forge_hearth", "unfired_anvil_mold", "anvil_mold", "iron_anvil", "quench_basin", "tool_rack")
              //1.0.3-beta (lot 11) : porte-outils des autres bois, apres celui du chene (ordre des bois)
              .add(fr.pioneers.building.home.HomeWoods.ALL.stream().filter(w -> !w.equals("oak")).map(w -> w + "_tool_rack").toList());
        section(t, "etablis").add("carpenter_bench", "mason_bench", "potter_bench", "metalworker_bench", "toolmaker_bench", "coppersmith_bench",
              "redstoner_bench", "transporter_bench", "luthier_bench", "agricultural_bench", "tanner_bench", "molder_bench",
              "B:*_bench");
        // ===== Age du bronze : cuisine, ferme, fonderie, haut fourneau, vapeur, foreuse
        //1.9.2 : postes de la cuisine (liste generee FarmIds.STATIONS) ; leurs contenants vont au Core, le mobilier a la Construction
        section(t, "cuisine").add(only(FarmIds.STATIONS, id -> !farmElsewhere(id)));
        //1.9.2 : machines de la ferme = les BLOCS des listes de la ferme animee et de l'elevage (leurs produits vont au Core), pieges a crustaces
        section(t, "ferme").add(each(only(FarmIds.DEVICES, id -> !farmElsewhere(id)), "B:%s")).add(each(FarmIds.LIVESTOCK, "B:%s"))
              .add("fishing_net", "crab_pot", "mushroom_substrate")
              .add("rabbit_hutch");   //1.0.4-beta (lot 9) : clapier
        //1.0.5-beta (lot 3) : serre multibloc : regulateur, puis vitres, cadres et trappes vitrees par matiere (bois traite, fer)
        section(t, "serre multibloc").add("greenhouse_regulator", "treated_greenhouse_glass", "treated_greenhouse_frame", "treated_greenhouse_vent",
              "iron_greenhouse_glass", "iron_greenhouse_frame", "iron_greenhouse_vent");
        //Fonderie : matieres de la poterie refractaire, creusets, coulee ; puis le multibloc de la fonderie (foyer, cuve, controleur) et le
        //four de potier (multibloc depuis le lot 24)
        section(t, "fonderie").add("fire_clay", "unfired_fire_brick", "fire_brick", "quicklime", "unfired_crucible", "crucible",
              "crucible_furnace", "casting_table", "casting_channel", "auto_mold", "fire_brick_firebox", "foundry_vat", "auto_foundry",
              "pottery_kiln");
        //1.0.4-beta (lot 25) : haut fourneau primitif et four a coke : leurs blocs (briques refractaires, tuyere) puis leurs controleurs
        section(t, "haut fourneau").add("fire_bricks", "tuyere", "blast_furnace", "coke_oven");
        //Chaudiere (blocs puis controleur, le foyer), accessoires, puis les machines a vapeur
        section(t, "vapeur").add("copper_boiler_shell", "steel_boiler_shell", "steam_dome", "chimney", "firebox", "safety_valve", "pressure_gauge",
              "water_gauge", "steam_whistle", "steam_engine", "steam_blower",
              //1.0.5-beta (lot 38) : machines de traitement a vapeur (T1 de leurs familles) ; plus de marteau-pilon, crible et tour nouveaux
              "steam_furnace", "steam_macerator", "steam_sifter", "steam_compressor", "steam_extractor", "steam_rolling_mill",
              "steam_wiremill", "steam_saw", "steam_lathe", "steam_mixer", "steam_press", "~B:steam_*");
        section(t, "foreuse").add("drill_base", "drill_frame", "drill_crown", "drill_port", "drill_string_bronze", "steam_drill");
        // ===== Age industriel
        //Etablis de montage (les machines industrielles s'y fabriquent)
        section(t, "atelier").add("assembly_bench", "components_bench", "advanced_components_bench", "logistics_bench");
        //Multiblocs : 1. blocs de construction (lots 21 a 23 : foyers, bobines de chauffe, salle blanche ; rotor, train de forage, pompe a
        //colonne ; voile de beton de la tour de refroidissement, refrigerateur a dilution, baies de serveurs), chaque famille MKI a MKIV
        Section building = section(t, "multiblocs : blocs de construction").add("firebox_basic", "firebox_advanced", "firebox_elite",
              "firebox_ultimate", "heating_coil_basic", "heating_coil_advanced", "heating_coil_elite", "heating_coil_ultimate",
              "turbine_rotor_basic", "turbine_rotor_advanced", "turbine_rotor_elite", "turbine_rotor_ultimate", "drill_string_basic",
              "drill_string_advanced", "drill_string_elite", "drill_string_ultimate", "column_pump_basic", "column_pump_advanced",
              "column_pump_elite", "column_pump_ultimate", "cleanroom_casing", "cleanroom_glass", "cleanroom_filter",
              "concrete_shell_panel", "industrial_dilution_refrigerator", "server_rack_module");
        //modules des machines du catalogue encore enregistres (baies de serveurs), dans l'ordre de la premiere machine qui s'en sert
        building.modules = true;
        //2. carcasses, verres, cadres, toles : chaque famille MKI a MKIV
        section(t, "multiblocs : carcasses et verres").add("industrial_casing_basic", "industrial_casing_advanced", "industrial_casing_elite",
              "industrial_casing_ultimate", "industrial_glass", "industrial_glass_advanced", "industrial_glass_elite", "industrial_glass_ultimate",
              "steel_frame", "aluminium_frame", "titanium_frame", "tungsten_frame", "steel_sheet_metal", "aluminium_sheet_metal",
              "titanium_sheet_metal", "tungsten_sheet_metal", "B:industrial_casing_*", "B:industrial_glass*", "B:*_sheet_metal");
        //3. ports
        section(t, "multiblocs : ports").add("industrial_item_port", "industrial_fluid_port", "industrial_gas_port", "industrial_energy_port",
              "industrial_heat_port", "industrial_pressure_port", "industrial_data_port", "B:industrial_*_port");
        //4. controleurs par domaine (ordre du catalogue dans le domaine) ; 5. compacts par domaine, tier par tier
        for (String[] d : DOMAINS) {
            Section c = section(t, "controleurs : " + d[1]);
            c.machines = m -> domain(m).equals(d[0]) && placeOf(m) == Tab.TECH && !nuclear(m) && !computing(m);
            c.parts = PARTS_CONTROLLERS;
        }
        for (String[] d : DOMAINS) {
            Section c = section(t, "compacts : " + d[1], Order.TIER);
            c.machines = m -> domain(m).equals(d[0]) && placeOf(m) == Tab.TECH && !nuclear(m) && !computing(m);
            c.parts = PARTS_COMPACTS;
            if (d[0].equals("energie")) {
                //Chaleur et renouvelables (MKI..MKIV, motifs : une famille nouvelle « <tier>_<x>_wind_... » se range seule)
                c.add("electric_heater").add(tiers("%s_radiator")).add(tiers("%s_solar_panel")).add(tiers("%s_wind_turbine"))
                      .add("*wind_rotor*", "*_wind_turbine*").add(tiers("%s_hydro_turbine")).add(tiers("%s_geothermal_plant"))
                      .add(tiers("%s_microbial_fuel_cell")).add("*_solar_panel", "*_hydro_turbine", "*_geothermal_plant");
            }
        }
        // ===== Age de l'atome : informatique industrielle (automates, ordinateurs, disques, centres de calcul) et IA
        section(t, "informatique").add("relay_cabinet", "plc", "supervision_screen", "network_terminal", "data_link", "computer", "data_disk")
              .machines = m -> computing(m) && placeOf(m) == Tab.TECH;
        section(t, "intelligence artificielle").add("ai_lab", "factory_ai_core", "neural_chip_blank", "ai_model_neural", "ai_model_expert",
              "ai_model_foundation", "I:ai_model_*");
        //Nucleaire : reacteurs (controleur puis blocs du coeur), stockage, RTG ; fusion
        section(t, "fission").machines = m -> m.kind().equals("REACTOR");
        //1.0.4-beta (lot 23) : barre de commande, pompe primaire a sodium, echangeur intermediaire et injecteur de neutres retires (integres)
        section(t, "coeurs de fission").add("fuel_channel", "fast_fuel_channel", "breeding_blanket", "diamond_reflector", "fragment_collector",
              "confinement_coil", "dry_storage_cask", "waste_package", "rtg");
        section(t, "fusion").machines = m -> m.kind().equals("FUSION");
        section(t, "coeurs de fusion").add("vacuum_chamber", "toroidal_coil", "solenoid_coil", "stellarator_coil", "direct_converter");
    }

    /**
     * 1.0.4-beta (lot 25) : domaines des machines du catalogue (sections « controleurs : » et « compacts : » de l'onglet Tech), dans l'ordre
     * de la progression : cle, nom de section. {@link #domain} : domaine d'une machine (par cle ; une machine nouvelle sans domaine va dans
     * « autres »).
     */
    static final String[][] DOMAINS = {{"energie", "energie"}, {"minerais", "minerais et broyage"}, {"metallurgie", "fours et metallurgie"},
          {"forme", "mise en forme et usinage"}, {"chimie", "chimie et petrochimie"}, {"vivant", "vivant et alimentation"},
          {"electronique", "electronique et assemblage"}, {"extraction", "extraction et stockage"}, {"nucleaire", "cycle du combustible"},
          {"autres", "autres"}};
    private static final Map<String, String> DOMAIN_OF = new HashMap<>();

    static {
        String[][] table = {
              {"energie", "solid_combustion_generator", "dynamo", "boiler", "steam_turbine", "gas_turbine", "liquid_combustion_generator",
                    "cooling_tower", "exhaust_stack"},
              {"minerais", "crusher", "ore_separator", "dryer", "centrifuge", "recycler"},
              {"metallurgie", "blast_furnace", "hot_blast_stove", "coke_oven", "pyrolysis_oven", "converter", "arc_furnace", "furnace",
                    "electric_crucible", "casting_machine"},
              {"forme", "press", "power_hammer", "rolling_mill", "wiremill", "bender", "extruder", "machining_center", "sawmill", "compressor"},
              {"chimie", "chemical_reactor", "crystallizer", "chemical_bath", "mixer", "electrolyzer", "autoclave", "cracker",
                    "distillation_column", "distillery", "gas_separator", "cryogenic_unit", "extractor", "bayer_digester", "kroll_reactor"},
              {"vivant", "fermenter", "food_processor", "hydroponic_farm", "tree_plantation", "aquafarm", "bottler"},
              {"electronique", "photolithograph", "semiconductor_fab", "pick_and_place", "assembler"},
              //1.0.5-beta (lot 46, retour 72) : bloc a eau (apres la pompe multibloc) et generateur de roches
              {"extraction", "drill", "pumpjack", "fluid_pump", "water_source", "rock_generator", "offshore_platform", "storage_tank", "gas_holder",
                    "item_silo"},
              {"nucleaire", "centrifuge_cascade", "spent_fuel_pool", "hot_cell"}};
        for (String[] row : table) {
            for (int i = 1; i < row.length; i++) {
                DOMAIN_OF.put(row[i], row[0]);
            }
        }
    }

    /** 1.0.4-beta (lot 25) : domaine d'une machine du catalogue ({@link #DOMAINS}). */
    static String domain(Machine m) {
        return DOMAIN_OF.getOrDefault(m.key(), "autres");
    }

    // ---------------------------------------------------------------- 2. Logistique

    /** 1.9.2 : Logistique = transport du socle (cables, buffers, air comprime), rail, marine, puis robotique (circuit MK3). */
    private static void layoutLogistics() {
        Tab t = Tab.LOGISTICS;
        //1.11 : cable logistique (6 niveaux, supraconducteur ; MAJ simplification : plus de transparents) ; les anciens cables n'ont plus
        //d'objet (blocs herites)
        List<String> cables = new ArrayList<>();
        for (String level : List.of("wooden", "bronze", "basic", "advanced", "elite", "ultimate")) {
            cables.add(level + "_logistic_cable");
        }
        cables.add("superconducting_cable");
        //1.0.4-beta (lot 30) : cadre de cache (cables et arbres) juste apres les cables
        cables.add("cover_frame");
        //1.0.5-beta (lot 22) : la poubelle (toutes les ressources sauf la donnee), au bout des cables
        cables.add("trash_can");
        //1.0.5-beta (lot 45, retour 65) : l'interface de sac (contenu du sac offert aux cables), apres la poubelle
        cables.add("bag_interface");
        section(t, "cables").add(cables).add("B:*_logistic_cable");
        //Buffers : chaque famille MKI..MKIV puis creatif ; banques de donnees et antenne
        List<String> buffers = new ArrayList<>();
        for (String family : CABLES) {
            buffers.addAll(tiers("%s_" + family + "_buffer"));
            buffers.add("creative_" + family + "_buffer");
        }
        section(t, "buffers").add(buffers).add(tiers("%s_data_bank")).add("creative_data_bank", "data_antenna", "B:*_buffer");
        //1.0.4-beta (lot 1) : l'entrepot (caisse multibloc, poulie d'entrepot, comptoir)
        //1.0.4-beta (lot 27) : poulie et poteau d'entrepot retires (la petite poulie, dans la transmission, est la station)
        section(t, "entrepot").add("warehouse_crate_frame", "warehouse_crate_panel", "warehouse_crate_port", "warehouse_crate_hatch",
              "warehouse_counter", "warehouse_inlet", "warehouse_outlet");
        //1.0.4-beta (lot 10) : les caravanes (relais, bat, feuille de route)
        section(t, "caravanes").add("caravan_relay", "pack_saddle", "caravan_route");
        //Air comprime en reseau et hypertubes
        //1.0.4-beta (lot 27) : caisson pneumatique, vanne de caverne, turbine de detente et ancrage d'hypertube retires
        section(t, "air comprime").add("air_compressor", "pneumatic_tube", "pneumatic_tube_station", "hypertube_*");
        //Rail : voies (profils et traverses, bois puis MKI..MKIV, voies fonctionnelles), ouvrages, signalisation, gare
        section(t, "voies").add("rail_profile", "stainless_rail_profile", "treated_tie", "concrete_tie", "wooden_track").add(tiers("%s_track"))
              .add("buffer_stop", "brake_track", "booster_track", "detector_track", "weighbridge", "uncoupler_track", "level_crossing",
                    "crossing_barrier", "viaduct_pillar", "railing", "tunnel_portal", "platform_slab", "platform_lamp", "block_signal",
                    "chain_signal", "train_station", "B:*_track", "*_signal", "platform_*");
        //Postes de quai (par sorte, MKI..MKIV), electrification
        section(t, "postes").add(tiers("%s_item_loader")).add(tiers("%s_loading_silo")).add(tiers("%s_unloading_pit")).add("coaling_tower",
              "water_crane", "steam_charger").add(tiers("%s_fluid_loader")).add("fuel_pump").add(tiers("%s_gas_loader"))
              .add(tiers("%s_energy_loader")).add("charging_post").add(tiers("%s_data_loader")).add(tiers("%s_air_loader"))
              .add("nuclear_fuel_station", "passenger_platform", "platform_crane", "catenary_pole", "third_rail").add(tiers("%s_substation"))
              .add("B:*_loader", "B:*_substation");
        //Vehicules : tractions puis wagons, chaque sorte MKI..MKIV
        section(t, "vehicules").add("draisine", "mine_wagon").add(tiers("%s_steam_locomotive")).add(tiers("%s_tender"))
              .add(tiers("%s_fireless_locomotive")).add(tiers("%s_diesel_locomotive")).add(tiers("%s_gas_locomotive"))
              .add(tiers("%s_electric_locomotive")).add(tiers("%s_battery_car")).add(tiers("%s_nuclear_locomotive"))
              .add(tiers("%s_maglev_locomotive")).add(tiers("%s_boxcar")).add(tiers("%s_hopper_car")).add(tiers("%s_flat_car"))
              .add(tiers("%s_tank_car")).add(tiers("%s_gas_tank_car")).add(tiers("%s_air_car")).add(tiers("%s_server_car"))
              .add(tiers("%s_passenger_car")).add(tiers("%s_robot_van")).add(tiers("%s_track_layer")).add("I:*_locomotive", "I:*_car");
        section(t, "ateliers ferroviaires").machines = m -> placeOf(m) == Tab.LOGISTICS && m.key().contains("rail");
        //Marine : capitainerie, quai, balises, equipements, navires (par sorte, MKI..MKIV), chantier naval
        section(t, "ports").add(tiers("%s_harbor")).add("harbor_quay", "harbor_interface", "sea_beacon", "navigation_chart", "harbor_crane",
              "harbor_fluid_arm", "harbor_gas_arm", "harbor_air_arm", "harbor_charger", "harbor_data_mast", "harbor_bunker", "harbor_gangway",
              "harbor_*", "*_harbor");
        section(t, "navires").add(tiers("%s_cargo_ship")).add(tiers("%s_tanker")).add(tiers("%s_gas_carrier")).add(tiers("%s_air_carrier"))
              .add(tiers("%s_power_barge")).add(tiers("%s_data_ship")).add(tiers("%s_ferry")).add("I:*_ship");
        section(t, "chantier naval").machines = m -> placeOf(m) == Tab.LOGISTICS && !m.key().contains("rail") && !m.key().contains("robot");
        //Robotique : hub, relais, points et coffres logistiques, cartes, pieces et unites (par genre, MKI..MKIV) ; ateliers robotiques
        section(t, "robotique").add("robotic_casing", "robotic_port", "robotic_bay", "robotic_bay_module", "robotic_relay", "logistic_marker",
              "logistic_chest_tiny", "logistic_chest_small", "logistic_chest_compact", "logistic_chest_medium", "logistic_chest_large",
              "logistic_terminal", "robotic_road", "zone_planner", "robotic_upgrade_*", "ai_upgrade", "robot_core", "robot_chassis", "robot_arm",
              "robot_wheel", "drone_armor_plating", "robotic_*", "logistic_*", "robot_assembl*", "robotic_assembl*")
              .machines = m -> placeOf(m) == Tab.LOGISTICS && m.key().contains("robot");
        section(t, "unites").add("drone_mk*", "robot_mk*", "turtle_mk*", "rover_mk*");
    }

    // ---------------------------------------------------------------- 3. Espace

    private static void layoutSpace() {
        Tab t = Tab.SPACE;
        //Recherche et analyse
        section(t, "recherche").add("research_table", "biome_analyzer", "geo_spectrometer", "bio_sequencer", "biome_data_cell", "asteroid_crusher");
        //Spatioport : assemblage (et son multibloc), pas de tir, ergols ; fusees (chaque tier : fusee, pieces, plan), modules, station
        section(t, "spatioport").add("rocket_integration_hall", "rocket_assembl*", "launch_pad_core", "launch_pad", "launch_port", "launch_tower",
              "flight_computer", "programmed_flight_computer", "launch_*").machines = m -> placeOf(m) == Tab.SPACE && !endgame(m);
        section(t, "fusees", Order.TIER).add("rocket_tier_*", "rocket_nose_cone_tier_*", "rocket_tank_tier_*", "rocket_booster_tier_*",
              "rocket_aux_booster_tier_*", "rocket_wing_tier_*", "rocket_blueprint_tier_*", "rocket_*_tier_*");
        section(t, "modules de fusee").add("rocket_module_*", "rocket_blueprint_cargo", "orbital_station_kit", "turbopump", "cryogenic_turbopump",
              "ntr_core", "fusion_drive_core", "quantum_avionics", "heat_shield_tile", "carbon_composite_panel");
        //Survie et protection
        section(t, "survie").add(tiers("%s_oxygen_diffuser")).add("airlock_door", "life_support_unit").add(tiers("%s_chunk_loader"))
              .add(tiers("%s_force_field_generator")).add(tiers("%s_lightning_collector")).add("lightning_conductor");
        //Bases : serre (et flore spatiale : graine puis recolte), avant-poste minier, rover et tables d'assemblage
        section(t, "serre").add("greenhouse_controller", "greenhouse_port", "hydroponic_substrate", "charge_pad", "greenhouse_*");
        section(t, "flore spatiale").plants = p -> List.of(p.block() + "_seeds", p.product());
        section(t, "avant-poste").add("mining_outpost_controller", "mining_port", "rover_dock", "prospecting_radar", "drill_head_*",
              "rover_chassis", "rover_control_unit", "rover_drill_arm", "rover_wheel", "rover_module_*", "module_assembly_table",
              "equipment_assembly_table", "*_assembly_table*", "rover_*");
        //Geantes gazeuses
        section(t, "geantes gazeuses").add("aerostat_kit", "aerostat_deck", "aerostat_landing_pad", "aerostat_hull", "aerostat_buoyancy_cell",
              "drone_bay", "drone_pad", "aerostat_*");
        //Fin de jeu : stabilisateur, compresseur gravitationnel, synthetiseur, planetes simulees, simulateur, absorbeur
        section(t, "singularite").add("space_stabilizer_controller", "space_stabilizer_casing", "space_stabilizer_glass", "space_stabilizer_port",
              "space_stabilizer_power_port", "space_stabilizer_emitter", "singularity_core", "stellar_core", "void_singularity_cell",
              "space_stabilizer_*").machines = m -> placeOf(m) == Tab.SPACE && endgame(m);
        section(t, "planetes simulees").add(each(List.of("mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune"),
              "compacted_%s", "empty_simulated_%s", "proto_simulated_%s", "simulated_%s")).add("compacted_*", "*simulated_*");
        section(t, "simulateur").add("space_simulator_controller", "space_simulator_casing", "space_simulator_glass", "space_simulator_port",
              "space_rift_projector", "supreme_space_coil", "solar_absorber_controller", "solar_absorber_casing", "solar_absorber_collector",
              "solar_absorber_port", "quasar_lamp", "space_simulator_*", "solar_absorber_*");
    }

    // ---------------------------------------------------------------- 4. Composants

    private static void layoutComponents() {
        Tab t = Tab.COMPONENTS;
        //Age du bronze : moules (cru puis cuit) et matrices ; formes de metal ; pieces d'outils (tetes, lames, bols) par sorte d'outil
        section(t, "moules").add(each(List.of("ingot", "nugget", "plate", "rod", "gear"), "unfired_%s_mold", "%s_mold")).add("I:*_mold");
        section(t, "matrices").add("ingot_die", "plate_die", "rod_die", "gear_die");
        //MAJ Recettes (lot 4b) : fonderie industrielle : sable, moules perdus et modeles en cire, du MKI au MKIII
        section(t, "fonderie industrielle").add("foundry_sand", "spent_foundry_sand", "sand_mold", "wax_pattern", "ceramic_shell_mold",
              "vacuum_casting_mold");
        //Formes de metal : toutes les plaques, puis toutes les tiges... (metaux dans l'ordre de la progression)
        List<String> forms = new ArrayList<>();
        for (String form : FORMS) {
            forms.addAll(each(METALS, "%s_" + form));
        }
        section(t, "formes").add(forms).add("depleted_uranium_plate", "lunar_alloy_plate", "tharsium_magnet", "zircaloy_tube",
              "steel_cladding_tube", "finned_tube", "nichrome_coil", "copper_coil", "superconducting_coil", "nbti_cable", "sulcite_hts_tape");
        List<String> heads = new ArrayList<>();
        for (String tool : TOOLS) {
            heads.addAll(each(List.of("flint", "stone"), "%s_" + tool + "_head", "%s_" + tool + "_blade"));
            heads.addAll(each(METALS, "%s_" + tool + "_head", "%s_" + tool + "_blade", "%s_" + tool + "_bowl"));
        }
        section(t, "pieces d'outils").add(heads);
        //Refonte des composants : une section par famille livree, dans l'ordre de la nomenclature
        for (GeneratedComponents.Family f : GeneratedComponents.FAMILIES) {
            Section s = section(t, "composants : " + f.name());
            s.refonte = true;
            s.add(f.ids());
            if ("textiles".equals(f.name())) {
                s.add("cotton_thread");                    //1.0.4-beta (lot 28) : fil de coton, avec les fibres et la toile
            }
        }
        //Plastiques, caoutchouc et composites (feuilles, tubes, joints)
        section(t, "plastiques").add("primitive_plastic", "plastic_sheet", "pvc_sheet", "ptfe_sheet", "rubber_sheet", "fiberglass_sheet",
              "plastic_pipe", "plastic_casing", "gasket", "conveyor_belt");
        //Electronique dans l'ordre ou on la fabrique : discrets primitifs, circuit MKI, filiere silicium, circuits suivants, calcul
        section(t, "electronique").add("vacuum_tube", "perforated_board", "etched_board", "carbon_resistor", "paper_capacitor", "choke_coil", "relay",
              "silicon_diode", "electrolytic_capacitor", "quartz_oscillator", "circuit_mk1", "polysilicon", "silicon_boule", "silicon_wafer",
              "doped_wafer", "photomask", "exposed_wafer", "etched_wafer", "microchip", "transistor", "capacitor", "resistor", "epoxy_board",
              "printed_circuit_board", "circuit_mk2", "processor", "circuit_mk3", "circuit_mk4", "circuit_*", "ram_module", "server_blade",
              "gpu_accelerator", "josephson_junction", "qubit_chip");
        //Pieces mecaniques : pieces simples puis chaque piece MKI..MKIV cote a cote ; filieres, electrodes ; chassis et kits d'amelioration
        section(t, "pieces mecaniques").add("plain_bearing", "bearing").add(tiers("%s_bearing")).add("piston_ring", "piston_rod", "honed_cylinder",
              "piston").add(tiers("%s_piston")).add("crankshaft", "camshaft", "poppet_valve", "engine_block").add(tiers("%s_engine_block"))
              .add("motor_stator", "motor_rotor", "electric_motor").add(tiers("%s_electric_motor")).add("pump_impeller", "pump_housing",
                    "centrifugal_pump").add(tiers("%s_centrifugal_pump")).add("bladed_rotor").add(tiers("%s_bladed_rotor")).add("planetary_gear")
              .add(tiers("%s_planetary_gear")).add("heat_exchanger").add(tiers("%s_heat_exchanger")).add("extrusion_die", "press_die", "tube_die",
                    "graphite_electrode")
              //MAJ Recettes (lot 8a) : objectifs du graveur optique (outillage use d'un point par gravure), neufs puis uses
              .add("glass_engraving_objective", "uv_engraving_objective", "euv_engraving_objective", "worn_glass_engraving_objective",
                    "worn_uv_engraving_objective", "worn_euv_engraving_objective")
              .add("machine_frame_*", "machine_hull_*", "upgrade_kit_*");
        //Pieces du nucleaire, de la fusion et des matieres des astres
        section(t, "pieces avancees").add("fuel_rod", "mox_rod", "spacer_grid", "lithium_target", "irradiated_lithium_target", "neptunium_target",
              "irradiated_neptunium_target", "lithium_ceramic_pebbles", "tungsten_tile", "plasma_heating_klystron", "diamond_lattice",
              "veronite_superconductor", "messinite_ceramic", "ruachite_crystal_grid", "venusium_liner", "mercurium_heat_spreader",
              "valhallite_shielding");
        //Pieces et electronique de l'industrie : electronique, puis chaque piece MKI..MKIV cote a cote (ordre du catalogue)
        Section industry = section(t, "pieces de l'industrie", Order.CATEGORY);
        industry.soft = true;
        industry.add(IndustryContent.ITEMS.stream().filter(id -> rawKey(id).cat().component()).toList());
        //1.0.4-beta (lot 27, Guillaume : « c'est une piece de la plate-forme offshore, pas une machine ») : tube de production
        industry.add("production_tubing");
        section(t, "par categorie", Order.CATEGORY).cats.addAll(List.of(Cat.FORM, Cat.TOOL_PART, Cat.ELECTRONIC, Cat.PART));
    }

    // ---------------------------------------------------------------- 5. Construction

    /** 1.9.2 : Construction = mobilier et decoration (etageres, cendrier...), place du village et commerce, blocs tailles des astres. */
    private static void layoutBuilding() {
        Tab t = Tab.BUILDING;
        //1.0.3-beta (lot 7) : meubles par bois (table, chaise, meuble de rangement, etagere a plats), dans l'ordre des bois ; crochet a corde
        List<String> home = new ArrayList<>();
        for (String w : fr.pioneers.building.home.HomeWoods.ALL) {
            //1.0.3-beta (lot 12) : etagere au sol puis etagere murale du bois
            home.addAll(List.of(w + "_table", w + "_chair", w + "_cabinet", w.equals("oak") ? "dish_shelf" : w + "_dish_shelf", w + "_wall_shelf"));
        }
        //1.0.3-beta (lot 12) : panier demi-hauteur (id d'origine) puis panier haut
        section(t, "meubles par bois").add(home).add("rope_hook", "basket", "tall_basket");
        section(t, "mobilier et decoration").add("B:*_shelf", "B:*_cupboard", "B:*_table_decor", "ashtray", "restorer_desk",
              "aquarium", "calendar");                     //1.0.4-beta (lot 28) : aquarium ; 1.0.5-beta (lot 1) : calendrier
        //1.0.5-beta (lot 5) : niches (chiens) et paniers (chats), dans l'ordre des bois des meubles
        section(t, "niches et paniers").add(each(fr.pioneers.building.home.HomeWoods.ALL, "%s_dog_kennel")).add(each(fr.pioneers.building.home.HomeWoods.ALL,
              "%s_cat_basket"));
        //1.0.5-beta (lot 7) : nichoirs a oiseaux (dans l'ordre des bois des meubles) et piege a rat
        section(t, "nichoirs et pieges").add(each(fr.pioneers.building.home.HomeWoods.ALL, "%s_bird_house")).add("rat_trap");
        //1.0.5-beta (lot 10) : porte-fusils (dans l'ordre des bois des meubles) et trophees de chasse
        section(t, "chasse").add(each(fr.pioneers.building.home.HomeWoods.ALL, "%s_gun_rack")).add("deer_trophy", "bear_trophy", "shark_trophy");
        //1.0.4-beta (lot 28) : poutres decoratives, dans l'ordre des bois des meubles, puis le bois traite
        section(t, "poutres").add(each(java.util.stream.Stream.concat(fr.pioneers.building.home.HomeWoods.ALL.stream(), java.util.stream.Stream.of("treated"))
              .toList(), "%s_timber_beam"));
        //1.0.4-beta (lot 5) : eclairage (interrupteurs par age, puis luminaires du bronze a l'electrique)
        section(t, "eclairage").add("wooden_light_lever", "bronze_light_lever", "electric_light_switch", "bronze_wall_sconce", "bronze_lantern",
              "bronze_chandelier", "electric_wall_lamp", "electric_ceiling_lamp", "industrial_cage_lamp");
        section(t, "village et commerce").add("needs_board", "village_fountain", "village_lamppost", "peddler_crate", "market_stall",
              "market_counter", "sale_bin", "mailbox", "letter_bin", "archive_bin", "B:village_*", "B:market_*");   //1.0.4-beta (lot 29) : boite aux lettres ; 1.0.5-beta (lot 16) : bacs a lettres et d archives
        //Blocs tailles de tous les astres, par astre puis par pierre (declares avant les blocs naturels : a precision egale,
        //« smooth_lunar_basalt » est un bloc taille)
        section(t, "blocs tailles", Order.BUILD).add("B:cobbled_*", "B:packed_*", "B:polished_*", "B:smooth_*", "B:chiseled_*",
              "B:cracked_*", "B:cut_*", "B:*_bricks", "B:*_brick_slab", "B:*_brick_stairs", "B:*_brick_wall", "B:*_sandstone*", "B:*_slab",
              "B:*_stairs", "B:*_wall");
    }

    // ---------------------------------------------------------------- 6. Mondes

    private static void layoutWorlds() {
        Tab t = Tab.WORLDS;
        //Blocs naturels de chaque astre (Terre d'abord), plantes sauvages, meteorites
        Section natural = section(t, "blocs naturels", Order.WORLD).add("B:*_outcrop", "alluvial_gravel", "black_sand", "oil_seep",
              "tar_sand", "B:*_ore", "B:*_ore_basalt", "stromatolite", "diogenite", "bloomstone", "flare_crystal", "B:glowcap_*", "storm_cloud",
              "deep_haze", "dry_ice");
        //Mots de la nature, en motifs faibles (~) : un bloc taille (« smooth_phobos_breccia ») reste a la Construction
        for (String word : List.of("rock", "regolith", "sand", "ice", "frost", "snow", "dust", "basalt", "breccia", "crust", "salt", "clay",
              "gypsum", "permafrost", "tholin", "sulfur", "soil", "silt", "moss", "glowmoss", "shale", "scoria", "slag", "ash", "pebbles", "haze")) {
            natural.add("~B:*_" + word);
        }
        natural.add("~B:*_cloud*");
        section(t, "plantes sauvages").plants = p -> List.of(p.block());
        section(t, "meteorites").add("B:*_meteorite");
        //1.9.2 : feuillages des arbres fruitiers (les pousses et les fruits vont au Core)
        section(t, "feuillages").add(FarmIds.LEAVES).add("B:*_leaves");
        //1.9.2 : faune (oeufs d'apparition)
        //1.0.3-beta lot 8 : oeufs des races (espece par espece) puis des poissons (ordre des especes), puis les autres
        //1.0.4-beta (lot 28) : oeufs des races de lapins, chevaux, anes et lamas, apres ceux des autres elevages
        section(t, "faune").add(FarmIds.BREED_EGGS).add("I:*_rabbit_spawn_egg", "I:*_horse_spawn_egg", "I:*_donkey_spawn_egg",
              "I:*_llama_spawn_egg").add(FarmIds.FISH_EGGS).add("I:*_spawn_egg");
        //1.9.2 (ex-onglet « Le monde habite ») : ruines (blocs delabres par sorte, MK1..MK3 cote a cote)
        section(t, "ruines").add("derelict_*", "perimeter_console_*", "archive_cabinet_*", "tech_rubble_*", "restoration_plaque");
    }

    // ---------------------------------------------------------------- 7. Core

    /** 1.9.2 : Core = matieres (metaux, minerais, gemmes, combustibles, chimie, astres) puis nourriture (graines -> plats -> boissons). */
    private static void layoutCore() {
        Tab t = Tab.CORE;
        //Minerais de surface de l'age du bronze, puis chaque metal terrestre : brut, concasse, broye, concentre, poudre, pepite, lingot, bloc
        section(t, "fragments").add("malachite_fragment", "cassiterite_fragment", "hematite_fragment", "magnetite_fragment", "gold_flake",
              "I:*_fragment");
        //MAJ Recettes (lot 4a) : demi-produits (brame, billette, ebauche de poutrelle, fil machine, ebauche d'engrenage) apres le lingot
        section(t, "metaux").add(each(METALS, "raw_%s", "raw_%s_block", "crushed_%s", "ground_%s", "%s_concentrate", "%s_scrap", "%s_dust",
              "%s_nugget", "%s_ingot", "%s_slab", "%s_billet", "%s_beam_blank", "%s_wire_rod", "%s_gear_blank", "%s_block"))
              .add("slag", "pig_iron", "steel_bloom", "hot_steel_bloom", "thomas_slag", "blister_copper", "copper_matte",
                    "anode_slime");
        //MAJ Recettes (lot 8a) : briquette de bois et balle de bagasse du compacteur, a cote de leur matiere
        section(t, "combustibles").add("coal", "coal_block", "charcoal", "charcoal_block", "coal_ball", "coke", "petroleum_coke", "biochar", "wood_pellets",
              "wood_chips", "wood_briquette", "bagasse", "bagasse_bale", "miscanthus", "press_cake");
        //Matieres de l'age mecanique et du vivant
        section(t, "vivant").add("plant_fiber", "hemp_fiber", "corn_starch", "flour", "sawdust",
              "wood_ash", "flint_dust", "canvas", "raw_resin", "rosin", "raw_latex", "natural_rubber", "microalgae", "fish_meal", "fish_feed",
              "crushed_shells", "soap", "fertilizer", "urea", "ammonium_nitrate");
        //Minerais des gisements industriels (chaine : minerai, concasse, broye, concentre), dans l'ordre du catalogue ; gemmes
        section(t, "minerais").add(each(IndustryContent.ORES, "%s", "crushed_%s", "ground_%s", "%s_concentrate", "%s_dust"))
              .add("crushed_redstone_ore", "redstone_ore", "redstone", "redstone_block");
        section(t, "gemmes").add(each(GEMS, "rough_%s", "%s_boule", "%s", "%s_dust", "%s_block"));
        //Matieres de l'industrie dans l'ordre du catalogue (metallurgie, chimie, plastiques, vivant, nucleaire, espace) ; les pieces vont aux
        //Composants
        Section industry = section(t, "matieres de l'industrie", Order.CATALOG);
        industry.soft = true;
        industry.add(IndustryContent.ITEMS.stream().filter(id -> !rawKey(id).cat().component()).toList());
        //Metaux des astres, du plus proche au plus lointain, puis matieres cosmiques (fin de jeu)
        //MAJ Recettes (lot 4a) : billette forgee, plaque et tige de chaque metal des astres
        section(t, "metaux des astres").add(each(SPACE_METALS, "raw_%s", "raw_%s_block", "%s_dust", "%s_nugget", "%s_ingot", "%s_billet",
              "%s_plate", "%s_rod", "%s_block")).add("asteroid_pgm_concentrate");
        section(t, "cosmos").add("stardust", "stardust_block", "metallic_hydrogen", "rain_diamond", "quasar_crystal", "neutronium_dust",
              "neutronium_ingot", "neutronium_billet", "neutronium_plate", "neutronium_rod", "neutronium_block", "dark_matter", "dark_matter_block");
        //Repli : objet qu'aucun motif ne prend, par categorie
        section(t, "par categorie", Order.CATEGORY).cats.addAll(List.of(Cat.METAL, Cat.GEM, Cat.FUEL, Cat.ORGANIC, Cat.CHEMICAL, Cat.COSMIC));
        //1.9.2 (ex-onglet « Le monde habite ») : scraps de recuperation (par sorte, MK1..MK3 cote a cote)
        section(t, "recuperation").add("I:scrap_*");
        //Nourriture (ex-onglet Ferme et cuisine), listes generees par farm2/gen_java.py (FarmIds) : graines et pousses, recoltes et fruits,
        //produits de la ferme (objets des listes de la ferme animee et de l'elevage), peche, ingredients, plats, contenants, boissons
        section(t, "graines").add(only(FarmIds.SEEDS, id -> !farmElsewhere(id))).add(FarmIds.SAPLINGS);
        section(t, "recoltes").add(FarmIds.PRODUCE).add(FarmIds.FRUITS);
        //1.0.4-beta (lot 6) : blocs de stockage des recoltes (caisses, cagettes, sacs) : MEME ORDRE que la table STORAGE de
        //T:farm2/crop_storage.py (verifie par tools/check_p104_6.py)
        section(t, "stockage des recoltes").add(
              "carrot_crate", "potato_crate", "beetroot_crate", "parsnip_crate", "radish_crate", "sweet_potato_crate", "onion_crate",
              "garlic_crate", "leek_crate", "red_cabbage_crate", "cauliflower_crate", "broccoli_crate", "artichoke_crate", "corn_crate",
              "apple_crate", "tomato_tray", "bell_pepper_tray", "hot_pepper_tray", "eggplant_tray", "zucchini_tray", "cucumber_tray",
              "strawberry_tray", "blueberry_tray", "cranberry_tray", "grapes_tray", "barley_sack", "oats_sack", "rye_sack",
              "paddy_rice_sack", "soybean_sack", "sunflower_seeds_sack", "hops_sack");
        section(t, "produits de la ferme").add(each(only(FarmIds.DEVICES, id -> !farmElsewhere(id)), "I:%s"))
              .add(each(FarmIds.LIVESTOCK, "I:%s"));
        section(t, "peche").add(only(FarmIds.FISHING, id -> !farmElsewhere(id))).add(FarmIds.FISH);
        section(t, "crustaces").add(each(List.of("green_crab", "lobster", "crayfish", "shrimp", "king_crab"), "raw_%s", "cooked_%s"))
              .add("seashell", "I:cooked_*", "crustacean_bucket");        //1.0.4-beta (lot 28) : seau de crustace (aquarium)
        //1.0.5-beta (lot 6) : le gibier (viandes crues puis cuites, espece par espece, et le bois de cerf), avant le repli « cooked_* »
        section(t, "gibier").add("raw_venison", "cooked_venison", "raw_boar_meat", "cooked_boar_meat", "raw_bison_meat", "cooked_bison_meat",
              "raw_turkey", "cooked_turkey", "deer_antler", "raw_bear_meat", "cooked_bear_meat", "raw_pigeon", "cooked_pigeon",   //lot 7 : ours, pigeon
              "raw_big_game_meat", "cooked_big_game_meat",                                                                           //lot 8 : gros gibier
              "raw_whale_meat", "cooked_whale_meat", "whale_blubber", "raw_shark_meat", "cooked_shark_meat", "raw_ray_wing",       //lot 9 : la mer
              "cooked_ray_wing", "raw_seal_meat", "cooked_seal_meat");
        section(t, "ingredients").add(FarmIds.INGREDIENTS).add(FarmIds.PREPARATIONS);
        section(t, "plats").add(FarmIds.DISHES);
        section(t, "contenants").add(only(FarmIds.CONTAINERS, id -> !farmElsewhere(id)));
        section(t, "boissons").add(FarmIds.DRINKS);
        //1.5 : tabac (pipes, cigare, allumettes) ; le cendrier est du mobilier, les instruments vont a l'Equipement
        section(t, "tabac").add("corncob_pipe", "briar_pipe", "clay_pipe", "matchbox", "cigar", "lit_cigar");
        //1.0.4-beta (lot 6) : chanvre (tetes fraiches puis sechees, joint, joint allume)
        section(t, "chanvre").add("hemp_flower", "dried_hemp_flower", "hemp_joint", "lit_hemp_joint");
        //1.0.4-beta (lot 9) : laine angora (lapin angora, clapier)
        section(t, "lapins").add("angora_wool");
    }

    // ---------------------------------------------------------------- 8. Fluides et gaz

    private static void layoutFluids() {
        Tab t = Tab.FLUIDS;
        //1.6 (unification) : seaux vanilla (seau vide, eau, lave, lait, neige poudreuse) en tete ; puis cuisine, metaux en fusion (age du
        //bronze), fluides de base (petrole), fluides de l'industrie (ordre du catalogue), capsules de gaz
        section(t, "seaux").add("minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:milk_bucket",
              "minecraft:powder_snow_bucket");
        section(t, "fluides de la cuisine").add(FarmIds.FLUIDS);
        section(t, "metaux en fusion").add(IndustryContent.MOLTEN.stream().map(f -> "molten_" + f.metal() + "_bucket").toList())
              .add("I:molten_*_bucket");
        section(t, "fluides de base").add("raw_oil_bucket", "heavy_oil_bucket", "light_oil_bucket", "diesel_bucket", "creosote_bucket",
              "liquid_nitrogen_bucket");
        section(t, "fluides de l'industrie").add(IndustryContent.FLUIDS.stream().map(f -> f.id() + "_bucket").toList()).add("I:*_bucket");
        section(t, "capsules").add("gas_capsule", "I:*capsule*");
        section(t, "par categorie", Order.CATEGORY).cats.add(Cat.FLUID);
    }

    // ---------------------------------------------------------------- 9. Equipement

    private static void layoutEquipment() {
        Tab t = Tab.EQUIPMENT;
        //1.9.2 (ex-onglet « Le monde habite ») : le Carnet (premier objet du pionnier), ecrits, manuels, carte des ruines
        section(t, "carnet et ecrits").add("restorer_notebook", "blank_notebook", "parchment_note", "lore_page", "memory_module", "ruin_map",
              "I:manual_*", "I:*_notebook", "contract_invoice", "letter");   //1.0.4-beta (lot 29) : lettre de la boite aux lettres
        section(t, "guide").add("wrench");
        //Outils par materiau (pierre, silex, puis metaux dans l'ordre de la progression), chaque sorte dans l'ordre de TOOLS ; cannes a peche
        List<String> tools = new ArrayList<>(List.of("stone_hammer", "stone_mortar"));
        tools.addAll(each(TOOLS, "flint_%s"));
        for (String metal : METALS) {
            tools.addAll(each(TOOLS, metal + "_%s"));
        }
        section(t, "outils").add(tools).add("collier_pole");
        section(t, "peche").add("I:*_fishing_rod");
        //1.0.5-beta (lot 10) : armes de chasse et munitions (age du bronze puis MKI)
        section(t, "chasse").add("musket", "lead_ball", "harpoon", "revolver", "cartridge");
        //1.0.3-beta (lot 7) : sacs a dos et sacs de rangement
        section(t, "sacs").add("leather_backpack", "tanned_backpack", "seed_bag", "sapling_bag", "fish_bag");
        section(t, "outils pneumatiques").add("impact_wrench", "air_blower", "pneumatic_jackhammer", "sandblaster", "diving_tank");
        //Armures par metal, puis combinaisons (protection, survie, espace)
        List<String> armor = new ArrayList<>();
        for (String metal : METALS) {
            armor.addAll(each(ARMOR, metal + "_%s"));
        }
        section(t, "armures").add(armor).add(each(List.of("hazmat", "acid_suit", "thermal_suit", "atmospheric_suit", "space_suit"), "%s_helmet",
              "%s_chestplate", "%s_leggings", "%s_boots")).add("dosimeter");
        //1.12.0 (lot E4) : cosmetiques, tenue par tenue (coiffe, haut, bas, chaussures), dans l'ordre du catalogue
        section(t, "cosmetiques").add(each(List.of(fr.pioneers.equipment.cosmetic.CosmeticCatalog.OUTFITS), "cosmetic_%s_hat", "cosmetic_%s_top",
              "cosmetic_%s_bottom", "cosmetic_%s_shoes"));
        //1.0.3-beta (lot 9) : equipement electrique de l'age MKI / MKII (avant le modulaire) : set Voltaique, jetpack, lance-flammes
        section(t, "equipement electrique").add("voltaic_cell", "voltaic_drill", "voltaic_chainsaw", "voltaic_trimmer", "voltaic_baton",
              "voltaic_helmet", "voltaic_chestplate", "voltaic_leggings", "voltaic_boots", "hydrogen_jetpack", "flamethrower");
        //Equipement modulaire : etabli, cadre et modules, puis chaque ensemble (piece, cellule, outils, lame, fusil, armure)
        section(t, "equipement modulaire").add("modulation_bench", "module_frame", "I:module_*");
        section(t, "ensembles").add(each(List.of("graviton", "stellaris", "neutronic", "singularity"), "%s_plating", "%s_cell", "%s_pickaxe",
              "%s_axe", "%s_shovel", "%s_hoe", "%s_multitool", "%s_blade", "%s_rifle", "%s_helmet", "%s_chestplate", "%s_leggings", "%s_boots"));
        //Repli des outils et armures futurs (motifs faibles : un id exact ou plus precis passe avant)
        section(t, "autres outils et armures").add(each(ARMOR, "~I:*_%s")).add(each(List.of("pickaxe", "axe", "shovel", "hoe", "sword",
              "hammer", "wrench"), "~I:*_%s"));
        //Scanners et divers
        //1.0.4-beta (lot 27, Guillaume : « c'est un outil de prospection ») : sismographe avec les scanners
        section(t, "divers").add("surface_scanner", "gas_scanner", "seismograph");
        //1.9.2 (ex-onglet « Le monde habite ») : clans (Charte, Drapeau de siege, Sceau de fonction, Pacte federal)
        section(t, "clans").add("clan_charter", "siege_flag", "function_seal", "federal_pact");
        //Loisirs : instruments, puis musique (disque vierge, disques de la Terre dans l'ordre de la progression, puis les disques spatiaux)
        section(t, "loisirs et musique").add("flute", "guitar", "accordion", "drum", "blank_music_disc", "music_disc_aube_sur_les_labours",
              "music_disc_le_chant_des_bles", "music_disc_les_ailes_du_moulin", "music_disc_l_enclume_et_le_soufflet",
              "music_disc_le_souffle_de_la_vapeur", "music_disc_les_hautes_cheminees", "music_disc_cornues_et_alambics",
              "music_disc_le_train_de_la_cote", "music_disc_lueur_de_tcherenkov", "music_disc_veillee_aux_etoiles", "music_disc_*");
    }

    private static void layoutHidden() {
        //Objets techniques, jamais montres : apparence du tir d'energie, decor des accidents et du charbonnier, carcasse brulee
        section(null, "techniques").add("energy_bolt", "energy_bolt_*", "corium", "cooled_corium", "corium_chunk", "ash_layer", "charred_log_pile",
              "industrial_casing_scorched", "machine_bounds", "heated_metal");
    }

    // ------------------------------------------------------------------
    // Machines du catalogue
    // ------------------------------------------------------------------

    private static boolean energy(Machine m) {
        return m.kind().equals("GENERATOR") || m.kind().equals("REACTOR") || m.kind().equals("FUSION") || m.key().equals("boiler")
               || m.key().equals("cooling_tower");
    }

    private static boolean nuclear(Machine m) {
        return m.kind().equals("REACTOR") || m.kind().equals("FUSION");
    }

    private static boolean computing(Machine m) {
        return m.kind().equals("COMPUTE") || m.kind().equals("QUANTUM");
    }

    /**
     * 1.9.2 : onglet d'une machine du catalogue, par motif de cle (une machine nouvelle se range seule) : rail, marine et robotique a la
     * Logistique ; fusees, lancement, ergols et fin de jeu a l'Espace ; tout le reste a Tech.
     */
    static Tab placeOf(Machine m) {
        String k = m.key();
        if (k.contains("rail") || k.contains("ship") || k.contains("harbor") || k.contains("naval") || k.contains("robot")) {
            return Tab.LOGISTICS;
        }
        if (k.contains("rocket") || k.contains("launch") || k.contains("propellant") || endgame(m)) {
            return Tab.SPACE;
        }
        return Tab.TECH;
    }

    /** Machines de la fin de jeu (planetes simulees). */
    private static boolean endgame(Machine m) {
        return m.key().equals("gravity_compressor") || m.key().equals("synthesizer");
    }

    /** Ids d'une machine du catalogue : ses tiers puis son multibloc (le bloc de l'age du bronze reste dans son onglet). */
    private static List<String> machineIds(Machine m) {
        return machineIds(m, PARTS_ALL);
    }

    /** 1.0.4-beta (lot 25) : ids d'une machine pris par une section ({@link #PARTS_ALL}, {@link #PARTS_COMPACTS}, {@link #PARTS_CONTROLLERS}). */
    private static List<String> machineIds(Machine m, int parts) {
        List<String> controller = switch (m.atelier()) {
            //1.0.3-beta (lot 5) : id du controleur de la machine (multiblocs seuls qui gardent leur id industrial_<cle>)
            case "NONE" -> List.of(m.multiId());
            case "BRONZE" -> List.of("industrial_" + m.key());
            default -> List.of(m.multiId());
        };
        List<String> compacts = m.atelier().equals("NONE") || m.atelier().equals("BRONZE") ? List.of() : tiers("%s_" + m.key());
        if (parts == PARTS_CONTROLLERS) {
            return controller;
        }
        if (parts == PARTS_COMPACTS) {
            return compacts;
        }
        List<String> out = new ArrayList<>(compacts);
        out.addAll(controller);
        return out;
    }

    // ------------------------------------------------------------------
    // Index
    // ------------------------------------------------------------------

    /** Plan resolu pour un catalogue de machines : index des ids exacts, motifs tries, sections par categorie. */
    public static final class Layout {

        private final Map<String, Placement> exact = new HashMap<>();
        private final Map<String, Placement> softExact = new HashMap<>();
        private final List<Glob> globs = new ArrayList<>();
        private final Map<Cat, Section> byCat = new LinkedHashMap<>();
        private final Section plants;
        private final Section fallback;
        /** Motifs declares deux fois (erreur de plan, signalee par le banc). */
        public final List<String> duplicates = new ArrayList<>();

        private record Glob(Section section, int rank, Pattern pattern, Boolean block, int fixed, int order) {
        }

        Layout(List<Machine> machines, List<Plant> plantList) {
            Section plantSection = null;
            Section last = null;
            int order = 0;
            Set<VanillaSpot> placed = new HashSet<>();
            for (Section s : LAYOUT) {
                List<String> patterns = new ArrayList<>(s.patterns);
                if (s.machines != null) {
                    for (Machine m : machines) {
                        if (s.machines.test(m)) {
                            patterns.addAll(machineIds(m, s.parts));
                        }
                    }
                }
                if (s.modules) {
                    machines.stream().filter(m -> !m.module().isEmpty()).map(m -> m.module() + "_module").distinct().forEach(patterns::add);
                }
                if (s.plants != null) {
                    plantList.forEach(p -> patterns.addAll(s.plants.apply(p)));
                    if (s.tab == Tab.WORLDS) {
                        plantSection = s;
                    }
                }
                placeVanilla(s, patterns, placed);
                for (int i = 0; i < patterns.size(); i++) {
                    String p = patterns.get(i);
                    Boolean block = null;
                    boolean weak = p.startsWith("~");
                    if (weak) {
                        p = p.substring(1);
                    }
                    if (p.startsWith("B:") || p.startsWith("I:")) {
                        block = p.startsWith("B:");
                        p = p.substring(2);
                    }
                    //1.9.2 : « B:id » / « I:id » sans etoile = id exact limite aux blocs / aux objets (motif le plus precis possible)
                    if (p.contains("*") || block != null) {
                        Pattern regex = Pattern.compile(("\\Q" + p + "\\E").replace("*", "\\E.*\\Q"));
                        globs.add(new Glob(s, i, regex, block, weak ? 0 : p.replace("*", "").length(), order++));
                    } else if (!s.refonte && REFONTE_IDS.contains(p)) {
                        continue;
                    } else if (s.soft) {
                        softExact.putIfAbsent(p, new Placement(s, i));
                    } else if (exact.putIfAbsent(p, new Placement(s, i)) != null && exact.get(p).section() != s) {
                        duplicates.add(p + " : " + exact.get(p).section() + " / " + s);
                    }
                }
                for (Cat cat : s.cats) {
                    byCat.putIfAbsent(cat, s);
                }
                if (s.tab == Tab.CORE) {
                    last = s;
                }
            }
            //1.6 : objet vanilla dont l'ancre a disparu : en fin de sa section de repli
            for (VanillaSpot v : VANILLA) {
                if (!placed.contains(v)) {
                    for (Section s : LAYOUT) {
                        if (s.tab == Tab.CORE && s.name.equals(v.fallback())) {
                            for (String id : v.ids()) {
                                exact.putIfAbsent(id, new Placement(s, 10_000));
                            }
                            break;
                        }
                    }
                }
            }
            globs.sort(Comparator.comparingInt(Glob::fixed).reversed().thenComparingInt(Glob::order));
            plants = plantSection;
            fallback = last;
        }

        /**
         * Place d'un objet. {@code block} : l'objet pose un bloc ; {@code all} : tous les ids du mod (une plante sauvage est un bloc dont
         * la graine {@code <id>_seeds} existe).
         */
        public Placement place(String id, boolean block, Set<String> all) {
            Placement p = exact.get(id);
            if (p != null) {
                return p;
            }
            for (Glob g : globs) {
                if ((g.block() == null || g.block() == block) && g.pattern().matcher(id).matches()) {
                    return new Placement(g.section(), g.rank());
                }
            }
            p = softExact.get(id);
            if (p != null) {
                return p;
            }
            if (block && plants != null && all.contains(id + "_seeds")) {
                return new Placement(plants, 0);
            }
            Section s = byCat.get(rawKey(id).cat());
            return new Placement(s == null ? fallback : s, 0);
        }

        /** 1.6 : objets vanilla ranges dans nos onglets (id complet « minecraft:... ») et leur place. */
        public Map<String, Placement> vanilla() {
            Map<String, Placement> out = new LinkedHashMap<>();
            exact.forEach((id, p) -> {
                if (id.indexOf(':') > 0) {
                    out.put(id, p);
                }
            });
            return out;
        }

        /** Tri des ids d'une meme section. */
        public List<String> sort(Section section, List<Map.Entry<String, Integer>> idsWithRank) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(idsWithRank);
            list.sort(comparator(section.order));
            return list.stream().map(Map.Entry::getKey).toList();
        }
    }

    // ------------------------------------------------------------------
    // 1.6 : objets vanilla dans nos onglets
    // ------------------------------------------------------------------

    /**
     * Objets vanilla ranges a cote d'un objet du mod (demande de Guillaume : « unifier les pommes, carottes... et les envoyer vers l'onglet
     * Ferme et cuisine ») : section de repli, ancre (id du mod ; {@code before} : avant l'ancre, sinon apres), ids vanilla sans espace de noms.
     * Ils quittent les onglets vanilla ({@code core/vanilla/VanillaTabs}).
     */
    record VanillaSpot(String fallback, String anchor, boolean before, List<String> ids) {
    }

    private static VanillaSpot before(String fallback, String anchor, String... ids) {
        return new VanillaSpot(fallback, anchor, true, List.of(ids).stream().map(i -> "minecraft:" + i).toList());
    }

    private static VanillaSpot after(String fallback, String anchor, String... ids) {
        return new VanillaSpot(fallback, anchor, false, List.of(ids).stream().map(i -> "minecraft:" + i).toList());
    }

    /** Dans l'ordre des cultures, des arbres, des especes de poissons (crops.py, fruits.py, fish.py). */
    static final List<VanillaSpot> VANILLA = List.of(
          before("recoltes", "barley", "wheat"),
          before("recoltes", "parsnip", "carrot", "golden_carrot", "potato", "poisonous_potato", "beetroot"),
          before("recoltes", "strawberry", "pumpkin", "carved_pumpkin", "melon", "melon_slice", "glistering_melon_slice"),
          after("recoltes", "cranberry", "sweet_berries", "glow_berries"),
          after("recoltes", "coffee_cherries", "cocoa_beans"),
          after("recoltes", "tea_leaves", "sugar_cane"),
          before("recoltes", "pear", "apple", "golden_apple", "enchanted_golden_apple"),
          after("ingredients", "beet_sugar", "sugar"),
          after("plats", "dried_fruit", "dried_kelp"),
          after("produits de la ferme", "animal_feed", "egg", "beef", "porkchop", "chicken", "mutton", "rabbit"),
          after("produits de la ferme", "honey_frame", "honey_bottle"),
          after("peche", "fish_bucket", "cod_bucket", "salmon_bucket", "pufferfish_bucket", "tropical_fish_bucket", "axolotl_bucket",
                "tadpole_bucket"),
          after("peche", "pike", "salmon"),
          after("peche", "glowfish", "cod"),
          after("peche", "clownfish", "pufferfish"),
          after("peche", "lionfish", "tropical_fish"));

    /** Insere dans les motifs d'une section les objets vanilla dont l'ancre y figure. */
    private static void placeVanilla(Section s, List<String> patterns, Set<VanillaSpot> placed) {
        if (s.tab != Tab.CORE) {
            return;
        }
        for (VanillaSpot v : VANILLA) {
            //1.9.2 : l'ancre peut etre ecrite « I:id » ou « B:id » (listes de la ferme reparties entre objets et blocs)
            int i = patterns.indexOf(v.anchor());
            if (i < 0) {
                i = patterns.indexOf("I:" + v.anchor());
            }
            if (i >= 0 && placed.add(v)) {
                patterns.addAll(v.before() ? i : i + 1, v.ids());
            }
        }
    }

    private static Layout cached;
    private static List<Object> cachedKey;

    /** Plan resolu pour ces catalogues (memorise tant qu'ils ne changent pas). */
    public static synchronized Layout layout(List<Machine> machines, List<Plant> plants) {
        List<Object> key = List.of(machines, plants);
        if (cached == null || !key.equals(cachedKey)) {
            cached = new Layout(machines, plants);
            cachedKey = key;
        }
        return cached;
    }

    // ------------------------------------------------------------------
    // Ordre dans une section
    // ------------------------------------------------------------------

    private static final Pattern TIER_NUMBER = Pattern.compile("_(?:tier_|mk)(\\d+)(?=_|$)");
    private static final String[] TIER_WORDS = {"primitive", "basic", "advanced", "elite", "ultimate", "creative"};

    /** Tier d'un id (0 = sans tier ou primitif ; MKI = 1 ... ; creatif = 5) et sa famille (l'id sans le tier). */
    record TierKey(String family, int tier) {
    }

    static TierKey tierKey(String id) {
        for (int t = 0; t < TIER_WORDS.length; t++) {
            String w = TIER_WORDS[t];
            if (id.startsWith(w + "_")) {
                return new TierKey(id.substring(w.length() + 1), t);
            }
            if (id.endsWith("_" + w)) {
                return new TierKey(id.substring(0, id.length() - w.length() - 1), t);
            }
        }
        Matcher m = TIER_NUMBER.matcher(id);
        if (m.find()) {
            return new TierKey(id.substring(0, m.start()) + id.substring(m.end()), Integer.parseInt(m.group(1)));
        }
        return new TierKey(id, 0);
    }

    private static Comparator<Map.Entry<String, Integer>> comparator(Order order) {
        Comparator<Map.Entry<String, Integer>> rank = Comparator.comparingInt(Map.Entry::getValue);
        Comparator<Map.Entry<String, Integer>> family = Comparator.comparing(e -> tierKey(e.getKey()).family());
        Comparator<Map.Entry<String, Integer>> tier = Comparator.comparingInt(e -> tierKey(e.getKey()).tier());
        Comparator<Map.Entry<String, Integer>> id = Map.Entry.comparingByKey();
        return switch (order) {
            case LIST -> rank.thenComparing(family).thenComparing(tier).thenComparing(id);
            case TIER -> tier.thenComparing(rank).thenComparing(id);
            case WORLD -> Comparator.<Map.Entry<String, Integer>>comparingInt(e -> bodyRank(e.getKey()))
                  .thenComparingInt(e -> natureRank(e.getKey())).thenComparing(id);
            case BUILD -> Comparator.<Map.Entry<String, Integer>>comparingInt(e -> bodyRank(e.getKey()))
                  .thenComparing(e -> stoneBase(e.getKey())).thenComparingInt(e -> variantRank(e.getKey())).thenComparing(id);
            case CATALOG -> Comparator.<Map.Entry<String, Integer>>comparingInt(e -> catalogRank(e.getKey())).thenComparing(id);
            case CATEGORY -> Comparator.<Map.Entry<String, Integer>>comparingInt(e -> key(e.getKey()).cat().ordinal())
                  .thenComparingInt(e -> catalogRank(key(e.getKey()).group())).thenComparing(e -> key(e.getKey()).group())
                  .thenComparingInt(e -> key(e.getKey()).rank()).thenComparing(id);
        };
    }

    /** Rang de l'astre d'un id (Terre si aucun astre n'est nomme). */
    static int bodyRank(String id) {
        String best = null;
        int bestLength = 0;
        for (String body : BODIES) {
            if (body.equals("earth") || body.equals("sun")) {
                continue;
            }
            boolean named = body.equals("io") ? id.startsWith("io_") || id.contains("_io_") : id.contains(body);
            if (named && body.length() > bestLength) {
                best = body;
                bestLength = body.length();
            }
        }
        for (Map.Entry<String, String> w : BODY_WORDS.entrySet()) {
            if (id.contains(w.getKey()) && w.getKey().length() > bestLength) {
                best = w.getValue();
                bestLength = w.getKey().length();
            }
        }
        return best == null ? 0 : BODIES.indexOf(best);
    }

    private static final List<List<String>> NATURE = List.of(
          List.of("sand", "regolith", "dust", "ash", "clay", "silt", "soil", "salt", "gypsum", "snow", "gravel", "pebbles", "tholin", "sulfur",
                "outcrop", "seep"),
          List.of("rock", "basalt", "breccia", "crust", "shale", "scoria", "slag", "stone", "diogenite", "stromatolite", "bloomstone"),
          List.of("ice", "frost", "permafrost", "glacier"),
          List.of("cloud", "haze"),
          List.of("moss", "glowmoss", "glowcap", "crystal", "stem", "cap"));

    /** Nature d'un bloc naturel : 0 sol, 1 roche, 2 glace, 3 nuages, 4 vivant ou cristal, 5 minerai (dernier mot de l'id qui en designe une). */
    static int natureRank(String id) {
        String[] words = id.split("_");
        if (List.of(words).contains("ore")) {
            return NATURE.size();
        }
        for (int w = words.length - 1; w >= 0; w--) {
            for (int r = 0; r < NATURE.size(); r++) {
                if (NATURE.get(r).contains(words[w])) {
                    return r;
                }
            }
        }
        return 0;
    }

    private static final String[] VARIANT_PREFIX = {"cobbled_", "packed_", "polished_", "smooth_", "cut_", "", "cracked_", "chiseled_"};

    /** Pierre de base d'un bloc taille (« chiseled_moon_rock_bricks » -> « moon_rock »). */
    static String stoneBase(String id) {
        String s = id;
        for (String p : VARIANT_PREFIX) {
            if (!p.isEmpty() && s.startsWith(p)) {
                s = s.substring(p.length());
                break;
            }
        }
        for (String suffix : new String[]{"_slab", "_stairs", "_wall"}) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
            }
        }
        if (s.endsWith("_bricks")) {
            s = s.substring(0, s.length() - 7);
        } else if (s.endsWith("_brick")) {
            s = s.substring(0, s.length() - 6);
        }
        return s;
    }

    /** Variante : brute, pavee, tassee, polie, lisse, taillee, briques, fissurees, ciselees ; puis forme (bloc, dalle, escalier, muret). */
    static int variantRank(String id) {
        int v = 0;
        for (int i = 0; i < VARIANT_PREFIX.length; i++) {
            if (!VARIANT_PREFIX[i].isEmpty() && id.startsWith(VARIANT_PREFIX[i])) {
                v = i + 1;
            }
        }
        if (v == 0 && (id.endsWith("_bricks") || id.contains("_brick_"))) {
            v = 6;
        }
        int shape = id.endsWith("_slab") ? 1 : id.endsWith("_stairs") ? 2 : id.endsWith("_wall") ? 3 : 0;
        return v * 4 + shape;
    }

    private static Map<String, Integer> catalogIndex;

    /** Rang dans les matieres du catalogue de l'industrie (ordre de la progression), sinon apres. */
    static int catalogRank(String id) {
        if (catalogIndex == null) {
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < IndustryContent.ITEMS.size(); i++) {
                index.putIfAbsent(IndustryContent.ITEMS.get(i), i);
            }
            catalogIndex = index;
        }
        return catalogIndex.getOrDefault(id, Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------
    // Categories (repli : objet qu'aucun motif ne prend)
    // ------------------------------------------------------------------

    /** Categorie devinee d'apres l'id. */
    public enum Cat {
        METAL, GEM, FUEL, ORGANIC, CHEMICAL, COSMIC,
        FORM, TOOL_PART, ELECTRONIC, PART,
        FLUID;

        public boolean component() {
            return this == FORM || this == TOOL_PART || this == ELECTRONIC || this == PART;
        }
    }

    /** Classement de repli : categorie, groupe (metal, sorte d'outil...), rang dans le groupe. */
    public record Key(Cat cat, String group, int rank) {
    }

    private static final String[] PREFIX_FORMS = {"raw_", "crushed_", "ground_", "dirty_"};
    private static final String[] SUFFIX_FORMS = {"_fragment", "_concentrate", "_dust", "_nugget", "_ingot", "_billet", "_block"};
    private static final String[] COMP_FORMS = {"_plate", "_sheet", "_foil", "_rod", "_wire", "_ring", "_spring", "_gear", "_screw", "_bolt", "_pipe",
          "_tube", "_magnet"};
    private static final String[] TOOL_KINDS = {"pickaxe", "mining_hammer", "axe", "shovel", "excavator", "hoe", "scythe", "sword", "hammer", "file",
          "saw", "knife", "mortar", "prospector"};
    private static final String[] ELECTRONIC = {"circuit", "microchip", "chip", "processor", "capacitor", "resistor", "transistor", "oscillator", "ram_",
          "memory", "diode", "wafer", "motor", "sensor", "photonic", "qubit", "inductor", "board", "server_blade", "vacuum_tube", "relay",
          "josephson", "gpu_", "photomask", "boule", "polysilicon"};
    private static final String[] GEM_WORDS = {"diamond", "emerald", "lapis", "quartz", "ruby", "sapphire", "topaz", "peridot", "zircon",
          "tanzanite", "amethyst", "garnet", "opal", "crystal", "redstone", "silicon", "graphite", "sulfur", "salt"};
    private static final String[] FUELS = {"coal", "charcoal", "coke", "pellets", "biochar", "briquette", "peat", "bagasse", "miscanthus",
          "oil_cake"};
    private static final String[] ORGANIC = {"seeds", "fiber", "fibre", "resin", "latex", "rubber", "sawdust", "wood", "flour", "bark", "straw",
          "hemp", "corn", "rapeseed", "leaf", "wax", "rope", "cloth", "canvas", "leather", "paper", "plank", "cellulose", "pulp", "algae", "yeast",
          "sugar", "starch", "berry", "fruit", "petal", "chips"};
    private static final String[] COSMIC = {"stardust", "neutronium", "dark_matter", "quasar", "metallic_hydrogen", "rain_diamond", "singularity"};
    private static final String[] PARTS = {"electric_motor", "planetary_gear", "stator", "engine_block", "bearing", "camshaft", "crankshaft", "piston",
          "belt", "cylinder", "pump", "valve", "impeller", "housing", "heat_exchanger", "spreader", "machine_frame", "tile", "target", "die", "rotor",
          "gasket", "membrane", "filter", "electrode", "klystron", "coil", "liner", "shielding", "superconductor", "ceramic", "lattice",
          "crystal_grid", "spacer_grid", "casing", "panel"};

    private static final String[] TIER_PREFIX = {"basic_", "advanced_", "elite_", "ultimate_"};

    /** Classement de repli ; les pieces par tier se rangent avec leur piece MKI (groupe = id sans prefixe, rang = tier). */
    public static Key key(String id) {
        Key k = rawKey(id);
        if (k.cat() == Cat.PART || k.cat() == Cat.ELECTRONIC) {
            for (int t = 0; t < TIER_PREFIX.length; t++) {
                if (id.startsWith(TIER_PREFIX[t])) {
                    return new Key(k.cat(), id.substring(TIER_PREFIX[t].length()), t + 1);
                }
            }
        }
        return k;
    }

    private static Key rawKey(String id) {
        if (id.endsWith("_bucket") || id.contains("capsule")) {
            return new Key(Cat.FLUID, id.contains("capsule") ? "~capsule" : "", 0);
        }
        for (String k : COSMIC) {
            if (id.contains(k)) {
                return new Key(Cat.COSMIC, id, 0);
            }
        }
        if (id.endsWith("_seeds")) {
            return new Key(Cat.ORGANIC, id, 0);
        }
        for (String k : PARTS) {
            if (id.contains(k)) {
                return new Key(Cat.PART, id, 0);
            }
        }
        for (int r = 0; r < PREFIX_FORMS.length; r++) {
            if (id.startsWith(PREFIX_FORMS[r])) {
                String g = id.substring(PREFIX_FORMS[r].length());
                if (g.endsWith("_block")) {
                    return new Key(Cat.METAL, g.substring(0, g.length() - 6), 1);
                }
                return new Key(Cat.METAL, g, r == 0 ? 0 : 2 + r);
            }
        }
        for (int r = 0; r < SUFFIX_FORMS.length; r++) {
            if (id.endsWith(SUFFIX_FORMS[r])) {
                return new Key(Cat.METAL, id.substring(0, id.length() - SUFFIX_FORMS[r].length()), 10 + r);
            }
        }
        for (int r = 0; r < COMP_FORMS.length; r++) {
            if (id.endsWith(COMP_FORMS[r])) {
                return new Key(Cat.FORM, COMP_FORMS[r], r);
            }
        }
        if (id.endsWith("_head") || id.endsWith("_blade") || id.endsWith("_bowl")) {
            for (int r = 0; r < TOOL_KINDS.length; r++) {
                if (id.contains("_" + TOOL_KINDS[r] + "_") || id.startsWith(TOOL_KINDS[r] + "_")) {
                    return new Key(Cat.TOOL_PART, TOOL_KINDS[r], r);
                }
            }
        }
        if (id.endsWith("_mold")) {
            return new Key(Cat.TOOL_PART, "~mold", 100);
        }
        if (id.endsWith("_chips")) {
            return new Key(Cat.ORGANIC, id, 0);
        }
        for (String k : ELECTRONIC) {
            if (id.contains(k)) {
                return new Key(Cat.ELECTRONIC, id, 0);
            }
        }
        for (String k : ORGANIC) {
            if (id.contains(k)) {
                return new Key(Cat.ORGANIC, id, 0);
            }
        }
        for (String k : GEM_WORDS) {
            if (id.contains(k)) {
                return new Key(Cat.GEM, id, 0);
            }
        }
        for (String k : FUELS) {
            if (id.contains(k)) {
                return new Key(Cat.FUEL, id, 0);
            }
        }
        return new Key(Cat.CHEMICAL, id, 0);
    }

    //En dernier : le plan lit les tables declarees plus haut
    static {
        //1.9.2 : un plan par onglet, dans l'ordre des onglets (Construction avant Mondes : a precision egale, un bloc taille l'emporte)
        layoutTech();
        layoutLogistics();
        layoutSpace();
        layoutComponents();
        layoutBuilding();
        layoutWorlds();
        layoutCore();
        layoutFluids();
        layoutEquipment();
        layoutHidden();
    }
}
