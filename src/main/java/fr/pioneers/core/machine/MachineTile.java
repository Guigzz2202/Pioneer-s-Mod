package fr.pioneers.core.machine;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.machine.unit.UnitHost;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Automation;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.energy.EnergyPush;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasTankHandler;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.side.Fronted;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.side.Sided;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des machines du socle (sans Mekanism) : reserve d'energie FE, cases, reservoirs de gaz et de fluide, configuration des faces (une
 * capacite par face, limitee par son reglage), ejection, commande redstone, etat {@code active} sans clignotement, interface standard
 * ({@link MachineMenu} + {@code GuiMachine}) et synchronisation des reservoirs vers le client (rendus, interface).
 * <p>
 * Les sous-classes declarent leurs cases ({@link #slot}), reservoirs ({@link #gasTank}, {@link #fluidTank}) dans leur constructeur, et
 * travaillent dans {@link #tick()}.
 * <p>
 * Actions de l'interface : celles de {@link MachineGui} ; {@link #REDSTONE} fait tourner la commande redstone.
 * <p>
 * API Machine (lot 2) : la base recoit les UNITES de l'API ({@code api.machine.unit} : procede, puis generateur, stockage...) par
 * {@link #access()} (les operations protegees de la machine, pretees a ses unites), et les valeurs d'interface NOMMEES
 * ({@link #declareValues}, {@link UiValues}) ; {@link #kitFormat()} garde le format de sauvegarde des machines du kit d'avant le lot 2.
 */
public abstract class MachineTile extends PiBlockEntity implements MenuProvider, MachineGui, Fronted {

    public static final int REDSTONE = 7;

    protected final EnergyBuffer energy;
    protected final SideConfig sides;
    /** Face avant d'un bloc sans orientation dans son etat ({@link Fronted}). */
    private Direction front = Direction.NORTH;
    protected final Averager usage = new Averager();
    private final List<ItemSlot> slots = new ArrayList<>();
    private final List<BasicGasTank> gasTanks = new ArrayList<>();
    private final List<FluidTank> fluidTanks = new ArrayList<>();
    private RedstoneMode redstone = RedstoneMode.IGNORED;
    private boolean powered;
    /** Ticks depuis le dernier travail (l'etat actif s'eteint apres une seconde d'arret). */
    private int idle = 100;
    private boolean syncDirty;
    private int ticks;
    private long usedThisTick;

    @Nullable
    private UiValues named;
    @Nullable
    private Access access;

    private final Map<Capability<?>, Map<Direction, LazyOptional<?>>> sidedCaps = new HashMap<>();
    private final Map<Capability<?>, LazyOptional<?>> internalCaps = new HashMap<>();

    protected MachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state, long energyCapacity, int maxReceive) {
        this(type, pos, state, () -> energyCapacity, maxReceive, energyCapacity > 0);
    }

    /** Reserve d'energie de capacite variable (multiblocs : selon le tier et la taille formes). */
    protected MachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state, LongSupplier energyCapacity, int maxReceive,
          boolean hasEnergy) {
        super(type, pos, state);
        energy = new EnergyBuffer(energyCapacity, maxReceive, producesEnergy() ? maxReceive : 0, this::setChanged);
        sides = new SideConfig(allowedModes(), initialMode(), canEject(), this::facing, this::sidesChanged).ejects(ejectTypes());
        if (hasEnergy) {
            //1.0 : energie par toutes les faces (entree, ou sortie pour un generateur)
            sides.independent(SideType.ENERGY, producesEnergy() ? SideMode.OUTPUT : SideMode.INPUT);
        }
    }

    // ------------------------------------------------------------------
    // Declaration (constructeur des sous-classes)
    // ------------------------------------------------------------------

    protected ItemSlot slot(ItemSlot slot) {
        slots.add(slot);
        sides.addType(SideType.ITEMS);
        return slot;
    }

    protected BasicGasTank gasTank(BasicGasTank tank) {
        gasTanks.add(tank);
        sides.addType(SideType.GASES);
        return tank;
    }

    protected FluidTank fluidTank(FluidTank tank) {
        fluidTanks.add(tank);
        sides.addType(SideType.FLUIDS);
        return tank;
    }

    /** Case de batterie (decharge dans la reserve). */
    protected ItemSlot batterySlot(int x, int y) {
        return slot(ItemSlot.of(stack -> stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canExtract).orElse(false), this::setChanged, x, y).ghostIcon("ghost_battery"));
    }

    protected Set<SideMode> allowedModes() {
        return EnumSet.allOf(SideMode.class);
    }

    protected SideMode initialMode() {
        return SideMode.NONE;
    }

    /** Ressources que la machine sait ejecter (interrupteur actif dans le panneau des faces ; vide : toutes). */
    protected SideType[] ejectTypes() {
        return new SideType[0];
    }

    /** Ejection automatique proposee dans l'interface. */
    protected boolean canEject() {
        return false;
    }

    /** L'energie peut-elle sortir par les faces (machine productrice) ? */
    protected boolean producesEnergy() {
        return false;
    }

    /** Cases vues de l'exterieur (tuyaux, entonnoirs) : toutes par defaut. */
    protected List<ItemSlot> automationSlots() {
        return slots;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    protected abstract void tick();

    /**
     * API Machine (lot 8c) : UNITE DE LOGIQUE portee par la machine (celle que nomme sa fiche, {@code logic} de {@code tools/gc/api}) : son
     * tick suit celui de la tile, ses cles se sauvent et se relisent apres celles de la machine, ses valeurs nommees suivent les 4 communes
     * (memes indices qu'avant), ce qu'elle envoie au client suit les reservoirs. Une tile qui porte une unite n'a plus que la geometrie de
     * son interface, ses capacites et des acces pour le rendu, le bloc et les GameTests.
     */
    @Nullable
    private fr.pioneers.api.machine.unit.MachineUnit carried;

    /** Lot 8c : porte l'unite de logique {@code unit} (a appeler une fois, dans le constructeur) ; la renvoie. */
    protected final <U extends fr.pioneers.api.machine.unit.MachineUnit> U carry(U unit) {
        carried = unit;
        return unit;
    }

    /** Lot 8c : l'unite de logique portee, ou null. */
    @Nullable
    public final fr.pioneers.api.machine.unit.MachineUnit carried() {
        return carried;
    }

    /** 1.0.2-beta : reglage a valeur (curseur de l'interface standard) : confie a l'unite portee (la tile qui en a un autre le redefinit). */
    @Override
    public void handleSetting(net.minecraft.server.level.ServerPlayer player, int setting, int value) {
        if (carried != null) {
            carried.setting(setting, value);
        }
    }

    protected void clientTick() {
    }

    public void tickServer() {
        if (level == null) {
            return;
        }
        if (ticks++ == 0) {
            updatePower();
            if (getBlockState().getBlock() instanceof MachineBlock block) {
                block.repairBounds(level, worldPosition);
            }
        }
        tick();
        if (carried != null) {
            carried.tick();
        }
        usage.add(usedThisTick);
        usedThisTick = 0;
        if (syncDirty && ticks % 10 == 0) {
            syncDirty = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public final void tickClient() {
        clientTick();
    }

    /** Commande redstone respectee ? */
    protected boolean canWork() {
        return redstone.allows(powered);
    }

    void updatePower() {
        if (level != null) {
            powered = level.hasNeighborSignal(worldPosition);
        }
    }

    /**
     * 1.0.3-beta (lot 6, Guillaume : « bug de saccade pour les combustions ») : TEMPORISATION D'EXTINCTION de l'etat {@code active} (ticks
     * sans travail avant de passer a l'arret ; 20 par defaut). Les generateurs gardent leur feu plus longtemps ({@link #GENERATOR_HOLD}) :
     * entre deux combustibles, ou quand leur reserve se vide et se remplit par a-coups, le modele ne bascule plus marche / arret (chaque
     * bascule reconstruit le rendu du chunk : la saccade vue en jeu). Comme la temporisation des machines de Mekanism.
     */
    public int activeHold() {
        if (hold < 0) {
            //fiche de l'API Machine du bloc : un GENERATEUR (compact ou multibloc) garde son feu GENERATOR_HOLD ticks
            fr.pioneers.api.machine.MachineRegistry.Entry e = fr.pioneers.api.machine.MachineRegistry.of(getBlockState().getBlock());
            hold = e != null && e.spec().kind() == fr.pioneers.api.machine.MachineSpec.Kind.GENERATOR ? GENERATOR_HOLD : 20;
        }
        return hold;
    }

    private int hold = -1;

    public static final int GENERATOR_HOLD = 100;

    protected void markWorking(boolean working) {
        idle = working ? 0 : Math.min(Math.max(100, activeHold()), idle + 1);
        BlockState state = getBlockState();
        boolean active = idle < activeHold();
        if (level != null && state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS); //etat visuel : pas de neighborChanged (les observateurs le voient toujours) (audit 0.20.46)
        }
    }

    /** Renvoi immediat au client (choix fait dans l'interface : recette, destination...). */
    protected void syncNow() {
        syncDirty = false;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Les reservoirs ou l'etat visible ont change : renvoyes au client au plus toutes les 10 ticks. */
    protected void markSync() {
        syncDirty = true;
    }

    /** Consomme de l'energie si la reserve la contient entierement ; compte la consommation moyenne. */
    protected boolean use(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (energy.getStored() < amount) {
            return false;
        }
        energy.extract(amount, false);
        usedThisTick += amount;
        return true;
    }

    /** Decharge une batterie dans la reserve. */
    protected void discharge(ItemSlot battery, long maxPerTick) {
        if (battery.isEmpty() || energy.getNeeded() <= 0) {
            return;
        }
        battery.getStack().getCapability(ForgeCapabilities.ENERGY).ifPresent(item -> {
            int taken = item.extractEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(maxPerTick, energy.getNeeded())), false);
            energy.insert(taken, false);
        });
    }

    /** Charge l'objet d'une case depuis la reserve. */
    protected void charge(ItemSlot slot, long maxPerTick) {
        if (slot.isEmpty() || energy.getStored() <= 0) {
            return;
        }
        slot.getStack().getCapability(ForgeCapabilities.ENERGY).ifPresent(item -> {
            int given = item.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.min(maxPerTick, energy.getStored())), false);
            energy.extract(given, false);
        });
    }

    /** Pousse l'energie dans les voisins par les faces de sortie (OUTPUT, IO), au plus {@code budget} FE. */
    protected void pushEnergy(long budget) {
        if (energy.getStored() <= 0 || level == null) {
            return;
        }
        EnergyPush.toNeighbours(level, worldPosition, Math.min(budget, energy.getStored()),
              (direction, neighbor) -> sides.mode(SideType.ENERGY, direction).canExtract(), given -> energy.extract(given, false));
    }

    /** Etat « en marche » du bloc (lu dans l'etat, donc aussi cote client). */
    public boolean getActive() {
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : front;
    }

    /** 1.0 : bloc sans orientation dans son etat : face avant des reglages retenue a la pose (vers le joueur). */
    @Override
    public void setFront(Direction front) {
        if (front.getAxis().isHorizontal() && this.front != front) {
            this.front = front;
            sidesChanged();
        }
    }

    protected void sidesChanged() {
        setChanged();
        invalidateSided();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> guiSlots() {
        return slots;
    }

    @Override
    public int guiHeight() {
        return 86;
    }

    /** Machines sans tier : habillage « acier » (textures {@code *_steel}, a defaut celles du tier de base). */
    @Override
    public String guiTier() {
        //Id du bloc : textures a la couleur de la machine (gui_machine.py, machine_colors) ; a defaut, l'acier
        String path = ForgeRegistries.BLOCKS.getKey(getBlockState().getBlock()).getPath();
        //1.0 : machines en 4 tiers (<tier>_<id>) : habillage de la machine, quel que soit le tier
        return getBlockState().getBlock() instanceof TieredMachineBlock ? path.replaceFirst("^(basic|advanced|elite|ultimate)_", "") : path;
    }

    /** Tier de la machine (0 = MKI ... 3 = MKIV ; 0 pour une machine sans tier). */
    public int machineTier() {
        return TieredMachineBlock.tierOf(getBlockState());
    }

    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    @Override
    public void handleAction(ServerPlayer player, int action) {
        if (action == REDSTONE) {
            redstone = redstone.next();
            setChanged();
            return;
        }
        MachineGui.super.handleAction(player, action);
    }

    /** Valeurs communes de l'interface : 0 energie, 1 reserve, 2 consommation FE/t x100, 3 commande redstone ; les suivantes a la machine. */
    @Override
    public int guiValueCount() {
        UiValues ui = uiValues();
        return ui != null ? ui.size() : 4 + machineValueCount();
    }

    @Override
    public long guiValue(int index) {
        UiValues ui = uiValues();
        if (ui != null) {
            return ui.get(index);
        }
        return switch (index) {
            case 0 -> energy.getStored();
            case 1 -> energy.getCapacity();
            case 2 -> Math.round(usage.get() * 100);
            case 3 -> redstone.ordinal();
            default -> machineValue(index - 4);
        };
    }

    /**
     * API Machine (lot 2) : valeurs NOMMEES (les 4 communes, a leur place, puis celles de {@link #declareValues}) ; null pour une machine qui
     * garde ses indices ({@link #machineValueCount}, {@link #machineValue}).
     */
    @Nullable
    @Override
    public UiValues uiValues() {
        if (!namedValues()) {
            return null;
        }
        if (named == null) {
            UiValues.Builder b = UiValues.builder().add(UiValues.ENERGY, energy::getStored).add(UiValues.CAPACITY, energy::getCapacity)
                  .add(UiValues.USAGE, () -> Math.round(usage.get() * 100)).add(UiValues.REDSTONE, () -> redstone.ordinal());
            declareValues(b);
            named = b.build();
        }
        return named;
    }

    /** La machine declare-t-elle des valeurs nommees ({@link #declareValues}) ? Lot 8c : oui des qu'elle porte une unite. */
    protected boolean namedValues() {
        return carried != null;
    }

    /** Valeurs nommees propres a la machine (apres les 4 communes) ; lot 8c : celles de l'unite portee. */
    protected void declareValues(UiValues.Builder values) {
        if (carried != null) {
            carried.declareValues(values);
        }
    }

    /** Valeurs propres a la machine (indices 4 et suivants de l'interface). */
    protected int machineValueCount() {
        return 0;
    }

    protected long machineValue(int index) {
        return 0;
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return standardMenu(id, inventory);
    }

    public EnergyBuffer getEnergy() {
        return energy;
    }

    public List<BasicGasTank> getGasTanks() {
        return gasTanks;
    }

    public List<FluidTank> getFluidTanks() {
        return fluidTanks;
    }

    // ------------------------------------------------------------------
    // Capacites
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        boolean mekGas = MekanismCompat.LOADED && !gasTanks.isEmpty() && MekanismGasBridge.isMekanismGasCapability(cap);
        boolean handled = cap == ForgeCapabilities.ITEM_HANDLER && !automationSlots().isEmpty() || cap == ForgeCapabilities.ENERGY && energy.getCapacity() > 0
              || cap == PioneersAPI.GAS_HANDLER && !gasTanks.isEmpty() || cap == ForgeCapabilities.FLUID_HANDLER && !fluidTanks.isEmpty() || mekGas;
        if (!handled) {
            return super.getCapability(cap, side);
        }
        if (side == null) {
            return internalCaps.computeIfAbsent(cap, c -> {
                Object handler = handler(c, SideMode.IO, Automation.EXTERNAL);
                return LazyOptional.of(() -> handler);
            }).cast();
        }
        SideMode mode = sides.mode(typeOf(cap), side);
        if (mode == SideMode.NONE) {
            return LazyOptional.empty();
        }
        return sidedCaps.computeIfAbsent(cap, c -> new EnumMap<>(Direction.class)).computeIfAbsent(side, d -> {
            Object handler = handler(cap, mode, Automation.EXTERNAL);
            return LazyOptional.of(() -> handler);
        }).cast();
    }

    /** Ressource dont les faces reglent cette capacite. */
    private static SideType typeOf(Capability<?> cap) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return SideType.ITEMS;
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return SideType.ENERGY;
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return SideType.FLUIDS;
        }
        return SideType.GASES;
    }

    private Object handler(Capability<?> cap, SideMode mode, Automation automation) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return Sided.items(new ItemSlots(automationSlots(), automation), mode);
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return Sided.energy(energy, mode);
        }
        if (cap == PioneersAPI.GAS_HANDLER) {
            return Sided.gases(new GasTankHandler(gasTanks, automation), mode);
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return Sided.fluids(new FluidTanks(fluidTanks), mode);
        }
        return MekanismGasBridge.expose(gasTanks, mode);
    }

    private void invalidateSided() {
        sidedCaps.values().forEach(map -> map.values().forEach(LazyOptional::invalidate));
        sidedCaps.clear();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateSided();
        internalCaps.values().forEach(LazyOptional::invalidate);
        internalCaps.clear();
    }

    /**
     * Capacite vue par un bloc fantome de la machine ({@code offset} depuis le bloc principal) ; aucune par defaut (les grandes machines
     * ouvrent leurs ports ici).
     */
    public <T> LazyOptional<T> boundCapability(Capability<T> cap, @Nullable Direction side, Vec3i offset) {
        return LazyOptional.empty();
    }

    // ------------------------------------------------------------------
    // API Machine (lot 2) : operations pretees aux unites
    // ------------------------------------------------------------------

    /** Les operations protegees de la machine, pour ses unites ({@code api.machine.unit}) : une seule instance par machine. */
    public final Access access() {
        if (access == null) {
            access = new Access();
        }
        return access;
    }

    /**
     * Ce qu'une unite de l'API peut faire sur sa machine : declarer ses cases et reservoirs (a la construction), consommer, recharger,
     * pousser l'energie, marquer l'etat actif, demander un envoi au client. Seule {@link MachineTile} en fabrique.
     */
    public final class Access implements UnitHost {

        private Access() {
        }

        public MachineTile tile() {
            return MachineTile.this;
        }

        @Nullable
        public Level level() {
            return level;
        }

        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public BlockState state() {
            return getBlockState();
        }

        public EnergyBuffer energy() {
            return energy;
        }

        public SideConfig sides() {
            return sides;
        }

        public ItemSlot slot(ItemSlot slot) {
            return MachineTile.this.slot(slot);
        }

        public ItemSlot batterySlot(int x, int y) {
            return MachineTile.this.batterySlot(x, y);
        }

        public FluidTank fluidTank(FluidTank tank) {
            return MachineTile.this.fluidTank(tank);
        }

        public BasicGasTank gasTank(BasicGasTank tank) {
            return MachineTile.this.gasTank(tank);
        }

        public boolean canWork() {
            return MachineTile.this.canWork();
        }

        public boolean use(long amount) {
            return MachineTile.this.use(amount);
        }

        public void discharge(ItemSlot battery, long maxPerTick) {
            MachineTile.this.discharge(battery, maxPerTick);
        }

        public void pushEnergy(long budget) {
            MachineTile.this.pushEnergy(budget);
        }

        /** Lot 8c : recharge l'objet de la case (case de recharge d'un generateur). */
        public void charge(ItemSlot slot, long maxPerTick) {
            MachineTile.this.charge(slot, maxPerTick);
        }

        /** Lot 8c : tier de la machine (0 = MKI). */
        public int machineTier() {
            return MachineTile.this.machineTier();
        }

        public void markWorking(boolean working) {
            MachineTile.this.markWorking(working);
        }

        public void markSync() {
            MachineTile.this.markSync();
        }

        public void syncNow() {
            MachineTile.this.syncNow();
        }

        public void changed() {
            setChanged();
        }
    }

    /** Gaz interne de la machine (pour l'ejection). */
    protected IGasHandler internalGases() {
        return new GasTankHandler(gasTanks, Automation.INTERNAL);
    }

    /** Plusieurs reservoirs de fluide vus comme un seul gestionnaire. */
    private record FluidTanks(List<FluidTank> tanks) implements IFluidHandler {

        @Override
        public int getTanks() {
            return tanks.size();
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tanks.get(tank).getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tanks.get(tank).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tanks.get(tank).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = 0;
            FluidStack rest = resource.copy();
            for (FluidTank tank : tanks) {
                if (rest.isEmpty()) {
                    break;
                }
                int n = tank.fill(rest, action);
                filled += n;
                rest.shrink(n);
            }
            return filled;
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack out = tank.drain(resource, action);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (FluidTank tank : tanks) {
                FluidStack out = tank.drain(maxDrain, action);
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return FluidStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde, synchronisation
    // ------------------------------------------------------------------

    /**
     * API Machine (lot 2) : format de sauvegarde des machines du kit d'avant le lot 2 ({@code energy}, {@code config}, {@code items} ;
     * ni drapeau {@code pioneers} ni listes de reservoirs vides ; la commande redstone seulement si elle n'est pas celle par defaut) : les
     * machines du kit posees avant gardent leur sauvegarde, les nouvelles ecrivent la meme.
     */
    protected boolean kitFormat() {
        return false;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        boolean kit = kitFormat();
        if (!kit) {
            tag.putBoolean("pioneers", true);
        }
        tag.put("energy", energy.serializeNBT());
        tag.put("config", sides.save());
        if (front != Direction.NORTH) {
            tag.putString("front", front.getName());
        }
        tag.put("items", ItemSlots.save(slots));
        if (!kit || redstone != RedstoneMode.IGNORED) {
            tag.putInt("redstone", redstone.ordinal());
        }
        if (!kit || !gasTanks.isEmpty() || !fluidTanks.isEmpty()) {
            writeTanks(tag);
        }
        if (carried != null) {
            carried.save(tag);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.getBoolean("pioneers") || kitFormat()) {
            energy.deserializeNBT(tag.getCompound("energy"));
            sides.load(tag.getCompound("config"));
            Direction f = tag.contains("front") ? Direction.byName(tag.getString("front")) : null;
            front = f != null && f.getAxis().isHorizontal() ? f : Direction.NORTH;
            ItemSlots.load(slots, tag.getList("items", Tag.TAG_COMPOUND));
            redstone = RedstoneMode.byIndex(tag.getInt("redstone"));
            readTanks(tag);
        }
        if (carried != null) {
            carried.load(tag);
        }
    }

    private void writeTanks(CompoundTag tag) {
        ListTag gases = new ListTag();
        gasTanks.forEach(tank -> gases.add(tank.serializeNBT()));
        tag.put("gases", gases);
        ListTag fluids = new ListTag();
        fluidTanks.forEach(tank -> fluids.add(tank.writeToNBT(new CompoundTag())));
        tag.put("fluids", fluids);
    }

    private void readTanks(CompoundTag tag) {
        ListTag gases = tag.getList("gases", Tag.TAG_COMPOUND);
        for (int i = 0; i < gasTanks.size() && i < gases.size(); i++) {
            gasTanks.get(i).deserializeNBT(gases.getCompound(i));
        }
        ListTag fluids = tag.getList("fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidTanks.size() && i < fluids.size(); i++) {
            fluidTanks.get(i).readFromNBT(fluids.getCompound(i));
        }
    }

    /** Donnees envoyees au client (reservoirs, plus ce qu'ajoute la machine). */
    protected void writeClient(CompoundTag tag) {
        writeTanks(tag);
        if (carried != null) {
            carried.writeClient(tag);
        }
    }

    protected void readClient(CompoundTag tag) {
        readTanks(tag);
        if (carried != null) {
            carried.readClient(tag);
        }
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeClient(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readClient(tag);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            readClient(packet.getTag());
        }
    }
}
