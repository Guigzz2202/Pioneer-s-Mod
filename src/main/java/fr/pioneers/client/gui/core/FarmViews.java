package fr.pioneers.client.gui.core;

import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.api.machine.StandardLayout.Rect;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.client.gui.core.GuiMachine.Row;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.farm.ui.FarmLayout;
import fr.pioneers.farm.ui.FarmMachine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 41, retour 60 de Guillaume : « Ajout d'interfaces sur les machines de fermes (plus d'interactions directes, une vraie
 * interface et fin de la bulle d'info, car l'interface est explicite) ») : VUE {@code farm} de l'interface standard v2 : postes de la cuisine
 * (planche, fourneau, marmite, poele, four, saladier, sechoir, bocal, cuve, fut, chaudron, alambic, pressoir, presse a fromage, sertisseuse,
 * baratte, mangeoire, abreuvoir, extracteur de miel, fumoir) et appareils de la ferme (arroseurs, composteur, station meteo, ruche, pondoir,
 * couveuse, distributeur, clapier). Tout vient de la disposition de la tile ({@link FarmLayout}) : jauge de gauche (feu, chaleur, air), jauges
 * des cuves, fleche, barres des cases de cuisson, ecran d'etat (le contenu de l'ancienne fiche au regard, recalcule une fois par tick du
 * client), boutons du geste ({@code MachineGui.CUSTOM + i}), infobulles des cases vides, onglet Infos. Valeurs lues par leur NOM.
 */
final class FarmViews {

    private static final int GREEN = 0x3FD0A0, AMBER = 0xE0B040, FIRE_LO = 0xFFE0451E, FIRE_HI = 0xFFFFE07A, HEAT = 0xFFE07A30, AIR = 0xFF7EC8F0,
          OFF = 0xFF1A2027;

    static final MachineScreens.View VIEW = new Farm();

    /** Etat a l'ecran garde pour le tick en cours (regle de performance 10 : textes une fois par tick, pas a chaque image). */
    @Nullable
    private static FarmCard cached;
    @Nullable
    private static BlockEntity cachedTile;
    private static long cachedTick = Long.MIN_VALUE;

    private FarmViews() {
    }

    static FarmCard state(FarmMachine m, BlockEntity be) {
        long tick = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        if (cached == null || cachedTile != be || cachedTick != tick) {
            cached = m.screenState();
            cachedTile = be;
            cachedTick = tick;
        }
        return cached;
    }

    private static final class Farm implements MachineScreens.View {

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            FarmMachine m = StorageViews.as(FarmMachine.class, be);
            if (m == null) {
                return;
            }
            FarmLayout l = m.farmLayout();
            if (l.gaugeRect != null) {
                gauge(gui, g, mouseX, mouseY, l, m);
            }
            for (FarmLayout.TankSpec t : l.tanks) {
                FluidTank tank = m.farmTank(t.which());
                if (tank != null) {
                    gui.views().fluidGauge(g, mouseX, mouseY, t.rect().x(), t.rect().y(), t.rect().w(), t.rect().h(), tank);
                }
            }
            double progress = gui.value(UiValues.PROGRESS_X1000) / 1000.0;
            if (l.arrow != null) {
                StandardView.arrow(g, l.arrow, progress, GREEN);
            }
            List<FarmLayout.SlotSpec> cells = l.slots(FarmLayout.Role.CELL);
            for (int i = 0; i < cells.size(); i++) {
                FarmLayout.SlotSpec s = cells.get(i);
                long v = gui.value(UiValues.item("farm_cell", i));
                SourceViews.slotBar(g, s.x(), s.y() + 18, v < 0 ? 0 : v / 1000.0, v >= 1000 ? GREEN : AMBER);
            }
            screen(gui, g, l.screen, state(m, be));
            for (int i = 0; i < l.buttons.size(); i++) {
                Rect r = l.buttonRects.get(i);
                int action = MachineGui.CUSTOM + i;
                boolean active = m.buttonActive(i);
                gui.steelButton(g, mouseX, mouseY, r.x(), r.y(), r.w(), r.h(), Component.translatable(l.buttons.get(i)), active, () -> gui.send(action));
                if (gui.over(mouseX, mouseY, r.x(), r.y(), r.w(), r.h())) {
                    List<Component> tip = new ArrayList<>(m.buttonTip(i));
                    if (i == 0 && m.manualGesture()) {
                        tip.add(Component.translatable("gui.pioneers.farm.tip.gesture").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    }
                    if (!tip.isEmpty()) {
                        gui.tooltip(tip);
                    }
                }
            }
        }

        /** Jauge de gauche : feu restant (du rouge au jaune), chaleur du dessous ou air sec (pleine ou eteinte). */
        private static void gauge(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, FarmLayout l, FarmMachine m) {
            Rect r = l.gaugeRect;
            int x = r.x(), y = r.y(), w = r.w(), h = r.h();
            gui.frame(g, x, y, w, h);
            float level = Math.max(0, Math.min(1, m.gaugeLevel()));
            int inner = h - 2;
            int filled = l.gauge == FarmLayout.Gauge.FIRE ? Math.round(inner * level) : level > 0 ? inner : 0;
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, OFF);
            for (int i = 0; i < filled; i++) {
                int c = switch (l.gauge) {
                    case FIRE -> GuiMachine.lerp(FIRE_LO, FIRE_HI, i / (double) inner);
                    case AIR -> AIR;
                    default -> GuiMachine.lerp(HEAT, FIRE_HI, i / (double) inner * 0.6);
                };
                g.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, c);
            }
            GuiMachine.ticks(g, x, y, w, h);
            if (gui.over(mouseX, mouseY, x, y, w, h)) {
                List<Component> tip = m.gaugeTip();
                if (!tip.isEmpty()) {
                    gui.tooltip(tip);
                }
            }
        }

        /** Ecran d'etat : la premiere ligne en en-tete (pastille, valeur, barre si c'est une jauge), les suivantes en lignes. */
        private static void screen(GuiMachine gui, GuiGraphics g, Rect s, FarmCard card) {
            List<FarmCard.Line> lines = card.lines();
            Component name = Component.translatable("gui.pioneers.farm.state.idle"), headline = Component.empty();
            double ratio = -1;
            int chip = 0, bar = GREEN;
            int from = 0;
            if (!lines.isEmpty()) {
                FarmCard.Line first = lines.get(0);
                from = 1;
                switch (first.kind()) {
                    case GAUGE -> {
                        name = first.text() != null ? first.text() : first.value() != null ? first.value() : name;
                        headline = first.text() != null && first.value() != null ? first.value() : Component.empty();
                        ratio = first.bar();
                        chip = first.colour();
                        bar = first.colour();
                    }
                    case PAIR -> {
                        name = first.text() == null ? name : first.text();
                        headline = first.value() == null ? Component.empty() : first.value();
                        chip = first.colour();
                    }
                    default -> name = first.text() == null ? name : first.text();
                }
            }
            List<Row> rows = new ArrayList<>();
            for (int i = from; i < lines.size(); i++) {
                FarmCard.Line line = lines.get(i);
                int colour = 0xFF000000 | (line.colour() == 0 ? 0xD8DCE0 : line.colour());
                switch (line.kind()) {
                    case PAIR -> rows.add(new Row(line.text() == null ? Component.empty() : line.text(), line.value() == null ? Component.empty() : line.value(),
                          colour));
                    case GAUGE -> rows.add(new Row(line.text() == null ? Component.empty() : line.text(), line.value() == null ? GuiMachine.percent(line.bar())
                          : line.value(), colour));
                    case TEXT -> rows.add(new Row(line.text() == null ? Component.empty() : line.text(), Component.empty(), colour));
                    default -> {
                    }
                }
            }
            gui.info(g, s.x(), s.y(), s.w(), s.h(), chip, name, headline, ratio, bar, rows, false);
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            FarmMachine m = StorageViews.as(FarmMachine.class, be);
            if (m != null) {
                for (FarmLayout.SlotSpec s : m.farmLayout().slots) {
                    gui.slotTip(mouseX, mouseY, s.x(), s.y(), s.tip());
                }
            }
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            List<StandardView.InfoRow> rows = new ArrayList<>();
            MachineSpec spec = MachineScreens.spec(be);
            if (spec != null && spec.age() != null) {
                rows.add(new StandardView.InfoRow("age", Component.translatable("tooltip.pioneers.api.age." + spec.age()), StandardView.ageColor(spec.age())));
            }
            FarmMachine m = StorageViews.as(FarmMachine.class, be);
            if (m != null) {
                for (Object[] row : m.infoRows()) {
                    rows.add(new StandardView.InfoRow((String) row[0], (Component) row[1], PiMenuScreen.TEXT));
                }
            }
            return rows;
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.farm.info.help";
        }
    }
}
