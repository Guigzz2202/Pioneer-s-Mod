package fr.pioneers.client.gui.core;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.tech.computing.NetworkTerminalTile;
import fr.pioneers.core.Texts;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 0.20.46 : interface du terminal de réseau : quatre onglets — Vue d'ensemble (données, calcul, recherche, machines par rôle), Données (stock
 * par type), Machines (branchées, rôle, état), Que faire ? (conseils calculés par le terminal). API Machine (lot 8b) : vue {@code network} de
 * la fiche, en VRAIE v2 (zone de 236 × 152, rangée des pages et écran déclarés par {@code NetworkTerminalTile.guiParts}), sans
 * {@code instanceof} ; page affichée gardée d'une ouverture à l'autre.
 */
final class NetworkViews {

    private static final int X = NetworkTerminalTile.PAGES.x(), TOP = NetworkTerminalTile.PAGES.y(), W = NetworkTerminalTile.PAGES.w(),
          TAB_H = NetworkTerminalTile.PAGES.h(), SCREEN_Y = NetworkTerminalTile.SCREEN.y(), SCREEN_H = NetworkTerminalTile.SCREEN.h();
    private static final int TEXT = PiMenuScreen.TEXT, DIM = PiMenuScreen.DIM, GREEN = 0xFF8CF5C6, RED = 0xFFFF7A6A, GOLD = 0xFFF2C46A;
    /** Couleurs des rôles (pastilles). */
    static final int[] ROLE_COLOR = {0x8FA3A0, 0x50A0FF, 0xF2C46A, 0x50DCA0, 0xB07CFF, 0xFF7AC8, 0x8CF5C6, 0xC8A070};
    private static final String[] PAGES = {"overview", "data", "machines", "help"};

    private final GuiMachine gui;
    private static int page;

    private NetworkViews(GuiMachine gui) {
        this.gui = gui;
    }

    static final MachineScreens.View VIEW = new MachineScreens.View() {
        @Override
        public void draw(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            NetworkTerminalTile t = StorageViews.as(NetworkTerminalTile.class, be);
            if (t != null) {
                new NetworkViews(gui).draw(g, mouseX, mouseY, t);
            }
        }

        @Override
        public boolean foreground(GuiMachine gui, GuiGraphics g, int mouseX, int mouseY, BlockEntity be) {
            return true;
        }

        @Override
        public boolean standard() {
            return true;
        }

        @Override
        public List<StandardView.InfoRow> infoRows(GuiMachine gui, BlockEntity be) {
            return List.of();
        }

        @Override
        public String infoHelp() {
            return "gui.pioneers.api.info.help_network";
        }
    };

    private static Component tr(String key, Object... args) {
        return Component.translatable("computing.pioneers.terminal." + key, args);
    }

    private static String num(double v) {
        return GuiMachine.compact(v);
    }

    private void draw(GuiGraphics g, int mouseX, int mouseY, NetworkTerminalTile t) {
        int tw = W / PAGES.length;
        for (int i = 0; i < PAGES.length; i++) {
            int p = i;
            gui.pageButton(g, mouseX, mouseY, X + i * tw, TOP, tw - 2, TAB_H, tr("tab." + PAGES[i]), page == i, 0x3FD0A0, () -> page = p);
        }
        gui.screenAt(g, X, SCREEN_Y, W, SCREEN_H);
        Font font = gui.font();
        int x = X + 6, y = SCREEN_Y + 5, w = W - 12;
        if (t.grids == 0) {
            GuiDraw.wrapped(g, font, tr("tip.0"), x, y, 0.7F, GOLD, w, 8, 8);
            return;
        }
        switch (page) {
            case 0 -> overview(g, font, t, x, y, w);
            case 1 -> data(g, font, t, x, y, w);
            case 2 -> machines(g, font, t, x, y, w);
            default -> help(g, font, t, x, y, w);
        }
    }

    private void row(GuiGraphics g, Font font, Component label, Component value, int color, int x, int y, int w) {
        float vw = Math.min(font.width(value) * 0.62F, w * 0.6F);
        GuiDraw.text(g, font, label, x, y, 0.62F, DIM, w - vw - 4);
        GuiDraw.text(g, font, value, x + w - vw, y, 0.62F, color, vw);
    }

    private void overview(GuiGraphics g, Font font, NetworkTerminalTile t, int x, int y, int w) {
        int half = (w - 6) / 2, x2 = x + half + 6;
        //Colonne gauche : données et calcul
        GuiDraw.text(g, font, tr("section.data"), x, y, 0.7F, TEXT, half);
        int yy = y + 9;
        row(g, font, tr("banks"), Component.literal(t.banks + ""), t.banks > 0 ? GREEN : RED, x, yy, half);
        yy += 7;
        row(g, font, tr("stored"), Component.literal(Texts.format(t.stored) + " / " + Texts.format(t.bankCapacity) + " Mo"), GREEN, x, yy, half);
        yy += 7;
        GuiDraw.bar(g, x, yy, half, 3, t.bankCapacity <= 0 ? 0 : Math.min(1, t.stored / (double) t.bankCapacity), 0xFF50A0FF);
        yy += 6;
        row(g, font, tr("types"), Component.literal(t.stock.size() + ""), GREEN, x, yy, half);
        yy += 7;
        row(g, font, tr("cables"), Component.literal(Texts.format(t.cables)), DIM, x, yy, half);
        yy += 11;
        GuiDraw.text(g, font, tr("section.compute"), x, yy, 0.7F, TEXT, half);
        yy += 9;
        computeRow(g, font, "GOPS", t.supplyGops, t.demandGops, x, yy, half);
        yy += 10;
        computeRow(g, font, "QOPS", t.supplyQops, t.demandQops, x, yy, half);
        yy += 10;
        GuiDraw.wrapped(g, font, tr("compute_note"), x, yy, 0.5F, DIM, half, 4, 5.5F);
        //Colonne droite : recherche et machines
        GuiDraw.text(g, font, tr("section.research"), x2, y, 0.7F, TEXT, half);
        yy = y + 9;
        if (t.research.isEmpty()) {
            GuiDraw.text(g, font, tr("no_research"), x2, yy, 0.62F, DIM, half);
            yy += 8;
        } else {
            GuiDraw.text(g, font, Component.translatable(t.research), x2, yy, 0.62F, GOLD, half);
            yy += 8;
            for (NetworkTerminalTile.Need n : t.needs) {
                boolean ok = n.inStock() >= n.perUnit();
                g.fill(x2, yy + 1, x2 + 3, yy + 4, 0xFF000000 | n.color());
                row(g, font, Component.translatable(n.key()), tr("need", Texts.format(n.inStock()), Texts.format(n.perUnit())), ok ? GREEN : RED,
                      x2 + 5, yy, half - 5);
                yy += 7;
            }
        }
        yy += 4;
        GuiDraw.text(g, font, tr("section.machines"), x2, yy, 0.7F, TEXT, half);
        yy += 9;
        int[] count = new int[ROLE_COLOR.length];
        for (NetworkTerminalTile.Machine m : t.machines) {
            count[m.role()]++;
        }
        for (int r = 1; r < count.length; r++) {
            if (count[r] == 0) {
                continue;
            }
            g.fill(x2, yy + 1, x2 + 3, yy + 4, 0xFF000000 | ROLE_COLOR[r]);
            row(g, font, tr("role." + r), Component.literal(count[r] + ""), TEXT, x2 + 5, yy, half - 5);
            yy += 7;
        }
        if (count[0] > 0) {
            row(g, font, tr("role.0"), Component.literal(count[0] + ""), DIM, x2 + 5, yy, half - 5);
        }
    }

    private void computeRow(GuiGraphics g, Font font, String unit, double supply, double demand, int x, int y, int w) {
        int color = demand <= 0 ? DIM : supply + 1e-6 >= demand ? GREEN : RED;
        row(g, font, Component.literal(unit), tr("supply_demand", num(supply), num(demand)), color, x, y, w);
        GuiDraw.bar(g, x, y + 7, w, 2, supply <= 0 ? 0 : Math.min(1, demand / supply), 0xFF000000 | (color & 0xFFFFFF));
    }

    private void data(GuiGraphics g, Font font, NetworkTerminalTile t, int x, int y, int w) {
        if (t.stock.isEmpty()) {
            GuiDraw.wrapped(g, font, tr("no_data"), x, y, 0.62F, DIM, w, 6, 7);
            return;
        }
        int half = (w - 6) / 2, rows = (SCREEN_H - 10) / 8;
        for (int i = 0; i < t.stock.size() && i < rows * 2; i++) {
            NetworkTerminalTile.Entry e = t.stock.get(i);
            int cx = i < rows ? x : x + half + 6, cy = y + (i % rows) * 8;
            g.fill(cx, cy + 1, cx + 4, cy + 5, 0xFF000000 | e.color());
            row(g, font, Component.translatable(e.key()), Component.literal(Texts.format(e.amount()) + " Mo"), GREEN, cx + 6, cy, half - 6);
        }
    }

    private void machines(GuiGraphics g, Font font, NetworkTerminalTile t, int x, int y, int w) {
        if (t.machines.isEmpty()) {
            GuiDraw.wrapped(g, font, tr("no_machine"), x, y, 0.62F, DIM, w, 6, 7);
            return;
        }
        int half = (w - 6) / 2, rows = (SCREEN_H - 10) / 8;
        List<NetworkTerminalTile.Machine> list = t.machines;
        for (int i = 0; i < list.size() && i < rows * 2; i++) {
            NetworkTerminalTile.Machine m = list.get(i);
            int cx = i < rows ? x : x + half + 6, cy = y + (i % rows) * 8;
            g.fill(cx, cy + 1, cx + 4, cy + 5, 0xFF000000 | ROLE_COLOR[Math.max(0, Math.min(ROLE_COLOR.length - 1, m.role()))]);
            row(g, font, Component.translatable(m.name()), detail(m), TEXT, cx + 6, cy, half - 6);
        }
        if (list.size() > rows * 2) {
            GuiDraw.text(g, font, tr("more", list.size() - rows * 2), x, y + rows * 8, 0.55F, DIM, w);
        }
    }

    private static Component detail(NetworkTerminalTile.Machine m) {
        String d = m.detail();
        if (d.contains("|")) {
            String[] p = d.split("\\|", 2);
            long amount;
            try {
                amount = Long.parseLong(p[0]);
            } catch (NumberFormatException e) {
                amount = 0;
            }
            return Component.literal(Texts.format(amount) + " Mo ").append(Component.translatable(p[1]));
        }
        if (d.equals("on") || d.equals("off")) {
            return tr(d);
        }
        return Component.literal(d);
    }

    private void help(GuiGraphics g, Font font, NetworkTerminalTile t, int x, int y, int w) {
        int yy = y;
        for (int tip : t.tips) {
            int color = tip == NetworkTerminalTile.TIP_ALL_GOOD ? GREEN : tip == NetworkTerminalTile.TIP_NO_RESEARCH ? DIM : GOLD;
            GuiDraw.text(g, font, Component.literal("• ").append(tr("tip." + tip + ".title")), x, yy, 0.65F, color, w);
            yy += 7;
            int lines = GuiDraw.wrapped(g, font, tr("tip." + tip), x + 5, yy, 0.55F, DIM, w - 5, 4, 6);
            yy += lines * 6 + 4;
            if (yy > SCREEN_Y + SCREEN_H - 14) {
                break;
            }
        }
        GuiDraw.wrapped(g, font, tr("legend"), x, SCREEN_Y + SCREEN_H - 13, 0.5F, DIM, w, 2, 5.5F);
    }

    static String percent(double v) {
        return String.format(Locale.ROOT, "%.0f %%", v * 100);
    }
}
