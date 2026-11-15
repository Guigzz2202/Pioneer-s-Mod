package fr.pioneers.client.nuclear;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Primitives de dessin des coeurs de reacteur (0.17.1), en coordonnees du controleur (blocs). Toutes les couleurs sont deja multipliees par
 * l'intensite (rendu additif {@code eyes}) ; {@code a} ne sert qu'aux types translucides. Le banc d'essai Python
 * ({@code tools/gc/nuclear/core_bench.py}) rejoue exactement ces formules.
 */
final class CoreDraw {

    private CoreDraw() {
    }

    /** Pseudo-hasard deterministe dans [0, 1) (meme formule en Python). */
    static double hash(int i, int k) {
        double s = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    static void vertex(VertexConsumer b, Matrix4f m, float x, float y, float z, float u, float v, float r, float g, float bl, float a, int light,
          float nx, float ny, float nz) {
        b.vertex(m, x, y, z).color(r, g, bl, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
    }

    /** Quadrilatere (4 sommets {x, y, z, u, v}), une face ; {@code both} : aussi la face arriere. */
    static void quad(VertexConsumer b, Matrix4f m, float[][] p, float r, float g, float bl, float a, int light, boolean both) {
        float ux = p[1][0] - p[0][0], uy = p[1][1] - p[0][1], uz = p[1][2] - p[0][2];
        float vx = p[3][0] - p[0][0], vy = p[3][1] - p[0][1], vz = p[3][2] - p[0][2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6F) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        for (int k = 0; k < 4; k++) {
            vertex(b, m, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, bl, a, light, nx, ny, nz);
        }
        if (both) {
            for (int k = 3; k >= 0; k--) {
                vertex(b, m, p[k][0], p[k][1], p[k][2], p[k][3], p[k][4], r, g, bl, a, light, -nx, -ny, -nz);
            }
        }
    }

    /** Panneau face a la camera (left/up : vecteurs de la camera), de demi-taille {@code s}. */
    static void billboard(VertexConsumer b, Matrix4f m, float x, float y, float z, float s, Vector3f left, Vector3f up, float r, float g,
          float bl, int light) {
        float lx = left.x * s, ly = left.y * s, lz = left.z * s, ux = up.x * s, uy = up.y * s, uz = up.z * s;
        vertex(b, m, x - lx - ux, y - ly - uy, z - lz - uz, 0, 1, r, g, bl, 1, light, 0, 1, 0);
        vertex(b, m, x + lx - ux, y + ly - uy, z + lz - uz, 1, 1, r, g, bl, 1, light, 0, 1, 0);
        vertex(b, m, x + lx + ux, y + ly + uy, z + lz + uz, 1, 0, r, g, bl, 1, light, 0, 1, 0);
        vertex(b, m, x - lx + ux, y - ly + uy, z - lz + uz, 0, 0, r, g, bl, 1, light, 0, 1, 0);
    }

    /**
     * Cylindre vertical ouvert (axe cx, cz), de y0 a y1, {@code seg} facettes ; texture repetee {@code ru} fois autour, {@code rv} fois en
     * hauteur, decalee de {@code dv} (defilement). Les deux faces.
     */
    static void cylinder(VertexConsumer b, Matrix4f m, float cx, float cz, float rad, float y0, float y1, int seg, float ru, float rv, float dv,
          float r, float g, float bl, float a, int light) {
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            float x0 = cx + (float) Math.cos(a0) * rad, z0 = cz + (float) Math.sin(a0) * rad;
            float x1 = cx + (float) Math.cos(a1) * rad, z1 = cz + (float) Math.sin(a1) * rad;
            float u0 = ru * i / seg, u1 = ru * (i + 1) / seg;
            quad(b, m, new float[][]{{x0, y0, z0, u0, rv + dv}, {x1, y0, z1, u1, rv + dv}, {x1, y1, z1, u1, dv}, {x0, y1, z0, u0, dv}}, r, g, bl, a,
                  light, true);
        }
    }

    /** Disque horizontal a y (anneaux polaires), texture plane de taille {@code tex} blocs, rotation {@code rot}. Les deux faces. */
    static void disc(VertexConsumer b, Matrix4f m, float cx, float y, float cz, float rad, int seg, float tex, float rot, float r, float g, float bl,
          float a, int light) {
        for (int i = 0; i < seg; i++) {
            double a0 = rot + 2 * Math.PI * i / seg, a1 = rot + 2 * Math.PI * (i + 1) / seg;
            float x0 = (float) Math.cos(a0) * rad, z0 = (float) Math.sin(a0) * rad;
            float x1 = (float) Math.cos(a1) * rad, z1 = (float) Math.sin(a1) * rad;
            quad(b, m, new float[][]{{cx, y, cz, 0.5F, 0.5F}, {cx + x0, y, cz + z0, 0.5F + x0 / tex, 0.5F + z0 / tex},
                  {cx + x1, y, cz + z1, 0.5F + x1 / tex, 0.5F + z1 / tex}, {cx, y, cz, 0.5F, 0.5F}}, r, g, bl, a, light, true);
        }
    }

    /** Boite pleine (6 faces, normales vers l'exterieur), texture repetee selon la taille. */
    static void box(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float bl, int light) {
        float h = y1 - y0, w = x1 - x0, d = z1 - z0;
        quad(b, m, new float[][]{{x0, y0, z0, 0, h}, {x0, y1, z0, 0, 0}, {x1, y1, z0, w, 0}, {x1, y0, z0, w, h}}, r, g, bl, 1, light, false);
        quad(b, m, new float[][]{{x1, y0, z1, 0, h}, {x1, y1, z1, 0, 0}, {x0, y1, z1, w, 0}, {x0, y0, z1, w, h}}, r, g, bl, 1, light, false);
        quad(b, m, new float[][]{{x0, y0, z1, 0, h}, {x0, y1, z1, 0, 0}, {x0, y1, z0, d, 0}, {x0, y0, z0, d, h}}, r, g, bl, 1, light, false);
        quad(b, m, new float[][]{{x1, y0, z0, 0, h}, {x1, y1, z0, 0, 0}, {x1, y1, z1, d, 0}, {x1, y0, z1, d, h}}, r, g, bl, 1, light, false);
        quad(b, m, new float[][]{{x0, y1, z0, 0, 0}, {x0, y1, z1, 0, d}, {x1, y1, z1, w, d}, {x1, y1, z0, w, 0}}, r * 1.1F, g * 1.1F, bl * 1.1F, 1,
              light, false);
        quad(b, m, new float[][]{{x0, y0, z1, 0, 0}, {x0, y0, z0, 0, d}, {x1, y0, z0, w, d}, {x1, y0, z1, w, 0}}, r * 0.6F, g * 0.6F, bl * 0.6F, 1,
              light, false);
    }

    /**
     * Sphere UV (lat x lon), texture enroulee {@code ru} fois en longitude, defilee de {@code du} ; les deux faces si {@code both}.
     */
    static void sphere(VertexConsumer b, Matrix4f m, float cx, float cy, float cz, float rad, int lat, int lon, float ru, float du, float r,
          float g, float bl, float a, int light, boolean both) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                float[][] q = {spherePoint(cx, cy, cz, rad, t0, p0, ru * j / lon + du, (float) i / lat),
                      spherePoint(cx, cy, cz, rad, t0, p1, ru * (j + 1) / lon + du, (float) i / lat),
                      spherePoint(cx, cy, cz, rad, t1, p1, ru * (j + 1) / lon + du, (float) (i + 1) / lat),
                      spherePoint(cx, cy, cz, rad, t1, p0, ru * j / lon + du, (float) (i + 1) / lat)};
                quad(b, m, q, r, g, bl, a, light, both);
            }
        }
    }

    private static float[] spherePoint(float cx, float cy, float cz, float rad, double theta, double phi, float u, float v) {
        return new float[]{cx + (float) (Math.cos(theta) * Math.cos(phi)) * rad, cy + (float) Math.sin(theta) * rad,
              cz + (float) (Math.cos(theta) * Math.sin(phi)) * rad, u, v};
    }

    /**
     * Ruban : bande de largeur {@code w} le long d'une polyligne (points {x, y, z}), tournee face a la camera ({@code view} : direction de la
     * camera vers le point). Texture : u en travers, v le long.
     */
    static void ribbon(VertexConsumer b, Matrix4f m, float[][] pts, float w, Vector3f camera, float r, float g, float bl, int light) {
        for (int i = 0; i + 1 < pts.length; i++) {
            float[] p = pts[i], q = pts[i + 1];
            float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
            float vx = (p[0] + q[0]) * 0.5F - camera.x, vy = (p[1] + q[1]) * 0.5F - camera.y, vz = (p[2] + q[2]) * 0.5F - camera.z;
            float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
            float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-6F) {
                continue;
            }
            sx *= w / len;
            sy *= w / len;
            sz *= w / len;
            float v0 = (float) i / (pts.length - 1), v1 = (float) (i + 1) / (pts.length - 1);
            quad(b, m, new float[][]{{p[0] - sx, p[1] - sy, p[2] - sz, 0, v0}, {p[0] + sx, p[1] + sy, p[2] + sz, 1, v0},
                  {q[0] + sx, q[1] + sy, q[2] + sz, 1, v1}, {q[0] - sx, q[1] - sy, q[2] - sz, 0, v1}}, r, g, bl, 1, light, true);
        }
    }

    /**
     * Anneau incline : bande plate de rayon {@code rad} et largeur {@code w} autour du centre, dans le plan obtenu en inclinant l'horizontale
     * de {@code tilt} autour de l'axe X puis en tournant de {@code yaw} autour de Y ; {@code spin} fait defiler la texture le long.
     */
    static void ring(VertexConsumer b, Matrix4f m, float cx, float cy, float cz, float rad, float w, float tilt, float yaw, float spin, int seg,
          float r, float g, float bl, int light) {
        for (int i = 0; i < seg; i++) {
            float[] a0 = ringPoint(rad - w, tilt, yaw, 2 * Math.PI * i / seg), a1 = ringPoint(rad + w, tilt, yaw, 2 * Math.PI * i / seg);
            float[] b0 = ringPoint(rad - w, tilt, yaw, 2 * Math.PI * (i + 1) / seg), b1 = ringPoint(rad + w, tilt, yaw, 2 * Math.PI * (i + 1) / seg);
            float u0 = 4F * i / seg + spin, u1 = 4F * (i + 1) / seg + spin;
            quad(b, m, new float[][]{{cx + a0[0], cy + a0[1], cz + a0[2], u0, 0}, {cx + b0[0], cy + b0[1], cz + b0[2], u1, 0},
                  {cx + b1[0], cy + b1[1], cz + b1[2], u1, 1}, {cx + a1[0], cy + a1[1], cz + a1[2], u0, 1}}, r, g, bl, 1, light, true);
        }
    }

    static float[] ringPoint(float rad, float tilt, float yaw, double ang) {
        double x = Math.cos(ang) * rad, z = Math.sin(ang) * rad;
        double y = -z * Math.sin(tilt);
        z = z * Math.cos(tilt);
        double rx = x * Math.cos(yaw) - z * Math.sin(yaw), rz = x * Math.sin(yaw) + z * Math.cos(yaw);
        return new float[]{(float) rx, (float) y, (float) rz};
    }
}
