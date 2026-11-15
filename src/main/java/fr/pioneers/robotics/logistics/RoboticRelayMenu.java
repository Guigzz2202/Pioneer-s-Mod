package fr.pioneers.robotics.logistics;

import fr.pioneers.core.menu.PiTileMenu;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.RoboticUpgrade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/** Menu du relais robotique (socle) : deux cartes, une batterie, l'energie et l'etat de la recharge. */
public class RoboticRelayMenu extends PiTileMenu<TileEntityRoboticRelay> {

    private long energy, capacity, used;
    private int coverage, charging;

    public RoboticRelayMenu(int id, Inventory inventory, TileEntityRoboticRelay relay) {
        super(PiRobotics.RELAY_MENU.get(), id, relay);
        addTileSlots(relay.getSlots());
        addPlayerInventory(inventory, 8, 84);
        trackLong(relay.getEnergyContainer()::getStored, value -> energy = value);
        trackLong(relay.getEnergyContainer()::getCapacity, value -> capacity = value);
        trackLong(relay::getLastEnergyUsed, value -> used = value);
        trackInt(relay::getCoverage, value -> coverage = value);
        trackInt(relay::getCharging, value -> charging = value);
    }

    public static RoboticRelayMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        var tile = inventory.player.level().getBlockEntity(buffer.readBlockPos());
        return new RoboticRelayMenu(id, inventory, tile instanceof TileEntityRoboticRelay relay ? relay : null, true);
    }

    private RoboticRelayMenu(int id, Inventory inventory, @Nullable TileEntityRoboticRelay relay, boolean client) {
        super(PiRobotics.RELAY_MENU.get(), id, relay);
        if (relay != null) {
            addTileSlots(relay.getSlots());
        }
        addPlayerInventory(inventory, 8, 84);
        trackLong(() -> 0, value -> energy = value);
        trackLong(() -> 0, value -> capacity = value);
        trackLong(() -> 0, value -> used = value);
        trackInt(() -> 0, value -> coverage = value);
        trackInt(() -> 0, value -> charging = value);
    }

    public long energy() {
        return energy;
    }

    public long capacity() {
        return capacity;
    }

    public long used() {
        return used;
    }

    public int coverage() {
        return coverage;
    }

    public int charging() {
        return charging;
    }

    /** Cartes d'une amelioration (d'apres les cases synchronisees). */
    public int cards(RoboticUpgrade upgrade) {
        return tile == null ? 0 : tile.cards(upgrade);
    }
}
