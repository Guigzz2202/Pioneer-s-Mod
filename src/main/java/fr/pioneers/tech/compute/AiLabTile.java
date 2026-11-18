package fr.pioneers.tech.compute;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.DataMachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.DigitalNetwork;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.industry.IndustryEvents;
import fr.pioneers.tech.compute.PiCompute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Laboratoire d'IA (0.17.2) : une puce (vierge, ou un modele a faire grandir) + des donnees (tout type, par les cables de donnees) + du calcul
 * (reseau de calcul : cables, ou colle a un centre de donnees) pendant un temps -> modele entraine.
 * <ul>
 *     <li>puce vierge -> systeme expert : {@value #EXPERT_DATA} Mo, {@value #EXPERT_GOPS} GOPS.s ;</li>
 *     <li>systeme expert -> reseau de neurones : {@value #NEURAL_DATA} Mo dont {@value #NEURAL_TELEMETRY} de telemetrie,
 *     {@value #NEURAL_GOPS} GOPS.s ;</li>
 *     <li>reseau de neurones -> modele de fondation : {@value #FOUNDATION_DATA} Mo, {@value #FOUNDATION_GOPS} GOPS.s et
 *     {@value #FOUNDATION_QOPS} QOPS.s.</li>
 * </ul>
 * Le laboratoire demande au plus {@value #DEMAND_GOPS} GOPS et {@value #DEMAND_QOPS} QOPS, et consomme {@value #FE_PER_TICK} FE/t en
 * entrainement.
 * <p>
 * Interface : 4 etat, 5 modele vise, 6/7 donnees faites/demandees, 8/9 telemetrie, 10/11 GOPS.s, 12/13 QOPS.s, 14 GOPS accordes x10,
 * 15 QOPS accordes x10, 16 type du reservoir, 17 contenu.
 */
public class AiLabTile extends DataMachineTile implements IComputeHandler {

    public static final long EXPERT_DATA = 5_000, NEURAL_DATA = 20_000, NEURAL_TELEMETRY = 5_000, FOUNDATION_DATA = 100_000;
    public static final long EXPERT_GOPS = 20_000, NEURAL_GOPS = 200_000, FOUNDATION_GOPS = 2_000_000, FOUNDATION_QOPS = 2_000;
    public static final double DEMAND_GOPS = 16_384, DEMAND_QOPS = 256;
    public static final long FE_PER_TICK = 256, ENERGY_STORAGE = 400_000, TANK = 50_000;
    /** Lot 8b (vraie v2) : puce a entrainer, carte du modele vise, modele entraine, reservoir de donnees ; batterie a la place commune. */
    public static final int IN_X = 30, IN_Y = StandardLayout.TOP + 22, OUT_X = 162, OUT_Y = StandardLayout.TOP + 22, BATTERY_X = StandardLayout.BATTERY_X,
          BATTERY_Y = StandardLayout.BATTERY_Y;
    public static final StandardLayout.Rect CARD = new StandardLayout.Rect(52, StandardLayout.TOP, 104, StandardLayout.GAUGE_H),
          GAUGE = new StandardLayout.Rect(184, StandardLayout.TOP, 6, StandardLayout.GAUGE_H);
    /** Lot 8b : valeurs nommees aux indices d'avant. */
    public static final UiValues.Key TARGET_MODEL = UiValues.Key.of("target_model"), DATA_DONE = UiValues.Key.of("data_done"),
          DATA_NEED = UiValues.Key.of("data_need"), TELE_DONE = UiValues.Key.of("telemetry_done"), TELE_NEED = UiValues.Key.of("telemetry_need"),
          GOPS_DONE = UiValues.Key.of("gops_done"), GOPS_NEED = UiValues.Key.of("gops_need"), QOPS_DONE = UiValues.Key.of("qops_done"),
          QOPS_NEED = UiValues.Key.of("qops_need"), GRANT_G = UiValues.Key.of("grant_gops_x10"), GRANT_Q = UiValues.Key.of("grant_qops_x10");
    /** Etats : 0 sans puce, 1 attend des donnees, 2 attend du calcul, 3 entraine, 4 sortie pleine, 5 sans energie, 6 rien a apprendre. */
    public static final int NO_CHIP = 0, NEED_DATA = 1, NEED_COMPUTE = 2, TRAINING = 3, OUTPUT_FULL = 4, NO_ENERGY = 5, DONE = 6;

    private static final long[] DATA = {0, EXPERT_DATA, NEURAL_DATA, FOUNDATION_DATA};
    private static final long[] TELEMETRY = {0, 0, NEURAL_TELEMETRY, 0};
    private static final long[] GOPS = {0, EXPERT_GOPS, NEURAL_GOPS, FOUNDATION_GOPS};
    private static final long[] QOPS = {0, 0, 0, FOUNDATION_QOPS};

    private final ItemSlot input, output, battery;
    private final BasicDataTank tank;
    private final List<BlockPos> cables = new ArrayList<>();
    private int target, status, scan;
    private long dataDone, telemetryDone;
    private double gopsDone, qopsDone, grantG, grantQ, wantG, wantQ;

    public AiLabTile(BlockPos pos, BlockState state) {
        super(PiCompute.AI_LAB_TILE.get(), pos, state, ENERGY_STORAGE, (int) (FE_PER_TICK * 8));
        input = slot(new ItemSlot(() -> 1, s -> AiChips.isBlank(s) || AiChips.level(s) > 0 && AiChips.level(s) < AiChips.FOUNDATION,
              (s, a) -> true, (s, a) -> !a.external(), this::setChanged, IN_X, IN_Y)
              .ghost(() -> new ItemStack(AiChips.item(0))));
        output = slot(new ItemSlot(() -> 1, AiChips::isModel, (s, a) -> a == Automation.INTERNAL, (s, a) -> true, this::setChanged, OUT_X, OUT_Y));
        battery = batterySlot(BATTERY_X, BATTERY_Y);
        tank = dataTank(BasicDataTank.of(TANK, t -> true, (t, a) -> a == Automation.INTERNAL || room(t) > 0, (t, a) -> a == Automation.INTERNAL,
              this::tankChanged));
        sides.lock(RelativeSide.FRONT);
        sides.set(SideType.ENERGY, RelativeSide.BOTTOM, SideMode.INPUT);
        sides.set(SideType.ENERGY, RelativeSide.BACK, SideMode.INPUT);
        dataDirections(true, false);
        dataSide(SideMode.INPUT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM);
    }

    private void tankChanged() {
        setChanged();
        markSync();
    }

    /** Modele vise selon la puce d'entree (0 : rien). */
    private int targetOf(ItemStack stack) {
        if (AiChips.isBlank(stack)) {
            return AiChips.EXPERT;
        }
        int level = AiChips.level(stack);
        return level > 0 && level < AiChips.FOUNDATION ? level + 1 : 0;
    }

    @Override
    protected void tick() {
        discharge(battery, FE_PER_TICK * 8);
        if (--scan <= 0) {
            scan = 20;
            cables.clear();
            cables.addAll(ComputeNetwork.adjacentCables(level, worldPosition));
        }
        int t = targetOf(input.getStack());
        if (t != target) {
            //Autre puce : l'apprentissage repart de zero
            target = t;
            dataDone = telemetryDone = 0;
            gopsDone = qopsDone = 0;
            setChanged();
        }
        wantG = wantQ = 0;
        boolean working = false;
        if (target == 0) {
            status = input.isEmpty() ? NO_CHIP : DONE;
        } else if (!output.isEmpty()) {
            status = OUTPUT_FULL;
        } else if (canWork()) {
            absorb();
            boolean dataOk = dataDone >= DATA[target] && telemetryDone >= TELEMETRY[target];
            wantG = gopsDone < GOPS[target] ? DEMAND_GOPS : 0;
            wantQ = qopsDone < QOPS[target] ? DEMAND_QOPS : 0;
            ComputeNetwork.register(level, worldPosition, this);
            boolean computing = grantG > 0 && wantG > 0 || grantQ > 0 && wantQ > 0;
            if (computing && use(FE_PER_TICK)) {
                gopsDone = Math.min(GOPS[target], gopsDone + (wantG > 0 ? grantG : 0) / 20);
                qopsDone = Math.min(QOPS[target], qopsDone + (wantQ > 0 ? grantQ : 0) / 20);
                working = true;
                setChanged();
            }
            if (dataOk && gopsDone >= GOPS[target] && qopsDone >= QOPS[target]) {
                finish();
            }
            status = working ? TRAINING : computing ? NO_ENERGY : wantG > 0 || wantQ > 0 ? NEED_COMPUTE : NEED_DATA;
        } else {
            status = NEED_COMPUTE;
        }
        markWorking(working);
    }

    /** Donnees de ce type encore utiles a l'entrainement en cours (la telemetrie compte pour les deux besoins). */
    private long room(DataType type) {
        int t = target;
        if (t <= 0 || !output.isEmpty()) {
            return 0;
        }
        if (type == PiDataTypes.TELEMETRY.get()) {
            return Math.max(0, DATA[t] - dataDone);
        }
        //Place laissee a la telemetrie encore attendue
        return Math.max(0, DATA[t] - dataDone - Math.max(0, TELEMETRY[t] - telemetryDone));
    }

    /** Prend dans le reservoir les donnees qui manquent ; le surplus d'un type devenu inutile est efface (sinon il bloquerait l'entree). */
    private void absorb() {
        DataStack stored = tank.getStack();
        if (stored.isEmpty()) {
            pullFromNetwork();
            return;
        }
        boolean telemetry = stored.getType() == PiDataTypes.TELEMETRY.get();
        long take = Math.min(stored.getAmount(), room(stored.getType()));
        if (take <= 0) {
            tank.extract(stored.getAmount(), Action.EXECUTE, Automation.INTERNAL);
            return;
        }
        tank.extract(take, Action.EXECUTE, Automation.INTERNAL);
        dataDone += take;
        if (telemetry) {
            telemetryDone += take;
        }
        setChanged();
    }

    /** 0.20.46 : reservoir vide : le laboratoire puise lui-meme dans les banques du reseau numerique le type qui lui sert le plus. */
    private void pullFromNetwork() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        DigitalNetwork.Snapshot net = DigitalNetwork.scan(level, worldPosition);
        for (Map.Entry<DataType, Long> e : DigitalNetwork.sortedStock(net)) {
            long want = Math.min(TANK, room(e.getKey()));
            if (want <= 0) {
                continue;
            }
            long got = DigitalNetwork.pull(level, worldPosition, e.getKey(), want, Action.EXECUTE);
            if (got > 0) {
                tank.insert(DataStack.of(e.getKey(), got), Action.EXECUTE, Automation.INTERNAL);
                return;
            }
        }
    }

    private void finish() {
        Item model = AiChips.item(target);
        output.setStack(new ItemStack(model));
        input.getStack().shrink(1);
        input.setStack(input.getStack());
        IndustryEvents.award(level, worldPosition, "ai_" + AiChips.KEYS[target]);
        target = 0;
        dataDone = telemetryDone = 0;
        gopsDone = qopsDone = 0;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Reseau de calcul
    // ------------------------------------------------------------------

    @Override
    public double demandGops() {
        return wantG;
    }

    @Override
    public double demandQops() {
        return wantQ;
    }

    @Override
    public void grant(double gops, double qops) {
        grantG = gops;
        grantQ = qops;
    }

    @Override
    public Collection<BlockPos> cables() {
        return cables;
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    protected int machineValueCount() {
        return 14;
    }

    @Override
    protected long machineValue(int index) {
        int t = target;
        return switch (index) {
            case 0 -> status;
            case 1 -> t;
            case 2 -> dataDone;
            case 3 -> DATA[t];
            case 4 -> telemetryDone;
            case 5 -> TELEMETRY[t];
            case 6 -> Math.round(gopsDone);
            case 7 -> GOPS[t];
            case 8 -> Math.round(qopsDone);
            case 9 -> QOPS[t];
            case 10 -> Math.round(grantG * 10);
            case 11 -> Math.round(grantQ * 10);
            case 12 -> typeId(tank.getStack().isEmpty() ? null : tank.getStack().getType());
            default -> tank.getStack().getAmount();
        };
    }

    @Override
    public String guiTier() {
        return "ai_lab";
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("target", target);
        tag.putLong("data", dataDone);
        tag.putLong("telemetry", telemetryDone);
        tag.putDouble("gops", gopsDone);
        tag.putDouble("qops", qopsDone);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        target = tag.getInt("target");
        dataDone = tag.getLong("data");
        telemetryDone = tag.getLong("telemetry");
        gopsDone = tag.getDouble("gops");
        qopsDone = tag.getDouble("qops");
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.serviceParts(true, new StandardLayout.Part("card", CARD), StandardLayout.framed("gauge", GAUGE));
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        UiValues.Key[] keys = {UiValues.STATUS, TARGET_MODEL, DATA_DONE, DATA_NEED, TELE_DONE, TELE_NEED, GOPS_DONE, GOPS_NEED, QOPS_DONE, QOPS_NEED, GRANT_G,
              GRANT_Q, UiValues.dataType(0), UiValues.dataStored(0)};
        for (int i = 0; i < keys.length; i++) {
            int index = i;
            values.add(keys[i], () -> machineValue(index));
        }
    }
}
