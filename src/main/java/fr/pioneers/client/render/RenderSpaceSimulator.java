package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.space.content.spacesim.SpaceSimMode;
import fr.pioneers.space.content.spacesim.SpaceSimShape;
import fr.pioneers.core.RenderClock;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Rendu du Simulateur spatial (controleur) : la scene simulee au centre du globe ({@link SolarScene} ou {@link GalaxyScene}, agrandies a la
 * taille du globe de 25) et, en marche, l'anneau d'energie : les bobines supremes restent visibles, englobees par un torrent d'energie qui tourne autour
 * de l'astre (bandes lumineuses superposees, flux qui defile, rayons vers le globe) d'ou jaillissent de petits eclairs.
 */
public class RenderSpaceSimulator implements BlockEntityRenderer<SpaceSimControllerTile> {

    private static final float SOLAR_SCALE = 1.75F, GALAXY_SCALE = 1.9F;
    private static final int SEGMENTS = 160;
    private static final float RING_INNER = (float) SpaceSimShape.RING_INNER, RING_OUTER = (float) SpaceSimShape.RING_OUTER;
    /** Bord du globe (les rayons du torrent partent d'ici). */
    private static final float SHELL = SpaceSimShape.RADIUS + 0.5F;

    public RenderSpaceSimulator(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull SpaceSimControllerTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null || !tile.isFormed()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(tile.getCenter());
        Vec3 offset = center.subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
        UUID id = UUID.nameUUIDFromBytes(("space_simulator" + tile.getBlockPos().asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean running = tile.isRunning();
        boolean solar = running && tile.getRunMode() == SpaceSimMode.SOLAR;
        float beamFrom = SpaceSimShape.RADIUS - 0.5F;
        SolarScene.draw(level, id, center, offset, solar, tile::hasClientPlanet, beamFrom, SOLAR_SCALE, partialTick, pose, buffers, overlay);
        if (running && tile.getRunMode() == SpaceSimMode.GALAXY) {
            GalaxyScene.draw(level, center, offset, tile.getGalaxy(), tile.getIgnitedAt(), beamFrom, GALAXY_SCALE, partialTick, pose, buffers);
        }
        if (running) {
            double clock = RenderClock.time(partialTick);       //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
            float time = (float) clock;
            float age = (float) Math.max(0, clock - tile.getIgnitedAt());
            float power = Mth.clamp(age / 60F, 0, 1);
            boolean galaxy = tile.getRunMode() == SpaceSimMode.GALAXY;
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            torrent(pose.last().pose(), buffers.getBuffer(RenderType.lightning()), time, power, galaxy);
            arcs(pose.last().pose(), buffers.getBuffer(RenderType.lightning()), time, power, galaxy);
            pose.popPose();
        }
    }

    /** Couleur du torrent : blanc electrique a coeur, cyan (systeme solaire) ou violet-bleu (galaxie) au bord. */
    private static float[] color(boolean galaxy, float k) {
        float[] edge = galaxy ? new float[]{0.55F, 0.45F, 1F} : new float[]{0.3F, 0.85F, 1F};
        return new float[]{Mth.lerp(k, edge[0], 1), Mth.lerp(k, edge[1], 1), Mth.lerp(k, edge[2], 1)};
    }

    /**
     * Anneau : plusieurs bandes horizontales superposees (dessus et dessous) et deux parois verticales, dont l'eclat defile autour de l'astre
     * a des vitesses differentes ; puis les quatre rayons qui relient l'anneau au globe.
     */
    private static void torrent(Matrix4f m, VertexConsumer b, float time, float power, boolean galaxy) {
        float[][] layers = {{-0.85F, 0.55F, 0.9F}, {-0.3F, 0.8F, -1.6F}, {0.3F, 1F, -1.3F}, {0.85F, 0.55F, 1.7F}};
        for (float[] layer : layers) {
            float y = layer[0], strength = layer[1] * power, speed = layer[2];
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
                float f0 = flow(a0, time, speed), f1 = flow(a1, time, speed);
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                //Bande : bord interieur et exterieur pales, milieu brillant
                float mid = (RING_INNER + RING_OUTER) / 2;
                for (int half = 0; half < 2; half++) {
                    float r0 = half == 0 ? RING_INNER - 1.2F : mid, r1 = half == 0 ? mid : RING_OUTER + 1.2F;
                    float k0 = half == 0 ? 0 : 1, k1 = half == 0 ? 1 : 0;
                    float[] ca = color(galaxy, k0 * f0), cb = color(galaxy, k1 * f0), cc = color(galaxy, k1 * f1), cd = color(galaxy, k0 * f1);
                    float al0 = strength * (0.15F + 0.7F * k0 * f0), al1 = strength * (0.15F + 0.7F * k1 * f0);
                    float al2 = strength * (0.15F + 0.7F * k1 * f1), al3 = strength * (0.15F + 0.7F * k0 * f1);
                    quad(b, m, c0 * r0, y, s0 * r0, ca, al0, c0 * r1, y, s0 * r1, cb, al1, c1 * r1, y, s1 * r1, cc, al2, c1 * r0, y, s1 * r0, cd, al3);
                }
            }
        }
        //Parois : le torrent a de l'epaisseur, vu de cote
        for (float r : new float[]{RING_INNER - 1.2F, RING_OUTER + 1.2F}) {
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
                float f0 = flow(a0, time, -2.1F), f1 = flow(a1, time, -2.1F);
                float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
                float[] col0 = color(galaxy, f0), col1 = color(galaxy, f1);
                float al0 = power * (0.2F + 0.6F * f0), al1 = power * (0.2F + 0.6F * f1);
                quad(b, m, c0, -1.0F, s0, col0, 0, c0, 0, s0, col0, al0, c1, 0, s1, col1, al1, c1, -1.0F, s1, col1, 0);
                quad(b, m, c0, 0, s0, col0, al0, c0, 1.0F, s0, col0, 0, c1, 1.0F, s1, col1, 0, c1, 0, s1, col1, al1);
            }
        }
        //Rayons vers le globe
        for (int side = 0; side < 4; side++) {
            float dx = side == 0 ? 1 : side == 1 ? -1 : 0, dz = side == 2 ? 1 : side == 3 ? -1 : 0;
            float pulse = 0.6F + 0.4F * Mth.sin(time * 0.5F + side * 1.6F);
            Vector3f from = new Vector3f(dx * SHELL, 0, dz * SHELL), to = new Vector3f(dx * RING_INNER, 0, dz * RING_INNER);
            beam(b, m, from, to, 1.1F, color(galaxy, 0.4F), 0.35F * power * pulse);
            beam(b, m, from, to, 0.7F, color(galaxy, 0.7F), 0.5F * power * pulse);
            beam(b, m, from, to, 0.2F, color(galaxy, 1), 0.9F * power * pulse);
        }
    }

    /** Eclat du flux en un point de l'anneau : vagues qui defilent, plus quelques paquets d'energie brillants. */
    private static float flow(float angle, float time, float speed) {
        float wave = 0.5F + 0.5F * Mth.sin(angle * 14 + time * 0.25F * speed);
        float packet = Mth.sin(angle * 3 - time * 0.06F * speed);
        packet = packet > 0.85F ? (packet - 0.85F) / 0.15F : 0;
        return Mth.clamp(wave * 0.7F + packet, 0, 1);
    }

    /**
     * Petits eclairs qui jaillissent du torrent : lignes brisees tirees au hasard (renouvelees toutes les 2 ticks), du bord de l'anneau vers
     * l'exterieur, vers le globe ou d'un point de l'anneau a un autre.
     */
    private static void arcs(Matrix4f m, VertexConsumer b, float time, float power, boolean galaxy) {
        long frame = (long) (time / 2);
        Random random = new Random(frame * 341873128712L + 132897987541L);
        int count = 10;
        for (int n = 0; n < count; n++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float r = Mth.lerp(random.nextFloat(), RING_INNER, RING_OUTER);
            Vector3f start = new Vector3f(Mth.cos(angle) * r, (random.nextFloat() - 0.5F) * 0.8F, Mth.sin(angle) * r);
            Vector3f end;
            int kind = random.nextInt(3);
            if (kind == 0) {
                //Vers le globe
                float a = angle + (random.nextFloat() - 0.5F) * 0.4F;
                end = new Vector3f(Mth.cos(a) * SHELL, (random.nextFloat() - 0.5F) * 3, Mth.sin(a) * SHELL);
            } else if (kind == 1) {
                //Vers l'exterieur, en l'air
                float a = angle + (random.nextFloat() - 0.5F) * 0.3F, out = RING_OUTER + 1 + random.nextFloat() * 2.5F;
                end = new Vector3f(Mth.cos(a) * out, (random.nextFloat() - 0.5F) * 4, Mth.sin(a) * out);
            } else {
                //D'un point de l'anneau a un autre, en arc au-dessus
                float a = angle + 0.15F + random.nextFloat() * 0.3F;
                end = new Vector3f(Mth.cos(a) * r, (random.nextFloat() - 0.2F) * 1.5F, Mth.sin(a) * r);
            }
            float alpha = power * (0.55F + 0.45F * random.nextFloat());
            int steps = 7;
            Vector3f previous = new Vector3f(start);
            for (int s = 1; s <= steps; s++) {
                float t = s / (float) steps;
                Vector3f point = new Vector3f(start).lerp(end, t);
                if (s < steps) {
                    float jitter = 0.55F * (1 - Math.abs(t - 0.5F) * 1.4F);
                    point.add((random.nextFloat() - 0.5F) * jitter * 2, (random.nextFloat() - 0.5F) * jitter * 2, (random.nextFloat() - 0.5F) * jitter * 2);
                }
                beam(b, m, previous, point, 0.09F, color(galaxy, 0.3F), alpha * 0.45F);
                beam(b, m, previous, point, 0.03F, color(galaxy, 1), alpha);
                previous = point;
            }
        }
    }

    /** Segment lumineux : deux plans croises le long de l'axe. */
    private static void beam(VertexConsumer b, Matrix4f m, Vector3f from, Vector3f to, float width, float[] c, float alpha) {
        Vector3f axis = new Vector3f(to).sub(from);
        if (axis.lengthSquared() < 1.0E-6F) {
            return;
        }
        axis.normalize();
        Vector3f u = Math.abs(axis.y) < 0.9F ? new Vector3f(0, 1, 0).cross(axis).normalize() : new Vector3f(1, 0, 0).cross(axis).normalize();
        Vector3f v = new Vector3f(axis).cross(u).normalize();
        for (Vector3f side : new Vector3f[]{u, v}) {
            float sx = side.x * width, sy = side.y * width, sz = side.z * width;
            quad(b, m, from.x - sx, from.y - sy, from.z - sz, c, alpha, from.x + sx, from.y + sy, from.z + sz, c, alpha, to.x + sx, to.y + sy, to.z + sz, c, alpha,
                  to.x - sx, to.y - sy, to.z - sz, c, alpha);
        }
    }

    /** Quadrilatere a deux faces (rendu sans tri ni masquage des faces arriere). */
    private static void quad(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float[] c0, float a0, float x1, float y1, float z1, float[] c1, float a1,
          float x2, float y2, float z2, float[] c2, float a2, float x3, float y3, float z3, float[] c3, float a3) {
        b.vertex(m, x0, y0, z0).color(c0[0], c0[1], c0[2], a0).endVertex();
        b.vertex(m, x1, y1, z1).color(c1[0], c1[1], c1[2], a1).endVertex();
        b.vertex(m, x2, y2, z2).color(c2[0], c2[1], c2[2], a2).endVertex();
        b.vertex(m, x3, y3, z3).color(c3[0], c3[1], c3[2], a3).endVertex();
        b.vertex(m, x3, y3, z3).color(c3[0], c3[1], c3[2], a3).endVertex();
        b.vertex(m, x2, y2, z2).color(c2[0], c2[1], c2[2], a2).endVertex();
        b.vertex(m, x1, y1, z1).color(c1[0], c1[1], c1[2], a1).endVertex();
        b.vertex(m, x0, y0, z0).color(c0[0], c0[1], c0[2], a0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull SpaceSimControllerTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
