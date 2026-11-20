package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.face;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (lot 11) : les ASTRES SINGULIERS des trois systemes lointains, en 3D dans le ciel de leurs planetes (meme tampon que les
 * megastructures, calcul pur) :
 * <ul>
 *     <li>PULSAR (Lich) : un point bleu-blanc, deux faisceaux opposes qui tournent autour de l'axe magnetique incline (le phare), un
 *     eclair quand un faisceau balaie l'observateur, les boucles du champ, la nebuleuse du vent du pulsar ;</li>
 *     <li>NAINE BLANCHE (Van Maanen) : un point blanc aveuglant et minuscule, son disque de debris incline (anneaux de poussiere et
 *     eclats), des debris qui tombent vers l'etoile ;</li>
 *     <li>TROU NOIR (Gaia BH1) : un disque noir qui masque les etoiles, l'anneau de photons, le disque d'accretion incline (plus vif du
 *     cote qui vient vers l'observateur), l'arc du disque vu par-dessus et par-dessous (lentille), un jet tenu aux poles.</li>
 * </ul>
 * Centre : la direction de l'etoile principale du ciel (la lumiere de la vue), a {@link #DIST} unites.
 */
public final class PhenomenonModels {

    public static final int PULSAR = 0, WHITE_DWARF = 1, BLACK_HOLE = 2;
    static final double DIST = 95;

    private PhenomenonModels() {
    }

    public static void build(int kind, MegaBuffer b, MegaView v) {
        double lx = v.lx, ly = v.ly, lz = v.lz;
        double cx = lx * DIST, cy = ly * DIST, cz = lz * DIST;
        double e1x = -lz, e1z = lx, e1l = Math.sqrt(e1x * e1x + e1z * e1z);
        if (e1l < 1e-6) {
            e1x = 1;
            e1z = 0;
            e1l = 1;
        }
        e1x /= e1l;
        e1z /= e1l;
        double e2x = ly * e1z, e2y = lz * e1x - lx * e1z, e2z = -ly * e1x;
        switch (kind) {
            case PULSAR -> pulsar(b, v, cx, cy, cz, e1x, e1z, e2x, e2y, e2z);
            case WHITE_DWARF -> whiteDwarf(b, v, cx, cy, cz, e1x, e1z, e2x, e2y, e2z);
            case BLACK_HOLE -> blackHole(b, v, cx, cy, cz, e1x, e1z, e2x, e2y, e2z);
            default -> {
            }
        }
    }

    private static void pulsar(MegaBuffer b, MegaView v, double cx, double cy, double cz, double e1x, double e1z, double e2x, double e2y,
          double e2z) {
        double t = v.ticks;
        double lx = v.lx, ly = v.ly, lz = v.lz;
        //nebuleuse du vent : halo diffus bleute, en deux couches
        light(b, cx, cy, cz, 22, 0.35, 0.5, 1, 0.12);
        light(b, cx, cy, cz, 10, 0.5, 0.7, 1, 0.22);
        //axe magnetique incline (dans le plan du ciel, un peu vers l'observateur), faisceaux qui tournent autour
        double tilt = 0.55, spin = t * 0.09;
        double axx = e2x * Math.cos(tilt) + e1x * Math.sin(tilt), axy = e2y * Math.cos(tilt), axz = e2z * Math.cos(tilt) + e1z * Math.sin(tilt);
        //base orthogonale a l'axe
        double px = -lx, py = -ly, pz = -lz;
        double qx = axy * pz - axz * py, qy = axz * px - axx * pz, qz = axx * py - axy * px, ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
        qx /= ql;
        qy /= ql;
        qz /= ql;
        double rx = qy * axz - qz * axy, ry = qz * axx - qx * axz, rz = qx * axy - qy * axx;
        double cone = 0.42;
        double bdx = axx * Math.cos(cone) + (qx * Math.cos(spin) + rx * Math.sin(spin)) * Math.sin(cone);
        double bdy = axy * Math.cos(cone) + (qy * Math.cos(spin) + ry * Math.sin(spin)) * Math.sin(cone);
        double bdz = axz * Math.cos(cone) + (qz * Math.cos(spin) + rz * Math.sin(spin)) * Math.sin(cone);
        //eclair : le faisceau passe pres de l'observateur deux fois par tour
        double flash = Math.pow(Math.max(0, 1 - Math.abs(Math.sin(spin))), 18);
        for (int s = -1; s <= 1; s += 2) {
            double len = 46;
            MegaKit.cone(b, cx, cy, cz, cx + s * bdx * len, cy + s * bdy * len, cz + s * bdz * len, 0.5, 7, 0.75, 0.88, 1, 0.7, 0, 10);
            MegaKit.cone(b, cx, cy, cz, cx + s * bdx * len * 0.6, cy + s * bdy * len * 0.6, cz + s * bdz * len * 0.6, 0.25, 2.2, 1, 1, 1, 0.9, 0,
                  6);
        }
        //boucles du champ magnetique (dipole) autour de l'axe
        for (int k = 0; k < 6; k++) {
            double ph = k * Math.PI / 3 + t * 0.002;
            double mx = qx * Math.cos(ph) + rx * Math.sin(ph), my = qy * Math.cos(ph) + ry * Math.sin(ph), mz = qz * Math.cos(ph) + rz * Math.sin(ph);
            double ox = cx, oy = cy, oz = cz;
            for (int j = 1; j <= 12; j++) {
                double th = Math.PI * j / 12, rr = 9 * Math.sin(th) * Math.sin(th);
                double x = cx + (axx * Math.cos(th) + mx * Math.sin(th)) * rr, y = cy + (axy * Math.cos(th) + my * Math.sin(th)) * rr;
                double z = cz + (axz * Math.cos(th) + mz * Math.sin(th)) * rr;
                ray(b, ox, oy, oz, x, y, z, 0.1, 0.5, 0.7, 1, 0.26);
                ox = x;
                oy = y;
                oz = z;
            }
        }
        //l'etoile : un point, et l'eclair du phare
        light(b, cx, cy, cz, 4.5, 0.6, 0.78, 1, 0.45);
        light(b, cx, cy, cz, 2.2 + 6 * flash, 0.85, 0.92, 1, 1);
        if (flash > 0.05) {
            light(b, cx, cy, cz, 30 * flash, 0.7, 0.85, 1, 0.45 * flash);
        }
    }

    private static void whiteDwarf(MegaBuffer b, MegaView v, double cx, double cy, double cz, double e1x, double e1z, double e2x, double e2y,
          double e2z) {
        double t = v.ticks;
        double lx = v.lx, ly = v.ly, lz = v.lz;
        //disque de debris incline : trois anneaux de poussiere et des eclats
        double tilt = 0.3;
        for (int k = 0; k < 3; k++) {
            double r = 9 + k * 4.5;
            double ax = e1x * r, az = e1z * r;
            double bx = (e2x * tilt - lx * 0.95) * r, by = (e2y * tilt - ly * 0.95) * r, bz = (e2z * tilt - lz * 0.95) * r;
            ring(b, cx, cy, cz, ax, 0, az, bx, by, bz, 0.45 + 0.25 * k, 72, 0.92, 0.9, 1, 0.2 + 0.06 * (2 - k));
            int n = 22 + k * 10;
            double rot = t * (0.0012 - 0.0003 * k);
            for (int j = 0; j < n; j++) {
                double a = rot + 2 * Math.PI * j / n + hash(k, j);
                double ca = Math.cos(a), sa = Math.sin(a);
                double x = cx + ax * ca + bx * sa, y = cy + by * sa, z = cz + az * ca + bz * sa;
                double glint = Math.pow(Math.max(0, Math.cos(t * 0.01 + j * 2.3 + k)), 30);
                light(b, x, y, z, 0.5 + 0.9 * glint, 1, 0.97, 0.92, 0.5 + 0.5 * glint);
            }
        }
        //debris qui tombent vers l'etoile (spirale)
        for (int j = 0; j < 4; j++) {
            double u = frac(t * 0.002 + j * 0.25);
            double a = j * 1.7 + u * 5, r = 18 * (1 - u);
            double x = cx + (e1x * Math.cos(a) + e2x * Math.sin(a) * 0.3) * r, y = cy + e2y * Math.sin(a) * 0.3 * r;
            double z = cz + (e1z * Math.cos(a) + e2z * Math.sin(a) * 0.3) * r;
            light(b, x, y, z, 0.8, 1, 0.85, 0.7, 0.8 * u);
        }
        //l'etoile : minuscule, aveuglante
        light(b, cx, cy, cz, 14, 0.85, 0.92, 1, 0.14);
        light(b, cx, cy, cz, 7, 0.9, 0.95, 1, 0.4);
        light(b, cx, cy, cz, 2.2, 1, 1, 1, 1);
    }

    private static void blackHole(MegaBuffer b, MegaView v, double cx, double cy, double cz, double e1x, double e1z, double e2x, double e2y,
          double e2z) {
        double t = v.ticks;
        double lx = v.lx, ly = v.ly, lz = v.lz;
        double rs = 4.2;
        //disque d'accretion incline (presque par la tranche), plus vif du cote qui vient vers l'observateur (effet Doppler)
        double tilt = 0.22;
        int rings = 9;
        for (int k = 0; k < rings; k++) {
            double r = rs * (1.7 + k * 0.55);
            double ax = e1x * r, az = e1z * r;
            double bx = (e2x * tilt - lx * 0.97) * r, by = (e2y * tilt - ly * 0.97) * r, bz = (e2z * tilt - lz * 0.97) * r;
            int n = 64;
            double heat = 1 - (double) k / rings;
            double rot = t * (0.02 / (1 + k * 0.4));
            for (int j = 0; j < n; j++) {
                double a0 = 2 * Math.PI * j / n + rot, a1 = 2 * Math.PI * (j + 1) / n + rot;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                //cote qui approche : c > 0 (vers e1) ; tourbillons
                double doppler = 0.45 + 0.55 * Math.max(0, c0);
                double swirl = 0.7 + 0.3 * Math.sin(a0 * 5 + k * 1.3 - t * 0.05);
                double a = (0.28 + 0.5 * heat) * doppler * swirl;
                ray(b, cx + ax * c0 + bx * s0, cy + by * s0, cz + az * c0 + bz * s0, cx + ax * c1 + bx * s1, cy + by * s1, cz + az * c1 + bz * s1,
                      0.35 + 0.15 * heat, 1, 0.55 + 0.4 * heat, 0.35 + 0.5 * heat, a);
            }
        }
        //lentille : l'arriere du disque vu par-dessus et par-dessous l'ombre (deux arcs)
        for (int s = -1; s <= 1; s += 2) {
            double r = rs * 2.1;
            double ox = cx, oy = cy, oz = cz;
            for (int j = 0; j <= 32; j++) {
                double a = Math.PI * j / 32;
                double x = cx + e1x * Math.cos(a) * r + e2x * Math.sin(a) * r * 0.62 * s, y = cy + e2y * Math.sin(a) * r * 0.62 * s;
                double z = cz + e1z * Math.cos(a) * r + e2z * Math.sin(a) * r * 0.62 * s;
                if (j > 0) {
                    ray(b, ox, oy, oz, x, y, z, 0.55, 1, 0.75, 0.55, 0.5);
                }
                ox = x;
                oy = y;
                oz = z;
            }
        }
        //anneau de photons
        ring(b, cx, cy, cz, e1x * rs * 1.08, 0, e1z * rs * 1.08, e2x * rs * 1.08, e2y * rs * 1.08, e2z * rs * 1.08, 0.24, 64, 1, 0.92, 0.8,
              0.9 + 0.1 * Math.sin(t * 0.07));
        //l'ombre : disque noir tourne vers l'observateur (masque les etoiles et le fond du disque)
        int n = 40;
        for (int j = 0; j < n; j++) {
            double a0 = 2 * Math.PI * j / n, a1 = 2 * Math.PI * (j + 1) / n;
            double x0 = cx + (e1x * Math.cos(a0) + e2x * Math.sin(a0)) * rs, y0 = cy + e2y * Math.sin(a0) * rs;
            double z0 = cz + (e1z * Math.cos(a0) + e2z * Math.sin(a0)) * rs;
            double x1 = cx + (e1x * Math.cos(a1) + e2x * Math.sin(a1)) * rs, y1 = cy + e2y * Math.sin(a1) * rs;
            double z1 = cz + (e1z * Math.cos(a1) + e2z * Math.sin(a1)) * rs;
            v.litOverride = 0;
            face(b, v, 1e7, cx, cy, cz, cx, cy, cz, x1, y1, z1, x0, y0, z0, -lx, -ly, -lz, 0, 0, 0, 0);
            v.litOverride = -1;
        }
        //jet aux poles : tenu, qui pulse
        double pulse = 0.6 + 0.4 * Math.sin(t * 0.03);
        for (int s = -1; s <= 1; s += 2) {
            MegaKit.cone(b, cx, cy, cz, cx + e2x * 40 * s, cy + e2y * 40 * s, cz + e2z * 40 * s, 0.35, 2.5, 0.65, 0.5, 1, 0.42 * pulse, 0, 10);
        }
    }

    /** Hauteur (degres) ou regarder l'astre singulier (apercus). */
    public static double viewPitch(MegaView v) {
        return Math.max(10, Math.min(70, Math.toDegrees(Math.asin(v.ly))));
    }
}
