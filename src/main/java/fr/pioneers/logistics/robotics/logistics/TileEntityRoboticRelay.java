package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.core.Automation;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.logistics.robotics.ItemRoboticUpgrade;
import fr.pioneers.logistics.robotics.RobotUnitEntity;
import fr.pioneers.logistics.robotics.RoboticUpgrade;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Relais robotique (le roboport de Factorio, socle sans Mekanism) : il etend le reseau robotique (carre de 24 blocs autour de lui, plus avec
 * des cartes de portee), recharge les unites (4 places en vol pour les drones, 4 au sol), en FE. Deux cartes et une case de batterie.
 */
public class TileEntityRoboticRelay extends PiBlockEntity implements NetworkNode, MenuProvider {

    public static final int BASE_COVERAGE = 24, COVERAGE_PER_CARD = 8;
    public static final long ENERGY_STORAGE_FE = 4_000_000, IDLE_FE = 5, CHARGE_FE = 2_000;
    /** Positions des cases dans l'interface. */
    public static final int UPGRADES_X = 112, UPGRADES_Y = 57, BATTERY_X = 140, BATTERY_Y = 35;

    private final EnergyBuffer energyContainer = EnergyBuffer.input(ENERGY_STORAGE_FE, Integer.MAX_VALUE, this::setChanged);
    private final List<ItemSlot> upgradeSlots = new ArrayList<>();
    private final ItemSlot energySlot;
    private final List<ItemSlot> slots = new ArrayList<>();
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyContainer);
    private final LazyOptional<ItemSlots> itemCap;

    private int coverage = BASE_COVERAGE;
    private int charging;
    private long lastEnergyUsed;
    private final ChargePads pads = new ChargePads();
    private long usedThisTick;

    public TileEntityRoboticRelay(BlockPos pos, BlockState state) {
        super(PiRobotics.RELAY_TILE.get(), pos, state);
        for (int i = 0; i < 2; i++) {
            ItemSlot slot = ItemSlot.of(stack -> stack.getItem() instanceof ItemRoboticUpgrade upgrade && !upgrade.upgrade.forHub, this::setChanged,
                  UPGRADES_X + i * 18, UPGRADES_Y);
            upgradeSlots.add(slot);
            slots.add(slot);
        }
        energySlot = new ItemSlot(() -> 1, stack -> stack.getCapability(ForgeCapabilities.ENERGY).isPresent(), (stack, type) -> true, (stack, type) -> true,
              this::setChanged, BATTERY_X, BATTERY_Y);
        slots.add(energySlot);
        ItemSlots handler = new ItemSlots(List.of(energySlot), Automation.EXTERNAL);
        itemCap = LazyOptional.of(() -> handler);
    }

    public List<ItemSlot> getSlots() {
        return slots;
    }

    /** Cartes d'une amelioration installees (bornees). */
    public int cards(RoboticUpgrade upgrade) {
        int count = 0;
        for (ItemSlot slot : upgradeSlots) {
            if (slot.getStack().getItem() instanceof ItemRoboticUpgrade card && card.upgrade == upgrade) {
                count += slot.getCount();
            }
        }
        return Math.min(upgrade.max, count);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        chargeFromItem();
        coverage = BASE_COVERAGE + COVERAGE_PER_CARD * cards(RoboticUpgrade.RANGE);
        long upkeep = Math.round(IDLE_FE * (1 + 0.5 * cards(RoboticUpgrade.RANGE)));
        boolean powered = energyContainer.getStored() >= upkeep;
        //Le relais etend toujours le reseau ; sans energie, il ne recharge simplement plus
        LogisticsRegistry.get(server).registerNode(this, server.getGameTime());
        if (powered) {
            energyContainer.extract(upkeep, false);
        }
        if (server.getGameTime() % 20 == 0) {
            pads.cleanup(server);
        }
        charging = pads.size();
        lastEnergyUsed = upkeep + usedThisTick;
        usedThisTick = 0;
        setActive(powered && charging > 0);
    }

    /** Batterie posee dans la case : elle remplit le relais. */
    private void chargeFromItem() {
        ItemStack stack = energySlot.getStack();
        if (stack.isEmpty() || energyContainer.getNeeded() <= 0) {
            return;
        }
        stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(item -> {
            int wanted = (int) Math.min(Integer.MAX_VALUE, energyContainer.getNeeded());
            int taken = item.extractEnergy(wanted, false);
            if (taken > 0) {
                energyContainer.insert(taken, false);
            }
        });
    }

    private void setActive(boolean active) {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockRoboticRelay.ACTIVE) && state.getValue(BlockRoboticRelay.ACTIVE) != active && level != null) {
            level.setBlock(worldPosition, state.setValue(BlockRoboticRelay.ACTIVE, active), 3);
        }
    }

    public Direction getDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockRoboticRelay.FACING) ? state.getValue(BlockRoboticRelay.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // Reseau et recharge
    // ------------------------------------------------------------------

    @Override
    public BlockPos nodeCenter() {
        return worldPosition;
    }

    @Override
    public int coverage() {
        return coverage;
    }

    @Override
    public boolean nodeValid() {
        return !isRemoved();
    }

    /** Places : 4 en vol au-dessus (drones), 4 au sol autour (robots, rovers). */
    private List<Vec3> spots(boolean flying) {
        List<Vec3> list = new ArrayList<>();
        Vec3 c = Vec3.atBottomCenterOf(worldPosition);
        if (flying) {
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                list.add(c.add(Math.cos(a) * 0.9, 1.35 + (i % 2) * 0.25, Math.sin(a) * 0.9));
            }
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                list.add(Vec3.atBottomCenterOf(worldPosition.relative(dir)));
            }
        }
        return list;
    }

    @Nullable
    @Override
    public Vec3 chargeSpot(RobotUnitEntity unit) {
        boolean flying = unit.getKind().flying;
        return pads.reserve(unit, spots(flying), flying ? 0 : 4);
    }

    @Override
    public long charge(RobotUnitEntity unit, long wanted) {
        int boost = cards(RoboticUpgrade.CHARGING);
        //Recharge rapide : une batterie pleine en environ 3 s (moins avec l'amelioration)
        long rate = Math.round(Math.max(CHARGE_FE, unit.getMaxBattery() / 60) * (1 + 0.6 * boost));
        double loss = 1.05 + 0.1 * boost;
        long give = Math.min(wanted, rate);
        long cost = Math.round(give * loss);
        long available = energyContainer.getStored();
        if (available < cost) {
            give = (long) (available / loss * 0.25);
            cost = Math.round(give * loss);
        }
        if (give <= 0) {
            return 0;
        }
        energyContainer.extract(cost, false);
        usedThisTick += cost;
        return give;
    }

    @Override
    public void leave(RobotUnitEntity unit) {
        pads.leave(unit);
    }

    @Override
    public float crowd() {
        return pads.size() / 8F;
    }

    @Override
    public Vec3 idleSpot(RobotUnitEntity unit) {
        double a = (unit.getId() * 0.618034 % 1) * Mth.TWO_PI;
        Vec3 c = Vec3.atBottomCenterOf(worldPosition);
        double r = unit.getKind().flying ? 2.2 : 3.2;
        return c.add(Math.cos(a) * r, unit.getKind().flying ? 3.2 : 0, Math.sin(a) * r);
    }

    // ------------------------------------------------------------------
    // Interface et capacites
    // ------------------------------------------------------------------

    public EnergyBuffer getEnergyContainer() {
        return energyContainer;
    }

    public long getLastEnergyUsed() {
        return lastEnergyUsed;
    }

    public int getCoverage() {
        return coverage;
    }

    public int getCharging() {
        return charging;
    }

    @NotNull
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.pioneers.robotic_relay");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player player) {
        return new RoboticRelayMenu(id, inventory, this);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        } else if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", ItemSlots.save(slots));
        tag.putLong("energy", energyContainer.getStored());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            ItemSlots.load(slots, tag.getList("inventory", Tag.TAG_COMPOUND));
            energyContainer.setStored(tag.getLong("energy"));
        }
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1, 2, 1);
    }
}
