package fr.pioneers.rail.block;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.train.Car;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * API Machine (lot 8c) : LOGIQUE DE LA GRUE A EAU (code d'avant de {@link WaterCraneTile}, deplace) : reserve de 16 000 mB (declaree par
 * l'unite, seul reservoir de la machine comme avant), remplie par les conduites ou par une source d'eau dessous (100 mB/t) ; verse 250 mB/t
 * dans la locomotive ou le tender arrete a cote (pas a travers une frontiere de claim refusee). Cle sauvee : {@code poured}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : vehicule servi (0/1), eau versee depuis la pose (mB).
 */
public final class WaterCraneUnit extends MachineUnit {

    public static final int RATE = 250, SPRING = 100;
    public static final UiValues.Key SERVING = UiValues.Key.of("serving"), POURED = UiValues.Key.of("poured");

    final FluidTank tank;
    private boolean serving;
    private long poured;

    public WaterCraneUnit(MachineTile.Access machine) {
        super(machine);
        tank = machine.fluidTank(new FluidTank(16_000, f -> f.getFluid().isSame(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                machine.changed();
                machine.markSync();
            }
        });
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        BlockState below = sl.getBlockState(pos().below());
        if (below.getFluidState().is(FluidTags.WATER) && below.getFluidState().isSource()) {
            tank.fill(new FluidStack(Fluids.WATER, SPRING), IFluidHandler.FluidAction.EXECUTE);
        }
        Car car = null;
        RailWorld w = RailWorld.get(sl);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Car c = w.carAt(pos().relative(d));
            //1.9 (MAJ RP, bloc 4) : pas d'eau a travers une frontiere de claim refusee
            if (c != null && c.waterCapacity() > 0 && ClanApi.canTransfer(sl, pos(), pos().relative(d))) {
                car = c;
                break;
            }
        }
        serving = car != null;
        boolean worked = false;
        if (car != null && machine.canWork()) {
            int n = Math.min(RATE, Math.min(tank.getFluidAmount(), car.waterCapacity() - car.water));
            if (n > 0) {
                tank.drain(n, IFluidHandler.FluidAction.EXECUTE);
                car.water += n;
                car.touched = true;
                poured += n;
                worked = true;
            }
        }
        machine.markWorking(worked);
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(SERVING, () -> serving ? 1 : 0).add(POURED, () -> poured);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("poured", poured);
    }

    @Override
    public void load(CompoundTag tag) {
        poured = tag.getLong("poured");
    }
}
