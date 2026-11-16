package fr.pioneers.tech.industry;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.world.geology.fluid.FluidDeposits;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Extraction des fluides, commune aux machines d'atelier et aux multiblocs :
 * <ul>
 *     <li>puits ({@link IndustryMachine.Kind#WELL}) : tirent d'un gisement fluide virtuel sous eux ({@link FluidDeposits}) ; debit = debit de
 *     base du gisement x multiplicateur de la machine x pression ; l'eau ou la vapeur du reservoir d'entree, injectee, remet la pression a
 *     100 % (0,5 mB injecte par mB tire ; boue de forage : 0,25) ; un gaz associe sort par le reservoir de gaz ;</li>
 *     <li>pompes ({@link IndustryMachine.Kind#PUMP}) : aspirent les blocs de fluide sous elles, 1 000 mB par bloc ; l'eau ne s'epuise
 *     jamais (le bloc reste), les autres fluides (mares de petrole...) se vident.</li>
 * </ul>
 */
public final class Extraction {

    public static final int OK = 0, NO_DEPOSIT = 1, EXHAUSTED = 2, FULL = 3, NO_FLUID = 4;
    /** Pompe : blocs de fluide explores au plus, et portee horizontale de base (+8 par tier). */
    public static final int PUMP_SCAN = 512, PUMP_RANGE = 16;

    private Extraction() {
    }

    /**
     * Une passe de puits a {@code at}. {@code multiplier} : x2 par tier, et le parallelisme des multiblocs. {@code carry} : fraction de mB
     * reportee d'un tick a l'autre (tableau d'un element, un par tete de puits ; borne a deux passes pour qu'un puits bloque ne fasse pas de
     * reserve). Le gaz principal d'un gisement de gaz va dans {@code gasOut}, son gaz associe dans {@code byOut} (sinon {@code gasOut}) ; un
     * gisement de petrole met son gaz associe dans {@code gasOut}. Les gisements de gaz donnent aussi 2 % de condensat (fluide).
     * L'injection n'est consommee que pour ce qui est vraiment tire. Renvoie OK, NO_DEPOSIT, EXHAUSTED ou FULL.
     */
    public static int well(ServerLevel level, BlockPos at, IndustryMachine machine, double multiplier, @Nullable FluidTank injection,
          @Nullable FluidTank out, @Nullable BasicGasTank gasOut, @Nullable BasicGasTank byOut, double[] carry) {
        return well(level, at, machine, multiplier, injection, out, gasOut, byOut, carry, null);
    }

    /** Air comprime injecte (gas-lift) : {@link #GAS_LIFT_AIR} L par mB tire, des {@link #GAS_LIFT_BAR} bar, quand aucun fluide n'est injecte. */
    public static final double GAS_LIFT_AIR = 2, GAS_LIFT_BAR = 2;

    public static int well(ServerLevel level, BlockPos at, IndustryMachine machine, double multiplier, @Nullable FluidTank injection,
          @Nullable FluidTank out, @Nullable BasicGasTank gasOut, @Nullable BasicGasTank byOut, double[] carry,
          @Nullable IPressureHandler air) {
        FluidDeposits.Deposit deposit = FluidDeposits.at(level, at);
        if (deposit == null || !machine.wells.contains(deposit.type().category())) {
            return NO_DEPOSIT;
        }
        long left = deposit.left(level);
        if (left <= 0) {
            return EXHAUSTED;
        }
        double pressure = deposit.pressure(level);
        double wanted = deposit.type().rate() * multiplier;
        //Recuperation secondaire : injection d'eau (boue de forage : deux fois plus efficace) ; 1.0 : la vapeur est un gaz, plus injectee
        double ratio = 0;
        if (injection != null && pressure < 1 && !injection.isEmpty()) {
            FluidStack inj = injection.getFluid();
            boolean mud = inj.getFluid().isSame(PiIndustry.fluid("drilling_mud"));
            boolean usable = mud || inj.getFluid().is(FluidTags.WATER);
            double r = mud ? 0.25 : 0.5;
            if (usable && inj.getAmount() >= Math.ceil(wanted * r)) {
                ratio = r;
                pressure = 1;
            }
        }
        //Gas-lift : l'air comprime allege la colonne et rend toute sa pression au gisement
        boolean lift = false;
        if (ratio == 0 && pressure < 1 && air != null && air.getPressure() >= GAS_LIFT_BAR && air.getAir() >= wanted * GAS_LIFT_AIR) {
            lift = true;
            pressure = 1;
        }
        carry[0] = Math.min(carry[0] + wanted * pressure, Math.max(1, 2 * wanted));
        long amount = Math.min(left, (long) carry[0]);
        if (amount <= 0) {
            return OK;
        }
        long stored;
        boolean gasDeposit = deposit.type().isGas();
        if (gasDeposit) {
            GasStack gas = deposit.type().gasStack(amount);
            if (gasOut == null || gas.isEmpty()) {
                return FULL;
            }
            GasStack rest = gasOut.insert(gas, Action.EXECUTE, Automation.INTERNAL);
            stored = amount - rest.getAmount();
        } else {
            if (out == null) {
                return FULL;
            }
            stored = fill(out, deposit.type().fluidStack((int) Math.min(Integer.MAX_VALUE, amount)));
        }
        if (stored <= 0) {
            carry[0] = Math.min(carry[0], wanted);
            return FULL;
        }
        carry[0] -= stored;
        deposit.extract(level, stored);
        if (ratio > 0) {
            drain(injection, (int) Math.ceil(stored * ratio));
        } else if (lift) {
            air.extractAir(stored * GAS_LIFT_AIR, false);
        }
        //Gaz associe (le surplus, faute de place, s'echappe)
        GasStack by = deposit.type().byproductStack(Math.round(stored * deposit.type().share()));
        BasicGasTank byTank = gasDeposit && byOut != null ? byOut : gasOut;
        if (!by.isEmpty() && byTank != null) {
            byTank.insert(by, Action.EXECUTE, Automation.INTERNAL);
        }
        //Condensat des gisements de gaz
        if (gasDeposit && out != null && stored >= 50) {
            fill(out, new FluidStack(PiIndustry.fluid("gas_condensate"), (int) Math.min(Integer.MAX_VALUE, stored / 50)));
        }
        return OK;
    }

    /**
     * Une passe de pompe sous {@code pump} : jusqu'a {@code blocks} blocs de fluide. Renvoie OK, NO_FLUID ou FULL.
     */
    public static int pump(ServerLevel level, BlockPos pump, int tier, int blocks, FluidTank out) {
        BlockPos start = pump.below();
        FluidState first = level.getFluidState(start);
        if (first.isEmpty()) {
            return NO_FLUID;
        }
        Fluid fluid = first.getType() instanceof FlowingFluid flowing ? flowing.getSource() : first.getType();
        if (out.getSpace() < 1000 || (!out.isEmpty() && !out.getFluid().getFluid().isSame(fluid))) {
            return FULL;
        }
        if (fluid.is(FluidTags.WATER) && first.isSource()) {
            //L'eau ne s'epuise pas (un bloc d'eau source dessous)
            int n = Math.min(blocks, out.getSpace() / 1000);
            fill(out, new FluidStack(fluid, 1000 * n));
            return OK;
        }
        int range = PUMP_RANGE + 8 * tier, done = 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < PUMP_SCAN && done < blocks) {
            BlockPos p = queue.poll();
            FluidState state = level.getFluidState(p);
            if (state.isEmpty() || !state.getType().isSame(fluid)) {
                continue;
            }
            if (state.isSource()) {
                BlockState block = level.getBlockState(p);
                if (out.getSpace() < 1000) {
                    break;
                }
                if (block.getBlock() instanceof BucketPickup pickup && !pickup.pickupBlock(level, p, block).isEmpty()) {
                    fill(out, new FluidStack(fluid, 1000));
                    done++;
                } else if (block.getBlock() instanceof LiquidBlock) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    fill(out, new FluidStack(fluid, 1000));
                    done++;
                }
            }
            for (Direction d : Directions.ALL) {
                BlockPos n = p.relative(d);
                if (d != Direction.UP && Math.abs(n.getX() - pump.getX()) <= range && Math.abs(n.getZ() - pump.getZ()) <= range && n.getY() < pump.getY()
                    && level.isLoaded(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return done > 0 ? OK : NO_FLUID;
    }

    private static int fill(FluidTank tank, FluidStack stack) {
        return tank instanceof IoTank io ? io.fillInternal(stack, FluidAction.EXECUTE) : tank.fill(stack, FluidAction.EXECUTE);
    }

    private static void drain(FluidTank tank, int amount) {
        if (tank instanceof IoTank io) {
            io.drainInternal(amount, FluidAction.EXECUTE);
        } else {
            tank.drain(amount, FluidAction.EXECUTE);
        }
    }
}
