package fr.pioneers.computing;

import fr.pioneers.core.menu.PiTileMenu;
import fr.pioneers.registries.PiComputing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Menu des blocs de l'informatique industrielle (0.17.0) : editeur de regles des automates, reglages des ecrans de supervision. Pas de
 * cases : tout passe par les actions de l'interface ({@code PacketTileAction}) et par la synchronisation des tiles.
 */
public class ComputingMenu extends PiTileMenu<BlockEntity> {

    public ComputingMenu(int id, Inventory inventory, @Nullable BlockEntity tile) {
        super(PiComputing.MENU.get(), id, tile);
    }

    public static ComputingMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new ComputingMenu(id, inventory, inventory.player.level().getBlockEntity(buffer.readBlockPos()));
    }

    @Override
    protected boolean valid() {
        return tile instanceof LogicTile || tile instanceof ScreenTile;
    }
}
