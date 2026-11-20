package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (« ciel grandiose ») : l'ESSAIM autour du Soleil, dans les ciels du systeme solaire : trois orbites inclinees de capteurs (panneaux
 * sombres en contre-jour, eclats quand l'un d'eux renvoie le Soleil vers l'observateur), traces fines des orbites, couronne.
 * EN FONCTIONNEMENT : filaments d'energie dores entre capteurs voisins, couronne qui pulse, orbites qui s'allument.
 * Dessine autour de la direction du Soleil (repere du sol), a 100 unites ; seulement si le Soleil est dans le ciel de la scene.
 */
public final class SwarmModel {

    public static final double SCALE = 1;
    static final double DIST = 100;
    private static final double[] RADII = {10, 14, 19};
    private static final double[] TILTS = {0.35, -0.6, 1.1};
    private static final int[] COUNTS = {30, 40, 52};

    private SwarmModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double t = v.ticks, act = v.activity;
        v.planetKm = 6371;
        //centre : le Soleil ; repere e1, e2 perpendiculaire a sa direction
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
        double day = Math.max(0, Math.min(1, (v.ly + 0.15) * 4));
        if (day <= 0) {
            return;
        }
        light(b, cx, cy, cz, 26 + 4 * Math.sin(t * 0.03) * act, 1, 0.85, 0.55, (0.06 + 0.12 * act) * day);
        for (int o = 0; o < RADII.length; o++) {
            double r = RADII[o], tilt = TILTS[o], ct = Math.cos(tilt), st = Math.sin(tilt);
            //axes de l'orbite : e1 et (e2 incline vers le Soleil) : on la voit en ellipse
            double bx = e2x * ct + lx * st, by = e2y * ct + ly * st, bz = e2z * ct + lz * st;
            ring(b, cx, cy, cz, e1x * r, 0, e1z * r, bx * r, by * r, bz * r, 0.12, 64, 1, 0.82, 0.5, (0.05 + 0.25 * act) * day);
            int n = COUNTS[o];
            double rot = t * (0.0006 + 0.0004 * o) * (o % 2 == 0 ? 1 : -1) * (1 + act);
            for (int k = 0; k < n; k++) {
                double a = rot + 2 * Math.PI * k / n, ca = Math.cos(a), sa = Math.sin(a);
                double px = cx + (e1x * ca + bx * sa) * r, py = cy + by * sa * r, pz = cz + (e1z * ca + bz * sa) * r;
                //capteur : panneau tourne vers le Soleil (on voit son dos sombre), qui oscille un peu
                double wob = 0.3 * Math.sin(t * 0.002 + k * 1.3 + o);
                double nx = -lx + e1x * wob, ny = -ly, nz = -lz + e1z * wob;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                double s = 0.55 + 0.25 * hash(o, k);
                panel(b, v, 1e7, px, py, pz, e1x * s, 0, e1z * s, bx * s, by * s, bz * s, nx, ny, nz, 0.2, 0.18, 0.15, 0.5);
                double glint = Math.pow(Math.max(0, Math.cos(t * 0.004 + k * 2.7 + o * 1.9)), 60);
                if (glint > 0.02) {
                    light(b, px, py, pz, 1.5 + 2 * glint, 1, 0.95, 0.8, glint * day);
                }
                if (act > 0.02 && k % 2 == 0) {
                    double a2 = rot + 2 * Math.PI * (k + 2) / n, c2 = Math.cos(a2), s2 = Math.sin(a2);
                    double qx = cx + (e1x * c2 + bx * s2) * r, qy = cy + by * s2 * r, qz = cz + (e1z * c2 + bz * s2) * r;
                    double flick = 0.5 + 0.5 * Math.sin(t * 0.21 + k);
                    ray(b, px, py, pz, qx, qy, qz, 0.18, 1, 0.8, 0.4, act * 0.4 * flick * day);
                }
            }
        }
    }
}
