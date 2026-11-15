package fr.pioneers.logistics.warehouse;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 1.0.4-beta (lot 1) : GEOMETRIE du trajet d'un colis le long des cordes de l'entrepot, la meme au serveur (longueur, chute d'un colis
 * perdu) et au client (dessin) : poulies P0 .. Pn (P0 et Pn : stations), sens de rotation {@code spin}, angle de depose de chaque station.
 * <p>
 * Repere : plan (x, z), angle a = atan2(dz, dx). Regle de la corde du mod ({@code RopeMotion}, axe Y) : une roue qui tourne dans le sens
 * positif (spin +1) fait courir sa jante vers les angles DECROISSANTS. Le colis : depart a l'angle de depose de P0, tour de jante jusqu'au
 * brin du premier troncon, troncon droit, tour de jante de chaque poulie intermediaire (du brin d'arrivee au brin de depart), dernier tour
 * de jante jusqu'a l'angle de depose de Pn.
 * <p>
 * 1.0.4-beta (lot 2) : chaque roue a SON rayon primitif et SA hauteur de gorge ({@link LineNode} : poulie d'entrepot 5,25 px a 12,5 px,
 * poulie de base 4 px par taille a 8 px) : le brin qui va de A vers B est la tangente exterieure des deux gorges, normale a l'angle
 * base(A -> B) + spin x (pi / 2 - phi), phi = asin((rA - rB) / d) (le dessin de la corde du mod) ; il monte ou descend en ligne droite
 * d'une gorge a l'autre ; {@code point} rend aussi la hauteur de la corde.
 */
public final class ParcelRoute {

    /** Rayon de la gorge d'une station (blocs) : 1.0.4-beta (lot 27) la petite poulie (4 px ; la poulie d'entrepot, retiree, avait 5,25). */
    public static final double R = 4 / 16.0;
    /** Hauteur de la corde d'une station seule (px) : 8 (une corde), comme la petite poulie. */
    public static final float ROPE_Y_PX = 8F;

    private final double[] cx, cz, r;
    /** Hauteurs absolues (blocs) de la corde au depart et a l'arrivee de chaque troncon : gorge de chaque bout. */
    private final double[] ya, yb;
    /** Longueurs cumulees des morceaux : arc0, troncon0, arc1, troncon1, ..., arcN. */
    private final double[] cum;
    private final double[] arcStart, arcSweep, strand;
    private final int spin;
    private final double length;

    /** Course de petites poulies seulement (rayon {@link #R}, gorge a {@link #ROPE_Y_PX}) : celle du lot 1 (banc). */
    public ParcelRoute(List<BlockPos> pulleys, int spin, double dropStart, double dropEnd) {
        this(pulleys, spin, dropStart, dropEnd, null, null);
    }

    /**
     * {@code radius} : rayon primitif de chaque roue (blocs) ; {@code ropeY} : hauteurs absolues (blocs) de la corde de chaque troncon,
     * au depart puis a l'arrivee ({@code 2 (n - 1)} valeurs : une poulie de base qui porte deux cordes les tient a deux hauteurs de sa
     * gorge) ; null : poulies d'entrepot.
     */
    public ParcelRoute(List<BlockPos> pulleys, int spin, double dropStart, double dropEnd, double[] radius, double[] ropeY) {
        int n = pulleys.size();
        this.spin = spin >= 0 ? 1 : -1;
        cx = new double[n];
        cz = new double[n];
        r = new double[n];
        ya = new double[Math.max(1, n - 1)];
        yb = new double[Math.max(1, n - 1)];
        for (int i = 0; i < n; i++) {
            BlockPos p = pulleys.get(i);
            cx[i] = p.getX() + 0.5;
            cz[i] = p.getZ() + 0.5;
            r[i] = radius != null ? radius[i] : R;
        }
        for (int i = 0; i < ya.length; i++) {
            double def = pulleys.get(Math.min(i, n - 1)).getY() + ROPE_Y_PX / 16.0;
            ya[i] = ropeY != null && n > 1 ? ropeY[2 * i] : def;
            yb[i] = ropeY != null && n > 1 ? ropeY[2 * i + 1] : (n > 1 ? pulleys.get(i + 1).getY() + ROPE_Y_PX / 16.0 : def);
        }
        strand = new double[Math.max(0, n - 1)];
        for (int i = 0; i + 1 < n; i++) {
            double d = Math.max(1e-6, Math.hypot(cx[i + 1] - cx[i], cz[i + 1] - cz[i]));
            double phi = Math.asin(Math.max(-1, Math.min(1, (r[i] - r[i + 1]) / d)));
            strand[i] = Math.atan2(cz[i + 1] - cz[i], cx[i + 1] - cx[i]) + this.spin * (Math.PI / 2 - phi);
        }
        arcStart = new double[n];
        arcSweep = new double[n];
        for (int i = 0; i < n; i++) {
            double from = i == 0 ? dropStart : strand[i - 1];
            double to = i == n - 1 ? dropEnd : strand[i];
            arcStart[i] = from;
            //La jante court vers les angles decroissants quand spin = +1
            double sweep = this.spin > 0 ? mod2pi(from - to) : mod2pi(to - from);
            //Ecart d'arrondi autour de 0 : pas de tour complet pour un passage droit
            arcSweep[i] = sweep > 2 * Math.PI - 1e-6 ? 0 : sweep;
        }
        cum = new double[2 * n];
        double acc = 0;
        for (int i = 0; i < n; i++) {
            acc += arcSweep[i] * r[i];
            cum[2 * i] = acc;
            if (i + 1 < n) {
                acc += segment(i);
            }
            cum[2 * i + 1] = acc;
        }
        length = acc;
    }

    /** Course reelle : rayons et hauteurs lus sur les roues du monde, angles de depose des stations. */
    public static ParcelRoute of(BlockGetter level, List<BlockPos> route, int spin) {
        int n = route.size();
        double[] radius = new double[n], ropeY = new double[Math.max(0, 2 * (n - 1))];
        LineNode[] nodes = new LineNode[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = LineNode.at(level, route.get(i));
            radius[i] = (nodes[i] == null ? 4F : nodes[i].pitchPx()) / 16.0;
        }
        for (int i = 0; i + 1 < n; i++) {
            ropeY[2 * i] = route.get(i).getY() + (nodes[i] == null ? ROPE_Y_PX : nodes[i].ropeYPx(route.get(i + 1))) / 16.0;
            ropeY[2 * i + 1] = route.get(i + 1).getY() + (nodes[i + 1] == null ? ROPE_Y_PX : nodes[i + 1].ropeYPx(route.get(i))) / 16.0;
        }
        return new ParcelRoute(route, spin, WarehouseLines.dropAngle(level, route.get(0)), WarehouseLines.dropAngle(level, route.get(n - 1)), radius,
              ropeY);
    }

    private static double mod2pi(double a) {
        double t = a % (2 * Math.PI);
        return t < 0 ? t + 2 * Math.PI : t;
    }

    public double length() {
        return length;
    }

    public int spin() {
        return spin;
    }

    /** Points de tangence (x, z) du brin du troncon {@code i} sur ses deux roues : {ax, az, bx, bz}. */
    private void ends(int i, double[] e) {
        double c = Math.cos(strand[i]), s = Math.sin(strand[i]);
        e[0] = cx[i] + c * r[i];
        e[1] = cz[i] + s * r[i];
        e[2] = cx[i + 1] + c * r[i + 1];
        e[3] = cz[i + 1] + s * r[i + 1];
    }

    /**
     * Point de contact (x, z), direction de marche (dx, dz), troncon (indice de sa premiere poulie, -1 sur une roue), avancee 0..1 sur le
     * troncon (fleche de la corde) et hauteur absolue de la corde (blocs, sans la fleche) a l'abscisse {@code s} (bornee) ; {@code out} : 6
     * valeurs, ou 7 avec la hauteur.
     */
    public void point(double s, double[] out) {
        s = Math.max(0, Math.min(length, s));
        int n = cx.length;
        double prev = 0;
        double[] e = new double[4];
        for (int i = 0; i < n; i++) {
            double arcEnd = cum[2 * i];
            if (s <= arcEnd || (i == n - 1)) {
                double t = arcSweep[i] <= 1e-9 ? 0 : Math.min(1, (s - prev) / (arcSweep[i] * r[i]));
                double a = arcStart[i] - spin * arcSweep[i] * t;
                out[0] = cx[i] + Math.cos(a) * r[i];
                out[1] = cz[i] + Math.sin(a) * r[i];
                //Tangente de la jante (sens de marche) : derivee de l'angle -spin
                out[2] = Math.sin(a) * spin;
                out[3] = -Math.cos(a) * spin;
                if (arcSweep[i] <= 1e-9 && i + 1 < n) {
                    ends(i, e);
                    double dx = e[2] - e[0], dz = e[3] - e[1], d = Math.max(1e-9, Math.hypot(dx, dz));
                    out[2] = dx / d;
                    out[3] = dz / d;
                }
                if (out.length > 5) {
                    out[4] = -1;
                    out[5] = 0;
                }
                if (out.length > 6) {
                    //Sur la roue : de la hauteur de la corde d'arrivee a celle de la corde de depart
                    double yIn = i == 0 ? ya[0] : yb[i - 1], yOut = i == n - 1 ? (n > 1 ? yb[n - 2] : ya[0]) : ya[i];
                    out[6] = yIn + (yOut - yIn) * t;
                }
                return;
            }
            prev = arcEnd;
            double segEnd = cum[2 * i + 1];
            if (s <= segEnd) {
                ends(i, e);
                double d = segEnd - arcEnd, t = d <= 1e-9 ? 0 : (s - arcEnd) / d;
                out[0] = e[0] + (e[2] - e[0]) * t;
                out[1] = e[1] + (e[3] - e[1]) * t;
                double dx = e[2] - e[0], dz = e[3] - e[1], len = Math.max(1e-9, Math.hypot(dx, dz));
                out[2] = dx / len;
                out[3] = dz / len;
                if (out.length > 5) {
                    out[4] = i;
                    out[5] = t;
                }
                if (out.length > 6) {
                    out[6] = ya[i] + (yb[i] - ya[i]) * t;
                }
                return;
            }
            prev = segEnd;
        }
    }

    /** Longueur horizontale (blocs, entre centres) du troncon {@code i} : celle de la fleche de la corde. */
    public double span(int i) {
        return i < 0 || i + 1 >= cx.length ? 0 : Math.hypot(cx[i + 1] - cx[i], cz[i + 1] - cz[i]);
    }

    /** Longueur parcourue (blocs) sur le brin du troncon {@code i} : tangente exterieure des deux gorges, pente comprise. */
    public double segment(int i) {
        if (i < 0 || i + 1 >= cx.length) {
            return 0;
        }
        double d = span(i), dr = r[i] - r[i + 1];
        double flat = Math.sqrt(Math.max(0, d * d - dr * dr));
        return Math.hypot(flat, yb[i] - ya[i]);
    }

    /** Indice de la poulie dont le colis est le plus proche en {@code s} (vitesse lue sur sa roue). */
    public int pulleyAt(double s) {
        int n = cx.length;
        for (int i = 0; i < n; i++) {
            if (s <= cum[2 * i + 1]) {
                return i;
            }
        }
        return n - 1;
    }

    /** Courbure au point s : vrai si le colis tourne autour d'une roue (pour le balancement). */
    public boolean onArc(double s) {
        int n = cx.length;
        double prev = 0;
        for (int i = 0; i < n; i++) {
            if (s <= cum[2 * i]) {
                return s > prev && arcSweep[i] > 1e-6;
            }
            prev = cum[2 * i + 1];
            if (s <= prev) {
                return false;
            }
        }
        return false;
    }
}
