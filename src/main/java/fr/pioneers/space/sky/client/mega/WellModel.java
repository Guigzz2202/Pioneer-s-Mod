package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;

/**
 * 1.0.7 (lot 11) : le PUITS DE CRISTAL, vu de Van Maanen b (ciel des planetes de la naine blanche) : un puits en trompe d'anneaux
 * superposes, plonge vers la naine blanche jusqu'a froler son coeur de carbone cristallise, tenu par quatre rails ; a sa bouche, une
 * plate-forme annulaire et ses modules. La bouche s'evase vers l'observateur (axe tourne vers lui et un peu vers le haut) : on regarde
 * dans le puits, ses anneaux en ellipses emboitees, l'etoile au fond.
 * EN FONCTIONNEMENT : des eclats de cristal montent le long du puits jusqu'a la bouche, une onde de feux remonte les anneaux, le fond
 * rougeoie de la chaleur prise a l'etoile et un faisceau de fret descend vers Van Maanen b. Les pieces derriere l'etoile sont des traits de
 * lumiere. Reglages (T:au_dela/megastructures.py, sorte « well ») : longueur du puits en degres, anneaux, rayon de la bouche en degres,
 * cristaux qui montent, scintillement, eclat de nuit.
 */
public final class WellModel {

    public static final double SCALE = 1;
    static final double DIST = PhenomenonModels.DIST;
    /** Inclinaison de l'axe du puits vers l'observateur (radians, depuis le plan du ciel). */
    private static final double LEAN = 1.1;
    /** Hauteur du fond du puits au-dessus de l'etoile (unites). */
    private static final double FLOOR = 1.7;
    private static final int SEG = 28, RISING = 12;

    private WellModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double t = v.ticks, act = v.activity;
        v.planetKm = 7000;
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
        //axe du puits : surtout vers l'observateur (on regarde dans le puits, l'etoile au fond), un peu vers le haut (e2 descend)
        double wx = -e2x * Math.cos(LEAN) - lx * Math.sin(LEAN), wy = -e2y * Math.cos(LEAN) - ly * Math.sin(LEAN);
        double wz = -e2z * Math.cos(LEAN) - lz * Math.sin(LEAN);
        double fx = wy * e1z, fy = wz * e1x - wx * e1z, fz = -wy * e1x;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        double height = DIST * Math.tan(Math.toRadians(p[0])), mouth = DIST * Math.tan(Math.toRadians(p[2]));
        int rings = (int) Math.round(p[1]);
        double ar = MegaKit.red(v.accent), ag = MegaKit.green(v.accent), abl = MegaKit.blue(v.accent);
        double glowNight = 1 + p[5] * v.night;
        //onde de feux qui remonte le puits (en marche)
        double wave = frac(t * 0.01);
        for (int k = 0; k < rings; k++) {
            double u = (double) k / (rings - 1), h = FLOOR + (height - FLOOR) * u, rr = radius(u, mouth);
            double ox = cx + wx * h, oy = cy + wy * h, oz = cz + wz * h;
            double lit = Math.max(0, 1 - Math.abs(u - wave) * 6);
            double lamp = (0.16 + 0.1 * p[4] * Math.sin(t * 0.04 + k)) * glowNight + (0.15 + 0.6 * lit) * act;
            double px = 0, py = 0, pz = 0;
            for (int j = 0; j <= SEG; j++) {
                double a = 2 * Math.PI * j / SEG, ca = Math.cos(a), sa = Math.sin(a);
                double x = ox + (e1x * ca + fx * sa) * rr, y = oy + fy * sa * rr, z = oz + (e1z * ca + fz * sa) * rr;
                if (j > 0) {
                    double mx = (px + x) / 2, my = (py + y) / 2, mz = (pz + z) / 2;
                    boolean solid = h > 6 || mx * lx + my * ly + mz * lz < DIST;
                    if (solid) {
                        beam(b, v, 1e7, px, py, pz, x, y, z, 0.14 + 0.1 * u, 0.5, 0.53, 0.62, 0.6);
                    } else {
                        ray(b, px, py, pz, x, y, z, 0.08, 0.85, 0.9, 1, 0.16);
                    }
                    if (j % 4 == 0) {
                        light(b, x, y, z, 0.35 + 0.25 * u, 0.9, 0.95, 1, lamp);
                    }
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        //quatre rails le long de la trompe
        for (int r = 0; r < 4; r++) {
            double a = Math.PI / 4 + r * Math.PI / 2, ca = Math.cos(a), sa = Math.sin(a);
            double px = 0, py = 0, pz = 0;
            for (int k = 0; k < rings; k++) {
                double u = (double) k / (rings - 1), h = FLOOR + (height - FLOOR) * u, rr = radius(u, mouth) * 1.02;
                double x = cx + wx * h + (e1x * ca + fx * sa) * rr, y = cy + wy * h + fy * sa * rr, z = cz + wz * h + (e1z * ca + fz * sa) * rr;
                if (k > 0) {
                    if (h > 6 || (px + x) / 2 * lx + (py + y) / 2 * ly + (pz + z) / 2 * lz < DIST) {
                        beam(b, v, 1e7, px, py, pz, x, y, z, 0.12, 0.42, 0.44, 0.52, 0.5);
                    } else {
                        ray(b, px, py, pz, x, y, z, 0.06, 0.8, 0.85, 1, 0.06);
                    }
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        //plate-forme de la bouche : anneau large et six modules
        double top = FLOOR + height - FLOOR, ring = mouth * 1.4;
        double ox = cx + wx * top, oy = cy + wy * top, oz = cz + wz * top;
        double px = 0, py = 0, pz = 0;
        for (int j = 0; j <= SEG + 8; j++) {
            double a = 2 * Math.PI * j / (SEG + 8), ca = Math.cos(a), sa = Math.sin(a);
            double x = ox + (e1x * ca + fx * sa) * ring, y = oy + fy * sa * ring, z = oz + (e1z * ca + fz * sa) * ring;
            if (j > 0) {
                beam(b, v, 1e7, px, py, pz, x, y, z, 0.32, 0.46, 0.48, 0.56, 0.6);
            }
            px = x;
            py = y;
            pz = z;
        }
        for (int m = 0; m < 6; m++) {
            double a = 2 * Math.PI * m / 6 + 0.3, ca = Math.cos(a), sa = Math.sin(a);
            double ux = e1x * ca + fx * sa, uy = fy * sa, uz = e1z * ca + fz * sa;
            double tx = -e1x * sa + fx * ca, ty = fy * ca, tz = -e1z * sa + fz * ca;
            double x = ox + ux * ring, y = oy + uy * ring, z = oz + uz * ring;
            double s = mouth * 0.14;
            box(b, v, 1e7, x, y, z, tx * s * 1.6, ty * s * 1.6, tz * s * 1.6, wx * s, wy * s, wz * s, ux * s, uy * s, uz * s, 0.62, 0.64, 0.72, 0.6);
            beam(b, v, 1e7, x, y, z, ox + ux * mouth, oy + uy * mouth, oz + uz * mouth, 0.1, 0.42, 0.44, 0.52, 0.4);
            light(b, x + wx * s * 1.2, y + wy * s * 1.2, z + wz * s * 1.2, 0.5, ar, ag, abl, (0.15 + 0.3 * act) * glowNight
                  * (0.7 + 0.3 * Math.sin(t * 0.05 + m * 1.7) * p[4]));
        }
        //le fond : la chaleur prise a l'etoile
        double bx = cx + wx * FLOOR, by = cy + wy * FLOOR, bz = cz + wz * FLOOR;
        light(b, bx, by, bz, 2.2 + 1.2 * act, 1, 0.86, 0.7, 0.2 + 0.4 * act);
        if (act > 0.02) {
            //eclats de cristal qui montent le long de l'axe, en spirale
            int n = (int) Math.round(RISING * p[3]);
            for (int j = 0; j < n; j++) {
                double u = frac(t * 0.0045 + (double) j / n + hash(j, 5) * 0.03), h = FLOOR + (height - FLOOR) * u;
                double a = j * 2.4 + u * 6, rr = radius(u, mouth) * 0.35;
                double x = cx + wx * h + (e1x * Math.cos(a) + fx * Math.sin(a)) * rr, y = cy + wy * h + fy * Math.sin(a) * rr;
                double z = cz + wz * h + (e1z * Math.cos(a) + fz * Math.sin(a)) * rr;
                double glint = 0.5 + 0.5 * Math.pow(Math.max(0, Math.cos(t * 0.08 + j * 1.9)), 8);
                light(b, x, y, z, 0.8 + 0.5 * glint, 0.92, 0.97, 1, (0.6 + 0.4 * glint) * act);
            }
            //reserve des cristaux a la bouche
            light(b, ox, oy, oz, 1.6 + 0.4 * Math.sin(t * 0.03), 0.9, 0.96, 1, 0.45 * act);
            //faisceau de fret vers Van Maanen b
            double gx = ox, gy = oy, gz = oz;
            double hx = gx * 0.5, hz = gz * 0.5, hy = -3;
            ray(b, gx, gy, gz, hx, hy, hz, 0.28, ar, ag, abl, 0.22 * act);
            for (int k = 0; k < 4; k++) {
                double u = frac(t * 0.012 + k * 0.25);
                light(b, gx + (hx - gx) * u, gy + (hy - gy) * u, gz + (hz - gz) * u, 0.9, 0.9, 0.96, 1, 0.5 * act * (1 - u));
            }
        }
    }

    /** Rayon de la trompe a la fraction u de sa longueur (0 = fond, 1 = bouche) : evasee vers la bouche. */
    private static double radius(double u, double mouth) {
        return Math.max(0.7, mouth * Math.pow(Math.max(0, u), 0.75));
    }
}
