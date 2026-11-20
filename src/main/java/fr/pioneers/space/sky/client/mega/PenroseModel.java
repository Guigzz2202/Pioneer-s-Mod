package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (lot 11) : l'ANNEAU DE PENROSE, vu de Gaia BH1 b (ciel des planetes du trou noir) : un anneau de stations autour du trou noir,
 * incline en travers de son disque d'accretion (on le voit en ellipse penchee), double rail et traverses, des stations a intervalles
 * reguliers reliees par des cables a l'ergosphere. EN FONCTIONNEMENT : chaque station lache des lests qui tombent vers le trou noir, se
 * scindent dans l'ergosphere (eclair) et dont la moitie ressort plus vite, chargee de l'energie de rotation (le procede de Penrose), rattrapee
 * par sa station ; l'ergosphere s'illumine, des feux courent le long de l'anneau dans le sens de la rotation et un faisceau descend vers
 * Gaia BH1 b. L'anneau ne passe jamais devant l'ombre (son petit axe reste plus grand qu'elle). Reglages (T:au_dela/megastructures.py, sorte « penrose ») :
 * rayon en degres, aplatissement, stations, lests en vol par station, eclat des retours, eclat de nuit.
 */
public final class PenroseModel {

    public static final double SCALE = 1;
    static final double DIST = PhenomenonModels.DIST;
    /** Rayon de l'ombre du trou noir (unites, comme {@link PhenomenonModels}) et de l'ergosphere. */
    private static final double SHADOW = 4.2, ERGO = SHADOW * 1.55;
    /** Penche de l'anneau dans le ciel (radians) : en travers du disque d'accretion. */
    private static final double SLANT = 0.6;
    private static final int RAIL = 72, RUNNERS = 14;

    private PenroseModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double t = v.ticks, act = v.activity;
        v.planetKm = 6500;
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
        double radius = DIST * Math.tan(Math.toRadians(p[0])), flat = p[1], st = Math.sqrt(1 - flat * flat);
        double cs = Math.cos(SLANT), ss = Math.sin(SLANT);
        //axe A dans le plan du ciel (penche), axe B : la normale du ciel a A, aplatie et inclinee vers le fond
        double ux = e1x * cs + e2x * ss, uy = e2y * ss, uz = e1z * cs + e2z * ss;
        double nx = -e1x * ss + e2x * cs, ny = e2y * cs, nz = -e1z * ss + e2z * cs;
        double ax = ux * radius, ay = uy * radius, az = uz * radius;
        double bx = (nx * flat - lx * st) * radius, by = (ny * flat - ly * st) * radius, bz = (nz * flat - lz * st) * radius;
        //normale du plan de l'anneau
        double qx = ay * bz - az * by, qy = az * bx - ax * bz, qz = ax * by - ay * bx, ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
        qx /= ql;
        qy /= ql;
        qz /= ql;
        double ar = MegaKit.red(v.accent), ag = MegaKit.green(v.accent), abl = MegaKit.blue(v.accent);
        double glowNight = 1 + p[5] * v.night;
        double off = radius * 0.025;
        //double rail et traverses
        for (int k = 0; k < RAIL; k++) {
            double a0 = 2 * Math.PI * k / RAIL, a1 = 2 * Math.PI * (k + 1) / RAIL;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double x0 = cx + ax * c0 + bx * s0, y0 = cy + ay * c0 + by * s0, z0 = cz + az * c0 + bz * s0;
            double x1 = cx + ax * c1 + bx * s1, y1 = cy + ay * c1 + by * s1, z1 = cz + az * c1 + bz * s1;
            //la moitie du fond (derriere le trou noir) un peu plus sombre ; l'anneau ne passe jamais devant l'ombre
            double shade = (x0 + x1) / 2 * lx + (y0 + y1) / 2 * ly + (z0 + z1) / 2 * lz < DIST ? 1 : 0.75;
            for (int s = -1; s <= 1; s += 2) {
                double ox = qx * off * s, oy = qy * off * s, oz = qz * off * s;
                beam(b, v, 1e7, x0 + ox, y0 + oy, z0 + oz, x1 + ox, y1 + oy, z1 + oz, 0.2, 0.34 * shade, 0.3 * shade, 0.42 * shade, 0.55);
            }
            if (k % 2 == 0) {
                beam(b, v, 1e7, x0 - qx * off, y0 - qy * off, z0 - qz * off, x0 + qx * off, y0 + qy * off, z0 + qz * off, 0.12, 0.3, 0.27,
                      0.36, 0.3);
            }
            ray(b, x0, y0, z0, x1, y1, z1, 0.07, ar, ag, abl, (0.08 + 0.2 * act) * glowNight);
        }
        //l'ergosphere : anneau aplati autour de l'ombre, dans le plan de l'anneau
        double er = ERGO / radius;
        ring(b, cx, cy, cz, ax * er, ay * er, az * er, bx * er, by * er, bz * er, 0.22, 48, ar, ag, abl,
              0.08 + 0.3 * act * (0.7 + 0.3 * Math.sin(t * 0.05)));
        //stations, cables et lests
        int n = (int) Math.round(p[2]), fly = (int) Math.round(p[3]);
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * (k + 0.5) / n, ca = Math.cos(a), sa = Math.sin(a);
            double rx = ax * ca + bx * sa, ry = ay * ca + by * sa, rz = az * ca + bz * sa, rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double dx = rx / rl, dy = ry / rl, dz = rz / rl;
            double tx = qy * dz - qz * dy, ty = qz * dx - qx * dz, tz = qx * dy - qy * dx;
            double sx = cx + rx, sy = cy + ry, sz = cz + rz;
            double ex = cx + rx * er, ey = cy + ry * er, ez = cz + rz * er;
            double s = radius * 0.05;
            box(b, v, 1e7, sx, sy, sz, tx * s * 1.4, ty * s * 1.4, tz * s * 1.4, qx * s, qy * s, qz * s, dx * s * 0.8, dy * s * 0.8, dz * s * 0.8,
                  0.44, 0.4, 0.52, 0.6);
            double tw = 0.7 + 0.3 * Math.sin(t * 0.045 + k * 1.9);
            light(b, sx - dx * s, sy - dy * s, sz - dz * s, 0.6 + 0.3 * act, ar, ag, abl, (0.3 + 0.4 * act) * tw * glowNight);
            //cable vers l'ergosphere
            ray(b, sx, sy, sz, ex, ey, ez, 0.06, 0.8, 0.7, 1, (0.05 + 0.15 * act) * glowNight);
            if (act <= 0.02) {
                continue;
            }
            for (int j = 0; j < fly; j++) {
                double u = frac(t * 0.003 + (double) j / fly + hash(k, j) * 0.4);
                if (u < 0.55) {
                    //chute : lente au depart, rapide pres du trou noir
                    double w = Math.pow(u / 0.55, 2.2);
                    double x = sx + (ex - sx) * w, y = sy + (ey - sy) * w, z = sz + (ez - sz) * w;
                    light(b, x, y, z, 0.35, 1, 0.55, 0.4, 0.35 * act);
                } else if (u < 0.6) {
                    //scission dans l'ergosphere
                    double f = 1 - (u - 0.55) / 0.05;
                    light(b, ex, ey, ez, 0.6 + 1.6 * f, 0.85, 0.7, 1, f * act * p[4]);
                } else {
                    //retour, plus rapide, charge d'energie (rattrape par la station)
                    double w = Math.min(1, (u - 0.6) / 0.25);
                    double x = ex + (sx - ex) * w, y = ey + (sy - ey) * w, z = ez + (sz - ez) * w;
                    if (w < 1) {
                        ray(b, ex + (sx - ex) * Math.max(0, w - 0.15), ey + (sy - ey) * Math.max(0, w - 0.15), ez + (sz - ez) * Math.max(0, w - 0.15),
                              x, y, z, 0.12, 0.75, 0.85, 1, 0.5 * act * p[4]);
                        light(b, x, y, z, 0.55, 0.85, 0.92, 1, 0.8 * act * p[4]);
                    } else {
                        //rattrape : eclair a la station, qui s'eteint
                        light(b, sx, sy, sz, 1.4, ar, ag, abl, (1 - (u - 0.85) / 0.15) * 0.45 * act * p[4]);
                    }
                }
            }
        }
        if (act > 0.02) {
            //feux qui courent dans le sens de la rotation du trou noir (entrainement du referentiel)
            for (int k = 0; k < RUNNERS; k++) {
                double a = 2 * Math.PI * frac(t * 0.0035 + (double) k / RUNNERS), ca = Math.cos(a), sa = Math.sin(a);
                light(b, cx + ax * ca + bx * sa, cy + ay * ca + by * sa, cz + az * ca + bz * sa, 0.7, ar, ag, abl, 0.45 * act);
            }
            //faisceau vers Gaia BH1 b, depuis le bas de l'anneau (e2, donc n, descend)
            double gx = cx + bx, gy = cy + by, gz = cz + bz;
            double hx = gx * 0.55, hz = gz * 0.55, hy = -3;
            ray(b, gx, gy, gz, hx, hy, hz, 0.32, ar, ag, abl, 0.26 * act);
            for (int k = 0; k < 4; k++) {
                double u = frac(t * 0.012 + k * 0.25);
                light(b, gx + (hx - gx) * u, gy + (hy - gy) * u, gz + (hz - gz) * u, 1.0, 0.85, 0.75, 1, 0.55 * act * (1 - u));
            }
        }
    }
}
