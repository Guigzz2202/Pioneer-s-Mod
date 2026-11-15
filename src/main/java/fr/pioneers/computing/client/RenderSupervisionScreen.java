package fr.pioneers.computing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.computing.ProbeValue;
import fr.pioneers.computing.ScreenLayout;
import fr.pioneers.computing.ScreenTile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Rendu d'un ecran de supervision (0.17.0), dessine par le bloc maitre (en bas a gauche) de chaque ecran rectangulaire : fond sombre, nom de
 * la machine, lignes libelle / valeur avec jauges, et graphe des 60 dernieres valeurs de la grandeur choisie quand l'ecran est assez grand.
 * Pleine lumiere. 1.0.4-beta (lot 26, PJ de Guillaume : texte coupe, cadres dedoubles, L dessine comme deux ecrans) : la mise en page est
 * celle de {@link ScreenLayout} (texte a la taille de la surface, retour a la ligne, rien sous le cadre du bloc) sur l'ecran rectangulaire
 * du decoupage {@code ScreenWall} ; ce rendu ne fait qu'executer le plan (aplats, puis textes). Plan garde tant que l'etat recu, la taille et
 * la langue ne changent pas.
 */
public class RenderSupervisionScreen implements BlockEntityRenderer<ScreenTile> {

    private static final int FULL = 0xF000F0, UNIT = ScreenLayout.UNIT;
    private final Font font;

    public RenderSupervisionScreen(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    private static final class Cache {
        int version = -1, cw, ch;
        @Nullable
        Language language;
        ScreenLayout.Plan plan;
    }

    private final Map<ScreenTile, Cache> caches = new WeakHashMap<>();

    @Override
    public boolean shouldRenderOffScreen(ScreenTile tile) {
        //Appele par les fils de construction des chunks : aucune lecture du monde ici (les non-maitres sont ecartes par shouldRender)
        return true;
    }

    /** 0.20.46 (audit) : ecrans dessines jusqu'a 48 blocs (64 avant). */
    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public boolean shouldRender(ScreenTile tile, Vec3 camera) {
        return tile.isMaster() && BlockEntityRenderer.super.shouldRender(tile, camera);
    }

    @Override
    public void render(ScreenTile tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!tile.isMaster()) {
            return;
        }
        int[] size = tile.size();
        int cw = size[0] * UNIT, ch = size[1] * UNIT;
        ScreenLayout.Plan plan = plan(tile, cw, ch);
        Direction f = tile.facing();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        //Coin haut gauche de l'ecran (vu de face), juste devant la vitre (enfoncee de 1 px), derriere le cadre du bloc
        pose.translate(-0.5, -0.5 + size[1], 0.5 - 1 / 16.0 + 0.004);
        pose.scale(1F / UNIT, -1F / UNIT, 1F / UNIT);
        //Passe 1 : aplats
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        Matrix4f m = pose.last().pose();
        for (ScreenLayout.Rect r : plan.rects) {
            rect(vc, m, r.x0(), r.y0(), r.x1(), r.y1(), r.argb(), r.layer());
        }
        //Passe 2 : textes
        pose.translate(0, 0, 0.5F);
        for (ScreenLayout.Text t : plan.texts) {
            pose.pushPose();
            pose.translate(t.x(), t.y(), 0);
            pose.scale(t.scale(), t.scale(), 1);
            font.drawInBatch(t.s(), 0, 0, t.argb(), false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Plan de l'ecran, refait seulement quand l'etat recu, la taille ou la langue changent. */
    private ScreenLayout.Plan plan(ScreenTile tile, int cw, int ch) {
        Cache c = caches.computeIfAbsent(tile, t -> new Cache());
        Language language = Language.getInstance();
        if (c.plan != null && c.version == tile.clientVersion() && c.cw == cw && c.ch == ch && c.language == language) {
            return c.plan;
        }
        c.version = tile.clientVersion();
        c.cw = cw;
        c.ch = ch;
        c.language = language;
        ScreenLayout.Measure measure = font::width;
        if (!tile.text().isEmpty()) {
            c.plan = ScreenLayout.terminal(cw, ch, tile.text(), measure);
        } else if (tile.link() == null) {
            c.plan = ScreenLayout.unlinked(cw, ch, Component.translatable("computing.pioneers.screen.unlinked").getString(),
                  Component.translatable("computing.pioneers.screen.hint").getString(), measure);
        } else {
            List<ProbeValue> values = tile.values();
            List<ScreenLayout.Row> rows = new ArrayList<>();
            for (ProbeValue v : values) {
                rows.add(new ScreenLayout.Row(v.name().getString(), v.text().getString(), v.fill()));
            }
            ProbeValue g = ProbeValue.find(values, tile.graphKey());
            double[] hist = tile.history();
            String hi = "", lo = "";
            if (g != null && hist.length > 0) {
                double l = Double.MAX_VALUE, h = -Double.MAX_VALUE;
                for (double v : hist) {
                    l = Math.min(l, v);
                    h = Math.max(h, v);
                }
                if (h - l < 1e-6) {
                    h = l + 1;
                }
                hi = ProbeValue.format(h, g.unit());
                lo = ProbeValue.format(l, g.unit());
            }
            c.plan = ScreenLayout.machine(cw, ch, tile.name().getString(), rows, hist, ScreenTile.HISTORY, g == null ? "" : g.name().getString(),
                  g == null ? "" : g.text().getString(), hi, lo, measure);
        }
        return c.plan;
    }

    /** Aplat (coordonnees de l'ecran), emis dans les deux sens pour rester visible quel que soit l'elagage. */
    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, float z) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(FULL).endVertex();
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(FULL).endVertex();
    }
}
