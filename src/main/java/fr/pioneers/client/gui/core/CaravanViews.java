package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.caravan.RelayTile;
import fr.pioneers.caravan.RelayUnit;
import fr.pioneers.client.gui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 1.0.4-beta (lot 10) : vue v2 du RELAIS DE CARAVANE ({@code caravan_relay}) : rangee ORDRE (charger, decharger, les deux, passer), FILTRE
 * (9 cases fantomes : clic avec un objet = l'ajoute, clic droit = vide), DEPART (apres l'attente, bats pleins, bats vides), ATTENTE (pas de
 * 5 s), MANGEOIRE (4 cases), ecran d'etat (conteneur voisin, rations, caravanes a l'arret, proprietaire). Geometrie : {@link RelayTile} ;
 * maquette fidele {@code scratchpad/p104/lot10/tools/gui_mock10.py}.
 */
final class CaravanViews {

    static final String[] ORDERS = {"load", "unload", "both", "pass"}, CONDS = {"time", "full", "empty"};
    static final int ACCENT = 0xC8964A, ORDER_W = 36, ORDER_STEP = 37, COND_W = 48, COND_STEP = 49, STEP_W = 66;

    private CaravanViews() {
    }

    private static int x0(Rect r) {
        return r.x() + RelayTile.LABEL_W + 2;
    }

    private static void label(GuiMachine gui, GuiGraphics g, Rect r, String key) {
        GuiDraw.text(g, gui.font(), Component.translatable(key), r.x(), r.y() + (r.h() - 6) / 2F, 0.7F, PiMenuScreen.DIM, RelayTile.LABEL_W);
    }

    /** Case du filtre sous (x, y) (coordonnees du panneau), ou -1. */
    static int filterCell(double x, double y) {
        Rect r = RelayTile.FILTER_ROW;
        int fx = x0(r);
        if (y < r.y() + 1 || y >= r.y() + 1 + RelayTile.CELL || x < fx || x >= fx + RelayUnit.FILTER_KEYS.length * RelayTile.CELL) {
            return -1;
        }
        return (int) ((x - fx) / RelayTile.CELL);
    }

    /** Clic droit sur une case du filtre : elle se vide. */
    static boolean clicked(GuiMachine gui, BlockEntity tile, double x, double y, int button) {
        if (StorageViews.as(RelayTile.class, tile) == null || button != 1) {
            return false;
        }
        int i = filterCell(x, y);
        if (i < 0) {
            return false;
        }
        gui.sendSetting(RelayUnit.SETTING_FILTER + i, 0);
        return true;
    }

    static final MachineScreens.View RELAY = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            int order = (int) gui.value(RelayUnit.ORDER), cond = (int) gui.value(RelayUnit.COND), wait = (int) gui.value(RelayUnit.WAIT);
            //Ordre
            Rect r = RelayTile.ORDER_ROW;
            label(gui, g, r, "gui.pioneers.relay.order");
            for (int k = 0; k < ORDERS.length; k++) {
                int kk = k, x = x0(r) + k * ORDER_STEP;
                gui.pageButton(g, mouseX, mouseY, x, r.y(), ORDER_W, r.h(), Component.translatable("caravan.pioneers.order." + ORDERS[k]), order == k,
                      ACCENT, () -> gui.sendSetting(RelayUnit.SETTING_ORDER, kk));
                if (gui.over(mouseX, mouseY, x, r.y(), ORDER_W, r.h())) {
                    gui.tooltip(List.of(Component.translatable("caravan.pioneers.order." + ORDERS[k]),
                          Component.translatable("caravan.pioneers.order." + ORDERS[k] + ".tip").withStyle(ChatFormatting.GRAY)));
                }
            }
            //Filtre
            r = RelayTile.FILTER_ROW;
            label(gui, g, r, "gui.pioneers.relay.filter");
            for (int i = 0; i < RelayUnit.FILTER_KEYS.length; i++) {
                int ii = i, x = x0(r) + i * RelayTile.CELL, y = r.y() + 1;
                gui.frame(g, x, y, RelayTile.CELL, RelayTile.CELL);
                long id = gui.value(RelayUnit.FILTER_KEYS[i]);
                Item it = id <= 0 ? null : BuiltInRegistries.ITEM.byId((int) id);
                if (it != null && it != Items.AIR) {
                    g.pose().pushPose();
                    g.pose().translate(x + 1, y + 1, 0);
                    g.pose().scale(0.875F, 0.875F, 1);
                    g.renderFakeItem(new ItemStack(it), 0, 0);
                    g.pose().popPose();
                }
                gui.click(x, y, RelayTile.CELL, RelayTile.CELL, () -> {
                    ItemStack carried = gui.getMenu().getCarried();
                    gui.sendSetting(RelayUnit.SETTING_FILTER + ii, carried.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(carried.getItem()));
                });
                if (gui.over(mouseX, mouseY, x, y, RelayTile.CELL, RelayTile.CELL)) {
                    List<Component> tip = new ArrayList<>();
                    if (it != null && it != Items.AIR) {
                        tip.add(new ItemStack(it).getHoverName());
                    }
                    tip.add(Component.translatable("gui.pioneers.relay.filter.tip").withStyle(ChatFormatting.GRAY));
                    gui.tooltip(tip);
                }
            }
            //Depart
            r = RelayTile.LEAVE_ROW;
            label(gui, g, r, "gui.pioneers.relay.leave");
            for (int k = 0; k < CONDS.length; k++) {
                int kk = k, x = x0(r) + k * COND_STEP;
                gui.pageButton(g, mouseX, mouseY, x, r.y(), COND_W, r.h(), Component.translatable("caravan.pioneers.cond." + CONDS[k]), cond == k,
                      ACCENT, () -> gui.sendSetting(RelayUnit.SETTING_COND, kk));
                if (gui.over(mouseX, mouseY, x, r.y(), COND_W, r.h())) {
                    gui.tooltip(List.of(Component.translatable("caravan.pioneers.cond." + CONDS[k]),
                          Component.translatable("caravan.pioneers.cond.tip").withStyle(ChatFormatting.GRAY)));
                }
            }
            //Attente
            r = RelayTile.WAIT_ROW;
            label(gui, g, r, "gui.pioneers.relay.wait");
            gui.stepper(g, mouseX, mouseY, x0(r), r.y(), STEP_W, Component.translatable("gui.pioneers.relay.wait_value", wait),
                  Component.translatable("gui.pioneers.relay.wait"), RelayTile.WAIT_DOWN, RelayTile.WAIT_UP);
            //Ecran d'etat
            Rect s = StandardLayout.screen(RelayTile.WIDTH);
            boolean container = gui.value(RelayUnit.CONTAINER) > 0;
            long rations = gui.value(RelayUnit.RATIONS), here = gui.value(RelayUnit.HERE);
            List<GuiMachine.Row> rows = new ArrayList<>();
            rows.add(new GuiMachine.Row(Component.translatable("gui.pioneers.relay.container"), Component.translatable(container
                  ? "gui.pioneers.relay.container.ok" : "gui.pioneers.relay.container.none"), container ? GuiMachine.SCREEN_TEXT : 0xFFE0A040));
            rows.add(GuiMachine.Row.of("gui.pioneers.relay.manger", Component.translatable("gui.pioneers.relay.rations", rations)));
            rows.add(GuiMachine.Row.of("gui.pioneers.relay.caravans", Component.translatable("gui.pioneers.relay.caravans_value", here)));
            //Proprietaire en tete de l'ecran (3 lignes au plus tiennent sous l'en-tete : conteneur, rations, caravanes)
            RelayTile t = StorageViews.as(RelayTile.class, be);
            String owner = t == null ? "" : t.relay().ownerName;
            gui.info(g, s.x(), s.y(), s.w(), s.h(), container ? 0x6EDC5A : 0xE0A040, Component.translatable("block.pioneers.caravan_relay"),
                  Component.literal(owner.isEmpty() ? "-" : owner), -1, ACCENT, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            for (int i = 0; i < 4; i++) {
                gui.slotTip(mouseX, mouseY, RelayTile.MANGER_X + i * 18, RelayTile.MANGER_Y, "gui.pioneers.relay.manger.tip");
            }
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age.bronze"), StandardView.ageColor("bronze")));
            return rows;
        }
    };
}
