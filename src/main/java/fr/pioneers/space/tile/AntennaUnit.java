package fr.pioneers.space.tile;

import fr.pioneers.api.data.DataSideMode;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.data.BasicDataTank;
import fr.pioneers.core.data.DataLinks;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ProbeNetwork;
import fr.pioneers.space.research.ResearchEffects;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * API Machine (lot 8c) : LOGIQUE DE L'ANTENNE DE RECEPTION (code d'avant de {@link TileEntityDataAntenna}, deplace) : reservoir de donnees
 * en attente (declare par l'unite, seul reservoir comme avant), ecoute de la destination (flux partage entre antennes), cable branche,
 * debit de la derniere seconde, parabole qui tourne chez le client. Cle sauvee : {@code destination} ; client : {@code listening},
 * {@code destination}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : destination, sondes, debit, cable branche, en ecoute.
 */
public final class AntennaUnit extends MachineUnit {

    private static final long MAX_RATE = TileEntityDataAntenna.MAX_RATE, ENERGY_PER_TICK_FE = TileEntityDataAntenna.ENERGY_PER_TICK_FE;

    private final TileEntityDataAntenna tile;
    /** Donnees en attente d'envoi (un seul type a la fois). */
    final BasicDataTank buffer;
    int destination;
    private int probes;
    /** Donnees recues pendant la derniere seconde (mB). */
    private long rate;
    private long receivedThisSecond;
    private boolean connected;
    private boolean listening;
    boolean clientActive;

    AntennaUnit(TileEntityDataAntenna tile, MachineTile.Access machine) {
        super(machine);
        this.tile = tile;
        buffer = tile.declareData(BasicDataTank.output(TileEntityDataAntenna.BUFFER_CAPACITY, () -> {
            machine.changed();
            machine.markSync();
        }));
    }

    ProbeDestination getDestination() {
        return ProbeDestination.byIndex(destination);
    }

    @Override
    public void tick() {
        ProbeDestination target = getDestination();
        ProbeNetwork network = ProbeNetwork.get(level().getServer());
        listening = false;
        if (machine.canWork() && machine.use(ENERGY_PER_TICK_FE)) {
            listening = true;
            //Des donnees d'un autre type (antenne re-accordee) doivent d'abord etre envoyees
            if (buffer.isEmpty() || buffer.getType() == PiDataTypes.of(target)) {
                long received = network.extract(target, Math.min((long) (MAX_RATE * ResearchEffects.antennaRateMultiplier()), buffer.getNeeded()));
                if (received > 0) {
                    buffer.insert(DataStack.of(PiDataTypes.of(target), received), Action.EXECUTE, Automation.INTERNAL);
                    receivedThisSecond += received;
                }
            }
        }
        if (level().getGameTime() % 20 == 0) {
            connected = false;
            for (Direction side : Directions.ALL) {
                if (tile.getDataMode(side) == DataSideMode.OUTPUT && DataLinks.isLinked(level(), pos(), side)) {
                    connected = true;
                    break;
                }
            }
            rate = receivedThisSecond;
            receivedThisSecond = 0;
            probes = network.getProbes(target);
        }
        machine.markWorking(listening);
        if (listening != clientActive) {
            clientActive = listening;
            machine.markSync();
        }
    }

    /** Destination precedente ou suivante (reglage du joueur). */
    void step(boolean previous) {
        int count = ProbeDestination.values().length;
        destination = Math.floorMod(destination + (previous ? -1 : 1), count);
        probes = ProbeNetwork.get(level().getServer()).getProbes(getDestination());
        machine.changed();
        machine.syncNow();
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.DESTINATION, () -> destination).add(UiValues.PROBES, () -> probes).add(UiValues.DATA_RATE, () -> rate)
              .add(UiValues.LINKED, () -> connected ? 1 : 0).add(UiValues.LISTENING, () -> listening ? 1 : 0);
    }

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("listening", clientActive);
        tag.putInt("destination", destination);
    }

    @Override
    public void readClient(CompoundTag tag) {
        clientActive = tag.getBoolean("listening");
        destination = tag.getInt("destination");
    }

    @Override
    public void load(CompoundTag nbt) {
        destination = nbt.getInt("destination");
    }

    @Override
    public void save(CompoundTag nbtTags) {
        nbtTags.putInt("destination", destination);
    }
}
