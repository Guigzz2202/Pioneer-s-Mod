package fr.pioneers.space.sky.client.mega;

import static fr.pioneers.space.sky.client.mega.MegaKit.beam;
import static fr.pioneers.space.sky.client.mega.MegaKit.blink;
import static fr.pioneers.space.sky.client.mega.MegaKit.box;
import static fr.pioneers.space.sky.client.mega.MegaKit.cone;
import static fr.pioneers.space.sky.client.mega.MegaKit.light;
import static fr.pioneers.space.sky.client.mega.MegaKit.panel;
import static fr.pioneers.space.sky.client.mega.MegaKit.ray;
import static fr.pioneers.space.sky.client.mega.MegaKit.ringY;

/**
 * 1.0.7 (« ciel grandiose ») : le DIADEME de Mercure en 3D, vu de Mercure : une arche large de dix degres qui enjambe le ciel noir d'un
 * horizon a l'autre. Une poutre treillis (epine dorsale, deux rails, traverses) porte trois rangees de miroirs orientables ; tous les
 * quatre troncons, un noeud (caisson, feux) ; au sommet, la station de commande.
 * <ul>
 *     <li>au repos, les miroirs renvoient le Soleil vers le sol et ondulent lentement : des eclats courent le long de l'arche ; l'anneau
 *     etant tres haut, il reste au Soleil longtemps apres le coucher, et l'OMBRE de Mercure le parcourt par morceaux la nuit ;</li>
 *     <li>EN FONCTIONNEMENT (ports actifs), tous les miroirs se tournent vers la forge, sur l'horizon : un faisceau par miroir converge sur
 *     un point chauffe a blanc (halo, lance de lumiere verticale, ondes de chaleur), des feux courent sur les rails, la station s'embrase.</li>
 * </ul>
 * Reglages SKY : (azimut du sommet, hauteur du sommet, -, hauteur reelle en km, rayon de Mercure en km, eclat des reflets, ...).
 */
public final class DiademModel {

    /** Echelle du modele vers le ciel. */
    public static final double SCALE = 1;
    static final double DIST = 100, HALF_WIDTH = 9;
    static final int SEGMENTS = 44;
    private static final double[] ROWS = {-5.6, 0, 5.6};

    private DiademModel() {
    }

    public static void build(MegaBuffer b, MegaView v) {
        double[] p = v.params;
        double az = Math.toRadians(p[0]), apex = Math.toRadians(p[1]);
        double km = p[3];
        v.planetKm = p[4];
        double glintGain = p[5];
        double t = v.ticks, act = v.activity, night = v.night;
        //sommet A, bout E (sur l'horizon, perpendiculaire), forge F (sur l'horizon sous le sommet)
        double axd = Math.cos(apex) * Math.sin(az), ayd = Math.sin(apex), azd = -Math.cos(apex) * Math.cos(az);
        double ex = Math.cos(az), ez = Math.sin(az);
        double fX = Math.sin(az) * 72, fY = -1.2, fZ = -Math.cos(az) * 72;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        double h = Math.PI * DIST / SEGMENTS * 0.5;
        double w = HALF_WIDTH;
        for (int k = 0; k < SEGMENTS; k++) {
            double th = Math.PI * (k + 0.5) / SEGMENTS;
            double c = Math.cos(th), s = Math.sin(th);
            //position, tangente, direction (de l'observateur vers le troncon), travers
            double rx = c * ex + s * axd, ry = s * ayd, rz = c * ez + s * azd;
            double px = DIST * rx, py = DIST * ry, pz = DIST * rz;
            double tx = -s * ex + c * axd, ty = c * ayd, tz = -s * ez + c * azd;
            double bx = ty * rz - tz * ry, by = tz * rx - tx * rz, bz = tx * ry - ty * rx;
            double bl = Math.sqrt(bx * bx + by * by + bz * bz);
            bx /= bl;
            by /= bl;
            bz /= bl;
            //ombre de Mercure sur ce troncon
            double vis = v.sunAt(rx, ry, rz, km);
            v.litOverride = vis;
            //epine dorsale (au dos), rails sur les bords, traverse
            box(b, v, km, px + rx * 1.6, py + ry * 1.6, pz + rz * 1.6, tx * h, ty * h, tz * h, rx * 1.1, ry * 1.1, rz * 1.1, bx * 1.6, by * 1.6,
                  bz * 1.6, cr * 0.5, cg * 0.5, cb * 0.5, 0.3);
            for (int side = -1; side <= 1; side += 2) {
                double ox = bx * w * side, oy = by * w * side, oz = bz * w * side;
                beam(b, v, km, px + ox - tx * h, py + oy - ty * h, pz + oz - tz * h, px + ox + tx * h, py + oy + ty * h, pz + oz + tz * h, 0.42,
                      mr * 0.72, mg * 0.72, mb * 0.72, 0.4);
            }
            beam(b, v, km, px + rx * 0.8 - bx * w, py + ry * 0.8 - by * w, pz + rz * 0.8 - bz * w, px + rx * 0.8 + bx * w, py + ry * 0.8 + by * w,
                  pz + rz * 0.8 + bz * w, 0.22, mr * 0.6, mg * 0.6, mb * 0.6, 0.2);
            beam(b, v, km, px + rx * 0.8 - bx * w - tx * h, py + ry * 0.8 - by * w - ty * h, pz + rz * 0.8 - bz * w - tz * h,
                  px + rx * 0.8 + bx * w + tx * h, py + ry * 0.8 + by * w + ty * h, pz + rz * 0.8 + bz * w + tz * h, 0.16, mr * 0.55, mg * 0.55,
                  mb * 0.55, 0.2);
            //miroirs : trois rangees
            double dfx = fX - px, dfy = fY - py, dfz = fZ - pz;
            double dfl = Math.sqrt(dfx * dfx + dfy * dfy + dfz * dfz);
            dfx /= dfl;
            dfy /= dfl;
            dfz /= dfl;
            for (int row = 0; row < ROWS.length; row++) {
                double off = ROWS[row];
                double mcx = px + bx * off - rx * 0.9, mcy = py + by * off - ry * 0.9, mcz = pz + bz * off - rz * 0.9;
                //au repos : renvoyer le Soleil vers le sol (bissectrice Soleil / observateur), avec une vague ; en marche : vers la forge
                double nx = v.lx - rx, ny = v.ly - ry, nz = v.lz - rz;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-6) {
                    nx = -rx;
                    ny = -ry;
                    nz = -rz;
                } else {
                    nx /= nl;
                    ny /= nl;
                    nz /= nl;
                }
                double wave = 0.5 * Math.sin(t * 0.005 - k * 0.42 - row * 0.9);
                double cw = Math.cos(wave), sw = Math.sin(wave);
                nx = nx * cw + bx * sw;
                ny = ny * cw + by * sw;
                nz = nz * cw + bz * sw;
                double gx = v.lx + dfx, gy = v.ly + dfy, gz = v.lz + dfz;
                double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                if (gl > 1e-6) {
                    nx = nx * (1 - act) + gx / gl * act;
                    ny = ny * (1 - act) + gy / gl * act;
                    nz = nz * (1 - act) + gz / gl * act;
                }
                nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                //axes du panneau : tangente et n x tangente
                double qx = ny * tz - nz * ty, qy = nz * tx - nx * tz, qz = nx * ty - ny * tx;
                double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                qx = qx / ql * 2.55;
                qy = qy / ql * 2.55;
                qz = qz / ql * 2.55;
                double ph = h * 0.9;
                panel(b, v, km, mcx, mcy, mcz, tx * ph, ty * ph, tz * ph, qx, qy, qz, nx, ny, nz, 0.58, 0.62, 0.7, 1.4);
                //eclat du reflet vers l'observateur
                double nlDot = nx * v.lx + ny * v.ly + nz * v.lz;
                if (nlDot > 0 && vis > 0.01) {
                    double fx2 = 2 * nlDot * nx - v.lx, fy2 = 2 * nlDot * ny - v.ly, fz2 = 2 * nlDot * nz - v.lz;
                    double cl = Math.sqrt(mcx * mcx + mcy * mcy + mcz * mcz);
                    double toward = -(fx2 * mcx + fy2 * mcy + fz2 * mcz) / cl;
                    double g = Math.pow(Math.max(0, toward), 120) * glintGain * vis;
                    if (g > 0.01) {
                        light(b, mcx, mcy, mcz, 5 + 9 * g, 1, 0.95, 0.82, Math.min(1, g * 1.2));
                        light(b, mcx, mcy, mcz, 1.8, 1, 1, 1, Math.min(1, g * 3));
                    }
                }
                //faisceau vers la forge
                if (act > 0.02 && vis > 0.05) {
                    double flick = 0.75 + 0.25 * Math.sin(t * 0.31 + k * 1.7 + row * 2.3);
                    double a = act * vis * flick;
                    if (row == 1) {
                        ray(b, mcx, mcy, mcz, fX, fY + 1.5, fZ, 1.1, 1, 0.78, 0.45, a * 0.22);
                    }
                    ray(b, mcx, mcy, mcz, fX, fY + 1.5, fZ, 0.28, 1, 0.96, 0.88, a * 0.5);
                }
            }
            //lueur douce de l'arche eclairee (la nuit, l'anneau au Soleil se detache sur le ciel noir)
            light(b, px - rx, py - ry, pz - rz, w * 1.1, mr, mg, mb, 0.05 * vis * (0.35 + 0.65 * night));
            //noeud tous les quatre troncons : caisson et feux
            if (k % 4 == 2) {
                box(b, v, km, px + rx * 1.2, py + ry * 1.2, pz + rz * 1.2, tx * 1.6, ty * 1.6, tz * 1.6, rx * 2.2, ry * 2.2, rz * 2.2,
                      bx * (w + 1.4), by * (w + 1.4), bz * (w + 1.4), cr * 0.65, cg * 0.65, cb * 0.65, 0.3);
                for (int side = -1; side <= 1; side += 2) {
                    light(b, px - rx + bx * (w + 1.6) * side, py - ry + by * (w + 1.6) * side, pz - rz + bz * (w + 1.6) * side, 0.9,
                          side < 0 ? 1 : 0.3, side < 0 ? 0.2 : 1, 0.25, blink(t, 60, 30, k * 9) * (0.5 + 0.5 * night));
                }
            }
            //en fonctionnement, des feux courent sur les rails vers la forge
            if (act > 0.02) {
                double chase = Math.pow(Math.max(0, Math.cos((k / (double) SEGMENTS - t * 0.005) * Math.PI * 8)), 6);
                double tt = Math.abs(k + 0.5 - SEGMENTS / 2.0) < 1 ? 0 : 1;
                for (int side = -1; side <= 1; side += 2) {
                    light(b, px - rx + bx * w * side, py - ry + by * w * side, pz - rz + bz * w * side, 1.2, 1, 0.75, 0.4,
                          act * chase * tt);
                }
            }
        }
        v.litOverride = -1;
        station(b, v, axd * DIST, ayd * DIST, azd * DIST, axd, ayd, azd, ex, ez, km);
        if (act > 0.02) {
            forge(b, v, fX, fY, fZ);
        }
    }

    /** Station de commande au sommet de l'arche : caisson, baies, antennes, balise. */
    private static void station(MegaBuffer b, MegaView v, double px, double py, double pz, double rx, double ry, double rz, double ex,
          double ez, double km) {
        double t = v.ticks, act = v.activity, night = v.night;
        double mr = MegaKit.red(v.color), mg = MegaKit.green(v.color), mb = MegaKit.blue(v.color);
        double cr = MegaKit.red(v.accent), cg = MegaKit.green(v.accent), cb = MegaKit.blue(v.accent);
        //au sommet : tangente = -E, travers = T x R
        double tx = -ex, ty = 0, tz = -ez;
        double bx = ty * rz - tz * ry, by = tz * rx - tx * rz, bz = tx * ry - ty * rx;
        v.litOverride = v.sunAt(rx, ry, rz, km);
        double cx = px - rx * 2.5, cy = py - ry * 2.5, cz = pz - rz * 2.5;
        box(b, v, km, cx, cy, cz, tx * 5, ty * 5, tz * 5, rx * 2.2, ry * 2.2, rz * 2.2, bx * 6, by * 6, bz * 6, mr * 0.8, mg * 0.8, mb * 0.8,
              0.45);
        box(b, v, km, cx - rx * 2.6, cy - ry * 2.6, cz - rz * 2.6, tx * 3, ty * 3, tz * 3, rx * 0.8, ry * 0.8, rz * 0.8, bx * 3.6, by * 3.6,
              bz * 3.6, cr * 0.8, cg * 0.8, cb * 0.8, 0.4);
        for (int side = -1; side <= 1; side += 2) {
            double ax = cx + bx * 12 * side, ay = cy + by * 12 * side, az = cz + bz * 12 * side;
            beam(b, v, km, cx + bx * 6 * side, cy + by * 6 * side, cz + bz * 6 * side, ax, ay, az, 0.3, mr * 0.7, mg * 0.7, mb * 0.7, 0.3);
            panel(b, v, km, ax - rx * 0.6, ay - ry * 0.6, az - rz * 0.6, tx * 2.6, ty * 2.6, tz * 2.6, bx * 2.6, by * 2.6, bz * 2.6, -rx, -ry, -rz,
                  0.8, 0.8, 0.82, 0.6);
        }
        v.litOverride = -1;
        double wa = 0.2 + 0.7 * night + 0.3 * act;
        for (int k = -4; k <= 4; k++) {
            light(b, cx - rx * 2.3 + tx * k * 1.05, cy - ry * 2.3 + ty * k * 1.05, cz - rz * 2.3 + tz * k * 1.05, 0.4, 1, 0.82 - 0.3 * act,
                  0.55 - 0.3 * act, wa);
        }
        light(b, cx - rx * 3.6, cy - ry * 3.6, cz - rz * 3.6, 1.6, 1, 1, 1, blink(t, 50, 4, 0));
        if (act > 0.02) {
            light(b, cx, cy, cz, 10 + 2 * Math.sin(t * 0.1), 1, 0.6, 0.3, act * 0.3);
        }
    }

    /** La forge, sur l'horizon : point chauffe a blanc, halo, lance de lumiere verticale, ondes de chaleur. */
    private static void forge(MegaBuffer b, MegaView v, double fX, double fY, double fZ) {
        double t = v.ticks, act = v.activity;
        double pulse = 1 + 0.15 * Math.sin(t * 0.21);
        double y = fY + 1.5;
        light(b, fX, y + 6, fZ, 44 * pulse, 1, 0.38, 0.1, act * 0.5);
        light(b, fX, y + 3, fZ, 18 * pulse, 1, 0.75, 0.4, act * 0.9);
        light(b, fX, y, fZ, 7, 1, 1, 0.95, act);
        //lance de lumiere : elle depasse les reliefs de l'horizon
        cone(b, fX, y, fZ, fX, y + 85, fZ, 5, 1.2, 1, 0.75, 0.45, act * 0.6, 0.05 * act, 10);
        cone(b, fX, y, fZ, fX, y + 85, fZ, 1.6, 0.3, 1, 1, 0.95, act * 0.95, 0.1 * act, 10);
        for (int k = 0; k < 3; k++) {
            double u = MegaKit.frac(t / 90.0 + k / 3.0);
            ringY(b, fX, y + 0.5 + u * 4, fZ, 3 + u * 26, 0.6 + u * 1.6, 28, 1, 0.55, 0.25, act * 0.5 * (1 - u));
        }
        for (int k = 0; k < 5; k++) {
            double u = MegaKit.frac(t / 140.0 + k / 5.0);
            ray(b, fX, y + 1 + u * 34, fZ, fX, y + 6 + u * 34, fZ, 5 + 7 * u, 1, 0.55, 0.25, act * 0.16 * (1 - u));
        }
    }
}
