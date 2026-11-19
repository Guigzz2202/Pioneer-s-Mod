package fr.pioneers.space.mega.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.sky.client.SkyScene;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 7) : la CITE DES NUAGES vue de sa geante : les feux des plates-formes de la cite tout autour de
 * l'horizon, dans la brume (ciel des geantes, {@code ExoSkyEffects.renderSky}) ; geometrie et eclat : {@link SkyScene#cityLight}. Rien tant
 * que la megastructure de la geante n'est pas debloquee (etat synchronise {@link MegaClient}).
 */
public final class CityLights {

    /** Distance des feux (unites du ciel). */
    private static final float DIST = 100;
    private static final double[] DIR = new double[3];

    private CityLights() {
    }

    public static void render(ClientLevel level, float partial, PoseStack poseStack) {
        int i = Megastructures.byBody(SolarSystem.fromDimension(level.dimension()));
        if (i < 0 || Megastructures.skyKind(i) != SkyScene.MEGA_LIGHTS || (MegaClient.unlockedMask() & (1L << i)) == 0) {
            return;
        }
        double[] p = Megastructures.skyParams(i);
        int rgb = Megastructures.color(i);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        double night = Math.min(1, level.getStarBrightness(partial) * 2);
        double ticks = RenderClock.time(partial);
        float fade = 1 - level.getRainLevel(partial) * 0.6F;
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float size = (float) (DIST * Math.toRadians(p[3]));
        for (int k = 0; k < (int) p[0]; k++) {
            double e = SkyScene.cityLight(p, k, night, ticks, DIR);
            if (e <= 0.01) {
                continue;
            }
            //halo dans la brume, puis le feu
            quad(builder, matrix, DIR, size * 4, cr, cg, cb, (float) (e * 0.18 * fade));
            quad(builder, matrix, DIR, size, cr, cg, cb, (float) (e * fade));
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Carre tourne vers l'observateur, centre sur la direction {@code d}. */
    private static void quad(BufferBuilder builder, Matrix4f matrix, double[] d, float s, float r, float g, float b, float a) {
        //droite = d x haut (horizontale), haut' = droite x d
        double rx = -d[2], rz = d[0];
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        double ux = -rz * d[1], uy = rz * d[0] - rx * d[2], uz = rx * d[1];
        float cx = (float) (d[0] * DIST), cy = (float) (d[1] * DIST), cz = (float) (d[2] * DIST);
        builder.vertex(matrix, cx + (float) (-rx - ux) * s, cy + (float) (-uy) * s, cz + (float) (-rz - uz) * s).color(r, g, b, a).endVertex();
        builder.vertex(matrix, cx + (float) (rx - ux) * s, cy + (float) (-uy) * s, cz + (float) (rz - uz) * s).color(r, g, b, a).endVertex();
        builder.vertex(matrix, cx + (float) (rx + ux) * s, cy + (float) (uy) * s, cz + (float) (rz + uz) * s).color(r, g, b, a).endVertex();
        builder.vertex(matrix, cx + (float) (-rx + ux) * s, cy + (float) (uy) * s, cz + (float) (-rz + uz) * s).color(r, g, b, a).endVertex();
    }
}
