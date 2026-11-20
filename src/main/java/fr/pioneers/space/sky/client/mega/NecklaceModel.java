package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.ellipsoid;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (lot 11) : le COLLIER DES MAREES, vu de TRAPPIST-1e (ciel des exoplanetes, soleil fixe) : un fil de lumiere tendu d'un horizon a
 * l'autre en passant pres des mondes voisins (TRAPPIST-1c, 1d, 1g, 1f), et sept perles enfilees dessus : les relais poses aux points de
 * Lagrange, sphere de nacre, anneau et quatre panneaux d'induction. Le fil ondule doucement au rythme des marees. EN FONCTIONNEMENT :
 * des impulsions courent le long du fil d'une perle a l'autre, les perles s'allument l'une apres l'autre, et des ondes de maree
 * s'elargissent autour d'elles. Reglages (sorte « necklace ») : relais (perles), scintillement, vitesse des impulsions, eclat des
 * faisceaux, eclat de nuit, eclat de jour.
 */
public final class NecklaceModel {

    public static final double SCALE = 1;
    static final double DIST = 92;
    /** Points de passage du fil (azimut, hauteur en degres) : pres des mondes voisins vus de TRAPPIST-1e. */
    private static final double[] WAY_AZ = {196, 168, 122, 66, 4, 318, 296, 286, 281};
    private static final double[] WAY_ALT = {-2, 22, 45, 64, 71, 60, 42, 20, -2};
    /** Fil echantillonne (repere du sol, a DIST), calcule une fois. */
    private static final int SAMPLES = 96;
    private static final double[] PX = new double[SAMPLES + 1], PY = new double[SAMPLES + 1], PZ = new double[SAMPLES + 1];

    static {
        for (int k = 0; k <= SAMPLES; k++) {
            double u = (double) k / SAMPLES * (WAY_AZ.length - 1);
            int i = Math.min(WAY_AZ.length - 2, (int) Math.floor(u));
            double f = u - i;
            double az = spline(WAY_AZ, i, f, true), alt = spline(WAY_ALT, i, f, false);
            double a = Math.toRadians(az), e = Math.toRadians(alt);
            PX[k] = Math.cos(e) * Math.sin(a) * DIST;
            PY[k] = Math.sin(e) * DIST;
            PZ[k] = -Math.cos(e) * Math.cos(a) * DIST;
        }
    }

    private NecklaceModel() {
    }

    /** Catmull-Rom entre les points i et i + 1 ; azimuts deroules (pas de saut a 360). */
    private static double spline(double[] w, int i, double f, boolean angle) {
        double p1 = w[i], p2 = unwrap(p1, w[i + 1], angle);
        double p0 = i > 0 ? unwrap(p1, w[i - 1], angle) : 2 * p1 - p2;
        double p3 = i + 2 < w.length ? unwrap(p2, w[i + 2], angle) : 2 * p2 - p1;
        double f2 = f * f, f3 = f2 * f;
        return 0.5 * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f3);
    }

    private static double unwrap(double ref, double a, boolean angle) {
        if (!angle) {
            return a;
        }
        while (a - ref > 180) {
            a -= 360;
        }
        while (a - ref < -180) {
            a += 360;
        }
        return a;
    }

    /** Azimut du sommet du fil (apercus) : le point de passage le plus haut. */
    static double topYaw() {
        return WAY_AZ[4];
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double t = v.ticks, act = v.activity;
        v.planetKm = 5850;
        double ar = MegaKit.red(v.accent), ag = MegaKit.green(v.accent), abl = MegaKit.blue(v.accent);
        double cr = MegaKit.red(v.color), cg = MegaKit.green(v.color), cb = MegaKit.blue(v.color);
        double glow = (1 - v.night) * p[5] + v.night * p[4];
        //le fil : ondule doucement (marees), plus vif en marche
        double sway = 1.2;
        for (int k = 0; k < SAMPLES; k++) {
            double w0 = Math.sin(t * 0.01 + k * 0.33) * sway, w1 = Math.sin(t * 0.01 + (k + 1) * 0.33) * sway;
            ray(b, PX[k], PY[k] + w0, PZ[k], PX[k + 1], PY[k + 1] + w1, PZ[k + 1], 0.16, cr, cg, cb, (0.16 + 0.34 * act) * glow);
        }
        int n = (int) Math.round(p[0]);
        double speed = p[2];
        for (int j = 0; j < n; j++) {
            int k = (int) Math.round((j + 1.0) / (n + 1) * SAMPLES);
            double x = PX[k], y = PY[k] + Math.sin(t * 0.01 + k * 0.33) * sway, z = PZ[k];
            if (y < -2) {
                continue;
            }
            //tangente du fil et repere de la perle
            double tx = PX[k + 1] - PX[k - 1], ty = PY[k + 1] - PY[k - 1], tz = PZ[k + 1] - PZ[k - 1], tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double dl = Math.sqrt(x * x + y * y + z * z), dx = x / dl, dy = y / dl, dz = z / dl;
            //s = d x t (perpendiculaire au fil, dans le plan du ciel)
            double sx = dy * tz - dz * ty, sy = dz * tx - dx * tz, sz = dx * ty - dy * tx;
            double size = 2.4 + 0.5 * (j % 3);
            ellipsoid(b, v, 1e7, x, y, z, size, size, size, 6, 10, 0.86, 0.88, 0.9, 0.7);
            //anneau equatorial et quatre panneaux d'induction en croix
            ring(b, x, y, z, tx * size * 1.9, ty * size * 1.9, tz * size * 1.9, sx * size * 1.9, sy * size * 1.9, sz * size * 1.9, 0.12, 24, ar, ag,
                  abl, (0.12 + 0.35 * act) * glow);
            for (int q = 0; q < 4; q++) {
                double a = q * Math.PI / 2 + Math.PI / 4, ca = Math.cos(a), sa = Math.sin(a);
                double ox = tx * ca + sx * sa, oy = ty * ca + sy * sa, oz = tz * ca + sz * sa;
                double r0 = size * 2.6;
                beam(b, v, 1e7, x + ox * size, y + oy * size, z + oz * size, x + ox * r0, y + oy * r0, z + oz * r0, 0.12, 0.5, 0.52, 0.55, 0.4);
                panel(b, v, 1e7, x + ox * r0 * 1.25, y + oy * r0 * 1.25, z + oz * r0 * 1.25, ox * size * 0.6, oy * size * 0.6, oz * size * 0.6,
                      (sx * ca - tx * sa) * size * 0.35, (sy * ca - ty * sa) * size * 0.35, (sz * ca - tz * sa) * size * 0.35,
                      -dx, -dy, -dz, 0.2, 0.32, 0.4, 0.8);
            }
            //perle allumee : l'une apres l'autre en marche
            double wave = Math.pow(Math.max(0, Math.cos((t * 0.02 * speed - j * 0.9))), 8) * act;
            double tw = 0.75 + 0.25 * Math.sin(t * 0.07 + j * 2.1) * p[1];
            light(b, x, y, z, size * (1.8 + 1.6 * wave), ar, ag, abl, (0.18 + 0.4 * act) * tw * glow + wave);
            if (act > 0.02) {
                //onde de maree qui s'elargit autour de la perle
                double u = frac(t * 0.008 + j * 0.37);
                double rr = size * (2 + 7 * u);
                ring(b, x, y, z, tx * rr, ty * rr, tz * rr, sx * rr, sy * rr, sz * rr, 0.1, 28, ar, ag, abl, act * 0.35 * (1 - u) * glow);
            }
        }
        if (act > 0.02) {
            //impulsions qui courent le long du fil
            for (int q = 0; q < 6; q++) {
                double u = frac(t * 0.0035 * speed + q / 6.0);
                int k = Math.min(SAMPLES - 1, (int) (u * SAMPLES));
                double f = u * SAMPLES - k;
                double x = PX[k] + (PX[k + 1] - PX[k]) * f, z = PZ[k] + (PZ[k + 1] - PZ[k]) * f;
                double y = PY[k] + (PY[k + 1] - PY[k]) * f + Math.sin(t * 0.01 + (k + f) * 0.33) * sway;
                if (y > -1) {
                    light(b, x, y, z, 1.3, 1, 1, 1, act * 0.7 * p[3]);
                }
            }
        }
    }
}
