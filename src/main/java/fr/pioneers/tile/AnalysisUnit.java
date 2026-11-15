package fr.pioneers.tile;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.data.AnalysisData;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.space.research.ResearchState;
import fr.pioneers.tile.TileEntityAnalyzer.Sample;
import fr.pioneers.tile.TileEntityAnalyzer.Status;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8c) : LOGIQUE DES MACHINES D'ANALYSE (broyeur d'asteroides, spectrometre geologique, sequenceur biologique : code d'avant
 * de {@link TileEntityAnalyzer}, deplace) : cases d'entree, batterie et reservoirs de sortie (declares par l'unite dans le meme ordre
 * qu'avant), choix de l'echantillon (case suivante au prochain objet), recherche visee, avancement, donnees produites. Ce qui est analysable
 * (echantillons, duree, consommation, cases) reste decrit par chaque machine ({@code sampleOf}, {@code ticksPerItem}...). Cles sauvees :
 * {@code analysisProgress}, {@code analysisTarget}, {@code analysisCursor}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : avancement (pour mille), etat, recherche visee, FE/t, puis type et contenu
 * de chaque reservoir de sortie.
 */
public final class AnalysisUnit extends MachineUnit {

    private final TileEntityAnalyzer tile;
    final List<ItemSlot> inputSlots = new ArrayList<>();
    private final ItemSlot battery;
    final List<BasicDataTank> outputTanks = new ArrayList<>();
    private int progress;
    private Status status = Status.IDLE;
    /** Recherche visee (ordinal, -1 = aucune). */
    private int target = -1;
    /** Case traitee (tourne entre les cases pour ne pas bloquer sur un type complet). */
    private int cursor;

    AnalysisUnit(TileEntityAnalyzer tile, MachineTile.Access machine) {
        super(machine);
        this.tile = tile;
        //Ancien ordre des cases (Mekanism) : entrees, puis batterie
        for (int i = 0; i < tile.inputCount(); i++) {
            int index = i;
            int[] position = tile.inputPosition(i);
            inputSlots.add(machine.slot(new ItemSlot(() -> 64, stack -> tile.accepts(index, stack), (stack, automation) -> true,
                  (stack, automation) -> !automation.external(), machine::changed, position[0], position[1]).ghost(() -> tile.inputGhost(index))));
        }
        battery = machine.batterySlot(TileEntityAnalyzer.BATTERY_X, TileEntityAnalyzer.BATTERY_Y);
        for (int i = 0; i < tile.outputTankCount(); i++) {
            int index = i;
            outputTanks.add(tile.declareData(BasicDataTank.of(TileEntityAnalyzer.OUTPUT_CAPACITY, t -> tile.acceptsData(index, t),
                  (t, automation) -> automation != Automation.EXTERNAL, (t, automation) -> true, () -> {
                      machine.changed();
                      machine.markSync();
                  })));
        }
    }

    @Override
    public void tick() {
        machine.discharge(battery, 2_000);
        boolean active = false;
        if (level() == null || level().getServer() == null) {
            return;
        }
        ResearchState state = ResearchState.get(level().getServer());
        //Choix de l'echantillon a analyser : la premiere case utile a partir du curseur
        int chosen = -1;
        Sample sample = null;
        boolean anyItem = false, anyLocked = false, anyUnknown = false, anyFull = false;
        Sample lastSeen = null;
        for (int k = 0; k < inputSlots.size(); k++) {
            int index = (cursor + k) % inputSlots.size();
            ItemStack stack = inputSlots.get(index).getStack();
            if (stack.isEmpty()) {
                continue;
            }
            anyItem = true;
            Sample candidate = tile.sampleOf(index, stack);
            if (candidate == null) {
                anyUnknown = true;
                continue;
            }
            lastSeen = candidate;
            if (!state.isAvailable(candidate.node())) {
                anyLocked |= !state.isCompleted(candidate.node());
                continue;
            }
            if (state.getSamples(candidate.node(), candidate.type()) >= candidate.node().units) {
                continue;
            }
            if (outputFor(candidate) == null) {
                anyFull = true;
                continue;
            }
            chosen = index;
            sample = candidate;
            break;
        }
        if (sample == null) {
            status = !anyItem ? Status.IDLE : anyFull ? Status.OUTPUT_FULL : anyLocked ? Status.LOCKED
                                                                                     : anyUnknown && lastSeen == null ? Status.UNKNOWN : Status.COMPLETE;
            if (lastSeen != null) {
                target = lastSeen.node().ordinal();
            }
            if (progress != 0) {
                progress = 0;
                machine.changed();
            }
        } else {
            cursor = chosen;
            target = sample.node().ordinal();
            if (!machine.canWork()) {
                status = Status.IDLE;
            } else if (machine.use(tile.energyPerTick())) {
                active = true;
                status = Status.WORKING;
                if (++progress >= tile.ticksPerItem()) {
                    progress = 0;
                    BasicDataTank output = outputFor(sample);
                    if (output != null) {
                        output.insert(DataStack.of(sample.data(), sample.amount() * AnalysisData.PER_SAMPLE), Action.EXECUTE, Automation.INTERNAL);
                        inputSlots.get(chosen).extract(1, Action.EXECUTE, Automation.INTERNAL);
                        //Case suivante au prochain objet : les types avancent ensemble
                        cursor = (chosen + 1) % inputSlots.size();
                    }
                }
                machine.changed();
            } else {
                status = Status.NO_ENERGY;
            }
        }
        machine.markWorking(active);
    }

    /** Reservoir de sortie qui peut recevoir toutes les donnees de l'echantillon (null si aucun). */
    @Nullable
    private BasicDataTank outputFor(Sample sample) {
        DataStack stack = DataStack.of(sample.data(), sample.amount() * AnalysisData.PER_SAMPLE);
        for (BasicDataTank tank : outputTanks) {
            if (tank.insert(stack, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                return tank;
            }
        }
        return null;
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS_X1000, () -> Math.round(1000.0 * progress / tile.ticksPerItem())).add(UiValues.STATUS, status::ordinal)
              .add(UiValues.RESEARCH, () -> target).add(UiValues.FE_PER_TICK, tile::energyPerTick);
        for (int i = 0; i < outputTanks.size(); i++) {
            BasicDataTank tank = outputTanks.get(i);
            values.add(UiValues.dataType(i), () -> TileEntityAnalyzer.typeId(tank.isEmpty() ? null : tank.getType())).add(UiValues.dataStored(i), tank::getStored);
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        progress = nbt.getInt("analysisProgress");
        target = nbt.contains("analysisTarget") ? nbt.getInt("analysisTarget") : -1;
        cursor = nbt.getInt("analysisCursor");
    }

    @Override
    public void save(CompoundTag nbtTags) {
        nbtTags.putInt("analysisProgress", progress);
        nbtTags.putInt("analysisTarget", target);
        nbtTags.putInt("analysisCursor", cursor);
    }
}
