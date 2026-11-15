package fr.pioneers.client.caravan;

import fr.pioneers.caravan.GeneratedCaravan;
import fr.pioneers.caravan.PacketCaravanRoute;
import fr.pioneers.caravan.RouteMenu;
import fr.pioneers.caravan.RouteSheetItem;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.network.PiNetwork;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 1.0.4-beta (lot 10) : ecran de la FEUILLE DE ROUTE (style sombre du socle) : etapes numerotees (nom du relais, longueur de l'etape, en rouge
 * au-dela de {@code MAX_LEG}), boutons monter / descendre / retirer, boucle ou aller-retour, tout effacer ; 8 rangees visibles (molette).
 * Geometrie recopiee par la maquette {@code scratchpad/p104/lot10/tools/gui_mock10.py}.
 */
public class RouteScreen extends PiMenuScreen<RouteMenu> {

    public static final int W = 230, H = 178, LIST_Y = 22, ROW = 14, ROWS = 8, MODE_X = 150, MODE_W = 72, BTN = 12, UP_X = 176, DOWN_X = 190,
          DEL_X = 204, LEG_X = 124, NAME_X = 22, FOOT_Y = LIST_Y + ROWS * ROW + 4, CLEAR_Y = FOOT_Y + 22;
    private int scroll;

    public RouteScreen(RouteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        inventoryLabelY = -1;
    }

    private void send(int action, int index) {
        PiNetwork.sendToServer(new PacketCaravanRoute(menu.hand, action, index));
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        RouteSheetItem.Route r = RouteSheetItem.Route.of(menu.sheet());
        int n = r.stops().size();
        scroll = Math.max(0, Math.min(Math.max(0, n - ROWS), scroll));
        button(g, mouseX, mouseY, MODE_X, 4, MODE_W, BTN, Component.translatable(r.loop() ? "gui.pioneers.route.loop" : "gui.pioneers.route.back_forth"),
              true, () -> send(PacketCaravanRoute.MODE, 0));
        if (over(mouseX, mouseY, MODE_X, 4, MODE_W, BTN)) {
            tooltip(List.of(Component.translatable("gui.pioneers.route.mode.tip")));
        }
        screen(g, 6, LIST_Y - 2, W - 12, ROWS * ROW + 3);
        if (n == 0) {
            GuiDraw.center(g, font, Component.translatable("gui.pioneers.route.empty"), W / 2F, LIST_Y + ROWS * ROW / 2F - 4, 0.8F, DIM, W - 20);
        }
        int total = 0;
        for (int i = 0; i < n; i++) {
            int leg = r.leg(i);
            total += Math.max(0, leg);
        }
        for (int k = 0; k < ROWS && scroll + k < n; k++) {
            int i = scroll + k, y = LIST_Y + k * ROW;
            if (k % 2 == 1) {
                g.fill(7, y - 1, W - 7, y + ROW - 1, 0xFF111820);
            }
            GuiDraw.text(g, font, Component.literal((i + 1) + "."), 9, y + 3, 0.8F, ACCENT, 12);
            GuiDraw.text(g, font, Component.literal(r.names().get(i)), NAME_X, y + 3, 0.8F, TEXT, LEG_X - NAME_X - 4);
            int leg = r.leg(i);
            if (leg >= 0) {
                boolean bad = leg > GeneratedCaravan.MAX_LEG;
                GuiDraw.text(g, font, bad ? Component.translatable("gui.pioneers.route.too_far") : Component.translatable("gui.pioneers.route.leg", leg),
                      LEG_X, y + 3, 0.7F, bad ? 0xFFE05050 : DIM, UP_X - LEG_X - 4);
            }
            button(g, mouseX, mouseY, UP_X, y, BTN, BTN, Component.literal("\u25B2"), i > 0, () -> send(PacketCaravanRoute.UP, i));
            button(g, mouseX, mouseY, DOWN_X, y, BTN, BTN, Component.literal("\u25BC"), i < n - 1, () -> send(PacketCaravanRoute.DOWN, i));
            button(g, mouseX, mouseY, DEL_X, y, BTN, BTN, Component.literal("\u00D7"), true, () -> send(PacketCaravanRoute.REMOVE, i));
            if (over(mouseX, mouseY, UP_X, y, BTN * 3 + 4, BTN)) {
                int which = (mouseX - UP_X) / 14;
                tooltip(List.of(Component.translatable(which == 0 ? "gui.pioneers.route.up" : which == 1 ? "gui.pioneers.route.down" : "gui.pioneers.route.remove")));
            }
        }
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.route.stops", n).append(" \u00B7 ").append(Component.translatable("gui.pioneers.route.leg", total)),
              8, FOOT_Y + 2, 0.8F, TEXT, W - 16);
        GuiDraw.text(g, font, Component.translatable("gui.pioneers.route.hint"), 8, FOOT_Y + 12, 0.7F, DIM, W - 16);
        button(g, mouseX, mouseY, W - 78, CLEAR_Y, 72, BTN, Component.translatable("gui.pioneers.route.clear"), n > 0,
              () -> send(PacketCaravanRoute.CLEAR, 0));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll -= (int) Math.signum(delta);
        return true;
    }
}
