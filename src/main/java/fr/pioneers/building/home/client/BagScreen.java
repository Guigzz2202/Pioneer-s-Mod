package fr.pioneers.building.home.client;

import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.building.home.bag.BagMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : ecran d'un sac ({@link BagMenu}) au style sombre du socle : rangees du sac (8 colonnes pour les sacs a dos, 9 pour
 * les sacs de rangement), puis l'inventaire du joueur ; la case qui tient le sac est voilee (verrouillee).
 */
public class BagScreen extends PiMenuScreen<BagMenu> {

    /** Cuir du filet d'accent (sac a dos simple, tanne ; sacs de rangement : toile). */
    private static final int[] ACCENTS = {0xFFB07A44, 0xFF7A4A28, 0xFFB8A070, 0xFF8AA060, 0xFF6A9AB0};

    public BagScreen(BagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BagMenu.WIDTH, BagMenu.height(menu.kind));
        inventoryLabelY = menu.inventoryTop() - 11;
    }

    @Override
    protected void drawPanel(GuiGraphics g, int x, int y) {
        super.drawPanel(g, x, y);
        g.fill(x, y, x + imageWidth, y + 1, ACCENTS[menu.kind.ordinal()]);
        int top = y + BagMenu.TOP - 3, rows = menu.kind.rows(), w = menu.kind.columns * BagMenu.SLOT + 4;
        int x0 = x + (imageWidth - w) / 2;
        g.renderOutline(x0, top, w, rows * BagMenu.SLOT + 4, ACCENTS[menu.kind.ordinal()] & 0x80FFFFFF);
    }

    @Override
    protected void drawSlot(GuiGraphics g, Slot slot, int x, int y) {
        super.drawSlot(g, slot, x, y);
        if (slot.index == menu.lockedMenuSlot() - menu.kind.slots - 27 && slot.container == minecraft.player.getInventory()) {
            g.fill(x, y, x + 16, y + 16, 0x90000000);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
    }
}
