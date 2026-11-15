package fr.pioneers.api.machine.unit;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.resource.Resource;
import fr.pioneers.api.machine.resource.ResourceStore;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Texts;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.core.machine.Averager;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.buffer.BufferType;
import fr.pioneers.transport.buffer.EnergyBufferBlock;
import fr.pioneers.transport.buffer.HeatBufferBlock;
import fr.pioneers.transport.buffer.PressureBufferBlock;
import fr.pioneers.transport.logistic.CableLevel;
import fr.pioneers.transport.logistic.ChannelKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 3) : UNITE DE STOCKAGE d'un buffer (genre BUFFER des fiches, decision 2 de Guillaume), sortie de l'ancienne
 * {@code BufferTile} qui ne fait plus que la porter. Une ressource ({@link Resource}) et son stockage ({@link ResourceStore}) selon la
 * famille du bloc ({@link BufferType}) : cases d'objets, reservoir de fluide, de gaz, reserve d'energie, corps thermique ou volume d'air.
 * <p>
 * Ce qu'elle fait (code repris tel quel de l'ancienne tile) : faces (une seule ressource ; chaleur : fermee ou les deux sens), vues des
 * faces reglees, ejection au debit du cable du tier, cases d'entree / sortie des recipients (seaux, capsules et reservoirs de gaz,
 * batteries), creatif toujours plein, debit net moyen, etat visible du bloc (chaleur, charge, pression), envoi des jauges au client,
 * comparateur, valeurs d'interface NOMMEES ({@link UiValues#STORED}..., memes indices qu'avant), sauvegarde sous {@code contents} (copie
 * dans l'objet par la table de butin), {@code config}, {@code front}, {@code io} : format d'avant le lot 3.
 */
public class StorageUnit {

    /** Ce que la tile prete a l'unite. */
    public interface Host {

        @Nullable
        Level level();

        BlockPos pos();

        BlockState state();

        /** Le contenu a change (sauvegarde). */
        void changed();

        boolean creative();
    }

    /** Cases d'entree (haut) et de sortie (bas) des recipients (lot 4 : places de {@link StandardLayout}). */
    public static final int IO_X = StandardLayout.IO_X, IN_Y = StandardLayout.IN_Y, OUT_Y = StandardLayout.OUT_Y;

    private final Host host;
    public final BufferType type;
    public final CableTier tier;
    public final ResourceStore store;
    private final SideConfig sides;
    private Direction front = Direction.NORTH;
    private final List<ItemSlot> io = new ArrayList<>();
    private final Map<Direction, LazyOptional<?>> sided = new EnumMap<>(Direction.class);
    private final LazyOptional<?> capability;
    private final Averager flow = new Averager();
    private double lastAmount = Double.NaN;
    private int ticks;
    /** 1.0.3-beta : buffer creatif (contenance de milliards, ejection sans limite propre). */
    private final boolean creative;
    /** Dernier etat envoye au client (jauges) : remplissage et contenu. */
    private double syncedFill = -1;
    @Nullable
    private Object syncedContent;
    /** Client : remplissage affiche (lisse) et tick de sa derniere mise a jour. */
    private double shownFill = -1;
    private long shownTick;

    public StorageUnit(Host host, BufferType type, CableTier tier, boolean creative) {
        this.host = host;
        this.type = type;
        this.tier = tier;
        this.creative = creative;
        int stackMultiplier = type.stackMultiplier(tier, creative);
        long capacity = type.capacity(tier, creative);
        store = switch (type) {
            case ITEM -> {
                //1.12.3 : chaque case garde plusieurs piles normales (BufferType.stackMultiplier)
                List<ItemSlot> slots = new ArrayList<>();
                for (int i = 0; i < capacity; i++) {
                    slots.add(ItemSlot.of(host::changed, StandardLayout.ITEMS_X + (i % 9) * 18, StandardLayout.ITEMS_Y + (i / 9) * 18).stacked(stackMultiplier));
                }
                yield new ResourceStore.Items(slots);
            }
            case FLUID -> new ResourceStore.Fluid(capacity, host::changed);
            case GAS -> new ResourceStore.Gas(capacity, host::changed);
            case PRESSURE -> new ResourceStore.Pressure(capacity, type.maxPressure(tier), type.pressureConductance(tier, creative), host::changed);
            case ENERGY -> new ResourceStore.Energy(capacity, host::changed);
            case HEAT -> new ResourceStore.Heat(capacity, type.heatConductance(tier, creative), host::changed);
        };
        Object own = store.handler();
        capability = LazyOptional.of(() -> own);
        Set<SideMode> allowed = type == BufferType.HEAT ? EnumSet.of(SideMode.NONE, SideMode.IO) : EnumSet.allOf(SideMode.class);
        sides = new SideConfig(allowed, SideMode.NONE, type != BufferType.HEAT, () -> front, this::sidesChanged);
        //Une seule ressource, montree dans le panneau des faces
        sides.addType(store.resource.side);
        if (type == BufferType.FLUID || type == BufferType.GAS || type == BufferType.ENERGY) {
            Predicate<ItemStack> accepts = switch (type) {
                case FLUID -> stack -> stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
                case GAS -> stack -> stack.getItem() instanceof GasCapsuleItem || GasAccess.find(stack) != null;
                default -> stack -> stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
            };
            ItemSlot in = new ItemSlot(() -> 64, accepts, (stack, automation) -> true, (stack, automation) -> true, host::changed, IO_X, IN_Y);
            //Lot 3 : l'aide de la case d'entree vide est declaree par la case (plus d'aiguillage par classe dans l'ecran)
            switch (type) {
                case FLUID -> in.ghost(Items.BUCKET);
                case GAS -> in.ghostIcon("ghost_gas_tank");
                default -> in.ghostIcon("ghost_battery");
            }
            io.add(in);
            //Sortie : le joueur et l'automatisation reprennent, seule la machine y depose
            io.add(new ItemSlot(() -> 64, stack -> true, (stack, automation) -> automation == Automation.INTERNAL, (stack, automation) -> true,
                  host::changed, IO_X, OUT_Y));
        }
    }

    public Resource resource() {
        return store.resource;
    }

    public SideConfig sides() {
        return sides;
    }

    public List<ItemSlot> ioSlots() {
        return io;
    }

    /** Cases du buffer d'objets (vide pour les autres familles). */
    public List<ItemSlot> slots() {
        return store instanceof ResourceStore.Items items ? items.slots : List.of();
    }

    @Nullable
    public FluidTank fluidTank() {
        return store instanceof ResourceStore.Fluid f ? f.tank : null;
    }

    @Nullable
    public fr.pioneers.core.gas.BasicGasTank gasTank() {
        return store instanceof ResourceStore.Gas g ? g.tank : null;
    }

    // ------------------------------------------------------------------
    // Faces
    // ------------------------------------------------------------------

    public Direction front() {
        return front;
    }

    public void setFront(Direction front) {
        if (front.getAxis().isHorizontal() && this.front != front) {
            this.front = front;
            sidesChanged();
        }
    }

    private void sidesChanged() {
        host.changed();
        sided.values().forEach(LazyOptional::invalidate);
        sided.clear();
        Level level = host.level();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(host.pos(), host.state().getBlock());
        }
    }

    /** Capacite de la ressource par une face (null : sans face), selon son reglage ; vide si la capacite n'est pas la sienne. */
    public <T> LazyOptional<T> capability(Capability<T> cap, @Nullable Direction side) {
        if (cap != store.resource.capability()) {
            return LazyOptional.empty();
        }
        if (side == null) {
            return capability.cast();
        }
        SideMode mode = sides.mode(side);
        if (mode == SideMode.NONE) {
            return LazyOptional.empty();
        }
        if (mode == SideMode.IO || type == BufferType.HEAT) {
            return capability.cast();
        }
        return sided.computeIfAbsent(side, face -> {
            Object view = store.sided(mode);
            return LazyOptional.of(() -> view);
        }).cast();
    }

    /** La capacite demandee est-elle celle de la ressource ? */
    public boolean handles(Capability<?> cap) {
        return cap == store.resource.capability();
    }

    public void invalidate() {
        capability.invalidate();
        sided.values().forEach(LazyOptional::invalidate);
        sided.clear();
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    /**
     * Tick serveur (pas pour les objets). Chaleur : pertes a l'air (les cables et les machines voisines font les echanges). Puis l'etat
     * visible : niveau de chaleur ou de charge (4 fois par seconde), jauges envoyees au client (1 fois par seconde au plus).
     */
    public void tick() {
        boolean creative = host.creative();
        if (store instanceof ResourceStore.Heat heat && !creative) {
            HeatExchange.ambient(heat.body, type.heatLoss(tier));
        }
        Level level = host.level();
        if (level == null) {
            return;
        }
        if (creative) {
            store.topUp();
        }
        ticks++;
        double amount = store.amount();
        if (!Double.isNaN(lastAmount)) {
            flow.add(amount - lastAmount);
        }
        if (ticks % 4 == 0) {
            processSlots();
        }
        if (creative && sides.isEject()) {
            //1.0.3-beta : buffer creatif : ejection a chaque tick, sans limite propre (le cable ou la machine voisine fixe le debit) ;
            //objets : une pile par face et par passe, 16 passes par tick
            for (int pass = 0; pass < (type == BufferType.ITEM ? 16 : 1); pass++) {
                store.eject(level, host.pos(), sides, Long.MAX_VALUE / 4);
            }
        } else if (ticks % 10 == 0 && sides.isEject()) {
            long max = (long) Math.max(1, CableLevel.byTier(tier).perTick(ChannelKind.valueOf(type.name())) * 10);
            store.eject(level, host.pos(), sides, max);
        }
        lastAmount = store.amount();
        BlockState state = host.state();
        if (ticks % 5 == 1) {
            BlockState updated = state;
            if (store instanceof ResourceStore.Heat heat && state.hasProperty(HeatBufferBlock.HEAT)) {
                updated = state.setValue(HeatBufferBlock.HEAT, HeatBufferBlock.level(state.getValue(HeatBufferBlock.HEAT), heat.body.getTemperature()));
            } else if (store instanceof ResourceStore.Pressure p && state.hasProperty(PressureBufferBlock.PRESSURE)) {
                updated = state.setValue(PressureBufferBlock.PRESSURE, PressureBufferBlock.level(p.volume.ratio()));
            } else if (store instanceof ResourceStore.Energy e && state.hasProperty(EnergyBufferBlock.CHARGE)) {
                updated = state.setValue(EnergyBufferBlock.CHARGE, EnergyBufferBlock.level(e.buffer.getStored(), e.buffer.getCapacity()));
            }
            if (updated != state) {
                //Meme bloc : la tile et son contenu restent en place ; etat visuel : pas de neighborChanged (audit 0.20.46)
                level.setBlock(host.pos(), updated, Block.UPDATE_CLIENTS);
            }
        }
        //Nature du contenu : envoyee tout de suite quand elle change ; quantite : au plus 4 fois par seconde
        if (syncsContents()) {
            double fill = store.fill();
            Object content = store.content();
            if (Objects.equals(content, syncedContent) && ticks % 5 != 1) {
                fill = syncedFill;
            }
            boolean changed = !Objects.equals(content, syncedContent) || Math.abs(fill - syncedFill) >= 1 / 64.0
                              || (fill <= 0) != (syncedFill <= 0) || (fill >= 1) != (syncedFill >= 1);
            if (changed) {
                syncedFill = fill;
                syncedContent = content;
                level.sendBlockUpdated(host.pos(), state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Fluide et gaz : le contenu part au client (jauges du rendu). */
    public boolean syncsContents() {
        return type == BufferType.FLUID || type == BufferType.GAS;
    }

    /** Cases d'entree / sortie : vider ou remplir un recipient, decharger ou charger une batterie. */
    private void processSlots() {
        if (io.size() < 2 || io.get(0).isEmpty()) {
            return;
        }
        ItemSlot in = io.get(0), out = io.get(1);
        ItemStack one = in.getStack().copyWithCount(1);
        if (store instanceof ResourceStore.Fluid f) {
            FluidTank fluid = f.tank;
            //D'abord vider le recipient dans le buffer, sinon le remplir depuis le buffer
            FluidActionResult result = FluidUtil.tryEmptyContainer(one, fluid, Integer.MAX_VALUE, null, false);
            boolean empty = result.isSuccess();
            if (!empty) {
                result = FluidUtil.tryFillContainer(one, fluid, Integer.MAX_VALUE, null, false);
            }
            if (!result.isSuccess() || !fits(out, result.getResult())) {
                return;
            }
            result = empty ? FluidUtil.tryEmptyContainer(one, fluid, Integer.MAX_VALUE, null, true)
                           : FluidUtil.tryFillContainer(one, fluid, Integer.MAX_VALUE, null, true);
            moveOut(in, out, result.getResult());
        } else if (store instanceof ResourceStore.Gas g) {
            var gas = g.tank;
            if (in.getStack().getItem() instanceof GasCapsuleItem capsule) {
                capsule(gas, in, out, capsule);
                return;
            }
            IGasHandler item = GasAccess.find(in.getStack());
            if (item == null) {
                return;
            }
            long rate = CableLevel.byTier(tier).rate(ChannelKind.GAS) * 4;
            GasStack offered = item.extractGas(rate, Action.SIMULATE);
            if (!offered.isEmpty() && gas.insert(offered, Action.SIMULATE, Automation.INTERNAL).getAmount() < offered.getAmount()) {
                GasStack rest = gas.insert(item.extractGas(rate, Action.EXECUTE), Action.EXECUTE, Automation.INTERNAL);
                if (!rest.isEmpty()) {
                    item.insertGas(rest, Action.EXECUTE);
                }
            } else if (!gas.getStack().isEmpty()) {
                GasStack moving = gas.extract(rate, Action.SIMULATE, Automation.INTERNAL);
                long accepted = moving.getAmount() - item.insertGas(moving, Action.SIMULATE).getAmount();
                if (accepted > 0) {
                    item.insertGas(gas.extract(accepted, Action.EXECUTE, Automation.INTERNAL), Action.EXECUTE);
                }
            }
            //Recipient plein ou vide : il passe en sortie
            item = GasAccess.find(in.getStack());
            boolean done = item == null || item.getGasInTank(0).isEmpty() || item.getGasInTank(0).getAmount() >= item.getGasTankCapacity(0);
            if (done && fits(out, in.getStack())) {
                moveOut(in, out, in.getStack().copy());
            }
        } else if (store instanceof ResourceStore.Energy e) {
            var energy = e.buffer;
            IEnergyStorage item = in.getStack().getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
            if (item == null) {
                return;
            }
            int rate = (int) Math.min(Integer.MAX_VALUE, CableLevel.byTier(tier).rate(ChannelKind.ENERGY) * 4);
            if (item.canExtract() && item.getEnergyStored() > 0) {
                int taken = item.extractEnergy((int) Math.min(rate, energy.getNeeded()), false);
                energy.insert(taken, false);
            } else if (item.canReceive()) {
                int given = item.receiveEnergy((int) Math.min(rate, energy.getStored()), false);
                energy.extract(given, false);
            }
            boolean done = item.getEnergyStored() <= 0 || item.getEnergyStored() >= item.getMaxEnergyStored();
            if (done && fits(out, in.getStack())) {
                moveOut(in, out, in.getStack().copy());
            }
        }
    }

    /** Capsule de gaz (tout ou rien, 1 B) : une pleine se vide dans le buffer, une vide s'y remplit ; elle passe ensuite en sortie. */
    private void capsule(fr.pioneers.core.gas.BasicGasTank gas, ItemSlot in, ItemSlot out, GasCapsuleItem capsule) {
        long size = GasCapsuleItem.CAPACITY;
        Gas held = GasCapsuleItem.getGas(in.getStack());
        ItemStack result;
        if (held != null) {
            GasStack moving = GasStack.of(held, size);
            if (!gas.insert(moving, Action.SIMULATE, Automation.INTERNAL).isEmpty()) {
                return;
            }
            result = new ItemStack(capsule);
            if (!fits(out, result)) {
                return;
            }
            gas.insert(moving, Action.EXECUTE, Automation.INTERNAL);
        } else {
            GasStack taken = gas.extract(size, Action.SIMULATE, Automation.INTERNAL);
            if (taken.getAmount() < size) {
                return;
            }
            result = GasCapsuleItem.filled(capsule, taken.getGas());
            if (!fits(out, result)) {
                return;
            }
            gas.extract(size, Action.EXECUTE, Automation.INTERNAL);
        }
        moveOut(in, out, result);
    }

    private static boolean fits(ItemSlot out, ItemStack result) {
        if (result.isEmpty() || out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameTags(out.getStack(), result) && out.getCount() + result.getCount() <= out.getLimit(result);
    }

    /** Le recipient traite quitte l'entree pour la sortie. */
    private static void moveOut(ItemSlot in, ItemSlot out, ItemStack result) {
        ItemStack rest = in.getStack().copy();
        rest.shrink(1);
        in.setStack(rest);
        if (!result.isEmpty()) {
            if (out.isEmpty()) {
                out.setStack(result.copy());
            } else {
                ItemStack merged = out.getStack().copy();
                merged.grow(result.getCount());
                out.setStack(merged);
            }
        }
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    /** Hauteur de la zone de la machine : interface standard v2 (lot 3) : la zone commune a jauge ; objets : leurs rangees. */
    public int guiHeight() {
        return type == BufferType.ITEM ? StandardLayout.itemBufferHeight(slots().size()) : StandardLayout.ZONE;
    }

    /** Lot 4 : elements de l'interface (controle des chevauchements du banc). */
    public List<StandardLayout.Part> guiParts() {
        if (type != BufferType.ITEM) {
            return StandardLayout.bufferParts(!io.isEmpty());
        }
        List<StandardLayout.Part> out = new ArrayList<>();
        for (ItemSlot slot : slots()) {
            out.add(new StandardLayout.Part("slot", StandardLayout.slotFrame(slot.x, slot.y)));
        }
        return out;
    }

    /** Valeurs NOMMEES (memes indices qu'avant le lot 3, aucune valeur commune : un buffer n'a pas d'energie propre). */
    public UiValues values() {
        UiValues.Builder b = UiValues.builder();
        switch (type) {
            case ITEM -> b.add(UiValues.SLOTS_USED, () -> ((ResourceStore.Items) store).used()).add(UiValues.SLOTS, () -> slots().size());
            case HEAT -> {
                var body = ((ResourceStore.Heat) store).body;
                b.add(UiValues.TEMPERATURE, () -> Math.round(body.getTemperature() * 100)).add(UiValues.HEAT_CAPACITY, () -> Math.round(body.getHeatCapacity()))
                      .add(UiValues.FLOW, () -> Math.round(flow.get() * 100));
            }
            case PRESSURE -> {
                var v = ((ResourceStore.Pressure) store).volume;
                b.add(UiValues.AIR, () -> Math.round(v.getAir() * 100)).add(UiValues.AIR_MAX, () -> Math.round(v.getMaxPressure() * v.getVolume() * 100))
                      .add(UiValues.FLOW, () -> Math.round(flow.get() * 100)).add(UiValues.PRESSURE, () -> Math.round(v.getPressure() * 1000))
                      .add(UiValues.PRESSURE_MAX, () -> Math.round(type.maxPressure(tier) * 1000));
            }
            default -> b.add(UiValues.STORED, () -> (long) store.amount()).add(UiValues.STORED_MAX, this::capacity)
                  .add(UiValues.FLOW, () -> Math.round(flow.get() * 100));
        }
        return b.build();
    }

    /** Contenance (cases, mB, J/K, FE ou litres), creatif compris. */
    public long capacity() {
        return type.capacity(tier, creative);
    }

    /** Debit d'ejection du tier (par tick, unite de la ressource) : celui du cable logistique du meme tier. */
    public double tierRate() {
        return CableLevel.byTier(tier).perTick(ChannelKind.valueOf(type.name()));
    }

    /** Remplissage (0..1). */
    public double fill() {
        return store.fill();
    }

    public int comparator() {
        double fill = fill();
        return fill <= 0 ? 0 : 1 + (int) Math.floor(fill * 14);
    }

    /**
     * Client : remplissage a afficher, qui rejoint le vrai remplissage en douceur (un cinquieme de l'ecart par tick). Un contenu non vide
     * reste visible.
     */
    public double shownFill(long gameTime) {
        double target = fill();
        if (shownFill < 0 || gameTime - shownTick > 100) {
            shownFill = target;
        } else {
            for (long t = shownTick; t < gameTime; t++) {
                shownFill += (target - shownFill) * 0.2;
            }
            if (Math.abs(target - shownFill) < 1e-4) {
                shownFill = target;
            }
        }
        shownTick = gameTime;
        return target <= 0 ? 0 : shownFill;
    }

    /** Etat lisible (clic droit). */
    public Component status() {
        return switch (type) {
            case ITEM -> Component.translatable("buffer.pioneers.status.item", ((ResourceStore.Items) store).used(), slots().size());
            case FLUID -> {
                FluidTank fluid = fluidTank();
                yield fluid == null || fluid.isEmpty() ? Component.translatable("buffer.pioneers.status.empty")
                      : Component.translatable("buffer.pioneers.status.fluid", fluid.getFluid().getDisplayName(), Texts.format(fluid.getFluidAmount()),
                            Texts.format(fluid.getCapacity()));
            }
            case GAS -> {
                var gas = gasTank();
                GasStack stack = gas == null ? GasStack.EMPTY : gas.getStack();
                yield stack.isEmpty() ? Component.translatable("buffer.pioneers.status.empty")
                      : Component.translatable("buffer.pioneers.status.fluid", stack.getGas().getName(), Texts.format(stack.getAmount()),
                            Texts.format(gas.getCapacity()));
            }
            case PRESSURE -> {
                var v = ((ResourceStore.Pressure) store).volume;
                yield Component.translatable("buffer.pioneers.status.pressure", String.format(Locale.ROOT, "%.2f", v.getPressure()),
                      String.format(Locale.ROOT, "%.0f", type.maxPressure(tier)), Texts.format((long) v.getAir()));
            }
            case ENERGY -> Component.translatable("buffer.pioneers.status.energy", Texts.format((long) store.amount()), Texts.format(capacity()));
            case HEAT -> Component.translatable("buffer.pioneers.status.heat",
                  String.format(Locale.ROOT, "%.0f", ((ResourceStore.Heat) store).body.getTemperature()));
        };
    }

    // ------------------------------------------------------------------
    // Sauvegarde (le contenu suit l'objet)
    // ------------------------------------------------------------------

    public void save(CompoundTag tag) {
        CompoundTag contents = new CompoundTag();
        store.save(contents);
        if (!contents.isEmpty()) {
            tag.put(fr.pioneers.transport.buffer.BufferTile.NBT_CONTENTS, contents);
        }
        tag.put("config", sides.save());
        if (front != Direction.NORTH) {
            tag.putString("front", front.getName());
        }
        if (io.stream().anyMatch(slot -> !slot.isEmpty())) {
            tag.put("io", ItemSlots.save(io));
        }
    }

    public void load(CompoundTag tag) {
        CompoundTag contents = tag.getCompound(fr.pioneers.transport.buffer.BufferTile.NBT_CONTENTS);
        sides.load(tag.getCompound("config"));
        Direction f = tag.contains("front") ? Direction.byName(tag.getString("front")) : null;
        front = f != null && f.getAxis().isHorizontal() ? f : Direction.NORTH;
        ItemSlots.load(io, tag.getList("io", Tag.TAG_COMPOUND));
        store.load(contents);
    }
}
