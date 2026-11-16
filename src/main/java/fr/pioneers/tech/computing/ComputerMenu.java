package fr.pioneers.tech.computing;

import fr.pioneers.core.menu.PiTileMenu;
import fr.pioneers.tech.computing.PiComputing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Menu du terminal de l'ordinateur (0.17.1) : pas de cases, tout passe par les paquets et la synchronisation de la tile. */
public class ComputerMenu extends PiTileMenu<BlockEntity> {

    public ComputerMenu(int id, Inventory inventory, @Nullable BlockEntity tile) {
        super(PiComputing.COMPUTER_MENU.get(), id, tile);
    }

    public static ComputerMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new ComputerMenu(id, inventory, inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    @Override
    protected boolean valid() {
        return tile instanceof ComputerTile;
    }
}
