package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.menu.PiTileMenu;
import fr.pioneers.logistics.robotics.PiRobotics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

/**
 * Menu du hub robotique (socle) : les cases du hub (unites, ameliorations, module, tampon), l'inventaire du joueur, et l'etat (genre,
 * statut, unites dehors, energie).
 */
public class RoboticHubMenu extends PiTileMenu<TileEntityRoboticCasing> {

    public static final int HEIGHT = 166 + RoboticHubData.INVENTORY_OFFSET;

    private long energy, capacity, used;
    private int kind, status, active, outMask;

    /** Serveur. */
    public RoboticHubMenu(int id, Inventory inventory, TileEntityRoboticCasing master) {
        super(PiRobotics.HUB_MENU.get(), id, master);
        RoboticHubData hub = master.ownHub();
        addTileSlots(hub.getSlots());
        addPlayerInventory(inventory, 8, HEIGHT - 82);
        trackInt(hub::kindIndex, value -> kind = value);
        trackInt(hub::statusIndex, value -> status = value);
        trackInt(hub::getActive, value -> active = value);
        trackInt(hub::outMask, value -> outMask = value);
        trackLong(hub.energyContainer::getStored, value -> energy = value);
        trackLong(hub.energyContainer::getCapacity, value -> capacity = value);
        trackLong(hub::getLastEnergyUsed, value -> used = value);
    }

    /** Client. */
    public static RoboticHubMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        TileEntityRoboticCasing tile = inventory.player.level().getBlockEntity(buffer.readBlockPos()) instanceof TileEntityRoboticCasing casing ? casing : null;
        return new RoboticHubMenu(id, inventory, tile, true);
    }

    private RoboticHubMenu(int id, Inventory inventory, @Nullable TileEntityRoboticCasing tile, boolean client) {
        super(PiRobotics.HUB_MENU.get(), id, tile);
        //Cote client : les cases pointent vers une copie locale du hub (le contenu arrive par la synchronisation des cases)
        RoboticHubData hub = tile == null ? new RoboticHubData(null) : tile.ownHub();
        clientHub = hub;
        addTileSlots(hub.getSlots());
        addPlayerInventory(inventory, 8, HEIGHT - 82);
        trackInt(() -> 0, value -> {
            kind = value;
            hub.restoreKind(value);
        });
        trackInt(() -> 0, value -> status = value);
        trackInt(() -> 0, value -> active = value);
        trackInt(() -> 0, value -> outMask = value);
        trackLong(() -> 0, value -> energy = value);
        trackLong(() -> 0, value -> capacity = value);
        trackLong(() -> 0, value -> used = value);
    }

    @Nullable
    private RoboticHubData clientHub;

    /** Le hub (serveur : le vrai ; client : la copie locale). */
    public RoboticHubData hub() {
        return clientHub != null ? clientHub : tile.ownHub();
    }

    @Override
    protected boolean valid() {
        return tile != null && tile.getHub() != null;
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

    public int kind() {
        return kind;
    }

    public RoboticHubData.Status status() {
        return RoboticHubData.Status.values()[Math.max(0, Math.min(RoboticHubData.Status.values().length - 1, status))];
    }

    public int active() {
        return active;
    }

    public boolean isOut(int slot) {
        return (outMask >> slot & 1) != 0;
    }
}
