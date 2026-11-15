package fr.pioneers.farm.aquarium;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 25) : interface de COMPOSITION d'un bassin d'aquarium (Maj + clic droit, {@code AquariumEvents}) : aucune case propre,
 * seulement l'inventaire du joueur (synchronise : l'ecran compte les objets disponibles). Les poses et retraits passent par
 * {@link PacketAquarium} ({@link AquariumCompose#edit}, valide au serveur). Cotes communes avec l'ecran {@code J:client/farm/aquarium/
 * AquariumScreen} et sa maquette {@code tools/aquarium/aquarium_gui.py}.
 */
public class AquariumMenu extends AbstractContainerMenu {

    /** Panneau : largeur, zone de la composition, hauteur ; inventaire. */
    public static final int W = 248, ZONE = 148, H = ZONE + 108, INV_X = 44, INV_Y = ZONE + 26, HOTBAR_Y = ZONE + 84;
    /** Grille du fond (cadre), palette. */
    public static final int GRID_X = 8, GRID_Y = 30, GRID_W = 128, GRID_H = 102, PAL_X = 142, PAL_Y = 30, PAL_W = 98, CELL = 18, COLS = 5;

    private final BlockPos pos;

    public AquariumMenu(int id, Inventory inv, BlockPos pos) {
        super(PiAquarium.MENU.get(), id);
        this.pos = pos;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
        }
    }

    /** Client : position du bassin lue dans le tampon d'ouverture. */
    public static AquariumMenu client(int id, Inventory inv, FriendlyByteBuf buf) {
        return new AquariumMenu(id, inv, buf.readBlockPos());
    }

    /** Aquarium ouvert (n'importe quelle case du bassin). */
    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return p.level().getBlockState(pos).getBlock() instanceof AquariumBlock && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }
}
