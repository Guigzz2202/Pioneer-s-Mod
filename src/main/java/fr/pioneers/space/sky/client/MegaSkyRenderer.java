package fr.pioneers.space.sky.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mega.client.MegaClient;
import fr.pioneers.space.sky.client.mega.MegaBuffer;
import fr.pioneers.space.sky.client.mega.MegaModels;
import fr.pioneers.space.sky.client.mega.MegaView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 1.0.7 (« ciel grandiose ») : dessine les megastructures en 3D de leur astre ({@link MegaModels}, calcul pur partage avec le banc).
 * Appele par {@link CelestialSkyRenderer} apres les astres, dans le repere de la camera (repere du sol : x est, y haut, z sud) :
 * <ol>
 *     <li>faces pleines, deja eclairees, de la plus lointaine a la plus proche (peintre ; pas de tampon de profondeur dans le ciel) ;</li>
 *     <li>lueurs en melange additif : points (feux, hublots, halos, eclats) puis traits (faisceaux, cables, anneaux).</li>
 * </ol>
 * L'animation suit l'horloge de rendu ({@link RenderClock}) et l'activite de la megastructure ({@link MegaClient#activity}). Tampons
 * preallouees : aucune allocation par image.
 */
public final class MegaSkyRenderer {

    private static final ResourceLocation DOT = Pioneers.rl("textures/environment/sky/mega_dot.png");
    private static final ResourceLocation LINE = Pioneers.rl("textures/environment/sky/mega_line.png");
    private static final MegaBuffer BUF = new MegaBuffer();
    private static final MegaView VIEW = new MegaView();
    private static final double[] SUN = new double[3];

    private MegaSkyRenderer() {
    }

    /** Vrai si le ciel de cette scene montre au moins une megastructure en 3D (l'Essaim : dans tous les ciels ou le Soleil est la). */
    public static boolean any(SkyScene scene) {
        for (SkyScene.Item item : scene.items) {
            if (item.type == SkyScene.MEGA && MegaModels.has(item.mega)) {
                return true;
            }
        }
        return swarm(scene) >= 0;
    }

    /** L'Essaim, s'il est debloque et que le Soleil est dans cette scene, sinon -1. */
    private static int swarm(SkyScene scene) {
        if (scene.sun == null) {
            return -1;
        }
        long mask = MegaClient.unlockedMask();
        for (int i = 0; i < Megastructures.COUNT && i < 64; i++) {
            if (MegaModels.everywhere(i) && (mask & (1L << i)) != 0) {
                return i;
            }
        }
        return -1;
    }

    /** Lumiere du dernier rendu (captures : ou regarder l'Essaim et le Halo). */
    public static MegaView view() {
        return VIEW;
    }

    /**
     * 1.0.7 : megastructure d'une geante (Cite des nuages, Anneau-Rotor, Boucle, Descente) dans son ciel propre ({@code ExoSkyEffects}) :
     * Soleil a l'heure du monde, atmosphere, pluie ; {@code poseStack} : pose de la camera.
     */
    public static void renderGiant(net.minecraft.client.multiplayer.ClientLevel level, int i, float partialTick, PoseStack poseStack) {
        double t = level.getTimeOfDay(partialTick) * 2 * Math.PI;
        VIEW.sun(-Math.sin(t), Math.cos(t), 0, 1, 0.96, 0.9, true, level.getRainLevel(partialTick));
        int sky = Megastructures.color(i);
        VIEW.hazeR = (sky >> 16 & 255) / 255.0 * 0.6;
        VIEW.hazeG = (sky >> 8 & 255) / 255.0 * 0.6;
        VIEW.hazeB = (sky & 255) / 255.0 * 0.6;
        VIEW.ticks = RenderClock.time(partialTick);
        BUF.clear();
        BUF.drop = horizonDrop();
        add(i);
        draw(poseStack.last().pose());
    }

    private static void add(int i) {
        VIEW.params = Megastructures.skyParams(i);
        VIEW.color = Megastructures.color(i);
        VIEW.accent = Megastructures.accent(i);
        VIEW.activity = MegaClient.activity(i);
        MegaModels.build(i, BUF, VIEW);
    }

    /**
     * Dessine les megastructures en 3D de la scene. {@code poseStack} : pose de la camera (rotation seule), comme pour le ciel vanilla.
     */
    public static void render(SkyScene scene, float partialTick, PoseStack poseStack, CelestialSkyRenderer.Style style, float timeDeg) {
        if (!any(scene)) {
            return;
        }
        //lumiere du moment : Soleil dans le repere du sol (sous l'horizon la nuit), teinte de l'Essaim
        if (scene.sun != null) {
            SkyScene.toGround(scene.sun.x, scene.sun.y, scene.sun.z, timeDeg, SUN);
        } else {
            SUN[0] = 0;
            SUN[1] = Math.cos(Math.toRadians(timeDeg));
            SUN[2] = 0;
        }
        double light = scene.sunLight;
        VIEW.sun(SUN[0], SUN[1], SUN[2], light * scene.sunTintR, light * scene.sunTintG, light * scene.sunTintB, style.atmosphere, style.rain);
        VIEW.hazeR = style.skyR;
        VIEW.hazeG = style.skyG;
        VIEW.hazeB = style.skyB;
        VIEW.ticks = RenderClock.time(partialTick);
        BUF.clear();
        BUF.drop = horizonDrop();
        for (SkyScene.Item item : scene.items) {
            if (item.type == SkyScene.MEGA && MegaModels.has(item.mega) && !MegaModels.everywhere(item.mega)) {
                add(item.mega);
            }
        }
        int swarm = swarm(scene);
        if (swarm >= 0) {
            add(swarm);
        }
        draw(poseStack.last().pose());
    }

    /** Sol moyen sous la camera (lisse : un precipice ne fait pas sauter le ciel), NaN avant la premiere image. */
    private static double ground = Double.NaN;

    /**
     * Plongee de l'horizon (tangente) : le relief visible s'arrete a la distance de vue ; vu de {@code h} blocs au-dessus du sol moyen, son
     * bord est {@code h / distance} sous l'horizontale. La megastructure descend d'autant (elle ne flotte plus au-dessus du relief).
     */
    private static double horizonDrop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(cam.x), Mth.floor(cam.z));
        if (h > mc.level.getMinBuildHeight()) {
            ground = Double.isNaN(ground) ? h : ground + (h - ground) * 0.01;
        }
        if (Double.isNaN(ground)) {
            return 0;
        }
        double above = Math.max(0, cam.y - ground);
        double reach = Math.max(32, mc.options.getEffectiveRenderDistance() * 16);
        return Math.min(0.7, above / reach);
    }

    private static void draw(Matrix4f m) {
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        //faces pleines, de la plus lointaine a la plus proche
        if (BUF.solidCount > 0) {
            BUF.sort();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float[] t = BUF.solid;
            for (int k = 0; k < BUF.solidCount; k++) {
                int o = BUF.solidAt(k) * MegaBuffer.STRIDE;
                float r = t[o + 12], g = t[o + 13], b = t[o + 14], a = t[o + 15];
                for (int v = 0; v < 4; v++) {
                    builder.vertex(m, t[o + v * 3], t[o + v * 3 + 1], t[o + v * 3 + 2]).color(r, g, b, a).endVertex();
                }
            }
            BufferUploader.drawWithShader(builder.end());
        }
        //lueurs additives
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
              GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        glows(builder, m, BUF.dots, BUF.dotCount, DOT);
        glows(builder, m, BUF.lines, BUF.lineCount, LINE);
        RenderSystem.defaultBlendFunc();
    }

    private static void glows(BufferBuilder builder, Matrix4f m, float[] t, int count, ResourceLocation texture) {
        if (count == 0) {
            return;
        }
        RenderSystem.setShaderTexture(0, texture);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int k = 0; k < count; k++) {
            int o = k * MegaBuffer.STRIDE;
            float r = t[o + 12], g = t[o + 13], b = t[o + 14], a = t[o + 15];
            builder.vertex(m, t[o], t[o + 1], t[o + 2]).uv(0, 0).color(r, g, b, a).endVertex();
            builder.vertex(m, t[o + 3], t[o + 4], t[o + 5]).uv(1, 0).color(r, g, b, a).endVertex();
            builder.vertex(m, t[o + 6], t[o + 7], t[o + 8]).uv(1, 1).color(r, g, b, a).endVertex();
            builder.vertex(m, t[o + 9], t[o + 10], t[o + 11]).uv(0, 1).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }
}
