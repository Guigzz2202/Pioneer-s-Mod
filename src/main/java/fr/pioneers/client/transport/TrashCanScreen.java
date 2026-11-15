package fr.pioneers.client.transport;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.GuiSprites;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.transport.trash.TrashCanMenu;
import fr.pioneers.transport.trash.TrashCanTile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 22) : ecran de la POUBELLE, au dessin des interfaces standard v2 (panneau d'acier {@code panel_steel} en bandes, cases
 * {@code slot_steel}, inventaire dans son cadre) : deux cases (« Jeter », « Vider ») separees par la grille du broyeur, la phrase « par
 * toutes les faces » et les quatre ressources acceptees (lot 27 : plus la chaleur ni l'air) en pastilles a la couleur de leur canal de cable.
 * Maquette : {@code tools/trash/trash_preview.py} (memes cotes, lues ici : {@link TrashCanMenu}).
 */
public class TrashCanScreen extends PiMenuScreen<TrashCanMenu> {

    /** Couleurs des canaux (celles des cables et des buffers, {@code T:core/transport_assets.py FAMILIES}), dans l'ordre de {@link TrashCanTile#RESOURCES}. */
    public static final int[] COLORS = {0xFFB07A3E, 0xFF3F76E4, 0xFFE0E6EE, 0xFF3FB6C4};
    public static final int LABEL_Y = 20, LINE_Y = 55, CHIPS_Y = 62, CHIP_H = 8, GRATE_X = 76, GRATE_W = 44;

    public TrashCanScreen(TrashCanMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TrashCanMenu.W, TrashCanMenu.H);
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelY = -1;
    }

    @Override
    protected void drawPanel(GuiGraphics g, int x, int y) {
        if (!GuiSprites.bands(g, "panel_steel", x, y, imageWidth, StandardLayout.PANEL_TEX, StandardLayout.PANEL_TEX, 8, 8,
              StandardLayout.panelBands(TrashCanMenu.ZONE, imageHeight))) {
            super.drawPanel(g, x, y);
        }
        StandardLayout.Rect f = StandardLayout.inventoryFrame(imageWidth, TrashCanMenu.ZONE);
        int fx0 = x + f.x(), fy0 = y + f.y(), fx1 = fx0 + f.w(), fy1 = fy0 + f.h();
        g.fill(fx0, fy0, fx1, fy1, 0xFF0E141A);
        g.fill(fx0, fy0, fx1, fy0 + 1, 0xFF06090C);
        g.fill(fx0, fy1 - 1, fx1, fy1, 0xFF34404C);
        g.fill(fx1 - 1, fy0, fx1, fy1, 0xFF34404C);
    }

    @Override
    protected void drawSlot(GuiGraphics g, Slot slot, int x, int y) {
        String name = slot.index <= TrashCanMenu.DRAIN ? "slot_steel" : "slot";
        if (!GuiSprites.draw(g, name, x - 1, y - 1, 18, 18, 18, 18)) {
            super.drawSlot(g, slot, x, y);
        }
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        //Grille du broyeur entre les deux cases : cadre d'acier, fosse sombre, barreaux, dents des deux rouleaux entre les barreaux
        int gx = GRATE_X, gy = TrashCanMenu.SLOT_Y - 1, gw = GRATE_W, gh = 18;
        g.fill(gx, gy, gx + gw, gy + gh, 0xFF14181E);
        g.fill(gx + 1, gy + 1, gx + gw - 1, gy + gh - 1, 0xFF07090C);
        for (int k = 0; k < 6; k++) {
            int tx = gx + 4 + k * 7;
            g.fill(tx, gy + 7, tx + 3, gy + 9, 0xFF3A4250);
            g.fill(tx + 1, gy + 9, tx + 2, gy + 11, 0xFF55606F);
        }
        for (int k = 0; k <= 5; k++) {
            int bx = gx + 2 + k * 8;
            g.fill(bx, gy + 1, bx + 2, gy + gh - 1, 0xFF55606F);
            g.fill(bx, gy + 1, bx + 1, gy + gh - 1, 0xFF7C8796);
        }
        g.fill(gx, gy, gx + gw, gy + 1, 0xFF7C8796);
        g.fill(gx, gy + gh - 1, gx + gw, gy + gh, 0xFF1F242C);
    }

    @Override
    protected void drawForeground(GuiGraphics g, int mouseX, int mouseY) {
        //Libelle de l'inventaire dans son cadre (comme GuiMachine)
        GuiDraw.text(g, font, playerInventoryTitle, TrashCanMenu.INV_X, TrashCanMenu.ZONE + StandardLayout.INV_LABEL, 0.8F, DIM, 80);
        GuiDraw.center(g, font, Component.translatable("gui.pioneers.trash_can.throw"), TrashCanMenu.TRASH_X + 8, LABEL_Y, 0.62F, DIM, 60);
        GuiDraw.center(g, font, Component.translatable("gui.pioneers.trash_can.drain"), TrashCanMenu.DRAIN_X + 8, LABEL_Y, 0.62F, DIM, 60);
        GuiDraw.center(g, font, Component.translatable("gui.pioneers.trash_can.faces"), imageWidth / 2F, LINE_Y, 0.58F, TEXT, imageWidth - 16);
        int n = TrashCanTile.RESOURCES.length, w = (imageWidth - 16) / n;
        for (int i = 0; i < n; i++) {
            int x = 8 + i * w;
            g.fill(x + 1, CHIPS_Y, x + w - 1, CHIPS_Y + CHIP_H, 0xFF101419);
            g.fill(x + 2, CHIPS_Y + 2, x + 6, CHIPS_Y + 6, COLORS[i]);
            g.fill(x + 2, CHIPS_Y + 5, x + 6, CHIPS_Y + 6, 0xFF000000 | GuiDraw.darken(COLORS[i], 0.6F));
            GuiDraw.text(g, font, Component.translatable("gui.pioneers.trash_can.res." + TrashCanTile.RESOURCES[i]), x + 8, CHIPS_Y + 2.2F, 0.5F, TEXT,
                  w - 10);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<Component> tip = null;
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index <= TrashCanMenu.DRAIN && !hoveredSlot.hasItem()) {
            String k = hoveredSlot.index == TrashCanMenu.TRASH ? "throw" : "drain";
            tip = List.of(Component.translatable("gui.pioneers.trash_can." + k), Component.translatable("gui.pioneers.trash_can." + k + ".tip")
                  .withStyle(ChatFormatting.GRAY));
        } else if (my >= LINE_Y - 1 && my < CHIPS_Y + CHIP_H && mx >= 8 && mx < imageWidth - 8) {
            tip = new ArrayList<>();
            tip.add(Component.translatable("gui.pioneers.trash_can.faces.tip"));
            tip.add(Component.translatable("gui.pioneers.trash_can.no_data").withStyle(ChatFormatting.RED));
            tip.add(Component.translatable("gui.pioneers.trash_can.no_heat_air").withStyle(ChatFormatting.GRAY));
        } else if (mx >= GRATE_X && mx < GRATE_X + GRATE_W && my >= TrashCanMenu.SLOT_Y - 1 && my < TrashCanMenu.SLOT_Y + 17) {
            tip = List.of(Component.translatable("gui.pioneers.trash_can.grate.tip"));
        }
        if (tip != null && menu.getCarried().isEmpty() && (hoveredSlot == null || !hoveredSlot.hasItem())) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }
}
