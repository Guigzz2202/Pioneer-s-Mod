package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cone;
import static fr.pioneers.space.sky.client.mega.MegaKit.cylinderY;
import static fr.pioneers.space.sky.client.mega.MegaKit.frac;
import static fr.pioneers.space.sky.client.mega.MegaKit.hash;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ringY;

/**
 * 1.0.7 (« ciel grandiose ») : l'ASCENSEUR DE MARS en 3D, vu de Mars. Il est si haut qu'il faut lever la tete :
 * <ul>
 *     <li>sur l'horizon, l'ARCOLOGIE D'ANCRAGE : quatre gradins couverts de baies eclairees (une ville la nuit), deux tours de lancement,
 *     des contreforts qui montent vers la fleche ou s'accroche le cable, et quatre projecteurs dont les faisceaux balaient la poussiere ;</li>
 *     <li>le CABLE : trois torons entretoises, balises a chaque noeud, un relais a mi-hauteur ; au crepuscule il s'eteint de bas en haut
 *     (ombre de Mars), et il continue au-dela de la station vers le contrepoids ;</li>
 *     <li>la STATION : moyeu, grand anneau habite qui tourne (baies eclairees), anneau interieur qui tourne en sens inverse, bras
 *     d'amarrage, ailes solaires qui suivent le Soleil, navettes qui tournent autour avec leurs trainees.</li>
 * </ul>
 * EN FONCTIONNEMENT (ports actifs) : les cabines se multiplient et filent avec leurs phares et leurs trainees, le courant monte dans le
 * cable par impulsions cyan, les projecteurs se braquent sur les cabines, l'arcologie s'allume, l'anneau tourne plus vite et passe au
 * cyan, plus de navettes.
 * Reglages SKY : (azimut de la base, -, -, hauteur reelle de la station en km, rayon de Mars en km, eclat).
 */
public final class ElevatorModel {

    /** Echelle du modele vers le ciel (les angles ne changent pas ; tout reste en deca de la voute). */
    public static final double SCALE = 0.6;
    /** Distance de la base, pied du cable (sommet de la fleche), centre de la station, bout visible du cable (unites du modele). */
    static final double DIST = 70, FOOT = 34, TOP = 130, END = 172, MID = 82;
    private static final double[] TIERS = {20, 15.5, 11, 7};
    private static final double TIER_H = 4.5, TIER_Y = -2;

    private ElevatorModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double az = Math.toRadians(p[0]);
        v.planetKm = p[4];
        //f : de l'observateur vers la base (horizontal) ; s : de cote
        double fx = Math.sin(az), fz = -Math.cos(az), sx = -fz, sz = fx;
        base(b, v, fx, fz, sx, sz);
        tether(b, v, fx * DIST, fz * DIST, p[3], p[5]);
        station(b, v, fx * DIST, fz * DIST, fx, fz, sx, sz, p[3]);
    }

    /** Hauteur reelle (km) d'un point du cable a la hauteur {@code y} du modele. */
    private static double kmAt(double km, double y) {
        double u = Math.max(0, (y - FOOT) / (TOP - FOOT));
        return 1.5 + km * u * u;
    }

    // ------------------------------------------------------------------ arcologie d'ancrage

    private static void base(MegaBuffer b, MegaView v, double fx, double fz, double sx, double sz) {
        double ax = fx * DIST, az0 = fz * DIST;
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double win = 0.07 + 0.88 * night;
        for (int i = 0; i < TIERS.length; i++) {
            double hs = TIERS[i], y0 = TIER_Y + i * TIER_H;
            double shade = 0.4 + 0.07 * i;
            box(b, v, 0.5, ax, y0 + TIER_H / 2, az0, fx * hs, 0, fz * hs, 0, TIER_H / 2, 0, sx * hs, 0, sz * hs, cr * shade, cg * shade, cb * shade,
                  0.06);
            //corniche claire en haut du gradin
            box(b, v, 0.5, ax - fx * (hs + 0.15), y0 + TIER_H - 0.25, az0 - fz * (hs + 0.15), fx * 0.3, 0, fz * 0.3, 0, 0.25, 0, sx * hs, 0,
                  sz * hs, mr * 0.7, mg * 0.7, mb * 0.7, 0.2);
            //lueur de la ville sur le gradin, la nuit
            light(b, ax - fx * (hs + 0.3), y0 + TIER_H / 2, az0 - fz * (hs + 0.3), hs * 0.75, 1, 0.68, 0.4, 0.07 * night + 0.05 * act);
            //baies de la face avant : deux rangees ; en fonctionnement, presque toutes allumees
            int n = (int) (hs * 2 / 1.2);
            for (int row = 0; row < 2; row++) {
                for (int k = 0; k < n; k++) {
                    double h = hash(i * 7 + row, k);
                    if (h < 0.32 - 0.26 * act) {
                        continue;
                    }
                    double off = -hs + (k + 0.5) * hs * 2 / n;
                    double wx = ax - fx * (hs + 0.1) + sx * off, wz = az0 - fz * (hs + 0.1) + sz * off;
                    boolean blue = h > 0.86 || act > 0.5 && h > 0.6;
                    light(b, wx, y0 + 1.25 + row * 1.7, wz, 0.45, blue ? 0.55 : 1, blue ? 0.85 : 0.78, blue ? 1 : 0.48,
                          win * (0.6 + 0.4 * h) * (0.85 + 0.15 * Math.sin(t * 0.02 + h * 40)));
                }
            }
        }
        double top = TIER_Y + TIERS.length * TIER_H;
        //fleche : fut central, contreforts, collier d'accroche du cable
        box(b, v, 1, ax, (top + FOOT) / 2, az0, fx * 2.6, 0, fz * 2.6, 0, (FOOT - top) / 2, 0, sx * 2.6, 0, sz * 2.6, mr * 0.62, mg * 0.62,
              mb * 0.62, 0.25);
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                beam(b, v, 1, ax + sx * 6 * i + fx * 6 * j, top, az0 + sz * 6 * i + fz * 6 * j, ax + sx * 1.8 * i + fx * 1.8 * j, FOOT - 2,
                      az0 + sz * 1.8 * i + fz * 1.8 * j, 0.55, mr * 0.75, mg * 0.75, mb * 0.75, 0.3);
            }
            //grands arcs-boutants depuis le deuxieme gradin
            beam(b, v, 1, ax - fx * 15 + sx * 15 * i, TIER_Y + TIER_H * 2, az0 - fz * 15 + sz * 15 * i, ax + sx * 2.2 * i - fx * 1.5,
                  FOOT - 7, az0 + sz * 2.2 * i - fz * 1.5, 0.7, cr * 0.6, cg * 0.6, cb * 0.6, 0.15);
        }
        cylinderY(b, v, 2, ax, FOOT - 1.5, az0, 4.2, 3, 18, cr * 0.75, cg * 0.75, cb * 0.75, 0.3);
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            double lx = ax + Math.cos(a) * 4.4, lz = az0 + Math.sin(a) * 4.4;
            if (Math.cos(a) * lx + Math.sin(a) * lz < 0) {
                light(b, lx, FOOT, lz, 0.4, 1, 0.85, 0.6, 0.15 + 0.7 * night);
            }
        }
        //tours de lancement de part et d'autre, feu rouge au sommet
        for (int i = -1; i <= 1; i += 2) {
            double tx = ax + sx * 27 * i + fx * 3, tz = az0 + sz * 27 * i + fz * 3;
            cylinderY(b, v, 0.5, tx, -2, tz, 2.3, 21, 12, mr * 0.55, mg * 0.55, mb * 0.55, 0.15);
            cylinderY(b, v, 0.5, tx, 19, tz, 3.2, 1.2, 12, cr * 0.6, cg * 0.6, cb * 0.6, 0.15);
            light(b, tx, 21, tz, 0.7, 1, 0.15, 0.1, blink(t, 60, 30, i * 15) * (0.45 + 0.55 * night));
            for (int k = 0; k < 4; k++) {
                light(b, tx - fx * 2.4, 3 + k * 4.2, tz - fz * 2.4, 0.3, 1, 0.8, 0.5, win * 0.8);
            }
        }
        //projecteurs : ils balaient la poussiere du ciel ; en fonctionnement, ils suivent la premiere cabine
        double atm = Math.min(1, v.haze * 2.5);
        double target = FOOT + 6 + frac(t * (1 + 3.5 * act) / 3600) * (TOP - FOOT - 22);
        for (int k = 0; k < 4; k++) {
            int side = k % 2 == 0 ? -1 : 1;
            double hs = k < 2 ? TIERS[1] : TIERS[2];
            double y = TIER_Y + TIER_H * (k < 2 ? 2 : 3);
            double px = ax - fx * hs + sx * hs * side, pz = az0 - fz * hs + sz * hs * side;
            double phi = t * 0.0045 + k * 1.7, tilt = 0.38 + 0.16 * Math.sin(t * 0.003 + k);
            double dx = Math.sin(tilt) * (Math.cos(phi) * sx + Math.sin(phi) * fx), dy = Math.cos(tilt),
                  dz = Math.sin(tilt) * (Math.cos(phi) * sz + Math.sin(phi) * fz);
            double gx = ax - px, gy = target - y, gz = az0 - pz;
            double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
            dx = dx * (1 - act) + gx / gl * act;
            dy = dy * (1 - act) + gy / gl * act;
            dz = dz * (1 - act) + gz / gl * act;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double len = act > 0.5 ? Math.min(78, gl) : 78;
            double a = (0.03 + 0.14 * night + 0.06 * act) * atm;
            cone(b, px, y, pz, px + dx / dl * len, y + dy / dl * len, pz + dz / dl * len, 0.35, 4.8, 1, 0.94, 0.82, a, 0, 7);
            light(b, px, y, pz, 0.7, 1, 0.95, 0.85, 0.15 + 0.75 * night);
        }
    }

    // ------------------------------------------------------------------ cable, relais, cabines

    private static void tether(MegaBuffer b, MegaView v, double ax, double az0, double km, double glintGain) {
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double hubLow = TOP - 12;
        int segs = 22;
        double grazing = 1 - Math.abs(v.ly);
        for (int s = 0; s < segs; s++) {
            double y0 = FOOT + (hubLow - FOOT) * s / segs, y1 = FOOT + (hubLow - FOOT) * (s + 1) / segs;
            double kmS = kmAt(km, (y0 + y1) / 2);
            for (int c = 0; c < 3; c++) {
                double a = 2 * Math.PI * c / 3 + 0.4;
                double ox = Math.cos(a) * 1.3, oz = Math.sin(a) * 1.3;
                beam(b, v, kmS, ax + ox, y0, az0 + oz, ax + ox, y1, az0 + oz, 0.3, mr * 0.9, mg * 0.9, mb * 0.9, 0.6);
            }
            //entretoises en triangle et balise a chaque noeud
            for (int c = 0; c < 3; c++) {
                double a0 = 2 * Math.PI * c / 3 + 0.4, a1 = 2 * Math.PI * (c + 1) / 3 + 0.4;
                beam(b, v, kmS, ax + Math.cos(a0) * 1.3, y1, az0 + Math.sin(a0) * 1.3, ax + Math.cos(a1) * 1.3, y1, az0 + Math.sin(a1) * 1.3, 0.14,
                      mr * 0.75, mg * 0.75, mb * 0.75, 0.2);
            }
            //impulsion de lumiere qui monte le long du cable (lente au repos, cyan et rapide en fonctionnement)
            double idle = Math.pow(Math.max(0, Math.cos(2 * Math.PI * (y1 / 95 - t / 260))), 12);
            double fast = Math.pow(Math.max(0, Math.cos(2 * Math.PI * (y1 / 30 - t / 45))), 6);
            light(b, ax, y1, az0, 0.75 + 0.6 * fast * act, 1 - 0.6 * act, 0.25 + 0.65 * act, 0.2 + 0.8 * act,
                  (0.18 + 0.5 * night) * (0.25 + 0.75 * idle) * (1 - act) + act * (0.25 + 0.75 * fast));
            //le cable accroche la lumiere quand le Soleil est bas
            double vis = v.sunVisibility(kmS);
            double glint = glintGain * 0.4 * vis * grazing * grazing;
            if (glint > 0.01) {
                ray(b, ax, y0, az0, ax, y1, az0, 1.9, v.sunR, v.sunG * 0.85, v.sunB * 0.7, glint);
            }
            double flow = act * (0.2 + 0.4 * Math.max(0, Math.sin(y0 * 0.2 - t * 0.35)));
            if (flow > 0.01) {
                ray(b, ax, y0, az0, ax, y1, az0, 1.5, 0.45, 0.9, 1, flow);
            }
        }
        //au-dela de la station : le cable file vers le contrepoids et se perd
        double above = TOP + 14;
        for (int c = 0; c < 3; c++) {
            double a = 2 * Math.PI * c / 3 + 0.4;
            beam(b, v, km, ax + Math.cos(a) * 1.1, above, az0 + Math.sin(a) * 1.1, ax + Math.cos(a) * 0.5, END, az0 + Math.sin(a) * 0.5, 0.24,
                  mr * 0.8, mg * 0.8, mb * 0.8, 0.5);
        }
        cone(b, ax, above, az0, ax, END + 6, az0, 1.6, 0.3, v.sunR, v.sunG * 0.9, v.sunB * 0.8, 0.12 * glintGain * v.sunVisibility(km) + 0.25 * act,
              0, 5);
        for (int k = 0; k < 5; k++) {
            double y = above + 6 + k * 6;
            light(b, ax, y, az0, 0.5, 1, 0.3, 0.2, blink(t, 70, 10, k * 8) * (0.3 + 0.6 * night) * (1 - k * 0.15));
        }

        //relais a mi-hauteur : anneau, rayons, feux
        double kmM = kmAt(km, MID);
        for (int k = 0; k < 16; k++) {
            double a = 2 * Math.PI * k / 16;
            double cx = Math.cos(a), cz = Math.sin(a), half = Math.PI * 6 / 16 * 1.05;
            box(b, v, kmM, ax + cx * 6, MID, az0 + cz * 6, -cz * half, 0, cx * half, cx * 0.8, 0, cz * 0.8, 0, 0.8, 0, mr * 0.85, mg * 0.85,
                  mb * 0.85, 0.35);
            light(b, ax + cx * 6, MID - 0.9, az0 + cz * 6, 0.3, 1, 0.85, 0.55, 0.12 + 0.7 * night);
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + 0.4;
            beam(b, v, kmM, ax, MID, az0, ax + Math.cos(a) * 5.4, MID, az0 + Math.sin(a) * 5.4, 0.25, mr * 0.7, mg * 0.7, mb * 0.7, 0.2);
        }
        if (act > 0.02) {
            ringY(b, ax, MID, az0, 6.8, 0.5, 20, 0.45, 0.9, 1, act * (0.45 + 0.25 * Math.sin(t * 0.15)));
        }

        //cabines : trains de deux caissons, phares dans le sens de la marche, moteur, trainee en fonctionnement
        int n = 2 + (int) Math.round(act * 4);
        double speed = (1 + 3.5 * act) / 3600;
        double run = hubLow - FOOT - 10;
        for (int k = 0; k < n; k++) {
            double u = frac(t * speed + (double) k / n);
            boolean up = k % 2 == 0;
            double y = up ? FOOT + 5 + u * run : FOOT + 5 + (1 - u) * run;
            double dir = up ? 1 : -1;
            double a = 2 * Math.PI * (k % 3) / 3 + 0.4 + Math.PI / 3;
            double ca = Math.cos(a), sa = Math.sin(a);
            double ox = ca * 2.6, oz = sa * 2.6;
            double kmC = kmAt(km, y);
            for (int c = 0; c < 2; c++) {
                double yc = y - dir * c * 3.9;
                box(b, v, kmC, ax + ox, yc, az0 + oz, ca * 1.15, 0, sa * 1.15, 0, 1.8, 0, -sa * 1.15, 0, ca * 1.15, 0.9, 0.9, 0.93, 0.55);
                light(b, ax + ca * 3.85, yc + 0.6, az0 + sa * 3.85, 0.42, 1, 0.85, 0.6, 0.3 + 0.6 * night);
                light(b, ax + ca * 3.85, yc - 0.6, az0 + sa * 3.85, 0.42, 1, 0.85, 0.6, 0.3 + 0.6 * night);
            }
            double head = y + dir * 2;
            cone(b, ax + ox, head, az0 + oz, ax + ox, head + dir * 11, az0 + oz, 0.4, 1.8, 1, 0.97, 0.88, 0.22 + 0.25 * night, 0, 3);
            light(b, ax + ox, head, az0 + oz, 0.7, 1, 1, 0.95, 0.6);
            double tail = y - dir * 6;
            light(b, ax + ox, tail, az0 + oz, 1.0 + 0.9 * act, 0.5, 0.85, 1, 0.45 + 0.5 * act);
            if (act > 0.02) {
                cone(b, ax + ox, tail, az0 + oz, ax + ox, tail - dir * (10 + 10 * act), az0 + oz, 0.8, 0.2, 0.45, 0.85, 1, 0.45 * act, 0, 4);
            }
        }
        //anneaux d'energie qui montent le long du cable (fonctionnement)
        if (act > 0.02) {
            for (int k = 0; k < 5; k++) {
                double u = frac(t / 80.0 + k / 5.0);
                double y = FOOT + 2 + u * (hubLow - FOOT - 2);
                ringY(b, ax, y, az0, 2.4 + 1.2 * Math.sin(u * Math.PI), 0.4, 16, 0.5, 0.9, 1, act * 0.85 * Math.sin(u * Math.PI));
            }
        }
    }

    // ------------------------------------------------------------------ station

    private static void station(MegaBuffer b, MegaView v, double ax, double az0, double fx, double fz, double sx, double sz, double km) {
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        //moyeu et ses coiffes
        cylinderY(b, v, km, ax, TOP - 12, az0, 3, 3, 10, mr * 0.7, mg * 0.7, mb * 0.7, 0.3);
        cylinderY(b, v, km, ax, TOP - 9, az0, 4.8, 18, 16, mr * 0.95, mg * 0.95, mb * 0.95, 0.45);
        cylinderY(b, v, km, ax, TOP + 9, az0, 3, 5, 10, cr * 0.8, cg * 0.8, cb * 0.8, 0.3);
        //grand anneau habite (il tourne ; baies dessous et sur la face visible)
        double rot = t * (0.0006 + 0.0022 * act);
        int ring = 48;
        double rr = 22;
        double wr = 1 - 0.5 * act, wg = 0.85 + 0.1 * act, wb = 0.6 + 0.4 * act, wa = 0.2 + 0.75 * night + 0.2 * act;
        for (int k = 0; k < ring; k++) {
            double a = 2 * Math.PI * k / ring + rot;
            double cx = Math.cos(a), cz = Math.sin(a);
            double half = Math.PI * rr / ring * 1.03;
            boolean band = k % 6 == 0;
            double px = ax + cx * rr, pz = az0 + cz * rr;
            box(b, v, km, px, TOP, pz, -cz * half, 0, cx * half, cx * 1.7, 0, cz * 1.7, 0, 1.4, 0, band ? cr : mr * 0.92, band ? cg : mg * 0.92,
                  band ? cb : mb * 0.92, 0.4);
            light(b, px - cz * half * 0.5, TOP - 1.5, pz + cx * half * 0.5, 0.5, wr, wg, wb, wa * (0.7 + 0.3 * hash(k, 3)));
            light(b, px + cz * half * 0.5, TOP - 1.5, pz - cx * half * 0.5, 0.5, wr, wg, wb, wa * (0.7 + 0.3 * hash(k, 4)));
            double ox = px + cx * 1.8, oz = pz + cz * 1.8;
            if (cx * ox + cz * oz < 0) {
                light(b, ox, TOP + 0.4, oz, 0.34, wr, wg, wb, wa * 0.8);
                light(b, ox, TOP - 0.5, oz, 0.34, wr, wg, wb, wa * 0.8);
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = 2 * Math.PI * k / 8 + rot;
            beam(b, v, km, ax + Math.cos(a) * 4.8, TOP, az0 + Math.sin(a) * 4.8, ax + Math.cos(a) * (rr - 1.6), TOP, az0 + Math.sin(a) * (rr - 1.6),
                  0.4, mr * 0.78, mg * 0.78, mb * 0.78, 0.3);
        }
        //anneau interieur, en sens inverse
        double rot2 = -t * (0.0011 + 0.003 * act);
        double r2 = 12.5, y2 = TOP + 6;
        for (int k = 0; k < 28; k++) {
            double a = 2 * Math.PI * k / 28 + rot2;
            double cx = Math.cos(a), cz = Math.sin(a), half = Math.PI * r2 / 28 * 1.04;
            box(b, v, km, ax + cx * r2, y2, az0 + cz * r2, -cz * half, 0, cx * half, cx * 1.0, 0, cz * 1.0, 0, 0.9, 0, cr * 0.85, cg * 0.85,
                  cb * 0.85, 0.35);
            light(b, ax + cx * r2, y2 - 1.0, az0 + cz * r2, 0.3, 1, 0.8, 0.5, (0.15 + 0.6 * night) * (k % 2 == 0 ? 1 : 0.5));
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + rot2;
            beam(b, v, km, ax + Math.cos(a) * 4.8, y2, az0 + Math.sin(a) * 4.8, ax + Math.cos(a) * (r2 - 1), y2, az0 + Math.sin(a) * (r2 - 1), 0.3,
                  mr * 0.7, mg * 0.7, mb * 0.7, 0.3);
        }
        //bras d'amarrage avec leurs modules et les navettes a quai
        for (int k = 0; k < 3; k++) {
            double a = 2 * Math.PI * k / 3 + 0.9;
            double cx = Math.cos(a), cz = Math.sin(a);
            double ex = ax + cx * 33, ez = az0 + cz * 33;
            beam(b, v, km, ax + cx * 4.8, TOP - 6, az0 + cz * 4.8, ex, TOP - 6, ez, 0.45, mr * 0.7, mg * 0.7, mb * 0.7, 0.25);
            box(b, v, km, ex, TOP - 6, ez, cx * 2.4, 0, cz * 2.4, 0, 1.6, 0, -cz * 2.4, 0, cx * 2.4, mr * 0.85, mg * 0.85, mb * 0.85, 0.4);
            box(b, v, km, ex + cx * 3.8, TOP - 8.5, ez + cz * 3.8, cx * 2.2, 0, cz * 2.2, 0, 0.7, 0, -cz * 0.9, 0, cx * 0.9, 0.85, 0.86, 0.9, 0.5);
            light(b, ex + cx * 2.6, TOP - 4.2, ez + cz * 2.6, 0.55, 0.3, 1, 0.4, blink(t, 50, 8, k * 17) * (0.5 + 0.5 * night));
            light(b, ex, TOP - 7.8, ez, 0.4, 1, 0.85, 0.6, 0.15 + 0.6 * night);
        }
        //ailes solaires (axe s), tournees vers le Soleil
        double nx = v.lx - sx * (v.lx * sx + v.lz * sz), ny = v.ly, nz = v.lz - sz * (v.lx * sx + v.lz * sz);
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-6) {
            nx = 0;
            ny = 1;
            nz = 0;
            nl = 1;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        //second axe du panneau : s x n
        double qx = -sz * ny, qy = sz * nx - sx * nz, qz = sx * ny;
        double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
        qx = qx / ql * 3.6;
        qy = qy / ql * 3.6;
        qz = qz / ql * 3.6;
        double wy = TOP + 11;
        for (int i = -1; i <= 1; i += 2) {
            beam(b, v, km, ax, wy, az0, ax + sx * i * 34, wy, az0 + sz * i * 34, 0.28, mr * 0.7, mg * 0.7, mb * 0.7, 0.2);
            for (int k = 0; k < 4; k++) {
                double d = 7.5 + k * 7;
                panel(b, v, km, ax + sx * i * d, wy, az0 + sz * i * d, sx * 3.3, 0, sz * 3.3, qx, qy, qz, nx, ny, nz, 0.1, 0.17, 0.42, 1.2);
            }
            light(b, ax + sx * i * 35, wy, az0 + sz * i * 35, 0.7, i < 0 ? 1 : 0.2, i < 0 ? 0.15 : 1, 0.2,
                  blink(t, 40, 20, i * 10) * (0.5 + 0.5 * night));
        }
        light(b, ax, TOP + 14.5, az0, 1.4, 1, 1, 1, blink(t, 60, 4, 30));
        if (act > 0.02) {
            light(b, ax, TOP, az0, 9 + 2 * Math.sin(t * 0.08), 0.45, 0.85, 1, act * 0.3);
            ringY(b, ax, TOP - 2.4, az0, rr, 0.6, 48, 0.45, 0.9, 1, act * (0.35 + 0.2 * Math.sin(t * 0.1)));
        }

        //navettes en orbite autour de la station : moteur et trainee
        int ships = 2 + (int) Math.round(act * 3);
        for (int k = 0; k < ships; k++) {
            double psi = k * 2.1 + 0.3, inc = 0.45 + 0.15 * k;
            double ux = Math.cos(psi) * sx + Math.sin(psi) * fx, uz = Math.cos(psi) * sz + Math.sin(psi) * fz;
            double hx = -Math.sin(psi) * sx + Math.cos(psi) * fx, hz = -Math.sin(psi) * sz + Math.cos(psi) * fz;
            double wx = hx * Math.sin(inc), wyy = Math.cos(inc), wz = hz * Math.sin(inc);
            double radius = 30 + 3 * k;
            double dir = k % 2 == 0 ? 1 : -1;
            double phi = t * (0.004 + 0.004 * act) * dir + k * 1.9;
            double c = Math.cos(phi), s = Math.sin(phi);
            double px = ax + radius * (c * ux + s * wx), py = TOP + radius * s * wyy, pz = az0 + radius * (c * uz + s * wz);
            double vx = (-s * ux + c * wx) * dir, vy = c * wyy * dir, vz = (-s * uz + c * wz) * dir;
            //caisson oriente le long de la vitesse : o horizontal perpendiculaire, y = v x o
            double ox = -vz, oz = vx;
            double ol = Math.sqrt(ox * ox + oz * oz);
            if (ol < 1e-6) {
                ox = 1;
                oz = 0;
                ol = 1;
            }
            ox /= ol;
            oz /= ol;
            double yx = vy * oz, yy = vz * ox - vx * oz, yz = -vy * ox;
            box(b, v, km, px, py, pz, vx * 1.6, vy * 1.6, vz * 1.6, ox * 0.7, 0, oz * 0.7, yx * 0.45, yy * 0.45, yz * 0.45, 0.88, 0.88, 0.9, 0.6);
            double ex = px - vx * 1.9, ey = py - vy * 1.9, ez = pz - vz * 1.9;
            light(b, ex, ey, ez, 0.9 + 0.6 * act, 0.55, 0.8, 1, 0.55 + 0.4 * act);
            ray(b, ex, ey, ez, ex - vx * (6 + 8 * act), ey - vy * (6 + 8 * act), ez - vz * (6 + 8 * act), 0.35, 0.5, 0.8, 1, 0.25 + 0.3 * act);
            light(b, px + vx * 1.5, py + vy * 1.5, pz + vz * 1.5, 0.35, 1, 1, 1, blink(t, 30, 3, k * 7));
        }
    }
}
