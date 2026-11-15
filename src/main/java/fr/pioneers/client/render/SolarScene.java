package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.registries.PiItems;
import fr.pioneers.simulation.SimulatedPlanet;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Scene du systeme solaire du Simulateur spatial (reprise de l'ancien Simulateur du systeme solaire). Quand le soleil brille, les six
 * projecteurs de faille ouvrent une bulle d'espace : le fond (la moitie opposee a la camera) est un ciel noir etoile, devant lequel se
 * detachent le soleil (dessine par {@link ChamberEffectRenderer}) et les planetes miniatures sur leurs orbites ; l'avant n'est qu'un voile.
 * Tout est dessine a l'echelle {@code scale} autour du centre (le globe de 25 est plus grand que l'ancien cube de 17).
 */
public final class SolarScene {

    /** Couleurs des faisceaux des projecteurs (constantes). */
    private static final float[] BEAM_BASE = {0.6F, 0.85F, 1}, BEAM_TIP = {0.25F, 0.5F, 1}, BEAM_CORE = {0.9F, 0.97F, 1}, BEAM_CORE_TIP = {0.5F, 0.8F, 1};

    public static final ResourceLocation STARFIELD = Pioneers.rl("textures/misc/simulator_starfield.png");
    public static final ResourceLocation FIELD = Pioneers.rl("textures/misc/simulator_field.png");
    /** Taille du soleil (meme echelle que le rate des chambres). */
    private static final int SUN_SIZE = 80;
    private static final float BUBBLE_RADIUS = 6.0F;
    private static final float MIN_ORBIT = 1.8F, ORBIT_SPAN = 3.7F;
    /** Inclinaison de chaque orbite (degres, exageree pour se voir) et longitude de son noeud. */
    private static final float[] INCLINATION = {11, -6, 0, 7, -4, 9, -13, 5};
    /** Vitesse angulaire de la planete la plus proche (rad/tick, un tour en ~30 s) ; les autres ralentissent avec la distance. */
    private static final double BASE_SPEED = 0.011;
    private static final int RING_SEGMENTS = 96, LAT = 24, LON = 48;

    private SolarScene() {
    }

    /**
     * @param offset    centre de la scene par rapport au bloc qui dessine
     * @param beamFrom  distance du centre aux projecteurs (en blocs du monde)
     * @param scale     echelle de la bulle et des orbites
     */
    public static void draw(Level level, UUID id, Vec3 center, Vec3 offset, boolean running, IntPredicate planetPresent, float beamFrom, float scale,
          float partialTick, PoseStack pose, MultiBufferSource buffers, int overlayLight) {
        ChamberEffectRenderer.queue(id, ChamberEffectRenderer.Kind.SIMULATED_SUN, center, running, 1, SUN_SIZE);
        if (!running) {
            return;
        }
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(center).scale(1 / scale);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.scale(scale, scale, scale);
        //1. Fond de la bulle (ciel etoile) et faisceaux des projecteurs
        drawSphere(pose, buffers.getBuffer(RenderType.beaconBeam(STARFIELD, false)), camera, false, 1, 0, 1);
        VertexConsumer beams = buffers.getBuffer(RenderType.lightning());
        for (Direction side : Direction.values()) {
            drawBeam(pose.last().pose(), beams, side, beamFrom / scale, BUBBLE_RADIUS, time);
        }
        //2. Orbites : avant les planetes (le rendu des objets peut changer de tampon et clore celui des anneaux)
        VertexConsumer rings = buffers.getBuffer(RenderType.lightning());
        for (SimulatedPlanet planet : SimulatedPlanet.values()) {
            if (planetPresent.test(planet.ordinal())) {
                pose.pushPose();
                tilt(pose, planet);
                drawRing(pose.last().pose(), rings, orbit(planet), planet.color(), 0.4F);
                pose.popPose();
            }
        }
        //3. Planetes
        for (SimulatedPlanet planet : SimulatedPlanet.values()) {
            if (!planetPresent.test(planet.ordinal())) {
                continue;
            }
            float radius = orbit(planet);
            double speed = BASE_SPEED / Math.pow(planet.orbit() * SimulatedPlanet.values().length, 1.2);
            double angle = time * speed + planet.ordinal() * 1.7;
            pose.pushPose();
            tilt(pose, planet);
            pose.translate(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2));
            float size = planet.ordinal() >= SimulatedPlanet.JUPITER.ordinal() ? 1.5F : 1.0F;
            pose.scale(size, size, size);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(PiItems.SIMULATED_PLANETS.get(planet).get()), ItemDisplayContext.FIXED,
                  LightTexture.FULL_BRIGHT, overlayLight, pose, buffers, level, 0);
            pose.popPose();
        }
        //4. Voile de la bulle, cote camera
        drawSphere(pose, buffers.getBuffer(RenderType.beaconBeam(FIELD, true)), camera, true, 0.22F, time * 0.0015F, 1.02F);
        pose.popPose();
    }

    /** Plan de l'orbite : tourne autour de la verticale (noeud), puis s'incline. */
    private static void tilt(PoseStack pose, SimulatedPlanet planet) {
        pose.mulPose(Axis.YP.rotationDegrees(planet.ordinal() * 47));
        pose.mulPose(Axis.XP.rotationDegrees(INCLINATION[planet.ordinal()]));
    }

    private static float orbit(SimulatedPlanet planet) {
        return MIN_ORBIT + planet.orbit() * ORBIT_SPAN;
    }

    /**
     * Sphere de la bulle, decoupee en quadrilateres ; seuls ceux tournes vers la camera ({@code front}) ou a l'oppose sont dessines
     * (tri fait ici : la moitie du fond reste visible meme si la camera entre dans la bulle).
     */
    private static void drawSphere(PoseStack pose, VertexConsumer buffer, Vec3 camera, boolean front, float alpha, float scroll, float scale) {
        Matrix4f matrix = pose.last().pose();
        Matrix3f normalMatrix = pose.last().normal();
        float r = BUBBLE_RADIUS * scale;
        for (int i = 0; i < LAT; i++) {
            float t0 = (float) Math.PI * i / LAT, t1 = (float) Math.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = (float) (Math.PI * 2) * j / LON, p1 = (float) (Math.PI * 2) * (j + 1) / LON;
                float tm = (t0 + t1) / 2, pm = (p0 + p1) / 2;
                float nx = Mth.sin(tm) * Mth.cos(pm), ny = Mth.cos(tm), nz = Mth.sin(tm) * Mth.sin(pm);
                //Face tournee vers la camera si la camera est du cote exterieur de son plan
                double facing = (camera.x - nx * r) * nx + (camera.y - ny * r) * ny + (camera.z - nz * r) * nz;
                if (facing > 0 != front) {
                    continue;
                }
                float u0 = (float) j / LON + scroll, u1 = (float) (j + 1) / LON + scroll, v0 = (float) i / LAT, v1 = (float) (i + 1) / LAT;
                //Ordre des sommets : face visible depuis l'interieur (fond) ou depuis l'exterieur (voile) ; le rendu masque l'autre cote
                if (front) {
                    vertex(buffer, matrix, normalMatrix, r, t0, p0, u0, v0, alpha, true);
                    vertex(buffer, matrix, normalMatrix, r, t0, p1, u1, v0, alpha, true);
                    vertex(buffer, matrix, normalMatrix, r, t1, p1, u1, v1, alpha, true);
                    vertex(buffer, matrix, normalMatrix, r, t1, p0, u0, v1, alpha, true);
                } else {
                    vertex(buffer, matrix, normalMatrix, r, t0, p0, u0, v0, alpha, false);
                    vertex(buffer, matrix, normalMatrix, r, t1, p0, u0, v1, alpha, false);
                    vertex(buffer, matrix, normalMatrix, r, t1, p1, u1, v1, alpha, false);
                    vertex(buffer, matrix, normalMatrix, r, t0, p1, u1, v0, alpha, false);
                }
            }
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float r, float theta, float phi, float u, float v, float alpha,
          boolean outward) {
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float sign = outward ? 1 : -1;
        buffer.vertex(matrix, nx * r, ny * r, nz * r).color(1, 1, 1, alpha).uv(u, v).uv2(LightTexture.FULL_BRIGHT)
              .normal(normalMatrix, nx * sign, ny * sign, nz * sign).endVertex();
    }

    /**
     * Cone de champ de force d'un emetteur (centre d'une face) jusqu'a la bulle : quatre plans croises qui s'evasent, lumineux pres de
     * l'emetteur, pulsants.
     */
    private static void drawBeam(Matrix4f matrix, VertexConsumer buffer, Direction side, float from, float to, float time) {
        drawBeam(matrix, buffer, side, from, to, time, BEAM_BASE, BEAM_TIP, BEAM_CORE, BEAM_CORE_TIP);
    }

    /** Faisceau d'un emetteur vers la bulle, aux couleurs donnees (base, bout, coeur, bout du coeur). */
    public static void drawBeam(Matrix4f matrix, VertexConsumer buffer, Direction side, float from, float to, float time, float[] base, float[] tip,
          float[] core, float[] coreTip) {
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.15F + side.ordinal() * 1.3F);
        float near = 0.35F, far = 1.9F;
        float sx = side.getStepX(), sy = side.getStepY(), sz = side.getStepZ();
        //Deux axes perpendiculaires au faisceau, et leurs diagonales
        float[] u = sy != 0 ? new float[]{1, 0, 0} : sx != 0 ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] v = sy != 0 ? new float[]{0, 0, 1} : sx != 0 ? new float[]{0, 0, 1} : new float[]{0, 1, 0};
        float d = 0.7071F;
        float[][] axes = {u, v, {(u[0] + v[0]) * d, (u[1] + v[1]) * d, (u[2] + v[2]) * d}, {(u[0] - v[0]) * d, (u[1] - v[1]) * d, (u[2] - v[2]) * d}};
        float x0 = sx * from, y0 = sy * from, z0 = sz * from, x1 = sx * to, y1 = sy * to, z1 = sz * to;
        for (float[] a : axes) {
            for (int pass = 0; pass < 2; pass++) {
                float k = pass == 0 ? 1 : -1;
                buffer.vertex(matrix, x0 - a[0] * near * k, y0 - a[1] * near * k, z0 - a[2] * near * k).color(base[0], base[1], base[2], 0.75F * pulse).endVertex();
                buffer.vertex(matrix, x0 + a[0] * near * k, y0 + a[1] * near * k, z0 + a[2] * near * k).color(base[0], base[1], base[2], 0.75F * pulse).endVertex();
                buffer.vertex(matrix, x1 + a[0] * far * k, y1 + a[1] * far * k, z1 + a[2] * far * k).color(tip[0], tip[1], tip[2], 0.05F).endVertex();
                buffer.vertex(matrix, x1 - a[0] * far * k, y1 - a[1] * far * k, z1 - a[2] * far * k).color(tip[0], tip[1], tip[2], 0.05F).endVertex();
            }
        }
        //Coeur du faisceau, plus fin et plus intense
        for (float[] a : new float[][]{u, v}) {
            for (int pass = 0; pass < 2; pass++) {
                float k = pass == 0 ? 1 : -1;
                buffer.vertex(matrix, x0 - a[0] * 0.08F * k, y0 - a[1] * 0.08F * k, z0 - a[2] * 0.08F * k).color(core[0], core[1], core[2], pulse).endVertex();
                buffer.vertex(matrix, x0 + a[0] * 0.08F * k, y0 + a[1] * 0.08F * k, z0 + a[2] * 0.08F * k).color(core[0], core[1], core[2], pulse).endVertex();
                buffer.vertex(matrix, x1 + a[0] * 0.2F * k, y1 + a[1] * 0.2F * k, z1 + a[2] * 0.2F * k).color(coreTip[0], coreTip[1], coreTip[2], 0.3F).endVertex();
                buffer.vertex(matrix, x1 - a[0] * 0.2F * k, y1 - a[1] * 0.2F * k, z1 - a[2] * 0.2F * k).color(coreTip[0], coreTip[1], coreTip[2], 0.3F).endVertex();
            }
        }
    }

    /** Orbite : un anneau plat, fin et lumineux, a la couleur de la planete. */
    private static void drawRing(Matrix4f matrix, VertexConsumer buffer, float radius, int color, float alpha) {
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        float inner = radius - 0.02F, outer = radius + 0.02F;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / RING_SEGMENTS, a1 = (i + 1) * Math.PI * 2 / RING_SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buffer.vertex(matrix, c0 * inner, 0, s0 * inner).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c0 * outer, 0, s0 * outer).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c1 * outer, 0, s1 * outer).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c1 * inner, 0, s1 * inner).color(r, g, b, alpha).endVertex();
            //Face du dessous
            buffer.vertex(matrix, c1 * inner, 0, s1 * inner).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c1 * outer, 0, s1 * outer).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c0 * outer, 0, s0 * outer).color(r, g, b, alpha).endVertex();
            buffer.vertex(matrix, c0 * inner, 0, s0 * inner).color(r, g, b, alpha).endVertex();
        }
    }
}
