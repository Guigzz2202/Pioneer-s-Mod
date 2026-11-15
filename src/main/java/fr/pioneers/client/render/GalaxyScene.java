package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.content.universe.GalaxyType;
import fr.pioneers.core.RenderClock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Scene de galaxie du Simulateur spatial (reprise de l'ancien Simulateur d'univers) : une bulle d'espace noire (comme le Simulateur du systeme solaire) ou tourne une galaxie entiere,
 * faite de milliers d'etoiles : bras spiraux, nuage elliptique, amas irreguliers ou quasar (disque d'accretion et jets). Chaque etoile
 * tourne avec la galaxie. Sprites additifs ronds (etoiles a aigrettes, nuages de gaz). Au Big Bang, la galaxie nait d'un eclair et s'etend.
 */
public final class GalaxyScene {

    private static final float BUBBLE_RADIUS = 5.6F, GALAXY_RADIUS = 4.6F;
    private static final int LAT = 24, LON = 48;
    private static final Map<GalaxyType, float[][]> STARS = new EnumMap<>(GalaxyType.class);

    private GalaxyScene() {
    }

    /** Sprites additifs (fond noir) : etoile ronde a aigrettes, nuage de gaz. */
    /** Couleurs des faisceaux des projecteurs (constantes : pas d'allocation a chaque image). */
    private static final float[] BEAM_BASE = {0.78F, 0.58F, 1}, BEAM_TIP = {0.45F, 0.25F, 0.9F}, BEAM_CORE = {0.96F, 0.9F, 1}, BEAM_CORE_TIP = {0.7F, 0.5F, 1};
    private static final ResourceLocation STAR = Pioneers.rl("textures/effect/star_sprite.png");
    private static final ResourceLocation PUFF = Pioneers.rl("textures/effect/nebula_puff.png");
    /** Champs d'un point de la galaxie. */
    private static final int R = 0, ANGLE = 1, HEIGHT = 2, CR = 3, CG = 4, CB = 5, SIZE = 6, SPEED = 7, BRIGHT = 8, NEBULA = 9;

    /** Points d'une galaxie : etoiles et nuages de gaz (rayon, angle, hauteur, couleur, taille, vitesse, eclat, nuage ?). */
    private static float[][] stars(GalaxyType type) {
        return STARS.computeIfAbsent(type, t -> {
            Random random = new Random(1337 + t.ordinal());
            List<float[]> list = new ArrayList<>();
            switch (t) {
                case SPIRAL -> spiral(random, list);
                case ELLIPTICAL -> elliptical(random, list);
                case IRREGULAR -> irregular(random, list);
                case QUASAR -> quasar(random, list);
            }
            return list.toArray(new float[0][]);
        });
    }

    private static float[] point(float r, float angle, float height, float[] color, float size, float speed, float bright, boolean nebula) {
        return new float[]{r, angle, height, color[0], color[1], color[2], size, speed, bright, nebula ? 1 : 0};
    }

    /** Couleur d'etoile selon sa population : jeunes bleues, intermediaires blanches, vieilles dorees ou rouges. */
    private static float[] starColor(Random random, float young) {
        float k = random.nextFloat();
        if (k < young * 0.6F) {
            return new float[]{0.62F, 0.76F, 1F};
        } else if (k < young) {
            return new float[]{0.86F, 0.92F, 1F};
        } else if (k < young + (1 - young) * 0.6F) {
            return new float[]{1F, 0.86F, 0.62F};
        }
        return new float[]{1F, 0.62F, 0.45F};
    }

    private static float starSize(Random random) {
        //Beaucoup de petites etoiles, quelques geantes brillantes
        float k = random.nextFloat();
        return k < 0.03F ? 0.16F + random.nextFloat() * 0.08F : 0.05F + (float) Math.pow(random.nextFloat(), 2) * 0.07F;
    }

    /** Spirale barree a quatre bras (deux majeurs), bulbe dore, bras bleus semes de nebuleuses roses, halo diffus. */
    private static void spiral(Random random, List<float[]> list) {
        //Bulbe et barre
        for (int i = 0; i < 450; i++) {
            float r = (float) Math.abs(random.nextGaussian()) * 0.6F;
            float angle = random.nextFloat() * Mth.TWO_PI;
            if (random.nextFloat() < 0.4F) {
                //Barre : etiree le long d'un axe
                float along = (float) random.nextGaussian() * 0.9F, across = (float) random.nextGaussian() * 0.18F;
                r = Mth.sqrt(along * along + across * across);
                angle = (float) Math.atan2(across, along);
            }
            list.add(point(r, angle, (float) random.nextGaussian() * 0.2F * Math.max(0.2F, 1 - r), starColor(random, 0.05F), starSize(random), 1,
                  0.42F, false));
        }
        //Bras
        for (int i = 0; i < 2600; i++) {
            int arm = random.nextInt(4);
            boolean major = arm < 2;
            if (!major && random.nextFloat() < 0.4F) {
                arm = random.nextInt(2);
                major = true;
            }
            float r = 0.9F + (float) Math.pow(random.nextFloat(), 0.9) * (GALAXY_RADIUS - 0.9F);
            float spread = (major ? 0.22F : 0.32F) * (0.6F + r / GALAXY_RADIUS);
            //Bras majeurs opposes (0 et pi), bras mineurs entre eux
            float base = arm < 2 ? arm * Mth.PI : (arm - 2) * Mth.PI + Mth.HALF_PI + 0.3F;
            float angle = base + (float) Math.log(r) * 2.4F + (float) random.nextGaussian() * spread;
            float fade = 1 - r / (GALAXY_RADIUS * 1.15F);
            list.add(point(r, angle, (float) random.nextGaussian() * 0.05F, starColor(random, 0.7F), starSize(random), 1,
                  (major ? 0.9F : 0.6F) * (0.45F + 0.55F * fade), false));
        }
        //Nebuleuses (regions HII roses et poussiere bleutee) le long des bras
        for (int i = 0; i < 120; i++) {
            int arm = random.nextInt(2);
            float r = 1.1F + random.nextFloat() * (GALAXY_RADIUS - 1.4F);
            float angle = arm * Mth.HALF_PI * 2 + (float) Math.log(r) * 2.4F + (float) random.nextGaussian() * 0.15F;
            boolean pink = random.nextFloat() < 0.35F;
            float[] color = pink ? new float[]{1F, 0.35F, 0.6F} : new float[]{0.35F, 0.5F, 1F};
            list.add(point(r, angle, 0, color, pink ? 0.35F + random.nextFloat() * 0.25F : 0.7F + random.nextFloat() * 0.6F, 1,
                  pink ? 0.18F : 0.07F, true));
        }
        //Lueur diffuse du disque
        for (int i = 0; i < 26; i++) {
            float r = random.nextFloat() * 2.6F;
            list.add(point(r, random.nextFloat() * Mth.TWO_PI, 0, new float[]{0.9F, 0.8F, 0.65F}, 1.6F + random.nextFloat(), 1, 0.05F, true));
        }
    }

    /** Elliptique geante : ellipsoide triaxial d'etoiles vieilles, coeur tres brillant, amas globulaires autour. */
    private static void elliptical(Random random, List<float[]> list) {
        for (int i = 0; i < 2600; i++) {
            //Profil de de Vaucouleurs approche : beaucoup d'etoiles au centre
            float rr = (float) Math.pow(random.nextFloat(), 2.2) * GALAXY_RADIUS * 0.95F;
            float u = random.nextFloat() * 2 - 1, phi = random.nextFloat() * Mth.TWO_PI, s = Mth.sqrt(1 - u * u);
            float x = rr * s * Mth.cos(phi), y = rr * u * 0.62F, z = rr * s * Mth.sin(phi) * 0.8F;
            list.add(point(Mth.sqrt(x * x + z * z), (float) Math.atan2(z, x), y, starColor(random, 0.03F), starSize(random) * 0.9F, 0.25F,
                  0.3F + 0.35F * (1 - rr / GALAXY_RADIUS), false));
        }
        for (int i = 0; i < 18; i++) {
            float r = random.nextFloat() * 1.4F;
            list.add(point(r, random.nextFloat() * Mth.TWO_PI, (float) random.nextGaussian() * 0.4F, new float[]{1F, 0.78F, 0.5F}, 1.4F + random.nextFloat(),
                  0.25F, 0.08F, true));
        }
        //Amas globulaires : petites boules serrees
        for (int c = 0; c < 9; c++) {
            float cr = 2.5F + random.nextFloat() * 1.8F, ca = random.nextFloat() * Mth.TWO_PI, cy = (float) random.nextGaussian() * 1.2F;
            for (int i = 0; i < 30; i++) {
                float dx = (float) random.nextGaussian() * 0.12F, dz = (float) random.nextGaussian() * 0.12F;
                float x = Mth.cos(ca) * cr + dx, z = Mth.sin(ca) * cr + dz;
                list.add(point(Mth.sqrt(x * x + z * z), (float) Math.atan2(z, x), cy + (float) random.nextGaussian() * 0.12F, new float[]{1F, 0.9F, 0.75F},
                      0.04F + random.nextFloat() * 0.03F, 0.25F, 0.9F, false));
            }
        }
    }

    /** Irreguliere : amas de formation stellaire (roses et bleus), filaments de gaz, etoiles eparses, sans symetrie. */
    private static void irregular(Random random, List<float[]> list) {
        float[][] knots = new float[7][];
        for (int k = 0; k < knots.length; k++) {
            knots[k] = new float[]{(float) random.nextGaussian() * 1.8F, (float) random.nextGaussian() * 0.4F, (float) random.nextGaussian() * 1.4F,
                                   0.4F + random.nextFloat() * 0.6F};
        }
        for (int i = 0; i < 2200; i++) {
            float x, y, z;
            if (random.nextFloat() < 0.65F) {
                float[] k = knots[random.nextInt(knots.length)];
                x = k[0] + (float) random.nextGaussian() * k[3];
                y = k[1] + (float) random.nextGaussian() * k[3] * 0.4F;
                z = k[2] + (float) random.nextGaussian() * k[3];
            } else {
                x = (float) random.nextGaussian() * 2.2F;
                y = (float) random.nextGaussian() * 0.5F;
                z = (float) random.nextGaussian() * 1.6F;
            }
            list.add(point(Mth.sqrt(x * x + z * z), (float) Math.atan2(z, x), y, starColor(random, 0.75F), starSize(random), 0.45F, 0.55F, false));
        }
        for (float[] k : knots) {
            for (int i = 0; i < 9; i++) {
                float x = k[0] + (float) random.nextGaussian() * k[3] * 0.8F, z = k[2] + (float) random.nextGaussian() * k[3] * 0.8F;
                boolean pink = random.nextFloat() < 0.55F;
                list.add(point(Mth.sqrt(x * x + z * z), (float) Math.atan2(z, x), k[1], pink ? new float[]{1F, 0.32F, 0.62F} : new float[]{0.3F, 0.7F, 1F},
                      0.45F + random.nextFloat() * 0.5F, 0.45F, 0.16F, true));
            }
        }
    }

    /** Quasar : disque d'accretion incandescent (blanc au centre, orange au bord), anneau de photons, galaxie hote pale. */
    private static void quasar(Random random, List<float[]> list) {
        for (int i = 0; i < 1500; i++) {
            float r = 0.3F + (float) Math.pow(random.nextFloat(), 1.6) * 1.5F;
            float heat = 1 - (r - 0.3F) / 1.5F;
            float[] color = {1F, 0.55F + 0.45F * heat, 0.25F + 0.75F * heat * heat};
            list.add(point(r, random.nextFloat() * Mth.TWO_PI, (float) random.nextGaussian() * 0.025F, color, 0.05F + random.nextFloat() * 0.05F,
                  5 / Mth.sqrt(r), 0.7F + 0.3F * heat, false));
        }
        //Anneau de photons, juste autour du trou noir
        for (int i = 0; i < 220; i++) {
            float a = random.nextFloat() * Mth.TWO_PI;
            list.add(point(0.26F + (float) random.nextGaussian() * 0.01F, a, 0, new float[]{1F, 0.95F, 0.85F}, 0.035F, 8, 1F, false));
        }
        //Galaxie hote
        for (int i = 0; i < 1100; i++) {
            float r = 1.6F + random.nextFloat() * (GALAXY_RADIUS - 1.6F);
            float angle = random.nextInt(2) * Mth.PI + (float) Math.log(r) * 2.2F + (float) random.nextGaussian() * 0.5F;
            list.add(point(r, angle, (float) random.nextGaussian() * 0.12F, new float[]{0.6F, 0.65F, 0.85F}, starSize(random) * 0.8F, 1, 0.35F, false));
        }
        for (int i = 0; i < 10; i++) {
            list.add(point(random.nextFloat() * 0.9F, random.nextFloat() * Mth.TWO_PI, 0, new float[]{1F, 0.7F, 0.4F}, 0.8F + random.nextFloat() * 0.5F, 5,
                  0.22F, true));
        }
    }

    /**
     * @param offset   centre de la scene par rapport au bloc qui dessine
     * @param bangTime temps de jeu du Big Bang (expansion)
     * @param beamFrom distance du centre aux projecteurs (en blocs du monde)
     * @param scale    echelle de la bulle et de la galaxie
     */
    public static void draw(Level level, Vec3 center, Vec3 offset, GalaxyType type, long bangTime, float beamFrom, float scale, float partialTick,
          PoseStack pose, MultiBufferSource buffers) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(center).scale(1 / scale);
        double clock = RenderClock.time(partialTick);           //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float time = (float) clock;
        float age = (float) Math.max(0, clock - bangTime);
        float growth = Mth.clamp(age / 120F, 0.02F, 1);
        growth = 1 - (1 - growth) * (1 - growth) * (1 - growth);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.scale(scale, scale, scale);
        //Fond de la bulle
        drawSphere(pose, buffers.getBuffer(RenderType.beaconBeam(SolarScene.STARFIELD, false)), camera, false, 1, 0, 1);
        //Champ de force : un faisceau violet de chaque projecteur d'horizon vers la bulle
        VertexConsumer beams = buffers.getBuffer(RenderType.lightning());
        for (Direction side : Direction.values()) {
            SolarScene.drawBeam(pose.last().pose(), beams, side, beamFrom / scale, BUBBLE_RADIUS, time, BEAM_BASE, BEAM_TIP, BEAM_CORE, BEAM_CORE_TIP);
        }
        Quaternionf facing = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        //Eclair du Big Bang
        if (age < 60) {
            float flash = 1 - age / 60F;
            sprite(buffers.getBuffer(RenderType.eyes(STAR)), pose.last().pose(), facing, 0, 0, 0, 0.8F + age * 0.15F, flash, flash, flash, 0);
        }
        //La galaxie, inclinee pour qu'on la voie de trois-quarts, et tournant lentement sur elle-meme
        float tiltX = type == GalaxyType.QUASAR ? 20 : type == GalaxyType.ELLIPTICAL ? 10 : 32;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        pose.mulPose(Axis.ZP.rotationDegrees(12));
        pose.scale(growth, growth, growth);
        Matrix4f matrix = pose.last().pose();
        Quaternionf local = new Quaternionf(facing);
        //Les sprites suivent la camera malgre l'inclinaison : on annule la rotation locale
        Quaternionf tiltInverse = new Quaternionf().rotateX((float) Math.toRadians(tiltX)).rotateZ((float) Math.toRadians(12)).invert();
        tiltInverse.mul(local, local);
        float spin = time * 0.0025F;
        //Deux passes (un tampon a la fois) : les nuages de gaz, puis les etoiles par-dessus
        for (int pass = 0; pass < 2; pass++) {
            boolean nebulaPass = pass == 0;
            VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(nebulaPass ? PUFF : STAR));
            for (float[] s : stars(type)) {
                if (s[NEBULA] > 0 != nebulaPass) {
                    continue;
                }
                //Rotation presque rigide (les bras gardent leur forme), un peu plus lente au bord
                float angle = s[ANGLE] + spin * s[SPEED] / (1 + 0.12F * s[R]);
                float x = Mth.cos(angle) * s[R], z = Mth.sin(angle) * s[R], y = s[HEIGHT];
                float twinkle = nebulaPass ? 1 : 0.8F + 0.2F * Mth.sin(time * 0.12F + s[ANGLE] * 31 + s[R] * 7);
                float b = s[BRIGHT] * twinkle;
                sprite(buffer, matrix, local, x, y, z, s[SIZE], s[CR] * b, s[CG] * b, s[CB] * b, nebulaPass ? angle : 0);
            }
        }
        VertexConsumer stars = buffers.getBuffer(RenderType.eyes(STAR));
        //Noyau : plusieurs halos de la couleur de la galaxie
        int core = type.color;
        float cr = (core >> 16 & 255) / 255F, cg = (core >> 8 & 255) / 255F, cb = (core & 255) / 255F;
        float coreSize = type == GalaxyType.ELLIPTICAL ? 0.9F : type == GalaxyType.QUASAR ? 0.5F : type == GalaxyType.IRREGULAR ? 0.3F : 0.55F;
        for (int k = 1; k <= 3; k++) {
            float a = 0.6F / k;
            sprite(stars, matrix, local, 0, 0, 0, coreSize * k * 0.6F, Math.min(1, cr + 0.25F) * a, Math.min(1, cg + 0.25F) * a, Math.min(1, cb + 0.25F) * a, 0);
        }
        if (type == GalaxyType.QUASAR) {
            //Noeuds lumineux qui remontent le long des jets
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int n = 0; n < 4; n++) {
                    float p = ((time * 0.02F + n * 0.25F) % 1F);
                    float a = (1 - p) * 0.9F;
                    sprite(stars, matrix, local, 0, sign * p * GALAXY_RADIUS, 0, 0.12F + p * 0.2F, 0.7F * a, 0.85F * a, a, 0);
                }
            }
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            float flicker = 0.7F + 0.3F * Mth.sin(time * 0.6F);
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int k = 0; k < 2; k++) {
                    float w = k == 0 ? 0.04F : 0.14F, a = (k == 0 ? 0.9F : 0.25F) * flicker;
                    jet(glow, matrix, sign, w, GALAXY_RADIUS * 1.05F, a);
                }
            }
        }
        pose.popPose();
        //Voile de la bulle, cote camera
        drawSphere(pose, buffers.getBuffer(RenderType.beaconBeam(SolarScene.FIELD, true)), camera, true, 0.18F, time * 0.0015F, 1.02F);
        pose.popPose();
    }

    /** Sprite additif tourne vers la camera (rotation propre {@code roll} pour varier les nuages). */
    private static void sprite(VertexConsumer buffer, Matrix4f matrix, Quaternionf facing, float x, float y, float z, float size, float r, float g, float b,
          float roll) {
        float c = Mth.cos(roll), s = Mth.sin(roll);
        float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
        for (float[] k : corners) {
            Vector3f v = new Vector3f((k[0] * c - k[1] * s) * size, (k[0] * s + k[1] * c) * size, 0);
            facing.transform(v);
            buffer.vertex(matrix, x + v.x(), y + v.y(), z + v.z()).color(r, g, b, 1).uv(k[2], k[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                  .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
        }
    }

    /** Jet le long de l'axe de la galaxie (deux plans croises). */
    private static void jet(VertexConsumer buffer, Matrix4f matrix, int sign, float width, float length, float alpha) {
        for (int plane = 0; plane < 2; plane++) {
            float dx = plane == 0 ? width : 0, dz = plane == 0 ? 0 : width;
            for (int pass = 0; pass < 2; pass++) {
                float k = pass == 0 ? 1 : -1;
                buffer.vertex(matrix, -dx * k, 0, -dz * k).color(0.8F, 0.9F, 1, alpha).endVertex();
                buffer.vertex(matrix, dx * k, 0, dz * k).color(0.8F, 0.9F, 1, alpha).endVertex();
                buffer.vertex(matrix, dx * k * 2.5F, sign * length, dz * k * 2.5F).color(0.4F, 0.6F, 1, 0).endVertex();
                buffer.vertex(matrix, -dx * k * 2.5F, sign * length, -dz * k * 2.5F).color(0.4F, 0.6F, 1, 0).endVertex();
            }
        }
    }

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
                double side = (camera.x - nx * r) * nx + (camera.y - ny * r) * ny + (camera.z - nz * r) * nz;
                if (side > 0 != front) {
                    continue;
                }
                float u0 = (float) j / LON + scroll, u1 = (float) (j + 1) / LON + scroll, v0 = (float) i / LAT, v1 = (float) (i + 1) / LAT;
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
}
