package fr.pioneers.client.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.client.clan.ui.ClanMapAccess;
import fr.pioneers.client.rail.ClientRailMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * 1.10.3 : dessin d'une vue de carte (minimap ou carte plein ecran) : fond, regions du terrain (une texture par region, decoupee par le
 * contour de la vue et dessinee en eventail), calques des voies et des routes, grille, claims, voies et routes maritimes connues de la
 * carte O, lieux, waypoints, entites, membres du clan, joueur. La projection et le contour sont dans {@link #m}, poses par l'appelant.
 * Tout se recalcule a partir de donnees deja pretes : le cout par image est celui du dessin.
 */
final class MapRenderer {

    final MapMath m = new MapMath();
    final MinimapLayers layers = new MinimapLayers();
    private final List<Poi> pois = new ArrayList<>();
    private final Matrix4f identity = new Matrix4f();
    private Matrix4f mat = identity;
    private BufferBuilder buf;
    private float alpha = 1;
    private static final float[] ARROW_X = {0, 0.72F, 0, -0.72F}, ARROW_Y = {-1, 0.78F, 0.38F, 0.78F};
    private final float[] arrowX = new float[4], arrowY = new float[4];

    /** Ce qui est sous la souris (carte plein ecran). */
    @Nullable
    Object hovered;
    @Nullable
    Component hoveredText;
    private float hoverDist;

    /** Options du dessin. */
    static final class Opts {
        boolean full;
        /** Minimap : waypoints hors du cadre ramenes au bord ; demi-cote (carre) ou rayon (rond). */
        boolean edge, round;
        float half;
        int mouseX = Integer.MIN_VALUE, mouseY = Integer.MIN_VALUE;
    }

    void draw(GuiGraphics g, MinimapState s, float opacity, Opts o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || s.store == null) {
            return;
        }
        MinimapLayers l = layers.read();
        alpha = opacity;
        hovered = null;
        hoveredText = null;
        hoverDist = 7;
        g.flush();
        mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        // fond
        colors();
        int bg = argb(0xFF0B1016, 1);
        fan(m.viewX, m.viewY, m.viewN, bg);
        flushColors();
        // etendue visible du monde
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < m.viewN; i++) {
            double wx = m.worldX(m.viewX[i], m.viewY[i]), wz = m.worldZ(m.viewX[i], m.viewY[i]);
            minX = Math.min(minX, wx);
            maxX = Math.max(maxX, wx);
            minZ = Math.min(minZ, wz);
            maxZ = Math.max(maxZ, wz);
        }
        long now = Util.getMillis();
        int rx0 = (int) Math.floor(minX / 256), rx1 = (int) Math.floor(maxX / 256), rz0 = (int) Math.floor(minZ / 256),
              rz1 = (int) Math.floor(maxZ / 256);
        pois.clear();
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                MapRegion r = s.store.region(rx, rz, false, now);
                if (r == null) {
                    continue;
                }
                if (r.terrainTex != null) {
                    textured(r.terrainTex, rx, rz);
                    if (l.rail && r.railTex != null) {
                        textured(r.railTex, rx, rz);
                    }
                    if (l.roads && r.roadTex != null) {
                        textured(r.roadTex, rx, rz);
                    }
                }
                r.collect(pois);
            }
        }
        // grille, claims, voies et routes maritimes (traits et aplats)
        colors();
        if (l.grid && m.scale >= 0.5F) {
            int line = argb(0x2CFFFFFF, 1);
            for (int x = (int) Math.floor(minX / 16) * 16; x <= maxX; x += 16) {
                segment(x, minZ, x, maxZ, 1, line);
            }
            for (int z = (int) Math.floor(minZ / 16) * 16; z <= maxZ; z += 16) {
                segment(minX, z, maxX, z, 1, line);
            }
        }
        String dim = mc.level.dimension().location().toString();
        if (l.claims) {
            ClaimLayer.refresh();
        }
        if (l.claims && dim.equals(ClaimLayer.dim)) {
            float pulse = 0.55F + 0.45F * (float) Math.sin(now / 260.0);
            for (ClaimLayer.Band b : ClaimLayer.bands) {
                if (b.x1() < minX || b.x0() > maxX || b.z1() < minZ || b.z0() > maxZ) {
                    continue;
                }
                int c = b.argb();
                if (b.disputed()) {
                    c = mixArgb(c, 0x90E5484D, pulse);
                }
                if (m.clipWorldRect(b.x0(), b.z0(), b.x1(), b.z1()) > 0) {
                    fan(m.outX, m.outY, m.outN, argb(c, 1));
                }
            }
            //1.12.1 (F1) : liseré sombre sous chaque frontiere, puis le trait a la couleur du clan
            int halo = argb(0xC0101418, 1);
            for (ClaimLayer.Edge e : ClaimLayer.edges) {
                segment(e.x0(), e.z0(), e.x1(), e.z1(), 3, halo);
            }
            for (ClaimLayer.Edge e : ClaimLayer.edges) {
                segment(e.x0(), e.z0(), e.x1(), e.z1(), 1, argb(e.argb(), 1));
            }
        }
        boolean railKnown = dim.equals(ClientRailMap.dim);
        if (railKnown && l.rail && m.scale < 1.5F) {
            //echelles larges : les voies connues de la carte O en traits (un pixel par bloc ne se voit plus)
            int c = argb(0xFF000000 | l.cRail, 1);
            for (ClientRailMap.Segment sg : ClientRailMap.segments) {
                if (Math.max(sg.x0(), sg.x1()) < minX || Math.min(sg.x0(), sg.x1()) > maxX || Math.max(sg.z0(), sg.z1()) < minZ
                    || Math.min(sg.z0(), sg.z1()) > maxZ) {
                    continue;
                }
                segment(sg.x0(), sg.z0(), sg.x1(), sg.z1(), 1, c);
            }
        }
        if (railKnown && l.sea) {
            int lane = argb(0xB07FC8F0, 1), route = argb(0xD0F2D45C, 1);
            for (float[] p : ClientRailMap.seaLanes) {
                polyline(p, lane);
            }
            for (float[] p : ClientRailMap.seaRoutes) {
                polyline(p, route);
            }
        }
        flushColors();
        // lieux
        Font font = mc.font;
        for (Poi p : pois) {
            if (!p.kind().shown(l) || m.scale < p.kind().minScale()) {
                continue;
            }
            float sx = m.screenX(p.x() + 0.5, p.z() + 0.5), sy = m.screenY(p.x() + 0.5, p.z() + 0.5);
            if (!m.inside(sx, sy)) {
                continue;
            }
            int tint = p.kind() == Poi.Kind.POINT ? p.color() : 0xFFFFFF;
            Icons.draw(g, p.kind().icon, sx, sy, tint, alpha);
            if (!p.name().isEmpty() && (o.full || m.scale >= 2)) {
                label(g, font, p.name(), sx, sy + 5, 0xFFE0E6EC, o);
            }
            if (o.mouseX != Integer.MIN_VALUE) {
                hover(o, sx, sy, p, Component.translatable("minimap.pioneers.poi." + p.kind().name().toLowerCase(Locale.ROOT))
                      .append(p.name().isEmpty() ? "" : " : " + p.name()));
            }
        }
        if (railKnown && l.stations) {
            for (ClientRailMap.MapStation st : ClientRailMap.stations) {
                double wx = st.pos().getX() + 0.5, wz = st.pos().getZ() + 0.5;
                float sx = m.screenX(wx, wz), sy = m.screenY(wx, wz);
                if (!m.inside(sx, sy) || near(st.pos().getX(), st.pos().getZ())) {
                    continue;
                }
                Icons.draw(g, st.harbor() ? Icons.HARBOR : Icons.STATION, sx, sy, 0xFFFFFF, alpha);
                if (o.full || m.scale >= 2) {
                    label(g, font, st.name(), sx, sy + 5, 0xFFE0E6EC, o);
                }
                if (o.mouseX != Integer.MIN_VALUE) {
                    hover(o, sx, sy, st, Component.literal(st.name()));
                }
            }
        }
        // waypoints
        if (l.waypoints) {
            for (Waypoint w : Waypoints.cached(dim)) {
                if (!w.visible) {
                    continue;
                }
                float sx = m.screenX(w.x + 0.5, w.z + 0.5), sy = m.screenY(w.x + 0.5, w.z + 0.5);
                boolean in = m.inside(sx, sy);
                if (!in && !o.edge) {
                    continue;
                }
                if (!in) {
                    float dx = sx - m.ox, dy = sy - m.oy, d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 1e-3) {
                        continue;
                    }
                    float k;
                    if (o.round) {
                        k = (o.half - 4) / d;
                    } else {
                        k = (o.half - 4) / Math.max(Math.abs(dx), Math.abs(dy));
                    }
                    sx = m.ox + dx * k;
                    sy = m.oy + dy * k;
                }
                int icon = w.death ? Icons.WP_DEATH : Icons.waypoint(w.icon);
                Icons.draw(g, icon, sx, sy, w.color, in ? alpha : alpha * 0.75F);
                if (in && (o.full || l.waypointNames)) {
                    label(g, font, w.name, sx, sy + 5, 0xFF000000 | w.color, o);
                }
                if (o.mouseX != Integer.MIN_VALUE) {
                    hover(o, sx, sy, w, Component.literal(w.name + "  (" + w.x + ", " + w.y + ", " + w.z + ")"));
                }
            }
        }
        // entites et joueurs
        colors();
        for (EntityMarkers.Marker mk : EntityMarkers.markers) {
            float sx = m.screenX(mk.x(), mk.z()), sy = m.screenY(mk.x(), mk.z());
            if (!m.inside(sx, sy)) {
                continue;
            }
            switch (mk.type()) {
                case PLAYER -> arrow(sx, sy, MapMath.headingOnScreen(mk.yaw(), rotation()), 4.5F, 0xFF000000 | mk.color(), true);
                case ITEM -> square(sx, sy, 1, 0xFF000000 | mk.color());
                case HOSTILE, PASSIVE -> dot(sx, sy, 0xFF000000 | mk.color());
                default -> {
                }
            }
        }
        flushColors();
        for (EntityMarkers.Marker mk : EntityMarkers.markers) {
            float sx = m.screenX(mk.x(), mk.z()), sy = m.screenY(mk.x(), mk.z());
            if (!m.inside(sx, sy)) {
                continue;
            }
            int icon = switch (mk.type()) {
                case VEHICLE -> Icons.TRAIN;
                case SHIP -> Icons.SHIP;
                case CART -> Icons.CART;
                case ROBOT -> Icons.ROBOT;
                default -> -1;
            };
            if (icon >= 0) {
                Icons.draw(g, icon, sx, sy, 0xFFFFFF, alpha);
            }
            if (!mk.name().isEmpty()) {
                label(g, font, mk.name(), sx, sy + 4, 0xFF000000 | mk.color(), o);
            }
            if (mk.type() == EntityMarkers.Type.PLAYER && o.mouseX != Integer.MIN_VALUE) {
                hover(o, sx, sy, mk, Component.literal(mk.name()));
            }
        }
        if (l.mates && ClanMapAccess.matesDim().equals(dim)) {
            colors();
            List<ClanMapAccess.Mate> mates = ClanMapAccess.mates();
            List<String> drawn = new ArrayList<>();
            for (ClanMapAccess.Mate mt : mates) {
                if (visible(mt.name())) {
                    continue;
                }
                float sx = m.screenX(mt.x() + 0.5, mt.z() + 0.5), sy = m.screenY(mt.x() + 0.5, mt.z() + 0.5);
                if (!m.inside(sx, sy)) {
                    continue;
                }
                int c = 0xFF000000 | (mt.dye() < 0 ? l.cPlayer : ClanMapAccess.dyeRgb(mt.dye()));
                ring(sx, sy, c);
                drawn.add(mt.name());
            }
            flushColors();
            for (ClanMapAccess.Mate mt : mates) {
                if (!drawn.contains(mt.name())) {
                    continue;
                }
                float sx = m.screenX(mt.x() + 0.5, mt.z() + 0.5), sy = m.screenY(mt.x() + 0.5, mt.z() + 0.5);
                if (l.playerNames) {
                    label(g, font, mt.name(), sx, sy + 4, 0xFFB8C8C4, o);
                }
                if (o.mouseX != Integer.MIN_VALUE) {
                    hover(o, sx, sy, mt, Component.translatable("minimap.pioneers.mate", mt.name(), mt.x(), mt.y(), mt.z()));
                }
            }
        }
        // joueur
        colors();
        double px = mc.player.getX(), pz = mc.player.getZ();
        float sx = m.screenX(px, pz), sy = m.screenY(px, pz);
        if (m.inside(sx, sy)) {
            arrow(sx, sy, MapMath.headingOnScreen(mc.player.getYRot(), rotation()), 5.5F, 0xFF000000 | l.cSelf, false);
        }
        flushColors();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        mat = identity;
    }

    private float rotationValue;

    /** Rotation de la vue en cours (la passe l'appelant avec {@link #rotation(float)}). */
    private float rotation() {
        return rotationValue;
    }

    void rotation(float r) {
        rotationValue = r;
    }

    private boolean near(int x, int z) {
        for (Poi p : pois) {
            if (Math.abs(p.x() - x) <= 1 && Math.abs(p.z() - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean visible(String name) {
        for (EntityMarkers.Marker mk : EntityMarkers.markers) {
            if (mk.type() == EntityMarkers.Type.PLAYER && mk.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void hover(Opts o, float sx, float sy, Object what, Component text) {
        if (o.mouseX == Integer.MIN_VALUE) {
            return;
        }
        float d = (float) Math.hypot(sx - o.mouseX, sy - o.mouseY);
        if (d < hoverDist) {
            hoverDist = d;
            hovered = what;
            hoveredText = text;
        }
    }

    private void label(GuiGraphics g, Font font, String text, float x, float y, int color, Opts o) {
        if (text.isEmpty()) {
            return;
        }
        float scale = o.full ? 0.75F : 0.5F;
        int w = font.width(text);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int a = Math.round(alpha * 255);
        g.fill(-w / 2 - 1, -1, w / 2 + 1 + (w & 1), 8, (Math.round(alpha * 0x90) << 24) | 0x0B1016);
        g.drawString(font, text, -w / 2, 0, (a << 24) | (color & 0xFFFFFF), false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ primitives
    private int argb(int c, float k) {
        int a = Math.round(((c >>> 24) & 255) * alpha * k);
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static int mixArgb(int a, int b, float t) {
        int aa = Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        return aa << 24 | SurfaceShade.mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }

    private void colors() {
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private void flushColors() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buf.end());
    }

    private void vertex(float x, float y, int c) {
        buf.vertex(mat, x, y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    private void fan(float[] xs, float[] ys, int n, int c) {
        for (int i = 1; i + 1 < n; i++) {
            vertex(xs[0], ys[0], c);
            vertex(xs[i], ys[i], c);
            vertex(xs[i + 1], ys[i + 1], c);
        }
    }

    private void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        vertex(x0, y0, c);
        vertex(x1, y1, c);
        vertex(x2, y2, c);
        vertex(x0, y0, c);
        vertex(x2, y2, c);
        vertex(x3, y3, c);
    }

    private void segment(double wx0, double wz0, double wx1, double wz1, float width, int c) {
        if (!m.clipWorldSegment(wx0, wz0, wx1, wz1)) {
            return;
        }
        float dx = m.sx1 - m.sx0, dy = m.sy1 - m.sy0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4) {
            return;
        }
        float nx = -dy / len * width / 2, ny = dx / len * width / 2;
        quad(m.sx0 + nx, m.sy0 + ny, m.sx1 + nx, m.sy1 + ny, m.sx1 - nx, m.sy1 - ny, m.sx0 - nx, m.sy0 - ny, c);
    }

    private void polyline(float[] xz, int c) {
        for (int i = 0; i + 3 < xz.length; i += 2) {
            segment(xz[i], xz[i + 1], xz[i + 2], xz[i + 3], 1, c);
        }
    }

    private void dot(float x, float y, int c) {
        int edge = argb(0xFF0B1016, 1), fill = argb(c, 1);
        quad(x - 2, y - 2, x + 2, y - 2, x + 2, y + 2, x - 2, y + 2, edge);
        quad(x - 1.25F, y - 1.25F, x + 1.25F, y - 1.25F, x + 1.25F, y + 1.25F, x - 1.25F, y + 1.25F, fill);
    }

    private void square(float x, float y, float h, int c) {
        int edge = argb(0xFF0B1016, 1), fill = argb(c, 1);
        quad(x - h - 0.75F, y - h - 0.75F, x + h + 0.75F, y - h - 0.75F, x + h + 0.75F, y + h + 0.75F, x - h - 0.75F, y + h + 0.75F, edge);
        quad(x - h, y - h, x + h, y - h, x + h, y + h, x - h, y + h, fill);
    }

    private void ring(float x, float y, int c) {
        int edge = argb(0xFF0B1016, 1), fill = argb(c, 1);
        quad(x - 3, y - 3, x + 3, y - 3, x + 3, y + 3, x - 3, y + 3, edge);
        quad(x - 2, y - 2, x + 2, y - 2, x + 2, y + 2, x - 2, y + 2, fill);
        quad(x - 1, y - 1, x + 1, y - 1, x + 1, y + 1, x - 1, y + 1, edge);
    }

    /** Fleche (pointe dans la direction {@code heading}, 0 = haut, sens horaire), contour sombre. */
    private void arrow(float x, float y, float heading, float size, int c, boolean small) {
        float s = (float) Math.sin(heading), co = (float) Math.cos(heading);
        //sommets dans le repere de la fleche : pointe (0, -size), ailes (+-0,7 size, 0,75 size), creux (0, 0,35 size)
        float[] px = ARROW_X, py = ARROW_Y;
        int edge = argb(0xFF0B1016, 1), fill = argb(c, 1);
        float[] xs = arrowX, ys = arrowY;
        for (int pass = 0; pass < 2; pass++) {
            float k = pass == 0 ? size + 1.2F : size;
            int col = pass == 0 ? edge : fill;
            for (int i = 0; i < 4; i++) {
                float lx = px[i] * k, ly = py[i] * k;
                xs[i] = x + lx * co - ly * s;
                ys[i] = y + lx * s + ly * co;
            }
            vertex(xs[0], ys[0], col);
            vertex(xs[1], ys[1], col);
            vertex(xs[2], ys[2], col);
            vertex(xs[0], ys[0], col);
            vertex(xs[2], ys[2], col);
            vertex(xs[3], ys[3], col);
        }
    }

    /** Une texture de region, decoupee par le contour. */
    private void textured(DynamicTexture tex, int rx, int rz) {
        double x0 = rx * 256.0, z0 = rz * 256.0;
        if (m.clipWorldRect(x0, z0, x0 + 256, z0 + 256) == 0) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex.getId());
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        int a = Math.round(alpha * 255);
        for (int i = 1; i + 1 < m.outN; i++) {
            texVertex(b, 0, x0, z0, a);
            texVertex(b, i, x0, z0, a);
            texVertex(b, i + 1, x0, z0, a);
        }
        BufferUploader.drawWithShader(b.end());
    }

    private void texVertex(BufferBuilder b, int i, double x0, double z0, int a) {
        float sx = m.outX[i], sy = m.outY[i];
        float u = (float) ((m.worldX(sx, sy) - x0) / 256.0), v = (float) ((m.worldZ(sx, sy) - z0) / 256.0);
        b.vertex(mat, sx, sy, 0).uv(u, v).color(255, 255, 255, a).endVertex();
    }
}
