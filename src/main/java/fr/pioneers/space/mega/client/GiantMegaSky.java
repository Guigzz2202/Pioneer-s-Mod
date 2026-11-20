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
import fr.pioneers.space.sky.client.ExoSky;
import fr.pioneers.space.sky.client.SkyScene;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 1.0.7 (« Plus loin et au-dela », lots 7 et 8) : les MEGASTRUCTURES DES GEANTES vues de leur geante (ciel des geantes,
 * {@code ExoSkyEffects.renderSky}), une fois debloquees (etat synchronise {@link MegaClient}) ; geometrie et eclat : calcul pur de
 * {@link SkyScene} :
 * <ul>
 *     <li>Cite des Nuages (Jupiter) : les feux des plates-formes tout autour de l'horizon ({@link SkyScene#cityLight}) ;</li>
 *     <li>Anneau-Rotor (Saturne) : l'arc des anneaux d'un horizon a l'autre, des segments reguliers plus vifs qui tournent
 *     ({@link SkyScene#rotorArc}, {@link SkyScene#rotorShade}) ;</li>
 *     <li>Boucle (Uranus) : une aurore permanente en rideaux tout autour du ciel ({@link SkyScene#aurora}) ;</li>
 *     <li>Descente (Neptune) : une colonne lumineuse qui plonge dans les nuages, parcourue d'impulsions ({@link SkyScene#column}).</li>
 * </ul>
 * Tampons et directions reutilises (fil du rendu seulement) : aucune allocation par image.
 */
public final class GiantMegaSky {

    /** Distance des dessins (unites du ciel). */
    private static final float DIST = 100;
    private static final int ARC = 96, AURORA = 120, COLUMN = 48;
    private static final double[] DIR = new double[3], DIR2 = new double[3], TOP = new double[1];

    private GiantMegaSky() {
    }

    /** @param sky ciel propre de l'astre (lumiere de son etoile principale ; 1.0.7 lot 11 : exoplanetes), ou null */
    public static void render(ClientLevel level, float partial, PoseStack poseStack, @org.jetbrains.annotations.Nullable ExoSky sky) {
        int i = Megastructures.byBody(SolarSystem.fromDimension(level.dimension()));
        if (i < 0 || (MegaClient.unlockedMask() & (1L << i)) == 0) {
            return;
        }
        if (fr.pioneers.space.sky.client.mega.MegaModels.has(i)) {
            //1.0.7 (« ciel grandiose ») : la megastructure en 3D
            fr.pioneers.space.sky.client.MegaSkyRenderer.renderGiant(level, i, partial, poseStack, sky);
            return;
        }
        int kind = Megastructures.skyKind(i);
        if (kind != SkyScene.MEGA_LIGHTS && kind != SkyScene.MEGA_ROTOR && kind != SkyScene.MEGA_AURORA && kind != SkyScene.MEGA_COLUMN) {
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
        switch (kind) {
            case SkyScene.MEGA_LIGHTS -> lights(builder, matrix, p, night, ticks, cr, cg, cb, fade);
            case SkyScene.MEGA_ROTOR -> rotor(builder, matrix, p, night, ticks, cr, cg, cb, fade);
            case SkyScene.MEGA_AURORA -> aurora(builder, matrix, p, night, ticks, cr, cg, cb, fade);
            default -> column(builder, matrix, p, ticks, cr, cg, cb, fade);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Feux de la Cite : halo dans la brume, puis le feu. */
    private static void lights(BufferBuilder builder, Matrix4f matrix, double[] p, double night, double ticks, float cr, float cg, float cb,
          float fade) {
        float size = (float) (DIST * Math.toRadians(p[3]));
        for (int k = 0; k < (int) p[0]; k++) {
            double e = SkyScene.cityLight(p, k, night, ticks, DIR);
            if (e <= 0.01) {
                continue;
            }
            quad(builder, matrix, DIR, size * 4, cr, cg, cb, (float) (e * 0.18 * fade));
            quad(builder, matrix, DIR, size, cr, cg, cb, (float) (e * fade));
        }
    }

    /** Arc des anneaux : un ruban d'un horizon a l'autre, plus vif sur les segments du rotor. */
    private static void rotor(BufferBuilder builder, Matrix4f matrix, double[] p, double night, double ticks, float cr, float cg, float cb,
          float fade) {
        double half = Math.toRadians(p[3]) / 2;
        float base = (float) ((0.35 + 0.35 * night) * fade);
        for (int k = 0; k < ARC; k++) {
            double t0 = Math.PI * k / ARC, t1 = Math.PI * (k + 1) / ARC;
            SkyScene.rotorArc(p, t0, DIR);
            SkyScene.rotorArc(p, t1, DIR2);
            float a0 = (float) Math.min(1, base * SkyScene.rotorShade(p, t0, ticks)), a1 = (float) Math.min(1, base * SkyScene.rotorShade(p, t1, ticks));
            //largeur selon la normale du plan de l'arc (le nord-sud du lieu, incline)
            double a = Math.toRadians(p[2]);
            double nx = 0, ny = Math.cos(a) * half, nz = -Math.sin(a) * half;
            vertex(builder, matrix, DIR[0] - nx, DIR[1] - ny, DIR[2] - nz, cr, cg, cb, a0);
            vertex(builder, matrix, DIR2[0] - nx, DIR2[1] - ny, DIR2[2] - nz, cr, cg, cb, a1);
            vertex(builder, matrix, DIR2[0] + nx, DIR2[1] + ny, DIR2[2] + nz, cr, cg, cb, a1);
            vertex(builder, matrix, DIR[0] + nx, DIR[1] + ny, DIR[2] + nz, cr, cg, cb, a0);
        }
    }

    /** Rideaux de l'aurore : du bas (p[0]) au bord haut ondulant, plus clairs en bas, transparents en haut. */
    private static void aurora(BufferBuilder builder, Matrix4f matrix, double[] p, double night, double ticks, float cr, float cg, float cb,
          float fade) {
        double lo = Math.toRadians(p[0]);
        for (int k = 0; k < AURORA; k++) {
            double z0 = 2 * Math.PI * k / AURORA, z1 = 2 * Math.PI * (k + 1) / AURORA;
            float e0 = (float) (SkyScene.aurora(p, z0, ticks, night, TOP) * fade);
            double h0 = Math.toRadians(TOP[0]);
            float e1 = (float) (SkyScene.aurora(p, z1, ticks, night, TOP) * fade);
            double h1 = Math.toRadians(TOP[0]);
            vertex(builder, matrix, dir(lo, z0, 0), dir(lo, z0, 1), dir(lo, z0, 2), cr, cg, cb, e0 * 0.7F);
            vertex(builder, matrix, dir(lo, z1, 0), dir(lo, z1, 1), dir(lo, z1, 2), cr, cg, cb, e1 * 0.7F);
            vertex(builder, matrix, dir(h1, z1, 0), dir(h1, z1, 1), dir(h1, z1, 2), cb, cr * 0.6F, cg, 0);
            vertex(builder, matrix, dir(h0, z0, 0), dir(h0, z0, 1), dir(h0, z0, 2), cb, cr * 0.6F, cg, 0);
        }
    }

    /** Colonne lumineuse : un ruban vertical parcouru d'impulsions, sur un halo plus large. */
    private static void column(BufferBuilder builder, Matrix4f matrix, double[] p, double ticks, float cr, float cg, float cb, float fade) {
        double az = Math.toRadians(p[0]);
        double half = Math.toRadians(p[2]) / 2;
        double rx = Math.cos(az) * half, rz = Math.sin(az) * half;
        for (int pass = 0; pass < 2; pass++) {
            double wide = pass == 0 ? 3 : 1;
            for (int k = 0; k < COLUMN; k++) {
                double s0 = k / (double) COLUMN, s1 = (k + 1) / (double) COLUMN;
                float a0 = (float) (SkyScene.column(p, s0, ticks, DIR) * fade * (pass == 0 ? 0.2 : 1));
                float a1 = (float) (SkyScene.column(p, s1, ticks, DIR2) * fade * (pass == 0 ? 0.2 : 1));
                vertex(builder, matrix, DIR[0] - rx * wide, DIR[1], DIR[2] - rz * wide, cr, cg, cb, a0);
                vertex(builder, matrix, DIR2[0] - rx * wide, DIR2[1], DIR2[2] - rz * wide, cr, cg, cb, a1);
                vertex(builder, matrix, DIR2[0] + rx * wide, DIR2[1], DIR2[2] + rz * wide, cr, cg, cb, a1);
                vertex(builder, matrix, DIR[0] + rx * wide, DIR[1], DIR[2] + rz * wide, cr, cg, cb, a0);
            }
        }
    }

    /** Composante {@code c} de la direction (hauteur, azimut depuis le nord) dans le repere du sol. */
    private static double dir(double el, double az, int c) {
        return c == 0 ? Math.cos(el) * Math.sin(az) : c == 1 ? Math.sin(el) : -Math.cos(el) * Math.cos(az);
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, float r, float g, float b, float a) {
        builder.vertex(matrix, (float) (x * DIST), (float) (y * DIST), (float) (z * DIST)).color(r, g, b, Math.max(0, Math.min(1, a))).endVertex();
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
