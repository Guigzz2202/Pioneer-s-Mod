package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.ellipsoid;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;

/**
 * 1.0.7 (« ciel grandiose ») : la BOUCLE d'Uranus, vue d'Uranus : un anneau magnetique colossal dresse dans le ciel, incline, fait de
 * bobines (caissons alternes, joints lumineux) autour d'un emetteur central, et l'AURORE qui en pend en rideaux ondulants.
 * EN FONCTIONNEMENT : bobines allumees, impulsions de plasma qui font le tour de la boucle, aurore plus haute, plus vive, qui danse.
 * Reglages SKY : (azimut, hauteur du centre en degres, ...).
 */
public final class LoopModel {

    public static final double SCALE = 1;
    static final double DIST = 92, RADIUS = 30, COILS = 56;

    private LoopModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double az = Math.toRadians(p[0]), el = Math.toRadians(p[1]);
        v.planetKm = 25362;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double dx = Math.cos(el) * Math.sin(az), dy = Math.sin(el), dz = -Math.cos(el) * Math.cos(az);
        double cx = dx * DIST, cy = dy * DIST, cz = dz * DIST;
        //plan de la boucle : e1 horizontal (perpendiculaire a la visee), e2 = visee inclinee de 55 deg (on la voit en ellipse ouverte)
        double e1x = Math.cos(az), e1z = Math.sin(az);
        double ux = -Math.sin(el) * Math.sin(az), uy = Math.cos(el), uz = Math.sin(el) * Math.cos(az);   //haut de l'image
        double tilt = Math.toRadians(55);
        double e2x = ux * Math.cos(tilt) + dx * Math.sin(tilt), e2y = uy * Math.cos(tilt) + dy * Math.sin(tilt), e2z = uz * Math.cos(tilt) + dz * Math.sin(tilt);
        double nx = -e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y;                                  //normale du plan (e1 x e2)
        int n = (int) COILS;
        double hs = Math.PI * RADIUS / n * 1.02;
        double pulse = t * (0.003 + 0.012 * act);
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, ca = Math.cos(a), sa = Math.sin(a);
            double ox = e1x * ca + e2x * sa, oy = e2y * sa, oz = e1z * ca + e2z * sa;
            double gx = -e1x * sa + e2x * ca, gy = e2y * ca, gz = -e1z * sa + e2z * ca;
            double px = cx + ox * RADIUS, py = cy + oy * RADIUS, pz = cz + oz * RADIUS;
            boolean coil = k % 2 == 0;
            double fat = coil ? 1.7 : 1.1;
            box(b, v, 50000, px, py, pz, gx * hs, gy * hs, gz * hs, ox * fat, oy * fat, oz * fat, nx * fat, ny * fat, nz * fat,
                  coil ? cr * 0.7 : mr * 0.45, coil ? cg * 0.7 : mg * 0.45, coil ? cb * 0.7 : mb * 0.5, 0.6);
            double chase = Math.pow(Math.max(0, Math.cos(a * 3 - pulse * 6)), 10);
            light(b, px - dx * 2, py - dy * 2, pz - dz * 2, coil ? 1.1 : 0.7, 0.55, 1, 0.85,
                  (coil ? 0.12 + 0.25 * night : 0.06) + act * (0.35 + 0.65 * chase));
            //aurore : rideaux qui pendent sous la moitie basse de la boucle
            if (oy < 0.25 && k % 2 == 1) {
                double wave = 0.5 + 0.5 * Math.sin(t * (0.01 + 0.03 * act) + k * 0.7) * Math.sin(t * 0.006 + k * 0.23);
                double len = (10 + 14 * wave) * (1 + 0.8 * act);
                double drift = Math.sin(t * 0.008 + k) * 3;
                double ga = (0.06 + 0.12 * night) * (0.4 + 0.6 * wave) * p[4] + act * 0.18 * wave;
                ray(b, px, py, pz, px + e1x * drift, py - len, pz + e1z * drift, 1.6 + 1.2 * wave, 0.35, 1, 0.7, ga);
                ray(b, px, py - len * 0.5, pz, px + e1x * drift, py - len * 1.4, pz + e1z * drift, 1.2, 0.6, 0.45, 1, ga * 0.6);
            }
        }
        //emetteur central et ses bras
        ellipsoid(b, v, 50000, cx, cy, cz, 3, 3, 3, 6, 12, mr * 0.6, mg * 0.65, mb * 0.7, 1.0);
        light(b, cx - dx * 3.5, cy - dy * 3.5, cz - dz * 3.5, 5 + 3 * act + Math.sin(t * 0.1), 0.5, 1, 0.85, 0.2 + 0.5 * act);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2, ca = Math.cos(a), sa = Math.sin(a);
            double ox = e1x * ca + e2x * sa, oy = e2y * sa, oz = e1z * ca + e2z * sa;
            MegaKit.beam(b, v, 50000, cx + ox * 3, cy + oy * 3, cz + oz * 3, cx + ox * (RADIUS - 1.5), cy + oy * (RADIUS - 1.5),
                  cz + oz * (RADIUS - 1.5), 0.35, mr * 0.5, mg * 0.52, mb * 0.55, 0.3);
            if (act > 0.02) {
                ray(b, cx, cy, cz, cx + ox * RADIUS, cy + oy * RADIUS, cz + oz * RADIUS, 0.5, 0.55, 1, 0.85,
                      act * 0.3 * (0.5 + 0.5 * Math.sin(t * 0.2 + k)));
            }
        }
        light(b, cx + e2x * (RADIUS + 2), cy + e2y * (RADIUS + 2), cz + e2z * (RADIUS + 2), 1.2, 1, 0.25, 0.2,
              blink(t, 70, 8, 0) * (0.4 + 0.6 * night) * (0.5 + 0.5 * hash(1, 1)));
    }
}
