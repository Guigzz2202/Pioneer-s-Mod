package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Machine d'analyse (socle) : analyse des echantillons (asteroides, minerais, recoltes) et en tire des donnees d'analyse d'un type precis
 * (composition d'une famille d'asteroides, geologie ou biologie d'un astre). Les donnees sortent par les faces reglees en sortie,
 * jusqu'a une table de recherche qui les convertit en echantillons pour la recherche correspondante.
 * <p>
 * Une machine n'analyse que les echantillons d'une recherche disponible (prerequis termines) ; elle s'arrete d'elle-meme
 * quand la recherche est terminee ou que le type d'echantillon est complet.
 * <p>
 * Interface : 4 avancement (pour mille), 5 etat ({@link Status}), 6 recherche visee (-1 aucune), 7 consommation FE/t ; puis pour chaque
 * reservoir de sortie : type (-1 vide), contenu.
 * <p>
 * API Machine (lot 8c) : logique dans {@link AnalysisUnit} (fiches {@code migrated}) ; la tile la porte ({@link #carry}) et decrit ce qui
 * s'analyse (echantillons, duree, consommation, cases).
 */
public abstract class TileEntityAnalyzer extends DataMachineTile {

    public enum Status {IDLE, WORKING, LOCKED, COMPLETE, NO_ENERGY, UNKNOWN, OUTPUT_FULL}

    /** Un echantillon : la recherche qu'il nourrit, son type, ce qu'il vaut, et les donnees qu'il produit. */
    public record Sample(ResearchNode node, int type, int amount, DataType data) {
    }

    public static final long ENERGY_STORAGE_FE = 200_000L;
    /** Reservoir de sortie (Mo) et debit d'ejection (Mo/t). */
    public static final long OUTPUT_CAPACITY = 64_000, EJECT_RATE = 8_000;
    /** Hauteur de la zone machine de l'interface (rangee des reservoirs de sortie sous l'ecran). */
    public static final int GUI_HEIGHT = StandardLayout.ZONE;
    /** Lot 8b (vraie v2) : batterie a la place commune, sous la reserve d'energie. */
    public static final int BATTERY_X = StandardLayout.BATTERY_X, BATTERY_Y = StandardLayout.BATTERY_Y;
    /** Lot 8b : carte de la recherche visee, barre d'avancement sous les entrees, ecran raccourci, rangee des reservoirs de sortie. */
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(72, StandardLayout.TOP, StandardLayout.WIDTH - 8 - 72, StandardLayout.GAUGE_H),
          BAR = new StandardLayout.Rect(28, StandardLayout.TOP + 48, 38, 3);

    protected final List<ItemSlot> inputSlots;
    /** API Machine (lot 8c) : la logique (choix de l'echantillon, avancement, donnees) est dans l'unite portee. */
    private final AnalysisUnit unit;

    protected TileEntityAnalyzer(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, ENERGY_STORAGE_FE, 2_000);
        unit = carry(new AnalysisUnit(this, access()));
        inputSlots = unit.inputSlots;
        //Objets par le dessus, l'arriere et les cotes ; donnees en sortie par l'arriere, les cotes et le dessous (avant et dessus sans prise)
        for (RelativeSide side : new RelativeSide[]{RelativeSide.TOP, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT}) {
            sides.set(SideType.ITEMS, side, SideMode.INPUT);
        }
        sides.set(SideType.ENERGY, RelativeSide.BOTTOM, SideMode.INPUT);
        sides.set(SideType.ENERGY, RelativeSide.BACK, SideMode.INPUT);
        dataDirections(false, true);
        dataSide(SideMode.OUTPUT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
        dataEject(EJECT_RATE);
    }

    /** Lot 8c : reservoir de donnees declare pour l'unite. */
    BasicDataTank declareData(BasicDataTank tank) {
        return dataTank(tank);
    }

    // ------------------------------------------------------------------
    // A definir par chaque machine
    // ------------------------------------------------------------------

    /** Nombre de cases d'entree. */
    protected abstract int inputCount();

    /** Position (x, y) de l'objet dans la case d'entree {@code index}. */
    protected abstract int[] inputPosition(int index);

    /** La case {@code index} accepte cet objet. */
    protected abstract boolean accepts(int index, ItemStack stack);

    /** Objet montre en transparence dans la case d'entree vide (aide de l'interface). */
    protected ItemStack inputGhost(int index) {
        return ItemStack.EMPTY;
    }

    /** Ce que vaut cet objet (null si ce n'est pas un echantillon reconnu). */
    @Nullable
    public abstract Sample sampleOf(int index, ItemStack stack);

    /** Duree d'analyse d'un objet (ticks). */
    public abstract int ticksPerItem();

    /** Consommation pendant l'analyse (FE/t). */
    public abstract long energyPerTick();

    /** Nombre de reservoirs de sortie (un seul type de donnees a la fois chacun). */
    protected int outputTankCount() {
        return 1;
    }

    /** Le reservoir de sortie {@code tank} accepte ce type de donnees. */
    protected boolean acceptsData(int tank, DataType type) {
        return PiDataTypes.isAnalysis(type);
    }

    /** Nom de chaque type d'echantillon (pour l'interface). */
    public Component typeName(int type) {
        return Component.empty();
    }

    @Override
    public int guiHeight() {
        return GUI_HEIGHT;
    }

    // ------------------------------------------------------------------

    @Override
    protected void tick() {
    }

    public List<BasicDataTank> getOutputTanks() {
        return unit.outputTanks;
    }

    public int[] inputSlotPosition(int index) {
        return inputPosition(index);
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public String guiTier() {
        return guiName();
    }

    /** Identifiant de la machine (textures de l'interface a sa couleur). */
    protected abstract String guiName();

    public static Status status(long value) {
        return Status.values()[(int) Math.max(0, Math.min(value, Status.values().length - 1))];
    }

    // ------------------------------------------------------------------

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    /** Lot 8b : vraie v2 : reserve et batterie, entrees, barre, carte, ecran raccourci, rangee des reservoirs de sortie. */
    @Override
    public List<StandardLayout.Part> guiParts() {
        List<StandardLayout.Part> out = new java.util.ArrayList<>(StandardLayout.serviceParts(true, new StandardLayout.Part("bar", BAR),
              new StandardLayout.Part("card", CARD)));
        out.removeIf(p -> p.role().equals("screen"));
        out.add(new StandardLayout.Part("screen", StandardLayout.shortScreen(StandardLayout.WIDTH)));
        out.add(new StandardLayout.Part("setting", StandardLayout.settingRow(StandardLayout.WIDTH)));
        return out;
    }
}
