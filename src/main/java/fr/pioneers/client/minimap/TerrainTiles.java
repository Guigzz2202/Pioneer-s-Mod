package fr.pioneers.client.minimap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 1.0.4-beta (lot 8, Guillaume : « la page territoire dans les parametres de clan doit montrer la carte et non une simple grille ») : facade
 * PUBLIQUE du terrain de la minimap pour les autres ecrans (fiche Territoire du Carnet). Dessine, dans un rectangle d'ecran droit (sans
 * rotation), les regions DEJA relevees par la minimap ({@link MapStore} de la dimension en cours : en memoire, ou lues du disque a la
 * demande), a l'echelle demandee. Les colonnes jamais vues sont transparentes (l'appelant dessine son brouillard dessous). Aucune
 * allocation, aucun calcul de terrain : seulement les textures deja envoyees a la carte graphique. Sans minimap active (coupee dans les
 * reglages) ou dans une autre dimension : rien, et {@code false}.
 */
public final class TerrainTiles {

    private TerrainTiles() {
    }

    /** Dimension dont la minimap tient le terrain (ex. {@code minecraft:overworld}), vide hors partie. */
    public static String dimension() {
        return MinimapClient.S.store == null ? "" : MinimapClient.S.dim;
    }

    /**
     * Dessine le terrain du monde {@code [wx0, wx0 + w * bpp] x [wz0, wz0 + h * bpp]} (blocs) dans le rectangle {@code (x, y, w, h)} (coordonnees
     * de la pose courante), {@code bpp} blocs par pixel, opacite {@code alpha}. Renvoie vrai si au moins une region connue a ete dessinee.
     */
    public static boolean draw(GuiGraphics g, String dim, double wx0, double wz0, double bpp, int x, int y, int w, int h, float alpha) {
        MapStore store = MinimapClient.S.store;
        if (store == null || !dim.equals(MinimapClient.S.dim) || w <= 0 || h <= 0 || bpp <= 0) {
            return false;
        }
        double wx1 = wx0 + w * bpp, wz1 = wz0 + h * bpp;
        int rx0 = (int) Math.floor(wx0 / MapRegion.SIZE), rx1 = (int) Math.floor((wx1 - 1e-6) / MapRegion.SIZE);
        int rz0 = (int) Math.floor(wz0 / MapRegion.SIZE), rz1 = (int) Math.floor((wz1 - 1e-6) / MapRegion.SIZE);
        long now = Util.getMillis();
        boolean any = false;
        g.flush();
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                MapRegion r = store.region(rx, rz, false, now);
                if (r == null || r.terrainTex == null) {
                    continue;
                }
                //intersection de la region et de la vue, en blocs
                double bx0 = Math.max(wx0, rx * (double) MapRegion.SIZE), bx1 = Math.min(wx1, (rx + 1) * (double) MapRegion.SIZE);
                double bz0 = Math.max(wz0, rz * (double) MapRegion.SIZE), bz1 = Math.min(wz1, (rz + 1) * (double) MapRegion.SIZE);
                if (bx1 <= bx0 || bz1 <= bz0) {
                    continue;
                }
                float sx0 = (float) (x + (bx0 - wx0) / bpp), sx1 = (float) (x + (bx1 - wx0) / bpp);
                float sy0 = (float) (y + (bz0 - wz0) / bpp), sy1 = (float) (y + (bz1 - wz0) / bpp);
                float u0 = (float) ((bx0 - rx * (double) MapRegion.SIZE) / MapRegion.SIZE), u1 = (float) ((bx1 - rx * (double) MapRegion.SIZE) / MapRegion.SIZE);
                float v0 = (float) ((bz0 - rz * (double) MapRegion.SIZE) / MapRegion.SIZE), v1 = (float) ((bz1 - rz * (double) MapRegion.SIZE) / MapRegion.SIZE);
                RenderSystem.setShaderTexture(0, r.terrainTex.getId());
                BufferBuilder b = Tesselator.getInstance().getBuilder();
                b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                b.vertex(mat, sx0, sy0, 0).uv(u0, v0).color(255, 255, 255, a).endVertex();
                b.vertex(mat, sx0, sy1, 0).uv(u0, v1).color(255, 255, 255, a).endVertex();
                b.vertex(mat, sx1, sy1, 0).uv(u1, v1).color(255, 255, 255, a).endVertex();
                b.vertex(mat, sx1, sy0, 0).uv(u1, v0).color(255, 255, 255, a).endVertex();
                BufferUploader.drawWithShader(b.end());
                any = true;
            }
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        return any;
    }
}
