package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.ellipsoid;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (« ciel grandiose ») : l'OBSERVATOIRE DU HALO, vu de la Terre la nuit, au point oppose au Soleil (L2) : un reseau de 7 x 7
 * satellites detecteurs (caisson, ailes, feu) relies par des lasers, autour de la station centrale qui tourne lentement. Des DETECTIONS
 * (le passage de la matiere noire) parcourent le reseau en ondes de lumiere.
 * EN FONCTIONNEMENT : lasers vifs, detections bien plus frequentes, anneau de la station allume.
 * Reglages SKY : (lignes, colonnes, pas en degres, ...).
 */
public final class HaloModel {

    public static final double SCALE = 1;
    static final double DIST = 100;
    static final int N = 7;
    static final double STEP = 6;

    private HaloModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double t = v.ticks, act = v.activity, night = v.night;
        v.planetKm = 6371;
        if (night <= 0.02) {
            return;
        }
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        //centre : oppose au Soleil, un peu au nord
        double dx = -v.lx, dy = -v.ly, dz = -v.lz - 0.25, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl;
        dy /= dl;
        dz /= dl;
        if (dy < -0.05) {
            return;
        }
        double cx = dx * DIST, cy = dy * DIST, cz = dz * DIST;
        double e1x = -dz, e1z = dx, e1l = Math.sqrt(e1x * e1x + e1z * e1z);
        e1x /= e1l;
        e1z /= e1l;
        double e2x = dy * e1z, e2y = dz * e1x - dx * e1z, e2z = -dy * e1x;
        double rot = t * 0.00015;
        double cr0 = Math.cos(rot), sr0 = Math.sin(rot);
        double ux = e1x * cr0 + e2x * sr0, uy = e2y * sr0, uz = e1z * cr0 + e2z * sr0;
        double wx = -e1x * sr0 + e2x * cr0, wy = e2y * cr0, wz = -e1z * sr0 + e2z * cr0;
        //detection en cours : une onde qui part d'un noeud
        double period = 160 - 110 * act;
        int seed = (int) Math.floor(t / period);
        double age = frac(t / period) * period;
        int oi = (int) (hash(seed, 1) * N), oj = (int) (hash(seed, 2) * N);
        double laser = (0.04 + 0.2 * act) * night;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double gi = (i - (N - 1) / 2.0) * STEP, gj = (j - (N - 1) / 2.0) * STEP;
                double px = cx + ux * gi + wx * gj, py = cy + uy * gi + wy * gj, pz = cz + uz * gi + wz * gj;
                if (i == N / 2 && j == N / 2) {
                    continue;
                }
                box(b, v, 1.5e6, px, py, pz, ux * 0.5, uy * 0.5, uz * 0.5, wx * 0.5, wy * 0.5, wz * 0.5, dx * 0.5, dy * 0.5, dz * 0.5, mr * 0.6,
                      mg * 0.58, mb * 0.62, 0.6);
                panel(b, v, 1.5e6, px + ux * 1.3, py + uy * 1.3, pz + uz * 1.3, ux * 0.8, uy * 0.8, uz * 0.8, wx * 0.35, wy * 0.35, wz * 0.35,
                      -dx, -dy, -dz, 0.1, 0.14, 0.35, 0.8);
                double dist = Math.hypot(i - oi, j - oj);
                double wave = Math.max(0, 1 - Math.abs(dist - age * 0.12) * 1.5) * Math.max(0, 1 - age / period);
                light(b, px - dx, py - dy, pz - dz, 0.6 + 1.6 * wave, cr + (1 - cr) * wave, cg + (1 - cg) * wave, cb,
                      (0.25 + 0.5 * blink(t, 90, 10, i * 7 + j * 13) + wave) * night);
                if (i + 1 < N) {
                    ray(b, px, py, pz, px + ux * STEP, py + uy * STEP, pz + uz * STEP, 0.08, cr, cg, cb, laser);
                    if (act > 0.02) {
                        //impulsions qui courent sur les lasers
                        double u = frac(t / 30.0 + hash(i, j));
                        light(b, px + ux * STEP * u, py + uy * STEP * u, pz + uz * STEP * u, 0.45, 1, 1, 1, act * night * 0.8);
                    }
                }
                if (j + 1 < N) {
                    ray(b, px, py, pz, px + wx * STEP, py + wy * STEP, pz + wz * STEP, 0.08, cr, cg, cb, laser);
                }
            }
        }
        //station centrale
        ellipsoid(b, v, 1.5e6, cx, cy, cz, 1.6, 1.6, 1.6, 6, 10, mr * 0.75, mg * 0.72, mb * 0.8, 0.8);
        ring(b, cx, cy, cz, ux * 3, uy * 3, uz * 3, wx * 3, wy * 3, wz * 3, 0.25, 32, cr, cg, cb, (0.15 + 0.5 * act) * night);
        light(b, cx - dx * 2, cy - dy * 2, cz - dz * 2, 2.5 + act * 2, cr, cg, cb, (0.3 + 0.4 * act) * night);
    }
}
