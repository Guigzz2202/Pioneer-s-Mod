package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.registries.PiScienceMachines;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Laboratoire de recherche.
 * <p>
 * Recoit les donnees de chaque astre par les cables optiques, sur les faces reglees en entree (memoire separee par type,
 * un reservoir de donnees par type). Quand une recherche est active
 * (arbre technologique commun au monde), le laboratoire preleve les donnees d'une unite, la traite pendant quelques secondes
 * en consommant de l'energie, puis l'ajoute a la progression commune. Tous les laboratoires du monde travaillent ensemble.
 * Il recoit aussi les donnees d'analyse (geologie, biologie, asteroides) produites par les machines d'analyse et les convertit en
 * echantillons pour la recherche correspondante.
 * Les plans des recherches terminees peuvent etre reimprimes contre des donnees orbitales.
 * <p>
 * Socle : interface standard large (236), valeurs : 4 recherche en cours (-1), 5 avancement de l'unite (pour mille), 6 attente, 7 plan a
 * imprimer (rang), 8 vitesse x100, puis les sondes en service par destination. La memoire est synchronisee avec le bloc (client).
 * <p>
 * API Machine (lot 8c) : logique dans {@link ResearchUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class TileEntityResearchTable extends DataMachineTile {

    /** Memoire : reservoirs (Mo) acceptant n'importe quel type, un reservoir par type ; la quantite reste limitee (les banques stockent). */
    public static final long CAPACITY = 100_000;
    public static final int MEMORY_TANKS = 16;
    /** Donnees de biome necessaires pour valider l'exploration d'un biome (recherche de surface). */
    public static final long BIOME_SCAN_COST = 10_000;
    public static final long ENERGY_PER_TICK_FE = 2_000;
    public static final long ENERGY_STORAGE_FE = 600_000;
    public static final long PRINT_COST = 1_000;
    public static final int ACTION_PRINT = CUSTOM, ACTION_NEXT_PRINT = CUSTOM + 1;
    /** Lot 8b (vraie v2) : reserve d'energie a la place commune, pages, ecrans principal et de l'unite, rangee du bas, sortie a droite. */
    public static final int GUI_WIDTH = 236, GUI_HEIGHT = StandardLayout.ZONE, OUTPUT_X = 211, OUTPUT_Y = StandardLayout.TOP + 104, BODY_X = 26,
          BODY_W = GUI_WIDTH - 8 - BODY_X, PAGE_Y = 17, MAIN_Y = 31, MAIN_H = 42, UNIT_Y = 75, UNIT_H = 46, ROW_Y = StandardLayout.TOP + 104,
          ROW_W = BODY_W - 24;

    private final ItemSlot outputSlot;
    /** API Machine (lot 8c) : la logique (unite de recherche, memoire, echantillons, plans) est dans l'unite portee. */
    private final ResearchUnit unit;

    public TileEntityResearchTable(BlockPos pos, BlockState state) {
        super(PiScienceMachines.RESEARCH_TABLE_TILE.get(), pos, state, ENERGY_STORAGE_FE, (int) (ENERGY_PER_TICK_FE * 4));
        unit = carry(new ResearchUnit(this, access()));
        outputSlot = unit.outputSlot;
        //Prises : toutes les faces sauf l'avant (le poste de travail) et le dessus ; par defaut, entree a l'arriere, sur les cotes et dessous
        sides.lock(RelativeSide.FRONT, RelativeSide.TOP);
        sides.set(SideType.ENERGY, RelativeSide.BOTTOM, SideMode.INPUT);
        sides.set(SideType.ENERGY, RelativeSide.BACK, SideMode.INPUT);
        dataDirections(true, false);
        dataSide(SideMode.INPUT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
    }

    /** Lot 8c : reservoir de donnees declare pour l'unite. */
    BasicDataTank declareData(BasicDataTank tank) {
        return dataTank(tank);
    }

    @Override
    public int guiWidth() {
        return GUI_WIDTH;
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    @Override
    public String guiTier() {
        return "research_table";
    }

    @Override
    protected List<ItemSlot> automationSlots() {
        return List.of(outputSlot);
    }

    @Override
    protected void tick() {
    }

    /** Donnees autres que celles des sondes (analyses, biomes), jusqu'a quatre, pour l'interface. */
    public List<BasicDataTank> getAnalysisTanks() {
        return unit.getAnalysisTanks();
    }

    /** Vitesse de traitement (ticks d'unite par tick). */
    /** 0.17.2 : GOPS demandes au reseau de calcul pendant une recherche (au plus x4 de vitesse). */
    public static final double COMPUTE_DEMAND = 3_000;

    public double getSpeed() {
        return unit.getSpeed();
    }

    // ------------------------------------------------------------------
    // Impression des plans
    // ------------------------------------------------------------------

    /** Recherches terminees dont le plan peut etre reimprime. */
    public static List<ResearchNode> printable(Predicate<ResearchNode> completed) {
        List<ResearchNode> list = new ArrayList<>();
        for (ResearchNode node : ResearchNode.values()) {
            if (node.getBlueprint() != null && completed.test(node)) {
                list.add(node);
            }
        }
        return list;
    }

    @Nullable
    public ResearchNode getPrintTarget(Predicate<ResearchNode> completed) {
        return unit.getPrintTarget(completed);
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        unit.handleCustom(action);
    }

    // ------------------------------------------------------------------
    // Donnees
    // ------------------------------------------------------------------

    public long getData(ProbeDestination type) {
        return unit.getData(type);
    }

    public int getProbes(ProbeDestination destination) {
        return unit.getProbes(destination);
    }

    // ------------------------------------------------------------------
    // Interface et rendu
    // ------------------------------------------------------------------

    /** Avancement de l'unite en cours (0..1). */
    public double getUnitProgress() {
        return unit.getUnitProgress();
    }

    public boolean isClientWorking() {
        return unit.clientWorking;
    }

    public int getClientNode() {
        return unit.clientNode;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    /** Lot 8b : reserve d'energie, onglets des pages, ecran principal, ecran de l'unite (page memoire : la meme place), rangee du bas, sortie. */
    @Override
    public List<StandardLayout.Part> guiParts() {
        return List.of(new StandardLayout.Part("energy", new StandardLayout.Rect(7, StandardLayout.TOP - 1, StandardLayout.GAUGE_W + 2, StandardLayout.ENERGY_H + 2)),
              new StandardLayout.Part("setting", new StandardLayout.Rect(BODY_X, PAGE_Y, BODY_W, 12)),
              new StandardLayout.Part("card", new StandardLayout.Rect(BODY_X, MAIN_Y, BODY_W, MAIN_H)),
              new StandardLayout.Part("card", new StandardLayout.Rect(BODY_X, UNIT_Y, BODY_W, UNIT_H)),
              new StandardLayout.Part("setting", new StandardLayout.Rect(BODY_X, ROW_Y, ROW_W, 16)));
    }
}
