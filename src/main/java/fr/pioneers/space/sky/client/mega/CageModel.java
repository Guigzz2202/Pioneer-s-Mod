package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;

/**
 * 1.0.7 (lot 11) : la CAGE DU PULSAR, vue de Poltergeist (ciel des planetes de Lich) : une sphere-cage de meridiens et de paralleles autour
 * du pulsar, ouverte aux poles de son axe magnetique (meme axe et meme rotation que le pulsar de {@link PhenomenonModels} : les faisceaux
 * sortent par les ouvertures), des bobines de confinement sur l'equateur et, sur le cercle que balaie chaque faisceau, une couronne d'aubes
 * tenues par des bras. Le faisceau frappe chaque aube a son passage (eclair) ; EN FONCTIONNEMENT les aubes gardent l'energie (arcs vers les
 * bobines), les coutures de la cage battent au rythme des impulsions et un faisceau descend vers Poltergeist. Les pieces derriere le pulsar
 * sont dessinees en traits de lumiere (pas de faces pleines devant l'etoile qu'elles cachent). Reglages (T:au_dela/megastructures.py, sorte
 * « cage ») : rayon de la cage en degres, meridiens, aubes par cone, eclat des aubes frappees, scintillement, eclat de nuit.
 */
public final class CageModel {

    public static final double SCALE = 1;
    static final double DIST = PhenomenonModels.DIST;
    /** Ouverture des poles (latitude au-dela de laquelle la cage s'arrete, radians). */
    private static final double OPEN = Math.toRadians(58);
    private static final int MER_STEPS = 10, PAR_STEPS = 48;

    private CageModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double t = v.ticks, act = v.activity;
        v.planetKm = 9000;
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
        //axe magnetique et base orthogonale : les memes que le pulsar
        double tilt = 0.55, spin = t * 0.09, cone = 0.42;
        double axx = e2x * Math.cos(tilt) + e1x * Math.sin(tilt), axy = e2y * Math.cos(tilt), axz = e2z * Math.cos(tilt) + e1z * Math.sin(tilt);
        double qx = axy * -lz - axz * -ly, qy = axz * -lx - axx * -lz, qz = axx * -ly - axy * -lx, ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
        qx /= ql;
        qy /= ql;
        qz /= ql;
        double rx = qy * axz - qz * axy, ry = qz * axx - qx * axz, rz = qx * axy - qy * axx;
        double radius = DIST * Math.tan(Math.toRadians(p[0]));
        int mer = (int) Math.round(p[1]), vanes = (int) Math.round(p[2]);
        double ar = MegaKit.red(v.accent), ag = MegaKit.green(v.accent), abl = MegaKit.blue(v.accent);
        double glowNight = 1 + p[5] * v.night;
        //battement des coutures : deux fois par tour (un faisceau de chaque cote)
        double beat = 0.5 + 0.5 * Math.cos(2 * spin);
        double seam = (0.09 + (0.12 + 0.3 * beat) * act) * glowNight;
        //meridiens
        for (int k = 0; k < mer; k++) {
            double lon = 2 * Math.PI * k / mer, cl = Math.cos(lon), sl = Math.sin(lon);
            double mx = qx * cl + rx * sl, my = qy * cl + ry * sl, mz = qz * cl + rz * sl;
            double px = 0, py = 0, pz = 0;
            for (int j = 0; j <= MER_STEPS; j++) {
                double lat = -OPEN + 2 * OPEN * j / MER_STEPS, ct = Math.cos(lat), st = Math.sin(lat);
                double x = cx + (axx * st + mx * ct) * radius, y = cy + (axy * st + my * ct) * radius, z = cz + (axz * st + mz * ct) * radius;
                if (j > 0) {
                    girder(b, v, px, py, pz, x, y, z, 0.2, ar, ag, abl, seam);
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        //paralleles (l'equateur plus epais)
        for (int k = -2; k <= 2; k++) {
            double lat = OPEN * k / 2.3, ct = Math.cos(lat), st = Math.sin(lat);
            double px = 0, py = 0, pz = 0;
            for (int j = 0; j <= PAR_STEPS; j++) {
                double lon = 2 * Math.PI * j / PAR_STEPS, cl = Math.cos(lon), sl = Math.sin(lon);
                double x = cx + (axx * st + (qx * cl + rx * sl) * ct) * radius, y = cy + (axy * st + (qy * cl + ry * sl) * ct) * radius;
                double z = cz + (axz * st + (qz * cl + rz * sl) * ct) * radius;
                if (j > 0) {
                    girder(b, v, px, py, pz, x, y, z, k == 0 ? 0.3 : 0.15, ar, ag, abl, seam * (k == 0 ? 1.4 : 0.8));
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        //bobines de confinement sur l'equateur (entre deux meridiens)
        for (int k = 0; k < mer; k++) {
            double lon = 2 * Math.PI * (k + 0.5) / mer, cl = Math.cos(lon), sl = Math.sin(lon);
            double ux = qx * cl + rx * sl, uy = qy * cl + ry * sl, uz = qz * cl + rz * sl;
            double tx = -qx * sl + rx * cl, ty = -qy * sl + ry * cl, tz = -qz * sl + rz * cl;
            double x = cx + ux * radius, y = cy + uy * radius, z = cz + uz * radius;
            double s = radius * 0.07;
            if (front(x, y, z, lx, ly, lz)) {
                box(b, v, 1e7, x, y, z, tx * s, ty * s, tz * s, axx * s * 1.6, axy * s * 1.6, axz * s * 1.6, ux * s, uy * s, uz * s, 0.55, 0.36,
                      0.26, 0.5);
            }
            double tw = 0.7 + 0.3 * Math.sin(t * 0.06 + k * 2.1) * p[4];
            light(b, x + ux * s * 1.2, y + uy * s * 1.2, z + uz * s * 1.2, 0.5 + 0.7 * act, ar, ag, abl, (0.25 + 0.5 * act) * tw * glowNight);
        }
        //couronnes d'aubes sur le cercle balaye par chaque faisceau
        double reach = radius * 1.55, cc = Math.cos(cone), sc = Math.sin(cone);
        for (int k = 0; k < vanes; k++) {
            double phi = 2 * Math.PI * k / vanes, cp = Math.cos(phi), sp = Math.sin(phi);
            double dx = axx * cc + (qx * cp + rx * sp) * sc, dy = axy * cc + (qy * cp + ry * sp) * sc, dz = axz * cc + (qz * cp + rz * sp) * sc;
            double tx = -qx * sp + rx * cp, ty = -qy * sp + ry * cp, tz = -qz * sp + rz * cp;
            //second axe de l'aube : d x t
            double wx = dy * tz - dz * ty, wy = dz * tx - dx * tz, wz = dx * ty - dy * tx;
            double hit = Math.pow(Math.max(0, Math.cos(spin - phi)), 60);
            for (int s = -1; s <= 1; s += 2) {
                double vx = cx + s * dx * reach, vy = cy + s * dy * reach, vz = cz + s * dz * reach;
                double bx = cx + s * dx * radius * 1.04, by = cy + s * dy * radius * 1.04, bz = cz + s * dz * radius * 1.04;
                girder(b, v, bx, by, bz, vx, vy, vz, 0.12, ar, ag, abl, seam * 0.6);
                double la = radius * 0.16, lb = radius * 0.07;
                if (front(vx, vy, vz, lx, ly, lz)) {
                    panel(b, v, 1e7, vx, vy, vz, tx * la, ty * la, tz * la, wx * lb, wy * lb, wz * lb, -s * dx, -s * dy, -s * dz, 0.3, 0.34,
                          0.42, 0.7);
                } else {
                    ray(b, vx - tx * la, vy - ty * la, vz - tz * la, vx + tx * la, vy + ty * la, vz + tz * la, lb, 0.4, 0.55, 0.8, 0.05);
                }
                double tw = 0.6 + 0.4 * Math.sin(t * 0.05 + k * 1.3 + s) * p[4];
                double flash = hit * p[3];
                light(b, vx, vy, vz, 0.6 + 2.6 * flash, 0.8, 0.9, 1, (0.22 + 0.25 * act) * tw * glowNight + flash * (0.5 + 0.5 * act));
                if (act > 0.02 && flash > 0.05) {
                    //l'energie gardee : un arc vers la bobine la plus proche de l'equateur
                    double ex = cx + (qx * cp + rx * sp) * radius, ey = cy + (qy * cp + ry * sp) * radius, ez = cz + (qz * cp + rz * sp) * radius;
                    ray(b, vx, vy, vz, ex, ey, ez, 0.14, ar, ag, abl, flash * act * 0.7);
                }
            }
        }
        if (act > 0.02) {
            //faisceau vers Poltergeist : du bas de la cage jusqu'a l'horizon, parcouru d'impulsions au rythme du pulsar
            double gx = cx - e2x * radius, gy = cy - e2y * radius, gz = cz - e2z * radius;
            double hx = gx * 0.55, hz = gz * 0.55, hy = -3;
            ray(b, gx, gy, gz, hx, hy, hz, 0.3, ar, ag, abl, (0.12 + 0.18 * beat) * act);
            for (int k = 0; k < 5; k++) {
                double u = frac(t * 0.015 + k * 0.2 + hash(k, 3) * 0.05);
                light(b, gx + (hx - gx) * u, gy + (hy - gy) * u, gz + (hz - gz) * u, 1.0, 0.8, 0.92, 1, 0.55 * act * (1 - u));
            }
        }
    }

    /** Vrai si le point est devant le centre (cote de l'observateur) : piece pleine ; sinon trait de lumiere. */
    static boolean front(double x, double y, double z, double lx, double ly, double lz) {
        return x * lx + y * ly + z * lz < DIST;
    }

    /** Poutre de la cage : pleine (sombre, couture lumineuse) devant le pulsar, trait de lumiere derriere. */
    private static void girder(MegaBuffer b, MegaView v, double x0, double y0, double z0, double x1, double y1, double z1, double hw, double ar,
          double ag, double abl, double seam) {
        double lx = v.lx, ly = v.ly, lz = v.lz;
        if (front((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, lx, ly, lz)) {
            beam(b, v, 1e7, x0, y0, z0, x1, y1, z1, hw, 0.32, 0.34, 0.42, 0.5);
            ray(b, x0, y0, z0, x1, y1, z1, hw * 0.35, ar, ag, abl, seam);
        } else {
            ray(b, x0, y0, z0, x1, y1, z1, hw * 0.5, ar, ag, abl, seam * 0.8 + 0.04);
        }
    }
}
