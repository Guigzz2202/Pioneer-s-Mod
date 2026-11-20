package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cone;
import static fr.pioneers.space.sky.client.mega.MegaKit.cylinderY;
import static fr.pioneers.space.sky.client.mega.MegaKit.ellipsoid;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;

/**
 * 1.0.7 (« ciel grandiose ») : la CITE DES NUAGES de Jupiter, vue de Jupiter : trois plates-formes empilees qui flottent au ras des nuages,
 * tours et fleche centrale, coupoles, quatre grands ballons tenus par leurs cables, entonnoirs de captage sous la cite, dirigeables qui
 * tournent autour. La nuit, une ville illuminee.
 * EN FONCTIONNEMENT : entonnoirs qui aspirent (lueur orange, colonnes de gaz qui montent), plus de dirigeables, baies toutes allumees.
 * Reglages SKY : (azimut, hauteur en degres, ...).
 */
public final class CloudCityModel {

    public static final double SCALE = 0.95;
    static final double DIST = 72;

    private CloudCityModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double azr = Math.toRadians(p[0]);
        v.planetKm = 69911;
        double fx = Math.sin(azr), fz = -Math.cos(azr), sx = -fz, sz = fx;
        double ax = fx * DIST, az = fz * DIST, base = DIST * Math.tan(Math.toRadians(p[1])) + 12;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double win = 0.1 + 0.85 * night + 0.25 * act;
        //plates-formes
        cylinderY(b, v, 1, ax, base - 4, az, 22, 3, 16, cr * 0.55, cg * 0.5, cb * 0.45, 0.2);
        cylinderY(b, v, 1, ax, base - 1, az, 15, 2.5, 14, mr * 0.75, mg * 0.72, mb * 0.68, 0.3);
        cylinderY(b, v, 1, ax, base + 1.5, az, 9, 2, 12, mr * 0.85, mg * 0.82, mb * 0.78, 0.3);
        //entonnoirs de captage sous la cite
        for (int k = 0; k < 3; k++) {
            double a = 2 * Math.PI * k / 3 + 0.5, ex = ax + Math.cos(a) * 11, ez = az + Math.sin(a) * 11;
            cylinderY(b, v, 1, ex, base - 12, ez, 3, 8, 8, cr * 0.4, cg * 0.35, cb * 0.3, 0.2);
            light(b, ex, base - 12, ez, 3 + 3 * act, 1, 0.55, 0.2, 0.15 + 0.5 * act);
            if (act > 0.02) {
                cone(b, ex, base - 30, ez, ex, base - 12, ez, 6, 1.5, 1, 0.6, 0.3, 0, act * 0.35, 5);
            }
        }
        //tours sur l'anneau, baies
        for (int k = 0; k < 10; k++) {
            double a = 2 * Math.PI * k / 10 + 0.2, r = 17;
            double h = 4 + 6 * hash(k, 1);
            double tx = ax + Math.cos(a) * r, tz = az + Math.sin(a) * r;
            box(b, v, 1, tx, base - 1 + h / 2, tz, 1.4, 0, 0, 0, h / 2, 0, 0, 0, 1.4, mr * 0.8, mg * 0.78, mb * 0.74, 0.35);
            if (Math.cos(a) * tx + Math.sin(a) * tz < 0) {
                for (int w = 0; w < (int) h; w += 2) {
                    light(b, tx + Math.cos(a) * 1.5, base + w, tz + Math.sin(a) * 1.5, 0.45, 1, 0.8, 0.5, win * (0.5 + 0.5 * hash(k, w)));
                }
            }
        }
        for (int k = 0; k < 36; k++) {
            double a = 2 * Math.PI * k / 36, ox = Math.cos(a) * 22.2, oz = Math.sin(a) * 22.2;
            if (Math.cos(a) * (ax + ox) + Math.sin(a) * (az + oz) < 0 && hash(k, 5) > 0.25 - 0.2 * act) {
                light(b, ax + ox, base - 2.5, az + oz, 0.55, 1, 0.78, 0.45, win);
            }
        }
        //fleche centrale et coupoles
        box(b, v, 1, ax, base + 14, az, 2, 0, 0, 0, 11, 0, 0, 0, 2, mr * 0.9, mg * 0.88, mb * 0.85, 0.4);
        light(b, ax, base + 26, az, 1.8, 1, 1, 1, blink(t, 60, 5, 0));
        ellipsoid(b, v, 1, ax, base + 3.5, az, 7, 4.5, 7, 6, 14, 0.45, 0.62, 0.7, 1.4);
        for (int k = 0; k < 3; k++) {
            double a = 2 * Math.PI * k / 3 + 1.2;
            ellipsoid(b, v, 1, ax + Math.cos(a) * 11, base + 1.5, az + Math.sin(a) * 11, 3, 2.2, 3, 5, 10, 0.5, 0.66, 0.72, 1.2);
        }
        //ballons et cables
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2, bx = ax + Math.cos(a) * 15, bz = az + Math.sin(a) * 15, by = base + 22;
            ellipsoid(b, v, 1, bx, by, bz, 8, 4.5, 8, 7, 16, mr * 0.95, mg * 0.9, mb * 0.82, 0.5);
            beam(b, v, 1, bx, by - 4.3, bz, ax + Math.cos(a) * 20, base - 1, az + Math.sin(a) * 20, 0.18, 0.35, 0.33, 0.3, 0.2);
            beam(b, v, 1, bx, by - 4.3, bz, ax + Math.cos(a) * 9, base + 2, az + Math.sin(a) * 9, 0.18, 0.35, 0.33, 0.3, 0.2);
            light(b, bx, by - 4.6, bz, 0.8, 1, 0.2, 0.15, blink(t, 90, 15, k * 22) * (0.3 + 0.6 * night));
        }
        //dirigeables autour de la cite
        int ships = 3 + (int) Math.round(4 * act);
        for (int k = 0; k < ships; k++) {
            double radius = 32 + 6 * (k % 3), dir = k % 2 == 0 ? 1 : -1;
            double a = t * (0.0012 + 0.0012 * act) * dir + k * 1.7;
            double px = ax + Math.cos(a) * radius, pz = az + Math.sin(a) * radius, py = base + 6 + (k % 4) * 4;
            double vx = -Math.sin(a) * dir, vz = Math.cos(a) * dir;
            ellipsoid(b, v, 1, px, py, pz, 3.2 * Math.abs(vx) + 1.1, 1.1, 3.2 * Math.abs(vz) + 1.1, 4, 8, mr * 0.9, mg * 0.86, mb * 0.8, 0.5);
            light(b, px - vx * 3.6, py, pz - vz * 3.6, 0.9 + 0.5 * act, 1, 0.7, 0.35, 0.5 + 0.3 * act);
            light(b, px, py - 1.3, pz, 0.45, 1, 0.9, 0.7, 0.3 + 0.6 * night);
            if (act > 0.3 && k < 3) {
                ray(b, px, py - 1.2, pz, px, base - 2, pz, 0.25, 1, 0.75, 0.4, act * 0.25);
            }
        }
    }
}
