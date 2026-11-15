package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.MachineSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : REGISTRE DES VUES de l'interface standard, a la place des chaines de {@code instanceof} : la vue d'une machine est
 * celle que nomme sa fiche ({@link MachineSpec#ui()}, {@code tools/gc/api}). Lot 8c : un bloc sans fiche (exception declaree : etablis,
 * noyaux du Stabilisateur) a sa vue par son id ({@link #registerBlock}) ; plus aucune vue d'avant aiguillee par classe ({@link #LEGACY} ne
 * dessine rien).
 * <p>
 * Vues enregistrees : {@code standard} / {@code atelier} / {@code kit} = l'interface standard v2 ({@link StandardView} : inventaire dans
 * le cadre, carre du selecteur, onglets a droite, onglet Infos, ecran d'etat de 48 px) ; lot 3 : {@code buffer}, {@code item_buffer},
 * {@code heater}, {@code compressor} = l'interface v2 aussi ({@link StorageViews}) ; toutes les autres, pour l'instant, = {@link #LEGACY}.
 */
public final class MachineScreens {

    /** Une vue : fond de la zone machine et premier plan (coordonnees relatives au panneau). */
    public interface View {

        void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile);

        /** Premier plan (infobulles des cases vides, fleches) ; faux : premier plan des vues d'avant. */
        default boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            return false;
        }

        /** Interface standard v2 (inventaire dans le cadre, carre du selecteur, onglets a droite, onglet Infos) ? */
        default boolean standard() {
            return false;
        }

        /** Lot 3 : texte court a droite du titre, avant les pastilles (buffer d'objets : cases occupees) ; null : aucun. */
        @Nullable
        default Component titleNote(GuiMachine gui, BlockEntity tile) {
            return null;
        }

        /** Lot 3 : lignes propres de l'onglet Infos (null : celles d'une machine a procede ou d'un generateur). */
        @Nullable
        default List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            return null;
        }

        /** Lot 5 : tier de la pastille du titre (multiblocs : celui de la structure formee, -1 non forme) ; {@code fallback} : celui de la fiche. */
        default int titleTier(GuiMachine gui, BlockEntity tile, int fallback) {
            return fallback;
        }

        /** Lot 5 : la machine a-t-elle l'onglet Structure (multiblocs du moteur commun) ? */
        default boolean structureTab(BlockEntity tile) {
            return false;
        }

        /** Lot 5 : panneau de l'onglet Structure (par-dessus la zone machine seulement). */
        default void structure(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
        }

        /** Lot 3 : cle de l'aide courte en bas de l'onglet Infos. */
        default String infoHelp() {
            return "gui.pioneers.api.info.help";
        }

        /**
         * 1.0.4-beta (lot 34) : bloc libre de l'onglet Infos, sous les lignes (x, y : haut gauche dans le panneau, largeur w, jusqu'a
         * {@code bottom}) : texte sur plusieurs lignes (diagnostic du comptoir d'entrepot) ; rien par defaut.
         */
        default void infoExtra(GuiMachine gui, GuiGraphics g, BlockEntity tile, int x, int y, int w, int bottom) {
        }
    }

    /**
     * Repli d'une machine sans vue enregistree : RIEN n'est dessine (lot 8c : la chaine d'avant de {@code MachineViews}, aiguillee par classe,
     * est retiree ; {@code tools/check_machine_api.py} exige une vue enregistree pour tout bloc qui ouvre cette interface).
     */
    public static final View LEGACY = (gui, g, mouseX, mouseY, tile) -> {
    };

    private static final Map<String, View> VIEWS = new HashMap<>();
    /** Lot 8b : corps propres des vues partagees par plusieurs fiches (maisons : {@code house}, {@code house_works}), par cle de fiche. */
    private static final Map<String, View> BODIES = new HashMap<>();
    /**
     * Lot 8c : vues et corps des blocs SANS FICHE qui ouvrent l'interface standard (exceptions declarees : etablis, noyaux du Stabilisateur),
     * par id de bloc : meme cadre v2 que les machines de l'API.
     */
    private static final Map<String, View> BLOCK_VIEWS = new HashMap<>(), BLOCK_BODIES = new HashMap<>();

    static {
        register("standard", StandardView.INSTANCE);
        register("atelier", StandardView.INSTANCE);
        register("kit", StandardView.INSTANCE);
        //Lot 3 : buffers, radiateur et compresseur passent a l'interface standard v2
        register("buffer", StorageViews.GAUGE_BUFFER);
        register("item_buffer", StorageViews.ITEM_BUFFER);
        register("heater", StorageViews.HEATER);
        register("compressor", StorageViews.COMPRESSOR);
        //Lot 4 : banque de donnees (logique DataBankUnit, ressource DATA)
        register("data_bank", StorageViews.DATA_BANK);
        //Lot 4 : petites machines spatiales a tiers (chunkloader, champ de force, collecteur de foudre, diffuseur d'oxygene)
        register("space", SpaceViews.VIEW);
        //Lot 4 : etabli de modulation
        register("gear", GearViews.VIEW);
        //Lot 4 : postes du quai, sous-station, grue a eau
        register("rail", RailViews.VIEW);
        //Lot 4 : machines a corps propre (science, recherche, tables d'assemblage, terminal, IA) dans le cadre v2 ; leur corps d'avant
        //(MachineViews, aiguille par classe) garde sa place et ses indices jusqu'a sa refonte (lots suivants)
        //Lot 8b : corps propres en vraie v2 (valeurs nommees, plus d'encadrement du corps d'avant)
        register("science", ScienceViews.VIEW);
        register("research", ScienceViews.RESEARCH);
        register("works", WorksViews.VIEW);
        register("network", NetworkViews.VIEW);
        register("ai", ComputeViews.VIEW);
        //Lot 3 : generateurs (renouvelables, turbine et echappement compacts) : leurs vues d'avant, choisies par la fiche
        //Lot 4 : a l'interface standard v2
        register("generator", EnergyViews.VIEW);
        //Lot 5 (API Multibloc) : controleurs des multiblocs industriels du moteur commun (et la plate-forme offshore) a l'interface v2,
        //onglet Structure ; reacteurs, fusion et calcul gardent leur ecran propre (lot 6)
        register("controller", MultiblockViews.CONTROLLER);
        register("offshore", MultiblockViews.CONTROLLER);
        //Lot 6 : reacteurs, fusion, calcul : meme interface v2, corps special (CoreViews)
        register("reactor", MultiblockViews.REACTOR);
        register("fusion", MultiblockViews.FUSION);
        register("compute", MultiblockViews.COMPUTE);
        //Lot 6 : le port (capitainerie) a l'interface v2, onglet Structure
        register("harbor", RailViews.HARBOR);
        //Lot 7 : age mecanique et du bronze a l'interface v2 (energie motrice rotation / vapeur / feu, pastille d'age)
        register("mechanical", AgeViews.MECHANICAL);
        //1.0.5-beta (lot 38) : les machines de traitement a vapeur passent a la vue standard (StandardView : jauge de vapeur, faces, selecteur)
        register("steam_blower", AgeViews.STEAM_BLOWER);
        register("coke_oven", AgeViews.COKE_OVEN);
        register("hearth", AgeViews.HEARTH);   //1.0.2-beta (lot 2) : foyer de forge
        register("auto_mold", FoundryViews.AUTO_MOLD);   //1.0.2-beta (lot 4) : moule automatique
        register("warehouse", WarehouseViews.VIEW);   //1.0.4-beta (lot 1) : comptoir d'entrepot
        register("warehouse_io", WarehouseViews.IO_VIEW);   //1.0.5-beta (lot 17) : interfaces d'entree et de sortie de l'entrepot
        register("bag_interface", BagInterfaceViews.VIEW);   //1.0.5-beta (lot 45, retour 65) : interface de sac
        register("caravan_relay", CaravanViews.RELAY);   //1.0.4-beta (lot 10) : relais de caravane
        register("farm", FarmViews.VIEW);   //1.0.5-beta (lot 41, retour 60) : postes de la cuisine et appareils de la ferme
        register("dynamo", AgeViews.DYNAMO);
        //1.0.2-beta : pieces de transmission a l'interface v2, avec un curseur (boite de vitesses : rapport ; regulateur a boules : vitesse visee)
        register("transmission", TransmissionViews.TRANSMISSION);
        register("governor", TransmissionViews.GOVERNOR);
        //Lot 8a : multiblocs maison (stabilisateur, simulateur, absorbeur, bases, complexe de lancement, halls, chaudiere, haut fourneau,
        //foreuse) a l'interface v2 : corps propre dans le cadre, onglet Structure, voile et erreur commune
        register("house", MultiblockViews.HOUSE);
        register("house_works", MultiblockViews.HOUSE_WORKS);
        //Lot 8b : corps des maisons en vraie v2 (valeurs nommees), choisis par la cle de la fiche
        registerBody("rocket_integration_hall", WorksViews.BODY);
        registerBody("robotic_assembly_bay", WorksViews.BODY);
        registerBody("greenhouse_controller", BaseViews.BODY);
        registerBody("mining_outpost_controller", BaseViews.BODY);
        registerBody("drone_bay", BaseViews.BODY);
        registerBody("launch_pad_core", BaseViews.BODY);
        registerBody("firebox", HouseAgeViews.FIREBOX);
        registerBody("steam_drill", HouseAgeViews.DRILL);
        registerBody("blast_furnace", HouseAgeViews.BLAST);
        registerBody("auto_foundry", FoundryViews.FOUNDRY);   //1.0.2-beta (lot 4) : fonderie automatique
        //1.0.4-beta (lot 24) : four de potier et four a coke primitif devenus multiblocs maison (vue maison, corps d'avant)
        registerBody("pottery_kiln", SourceViews.KILN);
        registerBody("coke_oven", AgeViews.COKE_OVEN);
        registerBody("greenhouse_regulator", GreenhouseViews.BODY);   //1.0.5-beta (lot 3) : regulateur de serre
        //Lot 8b : dernieres machines de l'age mecanique et du bronze a l'interface v2 (cuisine, sources, vapeur, fonderie, ateliers a vapeur)
        register("kitchen", SourceViews.KITCHEN);
        register("beam_engine", SourceViews.BEAM_ENGINE);
        register("steam_engine", SourceViews.STEAM_ENGINE);
        register("crucible", SourceViews.CRUCIBLE);
        register("casting", SourceViews.CASTING);
        register("kiln", SourceViews.KILN);
        //1.0.5-beta (lot 38) : vue « steam_workshop » retiree (marteau-pilon supprime, presse a vapeur a l'interface standard)
        //Lot 8c : multiblocs cosmiques en vraie v2 (196 de large), plus par la chaine d'avant
        registerBody("space_stabilizer_controller", CosmicViews.CONTROLLER);
        registerBody("space_simulator_controller", CosmicViews.SIMULATOR);
        registerBody("solar_absorber_controller", CosmicViews.ABSORBER);
        //Lot 8c : noyaux du Stabilisateur (pieces du multibloc, sans fiche) : vue maison (voile, erreur commune, Structure), corps v2
        for (String core : new String[]{"singularity_core", "stellar_core"}) {
            registerBlock(core, MultiblockViews.HOUSE);
            registerBlockBody(core, CosmicViews.CORE);
        }
        //Lot 8c : etablis (hors API Machine, decision 1) : meme cadre v2 (inventaire dans le cadre, onglets, pastille d'age)
        for (String bench : BronzeViews.BENCHES) {
            registerBlock(bench, BronzeViews.BENCH);
        }
    }

    private MachineScreens() {
    }

    /** Lot 8b : enregistre le corps propre de la fiche {@code key} (dessine par une vue partagee : maisons). */
    public static void registerBody(String key, View body) {
        BODIES.put(key, body);
    }

    /** Lot 8c : vue d'un bloc sans fiche (exception declaree) qui ouvre l'interface standard. */
    public static void registerBlock(String id, View view) {
        BLOCK_VIEWS.put(id, view);
    }

    /** Lot 8c : corps propre d'un bloc sans fiche dessine par une vue partagee (noyaux du Stabilisateur dans la vue maison). */
    public static void registerBlockBody(String id, View body) {
        BLOCK_BODIES.put(id, body);
    }

    /** Lot 8c : ids des blocs sans fiche qui ont une vue enregistree (lu par le banc des registres, par reflexion). */
    public static java.util.Set<String> blockViews() {
        return java.util.Collections.unmodifiableSet(BLOCK_VIEWS.keySet());
    }

    /** Lot 8b : corps propre de la machine (celui de sa fiche ou de son bloc, sinon {@link #LEGACY}). */
    public static View body(@Nullable BlockEntity tile) {
        MachineSpec spec = spec(tile);
        View body = spec != null ? BODIES.get(spec.key()) : tile == null ? null : BLOCK_BODIES.get(blockId(tile));
        return body == null ? LEGACY : body;
    }

    /** Id (chemin) du bloc de la tile, tire de sa cle de traduction {@code block.pioneers.<id>} (meme chemin en jeu et au banc). */
    @Nullable
    private static String blockId(BlockEntity tile) {
        String key = tile.getBlockState().getBlock().getDescriptionId();
        return key.startsWith("block.pioneers.") ? key.substring("block.pioneers.".length()) : null;
    }

    /** Enregistre la vue {@code ui} (nom de la fiche). */
    public static void register(String ui, View view) {
        VIEWS.put(ui, view);
    }

    /** Vue de la machine (celle de sa fiche, sinon {@link #LEGACY}). */
    public static View of(@Nullable BlockEntity tile) {
        MachineSpec spec = spec(tile);
        View view = spec != null ? VIEWS.get(spec.ui()) : tile == null ? null : BLOCK_VIEWS.get(blockId(tile));
        return view == null ? LEGACY : view;
    }

    /** Lot 8c : la fiche nomme-t-elle une vue enregistree ? (controle du banc : aucune machine a interface standard sans vue) */
    public static boolean registered(String ui) {
        return VIEWS.containsKey(ui);
    }

    /** Fiche de la machine ouverte, ou null. */
    @Nullable
    public static MachineSpec spec(@Nullable BlockEntity tile) {
        if (tile == null) {
            return null;
        }
        MachineRegistry.Entry e = MachineRegistry.of(tile.getBlockState().getBlock());
        return e == null ? null : e.spec();
    }
}
