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
        quad(matrix, 0, 1, 0, halo, 0, HALO, 1, 1, 1, haloAlpha * fade);
        quad(matrix, 0, 1, 0, core * 16 / 14.0, 0, SUN, 1, 1, 1, fade);
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
        normal();
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
