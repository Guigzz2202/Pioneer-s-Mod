package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ring;

/**
 * 1.0.7 (lot 11) : la COURONNE DE PROXIMA, vue de Proxima b (ciel des exoplanetes) : un anneau de collecteurs magnetiques autour de la
 * naine rouge, incline (on le voit en ellipse), ses collecteurs en pointe vers l'exterieur (dos sombres en contre-jour, pointes qui
 * rougeoient), des arcs magnetiques entre collecteurs voisins. Proxima erupte d'elle-meme : sans la Couronne en marche, ses eruptions
 * partent en panaches qui se perdent. EN FONCTIONNEMENT : les eruptions sont happees, courbees vers un collecteur qui s'embrase, l'anneau
 * s'allume et un faisceau descend vers Proxima b. Dessinee autour de l'etoile (direction de la lumiere du ciel), a 85 unites.
 * Reglages (T:au_dela/megastructures.py, sorte « crown ») : rayon en degres, aplatissement, collecteurs, eclat des eruptions,
 * scintillement, eclat de nuit.
 */
public final class CrownModel {

    public static final double SCALE = 1;
    static final double DIST = 85;
    /** Rayon apparent de Proxima (unites a 85) : celui de son disque dans le ciel de Proxima b. */
    private static final double STAR = 4.8;
    private static final int RAIL = 56;

    private CrownModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double t = v.ticks, act = v.activity;
        v.planetKm = 7160;
        double lx = v.lx, ly = v.ly, lz = v.lz;
        double cx = lx * DIST, cy = ly * DIST, cz = lz * DIST;
        //repere : e1 horizontal perpendiculaire a la visee, e2 = l x e1 (vers le haut)
        double e1x = -lz, e1z = lx, e1l = Math.sqrt(e1x * e1x + e1z * e1z);
        if (e1l < 1e-6) {
            e1x = 1;
            e1z = 0;
            e1l = 1;
        }
        e1x /= e1l;
        e1z /= e1l;
        double e2x = ly * e1z, e2y = lz * e1x - lx * e1z, e2z = -ly * e1x;
        double radius = DIST * Math.tan(Math.toRadians(p[0])), flat = p[1], st = Math.sqrt(1 - flat * flat);
        //axes de l'anneau : A horizontal, B incline vers l'observateur (l'anneau passe devant puis derriere l'etoile)
        double ax = e1x * radius, ay = 0, az = e1z * radius;
        double bx = (e2x * flat - lx * st) * radius, by = (e2y * flat - ly * st) * radius, bz = (e2z * flat - lz * st) * radius;
        //normale du plan de l'anneau (A x B normalisee)
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double ar = MegaKit.red(v.accent), ag = MegaKit.green(v.accent), abl = MegaKit.blue(v.accent);
        //couronne de l'etoile : plus vive quand l'anneau la tient
        light(b, cx, cy, cz, STAR * 2.6 + Math.sin(t * 0.02) * act, 1, 0.42, 0.28, 0.07 + 0.1 * act);
        //rail : poutres sombres le long de l'ellipse ; lisere allume en marche
        for (int k = 0; k < RAIL; k++) {
            double a0 = 2 * Math.PI * k / RAIL, a1 = 2 * Math.PI * (k + 1) / RAIL;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            beam(b, v, 1e7, cx + ax * c0 + bx * s0, cy + ay * c0 + by * s0, cz + az * c0 + bz * s0, cx + ax * c1 + bx * s1,
                  cy + ay * c1 + by * s1, cz + az * c1 + bz * s1, 0.42, 0.3, 0.24, 0.26, 0.4);
        }
        ring(b, cx, cy, cz, ax, ay, az, bx, by, bz, 0.16, RAIL, ar, ag, abl, 0.05 + 0.4 * act);
        //anneau interieur de guidage du champ : fil lumineux qui pulse
        double pulse = 0.5 + 0.5 * Math.sin(t * 0.04);
        ring(b, cx, cy, cz, ax * 0.62, ay * 0.62, az * 0.62, bx * 0.62, by * 0.62, bz * 0.62, 0.1, 48, 1, 0.5, 0.35,
              (0.04 + 0.22 * act) * (0.6 + 0.4 * pulse));
        int n = (int) Math.round(p[2]);
        //eruption en cours : une toutes les 90 ticks, vers un collecteur tire au sort
        double period = 90, phase = frac(t / period);
        int ev = (int) Math.floor(t / period), target = (int) Math.floor(hash(ev, 7) * n);
        double flare = Math.sin(Math.min(1, phase * 2.2) * Math.PI) * p[3];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * (k + 0.5) / n, ca = Math.cos(a), sa = Math.sin(a);
            //direction radiale dans le plan de l'anneau (non unitaire : suit l'ellipse)
            double rx = ax * ca + bx * sa, ry = ay * ca + by * sa, rz = az * ca + bz * sa, rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double ux = rx / rl, uy = ry / rl, uz = rz / rl;
            //tangente = n x u
            double tx = ny * uz - nz * uy, ty = nz * ux - nx * uz, tz = nx * uy - ny * ux;
            double px = cx + rx, py = cy + ry, pz = cz + rz;
            double len = radius * (0.28 + 0.08 * hash(k, 3)), w = radius * 0.05;
            double qx = px + ux * len, qy = py + uy * len, qz = pz + uz * len;
            //collecteur : lame en pointe, face tournee vers l'etoile (on voit son dos)
            panel(b, v, 1e7, px + ux * len * 0.5, py + uy * len * 0.5, pz + uz * len * 0.5, ux * len * 0.5, uy * len * 0.5, uz * len * 0.5,
                  tx * w, ty * w, tz * w, nx, ny, nz, 0.24, 0.2, 0.2, 0.55);
            beam(b, v, 1e7, px, py, pz, qx, qy, qz, 0.2, 0.35, 0.3, 0.3, 0.3);
            double charge = k == target ? flare * act : 0;
            double tw = 0.6 + 0.4 * Math.sin(t * 0.05 + k * 1.7) * p[4];
            light(b, qx, qy, qz, 0.9 + 2.8 * charge, ar, ag, abl, (0.22 + 0.3 * act) * tw + charge);
            //arc magnetique vers le collecteur voisin, bombe vers l'exterieur
            double a2 = 2 * Math.PI * (k + 1.5) / n, c2 = Math.cos(a2), s2 = Math.sin(a2);
            double r2x = ax * c2 + bx * s2, r2y = ay * c2 + by * s2, r2z = az * c2 + bz * s2;
            double hx = cx + (rx + r2x) * 0.5 * 1.32, hy = cy + (ry + r2y) * 0.5 * 1.32, hz = cz + (rz + r2z) * 0.5 * 1.32;
            double arc = (0.05 + 0.12 * act) * (0.5 + 0.5 * Math.sin(t * 0.03 + k));
            ray(b, qx, qy, qz, hx, hy, hz, 0.12, 1, 0.45, 0.3, arc);
            ray(b, hx, hy, hz, cx + r2x * (1 + len / rl), cy + r2y * (1 + len / rl), cz + r2z * (1 + len / rl), 0.12, 1, 0.45, 0.3, arc);
            if (k == target) {
                flare(b, cx, cy, cz, ux, uy, uz, tx, ty, tz, qx, qy, qz, phase, flare, act, rl);
            }
        }
        if (act > 0.02) {
            //faisceau vers Proxima b : du bas de l'anneau jusqu'a l'horizon, parcouru d'impulsions
            double gx = cx - bx * 0.98, gy = cy - by * 0.98, gz = cz - bz * 0.98;
            double hx = gx * 0.55, hz = gz * 0.55, hy = -3;
            ray(b, gx, gy, gz, hx, hy, hz, 0.35, 1, 0.55, 0.4, 0.3 * act);
            for (int k = 0; k < 4; k++) {
                double u = frac(t * 0.012 + k * 0.25);
                light(b, gx + (hx - gx) * u, gy + (hy - gy) * u, gz + (hz - gz) * u, 1.1, 1, 0.75, 0.55, 0.6 * act * (1 - u));
            }
        }
    }

    /**
     * Une eruption : du bord de l'etoile, un panache qui monte. Happe (en marche) : il se courbe jusqu'a la pointe du collecteur ;
     * libre (a l'arret) : il part droit vers l'exterieur et se dissipe.
     */
    private static void flare(MegaBuffer b, double cx, double cy, double cz, double ux, double uy, double uz, double tx, double ty, double tz,
          double qx, double qy, double qz, double phase, double flare, double act, double rl) {
        if (flare <= 0.01) {
            return;
        }
        double sx = cx + ux * STAR, sy = cy + uy * STAR, sz = cz + uz * STAR;
        //point libre : droit vers l'exterieur, decale le long de la tangente
        double fx = cx + ux * rl * 1.5 + tx * rl * 0.15, fy = cy + uy * rl * 1.5 + ty * rl * 0.15, fz = cz + uz * rl * 1.5 + tz * rl * 0.15;
        double ex = fx + (qx - fx) * act, ey = fy + (qy - fy) * act, ez = fz + (qz - fz) * act;
        //controle de la courbe : bombe le long de la tangente
        double kx = (sx + ex) / 2 + tx * rl * 0.25, ky = (sy + ey) / 2 + ty * rl * 0.25, kz = (sz + ez) / 2 + tz * rl * 0.25;
        double reach = Math.min(1, phase * 2.4);
        double px = sx, py = sy, pz = sz;
        for (int k = 1; k <= 10; k++) {
            double u = reach * k / 10.0, iu = 1 - u;
            double x = iu * iu * sx + 2 * iu * u * kx + u * u * ex, y = iu * iu * sy + 2 * iu * u * ky + u * u * ey;
            double z = iu * iu * sz + 2 * iu * u * kz + u * u * ez;
            double fade = 1 - (1 - act) * u * 0.8;
            ray(b, px, py, pz, x, y, z, 0.35 + 0.25 * (1 - u), 1, 0.62, 0.38, flare * 0.55 * fade);
            px = x;
            py = y;
            pz = z;
        }
        light(b, px, py, pz, 1.6, 1, 0.8, 0.6, flare * (0.3 + 0.6 * act));
    }
}
