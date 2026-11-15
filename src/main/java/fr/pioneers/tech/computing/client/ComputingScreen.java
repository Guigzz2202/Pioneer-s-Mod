package fr.pioneers.tech.computing.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.tech.computing.ComputingMenu;
import fr.pioneers.tech.computing.LogicKind;
import fr.pioneers.tech.computing.LogicRule;
import fr.pioneers.tech.computing.LogicTile;
import fr.pioneers.tech.computing.ProbeValue;
import fr.pioneers.tech.computing.Probes;
import fr.pioneers.tech.computing.ScreenTile;
import fr.pioneers.core.Directions;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.core.network.PiNetwork;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Interface de l'informatique industrielle (0.17.0). Automates : liaisons et sorties a gauche, onglets des regles et editeur de la regle
 * choisie a droite (SI source.grandeur comparateur seuil ALORS action). Ecrans de supervision : machine liee, grandeur du graphe, taille.
 * Tout passe par des actions ({@link PacketTileAction}) ; l'affichage suit la synchronisation des tiles.
 */
public class ComputingScreen extends PiMenuScreen<ComputingMenu> {

    private static final int ON = 0xFF3FD0A0, OFF = 0xFF59666A, RED = 0xFFE04848;
    private static final int RX = 106, RW = 188;
    private int selected;

    public ComputingScreen(ComputingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.getTile() instanceof ScreenTile ? 220 : 300, menu.getTile() instanceof ScreenTile ? 124 : 210);
        inventoryLabelY = -1000;
    }

    private void send(int action) {
        BlockEntity tile = menu.getTile();
        if (tile != null) {
            PiNetwork.sendToServer(new PacketTileAction(tile.getBlockPos(), action));
        }
    }

    @Override
    protected void drawBackground(@NotNull GuiGraphics g, int mouseX, int mouseY) {
        BlockEntity tile = menu.getTile();
        if (tile instanceof LogicTile logic) {
            logic(g, mouseX, mouseY, logic);
        } else if (tile instanceof ScreenTile screen) {
            screen(g, mouseX, mouseY, screen);
        }
    }

    // ------------------------------------------------------------------
    // Automates
    // ------------------------------------------------------------------

    private void logic(GuiGraphics g, int mx, int my, LogicTile t) {
        LogicKind kind = t.kind;
        //Liaisons
        label(g, Component.translatable("computing.pioneers.gui.links"), 6, 18, DIM);
        for (int i = 0; i < kind.links; i++) {
            int y = 27 + i * 22;
            screen(g, 6, y, 94, 20);
            BlockPos p = t.link(i);
            if (p == null) {
                GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.free_link"), 9, y + 6, 0.7F, OFF, 88);
            } else {
                GuiDraw.text(g, font, t.linkName(i), 9, y + 3, 0.7F, SCREEN_TEXT, 78);
                GuiDraw.text(g, font, Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()), 9, y + 12, 0.55F, DIM, 78);
                int link = i;
                button(g, mx, my, 88, y + 2, 10, 10, Component.literal("×"), true, () -> send(LogicTile.UNLINK + link));
            }
        }
        //Sorties redstone (une case par face)
        int y = 27 + kind.links * 22 + 4;
        label(g, Component.translatable("computing.pioneers.gui.outputs"), 6, y, DIM);
        for (Direction d : Directions.ALL) {
            int x = 6 + d.get3DDataValue() * 16, level = t.output(d);
            g.fill(x, y + 10, x + 14, y + 24, level > 0 ? 0xFF000000 | (90 + level * 11) << 16 | 0x1818 : 0xFF1A2027);
            g.renderOutline(x, y + 10, 14, 14, EDGE);
            GuiDraw.center(g, font, Component.translatable("computing.pioneers.face_short." + d.getName()), x + 7, y + 11, 0.6F, TEXT, 13);
            GuiDraw.center(g, font, Component.literal(String.valueOf(level)), x + 7, y + 17, 0.6F, level > 0 ? 0xFFFFD0D0 : OFF, 13);
        }
        y += 28;
        if (kind.advanced) {
            label(g, Component.translatable("computing.pioneers.gui.counters"), 6, y, DIM);
            for (int i = 0; i < LogicKind.COUNTERS; i++) {
                screen(g, 6 + i * 24, y + 10, 22, 12);
                GuiDraw.center(g, font, Component.literal(String.valueOf(t.counter(i))), 17 + i * 24, y + 13, 0.65F, SCREEN_TEXT, 20);
            }
        }
        //Energie
        long cap = Math.max(1, t.energyCapacity());
        double ratio = Math.min(1, t.energyStored() / (double) cap);
        g.fill(6, 196, 100, 204, SCREEN);
        g.renderOutline(6, 196, 94, 8, EDGE);
        g.fill(7, 197, 7 + (int) Math.round(92 * ratio), 203, t.powered() ? 0xFFE04848 : 0xFF6A2A2A);
        GuiDraw.text(g, font, Component.translatable(t.powered() ? "computing.pioneers.gui.powered" : "computing.pioneers.gui.unpowered",
              kind.power), 6, 187, 0.6F, t.powered() ? DIM : RED, 94);
        rules(g, mx, my, t);
    }

    private void rules(GuiGraphics g, int mx, int my, LogicTile t) {
        LogicKind kind = t.kind;
        selected = Math.min(selected, kind.rules - 1);
        int tw = Math.min(28, RW / kind.rules);
        for (int i = 0; i < kind.rules; i++) {
            LogicRule r = t.rule(i);
            int x = RX + i * tw, idx = i;
            int fill = !r.enabled ? 0xFF1A2027 : r.active ? 0xFF1F6A50 : 0xFF2A3540;
            g.fill(x, 18, x + tw - 1, 30, fill);
            g.renderOutline(x, 18, tw - 1, 12, i == selected ? ACCENT : EDGE);
            GuiDraw.center(g, font, Component.literal(String.valueOf(i + 1)), x + (tw - 1) / 2F, 21, 0.65F, r.enabled ? TEXT : OFF, tw - 2);
            click(x, 18, tw - 1, 12, () -> selected = idx);
        }
        LogicRule r = t.rule(selected);
        int base = LogicTile.RULE + selected * LogicTile.FIELDS;
        screen(g, RX, 33, RW, 172);
        int x0 = RX + 4;
        //Ligne 0 : activation et etat
        button(g, mx, my, x0, 36, 44, 11, Component.translatable(r.enabled ? "computing.pioneers.gui.enabled" : "computing.pioneers.gui.disabled"),
              true, () -> send(base + LogicTile.F_ENABLE));
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.rule", selected + 1), x0 + 50, 38, 0.75F, TEXT, 70);
        GuiDraw.text(g, font, Component.translatable(r.active ? "computing.pioneers.gui.true" : "computing.pioneers.gui.false"), x0 + 128, 38, 0.75F,
              r.active ? ON : OFF, 54);
        //Ligne 1 : source
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.if"), x0, 54, 0.75F, ACCENT, 14);
        selector(g, mx, my, x0 + 16, 51, RW - 24, sourceName(t, r.source), base + LogicTile.F_SOURCE_PREV, base + LogicTile.F_SOURCE_NEXT);
        //Ligne 2 : grandeur et valeur actuelle
        List<ProbeValue> values = t.source(r.source);
        ProbeValue v = ProbeValue.find(values, r.key);
        Component key = v != null ? v.name() : Component.translatable("computing.pioneers.gui.no_value");
        selector(g, mx, my, x0 + 16, 65, RW - 24, key, base + LogicTile.F_KEY_PREV, base + LogicTile.F_KEY_NEXT);
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.current", v == null ? Component.literal("-") : v.text()), x0 + 16, 79,
              0.6F, DIM, RW - 24);
        //Ligne 3 : comparateur et seuil
        int y = 88;
        button(g, mx, my, x0, y, 14, 12, Component.literal(LogicRule.SYMBOLS[r.comparator]), true, () -> send(base + LogicTile.F_CMP));
        int bx = x0 + 16;
        for (int k = 0; k < 3; k++) {
            int f = base + LogicTile.F_THRESHOLD + k;
            button(g, mx, my, bx + k * 19, y, 18, 12, Component.literal(String.valueOf(LogicTile.THRESHOLD_STEPS[k])), true, () -> send(f));
        }
        //Boutons de 18 px : « -100 » et « +100 » y tiennent sans etre coupes
        screen(g, bx + 57, y, 32, 12);
        GuiDraw.center(g, font, Component.literal(ProbeValue.format(r.threshold, v == null ? "" : v.unit())), bx + 73, y + 3, 0.65F, SCREEN_TEXT, 30);
        for (int k = 3; k < 6; k++) {
            int f = base + LogicTile.F_THRESHOLD + k;
            button(g, mx, my, bx + 91 + (k - 3) * 19, y, 18, 12, Component.literal("+" + LogicTile.THRESHOLD_STEPS[k]), true, () -> send(f));
        }
        button(g, mx, my, bx + 148, y, 16, 12, Component.literal("="), v != null, () -> send(base + LogicTile.F_SET_CURRENT));
        //Ligne 4 : mode et delai (automate)
        y = 104;
        if (t.kind.advanced) {
            button(g, mx, my, x0, y, 52, 12, Component.translatable("computing.pioneers.mode." + LogicRule.MODE_KEYS[r.mode]), true,
                  () -> send(base + LogicTile.F_MODE));
            GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.delay"), x0 + 55, y + 3, 0.65F, DIM, 20);
            stepper(g, mx, my, x0 + 78, y, base + LogicTile.F_DELAY, r.delay + " s");
        }
        //Ligne 5 : action
        y = 122;
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.then"), x0, y + 3, 0.75F, ACCENT, 30);
        selector(g, mx, my, x0 + 30, y, RW - 38, Component.translatable("computing.pioneers.action." + LogicRule.ACTION_KEYS[r.action]),
              base + LogicTile.F_ACTION_PREV, base + LogicTile.F_ACTION_NEXT);
        //Ligne 6 : cible
        y = 137;
        if (r.action != LogicRule.UNLATCH) {
            selector(g, mx, my, x0 + 30, y, RW - 38, targetName(t, r), base + LogicTile.F_TARGET_PREV, base + LogicTile.F_TARGET_NEXT);
        }
        //Ligne 7 : commande
        y = 152;
        if (r.action == LogicRule.COMMAND) {
            Component cmd = r.command.isEmpty() ? Component.translatable("computing.pioneers.gui.no_command")
                                                : Component.translatable("computing.pioneers.command." + r.command);
            selector(g, mx, my, x0 + 30, y, RW - 38, cmd, base + LogicTile.F_COMMAND_PREV, base + LogicTile.F_COMMAND_NEXT);
        }
        //Ligne 8 : niveau ou valeur
        y = 167;
        if (r.action == LogicRule.REDSTONE || r.action == LogicRule.COMMAND && commandHasValue(r.command)) {
            GuiDraw.text(g, font, Component.translatable(r.action == LogicRule.REDSTONE ? "computing.pioneers.gui.level" : "computing.pioneers.gui.value"),
                  x0, y + 3, 0.65F, DIM, 30);
            stepper(g, mx, my, x0 + 30, y, base + LogicTile.F_LEVEL, String.valueOf(r.level));
        }
        //Ligne 9 : effacer
        button(g, mx, my, RX + RW - 54, 190, 50, 12, Component.translatable("computing.pioneers.gui.clear"), true, () -> send(base + LogicTile.F_CLEAR));
    }

    private static boolean commandHasValue(String key) {
        for (Probes.Command c : Probes.REACTOR_COMMANDS) {
            if (c.key().equals(key)) {
                return c.hasValue();
            }
        }
        return false;
    }

    private Component sourceName(LogicTile t, int source) {
        if (source >= t.kind.links) {
            return Component.translatable("computing.pioneers.gui.internal");
        }
        BlockPos p = t.link(source);
        return p == null ? Component.translatable("computing.pioneers.gui.link_n", source + 1).append(" - ").append(
              Component.translatable("computing.pioneers.gui.free_link")) : Component.translatable("computing.pioneers.gui.link_n", source + 1)
                                                                                       .append(" : ").append(t.linkName(source));
    }

    private Component targetName(LogicTile t, LogicRule r) {
        return switch (r.action) {
            case LogicRule.REDSTONE -> Component.translatable("computing.pioneers.face." + Direction.from3DDataValue(Math.floorMod(r.target, 6)).getName());
            case LogicRule.COMMAND -> sourceName(t, Math.min(r.target, t.kind.links - 1));
            default -> Component.translatable("computing.pioneers.gui.counter_n", Math.floorMod(r.target, LogicKind.COUNTERS) + 1);
        };
    }

    /** [<] libelle [>] : deux actions. */
    private void selector(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, int prev, int next) {
        button(g, mx, my, x, y, 12, 12, Component.literal("<"), true, () -> send(prev));
        screen(g, x + 13, y, w - 26, 12);
        GuiDraw.center(g, font, label, x + w / 2F, y + 3, 0.65F, SCREEN_TEXT, w - 30);
        button(g, mx, my, x + w - 12, y, 12, 12, Component.literal(">"), true, () -> send(next));
    }

    /** -10 -1 valeur +1 +10 (champs consecutifs a partir de {@code field}). */
    private void stepper(GuiGraphics g, int mx, int my, int x, int y, int field, String value) {
        for (int k = 0; k < 2; k++) {
            int f = field + k;
            button(g, mx, my, x + k * 17, y, 16, 12, Component.literal(String.valueOf(LogicTile.SMALL_STEPS[k])), true, () -> send(f));
        }
        screen(g, x + 35, y, 30, 12);
        GuiDraw.center(g, font, Component.literal(value), x + 50, y + 3, 0.65F, SCREEN_TEXT, 28);
        for (int k = 2; k < 4; k++) {
            int f = field + k;
            button(g, mx, my, x + 67 + (k - 2) * 17, y, 16, 12, Component.literal("+" + LogicTile.SMALL_STEPS[k]), true, () -> send(f));
        }
    }

    private void label(GuiGraphics g, Component text, int x, int y, int color) {
        GuiDraw.text(g, font, text, x, y, 0.7F, color, 94);
    }

    // ------------------------------------------------------------------
    // Ecran de supervision
    // ------------------------------------------------------------------

    private void screen(GuiGraphics g, int mx, int my, ScreenTile t) {
        screen(g, 6, 18, 208, 34);
        BlockPos p = t.link();
        if (p == null) {
            GuiDraw.wrapped(g, font, Component.translatable("computing.pioneers.screen.hint"), 9, 21, 0.7F, DIM, 202, 4, 7);
        } else {
            GuiDraw.text(g, font, t.name(), 9, 21, 0.8F, SCREEN_TEXT, 200);
            GuiDraw.text(g, font, Component.literal(String.format(Locale.ROOT, "%d %d %d", p.getX(), p.getY(), p.getZ())), 9, 32, 0.6F, DIM, 120);
            button(g, mx, my, 160, 36, 50, 12, Component.translatable("computing.pioneers.gui.unlink"), true, () -> send(ScreenTile.UNLINK));
        }
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.graph"), 6, 60, 0.7F, DIM, 60);
        ProbeValue v = ProbeValue.find(t.values(), t.graphKey());
        Component name = v == null ? Component.translatable("computing.pioneers.gui.no_value") : v.name();
        selector(g, mx, my, 50, 57, 164, name, ScreenTile.GRAPH_PREV, ScreenTile.GRAPH_NEXT);
        int[] size = t.size();
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.gui.wall", size[0], size[1]), 6, 78, 0.7F, DIM, 208);
        List<ProbeValue> values = t.values();
        for (int i = 0; i < Math.min(3, values.size()); i++) {
            ProbeValue pv = values.get(i);
            GuiDraw.text(g, font, pv.name(), 6, 90 + i * 10, 0.65F, DIM, 120);
            GuiDraw.right(g, font, pv.text(), 214, 90 + i * 10, 0.65F, TEXT);
        }
    }
}
