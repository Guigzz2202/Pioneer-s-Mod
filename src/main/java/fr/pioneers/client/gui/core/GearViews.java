package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.equipment.gear.GearModule;
import fr.pioneers.equipment.gear.ModularGear;
import fr.pioneers.equipment.gear.ModulationBenchTile;
import fr.pioneers.equipment.gear.Modules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 0.20.15 : vue de l'etabli de modulation : energie et batterie a gauche, objet modulaire et module a installer (bouton Installer dessous),
 * ecran des modules installes a droite : nom et niveau, interrupteur (marche / arret) et retrait d'un niveau par module ; reserve de l'objet
 * en tete de l'ecran. L'objet est lu dans sa case du menu (synchronisee).
 * <p>
 * API Machine (lot 4) : interface standard v2 (vue {@code gear} de la fiche, {@link #VIEW}) : zone de 196 x 152, inventaire dans le cadre,
 * onglets a droite, valeur nommee {@link ModulationBenchTile#CHARGING} ; l'ecran des modules prend toute la hauteur a droite.
 */
final class GearViews {

    /** Lot 4 : interface standard v2 (places de {@link ModulationBenchTile} : colonne de l'objet et du module, ecran des modules). */
    private static final int SX = ModulationBenchTile.MODULES.x(), SY = ModulationBenchTile.MODULES.y(), SW = ModulationBenchTile.MODULES.w(),
          SH = ModulationBenchTile.MODULES.h(), ROW = 9;

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            new GearViews(gui).draw(g, mouseX, mouseY);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity tile) {
            new GearViews(gui).foreground(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity tile) {
            return List.of(new StandardView.InfoRow("consumption", GuiMachine.literal(GuiMachine.compact(gui.value(UiValues.USAGE) / 100.0)
                                                                                              + " FE/t"), 0xFFFFFF55),
                  new StandardView.InfoRow("reserve", GuiMachine.literal(Texts.energy(gui.value(UiValues.CAPACITY))), PiMenuScreen.TEXT));
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_bench";
        }
    };

    private final GuiMachine gui;

    GearViews(GuiMachine gui) {
        this.gui = gui;
    }

    void draw(GuiGraphics g, int mx, int my) {
        gui.energy(g, mx, my, 8, StandardLayout.TOP, StandardLayout.GAUGE_W, StandardLayout.ENERGY_H);
        ItemStack gear = gui.getMenu().slots.isEmpty() ? ItemStack.EMPTY : gui.getMenu().getSlot(0).getItem();
        ItemStack module = gui.getMenu().slots.size() < 2 ? ItemStack.EMPTY : gui.getMenu().getSlot(1).getItem();
        boolean can = ModularGear.is(gear) && !module.isEmpty();
        StandardLayout.Rect b = ModulationBenchTile.INSTALL_BUTTON;
        gui.steelButton(g, mx, my, b.x(), b.y(), b.w(), b.h(), Component.translatable("gui.pioneers.bench.install"), can, () -> gui.send(ModulationBenchTile.INSTALL));
        int cx = ModulationBenchTile.GEAR_X + 8;
        GuiDraw.center(g, gui.font(), Component.translatable("gui.pioneers.bench.gear"), cx, ModulationBenchTile.GEAR_Y - 8, 0.55F, PiMenuScreen.DIM, 34);
        GuiDraw.center(g, gui.font(), Component.translatable("gui.pioneers.bench.module"), cx, ModulationBenchTile.MODULE_Y - 8, 0.55F, PiMenuScreen.DIM, 34);
        int ay = ModulationBenchTile.MODULE_Y - 14;                                           // fleche : module -> objet
        g.fill(cx - 1, ay, cx + 1, ay + 4, 0xFF3C4A57);
        g.fill(cx - 2, ay, cx + 2, ay + 1, 0xFF3C4A57);
        g.fill(cx - 3, ay + 1, cx + 3, ay + 2, 0xFF3C4A57);
        gui.screenAt(g, SX, SY, SW, SH);
        if (!(gear.getItem() instanceof ModularGear item)) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.bench.empty"), SX + 6, SY + 26, 0.65F, PiMenuScreen.DIM, SW - 12, 4, 7);
            return;
        }
        int color = 0xFF000000 | item.tier().color;
        GuiDraw.text(g, gui.font(), gear.getHoverName(), SX + 5, SY + 4, 0.65F, color, SW - 40);
        Component slots = Component.literal(Modules.used(gear) + " / " + item.tier().slots);
        GuiDraw.right(g, gui.font(), slots, SX + SW - 5, SY + 4, 0.65F, PiMenuScreen.SCREEN_TEXT);
        float ratio = Modules.ratio(gear);
        g.fill(SX + 5, SY + 11, SX + SW - 5, SY + 15, 0xFF05080B);
        int bw = Math.round((SW - 12) * ratio);
        if (bw > 0) {
            g.fill(SX + 6, SY + 12, SX + 6 + bw, SY + 14, color);
        }
        if (gui.over(mx, my, SX + 5, SY + 10, SW - 10, 6)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gear.pioneers.energy", Texts.energy(Modules.energy(gear)), Texts.energy(Modules.capacity(gear))));
            if (gui.value(ModulationBenchTile.CHARGING) > 0) {
                tip.add(Component.translatable("gui.pioneers.bench.charging", Texts.energy(gui.value(ModulationBenchTile.CHARGING))).withStyle(ChatFormatting.GRAY));
            }
            gui.tooltip(tip);
        }
        List<GearModule> list = Modules.list(gear);
        if (list.isEmpty()) {
            GuiDraw.wrapped(g, gui.font(), Component.translatable("gui.pioneers.bench.no_modules"), SX + 6, SY + 22, 0.6F, PiMenuScreen.DIM, SW - 12, 4, 7);
            return;
        }
        int y = SY + 19;
        for (int i = 0; i < list.size(); i++) {
            if (y + ROW > SY + SH - 2) {
                GuiDraw.text(g, gui.font(), Component.literal("+" + (list.size() - i)), SX + 6, y, 0.6F, PiMenuScreen.DIM, 30);
                break;
            }
            GearModule m = list.get(i);
            boolean off = Modules.isOff(gear, m);
            int lvl = Math.min(Modules.installed(gear, m), Math.max(1, m.cap(item.tier())));
            Component name = m.displayName().copy().append(" " + Modules.roman(lvl));
            GuiDraw.text(g, gui.font(), name, SX + 6, y + 1, 0.6F, off ? PiMenuScreen.DIM : PiMenuScreen.SCREEN_TEXT, SW - 36);
            int tx = SX + SW - 27, rx = SX + SW - 11;
            boolean hoverT = gui.over(mx, my, tx, y, 14, 7);
            g.fill(tx, y, tx + 14, y + 7, off ? (hoverT ? 0xFF5A2A2A : 0xFF3A1E1E) : hoverT ? 0xFF2E6A4A : 0xFF1F4A34);
            GuiDraw.center(g, gui.font(), Component.translatable(off ? "gui.pioneers.bench.off" : "gui.pioneers.bench.on"), tx + 7, y + 1, 0.5F,
                  off ? 0xFFFF8A80 : 0xFF8CF5C6, 14);
            int index = i;
            gui.click(tx, y, 14, 7, () -> gui.send(ModulationBenchTile.TOGGLE + index));
            boolean hoverR = gui.over(mx, my, rx, y, 7, 7);
            g.fill(rx, y, rx + 7, y + 7, hoverR ? 0xFF6A4A2A : 0xFF3A2E22);
            g.fill(rx + 1, y + 3, rx + 6, y + 4, 0xFFFFC080);
            gui.click(rx, y, 7, 7, () -> gui.send(ModulationBenchTile.REMOVE + index));
            if (gui.over(mx, my, SX + 4, y, SW - 34, ROW - 1)) {
                gui.tooltip(List.of(m.displayName().copy().withStyle(ChatFormatting.AQUA), m.effect().copy().withStyle(ChatFormatting.GRAY)));
            } else if (hoverR) {
                gui.tooltip(List.of(Component.translatable("gui.pioneers.bench.remove")));
            } else if (hoverT) {
                gui.tooltip(List.of(Component.translatable("gui.pioneers.bench.toggle")));
            }
            y += ROW;
        }
    }

    void foreground(int mx, int my) {
        gui.slotTip(mx, my, ModulationBenchTile.GEAR_X, ModulationBenchTile.GEAR_Y, "gui.pioneers.bench.gear_tip");
        gui.slotTip(mx, my, ModulationBenchTile.MODULE_X, ModulationBenchTile.MODULE_Y, "gui.pioneers.bench.module_tip");
        gui.slotTip(mx, my, ModulationBenchTile.BATTERY_X, ModulationBenchTile.BATTERY_Y, "gui.pioneers.machine.battery");
    }
}
