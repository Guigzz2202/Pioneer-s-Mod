package fr.pioneers.logistics.transport.pressure;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Directions;
import fr.pioneers.core.Texts;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.Averager;
import fr.pioneers.core.machine.Ejector;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.pressure.PressureVolume;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.core.side.Sided;
import fr.pioneers.core.registries.PiTransport;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Compresseur d'air : {@value #ENERGY_PER_TICK} FE par tick pour {@code RATE} litres d'air par tick, dans un volume de {@code VOLUME} litres.
 * Le joueur regle la pression demandee (le compresseur s'arrete en l'atteignant, plafond {@code MAX_PRESSURE} bar). Faces : une entree
 * recoit l'energie, une sortie laisse partir l'air (clapet) ; avec l'ejection, l'air est pousse dans les voisins par les sorties.
 */
public class AirCompressorTile extends PiBlockEntity implements MenuProvider, MachineGui {

    public static final int ENERGY_PER_TICK = 400;
    public static final double RATE = 40, VOLUME = 500, MAX_PRESSURE = 10, CONDUCTANCE = 100;
    /** Chaleur de compression rendue aux voisins (J par litre comprime) : la turbine de detente en demande autant pour son meilleur rendement. */
    public static final double HEAT_PER_LITER = 25;
    private static final long ENERGY_CAPACITY = 20_000;
    /** Pression demandee, en dixiemes de bar (pas de 0,5 bar). */
    public static final int TARGET_STEP = 5, MIN_TARGET = 5;
    public static final int TARGET_DOWN = CUSTOM, TARGET_UP = CUSTOM + 1;

    private final EnergyBuffer energy = new EnergyBuffer(() -> ENERGY_CAPACITY, ENERGY_PER_TICK * 4, 0, this::setChanged);
    private final PressureVolume air = new PressureVolume(VOLUME, MAX_PRESSURE, CONDUCTANCE, this::setChanged);
    private final LazyOptional<EnergyBuffer> energyCap = LazyOptional.of(() -> energy);
    private final Map<Direction, LazyOptional<?>> airCaps = new EnumMap<>(Direction.class);
    private final LazyOptional<PressureVolume> airCap = LazyOptional.of(() -> air);
    private final SideConfig sides = new SideConfig(EnumSet.allOf(SideMode.class), SideMode.NONE, true, this::facing, this::sidesChanged).addType(SideType.ENERGY).addType(SideType.PRESSURE)
          .ejects(SideType.PRESSURE);
    private final ItemSlot battery = ItemSlot.of(stack -> stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canExtract).orElse(false),
          this::setChanged, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y).ghostIcon("ghost_battery");
    private int target = (int) (MAX_PRESSURE * 10);
    private final Averager used = new Averager(), produced = new Averager(), delivered = new Averager();
    private double lastAir = Double.NaN;
    /** Ticks depuis le dernier travail (l'etat actif s'eteint apres une seconde d'arret, sans clignoter). */
    private int idle = 100;
    private int ticks;

    public AirCompressorTile(BlockPos pos, BlockState state) {
        super(PiTransport.AIR_COMPRESSOR_TILE.get(), pos, state);
    }

    private Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(AirCompressorBlock.FACING) ? state.getValue(AirCompressorBlock.FACING) : Direction.NORTH;
    }

    private void sidesChanged() {
        setChanged();
        airCaps.values().forEach(LazyOptional::invalidate);
        airCaps.clear();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void tickServer() {
        if (level == null) {
            return;
        }
        ticks++;
        //Air parti vers les voisins depuis la fin du tick precedent (les tuyaux le tirent eux-memes)
        if (!Double.isNaN(lastAir)) {
            delivered.add(Math.max(0, lastAir - air.getAir()));
        }
        dischargeBattery();
        double made = 0;
        long spent = 0;
        double wanted = target / 10.0;
        if (energy.getStored() >= ENERGY_PER_TICK && air.getPressure() < wanted) {
            //Pres de la pression demandee, le compresseur ne fait que l'appoint (et consomme d'autant moins)
            double needed = Math.max(0, (wanted - air.getPressure()) * VOLUME);
            made = air.insertAir(Math.min(RATE, needed), false);
            spent = (long) Math.ceil(ENERGY_PER_TICK * made / RATE);
            energy.extract(spent, false);
        }
        if (made > 0) {
            releaseHeat(made * HEAT_PER_LITER);
        }
        used.add(spent);
        produced.add(made);
        if (ticks % 10 == 0) {
            double before = air.getAir();
            Ejector.ejectAir(level, worldPosition, sides, air);
            delivered.add(Math.max(0, before - air.getAir()) / 10);
        }
        lastAir = air.getAir();
        idle = made > 0 ? 0 : Math.min(100, idle + 1);
        BlockState state = getBlockState();
        boolean active = idle < 20;
        if (state.hasProperty(AirCompressorBlock.ACTIVE) && state.getValue(AirCompressorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(AirCompressorBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    /**
     * Chaleur de compression : comprimer chauffe l'air ; cette chaleur passe aux voisins qui l'acceptent (cables et buffers de chaleur,
     * turbine de detente...), a parts egales, sinon elle se perd dans l'air.
     */
    private void releaseHeat(double joules) {
        if (level == null) {
            return;
        }
        List<IHeatHandler> sinks = new ArrayList<>(6);
        for (Direction side : Directions.ALL) {
            //1.9 (MAJ RP, bloc 4) : la chaleur ne passe pas une frontiere de claim refusee
            BlockEntity neighbor = ClanApi.canTransfer(level, worldPosition, worldPosition.relative(side)) ? level.getBlockEntity(worldPosition.relative(side)) : null;
            if (neighbor != null) {
                neighbor.getCapability(PioneersAPI.HEAT_HANDLER, side.getOpposite()).ifPresent(sinks::add);
            }
        }
        for (IHeatHandler sink : sinks) {
            sink.addHeat(joules / sinks.size());
        }
    }

    private void dischargeBattery() {
        if (battery.isEmpty() || energy.getNeeded() <= 0) {
            return;
        }
        battery.getStack().getCapability(ForgeCapabilities.ENERGY).ifPresent(item -> {
            int taken = item.extractEnergy((int) Math.min(ENERGY_PER_TICK * 4L, energy.getNeeded()), false);
            energy.insert(taken, false);
        });
    }

    int comparator() {
        double ratio = air.ratio();
        return ratio <= 0 ? 0 : 1 + (int) Math.floor(ratio * 14);
    }

    Component status() {
        return Component.translatable("block.pioneers.air_compressor.status", String.format(Locale.ROOT, "%.2f", air.getPressure()),
              String.format(Locale.ROOT, "%.0f", MAX_PRESSURE), Texts.format(energy.getStored()), Texts.format(ENERGY_CAPACITY));
    }

    public PressureVolume getAir() {
        return air;
    }

    public ItemSlot getBattery() {
        return battery;
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    @Override
    public List<ItemSlot> guiSlots() {
        return List.of(battery);
    }

    /** API Machine (lot 3) : interface standard v2 (zone de 130, inventaire dans le cadre). */
    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.regulatorParts(true);
    }

    @Nullable
    private UiValues values;

    /** API Machine (lot 3) : valeurs NOMMEES (memes indices qu'avant : voir {@link #guiValue}). */
    @Override
    public UiValues uiValues() {
        if (values == null) {
            UiValues.Builder b = UiValues.builder().add(UiValues.ENERGY, () -> guiValue(0)).add(UiValues.CAPACITY, () -> guiValue(1))
                  .add(UiValues.USAGE, () -> guiValue(2));
            b.add(UiValues.PRESSURE, () -> guiValue(3)).add(UiValues.TARGET_PRESSURE, () -> guiValue(4)).add(UiValues.PRODUCED, () -> guiValue(5))
                  .add(UiValues.DELIVERED, () -> guiValue(6)).add(UiValues.PRESSURE_MAX, () -> guiValue(7)).add(UiValues.AIR, () -> guiValue(8));
            values = b.build();
        }
        return values;
    }

    /**
     * 0 energie, 1 reserve, 2 consommation FE/t x100, 3 pression x1000, 4 pression demandee x1000, 5 air produit L/t x100, 6 air livre L/t x100,
     * 7 pression maximale x1000, 8 air contenu x100.
     */
    @Override
    public int guiValueCount() {
        return 9;
    }

    @Override
    public long guiValue(int index) {
        return switch (index) {
            case 0 -> energy.getStored();
            case 1 -> ENERGY_CAPACITY;
            case 2 -> Math.round(used.get() * 100);
            case 3 -> Math.round(air.getPressure() * 1000);
            case 4 -> target * 100L;
            case 5 -> Math.round(produced.get() * 100);
            case 6 -> Math.round(delivered.get() * 100);
            case 7 -> Math.round(MAX_PRESSURE * 1000);
            default -> Math.round(air.getAir() * 100);
        };
    }

    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case TARGET_DOWN -> target = Mth.clamp(target - TARGET_STEP, MIN_TARGET, (int) (MAX_PRESSURE * 10));
            case TARGET_UP -> target = Mth.clamp(target + TARGET_STEP, MIN_TARGET, (int) (MAX_PRESSURE * 10));
            default -> {
                return;
            }
        }
        setChanged();
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

    // ------------------------------------------------------------------
    // Capacites (selon les faces) et sauvegarde
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        SideMode mode = sides.mode(cap == ForgeCapabilities.ENERGY ? SideType.ENERGY : SideType.PRESSURE, side);
        if (cap == ForgeCapabilities.ENERGY) {
            return mode.canInsert() ? energyCap.cast() : LazyOptional.empty();
        }
        if (cap == PioneersAPI.PRESSURE_HANDLER) {
            if (side == null) {
                return airCap.cast();
            }
            //L'air ne fait que sortir (clapet) par une sortie ; une face IO laisse passer dans les deux sens ; une entree seule ne donne pas d'air
            if (!mode.canExtract()) {
                return LazyOptional.empty();
            }
            if (mode == SideMode.IO) {
                return airCap.cast();
            }
            return airCaps.computeIfAbsent(side, face -> LazyOptional.of(() -> Sided.pressure(air, SideMode.OUTPUT))).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        airCap.invalidate();
        airCaps.values().forEach(LazyOptional::invalidate);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("energy", energy.serializeNBT());
        tag.put("air", air.serializeNBT());
        tag.put("config", sides.save());
        tag.put("battery", battery.save());
        tag.putInt("target", target);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        energy.deserializeNBT(tag.getCompound("energy"));
        air.deserializeNBT(tag.getCompound("air"));
        sides.load(tag.getCompound("config"));
        battery.load(tag.getCompound("battery"));
        target = tag.contains("target") ? tag.getInt("target") : (int) (MAX_PRESSURE * 10);
    }
}
