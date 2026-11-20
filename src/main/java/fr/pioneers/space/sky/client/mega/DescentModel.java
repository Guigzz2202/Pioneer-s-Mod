package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cone;
import static fr.pioneers.space.sky.client.mega.MegaKit.cylinderY;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ringY;

/**
 * 1.0.7 (« ciel grandiose ») : la DESCENTE de Neptune, vue de Neptune : une station tres haute dans le ciel bleu et, sous elle, une colonne
 * verticale (trois rails et un faisceau) qui plonge dans les nuages jusqu'a un tourbillon sombre sur l'horizon ; anneaux de stabilisation,
 * capsules qui descendent et remontent.
 * EN FONCTIONNEMENT : faisceau plus vif, capsules nombreuses avec leurs trainees, anneaux qui pulsent vers le bas, tourbillon qui s'eclaire.
 * Reglages SKY : (azimut, hauteur de la station en degres, largeur du faisceau, ...).
 */
public final class DescentModel {

    public static final double SCALE = 1;
    static final double DIST = 100;

    private DescentModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double az = Math.toRadians(p[0]), el = Math.toRadians(p[1]);
        v.planetKm = 24622;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double hd = Math.cos(el) * DIST, top = Math.sin(el) * DIST;
        double ax = Math.sin(az) * hd, az0 = -Math.cos(az) * hd;
        double bottom = -6;
        //colonne : trois rails, faisceau
        for (int c = 0; c < 3; c++) {
            double a = 2 * Math.PI * c / 3 + 0.3;
            beam(b, v, 3000, ax + Math.cos(a) * 1.4, bottom, az0 + Math.sin(a) * 1.4, ax + Math.cos(a) * 1.4, top - 6, az0 + Math.sin(a) * 1.4, 0.3,
                  mr * 0.6, mg * 0.65, mb * 0.75, 0.6);
        }
        ray(b, ax, bottom, az0, ax, top - 6, az0, p[2] * (1.2 + act), mr, mg, mb, 0.12 + 0.1 * night + 0.35 * act);
        ray(b, ax, bottom, az0, ax, top - 6, az0, 0.35, 0.85, 0.95, 1, 0.2 + 0.5 * act);
        //anneaux de stabilisation
        for (int k = 0; k < 7; k++) {
            double y = bottom + 6 + k * (top - 12 - bottom) / 7;
            ringY(b, ax, y, az0, 3.2, 0.35, 16, 0.5, 0.75, 1, 0.15 + 0.25 * night);
            if (act > 0.02) {
                double u = frac(t / 60.0 + k / 7.0);
                double yy = top - 8 - u * (top - 8 - bottom);
                ringY(b, ax, yy, az0, 3 + 4 * (1 - u), 0.5, 20, 0.55, 0.85, 1, act * 0.6 * (1 - u));
            }
        }
        //capsules
        int n = 2 + (int) Math.round(5 * act);
        for (int k = 0; k < n; k++) {
            double u = frac(t * (1 + 2.5 * act) / 2400.0 + (double) k / n);
            boolean down = k % 2 == 0;
            double y = down ? top - 10 - u * (top - 10 - bottom) : bottom + u * (top - 10 - bottom);
            double a = 2 * Math.PI * (k % 3) / 3 + 1.3, ox = Math.cos(a) * 2.4, oz = Math.sin(a) * 2.4;
            box(b, v, 3000, ax + ox, y, az0 + oz, 0.9, 0, 0, 0, 1.6, 0, 0, 0, 0.9, 0.85, 0.9, 0.95, 0.6);
            light(b, ax + ox, y, az0 + oz, 1.4 + act, 0.6, 0.85, 1, 0.45 + 0.4 * act);
            if (act > 0.02) {
                ray(b, ax + ox, y, az0 + oz, ax + ox, y + (down ? 10 : -10), az0 + oz, 0.5, 0.55, 0.8, 1, act * 0.35);
            }
        }
        //station : moyeu, anneau qui tourne, aiguilles d'amarrage, feux
        double sy = top;
        cylinderY(b, v, 26000, ax, sy - 6, az0, 4, 12, 14, mr * 0.75, mg * 0.8, mb * 0.85, 0.5);
        double rot = t * (0.0008 + 0.002 * act);
        for (int k = 0; k < 36; k++) {
            double a = rot + 2 * Math.PI * k / 36, ca = Math.cos(a), sa = Math.sin(a), hs = Math.PI * 15 / 36 * 1.04;
            box(b, v, 26000, ax + ca * 15, sy, az0 + sa * 15, -sa * hs, 0, ca * hs, ca * 1.3, 0, sa * 1.3, 0, 1.1, 0,
                  k % 6 == 0 ? cr : mr * 0.8, k % 6 == 0 ? cg : mg * 0.82, k % 6 == 0 ? cb : mb * 0.85, 0.5);
            light(b, ax + ca * 15, sy - 1.2, az0 + sa * 15, 0.5, 0.75, 0.9, 1, 0.15 + 0.7 * night + 0.2 * act);
        }
        for (int k = 0; k < 6; k++) {
            double a = rot + Math.PI * k / 3;
            beam(b, v, 26000, ax + Math.cos(a) * 4, sy, az0 + Math.sin(a) * 4, ax + Math.cos(a) * 14, sy, az0 + Math.sin(a) * 14, 0.35, mr * 0.6,
                  mg * 0.65, mb * 0.7, 0.3);
        }
        cone(b, ax, sy + 6, az0, ax, sy + 16, az0, 1.2, 0.1, 0.6, 0.85, 1, 0.3 + 0.3 * act, 0, 3);
        light(b, ax, sy + 7, az0, 1.5, 1, 1, 1, blink(t, 60, 4, 0));
        //tourbillon sombre sur l'horizon, au pied de la colonne
        double spin = t * (0.002 + 0.006 * act);
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < 14; k++) {
                double r = 2 + k * 1.6, a = spin + arm * 2 * Math.PI / 3 + k * 0.38;
                light(b, ax + Math.cos(a) * r, bottom + 1, az0 + Math.sin(a) * r, 2.6 + k * 0.25, 0.35, 0.5 + 0.2 * act, 0.95,
                      (0.05 + 0.05 * night + 0.12 * act) * (1 - k / 16.0));
            }
        }
    }
}
