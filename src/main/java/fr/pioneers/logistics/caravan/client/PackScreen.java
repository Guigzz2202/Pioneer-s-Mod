package fr.pioneers.logistics.caravan.client;

import fr.pioneers.logistics.caravan.PackMenu;
import fr.pioneers.client.gui.core.PiMenuScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 1.0.4-beta (lot 10) : ecran des SACOCHES d'une bete batee ({@link PackMenu}) au style sombre du socle (comme les sacs du 1.0.3 lot 7) :
 * filet de cuir, rangees du bat (cases utilisables seulement), puis l'inventaire du joueur.
 */
public class PackScreen extends PiMenuScreen<PackMenu> {

    public static final int LEATHER = 0xFFB07A44;

    public PackScreen(PackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PackMenu.WIDTH, PackMenu.height(menu.count));
        inventoryLabelY = PackMenu.inventoryY(menu.count) - 11;
    }

    @Override
    protected void drawPanel(GuiGraphics g, int x, int y) {
        super.drawPanel(g, x, y);
        g.fill(x, y, x + imageWidth, y + 1, LEATHER);
        int rows = PackMenu.rows(menu.count), w = 9 * PackMenu.SLOT + 4;
        g.renderOutline(x + 5, y + PackMenu.TOP - 3, w, rows * PackMenu.SLOT + 4, LEATHER & 0x80FFFFFF);
    }
}
