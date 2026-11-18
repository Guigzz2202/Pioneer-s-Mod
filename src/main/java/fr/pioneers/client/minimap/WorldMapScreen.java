package fr.pioneers.client.minimap;

import fr.pioneers.client.PiKeys;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.logistics.robotics.client.RoboticsScreen;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.10.3 : carte plein ecran (touche N) : memes calques que la minimap, deplacement a la souris (glisser), zoom a la molette autour du
 * pointeur, nord en haut. Barre laterale : interrupteurs des calques, liste des waypoints de la dimension (clic : centrer, oeil :
 * visible ou non, crayon : editer), nouveau waypoint ici ou au centre. Clic droit sur la carte : waypoint a cet endroit ; clic sur un
 * waypoint : edition. Ne montre que ce que le client a vu (ou garde sur le disque) : le reste est sombre.
 */
public final class WorldMapScreen extends RoboticsScreen {

    private static final int BAR = 18, SIDE = 156;
    private final MapRenderer render = new MapRenderer();
    private final MapRenderer.Opts opts = new MapRenderer.Opts();
    private double centerX, centerZ;
    private boolean follow = true, dragging, moved;
    private double dragX, dragY;
    private int listScroll, layersScroll;
    private boolean legend;
    private int mapX0, mapY0, mapX1, mapY1;

    public WorldMapScreen(@Nullable Screen parent) {
        super(Component.translatable("minimap.pioneers.map.title"), parent, 10_000, 10_000);
        drawPanel = false;
    }

    @Override
    protected void init() {
        super.init();
        if (minecraft != null && minecraft.player != null && follow) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
        MinimapClient.mapOpen = true;
    }

    @Override
    public void removed() {
        MinimapClient.mapOpen = false;
        MinimapConfig.save();
        super.removed();
    }

    private float scale() {
        return MinimapConfig.ZOOMS[Math.max(0, Math.min(MinimapConfig.ZOOMS.length - 1, MinimapConfig.num(MinimapConfig.MAP_ZOOM, 2)))];
    }

    @Override
    protected void drawContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (follow) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
        mapX0 = 0;
        mapY0 = BAR;
        mapX1 = width - SIDE;
        mapY1 = height;
        g.fill(0, 0, width, height, 0xFF0B1016);
        MapMath m = render.m;
        m.project(centerX, centerZ, scale(), 0, (mapX0 + mapX1) / 2F, (mapY0 + mapY1) / 2F);
        m.viewRect(mapX0, mapY0, mapX1, mapY1);
        render.rotation(0);
        opts.full = true;
        opts.edge = false;
        boolean overMap = mouseX >= mapX0 && mouseX < mapX1 && mouseY >= mapY0 && mouseY < mapY1;
        opts.mouseX = overMap ? mouseX : Integer.MIN_VALUE;
        opts.mouseY = mouseY;
        render.draw(g, MinimapClient.S, 1F, opts);
        topBar(g, mouseX, mouseY);
        side(g, mouseX, mouseY);
        status(g, mouseX, mouseY, overMap);
        if (legend) {
            legend(g);
        }
    }

    private void topBar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, BAR, D_BAR);
        g.fill(0, BAR - 1, width, BAR, 0xFF000000 | MinimapHud.accent());
        int x = width - SIDE - 196;
        int tw = Math.min(font.width(title), 120);
        GuiDraw.text(g, font, title, 6, 5, 1F, D_TEXT, 120);
        //dimension (cle) entre le titre et les boutons
        GuiDraw.text(g, font, Component.literal(MinimapClient.S.dim), 6 + tw + 8, 6.5F, 0.7F, D_DIM, Math.max(10, x - 6 - tw - 8 - 6));
        button(g, mx, my, x, 3, 18, 12, Component.literal("-"), MinimapConfig.num(MinimapConfig.MAP_ZOOM, 2) > 0, () -> zoomAt(-1, centerScreenX(),
              centerScreenY()));
        GuiDraw.center(g, font, Component.literal(scaleText(scale())), x + 34, 6, 0.7F, D_TEXT, 30);
        button(g, mx, my, x + 50, 3, 18, 12, Component.literal("+"), MinimapConfig.num(MinimapConfig.MAP_ZOOM, 2) < MinimapConfig.ZOOMS.length - 1,
              () -> zoomAt(1, centerScreenX(), centerScreenY()));
        button(g, mx, my, x + 72, 3, 56, 12, Component.translatable("minimap.pioneers.map.center"), !follow, () -> follow = true);
        button(g, mx, my, x + 132, 3, 60, 12, Component.translatable("minimap.pioneers.map.legend"), true, () -> legend = !legend);
    }

    static String scaleText(float s) {
        return s >= 1 ? "x" + (s == (int) s ? Integer.toString((int) s) : Float.toString(s)) : "1:" + Math.round(1 / s);
    }

    private float centerScreenX() {
        return (mapX0 + mapX1) / 2F;
    }

    private float centerScreenY() {
        return (mapY0 + mapY1) / 2F;
    }

    // ------------------------------------------------------------------ barre laterale
    private void side(GuiGraphics g, int mx, int my) {
        int x0 = width - SIDE, w = SIDE - 10;
        g.fill(x0, BAR, width, height, D_PANEL);
        g.fill(x0, BAR, x0 + 1, height, D_LINE);
        int x = x0 + 6;
        int y = BAR + 5;
        primary(g, mx, my, x, y, w / 2 - 2, 14, Component.translatable("minimap.pioneers.map.add_here"), true, () -> {
            Waypoint wp = MinimapClient.newAt(minecraft.player.getBlockX(), minecraft.player.getBlockY(), minecraft.player.getBlockZ());
            minecraft.setScreen(new WaypointEditScreen(this, null, wp));
        });
        button(g, mx, my, x + w / 2 + 2, y, w / 2 - 2, 14, Component.translatable("minimap.pioneers.map.settings"), true,
              () -> minecraft.setScreen(new MinimapSettingsScreen(this)));
        y += 20;
        // calques (deux colonnes)
        y = section(g, x, y, w, Component.translatable("minimap.pioneers.settings.layers"));
        Object[][] layers = layerToggles();
        int col = w / 2;
        for (int i = 0; i < layers.length; i++) {
            int lx = x + (i % 2) * col, ly = y + (i / 2) * 11;
            ForgeConfigSpec.BooleanValue v = (ForgeConfigSpec.BooleanValue) layers[i][0];
            boolean on = MinimapConfig.on(v, true);
            toggle(g, mx, my, lx, ly, col - 2, Component.translatable((String) layers[i][1]), on, () -> MinimapConfig.set(v, !on));
        }
        y += (layers.length + 1) / 2 * 11 + 4;
        // waypoints
        y = section(g, x, y, w, Component.translatable("minimap.pioneers.map.waypoints"));
        List<Waypoint> list = Waypoints.sorted(MinimapClient.S.dim, minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        int listTop = y, listBottom = height - 6, rowH = 13;
        int maxScroll = Math.max(0, list.size() * rowH - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));
        if (list.isEmpty()) {
            wrapped(g, Component.translatable("minimap.pioneers.map.no_waypoint"), x, y + 2, 0.7F, D_DIM, w, 4);
            return;
        }
        beginClip(g, x0 + 1, listTop, width, listBottom);
        int ry = listTop - listScroll;
        for (Waypoint wp : list) {
            if (ry + rowH >= listTop && ry <= listBottom) {
                row(g, mx, my, x, ry, w, wp);
            }
            ry += rowH;
        }
        endClip(g);
    }

    private void row(GuiGraphics g, int mx, int my, int x, int y, int w, Waypoint wp) {
        //1.0.4-beta (lot 29) : bouton « TP » des administrateurs (operateurs de niveau 2) avant « o » et « ... » ; lot 30 : grise hors du
        //creatif et du spectateur (jamais en survie ni en aventure), avec l'explication
        int tp = WaypointEditScreen.canTeleport(minecraft) ? 14 : 0;
        boolean hover = over(mx, my, x, y, w - 30 - tp, 12);
        g.fill(x, y, x + w, y + 12, hover ? 0xFF222B34 : 0xFF182028);
        Icons.draw(g, wp.death ? Icons.WP_DEATH : Icons.waypoint(wp.icon), x + 6, y + 6, wp.color, wp.visible ? 1F : 0.35F);
        int dist = (int) Math.round(wp.distanceTo(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()));
        Component d = Component.literal(dist + " m");
        int dw = Math.round(font.width(d) * 0.65F);
        GuiDraw.text(g, font, Component.literal(wp.name), x + 13, y + 3, 0.7F, wp.visible ? D_TEXT : D_FAINT, w - 13 - 30 - tp - dw - 4);
        GuiDraw.right(g, font, d, x + w - 30 - tp, y + 3.5F, 0.65F, D_DIM);
        click(x, y, w - 30 - tp, 12, () -> {
            follow = false;
            centerX = wp.x + 0.5;
            centerZ = wp.z + 0.5;
        });
        tooltip(mx, my, x, y, w - 30 - tp, 12, Component.literal(wp.name), Component.literal(wp.x + ", " + wp.y + ", " + wp.z),
              Component.translatable("minimap.pioneers.map.click_center"));
        if (tp > 0) {
            boolean mode = WaypointEditScreen.teleportMode(minecraft);
            button(g, mx, my, x + w - 42, y, 13, 12, Component.literal("TP"), mode, () -> WaypointEditScreen.teleportTo(minecraft, wp));
            tooltip(mx, my, x + w - 42, y, 13, 12, Component.translatable(mode ? "minimap.pioneers.edit.teleport_help" : "minimap.pioneers.tp.survival"));
        }
        button(g, mx, my, x + w - 28, y, 13, 12, Component.literal(wp.visible ? "o" : "-"), true, () -> {
            wp.visible = !wp.visible;
            Waypoints.save();
        });
        tooltip(mx, my, x + w - 28, y, 13, 12, Component.translatable(wp.visible ? "minimap.pioneers.map.hide" : "minimap.pioneers.map.show"));
        button(g, mx, my, x + w - 14, y, 14, 12, Component.literal("..."), true, () -> minecraft.setScreen(new WaypointEditScreen(this, wp, wp.copy())));
        tooltip(mx, my, x + w - 14, y, 14, 12, Component.translatable("minimap.pioneers.map.edit"));
    }

    /** Interrupteurs des calques : (valeur, cle de langue). */
    static Object[][] layerToggles() {
        return new Object[][]{{MinimapConfig.L_CLAIMS, "minimap.pioneers.layer.claims"}, {MinimapConfig.L_RAIL, "minimap.pioneers.layer.rail"},
              {MinimapConfig.L_STATIONS, "minimap.pioneers.layer.stations"}, {MinimapConfig.L_SEA, "minimap.pioneers.layer.sea"},
              {MinimapConfig.L_ROADS, "minimap.pioneers.layer.roads"}, {MinimapConfig.L_ROBOTICS, "minimap.pioneers.layer.robotics"},
              {MinimapConfig.L_POINTS, "minimap.pioneers.layer.points"}, {MinimapConfig.L_WAYPOINTS, "minimap.pioneers.layer.waypoints"},
              {MinimapConfig.L_ENTITIES, "minimap.pioneers.layer.entities"}, {MinimapConfig.L_PLAYERS, "minimap.pioneers.layer.players"},
              {MinimapConfig.L_MATES, "minimap.pioneers.layer.mates"}, {MinimapConfig.L_GRID, "minimap.pioneers.layer.grid"}};
    }

    // ------------------------------------------------------------------ bas de la carte, legende
    private void status(GuiGraphics g, int mx, int my, boolean overMap) {
        MapMath m = render.m;
        Component text;
        if (overMap) {
            int wx = (int) Math.floor(m.worldX(mx, my)), wz = (int) Math.floor(m.worldZ(mx, my));
            text = Component.translatable("minimap.pioneers.map.cursor", wx, wz);
        } else {
            text = Component.translatable("minimap.pioneers.map.help");
        }
        int w = Math.min(mapX1 - 8, Math.round(font.width(text) * 0.7F) + 8);
        g.fill(4, height - 14, 4 + w, height - 3, 0xC0141A20);
        GuiDraw.text(g, font, text, 8, height - 11, 0.7F, D_TEXT, w - 6);
        if (render.hoveredText != null && overMap) {
            tooltip(List.of(render.hoveredText));
        }
    }

    private void legend(GuiGraphics g) {
        int[][] rows = {{Icons.STATION, 0}, {Icons.HARBOR, 1}, {Icons.POST, 2}, {Icons.SUBSTATION, 3}, {Icons.BUOY, 4}, {Icons.HUB, 5},
              {Icons.RELAY, 6}, {Icons.TERMINAL, 7}, {Icons.POINT, 8}, {Icons.CHEST, 9}, {Icons.TUBE, 10}, {Icons.TRAIN, 11}, {Icons.SHIP, 12},
              {Icons.ROBOT, 13}, {Icons.CART, 14}};
        String[] keys = {"poi.station", "poi.harbor", "poi.rail_post", "poi.substation", "poi.sea_beacon", "poi.hub", "poi.relay", "poi.terminal",
              "poi.point", "poi.chest", "poi.tube_station", "entity.train", "entity.ship", "entity.robot", "entity.cart"};
        int w = 150, h = rows.length * 11 + 8, x = 6, y = BAR + 6;
        g.fill(x, y, x + w, y + h, 0xE8141A20);
        g.renderOutline(x, y, w, h, 0xFF2E3A44);
        for (int i = 0; i < rows.length; i++) {
            Icons.draw(g, rows[i][0], x + 9, y + 9 + i * 11, 0xFFFFFF, 1F);
            GuiDraw.text(g, font, Component.translatable("minimap.pioneers." + keys[i]), x + 17, y + 6 + i * 11, 0.7F, D_TEXT, w - 20);
        }
    }

    // ------------------------------------------------------------------ souris et clavier
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean overMap = mx >= mapX0 && mx < mapX1 && my >= mapY0 && my < mapY1 && !(legend && mx < 156 && my < BAR + 180);
        if (overMap) {
            if (button == 0 && render.hovered instanceof Waypoint wp) {
                minecraft.setScreen(new WaypointEditScreen(this, wp, wp.copy()));
                return true;
            }
            if (button == 1) {
                int wx = (int) Math.floor(render.m.worldX((float) mx, (float) my)), wz = (int) Math.floor(render.m.worldZ((float) mx, (float) my));
                minecraft.setScreen(new WaypointEditScreen(this, null, MinimapClient.newAt(wx, surfaceY(wx, wz), wz)));
                return true;
            }
            if (button == 0) {
                dragging = true;
                moved = false;
                dragX = mx;
                dragY = my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Hauteur du sol si le chunk est connu du client, sinon celle du joueur. */
    private int surfaceY(int x, int z) {
        if (minecraft.level != null && minecraft.level.hasChunk(x >> 4, z >> 4)) {
            return minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
        return minecraft.player.getBlockY();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            double tx = mx - dragX, ty = my - dragY;
            if (moved || Math.abs(tx) + Math.abs(ty) > 2) {
                moved = true;
                follow = false;
                centerX -= tx / scale();
                centerZ -= ty / scale();
                dragX = mx;
                dragY = my;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= mapX0 && mx < mapX1 && my >= mapY0 && my < mapY1) {
            zoomAt(delta > 0 ? 1 : -1, (float) mx, (float) my);
            return true;
        }
        if (mx >= width - SIDE) {
            listScroll -= (int) Math.round(delta * 13);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Zoom d'un cran en gardant le point sous le pointeur a sa place. */
    private void zoomAt(int step, float sx, float sy) {
        MapMath m = render.m;
        double wx = m.worldX(sx, sy), wz = m.worldZ(sx, sy);
        int z = Math.max(0, Math.min(MinimapConfig.ZOOMS.length - 1, MinimapConfig.num(MinimapConfig.MAP_ZOOM, 2) + step));
        MinimapConfig.set(MinimapConfig.MAP_ZOOM, z);
        float s = MinimapConfig.ZOOMS[z];
        if (step != 0 && (sx != centerScreenX() || sy != centerScreenY())) {
            follow = false;
            centerX = wx - (sx - centerScreenX()) / s;
            centerZ = wz - (sy - centerScreenY()) / s;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!editing() && PiKeys.WORLD_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
