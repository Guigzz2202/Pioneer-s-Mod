package fr.pioneers.rail.block;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.MachineUnit;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.pressure.PressureVolume;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailCarEntity;
import fr.pioneers.rail.train.RailFuels;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 8c) : LOGIQUE DES POSTES DE QUAI SANS CASES (bras de remplissage, pompe a carburant, borne de vapeur, prise de quai, borne
 * de recharge, fibre de quai, raccord pneumatique, quai voyageurs : code d'avant de {@link RailPostTile}, deplace) : reserves de la sorte
 * (declarees par l'unite dans le meme ordre qu'avant : fluide puis gaz, ou donnees, ou air), vehicule servi (pas a travers une frontiere de
 * claim refusee), plan de la gare logistique, transferts fluide / gaz / energie / donnees / air / voyageurs. Cles sauvees : {@code unload},
 * {@code moved}, {@code air} ; client : {@code unload}.
 * <p>
 * Valeurs nommees (apres les 4 communes, memes indices qu'avant) : sens, vehicule devant, quantite passee, sorte, air (L x10), volume d'air,
 * donnees stockees, capacite des donnees.
 */
public final class RailPostUnit extends MachineUnit {

    public static final UiValues.Key UNLOAD = UiValues.Key.of("unload"), SERVING = UiValues.Key.of("serving"), MOVED = UiValues.Key.of("moved"),
          KIND = UiValues.Key.of("kind"), AIR_X10 = UiValues.Key.of("air_x10"), VOLUME = UiValues.Key.of("volume"),
          DATA_STORED = UiValues.Key.of("data_stored"), DATA_CAPACITY = UiValues.Key.of("data_capacity");

    private final RailPostTile tile;
    private final PostKind kind;
    private final int tier;
    @Nullable
    FluidTank fluid;
    @Nullable
    BasicGasTank gas;
    @Nullable
    BasicDataTank data;
    @Nullable
    PressureVolume air;
    boolean unload;
    private boolean serving;
    private long moved;
    private int ticks;
    /** Travail impose par la gare logistique ce tick (null : reglage du joueur). */
    @Nullable
    private RailWorld.PostPlan plan;

    RailPostUnit(RailPostTile tile, MachineTile.Access machine, PostKind kind, int tier) {
        super(machine);
        this.tile = tile;
        this.kind = kind;
        this.tier = tier;
        int size = 1 << tier;
        switch (kind) {
            case FLUID -> {
                fluid = machine.fluidTank(tank(16_000 * size, f -> true));
                gas = machine.gasTank(BasicGasTank.of(32_000L * size, machine::changed));
            }
            case FUEL -> {
                fluid = machine.fluidTank(tank(32_000, f -> RailFuels.liquid(RailPostTile.id(f.getFluid())) > 0));
                gas = machine.gasTank(BasicGasTank.input(64_000, g -> RailFuels.gas(RailPostTile.id(g.getGas())) > 0, machine::changed));
            }
            case STEAM -> gas = machine.gasTank(BasicGasTank.input(64_000, g -> RailFuels.STEAM.equals(RailPostTile.id(g.getGas())), machine::changed));
            case DATA -> data = tile.declareData(BasicDataTank.create(4_000_000L * size, machine::changed));
            case AIR -> air = new PressureVolume(250 * size, 10, 40 * size, machine::changed);
            default -> {
            }
        }
    }

    private FluidTank tank(int capacity, Predicate<FluidStack> filter) {
        return new FluidTank(capacity, filter) {
            @Override
            protected void onContentsChanged() {
                machine.changed();
                machine.markSync();
            }
        };
    }

    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        ticks++;
        RailWorld w = RailWorld.get(sl);
        if (ticks % 20 == 0) {
            tile.point.update(sl, tile);
        }
        Car car = RailPostTile.carFor(w, pos(), kind);
        //1.9 (MAJ RP, bloc 4) : le poste ne charge ni ne decharge un vehicule a travers une frontiere de claim refusee
        if (car != null && !ClanApi.canTransfer(sl, pos(), RailPostTile.carPos(w, pos(), car))) {
            car = null;
        }
        serving = car != null;
        boolean worked = false;
        boolean out = kind.reversible() && unload;
        plan = car == null || tile.holds().isEmpty() ? null : w.postPlan(pos(), car);
        if (plan != null) {
            out = plan.unload();
        }
        if (car != null && machine.canWork() && (plan == null || !plan.idle())) {
            long done = switch (kind) {
                case FLUID -> car.type == CarType.TANK ? fluids(car, out) : gases(car, out);
                case FUEL -> car.type == CarType.DIESEL ? fluids(car, false) : gases(car, false);
                case STEAM -> gases(car, false);
                case ENERGY, CHARGER -> power(car, out);
                case DATA -> data(car, out);
                case AIR -> air(car, out);
                case PASSENGER -> ticks % 20 == 0 ? passengers(sl, w, car, out) : 0;
                default -> 0;
            };
            moved += done;
            worked = done > 0;
        }
        if (kind == PostKind.ENERGY && out) {
            machine.pushEnergy(kind.rate(tier));
        }
        machine.markWorking(worked);
    }

    /** Debit borne par la quantite prevue de la livraison. */
    private int cap(long rate) {
        return (int) Math.min(rate, plan == null ? rate : Math.min(Integer.MAX_VALUE, plan.limit()));
    }

    private boolean allows(Cargo c) {
        return plan == null || plan.allows(c);
    }

    private long fluids(Car car, boolean out) {
        if (fluid == null) {
            return 0;
        }
        int rate = cap(kind.rate(tier));
        if (out) {
            if (car.fluidAmount <= 0 || RailPostTile.rl(car.fluid) == null || !allows(Cargo.fluid(RailPostTile.rl(car.fluid)))) {
                return 0;
            }
            Fluid f = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(car.fluid));
            if (f == null) {
                return 0;
            }
            int n = fluid.fill(new FluidStack(f, Math.min(rate, car.fluidAmount)), IFluidHandler.FluidAction.EXECUTE);
            car.drainFluid(n, false);
            return n;
        }
        FluidStack have = fluid.getFluid();
        if (have.isEmpty() || RailPostTile.rl(RailPostTile.id(have.getFluid())) == null || !allows(Cargo.fluid(RailPostTile.rl(RailPostTile.id(have.getFluid()))))) {
            return 0;
        }
        int n = car.fillFluid(RailPostTile.id(have.getFluid()), Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            fluid.drain(n, IFluidHandler.FluidAction.EXECUTE);
        }
        return n;
    }

    private long gases(Car car, boolean out) {
        if (gas == null) {
            return 0;
        }
        int rate = cap(kind.rate(tier) * 2L);
        if (out) {
            if (car.gasAmount <= 0 || RailPostTile.rl(car.gas) == null || !allows(Cargo.gas(RailPostTile.rl(car.gas)))) {
                return 0;
            }
            Gas g = PioneersAPI.gas(ResourceLocation.parse(car.gas));
            if (g == null) {
                return 0;
            }
            GasStack rest = gas.insert(GasStack.of(g, Math.min(rate, car.gasAmount)), Action.EXECUTE, Automation.INTERNAL);
            int n = (int) (Math.min(rate, car.gasAmount) - rest.getAmount());
            car.drainGas(n, false);
            return n;
        }
        GasStack have = gas.getStack();
        if (have.isEmpty() || RailPostTile.rl(RailPostTile.id(have.getGas())) == null || !allows(Cargo.gas(RailPostTile.rl(RailPostTile.id(have.getGas()))))) {
            return 0;
        }
        int n = car.fillGas(RailPostTile.id(have.getGas()), (int) Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            gas.extract(n, Action.EXECUTE, Automation.INTERNAL);
        }
        return n;
    }

    private long power(Car car, boolean out) {
        long rate = cap(kind.rate(tier));
        if (out) {
            long n = Math.min(rate, Math.min(car.energy, machine.energy().getCapacity() - machine.energy().getStored()));
            if (n > 0) {
                car.energy -= n;
                car.touched = true;
                machine.energy().setStored(machine.energy().getStored() + n);
            }
            return Math.max(0, n);
        }
        long n = Math.min(rate, Math.min(machine.energy().getStored(), car.energyCapacity() - car.energy));
        if (n > 0) {
            car.energy += n;
            car.touched = true;
            machine.energy().setStored(machine.energy().getStored() - n);
        }
        return Math.max(0, n);
    }

    private long data(Car car, boolean out) {
        if (data == null) {
            return 0;
        }
        long rate = cap(kind.rate(tier));
        if (out) {
            if (car.data <= 0) {
                return 0;
            }
            DataType type = PioneersAPI.dataTypeRegistry().getValue(ResourceLocation.parse(car.dataType));
            if (type == null) {
                return 0;
            }
            long want = Math.min(rate, car.data);
            DataStack rest = data.insert(DataStack.of(type, want), Action.EXECUTE, Automation.INTERNAL);
            long n = want - rest.getAmount();
            car.drainData(n, false);
            return n;
        }
        DataStack have = data.getStack();
        if (have.isEmpty()) {
            return 0;
        }
        long n = car.fillData(have.getType().getRegistryName().toString(), Math.min(rate, have.getAmount()), false);
        if (n > 0) {
            data.extract(n, Action.EXECUTE, Automation.INTERNAL);
        }
        return n;
    }

    private long air(Car car, boolean out) {
        if (air == null) {
            return 0;
        }
        int rate = cap(kind.rate(tier));
        //Litres entiers : on simule, on arrondit en dessous, puis on deplace exactement cette quantite
        if (out) {
            int n = (int) Math.floor(air.insertAir(Math.min(rate, car.air), true));
            n = Math.max(0, Math.min(n, car.air));
            if (n > 0) {
                air.insertAir(n, false);
                car.air -= n;
                car.touched = true;
            }
            return n;
        }
        int room = car.airCapacity() - car.air;
        int n = (int) Math.floor(air.extractAir(Math.min(rate, room), true));
        n = Math.max(0, Math.min(n, room));
        if (n > 0) {
            air.extractAir(n, false);
            car.air += n;
            car.touched = true;
        }
        return n;
    }

    /** Quai voyageurs : embarque les villageois du quai (mode charger) ou fait descendre ceux de la voiture (decharger). */
    private long passengers(ServerLevel sl, RailWorld w, Car car, boolean out) {
        RailCarEntity view = w.view(car.id);
        if (view == null) {
            return 0;
        }
        if (out) {
            long n = 0;
            for (Entity e : List.copyOf(view.getPassengers())) {
                if (!(e instanceof Player)) {
                    e.stopRiding();
                    e.teleportTo(pos().getX() + 0.5, pos().getY() + 1, pos().getZ() + 0.5);
                    n++;
                }
            }
            return n;
        }
        long n = 0;
        AABB around = new AABB(pos()).inflate(2, 1, 2);
        for (AbstractVillager v : sl.getEntitiesOfClass(AbstractVillager.class, around, v -> !v.isPassenger())) {
            if (view.getPassengers().size() >= car.type.seats()) {
                break;
            }
            if (v.startRiding(view, true)) {
                n++;
            }
        }
        return n;
    }

    /** Sens du poste (vider le vehicule) : action du joueur, gare logistique ; la prise de quai retourne ses faces d'energie. */
    void setUnload(boolean value) {
        if (unload != value) {
            unload = value;
            tile.energySides();
            machine.changed();
            machine.markSync();
        }
    }

    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UNLOAD, () -> unload ? 1 : 0).add(SERVING, () -> serving ? 1 : 0).add(MOVED, () -> moved)
              .add(KIND, kind::ordinal).add(AIR_X10, () -> air == null ? 0 : Math.round(air.getAir() * 10))
              .add(VOLUME, () -> air == null ? 0 : Math.round(air.getVolume())).add(DATA_STORED, () -> data == null ? 0 : data.getStored())
              .add(DATA_CAPACITY, () -> data == null ? 0 : data.getCapacity());
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putBoolean("unload", unload);
        tag.putLong("moved", moved);
        if (air != null) {
            tag.put("air", air.serializeNBT());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        unload = tag.getBoolean("unload");
        moved = tag.getLong("moved");
        tile.energySides();
        if (air != null && tag.contains("air")) {
            air.deserializeNBT(tag.getCompound("air"));
        }
    }

    /** 0.20.7 : le sens du poste part aussi au client (copie des plans des robots). */
    @Override
    public void writeClient(CompoundTag tag) {
        tag.putBoolean("unload", unload);
    }

    @Override
    public void readClient(CompoundTag tag) {
        unload = tag.getBoolean("unload");
    }
}
