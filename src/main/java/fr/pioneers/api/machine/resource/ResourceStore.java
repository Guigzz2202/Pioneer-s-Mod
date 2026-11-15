package fr.pioneers.api.machine.resource;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasTankHandler;
import fr.pioneers.core.heat.HeatCapacitor;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.pressure.PressureVolume;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.side.Sided;
import fr.pioneers.data.BasicDataTank;
import fr.pioneers.transport.Transfer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 3) : STOCKAGE D'UNE RESSOURCE ({@link Resource}) : ce que l'objet de capacite Forge expose, la quantite, le remplissage,
 * la nature du contenu (jauges du client), la vue d'une face reglee (entree seule, sortie seule), l'ejection par les faces de sortie, le
 * remplissage du creatif et la sauvegarde sous {@code contents/<cle>} — exactement le format des buffers d'avant le lot 3 (rien n'est ecrit
 * pour un stockage vide). Une sous-classe par ressource, autour des stockages du socle ({@link ItemSlot}, {@link FluidTank},
 * {@link BasicGasTank}, {@link EnergyBuffer}, {@link HeatCapacitor}, {@link PressureVolume}).
 */
public abstract class ResourceStore {

    public final Resource resource;

    protected ResourceStore(Resource resource) {
        this.resource = resource;
    }

    /** L'objet expose par la capacite Forge de la ressource. */
    public abstract Object handler();

    /** Quantite contenue, dans l'unite de la ressource (objets, mB, FE, J pour la chaleur, litres d'air). */
    public abstract double amount();

    /** Remplissage 0..1. */
    public abstract double fill();

    /** Nature du contenu (fluide, gaz) pour les jauges du client ; null si vide ou sans nature. */
    @Nullable
    public Object content() {
        return null;
    }

    /** Vue d'une face en entree seule ou sortie seule (la chaleur passe dans les deux sens). */
    public abstract Object sided(SideMode mode);

    /** Ejection par les faces de sortie ({@code max} : debit du cable du tier sur 10 ticks). */
    public abstract void eject(Level level, BlockPos pos, SideConfig sides, long max);

    /** Creatif : le contenu reste plein. */
    public abstract void topUp();

    /** Ecrit le contenu dans {@code contents} (rien s'il est vide). */
    public abstract void save(CompoundTag contents);

    /** Relit le contenu ({@code contents} vide : stockage vide). */
    public abstract void load(CompoundTag contents);

    // ------------------------------------------------------------------
    // Les stockages
    // ------------------------------------------------------------------

    /** Objets : des cases (plusieurs piles par case pour les buffers, 1.12.3). */
    public static final class Items extends ResourceStore {

        public final List<ItemSlot> slots;
        private final ItemSlots handler;

        public Items(List<ItemSlot> slots) {
            super(Resource.ITEMS);
            this.slots = slots;
            handler = new ItemSlots(slots, Automation.EXTERNAL);
        }

        @Override
        public Object handler() {
            return handler;
        }

        @Override
        public double amount() {
            int n = 0;
            for (ItemSlot slot : slots) {
                n += slot.getCount();
            }
            return n;
        }

        public long used() {
            return slots.stream().filter(slot -> !slot.isEmpty()).count();
        }

        @Override
        public double fill() {
            double sum = 0;
            for (ItemSlot slot : slots) {
                sum += slot.isEmpty() ? 0 : slot.getCount() / (double) slot.getLimit(slot.getStack());
            }
            return slots.isEmpty() ? 0 : sum / slots.size();
        }

        @Override
        public Object sided(SideMode mode) {
            return Sided.items(handler, mode);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            Ejector.eject(level, pos, sides, Transfer.ITEMS, (IItemHandler) handler, max);
        }

        @Override
        public void topUp() {
            slots.forEach(slot -> {
                if (!slot.isEmpty() && slot.getCount() < slot.getLimit(slot.getStack())) {
                    slot.getStack().setCount(slot.getLimit(slot.getStack()));
                }
            });
        }

        @Override
        public void save(CompoundTag contents) {
            if (slots.stream().anyMatch(slot -> !slot.isEmpty())) {
                contents.put("items", ItemSlots.save(slots));
            }
        }

        @Override
        public void load(CompoundTag contents) {
            ItemSlots.load(slots, contents.getList("items", Tag.TAG_COMPOUND));
        }
    }

    /** Fluide : un reservoir Forge. */
    public static final class Fluid extends ResourceStore {

        public final FluidTank tank;

        public Fluid(long capacity, Runnable changed) {
            super(Resource.FLUIDS);
            tank = new FluidTank((int) capacity) {
                @Override
                protected void onContentsChanged() {
                    changed.run();
                }
            };
        }

        @Override
        public Object handler() {
            return tank;
        }

        @Override
        public double amount() {
            return tank.getFluidAmount();
        }

        @Override
        public double fill() {
            return tank.getFluidAmount() / (double) tank.getCapacity();
        }

        @Nullable
        @Override
        public Object content() {
            return tank.isEmpty() ? null : tank.getFluid().getFluid();
        }

        @Override
        public Object sided(SideMode mode) {
            return Sided.fluids(tank, mode);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            Ejector.eject(level, pos, sides, Transfer.FLUIDS, (IFluidHandler) tank, max);
        }

        @Override
        public void topUp() {
            if (!tank.isEmpty() && tank.getFluidAmount() < tank.getCapacity()) {
                tank.getFluid().setAmount(tank.getCapacity());
            }
        }

        @Override
        public void save(CompoundTag contents) {
            if (!tank.isEmpty()) {
                contents.put("fluid", tank.writeToNBT(new CompoundTag()));
            }
        }

        @Override
        public void load(CompoundTag contents) {
            tank.readFromNBT(contents.getCompound("fluid"));
        }
    }

    /** Gaz : un reservoir de gaz du socle. */
    public static final class Gas extends ResourceStore {

        public final BasicGasTank tank;
        private final GasTankHandler handler;

        public Gas(long capacity, Runnable changed) {
            super(Resource.GASES);
            tank = BasicGasTank.of(capacity, changed::run);
            handler = new GasTankHandler(List.of(tank), Automation.EXTERNAL);
        }

        @Override
        public Object handler() {
            return handler;
        }

        @Override
        public double amount() {
            return tank.getStack().getAmount();
        }

        @Override
        public double fill() {
            return tank.getStack().getAmount() / (double) tank.getCapacity();
        }

        @Nullable
        @Override
        public Object content() {
            return tank.getStack().isEmpty() ? null : tank.getStack().getGas();
        }

        @Override
        public Object sided(SideMode mode) {
            return Sided.gases(handler, mode);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            Ejector.eject(level, pos, sides, Transfer.GASES, (IGasHandler) handler, max);
        }

        @Override
        public void topUp() {
            if (!tank.isEmpty() && tank.getStored() < tank.getCapacity()) {
                GasStack full = tank.getStack().copy();
                full.setAmount(tank.getCapacity());
                tank.setStack(full);
            }
        }

        @Override
        public void save(CompoundTag contents) {
            if (!tank.getStack().isEmpty()) {
                contents.put("gas", tank.serializeNBT());
            }
        }

        @Override
        public void load(CompoundTag contents) {
            if (contents.contains("gas")) {
                tank.deserializeNBT(contents.getCompound("gas"));
            } else {
                tank.setStack(GasStack.EMPTY);
            }
        }
    }

    /** Energie : une reserve de FE. */
    public static final class Energy extends ResourceStore {

        public final EnergyBuffer buffer;

        public Energy(long capacity, Runnable changed) {
            super(Resource.ENERGY);
            buffer = new EnergyBuffer(() -> capacity, Integer.MAX_VALUE, Integer.MAX_VALUE, changed::run);
        }

        @Override
        public Object handler() {
            return buffer;
        }

        @Override
        public double amount() {
            return buffer.getStored();
        }

        @Override
        public double fill() {
            return buffer.ratio();
        }

        @Override
        public Object sided(SideMode mode) {
            return Sided.energy(buffer, mode);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            Ejector.eject(level, pos, sides, Transfer.ENERGY, (IEnergyStorage) buffer, max);
        }

        @Override
        public void topUp() {
            buffer.setStored(buffer.getCapacity());
        }

        @Override
        public void save(CompoundTag contents) {
            if (buffer.getStored() > 0) {
                contents.put("energy", buffer.serializeNBT());
            }
        }

        @Override
        public void load(CompoundTag contents) {
            buffer.deserializeNBT(contents.getCompound("energy"));
        }
    }

    /** Chaleur : un corps thermique (pertes a l'air, echanges par les cables et les voisins). */
    public static final class Heat extends ResourceStore {

        public final HeatCapacitor body;

        public Heat(long capacity, double conductance, Runnable changed) {
            super(Resource.HEAT);
            body = new HeatCapacitor(capacity, conductance, changed::run);
        }

        @Override
        public Object handler() {
            return body;
        }

        @Override
        public double amount() {
            return body.getTemperature() * body.getHeatCapacity();
        }

        /** De l'ambiante (0) a 2 300 K (1). */
        @Override
        public double fill() {
            return Math.max(0, Math.min(1, (body.getTemperature() - HeatExchange.AMBIENT) / 2_000));
        }

        @Override
        public Object sided(SideMode mode) {
            return body;
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            //La chaleur passe par conduction (cables, voisins), jamais par ejection
        }

        /** Creatif : 1 500 K. */
        @Override
        public void topUp() {
            double missing = (1_500 - body.getTemperature()) * body.getHeatCapacity();
            if (Math.abs(missing) > 1) {
                body.addHeat(missing);
            }
        }

        @Override
        public void save(CompoundTag contents) {
            if (Math.abs(body.getTemperature() - HeatExchange.AMBIENT) > 0.5) {
                contents.put("heat", body.serializeNBT());
            }
        }

        @Override
        public void load(CompoundTag contents) {
            body.deserializeNBT(contents.getCompound("heat"));
        }
    }

    /** Air comprime : un volume sous pression. */
    public static final class Pressure extends ResourceStore {

        public final PressureVolume volume;

        public Pressure(long capacity, double maxPressure, double conductance, Runnable changed) {
            super(Resource.PRESSURE);
            volume = new PressureVolume(capacity, maxPressure, conductance, changed::run);
        }

        @Override
        public Object handler() {
            return volume;
        }

        @Override
        public double amount() {
            return volume.getAir();
        }

        @Override
        public double fill() {
            return volume.ratio();
        }

        @Override
        public Object sided(SideMode mode) {
            return Sided.pressure((IPressureHandler) volume, mode);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            Ejector.ejectAir(level, pos, sides, volume);
        }

        @Override
        public void topUp() {
            volume.insertAir(volume.getVolume() * volume.getMaxPressure() - volume.getAir(), false);
        }

        @Override
        public void save(CompoundTag contents) {
            if (volume.getAir() > 0) {
                contents.put("pressure", volume.serializeNBT());
            }
        }

        @Override
        public void load(CompoundTag contents) {
            volume.deserializeNBT(contents.getCompound("pressure"));
        }
    }

    /**
     * Lot 4 : donnees : un reservoir de donnees du socle ({@link BasicDataTank}, ou creatif : source infinie d'un type). Vue d'une face :
     * {@link IDataHandler} borne au sens de la face ; ejection par les faces en SORTIE dans la capacite de donnees du voisin (fiche du
     * cable logistique, machine a donnees), jamais a travers une frontiere de claim refusee. Sauvegarde : le reservoir tel quel
     * ({@link BasicDataTank#serializeNBT}, cle {@code data} sous {@code contents}).
     */
    public static final class Data extends ResourceStore {

        public final BasicDataTank tank;
        private final IDataHandler internal;

        public Data(BasicDataTank tank) {
            super(Resource.DATA);
            this.tank = tank;
            internal = new View(SideMode.IO, Automation.INTERNAL);
        }

        @Override
        public Object handler() {
            return internal;
        }

        @Override
        public double amount() {
            return tank.getStored();
        }

        @Override
        public double fill() {
            return tank.getCapacity() <= 0 ? 0 : Math.min(1, tank.getStored() / (double) tank.getCapacity());
        }

        @Nullable
        @Override
        public Object content() {
            return tank.isEmpty() ? null : tank.getType();
        }

        @Override
        public Object sided(SideMode mode) {
            return new View(mode, Automation.EXTERNAL);
        }

        @Override
        public void eject(Level level, BlockPos pos, SideConfig sides, long max) {
            for (Direction side : Directions.ALL) {
                if (tank.isEmpty()) {
                    return;
                }
                if (sides.mode(SideType.DATA, side) != SideMode.OUTPUT) {
                    continue;
                }
                BlockPos next = pos.relative(side);
                //1.9 (MAJ RP, bloc 4) : les donnees ne passent pas une frontiere de claim refusee
                if (!level.isLoaded(next) || !ClanApi.canTransfer(level, pos, next)) {
                    continue;
                }
                DataStack available = tank.extract(max, Action.SIMULATE, Automation.INTERNAL);
                if (available.isEmpty()) {
                    return;
                }
                //1.11 : un cable logistique expose la capacite de donnees sur sa fiche (le reseau distribue tout de suite)
                BlockEntity neighbor = level.getBlockEntity(next);
                IDataHandler handler = neighbor == null ? null : neighbor.getCapability(PioneersAPI.DATA_HANDLER, side.getOpposite()).resolve().orElse(null);
                if (handler != null) {
                    long sent = available.getAmount() - handler.insertData(available, Action.EXECUTE).getAmount();
                    if (sent > 0) {
                        tank.extract(sent, Action.EXECUTE, Automation.INTERNAL);
                    }
                }
            }
        }

        @Override
        public void topUp() {
            //Le reservoir creatif reste plein de lui-meme
        }

        @Override
        public void save(CompoundTag contents) {
            if (!tank.isEmpty()) {
                contents.put("data", tank.serializeNBT());
            }
        }

        @Override
        public void load(CompoundTag contents) {
            tank.deserializeNBT(contents.getCompound("data"));
        }

        /** Le reservoir vu par une face reglee sur {@code mode} (interne : dans les deux sens). */
        private final class View implements IDataHandler {

            private final SideMode mode;
            private final Automation automation;

            View(SideMode mode, Automation automation) {
                this.mode = mode;
                this.automation = automation;
            }

            @Override
            public int getDataTanks() {
                return 1;
            }

            @NotNull
            @Override
            public DataStack getDataInTank(int index) {
                return index == 0 ? tank.getStack() : DataStack.EMPTY;
            }

            @Override
            public long getDataTankCapacity(int index) {
                return index == 0 ? tank.getCapacity() : 0;
            }

            @Override
            public boolean isDataValid(int index, @NotNull DataStack stack) {
                return index == 0 && tank.isValid(stack);
            }

            @NotNull
            @Override
            public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
                if (stack.isEmpty() || !mode.canInsert()) {
                    return stack;
                }
                return tank.insert(stack, action, automation);
            }

            @NotNull
            @Override
            public DataStack extractData(long amount, @NotNull Action action) {
                if (amount <= 0 || !mode.canExtract()) {
                    return DataStack.EMPTY;
                }
                return tank.extract(amount, action, automation);
            }

            @NotNull
            @Override
            public DataStack extractData(@NotNull DataStack stack, @NotNull Action action) {
                if (stack.isEmpty() || !mode.canExtract() || !tank.getStack().isTypeEqual(stack)) {
                    return DataStack.EMPTY;
                }
                return tank.extract(stack.getAmount(), action, automation);
            }
        }
    }
}
