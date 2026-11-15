package fr.pioneers.client.minimap;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.robotics.RoboticsScreen;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.10.3 : reglages de la minimap en jeu, sur deux colonnes qui defilent : affichage (taille, coin, decalage, forme, echelle, rotation,
 * opacite, cadre, textes), terrain, calques, entites, waypoints, couleurs, cache, touches (ecran des commandes du jeu). Chaque changement
 * s'applique tout de suite ; le fichier {@code pioneers-client.toml} est ecrit a la fermeture.
 * <p>
 * 1.0.2 : rendu des waypoints dans le monde « Balise / Point / Invisible » ({@link MinimapConfig#W_BEACON_STYLE}) a la place de
 * l'interrupteur des balises.
 */
public final class MinimapSettingsScreen extends RoboticsScreen {

    private static final int[] COLORS = {0xE5484D, 0xF07A3C, 0xF2D45C, 0x6CD46C, 0x3FD0A0, 0x5FB8E8, 0x3E7FD8, 0xC86CE0, 0xFF8FC8, 0xFFFFFF,
          0xC8CED6, 0xD8B070};
    private int scroll, contentH;

    public MinimapSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("minimap.pioneers.settings.title"), parent, 340, 250);
    }

    @Override
    public void removed() {
        MinimapConfig.save();
        super.removed();
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float partialTick) {
        int top0 = top + 20, bottom = top + panelHeight - 22;
        int colW = (panelWidth - 24) / 2;
        int xa = left + 8, xb = left + 16 + colW;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - (bottom - top0)), scroll));
        beginClip(g, left + 2, top0, left + panelWidth - 2, bottom);
        int ya = top0 + 2 - scroll, yb = ya;
        // ---- colonne A : affichage, terrain
        ya = section(g, xa, ya, colW, Component.translatable("minimap.pioneers.settings.display"));
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.ENABLED, "minimap.pioneers.settings.enabled", true);
        ya = label(g, xa, ya, colW, "minimap.pioneers.settings.shape");
        MinimapConfig.Shape shape = MinimapConfig.pick(MinimapConfig.SHAPE, MinimapConfig.Shape.SQUARE);
        chip(g, mx, my, xa, ya, colW / 2 - 2, 12, Component.translatable("minimap.pioneers.settings.square"), shape == MinimapConfig.Shape.SQUARE,
              0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.SHAPE, MinimapConfig.Shape.SQUARE));
        chip(g, mx, my, xa + colW / 2, ya, colW / 2, 12, Component.translatable("minimap.pioneers.settings.round"), shape == MinimapConfig.Shape.ROUND,
              0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.SHAPE, MinimapConfig.Shape.ROUND));
        ya += 16;
        ya = label(g, xa, ya, colW, "minimap.pioneers.settings.corner");
        MinimapConfig.Corner corner = MinimapConfig.pick(MinimapConfig.CORNER, MinimapConfig.DEFAULT_CORNER);
        MinimapConfig.Corner[] corners = MinimapConfig.Corner.values();
        for (int i = 0; i < corners.length; i++) {
            MinimapConfig.Corner c = corners[i];
            int cw = colW / 4, cxx = xa + i * cw;
            chip(g, mx, my, cxx, ya, cw - 2, 12, Component.empty(), corner == c, 0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.CORNER, c));
            //petit ecran : la minimap dans son coin
            int sx = cxx + (cw - 2) / 2 - 6, sy = ya + 2;
            g.renderOutline(sx, sy, 12, 8, corner == c ? 0xFFFFFFFF : 0xFF8FA3A0);
            int qx = i % 2 == 0 ? sx + 1 : sx + 7, qy = i < 2 ? sy + 1 : sy + 4;
            g.fill(qx, qy, qx + 4, qy + 3, corner == c ? 0xFFFFFFFF : 0xFF8FA3A0);
            tooltip(mx, my, cxx, ya, cw - 2, 12, Component.translatable("minimap.pioneers.settings.corner." + c.name().toLowerCase(Locale.ROOT)));
        }
        ya += 16;
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.size", MinimapConfig.SIZE, MinimapConfig.DEFAULT_SIZE, 48, 256, 16, "");
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.offset_x", MinimapConfig.OFFSET_X, 6, 0, 400, 10, "");
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.offset_y", MinimapConfig.OFFSET_Y, 6, 0, 400, 10, "");
        int z = MinimapConfig.num(MinimapConfig.ZOOM, 3);
        GuiDraw.text(g, font, Component.translatable("minimap.pioneers.settings.zoom_label"), xa, ya + 3, 0.7F, D_TEXT, colW - 72);
        //echelle : moins / valeur lisible / plus (l'indice de la table ne parle a personne)
        button(g, mx, my, xa + colW - 70, ya, 11, 12, Component.literal("-"), z > 0, () -> MinimapConfig.set(MinimapConfig.ZOOM, z - 1));
        GuiDraw.center(g, font, Component.literal(WorldMapScreen.scaleText(MinimapConfig.ZOOMS[z])), xa + colW - 35, ya + 3, 0.75F, D_TEXT, 44);
        button(g, mx, my, xa + colW - 11, ya, 11, 12, Component.literal("+"), z < MinimapConfig.ZOOMS.length - 1,
              () -> MinimapConfig.set(MinimapConfig.ZOOM, z + 1));
        ya += 15;
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.ROTATE, "minimap.pioneers.settings.rotate", false);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.NORTH_MARK, "minimap.pioneers.settings.north", true);
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.opacity", MinimapConfig.OPACITY, 100, 10, 100, 10, " %");
        ya = label(g, xa, ya, colW, "minimap.pioneers.settings.frame");
        MinimapConfig.Frame frame = MinimapConfig.pick(MinimapConfig.FRAME, MinimapConfig.Frame.STEEL);
        MinimapConfig.Frame[] frames = MinimapConfig.Frame.values();
        for (int i = 0; i < frames.length; i++) {
            MinimapConfig.Frame f = frames[i];
            int fw = colW / 5;
            int col = f == MinimapConfig.Frame.STEEL ? MinimapHud.STEEL : MinimapHud.TIER[f.ordinal() - 1];
            chip(g, mx, my, xa + i * fw, ya, fw - 2, 12, Component.translatable("minimap.pioneers.settings.frame." + f.name().toLowerCase(Locale.ROOT)),
                  frame == f, col, () -> MinimapConfig.set(MinimapConfig.FRAME, f));
        }
        ya += 16;
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.text_scale", MinimapConfig.TEXT_SCALE, MinimapConfig.DEFAULT_TEXT, 50, 150, 10, " %");
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.COORDS, "minimap.pioneers.settings.coords", true);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.BIOME, "minimap.pioneers.settings.biome", true);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.TIME, "minimap.pioneers.settings.time", false);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.ASTRE, "minimap.pioneers.settings.astre", true);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.ENVIRONMENT, "minimap.pioneers.settings.environment", true);
        ya += 4;
        ya = section(g, xa, ya, colW, Component.translatable("minimap.pioneers.settings.terrain"));
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.SHADING, "minimap.pioneers.settings.shading", true);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.BIOME_TINT, "minimap.pioneers.settings.biome_tint", true);
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.WATER_DEPTH, "minimap.pioneers.settings.water_depth", true);
        ya = label(g, xa, ya, colW, "minimap.pioneers.settings.ceiling");
        MinimapConfig.Ceiling ceil = MinimapConfig.pick(MinimapConfig.CEILING, MinimapConfig.Ceiling.IGNORE);
        chip(g, mx, my, xa, ya, colW / 2 - 2, 12, Component.translatable("minimap.pioneers.settings.ceiling.ignore"), ceil == MinimapConfig.Ceiling.IGNORE,
              0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.CEILING, MinimapConfig.Ceiling.IGNORE));
        chip(g, mx, my, xa + colW / 2, ya, colW / 2, 12, Component.translatable("minimap.pioneers.settings.ceiling.hide"), ceil == MinimapConfig.Ceiling.HIDE,
              0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.CEILING, MinimapConfig.Ceiling.HIDE));
        ya += 16;
        ya = section(g, xa, ya, colW, Component.translatable("minimap.pioneers.settings.cache"));
        ya = toggleRow(g, mx, my, xa, ya, colW, MinimapConfig.DISK_CACHE, "minimap.pioneers.settings.disk", true);
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.chunks_per_tick", MinimapConfig.CHUNKS_PER_TICK, 3, 1, 16, 4, "");
        ya = stepRow(g, mx, my, xa, ya, colW, "minimap.pioneers.settings.max_regions", MinimapConfig.MAX_REGIONS, 96, 16, 512, 32, "");
        //mesure en jeu du releve (cout reel par chunk depuis l'arrivee dans le monde)
        MinimapState st = MinimapClient.S;
        long us = st.scanned == 0 ? 0 : st.scanNanos / st.scanned / 1000;
        MapStore store = st.store;
        GuiDraw.text(g, font, Component.translatable("minimap.pioneers.settings.measure", us, st.scanned, store == null ? 0 : store.size()), xa, ya + 1,
              0.62F, D_FAINT, colW);
        ya += 10;
        // ---- colonne B : calques, entites, waypoints, couleurs, touches
        yb = section(g, xb, yb, colW, Component.translatable("minimap.pioneers.settings.layers"));
        for (Object[] row : WorldMapScreen.layerToggles()) {
            yb = toggleRow(g, mx, my, xb, yb, colW, (ForgeConfigSpec.BooleanValue) row[0], (String) row[1], row[0] != MinimapConfig.L_GRID);
        }
        yb += 4;
        yb = section(g, xb, yb, colW, Component.translatable("minimap.pioneers.settings.entities"));
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_HOSTILE, "minimap.pioneers.settings.hostile", true);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_PASSIVE, "minimap.pioneers.settings.passive", true);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_ITEMS, "minimap.pioneers.settings.items", false);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_VEHICLES, "minimap.pioneers.settings.vehicles", true);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_ROBOTS, "minimap.pioneers.settings.robots", true);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_NAMES, "minimap.pioneers.settings.names", false);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.E_PLAYER_NAMES, "minimap.pioneers.settings.player_names", true);
        yb = stepRow(g, mx, my, xb, yb, colW, "minimap.pioneers.settings.radius", MinimapConfig.E_RADIUS, 64, 8, 160, 16, "");
        yb = stepRow(g, mx, my, xb, yb, colW, "minimap.pioneers.settings.height", MinimapConfig.E_HEIGHT, 32, 4, 384, 16, "");
        yb += 4;
        yb = section(g, xb, yb, colW, Component.translatable("minimap.pioneers.settings.waypoints"));
        //1.0.2 : rendu dans le monde : Balise / Point / Invisible (chaque waypoint peut le remplacer dans sa fiche)
        yb = label(g, xb, yb, colW, "minimap.pioneers.settings.beacon_style");
        MinimapConfig.BeaconStyle style = MinimapConfig.beaconStyle();
        MinimapConfig.BeaconStyle[] styles = MinimapConfig.BeaconStyle.values();
        for (int i = 0; i < styles.length; i++) {
            MinimapConfig.BeaconStyle bs = styles[i];
            int sw = colW / styles.length, sx = xb + i * sw;
            String key = "minimap.pioneers.style." + bs.name().toLowerCase(Locale.ROOT);
            chip(g, mx, my, sx, yb, sw - 2, 12, Component.translatable(key), style == bs, 0x3FD0A0, () -> MinimapConfig.set(MinimapConfig.W_BEACON_STYLE, bs));
            tooltip(mx, my, sx, yb, sw - 2, 12, Component.translatable(key + ".tip"));
        }
        yb += 16;
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.W_DISTANCE, "minimap.pioneers.settings.distance", true);
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.W_NAMES, "minimap.pioneers.settings.waypoint_names", false);
        yb = stepRow(g, mx, my, xb, yb, colW, "minimap.pioneers.settings.beacon_range", MinimapConfig.W_BEACON_RANGE, 0, 0, 100_000, 500, "");
        yb = toggleRow(g, mx, my, xb, yb, colW, MinimapConfig.W_DEATHS, "minimap.pioneers.settings.deaths", true);
        yb = stepRow(g, mx, my, xb, yb, colW, "minimap.pioneers.settings.death_keep", MinimapConfig.W_DEATH_KEEP, 3, 1, 20, 5, "");
        yb += 4;
        yb = section(g, xb, yb, colW, Component.translatable("minimap.pioneers.settings.colors"));
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.self", MinimapConfig.C_SELF, 0x3FD0A0);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.player", MinimapConfig.C_PLAYER, 0xFFFFFF);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.hostile", MinimapConfig.C_HOSTILE, 0xE5484D);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.passive", MinimapConfig.C_PASSIVE, 0x6CD46C);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.item", MinimapConfig.C_ITEM, 0xF2D45C);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.rail", MinimapConfig.C_RAIL, 0xC8CED6);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.road", MinimapConfig.C_ROAD, 0xD8B070);
        yb = colorRow(g, xb, yb, colW, "minimap.pioneers.settings.color.text", MinimapConfig.C_TEXT, 0xE0F5EA);
        yb += 4;
        yb = section(g, xb, yb, colW, Component.translatable("minimap.pioneers.settings.keys"));
        button(g, mx, my, xb, yb, colW, 14, Component.translatable("minimap.pioneers.settings.keys_open"), true,
              () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
        yb += 18;
        endClip(g);
        contentH = Math.max(ya, yb) + scroll - top0;
        if (contentH > bottom - top0) {
            //barre de defilement
            int barH = Math.max(12, (bottom - top0) * (bottom - top0) / contentH);
            int barY = top0 + (bottom - top0 - barH) * scroll / Math.max(1, contentH - (bottom - top0));
            g.fill(left + panelWidth - 4, barY, left + panelWidth - 2, barY + barH, 0xFF3C4A57);
        }
        primary(g, mx, my, left + panelWidth - 80, top + panelHeight - 18, 72, 14, Component.translatable("gui.done"), true, this::onClose);
        GuiDraw.text(g, font, Component.translatable("minimap.pioneers.settings.hint"), left + 8, top + panelHeight - 14, 0.62F, D_FAINT,
              panelWidth - 96);
    }

    private int label(GuiGraphics g, int x, int y, int w, String key) {
        GuiDraw.text(g, font, Component.translatable(key), x, y, 0.7F, D_DIM, w);
        return y + 9;
    }

    private int toggleRow(GuiGraphics g, int mx, int my, int x, int y, int w, ForgeConfigSpec.BooleanValue v, String key, boolean def) {
        boolean on = MinimapConfig.on(v, def);
        toggle(g, mx, my, x, y + 2, w, Component.translatable(key), on, () -> MinimapConfig.set(v, !on));
        return y + 12;
    }

    private int stepRow(GuiGraphics g, int mx, int my, int x, int y, int w, String key, ForgeConfigSpec.IntValue v, int def, int min, int max, int big,
          String unit) {
        int value = MinimapConfig.num(v, def);
        GuiDraw.text(g, font, Component.translatable(key), x, y + 3, 0.7F, D_TEXT, w - 74);
        IntConsumer set = n -> MinimapConfig.set(v, n);
        stepper(g, mx, my, x + w - 70, y, 70, value, min, max, big, unit, set);
        return y + 15;
    }

    private int colorRow(GuiGraphics g, int x, int y, int w, String key, ForgeConfigSpec.IntValue v, int def) {
        int value = MinimapConfig.num(v, def);
        GuiDraw.text(g, font, Component.translatable(key), x, y + 2, 0.7F, D_TEXT, 54);
        int sw = Math.min(9, (w - 58) / COLORS.length);
        for (int i = 0; i < COLORS.length; i++) {
            int c = COLORS[i], cx = x + 58 + i * sw;
            boolean sel = value == c;
            g.fill(cx, y, cx + sw - 1, y + 9, sel ? 0xFFFFFFFF : 0xFF2C3640);
            g.fill(cx + 1, y + 1, cx + sw - 2, y + 8, 0xFF000000 | c);
            click(cx, y, sw - 1, 9, () -> MinimapConfig.set(v, c));
        }
        return y + 12;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (editing()) {
            commitEdit();
        }
        scroll -= (int) Math.round(delta * 20);
        return true;
    }
}
