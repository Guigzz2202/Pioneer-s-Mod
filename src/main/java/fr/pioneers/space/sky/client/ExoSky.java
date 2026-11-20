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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ciel des exoplanetes (Alpha du Centaure, TRAPPIST-1) et, plus simple, des geantes gazeuses : un dome de couleur (celle du biome au
 * zenith, celle du brouillard a l'horizon), les etoiles lointaines, puis les astres de la scene : soleils (halo additif) et planetes
 * voisines (disques eclaires par le soleil principal, avec leurs phases). Un astre est soit mobile (il suit l'heure, comme le Soleil
 * vanilla), soit fixe (monde en rotation synchrone : son soleil ne bouge jamais).
 */
public final class ExoSky {

    private static final float R = 60;
    private static final ResourceLocation HALO = Pioneers.rl("textures/environment/sky/sun_halo.png");
    private static VertexBuffer stars;

    /** Un astre du ciel. */
    public static final class Obj {

        final ResourceLocation texture;
        final double radius;
        final boolean star;
        final float r, g, b;
        final boolean fixed;
        /** Mobile : decalage horaire et declinaison (degres). Fixe : azimut et hauteur (degres). */
        final double a, c;
        /** Halo d'un soleil : multiple du rayon. */
        double haloScale = 7;

        private Obj(String body, double radius, boolean star, int color, boolean fixed, double a, double c) {
            this.texture = Pioneers.rl("textures/environment/sky/" + body + ".png");
            this.radius = radius;
            this.star = star;
            this.r = (color >> 16 & 255) / 255F;
            this.g = (color >> 8 & 255) / 255F;
            this.b = (color & 255) / 255F;
            this.fixed = fixed;
            this.a = a;
            this.c = c;
        }

        /** Soleil ou planete qui suit l'heure du monde. */
        public static Obj moving(String body, double radius, boolean star, int color, double hourOffset, double declination) {
            return new Obj(body, radius, star, color, false, hourOffset, declination);
        }

        /** Astre immobile dans le ciel (azimut depuis le nord vers l'est, hauteur sur l'horizon). */
        public static Obj fixed(String body, double radius, boolean star, int color, double azimuth, double elevation) {
            return new Obj(body, radius, star, color, true, azimuth, elevation);
        }

        public Obj halo(double scale) {
            this.haloScale = scale;
            return this;
        }
    }

    public final boolean atmosphere;
    public final List<Obj> objects = new ArrayList<>();
    /** Visibilite des etoiles en plein jour (0 sous une atmosphere epaisse, 1 sans atmosphere). */
    public float dayStars = 0.0F;
    /** Ciel des geantes : pas d'etoiles, soleil diffus. */
    public boolean veiled;

    public ExoSky(boolean atmosphere, Obj... objects) {
        this.atmosphere = atmosphere;
        this.objects.addAll(List.of(objects));
    }

    public ExoSky dayStars(float alpha) {
        this.dayStars = alpha;
        return this;
    }

    public ExoSky veiled() {
        this.veiled = true;
        return this;
    }

    /**
     * 1.0.7 (lot 11) : premier soleil du ciel, pour la lumiere des megastructures en 3D : direction (repere du sol) et couleur ; faux s'il
     * n'y en a pas. Sans allocation (fil du rendu).
     */
    public boolean mainStar(float timeDeg, double[] dir, float[] rgb) {
        for (Obj o : objects) {
            if (o.star) {
                if (o.fixed) {
                    double az = Math.toRadians(o.a), el = Math.toRadians(o.c);
                    dir[0] = Math.cos(el) * Math.sin(az);
                    dir[1] = Math.sin(el);
                    dir[2] = -Math.cos(el) * Math.cos(az);
                } else {
                    double t = Math.toRadians(timeDeg + o.a), dec = Math.toRadians(o.c);
                    dir[0] = -Math.cos(dec) * Math.sin(t);
                    dir[1] = Math.cos(dec) * Math.cos(t);
                    dir[2] = -Math.sin(dec);
                }
                rgb[0] = o.r;
                rgb[1] = o.g;
                rgb[2] = o.b;
                return true;
            }
        }
        return false;
    }

    /** Direction (repere monde : x est, y haut, z sud) d'un astre a l'heure {@code timeDeg}. */
    private static double[] direction(Obj o, float timeDeg) {
        if (o.fixed) {
            double az = Math.toRadians(o.a), el = Math.toRadians(o.c);
            return new double[]{Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
        }
        //Comme le Soleil vanilla : YP(-90) puis XP(heure) appliques a (0, 1, 0) ; la declinaison decale l'astre vers le nord (-z)
        double t = Math.toRadians(timeDeg + o.a), dec = Math.toRadians(o.c);
        return new double[]{-Math.cos(dec) * Math.sin(t), Math.cos(dec) * Math.cos(t), -Math.sin(dec)};
    }

    public void render(ClientLevel level, float partialTick, PoseStack poseStack, Matrix4f projection, Vec3 camera) {
        if (stars == null) {
            stars = SkyStars.buildStars();
        }
        float timeDeg = level.getTimeOfDay(partialTick) * 360.0F;
        Vec3 skyColor = level.getSkyColor(camera, partialTick);
        float[] fog = RenderSystem.getShaderFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        FogRenderer.setupNoFog();
        Matrix4f matrix = poseStack.last().pose();
        //1. Dome : zenith a la couleur du ciel, horizon a celle du brouillard
        if (atmosphere) {
            dome(matrix, (float) skyColor.x, (float) skyColor.y, (float) skyColor.z, fog[0], fog[1], fog[2]);
        }
        //2. Etoiles lointaines (masquees par le jour sous une atmosphere)
        float starAlpha = veiled ? 0 : atmosphere ? Math.max(dayStars, level.getStarBrightness(partialTick) * 1.6F) : 1;
        if (starAlpha > 0.01F) {
            additive();
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(timeDeg * 0.25F + 30));
            RenderSystem.setShaderColor(1, 1, 1, Math.min(1, starAlpha));
            stars.bind();
            stars.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            poseStack.popPose();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        //3. Astres : la lumiere vient du premier soleil
        double[] light = null;
        for (Obj o : objects) {
            double[] d = direction(o, timeDeg);
            if (o.star && light == null) {
                light = d;
            }
        }
        if (light == null) {
            light = new double[]{0, 1, 0};
        }
        for (Obj o : objects) {
            double[] d = direction(o, timeDeg);
            double size = R * Math.tan(Math.toRadians(o.radius));
            if (o.star) {
                float veil = veiled ? 0.45F : 1;
                additive();
                quad(matrix, d, size * o.haloScale, HALO, o.r, o.g, o.b, (atmosphere ? 0.55F : 0.4F) * veil);
                quad(matrix, d, size * 1.15, o.texture, 1, 1, 1, veiled ? 0.6F : 1);
            } else {
                RenderSystem.defaultBlendFunc();
                litDisc(matrix, d, size, o.texture, light, atmosphere ? 0.12F : 0.03F);
            }
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

    /** Hemisphere en degrade, prolongee sous l'horizon par la couleur du brouillard assombrie. */
    private static void dome(Matrix4f matrix, float tr, float tg, float tb, float hr, float hg, float hb) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int rings = 8, segments = 24;
        for (int i = -2; i < rings; i++) {
            double lat0 = Math.PI / 2 * i / rings, lat1 = Math.PI / 2 * (i + 1) / rings;
            float t0 = (float) Math.max(0, Math.sin(lat0)), t1 = (float) Math.max(0, Math.sin(lat1));
            float d0 = i < 0 ? 0.6F : 1, d1 = i + 1 < 0 ? 0.6F : 1;
            for (int j = 0; j < segments; j++) {
                double a0 = 2 * Math.PI * j / segments, a1 = 2 * Math.PI * (j + 1) / segments;
                vertex(builder, matrix, lat0, a0, mix(hr, tr, t0) * d0, mix(hg, tg, t0) * d0, mix(hb, tb, t0) * d0);
                vertex(builder, matrix, lat0, a1, mix(hr, tr, t0) * d0, mix(hg, tg, t0) * d0, mix(hb, tb, t0) * d0);
                vertex(builder, matrix, lat1, a1, mix(hr, tr, t1) * d1, mix(hg, tg, t1) * d1, mix(hb, tb, t1) * d1);
                vertex(builder, matrix, lat1, a0, mix(hr, tr, t1) * d1, mix(hg, tg, t1) * d1, mix(hb, tb, t1) * d1);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static float mix(float a, float b, float t) {
        float k = (float) Math.pow(t, 0.6);
        return a + (b - a) * k;
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, double lat, double lon, float r, float g, float b) {
        float d = R * 1.5F;
        builder.vertex(matrix, (float) (Math.cos(lat) * Math.cos(lon) * d), (float) (Math.sin(lat) * d), (float) (Math.cos(lat) * Math.sin(lon) * d))
              .color(r, g, b, 1).endVertex();
    }

    /** Axes droite / haut d'un quad face a l'observateur dans la direction d. */
    private static double[] basis(double[] d) {
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(d[1]) > 0.95) {
            ux = 0;
            uy = 0;
            uz = 1;
        }
        double k = ux * d[0] + uy * d[1] + uz * d[2];
        ux -= k * d[0];
        uy -= k * d[1];
        uz -= k * d[2];
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l;
        uy /= l;
        uz /= l;
        double rx = d[1] * uz - d[2] * uy, ry = d[2] * ux - d[0] * uz, rz = d[0] * uy - d[1] * ux;
        return new double[]{rx, ry, rz, ux, uy, uz};
    }

    private static void quad(Matrix4f matrix, double[] d, double size, ResourceLocation texture, float r, float g, float b, float a) {
        double[] e = basis(d);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[][] sg = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (int i = 0; i < 4; i++) {
            double sx = sg[i][0] * size, sy = sg[i][1] * size;
            builder.vertex(matrix, (float) (R * d[0] + sx * e[0] + sy * e[3]), (float) (R * d[1] + sx * e[1] + sy * e[4]),
                  (float) (R * d[2] + sx * e[2] + sy * e[5])).uv(uv[i][0], uv[i][1]).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Disque eclaire comme une sphere par la lumiere {@code light} (phases, terminateur). */
    private static void litDisc(Matrix4f matrix, double[] d, double h, ResourceLocation texture, double[] light, float ambient) {
        double[] e = basis(d);
        int n = 12;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                discVertex(builder, matrix, n, i, j, d, e, h, light, ambient);
                discVertex(builder, matrix, n, i + 1, j, d, e, h, light, ambient);
                discVertex(builder, matrix, n, i + 1, j + 1, d, e, h, light, ambient);
                discVertex(builder, matrix, n, i, j + 1, d, e, h, light, ambient);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void discVertex(BufferBuilder builder, Matrix4f matrix, int n, int i, int j, double[] d, double[] e, double h, double[] light,
          float ambient) {
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
        double gx = sx * e[0] + sy * e[3] - sz * d[0], gy = sx * e[1] + sy * e[4] - sz * d[1], gz = sx * e[2] + sy * e[5] - sz * d[2];
        double mu = gx * light[0] + gy * light[1] + gz * light[2];
        float s = (float) (ambient + (1 - ambient) * Math.max(0, mu) * (0.75 + 0.25 * sz));
        float alpha = r2 > 1 ? 0 : 1;
        builder.vertex(matrix, (float) (R * d[0] + h * (x * e[0] + y * e[3])), (float) (R * d[1] + h * (x * e[1] + y * e[4])),
              (float) (R * d[2] + h * (x * e[2] + y * e[5]))).uv((float) (x + 1) / 2, (float) (1 - y) / 2).color(s, s, s, alpha).endVertex();
    }
}
