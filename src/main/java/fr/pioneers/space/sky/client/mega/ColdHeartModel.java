package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cylinderY;
import static fr.pioneers.space.sky.client.mega.MegaKit.ellipsoid;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ringY;

/**
 * 1.0.7 (« ciel grandiose ») : le COEUR FROID de Pluton, sur l'horizon de la plaine glacee : trois reservoirs spheriques sur leurs
 * berceaux, tour de condensation aux ailettes de rayonnement, conduites, et trois GEYSERS d'azote qui montent lentement en scintillant.
 * EN FONCTIONNEMENT : ceintures des reservoirs qui s'allument au cyan, geysers plus hauts et plus denses, halo de givre, impulsions dans les
 * conduites.
 * Reglages SKY : (azimut, demi-largeur de la lueur, hauteur des geysers en degres, largeur, eclat).
 */
public final class ColdHeartModel {

    public static final double SCALE = 1;
    static final double DIST = 72;
    /** Reservoirs : decalage de cote, avant / arriere, rayon, hauteur du centre. */
    private static final double[][] TANKS = {{0, 2, 9, 13}, {-17, -2, 6.5, 9}, {16, -1, 5.5, 8}};

    private ColdHeartModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double azr = Math.toRadians(p[0]);
        v.planetKm = 1188;
        double fx = Math.sin(azr), fz = -Math.cos(azr), sx = -fz, sz = fx;
        double ax = fx * DIST, az = fz * DIST;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        //dalle et halo de givre
        cylinderY(b, v, 1, ax, -6, az, 27, 6, 10, 0.55, 0.6, 0.66, 0.1);
        light(b, ax, 8, az, 34, 0.55, 0.85, 1, 0.03 + 0.04 * night + 0.12 * act);
        //tour de condensation et ailettes
        double tx = ax + fx * 9, tz = az + fz * 9;
        box(b, v, 1, tx, 17, tz, fx * 3, 0, fz * 3, 0, 17, 0, sx * 3, 0, sz * 3, mr * 0.55, mg * 0.6, mb * 0.65, 0.3);
        for (int k = 0; k < 5; k++) {
            double y = 10 + k * 5.5;
            for (int i = -1; i <= 1; i += 2) {
                panel(b, v, 1, tx + sx * 7 * i, y, tz + sz * 7 * i, sx * 4, 0, sz * 4, 0, 0.18, 0, 0, 1, 0, cr * 0.6, cg * 0.7, cb * 0.8, 0.8);
            }
            light(b, tx - fx * 3.1, y, tz - fz * 3.1, 0.55, 0.6, 0.9, 1, (0.1 + 0.7 * night) * (0.6 + 0.4 * hash(k, 2)));
        }
        light(b, tx, 35, tz, 1.4, 1, 1, 1, blink(t, 60, 4, 10));
        //reservoirs, berceaux, ceintures, conduites vers la tour
        for (int k = 0; k < TANKS.length; k++) {
            double[] tk = TANKS[k];
            double cx = ax + sx * tk[0] + fx * tk[1], cz = az + sz * tk[0] + fz * tk[1], r = tk[2], cy = tk[3];
            for (int c = 0; c < 4; c++) {
                double a = Math.PI / 4 + c * Math.PI / 2;
                beam(b, v, 1, cx + Math.cos(a) * r * 0.9, 0, cz + Math.sin(a) * r * 0.9, cx + Math.cos(a) * r * 0.55, cy, cz + Math.sin(a) * r * 0.55,
                      0.45, mr * 0.45, mg * 0.5, mb * 0.55, 0.2);
            }
            ellipsoid(b, v, 1, cx, cy, cz, r, r, r, 8, 14, 0.78, 0.84, 0.9, 1.0);
            double band = 0.12 + 0.12 * night + act * (0.5 + 0.2 * Math.sin(t * 0.08 + k));
            ringY(b, cx, cy, cz, r * 1.02, 0.45, 32, 0.45, 0.9, 1, band);
            beam(b, v, 1, cx, cy * 0.6, cz, tx, cy * 0.6, tz, 0.5, mr * 0.5, mg * 0.55, mb * 0.6, 0.3);
            if (act > 0.02) {
                double u = frac(t / 40.0 + k * 0.3);
                light(b, cx + (tx - cx) * u, cy * 0.6, cz + (tz - cz) * u, 1.2, 0.5, 0.95, 1, act * 0.7);
            }
            light(b, cx, cy + r + 0.6, cz, 0.7, 1, 0.2, 0.15, blink(t, 80, 20, k * 21) * (0.3 + 0.6 * night));
        }
        //geysers d'azote
        double top = DIST * Math.tan(Math.toRadians(p[2])) * (0.8 + 0.6 * act);
        for (int g = 0; g < 3; g++) {
            double gx = ax + sx * (g - 1) * 11 - fx * 12, gz = az + sz * (g - 1) * 11 - fz * 12;
            light(b, gx, 0.5, gz, 3 + act * 2, 0.7, 0.95, 1, 0.25 + 0.4 * act);
            int n = 10 + (int) Math.round(8 * act);
            for (int k = 0; k < n; k++) {
                double u = frac(t * (1 + act) / 700.0 + (double) k / n + g * 0.37);
                double y = 1 + u * top;
                double w = (2 + u * 9) * p[3] / 16;
                double sw = Math.sin(t * 0.004 + k + g) * u * 3;
                light(b, gx + sx * sw, y, gz + sz * sw, w * 3, 0.8, 0.95, 1, (1 - u) * (0.22 + 0.2 * act) * p[4]);
                if (hash(k, g) > 0.6) {
                    double tw = 0.5 + 0.5 * Math.sin(t * 0.3 + k * 2.1);
                    light(b, gx + sx * (sw + (hash(k, 7) - 0.5) * w * 2), y, gz + sz * (sw + (hash(k, 7) - 0.5) * w * 2), 0.35, 1, 1, 1,
                          tw * (1 - u) * 0.8);
                }
            }
        }
    }
}
