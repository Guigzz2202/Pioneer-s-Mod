package fr.pioneers.core.machine;

import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.menu.PiTileMenu;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.registries.PiTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Menu standard des blocs du socle : les cases de la machine, l'inventaire du joueur sous la zone de la machine, la configuration des faces
 * et les valeurs affichees (synchronisees a chaque tick par le menu vanilla, seulement quand elles changent).
 */
public class MachineMenu extends PiTileMenu<BlockEntity> {

    /** Hauteur de la bande de l'inventaire du joueur (titre compris) ; l'interface standard v2 en a une de 98, dans un cadre. */
    public static final int INVENTORY = 90;

    @Nullable
    private final MachineGui machine;
    private final long[] values;
    private int[] sides = new int[1];

    public MachineMenu(int id, Inventory inventory, BlockEntity tile) {
        super(PiTransport.MACHINE_MENU.get(), id, tile);
        machine = tile instanceof MachineGui gui ? gui : null;
        values = new long[machine == null ? 0 : machine.guiValueCount()];
        if (machine != null) {
            addTileSlots(machine.guiSlots());
            SideConfig config = machine.getSideConfig();
            if (config != null) {
                sides = new int[config.channelCount()];
                for (int i = 0; i < sides.length; i++) {
                    int channel = i;
                    trackInt(() -> config.pack(channel), packed -> sides[channel] = packed);
                }
            }
            for (int i = 0; i < values.length; i++) {
                int index = i;
                trackLong(() -> machine.guiValue(index), value -> values[index] = value);
            }
        }
        addPlayerInventory(inventory, 8 + (width() - 176) / 2, height() - 82);
    }

    public static MachineMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        BlockEntity tile = inventory.player.level().getBlockEntity(buffer.readBlockPos());
        if (tile != null) {
            return new MachineMenu(id, inventory, tile);
        }
        return new MachineMenu(id, inventory, new EmptyTile());
    }

    @Nullable
    public MachineGui machine() {
        return machine;
    }

    /** Valeur synchronisee (cote client : la derniere recue). */
    public long value(int index) {
        if (index < 0 || index >= values.length) {
            return 0;
        }
        return tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide && machine != null ? machine.guiValue(index) : values[index];
    }

    /** API Machine (lot 2) : valeur NOMMEE (indice attribue par la machine ; 0 si elle ne la declare pas). */
    public long value(UiValues.Key key) {
        return value(UiValues.indexOf(machine == null ? null : machine.uiValues(), key));
    }

    /** Configuration des faces recue du serveur (2 bits par face + ejection). */
    public int sides() {
        return sides[0];
    }

    /** Reglages recus pour le canal {@code channel} (une ressource). */
    public int sides(int channel) {
        return channel >= 0 && channel < sides.length ? sides[channel] : sides[0];
    }

    public int width() {
        return machine == null ? 176 : machine.guiWidth();
    }

    public int height() {
        return machine == null ? 40 + INVENTORY : machine.guiHeight() + machine.guiInventory();
    }

    /** Tile de secours quand le bloc a disparu entre l'ouverture et l'arrivee du paquet. */
    private static final class EmptyTile extends PiBlockEntity {
        EmptyTile() {
            super(BlockEntityType.CHEST, BlockPos.ZERO,
                  Blocks.CHEST.defaultBlockState());
        }
    }

    @Override
    protected boolean valid() {
        return machine != null;
    }

    @Override
    protected double reach() {
        return machine == null ? 8 : machine.guiReach();
    }
}
