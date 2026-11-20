package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (« ciel grandiose ») : l'ANNEAU-ROTOR de Saturne, vu de Saturne : un rail en treillis qui suit les anneaux d'un horizon a l'autre
 * et sept roues colossales qui tournent sur le rail (jante en caissons, rayons, moyeu), baignees dans la poussiere des anneaux.
 * EN FONCTIONNEMENT : les roues tournent bien plus vite, leurs jantes s'allument, l'energie court le long du rail et des arcs relient les
 * roues voisines.
 * Reglages SKY : (azimut du sommet, -, hauteur du sommet en degres, -, -, vitesse de rotation).
 */
public final class RingRotorModel {

    public static final double SCALE = 1;
    static final double DIST = 98;
    static final int RAIL = 40, WHEELS = 7, RIM = 22;

    private RingRotorModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double az = Math.toRadians(p[0]), apex = Math.toRadians(p[2]);
        v.planetKm = 58232;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double axd = Math.cos(apex) * Math.sin(az), ayd = Math.sin(apex), azd = -Math.cos(apex) * Math.cos(az);
        double ex = Math.cos(az), ez = Math.sin(az);
        //rail en treillis le long de l'arc, poussiere des anneaux
        double h = Math.PI * DIST / RAIL * 0.5;
        for (int k = 0; k < RAIL; k++) {
            double th = Math.PI * (k + 0.5) / RAIL, c = Math.cos(th), s = Math.sin(th);
            double rx = c * ex + s * axd, ry = s * ayd, rz = c * ez + s * azd;
            double tx = -s * ex + c * axd, ty = c * ayd, tz = -s * ez + c * azd;
            double px = DIST * rx, py = DIST * ry, pz = DIST * rz;
            double bx0 = ty * rz - tz * ry, by0 = tz * rx - tx * rz, bz0 = tx * ry - ty * rx;
            box(b, v, 30000, px, py, pz, tx * h, ty * h, tz * h, rx * 0.8, ry * 0.8, rz * 0.8, bx0 * 1.6, by0 * 1.6, bz0 * 1.6, cr * 0.55, cg * 0.5,
                  cb * 0.45, 0.4);
            light(b, px, py, pz, 9, mr, mg, mb, 0.035 + 0.03 * night);
            double chase = Math.pow(Math.max(0, Math.cos((k / (double) RAIL - t * 0.004) * Math.PI * 10)), 8);
            if (act > 0.02) {
                light(b, px - rx, py - ry, pz - rz, 1.2, 1, 0.85, 0.5, act * chase);
            }
        }
        //roues
        double speed = p[5] * (1 + 6 * act);
        for (int w = 0; w < WHEELS; w++) {
            double th = Math.PI * (w + 0.5) / WHEELS, c = Math.cos(th), s = Math.sin(th);
            double rx = c * ex + s * axd, ry = s * ayd, rz = c * ez + s * azd;
            double tx = -s * ex + c * axd, ty = c * ayd, tz = -s * ez + c * azd;
            //plan de la roue : direction de visee (r) et travers (t x r) ; l'axe de la roue suit le rail
            double bx = ty * rz - tz * ry, by = tz * rx - tx * rz, bz = tx * ry - ty * rx;
            double cx = DIST * rx - rx * 1.5, cy = DIST * ry - ry * 1.5, cz = DIST * rz - rz * 1.5;
            double rad = 8.5, rot = t * speed * (w % 2 == 0 ? 1 : -1) + w;
            double hs = Math.PI * rad / RIM * 1.05;
            for (int k = 0; k < RIM; k++) {
                double a = rot + 2 * Math.PI * k / RIM, ca = Math.cos(a), sa = Math.sin(a);
                double ox = tx * ca + bx * sa, oy = ty * ca + by * sa, oz = tz * ca + bz * sa;
                double gx = -tx * sa + bx * ca, gy = -ty * sa + by * ca, gz = -tz * sa + bz * ca;
                boolean band = k % 4 == 0;
                box(b, v, 30000, cx + ox * rad, cy + oy * rad, cz + oz * rad, gx * hs, gy * hs, gz * hs, ox * 0.9, oy * 0.9, oz * 0.9, rx * 1.1,
                      ry * 1.1, rz * 1.1, band ? cr : mr * 0.85, band ? cg : mg * 0.82, band ? cb : mb * 0.78, 0.5);
                if (k % 2 == 0) {
                    light(b, cx + ox * (rad - 1), cy + oy * (rad - 1), cz + oz * (rad - 1), 0.45, 1, 0.85, 0.55,
                          (0.15 + 0.7 * night) * (0.5 + 0.5 * MegaKit.hash(w, k)) + act * 0.3);
                }
            }
            for (int k = 0; k < 6; k++) {
                double a = rot + Math.PI * k / 3, ca = Math.cos(a), sa = Math.sin(a);
                double ox = tx * ca + bx * sa, oy = ty * ca + by * sa, oz = tz * ca + bz * sa;
                beam(b, v, 30000, cx + ox * 1.2, cy + oy * 1.2, cz + oz * 1.2, cx + ox * (rad - 0.9), cy + oy * (rad - 0.9), cz + oz * (rad - 0.9), 0.3,
                      mr * 0.7, mg * 0.68, mb * 0.62, 0.3);
            }
            box(b, v, 30000, cx, cy, cz, rx * 1.6, ry * 1.6, rz * 1.6, tx * 1.3, ty * 1.3, tz * 1.3, bx * 1.3, by * 1.3, bz * 1.3, cr * 0.8, cg * 0.75,
                  cb * 0.7, 0.4);
            light(b, cx - rx * 1.4, cy - ry * 1.4, cz - rz * 1.4, 0.9, 1, 0.25, 0.15, blink(t, 60, 10, w * 13) * (0.4 + 0.6 * night));
            if (act > 0.02) {
                ring(b, cx, cy, cz, tx * rad, ty * rad, tz * rad, bx * rad, by * rad, bz * rad, 0.5, 28, 1, 0.8, 0.45, act * 0.45);
                if (w + 1 < WHEELS) {
                    double th2 = Math.PI * (w + 1.5) / WHEELS, c2 = Math.cos(th2), s2 = Math.sin(th2);
                    double nx = (c2 * ex + s2 * axd) * (DIST - 1.5), ny = s2 * ayd * (DIST - 1.5), nz = (c2 * ez + s2 * azd) * (DIST - 1.5);
                    double flick = 0.5 + 0.5 * Math.sin(t * 0.37 + w * 2.3);
                    ray(b, cx, cy, cz, nx, ny, nz, 0.35, 1, 0.85, 0.55, act * 0.35 * flick);
                }
            }
        }
    }
}
