package fr.pioneers.logistics.transport.heat;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.Texts;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.heat.HeatCapacitor;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.Averager;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.logistics.transport.PiTransport;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Radiateur electrique : jusqu'a {@value #ENERGY_PER_TICK} FE par tick changes en autant de joules, dans un corps de {@code CAPACITY} J/K.
 * Le joueur regle la puissance (FE/t) et la temperature visee (le radiateur s'arrete en l'atteignant, plafond {@code MAX_TEMPERATURE} K).
 * Faces : une entree recoit l'energie, une sortie laisse partir la chaleur (les cables thermiques et les buffers la tirent eux-memes).
 * Une case de batterie se decharge dans la reserve.
 */
public class ElectricHeaterTile extends PiBlockEntity implements MenuProvider, MachineGui {

    public static final int ENERGY_PER_TICK = 400, POWER_STEP = 25, TARGET_STEP = 50, MIN_TARGET = 350;
    public static final double CAPACITY = 1_000, CONDUCTANCE = 40, MAX_TEMPERATURE = 1_500, LOSS = 0.002;
    private static final long ENERGY_CAPACITY = 20_000;
    /** Actions de l'interface. */
    public static final int POWER_DOWN = CUSTOM, POWER_UP = CUSTOM + 1, TARGET_DOWN = CUSTOM + 2, TARGET_UP = CUSTOM + 3;

    private final EnergyBuffer energy = new EnergyBuffer(() -> ENERGY_CAPACITY, ENERGY_PER_TICK * 4, 0, this::setChanged);
    private final HeatCapacitor heat = new HeatCapacitor(CAPACITY, CONDUCTANCE, this::setChanged);
    private final LazyOptional<EnergyBuffer> energyCap = LazyOptional.of(() -> energy);
    private final LazyOptional<HeatCapacitor> heatCap = LazyOptional.of(() -> heat);
    private final SideConfig sides = new SideConfig(EnumSet.allOf(SideMode.class), SideMode.NONE, false, this::facing, this::sidesChanged).addType(SideType.ENERGY).addType(SideType.HEAT);
    private final ItemSlot battery = ItemSlot.of(stack -> stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canExtract).orElse(false),
          this::setChanged, StandardLayout.BATTERY_X, StandardLayout.BATTERY_Y).ghostIcon("ghost_battery");
    private int power = ENERGY_PER_TICK, target = (int) MAX_TEMPERATURE;
    private final Averager used = new Averager(), produced = new Averager(), delivered = new Averager();
    private double lastTemperature = Double.NaN;
    /** Ticks depuis le dernier travail (l'etat actif s'eteint apres une seconde d'arret, sans clignoter). */
    private int idle = 100;

    public ElectricHeaterTile(BlockPos pos, BlockState state) {
        super(PiTransport.ELECTRIC_HEATER_TILE.get(), pos, state);
    }

    private Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(ElectricHeaterBlock.FACING) ? state.getValue(ElectricHeaterBlock.FACING) : Direction.NORTH;
    }

    private void sidesChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public void tickServer() {
        if (level == null) {
            return;
        }
        //Chaleur partie vers les voisins depuis la fin du tick precedent (ils la tirent eux-memes)
        if (!Double.isNaN(lastTemperature)) {
            delivered.add(Math.max(0, (lastTemperature - heat.getTemperature()) * CAPACITY));
        }
        dischargeBattery();
        long made = 0;
        double ceiling = Math.min(MAX_TEMPERATURE, target);
        double room = (ceiling - heat.getTemperature()) * CAPACITY;
        if (room > 0 && energy.getStored() > 0 && power > 0) {
            //Pres de la temperature visee, le radiateur ne fait que l'appoint (et consomme d'autant moins)
            made = energy.extract((long) Math.min(power, Math.ceil(room)), false);
            heat.addHeat(made);
        }
        used.add(made);
        produced.add(made);
        HeatExchange.ambient(heat, LOSS);
        lastTemperature = heat.getTemperature();
        idle = made > 0 ? 0 : Math.min(100, idle + 1);
        BlockState state = getBlockState();
        boolean active = idle < 20;
        if (state.hasProperty(ElectricHeaterBlock.ACTIVE) && state.getValue(ElectricHeaterBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(ElectricHeaterBlock.ACTIVE, active), Block.UPDATE_CLIENTS); //etat visuel : pas de neighborChanged (les observateurs le voient toujours) (audit 0.20.46)
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
        double ratio = (heat.getTemperature() - HeatExchange.AMBIENT) / (MAX_TEMPERATURE - HeatExchange.AMBIENT);
        return ratio <= 0 ? 0 : 1 + (int) Math.floor(Math.min(1, ratio) * 14);
    }

    Component status() {
        return Component.translatable("block.pioneers.electric_heater.status", String.format(Locale.ROOT, "%.0f", heat.getTemperature()),
              Texts.format(energy.getStored()), Texts.format(ENERGY_CAPACITY));
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
        return StandardLayout.regulatorParts(false);
    }

    @Nullable
    private UiValues values;

    /** API Machine (lot 3) : valeurs NOMMEES (memes indices qu'avant : voir {@link #guiValue}). */
    @Override
    public UiValues uiValues() {
        if (values == null) {
            UiValues.Builder b = UiValues.builder().add(UiValues.ENERGY, () -> guiValue(0)).add(UiValues.CAPACITY, () -> guiValue(1))
                  .add(UiValues.USAGE, () -> guiValue(2));
            b.add(UiValues.TEMPERATURE, () -> guiValue(3)).add(UiValues.POWER_SETTING, () -> guiValue(4)).add(UiValues.TARGET, () -> guiValue(5))
                  .add(UiValues.DELIVERED, () -> guiValue(6)).add(UiValues.PRODUCED, () -> guiValue(7)).add(UiValues.MAX_TEMPERATURE, () -> guiValue(8));
            values = b.build();
        }
        return values;
    }

    /**
     * 0 energie, 1 reserve, 2 consommation FE/t x100, 3 temperature x100, 4 puissance reglee (FE/t), 5 temperature visee (K),
     * 6 chaleur livree J/t x100, 7 chaleur produite J/t x100, 8 temperature maximale (K).
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
            case 3 -> Math.round(heat.getTemperature() * 100);
            case 4 -> power;
            case 5 -> target;
            case 6 -> Math.round(delivered.get() * 100);
            case 7 -> Math.round(produced.get() * 100);
            default -> (long) MAX_TEMPERATURE;
        };
    }

    @Override
    public SideConfig getSideConfig() {
        return sides;
    }

    @Override
    public void handleCustom(ServerPlayer player, int action) {
        switch (action) {
            case POWER_DOWN -> power = Mth.clamp(power - POWER_STEP, 0, ENERGY_PER_TICK);
            case POWER_UP -> power = Mth.clamp(power + POWER_STEP, 0, ENERGY_PER_TICK);
            case TARGET_DOWN -> target = Mth.clamp(target - TARGET_STEP, MIN_TARGET, (int) MAX_TEMPERATURE);
            case TARGET_UP -> target = Mth.clamp(target + TARGET_STEP, MIN_TARGET, (int) MAX_TEMPERATURE);
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
        SideMode mode = sides.mode(cap == ForgeCapabilities.ENERGY ? SideType.ENERGY : SideType.HEAT, side);
        if (cap == ForgeCapabilities.ENERGY) {
            return mode.canInsert() ? energyCap.cast() : LazyOptional.empty();
        }
        if (cap == PioneersAPI.HEAT_HANDLER) {
            return mode.canExtract() ? heatCap.cast() : LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        heatCap.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("energy", energy.serializeNBT());
        tag.put("heat", heat.serializeNBT());
        tag.put("config", sides.save());
        tag.put("battery", battery.save());
        tag.putInt("power", power);
        tag.putInt("target", target);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        energy.deserializeNBT(tag.getCompound("energy"));
        heat.deserializeNBT(tag.getCompound("heat"));
        sides.load(tag.getCompound("config"));
        battery.load(tag.getCompound("battery"));
        power = tag.contains("power") ? tag.getInt("power") : ENERGY_PER_TICK;
        target = tag.contains("target") ? tag.getInt("target") : (int) MAX_TEMPERATURE;
    }
}
