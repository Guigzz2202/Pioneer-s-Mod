package fr.pioneers.tile;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.compute.ComputeNetwork;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.data.AnalysisData;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.data.DigitalNetwork;
import fr.pioneers.data.PiDataTypes;
import fr.pioneers.data.SpaceBiomes;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ProbeNetwork;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchMilestones;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.research.ResearchState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8c) : LOGIQUE DU LABORATOIRE DE RECHERCHE (code d'avant de {@link TileEntityResearchTable}, deplace) : sortie et memoire
 * de 16 reservoirs (declares par l'unite dans le meme ordre qu'avant), unite de recherche en cours (donnees prelevees, calcul du reseau,
 * vitesse, rendu si la recherche change), donnees d'analyse et de biome converties en echantillons, puisage dans le reseau numerique,
 * impression des plans. Cles sauvees : {@code workingNode}, {@code unitTicks}, {@code printIndex} ; client : {@code labWorking},
 * {@code labNode}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : unite en cours, avancement (pour mille), attente, plan a imprimer,
 * vitesse x100, puis les sondes en service par destination.
 */
public final class ResearchUnit extends MachineUnit {

    private static final long CAPACITY = TileEntityResearchTable.CAPACITY, BIOME_SCAN_COST = TileEntityResearchTable.BIOME_SCAN_COST,
          ENERGY_PER_TICK_FE = TileEntityResearchTable.ENERGY_PER_TICK_FE, PRINT_COST = TileEntityResearchTable.PRINT_COST;
    private static final double COMPUTE_DEMAND = TileEntityResearchTable.COMPUTE_DEMAND;
    private static final int ACTION_PRINT = TileEntityResearchTable.ACTION_PRINT, ACTION_NEXT_PRINT = TileEntityResearchTable.ACTION_NEXT_PRINT;
    private static final int TYPES = ProbeDestination.values().length;
    private static final BasicDataTank EMPTY_VIEW = BasicDataTank.create(0, null);

    final ItemSlot outputSlot;
    /** Memoire : tous les types de donnees (sondes, analyses, biomes). */
    private final BasicDataTank[] memory = new BasicDataTank[TileEntityResearchTable.MEMORY_TANKS];
    /** Sondes en service par destination (pour l'interface). */
    private final int[] probes = new int[TYPES];
    /** Recherche dont une unite est en cours de traitement (-1 = aucune). */
    private int workingNode = -1;
    private double unitTicks;
    private int printIndex;
    /** Le laboratoire attend l'energie ou les donnees (0 = ok, 1 = donnees, 2 = energie, 3 = pas de recherche, 4 = calcul). */
    private int waiting;
    /** Etat visuel cote client (rendu). */
    boolean clientWorking;
    int clientNode = -1;
    private boolean lastSentWorking;
    private double computeGops;

    ResearchUnit(TileEntityResearchTable tile, MachineTile.Access machine) {
        super(machine);
        //Ancien ordre des cases (Mekanism) : la sortie seule
        outputSlot = machine.slot(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> !automation.external(), (stack, automation) -> true,
              machine::changed, TileEntityResearchTable.OUTPUT_X, TileEntityResearchTable.OUTPUT_Y));
        for (int i = 0; i < memory.length; i++) {
            int index = i;
            memory[i] = tile.declareData(BasicDataTank.of(CAPACITY, type -> !heldElsewhere(index, type), (type, automation) -> true,
                  (type, automation) -> automation != Automation.EXTERNAL, this::memoryChanged));
        }
    }

    private void memoryChanged() {
        machine.changed();
        machine.markSync();
    }

    private static List<ResearchNode> printable(Predicate<ResearchNode> completed) {
        return TileEntityResearchTable.printable(completed);
    }

    @Override
    public void tick() {
        long now = level().getGameTime();
        if (now % 20 == 0) {
            ProbeNetwork network = ProbeNetwork.get(level().getServer());
            for (ProbeDestination type : ProbeDestination.values()) {
                probes[type.ordinal()] = network.getProbes(type);
            }
        }
        ResearchState state = ResearchState.get(level().getServer());
        if (now % 10 == 0) {
            convertAnalysis(state);
        }
        ResearchNode active = state.getActive();

        //La recherche active a change pendant le traitement : l'unite est rendue
        if (workingNode >= 0 && (active == null || active.ordinal() != workingNode)) {
            refund(ResearchNode.byIndex(workingNode));
            workingNode = -1;
            unitTicks = 0;
        }
        //0.18.0 : plancher de calcul selon l'ere ; 0.17.2 : calcul du reseau (cables de donnees, ou colle a un centre de donnees),
        //vitesse x(1 + GOPS/1000)
        ResearchNode current = workingNode >= 0 ? ResearchNode.byIndex(workingNode) : active;
        double gFloor = current == null ? 0 : ResearchMilestones.gopsFloor(current), qFloor = current == null ? 0 : ResearchMilestones.qopsFloor(current);
        computeGops = current != null && machine.canWork() ? ComputeNetwork.demand(level(), pos(), Math.max(COMPUTE_DEMAND, gFloor), qFloor) : 0;
        boolean computeOk = computeGops + 1e-6 >= gFloor
                            && (qFloor <= 0 || ComputeNetwork.grantedQops(level(), pos()) + 1e-6 >= qFloor);
        if (active == null) {
            waiting = 3;
        } else if (workingNode < 0) {
            if (!machine.canWork()) {
                waiting = 2;
            } else if (!computeOk) {
                waiting = 4;
            } else if (hasUnitData(active) || pullFromNetwork(active) && hasUnitData(active)) {
                for (Map.Entry<ProbeDestination, Long> cost : active.getUnitCost().entrySet()) {
                    setData(cost.getKey(), getData(cost.getKey()) - cost.getValue());
                }
                workingNode = active.ordinal();
                unitTicks = 0;
                waiting = 0;
            } else {
                waiting = 1;
            }
        }
        if (workingNode >= 0) {
            if (!computeOk) {
                waiting = 4;
            } else if (machine.canWork() && machine.use(ENERGY_PER_TICK_FE)) {
                unitTicks += getSpeed();
                waiting = 0;
                state.heartbeat(level().getServer(), pos(), now);
                ResearchNode node = ResearchNode.byIndex(workingNode);
                if (unitTicks >= node.unitTicks()) {
                    finishUnit(state, node);
                }
            } else {
                waiting = 2;
            }
        }
        boolean working = workingNode >= 0 && waiting == 0;
        machine.markWorking(working);
        if (working != lastSentWorking) {
            lastSentWorking = working;
            machine.markSync();
        }
    }

    private void finishUnit(ResearchState state, ResearchNode node) {
        workingNode = -1;
        unitTicks = 0;
        if (state.getActive() != node) {
            refund(node);
            return;
        }
        int before = state.getProgress(node);
        ResearchNode done = state.addUnit(level().getServer(), node);
        if (done == null && state.getProgress(node) == before) {
            //Unite refusee (recherche deja terminee par un autre laboratoire)
            refund(node);
            return;
        }
        if (done != null) {
            ItemStack blueprint = done.getBlueprint();
            if (blueprint != null) {
                outputSlot.insert(blueprint, Action.EXECUTE, Automation.INTERNAL);
            }
            level().playSound(null, pos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
        } else {
            level().playSound(null, pos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 0.8F + level().random.nextFloat() * 0.6F);
        }
        machine.changed();
    }

    /**
     * Donnees d'analyse vers echantillons : 1 echantillon par {@link AnalysisData#PER_SAMPLE} Mo, tant que la recherche est disponible.
     * Les donnees devenues inutiles (recherche terminee, type d'echantillon complet) sont effacees pour liberer le reservoir.
     */
    private void convertAnalysis(ResearchState state) {
        for (BasicDataTank tank : memory) {
            if (tank.isEmpty() || PiDataTypes.origin(tank.getType()) != null) {
                continue;
            }
            String biome = PiDataTypes.biomeOf(tank.getType());
            if (biome != null) {
                convertBiome(state, tank, biome);
                continue;
            }
            AnalysisData.Target target = AnalysisData.targetOf(tank.getType());
            if (target == null || state.isCompleted(target.node()) || state.getSamples(target.node(), target.type()) >= target.node().units) {
                tank.setStack(DataStack.EMPTY);
                continue;
            }
            if (!state.isAvailable(target.node())) {
                continue;
            }
            int samples = (int) Math.min(Integer.MAX_VALUE, tank.getStored() / AnalysisData.PER_SAMPLE);
            if (samples > 0) {
                int accepted = state.addSamples(level().getServer(), target.node(), target.type(), samples);
                if (accepted > 0) {
                    tank.extract(accepted * AnalysisData.PER_SAMPLE, Action.EXECUTE, Automation.INTERNAL);
                }
            }
        }
    }

    /**
     * Donnees de biome : {@link #BIOME_SCAN_COST} Mo valident l'exploration du biome pour la recherche de surface de son astre.
     * Une fois le biome valide (ou la recherche terminee), le reste est efface.
     */
    private void convertBiome(ResearchState state, BasicDataTank tank, String biome) {
        Body body = SpaceBiomes.bodyOf(biome);
        ResearchNode node = body == null ? null : BodyResearch.node(body, ResearchNode.Kind.SURFACE);
        if (node == null || state.isCompleted(node) || state.isScanned(node, biome)) {
            tank.setStack(DataStack.EMPTY);
        } else if (state.isAvailable(node) && tank.getStored() >= BIOME_SCAN_COST) {
            tank.extract(BIOME_SCAN_COST, Action.EXECUTE, Automation.INTERNAL);
            state.scan(level().getServer(), node, biome);
        }
    }

    /** Un autre reservoir de la memoire contient deja ce type. */
    private boolean heldElsewhere(int index, DataType type) {
        for (int i = 0; i < memory.length; i++) {
            if (i != index && memory[i] != null && !memory[i].isEmpty() && memory[i].getType() == type) {
                return true;
            }
        }
        return false;
    }

    /** Reservoir d'un type de donnees (null s'il n'y en a pas). */
    @Nullable
    private BasicDataTank tankOf(DataType type) {
        for (BasicDataTank tank : memory) {
            if (!tank.isEmpty() && tank.getType() == type) {
                return tank;
            }
        }
        return null;
    }

    /** Donnees autres que celles des sondes (analyses, biomes), jusqu'a quatre, pour l'interface. */
    public List<BasicDataTank> getAnalysisTanks() {
        List<BasicDataTank> list = new ArrayList<>();
        for (BasicDataTank tank : memory) {
            if (!tank.isEmpty() && PiDataTypes.origin(tank.getType()) == null && list.size() < 4) {
                list.add(tank);
            }
        }
        while (list.size() < 4) {
            list.add(EMPTY_VIEW);
        }
        return list;
    }

    void refund(ResearchNode node) {
        for (Map.Entry<ProbeDestination, Long> cost : node.getUnitCost().entrySet()) {
            setData(cost.getKey(), Math.min(CAPACITY, getData(cost.getKey()) + cost.getValue()));
        }
    }

    /**
     * 0.20.46 : stock du reseau numerique : il manque des donnees pour une unite, le laboratoire les puise dans les banques de donnees
     * du reseau (plus besoin de regler une face de banque en sortie).
     *
     * @return vrai si quelque chose a ete puise.
     */
    private boolean pullFromNetwork(ResearchNode node) {
        if (level() == null || level().getGameTime() % 10 != 0) {
            return false;
        }
        boolean any = false;
        for (Map.Entry<ProbeDestination, Long> cost : node.getUnitCost().entrySet()) {
            long missing = Math.min(cost.getValue(), CAPACITY) - getData(cost.getKey());
            if (missing > 0) {
                long got = DigitalNetwork.pull(level(), pos(), PiDataTypes.of(cost.getKey()), missing,
                      Action.EXECUTE);
                if (got > 0) {
                    setData(cost.getKey(), getData(cost.getKey()) + got);
                    any = true;
                }
            }
        }
        return any;
    }

    private boolean hasUnitData(ResearchNode node) {
        for (Map.Entry<ProbeDestination, Long> cost : node.getUnitCost().entrySet()) {
            if (getData(cost.getKey()) < cost.getValue()) {
                return false;
            }
        }
        return true;
    }

    public double getSpeed() {
        return ResearchEffects.labSpeedMultiplier() * (1 + Math.min(COMPUTE_DEMAND, computeGops) / 1000);
    }

    @Nullable
    public ResearchNode getPrintTarget(Predicate<ResearchNode> completed) {
        List<ResearchNode> list = printable(completed);
        return list.isEmpty() ? null : list.get(Math.floorMod(printIndex, list.size()));
    }

    void handleCustom(int action) {
        if (action == ACTION_NEXT_PRINT) {
            printIndex++;
            machine.changed();
        } else if (action == ACTION_PRINT) {
            ResearchNode target = getPrintTarget(ResearchEffects::has);
            if (target == null || getData(ProbeDestination.EARTH_ORBIT) < PRINT_COST) {
                return;
            }
            ItemStack blueprint = target.getBlueprint();
            if (outputSlot.insert(blueprint, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                outputSlot.insert(blueprint, Action.EXECUTE, Automation.INTERNAL);
                setData(ProbeDestination.EARTH_ORBIT, getData(ProbeDestination.EARTH_ORBIT) - PRINT_COST);
                level().playSound(null, pos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    public long getData(ProbeDestination type) {
        BasicDataTank tank = tankOf(PiDataTypes.of(type));
        return tank == null ? 0 : tank.getStored();
    }

    void setData(ProbeDestination type, long value) {
        long clamped = Math.max(0, Math.min(CAPACITY, value));
        DataType dataType = PiDataTypes.of(type);
        BasicDataTank tank = tankOf(dataType);
        if (tank == null) {
            if (clamped <= 0) {
                return;
            }
            for (BasicDataTank candidate : memory) {
                if (candidate.isEmpty()) {
                    tank = candidate;
                    break;
                }
            }
            if (tank == null) {
                return;
            }
        }
        tank.setStack(clamped <= 0 ? DataStack.EMPTY : DataStack.of(dataType, clamped));
        memoryChanged();
    }

    public int getProbes(ProbeDestination destination) {
        return probes[destination.ordinal()];
    }

    /** Avancement de l'unite en cours (0..1). */
    public double getUnitProgress() {
        if (workingNode < 0) {
            return 0;
        }
        return Math.min(1, unitTicks / ResearchNode.byIndex(workingNode).unitTicks());
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.UNIT, () -> workingNode).add(UiValues.PROGRESS_X1000, () -> Math.round(getUnitProgress() * 1000)).add(UiValues.WAITING, () -> waiting)
              .add(UiValues.PRINT, () -> printIndex).add(UiValues.SPEED_X100, () -> Math.round(getSpeed() * 100));
        for (int i = 0; i < TYPES; i++) {
            int index = i;
            values.add(UiValues.item("probes", i), () -> probes[index]);
        }
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("labWorking", workingNode >= 0 && waiting == 0);
        tag.putInt("labNode", workingNode);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientWorking = tag.getBoolean("labWorking");
        clientNode = tag.contains("labNode") ? tag.getInt("labNode") : -1;
    }

    @Override
    public void load(CompoundTag nbt) {
        workingNode = nbt.contains("workingNode") ? nbt.getInt("workingNode") : -1;
        unitTicks = nbt.getDouble("unitTicks");
        printIndex = nbt.getInt("printIndex");
    }

    @Override
    public void save(CompoundTag nbtTags) {
        nbtTags.putInt("workingNode", workingNode);
        nbtTags.putDouble("unitTicks", unitTicks);
        nbtTags.putInt("printIndex", printIndex);
    }
}
