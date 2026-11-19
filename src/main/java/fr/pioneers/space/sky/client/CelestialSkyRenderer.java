package fr.pioneers.space.sky.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.space.sky.client.Celestial.Body;
import fr.pioneers.core.RenderClock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Dessine le ciel calcule par {@link SkyScene} : etoiles et Voie lactee, Soleil a sa taille apparente, planetes en points
 * lumineux (taille et eclat selon leur magnitude reelle), astres proches en disques avec leurs phases, anneaux de Saturne,
 * eclipses, et la nuee d'asteroides de la ceinture.
 * <p>
 * 1.0.7 (lot 4) : les megastructures debloquees ({@link SkyScene#MEGA}) : le cable d'un ascenseur spatial (bande fine, partie a l'ombre
 * sombre, partie eclairee a la couleur de la megastructure, eclat additif a l'aube et au crepuscule, feux de balisage la nuit) ; leur
 * marque sur le disque de leur astre vu d'ailleurs. Aucune allocation par image (tableaux statiques).
 */
public final class CelestialSkyRenderer {

    private CelestialSkyRenderer() {
    }

    /** Distance des astres (reste sous le plan lointain, meme a 2 chunks de vue). */
    private static final float R = 60;

    private static final ResourceLocation SUN = Pioneers.rl("textures/environment/sky/sun.png");
    private static final ResourceLocation HALO = Pioneers.rl("textures/environment/sky/sun_halo.png");
    private static final ResourceLocation RING = Pioneers.rl("textures/environment/sky/saturn_ring.png");
    private static final ResourceLocation[] ROCKS = new ResourceLocation[4];
    private static final Map<Body, ResourceLocation> TEXTURES = new EnumMap<>(Body.class);

    static {
        for (int i = 0; i < ROCKS.length; i++) {
            ROCKS[i] = Pioneers.rl("textures/environment/sky/asteroid_" + i + ".png");
        }
        for (Body body : Body.ALL) {
            TEXTURES.put(body, body.skyTexture != null ? Pioneers.rl("textures/environment/sky/" + body.skyTexture + ".png")
                                                       : Pioneers.rl("textures/gui/solar/" + body.mapTexture() + ".png"));
        }
    }

    private static VertexBuffer stars, beltGlow;
    /**
     * 0.20.46 (audit) : points lumineux mis en lot. Les points qui se suivent dans l'ordre de dessin (du plus lointain au plus proche) sont
     * accumules puis dessines en deux appels (halos, coeurs) juste avant le prochain Soleil, disque ou nuee : meme image (melange additif,
     * l'ordre entre points n'y change rien), un appel de dessin par suite de points au lieu d'un ou deux par astre.
     */
    private static BufferBuilder haloBatch, coreBatch;
    private static int halos, cores;
    private static final VertexBuffer[] SHELLS = new VertexBuffer[3];
    /** Derive des coquilles d'asteroides (degres par jour) : les plus proches defilent plus vite. */
    private static final double[] SHELL_DRIFT = {2.0, 5.0, 11.0};

    /** Gros asteroides proches : longitude, latitude, derive (deg/jour), taille (deg), rotation (deg/tick), texture. */
    private static final double[][] ROCK_DATA = new double[220][];

    static {
        Random random = new Random(4242L);
        for (int i = 0; i < ROCK_DATA.length; i++) {
            double size = 0.14 + 1.5 * Math.pow(random.nextDouble(), 4);
            ROCK_DATA[i] = new double[]{random.nextDouble() * 360, random.nextGaussian() * 10, (random.nextDouble() * 2 - 1) * (4 + 26 * size),
                                        size, (random.nextDouble() * 2 - 1) * 0.6, random.nextInt(4), random.nextDouble() * 360};
        }
    }

    /** Ce qui change entre un ciel noir (sans atmosphere) et un ciel avec atmosphere. */
    public static final class Style {
        /** Visibilite des etoiles et des planetes (1 = ciel noir ; sinon luminosite des etoiles vanilla, 0..0,5). */
        public float starBrightness = 1;
        public boolean atmosphere;
        public float rain;
        /** Couleur du ciel (silhouette d'un astre passant devant le Soleil). */
        public float skyR, skyG, skyB;
        public boolean drawSun = true;
        public boolean drawStars = true;
        public boolean pointsOnly;
        /** 1.0.7 (lot 6) : seulement les megastructures (ciel nuageux de Venus : ni astres ni etoiles). */
        public boolean megaOnly;
    }

    private static void ensureBuffers() {
        if (stars == null) {
            stars = SkyStars.buildStars();
            beltGlow = SkyStars.buildBeltGlow();
            SHELLS[0] = SkyStars.buildAsteroidShell(11L, 3400, 6.0, 0.03, 0.09);
            SHELLS[1] = SkyStars.buildAsteroidShell(12L, 2200, 10.0, 0.04, 0.14);
            SHELLS[2] = SkyStars.buildAsteroidShell(13L, 1300, 15.0, 0.05, 0.22);
        }
    }

    /** Temps orbital : jours du monde. */
    public static double days(ClientLevel level) {
        return level.getDayTime() / 24000.0;
    }

    /**
     * Dessine tout le ciel calcule. La pose recue est celle de la camera (sans translation), comme pour le ciel vanilla.
     */
    public static void render(SkyScene scene, ClientLevel level, float partialTick, PoseStack poseStack, Matrix4f projection, Style style) {
        ensureBuffers();
        resetPoints();
        float timeDeg = level.getTimeOfDay(partialTick) * 360.0F;
        double t = days(level);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        FogRenderer.setupNoFog();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(timeDeg));
        float sky = style.atmosphere ? Math.min(1, style.starBrightness * 2) * (1 - style.rain) : 1;

        if (style.megaOnly) {
            Matrix4f m = poseStack.last().pose();
            for (SkyScene.Item item : scene.items) {
                if (item.type == SkyScene.MEGA) {
                    drawMega(m, item, style, timeDeg);
                }
            }
            poseStack.popPose();
            MegaSkyRenderer.render(scene, partialTick, poseStack, style, timeDeg);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            return;
        }
        //Etoiles et Voie lactee, fixes par rapport aux etoiles (le Soleil y avance au fil de l'annee)
        if (style.drawStars && sky > 0.01F) {
            additive();
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotation((float) scene.sunLongitude));
            RenderSystem.setShaderColor(1, 1, 1, sky);
            stars.bind();
            stars.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            poseStack.popPose();
        }
        //Ceinture d'asteroides
        if (scene.observer.belt && !style.pointsOnly) {
            additive();
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotation((float) scene.sunLongitude));
            RenderSystem.setShaderColor(1, 1, 1, 1);
            beltGlow.bind();
            beltGlow.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader());
            poseStack.popPose();
            for (int i = 0; i < SHELLS.length; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotation((float) (scene.sunLongitude + Math.toRadians(SHELL_DRIFT[i] * t))));
                SHELLS[i].bind();
                SHELLS[i].drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader());
                poseStack.popPose();
            }
            VertexBuffer.unbind();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);

        Matrix4f matrix = poseStack.last().pose();
        SkyScene.Item sun = scene.sun;
        for (SkyScene.Item item : scene.items) {
            switch (item.type) {
                case SkyScene.SUN -> {
                    if (style.drawSun) {
                        drawSun(matrix, scene, item, style);
                    }
                }
                case SkyScene.POINT -> drawPoint(matrix, item, item.pointAlpha, sun, style, sky);
                case SkyScene.MEGA -> {
                    if (!style.pointsOnly) {
                        drawMega(matrix, item, style, timeDeg);
                    }
                }
                default -> {
                    if (item.pointAlpha > 0.01F) {
                        drawPoint(matrix, item, item.pointAlpha, sun, style, sky);
                    }
                    if (!style.pointsOnly) {
                        drawDisc(matrix, scene, item, sun, style);
                    }
                }
            }
        }
        flushPoints();
        if (scene.observer.belt && !style.pointsOnly) {
            drawRocks(matrix, scene, level, partialTick, t);
        }

        poseStack.popPose();
        //1.0.7 (« ciel grandiose ») : megastructures en 3D, dans le repere de la camera, devant les astres
        if (!style.pointsOnly) {
            MegaSkyRenderer.render(scene, partialTick, poseStack, style, timeDeg);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void additive() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
              GlStateManager.DestFactor.ZERO);
    }

    private static void normal() {
        RenderSystem.defaultBlendFunc();
    }

    // ---------------------------------------------------------------- Soleil

    private static void drawSun(Matrix4f matrix, SkyScene scene, SkyScene.Item sun, Style style) {
        flushPoints();
        float fade = 1 - style.rain;
        if (fade <= 0) {
            return;
        }
        double core = R * Math.tan(Math.toRadians(sun.visual));
        double au = scene.sunDistance / Celestial.AU;
        //Halo : plus large et plus intense pres du Soleil
        double halo = Math.max(core * 5.5, R * Math.tan(Math.toRadians(3.5)));
        float haloAlpha = (float) Math.max(0.1, Math.min(0.55, 0.32 / Math.sqrt(au))) * (style.atmosphere ? 0.8F : 1);
        additive();
        //1.0.7 (lot 9) : l'Essaim voile et teinte le Soleil (1 sans lui)
        float sl = (float) scene.sunLight, tr = (float) scene.sunTintR, tg = (float) scene.sunTintG, tb = (float) scene.sunTintB;
        quad(matrix, 0, 1, 0, halo, 0, HALO, tr * sl, tg * sl, tb * sl, haloAlpha * fade);
        quad(matrix, 0, 1, 0, core * 16 / 14.0, 0, SUN, tr * sl, tg * sl, tb * sl, fade);
        if (sun.megaMarks != 0) {
            drawSwarm(matrix, sun, core, fade);
        }
    }

    /** 1.0.7 (lot 9) : l'Essaim autour du disque du Soleil : des eclats qui tournent lentement ({@link SkyScene#swarmGlint}). */
    private static void drawSwarm(Matrix4f matrix, SkyScene.Item sun, double core, float fade) {
        double ticks = RenderClock.now();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < fr.pioneers.space.mega.Megastructures.COUNT; i++) {
            if ((sun.megaMarks & (1L << i)) == 0 || fr.pioneers.space.mega.Megastructures.skyKind(i) != SkyScene.MEGA_SWARM) {
                continue;
            }
            double[] p = fr.pioneers.space.mega.Megastructures.skyParams(i);
            int rgb = fr.pioneers.space.mega.Megastructures.color(i);
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            double d = Math.max(0.15, core * 0.08);
            for (int k = 0; k < (int) p[4]; k++) {
                double e = SkyScene.swarmGlint(p, k, ticks, MARK);
                float a = (float) (e * fade);
                //le disque du Soleil est dessine en (0, R, 0) dans le plan x / z
                double x = MARK[0] * core, z = MARK[1] * core;
                builder.vertex(matrix, (float) (x - d), R, (float) (z - d)).color(r, g, b, a).endVertex();
                builder.vertex(matrix, (float) (x + d), R, (float) (z - d)).color(r, g, b, a).endVertex();
                builder.vertex(matrix, (float) (x + d), R, (float) (z + d)).color(r, g, b, a).endVertex();
                builder.vertex(matrix, (float) (x - d), R, (float) (z + d)).color(r, g, b, a).endVertex();
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    // ---------------------------------------------------------------- Points

    private static void drawPoint(Matrix4f matrix, SkyScene.Item item, float weight, SkyScene.Item sun, Style style, float sky) {
        double mag = item.mag;
        float alpha = (float) Math.max(0.15, Math.min(1, (6.5 - mag) / 4.0));
        if (style.atmosphere) {
            //Ciel avec atmosphere : seuls les astres assez brillants percent le crepuscule
            double threshold = Math.max(0, Math.min(0.45, 0.03 + 0.06 * (mag + 4)));
            alpha *= (float) Math.max(0, Math.min(1, (style.starBrightness - threshold) / 0.12)) * (1 - style.rain);
        } else if (mag > 6.2) {
            return;
        }
        //Noye dans l'eclat du Soleil
        double sep = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, item.y))));
        double sunVis = sun.visual * 1.15;
        alpha *= (float) SkyScene.smoothstep(sunVis, sunVis + 2.5, sep) * weight;
        if (alpha < 0.01F) {
            return;
        }
        double sizeDeg = Math.max(0.06, Math.min(0.32, 0.09 + 0.032 * (2 - mag)));
        double size = R * Math.tan(Math.toRadians(sizeDeg));
        float r = ((item.body.color >> 16) & 0xFF) / 255F, g = ((item.body.color >> 8) & 0xFF) / 255F, b = (item.body.color & 0xFF) / 255F;
        //0.20.46 (audit) : mis en lot (dessines par flushPoints, en additif)
        if (mag < -1.5) {
            float glow = (float) Math.min(1, (-1.5 - mag) / 3) * 0.35F * alpha;
            if (halos++ == 0) {
                haloBatch.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            texturedQuad(haloBatch, matrix, item.x, item.y, item.z, size * 5, 0, r, g, b, glow);
        }
        if (cores++ == 0) {
            coreBatch.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        coloredVertices(coreBatch, matrix, item.x, item.y, item.z, size, r, g, b, alpha);
    }

    /** Lots de points vides (et prets) en debut d'image ; un lot reste ouvert apres une erreur de rendu : il est jete. */
    private static void resetPoints() {
        if (haloBatch == null) {
            haloBatch = new BufferBuilder(256);
            coreBatch = new BufferBuilder(256);
        }
        if (haloBatch.building()) {
            haloBatch.end().release();
        }
        if (coreBatch.building()) {
            coreBatch.end().release();
        }
        halos = 0;
        cores = 0;
    }

    /** Dessine les points en attente (halos puis coeurs, en additif) ; a appeler avant tout autre dessin du ciel. */
    private static void flushPoints() {
        if (halos == 0 && cores == 0) {
            return;
        }
        additive();
        if (halos > 0) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, HALO);
            BufferUploader.drawWithShader(haloBatch.end());
            halos = 0;
        }
        if (cores > 0) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(coreBatch.end());
            cores = 0;
        }
        normal();
    }

    // ---------------------------------------------------------------- Disques

    private static void drawDisc(Matrix4f matrix, SkyScene scene, SkyScene.Item item, SkyScene.Item sun, Style style) {
        flushPoints();
        double h = R * Math.tan(Math.toRadians(item.visual));
        double nx = item.x, ny = item.y, nz = item.z;
        //"Haut" de la texture : le nord de l'ecliptique (ou le pole des anneaux)
        double upx = -1, upy = 0, upz = 0;
        if (item.ring) {
            upx = item.px;
            upy = item.py;
            upz = item.pz;
        }
        double d = upx * nx + upy * ny + upz * nz;
        upx -= d * nx;
        upy -= d * ny;
        upz -= d * nz;
        double ul = Math.sqrt(upx * upx + upy * upy + upz * upz);
        if (ul < 1e-4) {
            upx = 0;
            upy = 0;
            upz = 1;
            d = nz;
            upx -= d * nx;
            upy -= d * ny;
            upz -= d * nz;
            ul = Math.sqrt(upx * upx + upy * upy + upz * upz);
        }
        upx /= ul;
        upy /= ul;
        upz /= ul;
        //droite = avant x haut
        double rx = ny * upz - nz * upy, ry = nz * upx - nx * upz, rz = nx * upy - ny * upx;
        float alpha = item.discAlpha;
        ResourceLocation texture = TEXTURES.get(item.body);

        //Anneaux : moitie arriere avant la planete
        if (item.ring) {
            drawRing(matrix, item, h, false, style);
        }
        if (style.atmosphere) {
            //Devant le Soleil, la silhouette cache le Soleil mais prend la couleur du ciel
            double sep = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, ny))));
            if (sep < sun.visual + item.visual + 1) {
                normal();
                disc(matrix, nx, ny, nz, rx, ry, rz, upx, upy, upz, h, texture, item, 0, style.skyR, style.skyG, style.skyB, alpha, false);
            }
            additive();
            float day = 1 - Math.min(1, style.starBrightness * 2);
            disc(matrix, nx, ny, nz, rx, ry, rz, upx, upy, upz, h, texture, item, 0.0F, 1 - 0.25F * day, 1 - 0.25F * day, 1 - 0.25F * day,
                  alpha * (1 - style.rain * 0.8F), true);
        } else {
            normal();
            disc(matrix, nx, ny, nz, rx, ry, rz, upx, upy, upz, h, texture, item, 0.02F, 1, 1, 1, alpha, true);
        }
        if (item.ring) {
            drawRing(matrix, item, h, true, style);
        }
        if (item.megaMarks != 0) {
            drawMarks(matrix, item, h, nx, ny, nz, rx, ry, rz, upx, upy, upz, style);
        }
        normal();
    }

    // ---------------------------------------------------------------- Megastructures (1.0.7, lot 4)

    /** Points du cable (repere du ciel, rayon R) et normale de bande : reutilises a chaque image. */
    private static final int TETHER_STEPS = 40;
    private static final double[] TX = new double[TETHER_STEPS + 1], TY = new double[TETHER_STEPS + 1], TZ = new double[TETHER_STEPS + 1];
    private static final double[] WX = new double[TETHER_STEPS + 1], WY = new double[TETHER_STEPS + 1], WZ = new double[TETHER_STEPS + 1];

    private static void drawMega(Matrix4f matrix, SkyScene.Item item, Style style, float timeDeg) {
        if (fr.pioneers.space.sky.client.mega.MegaModels.has(item.mega)) {
            //1.0.7 (« ciel grandiose ») : dessinee en 3D apres les astres (MegaSkyRenderer)
            return;
        }
        if (item.megaKind == SkyScene.MEGA_TETHER) {
            drawTether(matrix, item, style);
        } else if (item.megaKind == SkyScene.MEGA_RING) {
            drawRingArc(matrix, item, style);
        } else if (item.megaKind == SkyScene.MEGA_PLUME) {
            drawPlume(matrix, item, style, timeDeg);
        } else if (item.megaKind == SkyScene.MEGA_LATTICE) {
            drawLattice(matrix, item, style);
        }
    }

    /** 1.0.7 (lot 10) : reseau de points de l'Observatoire du Halo (la nuit, sous un ciel sans nuages). */
    private static final double[] LP = new double[3];

    private static void drawLattice(Matrix4f matrix, SkyScene.Item item, Style style) {
        flushPoints();
        float vis = (style.atmosphere ? Math.min(1, style.starBrightness * 2) : 1) * (1 - style.rain);
        if (vis <= 0.01F) {
            return;
        }
        double[] p = fr.pioneers.space.mega.Megastructures.skyParams(item.mega);
        int rgb = fr.pioneers.space.mega.Megastructures.color(item.mega);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        double ticks = RenderClock.now();
        double d = R * Math.tan(Math.toRadians(p[3]));
        additive();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int n = (int) p[0] * (int) p[1];
        for (int k = 0; k < n; k++) {
            float a = (float) (SkyScene.latticePoint(item, p, k, ticks, LP) * vis);
            double qx = R * LP[0], qy = R * LP[1], qz = R * LP[2];
            builder.vertex(matrix, (float) (qx - item.ux * d), (float) (qy - item.uy * d), (float) (qz - item.uz * d)).color(cr, cg, cb, a).endVertex();
            builder.vertex(matrix, (float) (qx - item.bx * d), (float) (qy - item.by * d), (float) (qz - item.bz * d)).color(cr, cg, cb, a).endVertex();
            builder.vertex(matrix, (float) (qx + item.ux * d), (float) (qy + item.uy * d), (float) (qz + item.uz * d)).color(cr, cg, cb, a).endVertex();
            builder.vertex(matrix, (float) (qx + item.bx * d), (float) (qy + item.by * d), (float) (qz + item.bz * d)).color(cr, cg, cb, a).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        normal();
    }

    /** Lueur sur l'horizon (bande additive) et panache qui monte dans les nuages en s'elargissant et en palissant (Grand Forage). */
    private static final double[] PG = new double[3];

    private static void drawPlume(Matrix4f matrix, SkyScene.Item item, Style style, float timeDeg) {
        flushPoints();
        int rgb = fr.pioneers.space.mega.Megastructures.color(item.mega);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        float k = (float) item.glint * (1 - style.rain * 0.6F);
        double az = item.ux, half = Math.toRadians(item.terminator), top = Math.toRadians(4);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        additive();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        //lueur : 16 morceaux le long de l'horizon, plus forte au centre
        for (int i = 0; i < 16; i++) {
            double a0 = az - half + 2 * half * i / 16, a1 = az - half + 2 * half * (i + 1) / 16;
            float w0 = (float) Math.cos((i - 7.5) / 8.0 * Math.PI / 2), w1 = (float) Math.cos((i - 6.5) / 8.0 * Math.PI / 2);
            plumeVertex(builder, matrix, 0, a0, timeDeg, cr, cg * 0.7F, cb * 0.5F, 0.7F * k * Math.max(0, w0));
            plumeVertex(builder, matrix, 0, a1, timeDeg, cr, cg * 0.7F, cb * 0.5F, 0.7F * k * Math.max(0, w1));
            plumeVertex(builder, matrix, top, a1, timeDeg, cr, cg * 0.7F, cb * 0.5F, 0);
            plumeVertex(builder, matrix, top, a0, timeDeg, cr, cg * 0.7F, cb * 0.5F, 0);
        }
        //panache : 12 morceaux de la base au haut, de plus en plus large et pale
        double alt1 = altitudeOf(item, timeDeg);
        for (int i = 0; i < 12; i++) {
            double t0 = i / 12.0, t1 = (i + 1) / 12.0;
            double al0 = alt1 * t0, al1 = alt1 * t1;
            double w0 = Math.toRadians(item.width) * (0.6 + 2.2 * t0), w1 = Math.toRadians(item.width) * (0.6 + 2.2 * t1);
            float f0 = (float) (k * (1 - t0) * 0.8), f1 = (float) (k * (1 - t1) * 0.8);
            plumeVertex(builder, matrix, al0, az - w0, timeDeg, cr, cg, cb, f0);
            plumeVertex(builder, matrix, al0, az + w0, timeDeg, cr, cg, cb, f0);
            plumeVertex(builder, matrix, al1, az + w1, timeDeg, cr, cg, cb, f1);
            plumeVertex(builder, matrix, al1, az - w1, timeDeg, cr, cg, cb, f1);
        }
        BufferUploader.drawWithShader(builder.end());
        normal();
    }

    /** Hauteur (rad) du haut du panache, relue de sa direction dans le repere du ciel. */
    private static double altitudeOf(SkyScene.Item item, float timeDeg) {
        //sol = YP(-90) XP(t) ciel : hauteur = composante verticale
        double t = Math.toRadians(timeDeg);
        double y1 = item.y * Math.cos(t) - item.z * Math.sin(t);
        return Math.asin(Math.max(-1, Math.min(1, y1)));
    }

    private static void plumeVertex(BufferBuilder builder, Matrix4f matrix, double alt, double az, float timeDeg, float r, float g, float b, float a) {
        SkyScene.ground(alt, az, timeDeg, PG);
        builder.vertex(matrix, (float) (R * PG[0]), (float) (R * PG[1]), (float) (R * PG[2])).color(r, g, b, Math.max(0, a)).endVertex();
    }

    /** Anneau (Diademe) : arc est - sommet - ouest ; eclat de chaque morceau par {@link SkyScene#ringShade} (ombre, jour, terminateur). */
    private static final int RING_STEPS = 72;
    private static final double[] RS = new double[RING_STEPS + 1];

    private static void drawRingArc(Matrix4f matrix, SkyScene.Item item, Style style) {
        flushPoints();
        int rgb = fr.pioneers.space.mega.Megastructures.color(item.mega);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        float night = Math.min(1, style.starBrightness * 2), fade = 1 - style.rain * 0.7F;
        //base orthonormee du plan de l'arc : E (est) et A' (sommet sans sa part selon E)
        double ex = item.bx, ey = item.by, ez = item.bz;
        double d = item.x * ex + item.y * ey + item.z * ez;
        double ax = item.x - d * ex, ay = item.y - d * ey, az = item.z - d * ez;
        double al = Math.max(1e-9, Math.sqrt(ax * ax + ay * ay + az * az));
        ax /= al;
        ay /= al;
        az /= al;
        //normale du plan : largeur de la bande
        double nx = ey * az - ez * ay, ny = ez * ax - ex * az, nz = ex * ay - ey * ax;
        double w = R * Math.tan(Math.toRadians(item.width));
        boolean glints = false;
        for (int i = 0; i <= RING_STEPS; i++) {
            double th = Math.PI * i / RING_STEPS;
            double c = Math.cos(th), sn = Math.sin(th);
            double px = c * ex + sn * ax, py = c * ey + sn * ay, pz = c * ez + sn * az;
            RS[i] = SkyScene.ringShade(item, px, py, pz);
            glints |= RS[i] > 1.0;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        normal();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < RING_STEPS; i++) {
            ringQuad(builder, matrix, item, ex, ey, ez, ax, ay, az, nx, ny, nz, i, w, cr, cg, cb, night, fade, 1, false);
        }
        BufferUploader.drawWithShader(builder.end());
        if (glints) {
            additive();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < RING_STEPS; i++) {
                ringQuad(builder, matrix, item, ex, ey, ez, ax, ay, az, nx, ny, nz, i, w, cr, cg, cb, night, fade, 3, true);
            }
            BufferUploader.drawWithShader(builder.end());
        }
        normal();
    }

    private static void ringQuad(BufferBuilder builder, Matrix4f matrix, SkyScene.Item item, double ex, double ey, double ez, double ax, double ay,
          double az, double nx, double ny, double nz, int i, double w, float cr, float cg, float cb, float night, float fade, double widen,
          boolean glow) {
        for (int k = 0; k < 4; k++) {
            int j = k == 0 || k == 3 ? i : i + 1;
            double side = k < 2 ? -widen : widen;
            double th = Math.PI * j / RING_STEPS;
            double c = Math.cos(th), sn = Math.sin(th);
            double px = c * ex + sn * ax, py = c * ey + sn * ay, pz = c * ez + sn * az;
            double b = RS[j];
            float r, g, bl, a;
            if (glow) {
                double e = Math.max(0, b - 1);
                r = cr;
                g = cg;
                bl = cb;
                a = (float) Math.min(0.8, 0.45 * e) * fade;
            } else if (b <= 0) {
                r = 0.10F;
                g = 0.09F;
                bl = 0.12F;
                a = (0.25F + 0.2F * night) * fade;
            } else {
                float k1 = (float) Math.min(1.4, b);
                r = Math.min(1, cr * k1);
                g = Math.min(1, cg * k1);
                bl = Math.min(1, cb * k1);
                a = (0.5F + 0.4F * night) * fade;
            }
            builder.vertex(matrix, (float) (R * px + nx * w * side), (float) (R * py + ny * w * side), (float) (R * pz + nz * w * side))
                  .color(r, g, bl, a).endVertex();
        }
    }

    /**
     * Cable d'un ascenseur spatial : arc de grand cercle de la base au sommet, bande fine qui s'affine vers le haut. La part a l'ombre est
     * une silhouette sombre (feux rouges la nuit), la part eclairee prend la couleur de la megastructure ; l'eclat de l'aube et du crepuscule
     * ajoute un halo additif sur la part eclairee.
     */
    private static void drawTether(Matrix4f matrix, SkyScene.Item item, Style style) {
        flushPoints();
        int rgb = fr.pioneers.space.mega.Megastructures.color(item.mega);
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        float night = Math.min(1, style.starBrightness * 2), fade = 1 - style.rain * 0.7F;
        double ax = item.bx, ay = item.by, az = item.bz, bx = item.x, by = item.y, bz = item.z;
        double omega = Math.acos(Math.max(-1, Math.min(1, ax * bx + ay * by + az * bz)));
        double so = Math.max(1e-6, Math.sin(omega));
        for (int i = 0; i <= TETHER_STEPS; i++) {
            double s = i / (double) TETHER_STEPS;
            double ka = Math.sin((1 - s) * omega) / so, kb = Math.sin(s * omega) / so;
            double px = ka * ax + kb * bx, py = ka * ay + kb * by, pz = ka * az + kb * bz;
            TX[i] = px;
            TY[i] = py;
            TZ[i] = pz;
            //tangente approchee (vers le sommet) puis largeur = point x tangente
            double tx = bx - ax, ty = by - ay, tz = bz - az;
            double wx = py * tz - pz * ty, wy = pz * tx - px * tz, wz = px * ty - py * tx;
            double wl = Math.max(1e-6, Math.sqrt(wx * wx + wy * wy + wz * wz));
            //largeur : la base est plus proche (plus large), le sommet s'affine
            double w = R * Math.tan(Math.toRadians(item.width)) * (1.6 - 1.2 * s);
            WX[i] = wx / wl * w;
            WY[i] = wy / wl * w;
            WZ[i] = wz / wl * w;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        //silhouette et part eclairee (melange normal)
        normal();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < TETHER_STEPS; i++) {
            double s = (i + 0.5) / TETHER_STEPS;
            boolean lit = s >= item.shadow;
            float k = lit ? 0.78F + 0.45F * (float) item.glint : 0.16F;
            float a = (lit ? 0.62F + 0.3F * night : 0.38F + 0.2F * night) * fade;
            float r = lit ? Math.min(1, cr * k) : 0.13F, g = lit ? Math.min(1, cg * k) : 0.10F, b = lit ? Math.min(1, cb * k) : 0.17F;
            stripQuad(builder, matrix, i, r, g, b, a, 1);
        }
        BufferUploader.drawWithShader(builder.end());
        //eclat de l'aube et du crepuscule, puis feux de balisage de la part a l'ombre (additifs)
        boolean glint = item.glint > 0.03 && item.shadow < 1 - 1.0 / TETHER_STEPS, beacons = night > 0.2F && item.shadow > 4.5 / TETHER_STEPS;
        if (glint || beacons) {
            additive();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            //un quad au moins : le premier morceau eclaire ou le premier feu
            for (int i = 0; glint && i < TETHER_STEPS; i++) {
                if ((i + 0.5) / TETHER_STEPS >= item.shadow) {
                    stripQuad(builder, matrix, i, cr, cg, cb, (float) (0.5 * item.glint) * fade, 3.5);
                }
            }
            for (int i = 4; beacons && i < TETHER_STEPS; i += 9) {
                float a = (i + 0.5) / TETHER_STEPS < item.shadow ? 0.9F * night * fade : 0;
                stripQuad(builder, matrix, i, 1, 0.25F, 0.2F, a, 2.6);
            }
            BufferUploader.drawWithShader(builder.end());
        }
        normal();
    }

    /** Morceau i de la bande du cable, elargi de {@code widen}. */
    private static void stripQuad(BufferBuilder builder, Matrix4f matrix, int i, float r, float g, float b, float a, double widen) {
        int j = i + 1;
        builder.vertex(matrix, (float) (R * TX[i] - WX[i] * widen), (float) (R * TY[i] - WY[i] * widen), (float) (R * TZ[i] - WZ[i] * widen))
              .color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (R * TX[i] + WX[i] * widen), (float) (R * TY[i] + WY[i] * widen), (float) (R * TZ[i] + WZ[i] * widen))
              .color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (R * TX[j] + WX[j] * widen), (float) (R * TY[j] + WY[j] * widen), (float) (R * TZ[j] + WZ[j] * widen))
              .color(r, g, b, a).endVertex();
        builder.vertex(matrix, (float) (R * TX[j] - WX[j] * widen), (float) (R * TY[j] - WY[j] * widen), (float) (R * TZ[j] - WZ[j] * widen))
              .color(r, g, b, a).endVertex();
    }

    /**
     * Marque d'une megastructure sur le disque de son astre vu d'ailleurs : un fil lumineux qui part de l'equateur du disque et depasse son
     * bord (le cable de l'ascenseur jusqu'a l'orbite), a la couleur de la megastructure.
     */
    /** Coordonnees d'un feu sur le disque (rendu, fil du client seulement). */
    private static final double[] MARK = new double[2];

    private static void drawMarks(Matrix4f matrix, SkyScene.Item item, double h, double nx, double ny, double nz, double rx, double ry, double rz,
          double upx, double upy, double upz, Style style) {
        additive();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float fade = 1 - style.rain * 0.8F;
        double w = Math.max(0.02, h * 0.035);
        for (int i = 0; i < fr.pioneers.space.mega.Megastructures.COUNT; i++) {
            if ((item.megaMarks & (1L << i)) == 0) {
                continue;
            }
            int rgb = fr.pioneers.space.mega.Megastructures.color(i);
            float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
            int megaKind = fr.pioneers.space.mega.Megastructures.skyKind(i);
            if (megaKind == SkyScene.MEGA_SWARM || megaKind == SkyScene.MEGA_LATTICE) {
                //1.0.7 (lots 9 et 10) : l'Essaim est dessine avec le Soleil ; le Halo ne se voit que de son astre
                continue;
            }
            if (megaKind == SkyScene.MEGA_PLUME) {
                //1.0.7 (lot 9) : lueur d'un puits ou d'une plaine givree (Grand Forage, Coeur Froid) : une tache douce sur le disque
                double qx = R * nx + h * (0.25 * rx - 0.2 * upx), qy = R * ny + h * (0.25 * ry - 0.2 * upy), qz = R * nz + h * (0.25 * rz - 0.2 * upz);
                double d = h * 0.22;
                builder.vertex(matrix, (float) (qx - rx * d), (float) (qy - ry * d), (float) (qz - rz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) (qx - upx * d), (float) (qy - upy * d), (float) (qz - upz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) (qx + rx * d), (float) (qy + ry * d), (float) (qz + rz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) qx, (float) qy, (float) qz).color(cr, cg, cb, 0.85F * fade).endVertex();
                builder.vertex(matrix, (float) (qx + rx * d), (float) (qy + ry * d), (float) (qz + rz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) (qx + upx * d), (float) (qy + upy * d), (float) (qz + upz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) (qx - rx * d), (float) (qy - ry * d), (float) (qz - rz * d)).color(cr, cg, cb, 0).endVertex();
                builder.vertex(matrix, (float) qx, (float) qy, (float) qz).color(cr, cg, cb, 0.85F * fade).endVertex();
                continue;
            }
            if (megaKind == SkyScene.MEGA_ROTOR) {
                //1.0.7 (lot 8) : l'Anneau-Rotor se voit sur les anneaux eux-memes (drawRing)
                continue;
            }
            if (megaKind == SkyScene.MEGA_AURORA) {
                //1.0.7 (lot 8) : aurore permanente : une couronne lumineuse autour du bord du disque
                for (int k = 0; k < 48; k++) {
                    double f0 = 2 * Math.PI * k / 48, f1 = 2 * Math.PI * (k + 1) / 48;
                    double c0 = Math.cos(f0), s0 = Math.sin(f0), c1 = Math.cos(f1), s1 = Math.sin(f1);
                    double i0 = h * 1.02, o0 = h * 1.3;
                    builder.vertex(matrix, (float) (R * nx + i0 * (c0 * rx + s0 * upx)), (float) (R * ny + i0 * (c0 * ry + s0 * upy)),
                          (float) (R * nz + i0 * (c0 * rz + s0 * upz))).color(cr, cg, cb, 0.55F * fade).endVertex();
                    builder.vertex(matrix, (float) (R * nx + i0 * (c1 * rx + s1 * upx)), (float) (R * ny + i0 * (c1 * ry + s1 * upy)),
                          (float) (R * nz + i0 * (c1 * rz + s1 * upz))).color(cr, cg, cb, 0.55F * fade).endVertex();
                    builder.vertex(matrix, (float) (R * nx + o0 * (c1 * rx + s1 * upx)), (float) (R * ny + o0 * (c1 * ry + s1 * upy)),
                          (float) (R * nz + o0 * (c1 * rz + s1 * upz))).color(cr, cg, cb, 0).endVertex();
                    builder.vertex(matrix, (float) (R * nx + o0 * (c0 * rx + s0 * upx)), (float) (R * ny + o0 * (c0 * ry + s0 * upy)),
                          (float) (R * nz + o0 * (c0 * rz + s0 * upz))).color(cr, cg, cb, 0).endVertex();
                }
                continue;
            }
            if (megaKind == SkyScene.MEGA_COLUMN) {
                //1.0.7 (lot 8) : la colonne de la Descente : un trait lumineux qui plonge dans le disque
                double cw = Math.max(0.01, h * 0.03), y0 = h * 0.2, y1 = -h * 0.75;
                double qx = R * nx, qy = R * ny, qz = R * nz;
                builder.vertex(matrix, (float) (qx + upx * y0 - rx * cw), (float) (qy + upy * y0 - ry * cw), (float) (qz + upz * y0 - rz * cw))
                      .color(cr, cg, cb, 0.95F * fade).endVertex();
                builder.vertex(matrix, (float) (qx + upx * y0 + rx * cw), (float) (qy + upy * y0 + ry * cw), (float) (qz + upz * y0 + rz * cw))
                      .color(cr, cg, cb, 0.95F * fade).endVertex();
                builder.vertex(matrix, (float) (qx + upx * y1 + rx * cw), (float) (qy + upy * y1 + ry * cw), (float) (qz + upz * y1 + rz * cw))
                      .color(cr, cg, cb, 0.15F * fade).endVertex();
                builder.vertex(matrix, (float) (qx + upx * y1 - rx * cw), (float) (qy + upy * y1 - ry * cw), (float) (qz + upz * y1 - rz * cw))
                      .color(cr, cg, cb, 0.15F * fade).endVertex();
                continue;
            }
            if (megaKind == SkyScene.MEGA_LIGHTS) {
                //1.0.7 (lot 7) : feux de la cite flottante sur la face de nuit du disque
                double[] p = fr.pioneers.space.mega.Megastructures.skyParams(i);
                double d = Math.max(0.012, h * 0.025);
                for (int k = 0; k < (int) p[0]; k++) {
                    double e = SkyScene.cityMark(p, k, rx, ry, rz, upx, upy, upz, nx, ny, nz, item.lx, item.ly, item.lz, MARK);
                    if (e <= 0.01) {
                        continue;
                    }
                    double qx = R * nx + h * (MARK[0] * rx + MARK[1] * upx), qy = R * ny + h * (MARK[0] * ry + MARK[1] * upy),
                          qz = R * nz + h * (MARK[0] * rz + MARK[1] * upz);
                    float a = (float) (e * fade);
                    builder.vertex(matrix, (float) (qx - rx * d - upx * d), (float) (qy - ry * d - upy * d), (float) (qz - rz * d - upz * d))
                          .color(cr, cg, cb, a).endVertex();
                    builder.vertex(matrix, (float) (qx + rx * d - upx * d), (float) (qy + ry * d - upy * d), (float) (qz + rz * d - upz * d))
                          .color(cr, cg, cb, a).endVertex();
                    builder.vertex(matrix, (float) (qx + rx * d + upx * d), (float) (qy + ry * d + upy * d), (float) (qz + rz * d + upz * d))
                          .color(cr, cg, cb, a).endVertex();
                    builder.vertex(matrix, (float) (qx - rx * d + upx * d), (float) (qy - ry * d + upy * d), (float) (qz - rz * d + upz * d))
                          .color(cr, cg, cb, a).endVertex();
                }
                continue;
            }
            //axe du fil : la droite du disque (equateur), largeur selon le haut du disque (n x r)
            double ux = ny * rz - nz * ry, uy = nz * rx - nx * rz, uz = nx * ry - ny * rx;
            double cx = R * nx, cy = R * ny, cz = R * nz;
            double a0 = h * 0.15, a1 = h * 1.7;
            builder.vertex(matrix, (float) (cx + rx * a0 - ux * w), (float) (cy + ry * a0 - uy * w), (float) (cz + rz * a0 - uz * w))
                  .color(cr, cg, cb, 0.9F * fade).endVertex();
            builder.vertex(matrix, (float) (cx + rx * a0 + ux * w), (float) (cy + ry * a0 + uy * w), (float) (cz + rz * a0 + uz * w))
                  .color(cr, cg, cb, 0.9F * fade).endVertex();
            builder.vertex(matrix, (float) (cx + rx * a1 + ux * w * 0.4), (float) (cy + ry * a1 + uy * w * 0.4), (float) (cz + rz * a1 + uz * w * 0.4))
                  .color(cr, cg, cb, 0.1F * fade).endVertex();
            builder.vertex(matrix, (float) (cx + rx * a1 - ux * w * 0.4), (float) (cy + ry * a1 - uy * w * 0.4), (float) (cz + rz * a1 - uz * w * 0.4))
                  .color(cr, cg, cb, 0.1F * fade).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /**
     * Disque texture maille, eclaire comme une sphere : chaque sommet recoit la lumiere du Soleil selon la normale de la
     * sphere a cet endroit (phases, terminateur, leger assombrissement du bord).
     */
    private static void disc(Matrix4f matrix, double nx, double ny, double nz, double rx, double ry, double rz, double ux, double uy, double uz,
          double h, ResourceLocation texture, SkyScene.Item item, float ambient, float cr, float cg, float cb, float alpha, boolean lit) {
        int n = item.visual > 8 ? 24 : 12;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] shade = new float[(n + 1) * (n + 1)];
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                double x = -1 + 2.0 * i / n, y = -1 + 2.0 * j / n;
                double r2 = x * x + y * y;
                double sx = x, sy = y, sz;
                if (r2 > 1) {
                    double r = Math.sqrt(r2);
                    sx /= r;
                    sy /= r;
                    sz = 0;
                } else {
                    sz = Math.sqrt(1 - r2);
                }
                //normale de la sphere (vers l'observateur = -n)
                double gx = sx * rx + sy * ux - sz * nx, gy = sx * ry + sy * uy - sz * ny, gz = sx * rz + sy * uz - sz * nz;
                double mu0 = gx * item.lx + gy * item.ly + gz * item.lz;
                double b = 0;
                if (mu0 > 0) {
                    b = Math.min(1, 2 * mu0 / (mu0 + sz + 1e-3)) * (0.72 + 0.28 * sz);
                }
                shade[j * (n + 1) + i] = lit ? (float) (ambient + (1 - ambient) * b) : 1;
            }
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                discVertex(builder, matrix, n, i, j, nx, ny, nz, rx, ry, rz, ux, uy, uz, h, shade, cr, cg, cb, alpha);
                discVertex(builder, matrix, n, i + 1, j, nx, ny, nz, rx, ry, rz, ux, uy, uz, h, shade, cr, cg, cb, alpha);
                discVertex(builder, matrix, n, i + 1, j + 1, nx, ny, nz, rx, ry, rz, ux, uy, uz, h, shade, cr, cg, cb, alpha);
                discVertex(builder, matrix, n, i, j + 1, nx, ny, nz, rx, ry, rz, ux, uy, uz, h, shade, cr, cg, cb, alpha);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void discVertex(BufferBuilder builder, Matrix4f matrix, int n, int i, int j, double nx, double ny, double nz, double rx,
          double ry, double rz, double ux, double uy, double uz, double h, float[] shade, float cr, float cg, float cb, float alpha) {
        double x = -1 + 2.0 * i / n, y = -1 + 2.0 * j / n;
        float px = (float) (R * nx + h * (x * rx + y * ux));
        float py = (float) (R * ny + h * (x * ry + y * uy));
        float pz = (float) (R * nz + h * (x * rz + y * uz));
        float s = shade[j * (n + 1) + i];
        builder.vertex(matrix, px, py, pz).uv((float) (x + 1) / 2, (float) (1 - y) / 2).color(cr * s, cg * s, cb * s, alpha).endVertex();
    }

    /** Anneaux de Saturne : un anneau plat en 3D (vu de biais, ou par la tranche depuis Encelade), en deux moities. */
    private static void drawRing(Matrix4f matrix, SkyScene.Item item, double h, boolean front, Style style) {
        double px = item.px, py = item.py, pz = item.pz;
        double nx = item.x, ny = item.y, nz = item.z;
        //q1 = P x n (dans le plan, perpendiculaire a la visee), q2 = P x q1
        double ax = py * nz - pz * ny, ay = pz * nx - px * nz, az = px * ny - py * nx;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-5) {
            ax = 0;
            ay = 0;
            az = 1;
            al = 1;
        }
        ax /= al;
        ay /= al;
        az /= al;
        double bx = py * az - pz * ay, by = pz * ax - px * az, bz = px * ay - py * ax;
        //Moitie proche : celle dont q2 pointe vers l'observateur (-n)
        double towardViewer = -(bx * nx + by * ny + bz * nz);
        double sign = towardViewer >= 0 ? 1 : -1;
        //Projection dans le plan du ciel : une ellipse plate (petit axe = sinus de l'ouverture), qui reste loin de la camera
        bx += towardViewer * nx;
        by += towardViewer * ny;
        bz += towardViewer * nz;
        double lightSide = px * item.lx + py * item.ly + pz * item.lz;
        double viewSide = -(px * nx + py * ny + pz * nz);
        float bright = (float) (0.35 + 0.65 * Math.sqrt(Math.abs(lightSide)));
        if (lightSide * viewSide < 0) {
            bright *= 0.35F;
        }
        double size = h * 2.27;
        double cx = R * nx, cy = R * ny, cz = R * nz;
        double v0 = front ? 0 : -1, v1 = front ? 1 : 0;
        if (style.atmosphere) {
            additive();
        } else {
            normal();
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, RING);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        double[][] corners = {{-1, v0}, {1, v0}, {1, v1}, {-1, v1}};
        for (double[] c : corners) {
            double a = c[0] * size, b = c[1] * sign * size;
            builder.vertex(matrix, (float) (cx + a * ax + b * bx), (float) (cy + a * ay + b * by), (float) (cz + a * az + b * bz))
                  .uv((float) (c[0] + 1) / 2, (float) (c[1] * sign + 1) / 2).color(bright, bright, bright, item.discAlpha).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        //1.0.7 (lot 8) : l'Anneau-Rotor, vu d'une lune : des segments reguliers plus vifs sur les anneaux, qui tournent lentement
        for (int i = 0; i < fr.pioneers.space.mega.Megastructures.COUNT; i++) {
            if ((item.megaMarks & (1L << i)) == 0 || fr.pioneers.space.mega.Megastructures.skyKind(i) != SkyScene.MEGA_ROTOR) {
                continue;
            }
            double[] p = fr.pioneers.space.mega.Megastructures.skyParams(i);
            int rgb = fr.pioneers.space.mega.Megastructures.color(i);
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            double ticks = RenderClock.now();
            double d = Math.max(0.012, h * 0.05);
            additive();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int n = (int) p[0];
            for (int k = 0; k < n; k++) {
                double f = 2 * Math.PI * k / n + 2 * Math.PI * ticks * p[5] / n;
                double v = Math.sin(f) * sign;
                if (v < v0 || v > v1) {
                    continue;
                }
                double ea = Math.cos(f) * size * 0.8, eb = Math.sin(f) * size * 0.8;
                double qx = cx + ea * ax + eb * bx, qy = cy + ea * ay + eb * by, qz = cz + ea * az + eb * bz;
                float alpha = (float) (Math.min(1, p[1]) * bright * item.discAlpha);
                builder.vertex(matrix, (float) (qx - ax * d), (float) (qy - ay * d), (float) (qz - az * d)).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, (float) (qx - bx * d * 0.5), (float) (qy - by * d * 0.5), (float) (qz - bz * d * 0.5)).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, (float) (qx + ax * d), (float) (qy + ay * d), (float) (qz + az * d)).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, (float) (qx + bx * d * 0.5), (float) (qy + by * d * 0.5), (float) (qz + bz * d * 0.5)).color(r, g, b, alpha).endVertex();
            }
            BufferUploader.drawWithShader(builder.end());
            if (!style.atmosphere) {
                normal();
            }
        }
    }

    // ---------------------------------------------------------------- Asteroides proches

    private static void drawRocks(Matrix4f matrix, SkyScene scene, ClientLevel level, float partialTick, double t) {
        double ticks = RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue
        double cos = Math.cos(scene.sunLongitude), sin = Math.sin(scene.sunLongitude);
        double[] base = new double[3];
        normal();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (int k = 0; k < ROCKS.length; k++) {
            RenderSystem.setShaderTexture(0, ROCKS[k]);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (double[] rock : ROCK_DATA) {
                if ((int) rock[5] != k) {
                    continue;
                }
                double lon = Math.toRadians(rock[0] + rock[2] * t), lat = Math.toRadians(rock[1]);
                SkyScene.toBase(Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat), base);
                //rotation XP(longitude du Soleil)
                double x = base[0], y = base[1] * cos - base[2] * sin, z = base[1] * sin + base[2] * cos;
                double sep = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, y))));
                if (sep < scene.sun.visual * 1.2) {
                    continue;
                }
                float b = (float) (0.16 + 0.84 * Math.pow((1 - y) / 2, 0.8));
                double size = R * Math.tan(Math.toRadians(rock[3]));
                double roll = Math.toRadians(rock[6] + rock[4] * ticks);
                texturedQuad(builder, matrix, x, y, z, size, roll, b, b, b, 1);
            }
            BufferUploader.drawWithShader(builder.end());
        }
    }

    // ---------------------------------------------------------------- Outils

    /** Coins d'un quad (signes droite, haut) et leurs UV, dans l'ordre de dessin. */
    private static final int[][] SIGNS = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
    private static final float[][] UVS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /** Quad texture face a l'observateur, centre sur la direction (x, y, z). */
    private static void quad(Matrix4f matrix, double x, double y, double z, double size, double roll, ResourceLocation texture, float r, float g,
          float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texturedQuad(builder, matrix, x, y, z, size, roll, r, g, b, a);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void texturedQuad(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, double size, double roll, float r,
          float g, float b, float a) {
        double[] basis = basis(x, y, z, roll);
        for (int i = 0; i < 4; i++) {
            double sx = SIGNS[i][0] * size, sy = SIGNS[i][1] * size;
            builder.vertex(matrix, (float) (R * x + sx * basis[0] + sy * basis[3]), (float) (R * y + sx * basis[1] + sy * basis[4]),
                  (float) (R * z + sx * basis[2] + sy * basis[5])).uv(UVS[i][0], UVS[i][1]).color(r, g, b, a).endVertex();
        }
    }

    /** Quad colore (sans texture) face a l'observateur, ajoute a {@code builder} (0.20.46 (audit) : ex-coloredQuad, un dessin par point). */
    private static void coloredVertices(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, double size, float r, float g, float b,
          float a) {
        double[] basis = basis(x, y, z, Math.PI / 4);
        for (int[] s : SIGNS) {
            double sx = s[0] * size, sy = s[1] * size;
            builder.vertex(matrix, (float) (R * x + sx * basis[0] + sy * basis[3]), (float) (R * y + sx * basis[1] + sy * basis[4]),
                  (float) (R * z + sx * basis[2] + sy * basis[5])).color(r, g, b, a).endVertex();
        }
    }

    /** Deux axes perpendiculaires a la direction (droite puis haut), tournes de {@code roll}. */
    private static double[] basis(double x, double y, double z, double roll) {
        //haut de reference : le nord de l'ecliptique (-X), sinon +Z
        double ux = -1, uy = 0, uz = 0;
        if (Math.abs(x) > 0.95) {
            ux = 0;
            uz = 1;
        }
        double d = ux * x + uy * y + uz * z;
        ux -= d * x;
        uy -= d * y;
        uz -= d * z;
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l;
        uy /= l;
        uz /= l;
        double rx = y * uz - z * uy, ry = z * ux - x * uz, rz = x * uy - y * ux;
        double c = Math.cos(roll), s = Math.sin(roll);
        return new double[]{rx * c + ux * s, ry * c + uy * s, rz * c + uz * s, ux * c - rx * s, uy * c - ry * s, uz * c - rz * s};
    }
}
