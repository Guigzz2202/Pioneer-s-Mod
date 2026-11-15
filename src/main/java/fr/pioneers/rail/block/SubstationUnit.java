package fr.pioneers.rail.block;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.net.Electrification;
import net.minecraft.server.level.ServerLevel;

/**
 * API Machine (lot 8c) : LOGIQUE DE LA SOUS-STATION (code d'avant de {@link SubstationTile}, deplace) : l'energie recue des cables est versee
 * dans la reserve de sa grille (tenue par le reseau ferre) ; debit tire par les trains mesure chaque seconde. Rien n'est sauve en plus des
 * cles de la machine (la reserve de la grille vit dans {@link RailWorld}).
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : reserve de la grille, contenance, energie tiree par les trains (FE/t).
 */
public final class SubstationUnit extends MachineUnit {

    public static final UiValues.Key GRID_STORED = UiValues.Key.of("grid_stored"), GRID_CAPACITY = UiValues.Key.of("grid_capacity"),
          DRAW = UiValues.Key.of("trains_draw");

    private long lastStored, rate;
    private int ticks;

    public SubstationUnit(MachineTile.Access machine) {
        super(machine);
    }

    int tier() {
        return TieredMachineBlock.tierOf(machine.state());
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        RailWorld w = RailWorld.get(sl);
        if (w.power.conductor(pos()) == null) {
            w.power.add(new Electrification.Conductor(pos().immutable(), Electrification.Kind.SUBSTATION, tier()));
        }
        long have = machine.energy().getStored();
        if (have > 0) {
            long n = w.power.receive(pos(), have);
            machine.energy().setStored(have - n);
            if (n > 0) {
                w.setDirty();
            }
        }
        long stored = w.power.stored(pos());
        if (++ticks % 20 == 0) {
            rate = Math.max(0, (lastStored - stored) / 20);
            lastStored = stored;
        }
        machine.markWorking(rate > 0);
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(GRID_STORED, () -> level() instanceof ServerLevel sl ? RailWorld.get(sl).power.stored(pos()) : 0)
              .add(GRID_CAPACITY, () -> level() instanceof ServerLevel ? Electrification.capacity(tier()) : 0)
              .add(DRAW, () -> level() instanceof ServerLevel ? rate : 0);
    }
}
