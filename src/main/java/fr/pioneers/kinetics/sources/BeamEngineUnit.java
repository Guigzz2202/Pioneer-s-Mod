package fr.pioneers.kinetics.sources;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.api.machine.unit.PowerSource;
import fr.pioneers.api.machine.unit.Surplus;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.kinetics.KineticsConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/**
 * API Machine (lot 8b) : UNITE de la machine a balancier (chaudiere et piston), code de l'ancienne {@code BeamEngineTile} qui ne fait plus
 * que la porter (noeud source du reseau de rotation : couple de demarrage = {@link BeamEngineTile#TAU0} x pression). Thermique simple : le
 * foyer apporte {@link BeamEngineTile#FIRE} W tant qu'il brule ; la chaudiere ({@link BeamEngineTile#CAPACITY} J/K) perd vers l'air ; au-dela
 * de 373 K l'eau bout et la pression monte (0 a 1, pleine a 403 K) ; l'eau s'evapore au prorata de la puissance fournie ; a sec, elle
 * surchauffe et se coupe. La rotation (vitesse du noeud) est LUE ({@link PowerSource.Kinetic}) : la machine est une source, elle ne declare
 * aucune charge. Sauvegarde au format d'avant ({@code slots}, {@code water}, {@code temperature}, {@code burn}, {@code burnTotal}) ; cases :
 * combustible, seau d'eau, seau vide (ordre d'avant).
 */
public class BeamEngineUnit extends MachineUnit {

    private final PowerSource.Kinetic power;
    private final FluidTank water;
    final ItemSlot fuel, bucketIn, bucketOut;
    private double temperature = 300;
    /** 1.0.4-beta (lot 5) : chaleur perdue au maximum (interface : « Surplus perdu »). */
    public final Surplus surplus = new Surplus();
    private int burn, burnTotal;
    private boolean dry;
    private float beam;

    public BeamEngineUnit(UnitHost machine) {
        super(machine);
        this.power = (PowerSource.Kinetic) machine.power();
        water = new FluidTank(4_000, stack -> stack.getFluid().isSame(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                machine.changed();
            }
        };
        fuel = machine.slot(new ItemSlot(() -> 64, stack -> ForgeHooks.getBurnTime(stack, null) > 0 && !Buckets.isWaterBucket(stack),
              (s, a) -> true, (s, a) -> !a.external(), machine::changed, BeamEngineTile.FUEL_X, BeamEngineTile.FUEL_Y));
        bucketIn = machine.slot(new ItemSlot(() -> 1, Buckets::isWaterBucket, (s, a) -> true, (s, a) -> !a.external(), machine::changed,
              BeamEngineTile.BUCKET_X, BeamEngineTile.BUCKET_Y));
        bucketOut = machine.slot(new ItemSlot(() -> 16, stack -> false, (s, a) -> false, (s, a) -> true, machine::changed, BeamEngineTile.EMPTY_X,
              BeamEngineTile.EMPTY_Y));
    }

    public FluidTank water() {
        return water;
    }

    public List<ItemSlot> slots() {
        return List.of(fuel, bucketIn, bucketOut);
    }

    /** Pression de la chaudiere (0 a 1). */
    public double pressure() {
        return water.getFluidAmount() < 50 ? 0 : Math.max(0, Math.min(1, (temperature - BeamEngineTile.BOIL) / (BeamEngineTile.FULL - BeamEngineTile.BOIL)));
    }

    public double temperature() {
        return temperature;
    }

    /** Couple de demarrage de la source (N.m). */
    public double sourceTorque() {
        return BeamEngineTile.TAU0 * pressure() * KineticsConfig.sourcePower();
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = pos();
        double speed = power.speed();
        //Remplissage par seau
        //1.0.1 : tout seau d'eau ; le seau vide qu'il rend (vanilla, en argile...) s'empile avec ceux deja sortis
        ItemStack emptied = water.getSpace() >= Buckets.VOLUME ? Buckets.drained(bucketIn.getStack(), Fluids.WATER, false) : ItemStack.EMPTY;
        if (!emptied.isEmpty() && (bucketOut.isEmpty() || ItemStack.isSameItemSameTags(bucketOut.getStack(), emptied)
              && bucketOut.getCount() < Math.min(16, emptied.getMaxStackSize()))) {
            water.fill(new FluidStack(Fluids.WATER, Buckets.VOLUME), IFluidHandler.FluidAction.EXECUTE);
            bucketIn.setStack(ItemStack.EMPTY);
            if (bucketOut.isEmpty()) {
                bucketOut.setStack(emptied);
            } else {
                bucketOut.getStack().grow(1);
            }
        }
        //Foyer
        //1.0.4-beta (lot 5) : le feu ne s'eteint plus chaudiere au maximum : il brule toujours, la chaleur en trop se perd (Surplus)
        if (burn <= 0 && !fuel.isEmpty() && !dry) {
            int time = ForgeHooks.getBurnTime(fuel.getStack(), null);
            if (time > 0) {
                burnTotal = burn = time;
                ItemStack remainder = fuel.getStack().getCraftingRemainingItem();
                fuel.extract(1, Action.EXECUTE, Automation.INTERNAL);
                if (fuel.isEmpty() && !remainder.isEmpty()) {
                    fuel.setStack(remainder);
                }
            }
        }
        double heat = 0;
        if (burn > 0) {
            burn--;
            heat += BeamEngineTile.FIRE;
        }
        heat -= (temperature - 300) * BeamEngineTile.LOSS;
        //Vapeur consommee par le piston : la puissance fournie s'evapore en eau
        double out = Math.max(0, sourceTorque() * Math.max(0, 1 - Math.abs(speed) / BeamEngineTile.FREE)) * Math.abs(speed) * 2 * Math.PI / 60;
        heat -= out * 0.05 * 1.2;
        if (out > 0) {
            water.drain(Math.max(1, (int) Math.round(out / 1650)), IFluidHandler.FluidAction.EXECUTE);
        }
        temperature = Math.max(300, temperature + heat / BeamEngineTile.CAPACITY);
        dry = water.getFluidAmount() < 50 && temperature > BeamEngineTile.BOIL;
        if (temperature > BeamEngineTile.MAX_T) {
            //au maximum : la chaudiere y reste (la soupape laisse partir le surplus) ; avant, le feu etait coupe
            surplus.note(level.getGameTime(), 1, 0);
            temperature = BeamEngineTile.MAX_T;
        }
        boolean lit = burn > 0;
        BlockState state = machine.state();
        if (state.getValue(BeamEngineBlock.LIT) != lit) {
            //0.20.46 (audit) : changement purement visuel (pas de signal ni de comparateur) : plus de mise a jour des voisins (elle faisait
            //reconstruire le reseau) ; les observateurs le voient toujours (mise a jour des formes gardee)
            level.setBlock(pos, state.setValue(BeamEngineBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        if (pressure() > 0.2 && Math.abs(speed) > 0.5) {
            float before = beam;
            beam += (float) (speed * KineticTile.DEG_PER_TICK);
            if ((int) (before / 180) != (int) (beam / 180)) {
                level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.35F, 0.6F);
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.02);
            }
            if (level.getGameTime() % 200 == 0) {
                KineticEvents.award(level, pos, "beam_engine");
            }
        }
        if (lit && level.getGameTime() % 10 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 1, 0.05, 0.1, 0.05, 0.01);
        }
    }

    // ------------------------------------------------------------------
    // Interface : 0 vitesse x100, 1 couple x10, 2 temperature x10 (K), 3 pression x1000, 4 eau, 5 capacite eau, 6 combustion x1000, 7 a sec
    // (memes indices qu'avant)
    // ------------------------------------------------------------------

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.SPEED, () -> Math.round(power.speed() * 100)).add(UiValues.TORQUE, () -> Math.round(power.torque() * 10))
              .add(UiValues.BOILER, () -> Math.round(temperature * 10)).add(UiValues.PRESSURE, () -> Math.round(pressure() * 1000))
              .add(UiValues.WATER, water::getFluidAmount).add(UiValues.WATER_MAX, water::getCapacity)
              .add(UiValues.BURN, () -> burnTotal <= 0 ? 0 : Math.round(burn * 1000.0 / burnTotal)).add(UiValues.DRY, () -> dry ? 1 : 0)
              .add(UiValues.SURPLUS, () -> level() != null && surplus.active(level().getGameTime()) ? 1 : 0);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.put("slots", ItemSlots.save(slots()));
        tag.put("water", water.writeToNBT(new CompoundTag()));
        tag.putDouble("temperature", temperature);
        tag.putInt("burn", burn);
        tag.putInt("burnTotal", burnTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        ItemSlots.load(slots(), tag.getList("slots", 10));
        water.readFromNBT(tag.getCompound("water"));
        temperature = tag.contains("temperature") ? tag.getDouble("temperature") : 300;
        burn = tag.getInt("burn");
        burnTotal = tag.getInt("burnTotal");
    }
}
