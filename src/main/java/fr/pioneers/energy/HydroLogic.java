package fr.pioneers.energy;

import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.Directions;
import fr.pioneers.core.machine.MachineTile;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 0.20.45 : turbine de barrage. Elle se pose dans le mur d'une retenue : prise d'eau a l'ARRIERE (dans l'eau de la retenue), sortie a
 * l'AVANT (air ou eau libre, en aval). Toutes les {@link #SURVEY_TICKS} ticks, elle parcourt l'eau reliee a sa prise (au plus 1,5 fois le
 * volume voulu) : sources d'eau, surface la plus haute, et autres turbines puisant dans la meme retenue.
 * Facteur = min(chute, {@link EnergyKind#HYDRO_HEAD}) / 16 x min(1, sources par turbine / {@link EnergyKind#HYDRO_VOLUME}).
 * Si la sortie est bouchee, ou si l'eau devant appartient a la retenue (pas de barrage), rien.
 * <p>
 * Interface : 8 hauteur de chute, 9 sources d'eau de la retenue, 10 turbines sur la retenue, 11 etat (0 marche, 1 pas d'eau derriere,
 * 2 sortie bouchee, 3 pas de denivele).
 * <p>
 * API Machine (lot 3) : logique ({@link fr.pioneers.api.machine.unit.RenewableUnit}), code repris de l'ancienne {@code HydroTurbineTile}.
 */
public class HydroLogic extends RenewableUnit {

    public static final int SURVEY_TICKS = HydroTurbineTile.SURVEY_TICKS;
    public static final int OK = HydroTurbineTile.OK, NO_WATER = HydroTurbineTile.NO_WATER, BLOCKED = HydroTurbineTile.BLOCKED,
          NO_HEAD = HydroTurbineTile.NO_HEAD;

    private int head, volume, turbines = 1, status = NO_WATER, wait;

    public HydroLogic(MachineTile.Access machine, int tier) {
        super(machine, tier, EnergyKind.HYDRO);
    }

    @Override
    protected double survey(ServerLevel level) {
        if (--wait <= 0) {
            wait = SURVEY_TICKS / SCAN;
            measure(level);
        }
        if (status != OK) {
            return 0;
        }
        double share = volume / (double) Math.max(1, turbines);
        return Math.min(head, EnergyKind.HYDRO_HEAD) / (double) EnergyKind.HYDRO_HEAD * Math.min(1, share / EnergyKind.HYDRO_VOLUME[tier]);
    }

    private void measure(ServerLevel level) {
        Direction front = machine.tile().facing();
        BlockPos intake = pos().relative(front.getOpposite());
        BlockPos outlet = pos().relative(front);
        head = volume = 0;
        turbines = 1;
        if (!level.getFluidState(intake).is(FluidTags.WATER)) {
            status = NO_WATER;
            return;
        }
        BlockState out = level.getBlockState(outlet);
        if (!out.getCollisionShape(level, outlet).isEmpty()) {
            status = BLOCKED;
            return;
        }
        int limit = EnergyKind.HYDRO_VOLUME[tier] * 3 / 2;
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> others = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(intake);
        queue.add(intake);
        int top = intake.getY();
        boolean dam = true;
        while (!queue.isEmpty() && seen.size() < limit * 2) {
            BlockPos at = queue.poll();
            FluidState fluid = level.getFluidState(at);
            if (fluid.isSource()) {
                volume++;
            }
            top = Math.max(top, at.getY());
            if (at.equals(outlet)) {
                dam = false;
            }
            if (volume >= limit) {
                break;
            }
            for (Direction d : Directions.ALL) {
                BlockPos next = at.relative(d);
                if (seen.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                //Autre turbine dont la prise donne sur cette eau
                if (nextState.getBlock() instanceof GeneratorBlock gen && gen.kind == EnergyKind.HYDRO && !next.equals(pos())
                    && next.relative(nextState.getValue(GeneratorBlock.FACING).getOpposite()).equals(at)) {
                    others.add(next.immutable());
                }
                if (level.getFluidState(next).is(FluidTags.WATER)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        turbines = 1 + others.size();
        head = top - pos().getY();
        status = !dam || head < 1 ? NO_HEAD : OK;
    }

    @Override
    protected long detail(int index) {
        return switch (index) {
            case 0 -> head;
            case 1 -> volume;
            case 2 -> turbines;
            default -> status;
        };
    }
}
