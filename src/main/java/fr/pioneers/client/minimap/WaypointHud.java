package fr.pioneers.client.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 1.0.4-beta (lot 5, Guillaume : « pour voir les waypoints il faut que l'horizon soit degage actuellement, j'aimerais qu'on les voie en
 * toutes circonstances ») : MARQUEURS DES WAYPOINTS PROJETES A L'ECRAN, dessines avec l'interface (apres le monde, l'eau, la pluie, les
 * nuages et le compositeur « Fabuleux » : rien ne peut plus les cacher ni les faire scintiller). Remplace les etiquettes dans le monde de
 * {@link WaypointBeacons} (le faisceau du mode Balise reste dans le monde, cache par le relief comme une vraie balise).
 * <ul>
 *     <li>Position : a chaque image, l'etape {@code AFTER_PARTICLES} donne la projection (balancement de la vue compris) et la vue
 *     (rotation de la camera) ({@link #capture}) ; l'interface projette chaque waypoint ({@link MapMath#toScreen}).</li>
 *     <li>Dans le champ : losange a la couleur du waypoint, nom au-dessus, distance dessous ; taille CONSTANTE a l'ecran a toute distance ;
 *     en grand pres du viseur ({@link MapMath#focused}).</li>
 *     <li>Hors champ (ou derriere) : ramene au BORD de l'ecran (marges {@link MapMath#EDGE_SIDE}, au-dessus de la barre d'objets), fleche
 *     vers la cible, nom et distance en petit.</li>
 *     <li>Derriere le terrain : ATTENUE ({@link MapMath#markerAlpha}) : on le voit toujours sans croire qu'il est a decouvert. Test par un
 *     rayon camera -> waypoint dans les blocs charges ({@link #hidden}), refait toutes les {@link #RECHECK} ticks par waypoint et au plus
 *     {@link #RAYS_PER_FRAME} par image (beaucoup de waypoints : cout borne).</li>
 * </ul>
 * Reglages : calque « Waypoints » de la minimap, style par waypoint (Invisible = rien), portee et distance ({@link MinimapConfig}). Rien
 * quand l'interface est masquee (F1). Maquette fidele : {@code scratchpad/p104/lot5/tools/waypoint_mock.py} (vraie police).
 */
public final class WaypointHud {

    /** Demi-taille du losange (px de l'interface), petit et grand ; fond de l'etiquette. */
    static final float MARKER = 5F, MARKER_FOCUS = 6.5F;
    static final int BACKGROUND = 0x90000000;
    /** Echelle du texte : etiquette ordinaire / pres du viseur. */
    static final float TEXT_SMALL = 0.75F, TEXT_FOCUS = 1F;
    /** Test d'occultation : delai entre deux rayons pour un meme waypoint (ticks), rayons par image au plus. */
    static final int RECHECK = 10, RAYS_PER_FRAME = 6;

    private static final Matrix4f PROJECTION = new Matrix4f(), VIEW = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean captured;

    /** Dernier resultat d'occultation d'un waypoint (cache de l'image, identite du waypoint). */
    private static final Map<Waypoint, long[]> OCCLUSION = new IdentityHashMap<>();

    private WaypointHud() {
    }

    /** Appele a l'etape AFTER_PARTICLES ({@link WaypointBeacons#render}) : matrices et camera de cette image. */
    static void capture(RenderLevelStageEvent event) {
        PROJECTION.set(event.getProjectionMatrix());
        VIEW.set(event.getPoseStack().last().pose());
        camera = event.getCamera().getPosition();
        captured = true;
    }

    private record Mark(Waypoint w, MapMath.ScreenPos p, double dist, boolean hidden) {
    }

    /** Calque d'interface (enregistre par {@code MinimapClient.overlays}). */
    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!captured || mc.level == null || mc.player == null || mc.options.hideGui || !MinimapConfig.on(MinimapConfig.L_WAYPOINTS, true)) {
            return;
        }
        List<Waypoint> list = Waypoints.cached(mc.level.dimension().location().toString());
        if (list.isEmpty()) {
            return;
        }
        MinimapConfig.BeaconStyle global = MinimapConfig.beaconStyle();
        int range = MinimapConfig.num(MinimapConfig.W_BEACON_RANGE, 0);
        boolean showDist = MinimapConfig.on(MinimapConfig.W_DISTANCE, true);
        Vec3 p = mc.player.position();
        long now = mc.level.getGameTime();
        int rays = RAYS_PER_FRAME;
        List<Mark> marks = new ArrayList<>();
        Vector4f v = new Vector4f();
        for (Waypoint w : list) {
            if (!w.visible || w.resolvedStyle(global) == MinimapConfig.BeaconStyle.HIDDEN) {
                continue;
            }
            double dist = w.distanceTo(p.x, p.y, p.z);
            if (range > 0 && dist > range) {
                continue;
            }
            double ax = w.x + 0.5, ay = w.y + 0.5, az = w.z + 0.5;
            v.set((float) (ax - camera.x), (float) (ay - camera.y), (float) (az - camera.z), 1F);
            VIEW.transform(v);
            PROJECTION.transform(v);
            MapMath.ScreenPos sp = MapMath.toScreen(v.x, v.y, v.w, width, height);
            w.shownDist = MapMath.shownDistance(w.shownDist, dist);
            long[] occ = OCCLUSION.computeIfAbsent(w, k -> new long[]{Long.MIN_VALUE, 0});
            if (rays > 0 && (occ[0] == Long.MIN_VALUE || now - occ[0] >= RECHECK || now < occ[0])) {
                rays--;
                occ[0] = now;
                occ[1] = hidden(mc, ax, ay, az) ? 1 : 0;
            }
            marks.add(new Mark(w, sp, dist, occ[1] != 0));
        }
        if (OCCLUSION.size() > list.size() * 2 + 16) {
            OCCLUSION.keySet().retainAll(new HashSet<>(list));
        }
        //du plus loin au plus proche : le plus proche par-dessus
        marks.sort((a, b) -> Double.compare(b.dist, a.dist));
        RenderSystem.enableBlend();
        for (Mark m : marks) {
            draw(g, mc.font, m, width, height, showDist);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * Le waypoint est-il derriere le terrain ? Rayon de la camera vers lui (blocs visibles, pas les fluides), arrete a 0,6 bloc du but et
     * a la distance d'affichage (au-dela, les blocs ne sont pas charges chez le client).
     */
    static boolean hidden(Minecraft mc, double ax, double ay, double az) {
        Vec3 target = new Vec3(ax, ay, az);
        Vec3 d = target.subtract(camera);
        double len = d.length();
        if (len < 1.2) {
            return false;
        }
        double reach = Math.min(len - 0.6, mc.options.getEffectiveRenderDistance() * 16.0 - 8);
        if (reach <= 0.5) {
            return false;
        }
        Vec3 end = camera.add(d.scale(reach / len));
        BlockHitResult hit = mc.level.clip(new ClipContext(camera, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK;
    }

    private static void draw(GuiGraphics g, Font font, Mark m, int width, int height, boolean showDist) {
        Waypoint w = m.w;
        MapMath.ScreenPos sp = m.p;
        boolean focus = MapMath.focused(sp, width, height);
        int alpha = MapMath.markerAlpha(m.hidden);
        float half = focus ? MARKER_FOCUS : MARKER;
        float x = (float) sp.x(), y = (float) sp.y();
        int rgb = w.color & 0xFFFFFF;
        var pose = g.pose();
        //fleche hors champ, puis losange
        if (!sp.onScreen()) {
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotation((float) sp.angle()));
            arrow(g, alpha << 24 | rgb);
            pose.popPose();
        }
        RenderSystem.setShaderColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, alpha / 255F);
        pose.pushPose();
        pose.translate(x - half, y - half, 0);
        pose.scale(half * 2 / 32F, half * 2 / 32F, 1F);
        g.blit(WaypointBeacons.MARKER, 0, 0, 0, 0, 32, 32, 32, 32);
        pose.popPose();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        //etiquette : nom au-dessus, distance dessous (hors champ : a cote, vers l'interieur de l'ecran)
        float scale = focus ? TEXT_FOCUS : TEXT_SMALL;
        String name = w.name;
        String dist = showDist ? WaypointBeacons.distanceText(w.shownDist) : null;
        float nameW = font.width(name) * scale, distW = dist == null ? 0 : font.width(dist) * scale;
        float lineH = 9 * scale;
        float cx = x, nameY = y - half - 2 - lineH, distY = y + half + 2;
        if (!sp.onScreen()) {
            //au bord : etiquette tiree vers l'interieur (jamais coupee par le cadre de l'ecran)
            double ix = Math.cos(sp.angle()), iy = Math.sin(sp.angle());
            cx = (float) (x - ix * (Math.max(nameW, distW) / 2 + half + 4));
            float block = lineH * (dist == null ? 1 : 2) + 2;
            float top = (float) (y - iy * (block / 2 + half + 4)) - block / 2;
            nameY = top;
            distY = top + lineH + 2;
        }
        int textA = Math.max(0x20, alpha);
        label(g, font, name, cx, nameY, scale, textA << 24 | MapMath.readable(rgb), width, height);
        if (dist != null) {
            label(g, font, dist, cx, distY, scale, textA << 24 | 0xE0F5EA, width, height);
        }
    }

    /** Une ligne centree sur {@code cx}, fond sombre, gardee dans l'ecran. */
    private static void label(GuiGraphics g, Font font, String text, float cx, float top, float scale, int argb, int width, int height) {
        float tw = font.width(text) * scale;
        float left = Math.max(2, Math.min(width - 2 - tw, cx - tw / 2));
        top = Math.max(2, Math.min(height - 2 - 9 * scale, top));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1F);
        int bgA = (int) ((BACKGROUND >>> 24) * ((argb >>> 24) / 255F));
        g.fill(-1, -1, font.width(text) + 1, 9, bgA << 24);
        g.drawString(font, text, 0, 0, argb, false);
        pose.popPose();
    }

    /** Fleche (triangle en escalier, lisere sombre) tournee vers +x, la pointe a 12 px du centre du losange. */
    private static void arrow(GuiGraphics g, int argb) {
        int dark = (argb >>> 24) * 3 / 4 << 24;
        for (int i = 0; i < 5; i++) {
            g.fill(6 + i, -5 + i, 8 + i, 6 - i, dark);
        }
        for (int i = 0; i < 5; i++) {
            g.fill(7 + i, -4 + i, 8 + i, 5 - i, argb);
        }
    }
}
