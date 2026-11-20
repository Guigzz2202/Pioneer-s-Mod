package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cone;
import static fr.pioneers.space.sky.client.mega.MegaKit.cylinderY;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ringY;

/**
 * 1.0.7 (« ciel grandiose ») : le GRAND FORAGE de Venus vu de Venus, sur l'horizon dans la brume jaune : plate-forme hexagonale sur son
 * caisson, derrick colossal en treillis (quatre pieds, ceintures, croix de Saint-Andre), moufle, train de tiges incandescent, deux torcheres,
 * lueur du puits sur l'horizon et PANACHE de gaz brulants qui monte et derive avec le vent.
 * EN FONCTIONNEMENT : train de tiges chauffe a blanc, tete de forage qui tourne (anneau de feux), panache plus haut et plus dense, eclairs
 * dans le panache, torcheres qui rugissent.
 * Reglages SKY : (azimut, demi-largeur de la lueur, hauteur du panache en degres, largeur du panache, eclat).
 */
public final class DrillModel {

    public static final double SCALE = 0.9;
    static final double DIST = 85, TOP = 52;
    /** Pieds du derrick (fil du rendu seulement : aucune allocation par image). */
    private static final double[] LX = new double[4], LZ = new double[4];
    private static final int[][] CORNER = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private DrillModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double azr = Math.toRadians(p[0]);
        v.planetKm = 6052;
        double fx = Math.sin(azr), fz = -Math.cos(azr), sx = -fz, sz = fx;
        double ax = fx * DIST, az = fz * DIST;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        //lueur du puits sur l'horizon
        light(b, ax, 2, az, 30 + 6 * act, 1, 0.45, 0.12, 0.08 + 0.14 * night + 0.18 * act);
        //plate-forme et caisson
        cylinderY(b, v, 1, ax, -30, az, 13, 27, 6, cr * 0.35, cg * 0.3, cb * 0.28, 0.05);
        cylinderY(b, v, 1, ax, -3, az, 19, 5, 6, cr * 0.55, cg * 0.5, cb * 0.45, 0.15);
        for (int k = 0; k < 18; k++) {                                     //baies du bord de la plate-forme
            double a = 2 * Math.PI * k / 18, ox = Math.cos(a) * 19.3, oz = Math.sin(a) * 19.3;
            if (Math.cos(a) * (ax + ox) + Math.sin(a) * (az + oz) < 0) {
                light(b, ax + ox, 0, az + oz, 0.7, 1, 0.75, 0.4, (0.1 + 0.8 * night) * (0.6 + 0.4 * hash(k, 9)));
            }
        }
        //derrick : quatre pieds qui se resserrent, ceintures, croix sur la face avant
        double[] lx = LX, lz = LZ;
        int[][] corner = CORNER;
        for (int level = 0; level <= 5; level++) {
            double y = 2 + (TOP - 2) * level / 5.0, half = 11 - 8.4 * level / 5.0;
            for (int c = 0; c < 4; c++) {
                double nx = ax + sx * half * corner[c][0] + fx * half * corner[c][1], nz = az + sz * half * corner[c][0] + fz * half * corner[c][1];
                if (level > 0) {
                    double py = 2 + (TOP - 2) * (level - 1) / 5.0;
                    beam(b, v, 2, lx[c], py, lz[c], nx, y, nz, 0.8, mr * 0.55, mg * 0.5, mb * 0.45, 0.25);
                    if (c == 0) {
                        //croix de la face avant (pieds 0 et 1, vers l'observateur)
                        beam(b, v, 2, lx[0], py, lz[0], ax + sx * half + fx * -half, y, az + sz * half + fz * -half, 0.35, mr * 0.5, mg * 0.45,
                              mb * 0.4, 0.2);
                        beam(b, v, 2, lx[1], py, lz[1], ax - sx * half - fx * half, y, az - sz * half - fz * half, 0.35, mr * 0.5, mg * 0.45,
                              mb * 0.4, 0.2);
                    }
                }
                lx[c] = nx;
                lz[c] = nz;
            }
            if (level > 0) {
                for (int c = 0; c < 4; c++) {
                    int d = (c + 1) % 4;
                    beam(b, v, 2, lx[c], y, lz[c], lx[d], y, lz[d], 0.45, mr * 0.5, mg * 0.45, mb * 0.4, 0.2);
                }
                light(b, lx[0], y, lz[0], 0.8, 1, 0.15, 0.1, blink(t, 70, 25, level * 9) * (0.3 + 0.6 * night));
                light(b, lx[1], y, lz[1], 0.8, 1, 0.15, 0.1, blink(t, 70, 25, level * 9 + 35) * (0.3 + 0.6 * night));
            }
        }
        //moufle au sommet
        box(b, v, 4, ax, TOP + 1.8, az, fx * 4, 0, fz * 4, 0, 1.8, 0, sx * 4, 0, sz * 4, cr * 0.7, cg * 0.6, cb * 0.5, 0.3);
        light(b, ax, TOP + 4.2, az, 1.6, 1, 1, 1, blink(t, 60, 4, 0));
        //train de tiges : rouge sombre au repos, blanc en fonctionnement
        double heat = 0.25 + 0.75 * act;
        ray(b, ax, 0, az, ax, TOP, az, 1.0 + act, 1, 0.45 + 0.45 * act, 0.15 + 0.6 * act, 0.25 + 0.55 * heat);
        ray(b, ax, 0, az, ax, TOP, az, 0.35, 1, 0.9, 0.7 * act + 0.2, 0.3 + 0.6 * act);
        //tete de forage : anneau de feux qui tourne
        double spin = t * (0.02 + 0.2 * act);
        for (int k = 0; k < 10; k++) {
            double a = spin + 2 * Math.PI * k / 10;
            light(b, ax + Math.cos(a) * 5, 1.5, az + Math.sin(a) * 5, 0.9, 1, 0.6, 0.2, 0.25 + 0.6 * act);
        }
        if (act > 0.02) {
            ringY(b, ax, 1.2, az, 7 + 2 * Math.sin(t * 0.2), 0.6, 24, 1, 0.55, 0.2, act * 0.5);
        }
        //torcheres
        for (int i = -1; i <= 1; i += 2) {
            double tx = ax + sx * 17 * i + fx * 4, tz = az + sz * 17 * i + fz * 4;
            cylinderY(b, v, 1, tx, 2, tz, 1.3, 26, 8, mr * 0.5, mg * 0.45, mb * 0.4, 0.2);
            double flick = 0.7 + 0.3 * Math.sin(t * 0.7 + i) * Math.sin(t * 1.3);
            light(b, tx, 29 + 2 * act, tz, (3 + 4 * act) * flick, 1, 0.55, 0.15, 0.6 + 0.3 * act);
            cone(b, tx, 28, tz, tx + sx * 2, 36 + 10 * act, tz + sz * 2, 1.4, 0.2, 1, 0.65, 0.25, (0.45 + 0.4 * act) * flick, 0, 3);
        }
        //panache : bouffees qui montent et derivent
        double top = DIST * Math.tan(Math.toRadians(p[2])) + 6;
        int n = 18 + (int) Math.round(12 * act);
        double speed = (1 + 1.5 * act) / 900.0;
        for (int k = 0; k < n; k++) {
            double u = frac(t * speed + (double) k / n);
            double y = TOP + 4 + u * (top - TOP) * (0.75 + 0.35 * act);
            double drift = Math.pow(u, 1.4) * 26 + Math.sin(t * 0.01 + k) * 2;
            double w = (5 + u * 20) * p[3] / 2.6;
            double hot = 1 - u;
            light(b, ax + sx * drift, y, az + sz * drift, w, 0.75 + 0.25 * hot, 0.42 + 0.25 * hot, 0.25 + 0.1 * hot,
                  (1 - u) * (0.16 + 0.16 * act + 0.1 * night) * p[4]);
        }
        //eclairs dans le panache (fonctionnement)
        if (act > 0.3) {
            int seed = (int) Math.floor(t / 23);
            if (hash(seed, 3) < 0.35 * act && frac(t / 23) < 0.25) {
                double y0 = TOP + 12 + hash(seed, 4) * (top - TOP) * 0.6, d0 = (y0 - TOP) / (top - TOP) * 20;
                double px = ax + sx * d0, pz = az + sz * d0, py = y0;
                for (int j = 0; j < 4; j++) {
                    double qx = px + sx * (hash(seed, 10 + j) - 0.5) * 8, qy = py - 4 - hash(seed, 20 + j) * 4, qz = pz + sz * (hash(seed, 10 + j) - 0.5) * 8;
                    ray(b, px, py, pz, qx, qy, qz, 0.35, 0.85, 0.85, 1, 0.9);
                    px = qx;
                    py = qy;
                    pz = qz;
                }
            }
        }
    }
}
