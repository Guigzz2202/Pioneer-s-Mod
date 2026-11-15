package fr.pioneers.client.home;

import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.home.line.LineShape;
import java.util.Arrays;

/**
 * 1.0.3-beta (lot 11, Guillaume : « largement ameliorer la texture du fil quand un item est accroche dessus (impression de levitation) ») :
 * dessin de la corde a linge CHARGEE et des attaches, en px dans le repere du crochet maitre (uniquement {@link KDraw} : le banc des rendus
 * {@code tools/kinetics} l'execute tel quel, scene « clothesline »).
 * <ul>
 *   <li>corde : chainette + creux de chaque piece ({@link LineShape#dip}), un troncon jusqu'a chaque piece (le V est net sous la pince) ;</li>
 *   <li>vetements et bannieres : une PINCE A LINGE en bois (deux machoires a cheval sur la corde, ressort de fer) qui mord le haut de la
 *   piece ; poissons, viandes, herbes : une FICELLE nouee a la corde et un petit crochet de fer ;</li>
 *   <li>{@link #attach} : point ou le HAUT de la piece est tenu (le rendu y pose le haut reel du modele de l'objet) et pente de la corde.</li>
 * </ul>
 */
public final class LineVisuals {

    /** Segments de la chainette (en plus d'un point a chaque piece). */
    public static final int SEGMENTS = 16;
    /** Pince : longueur des machoires sous la corde, morsure dans la piece (px). Ficelle : longueur jusqu'au crochet (px). */
    public static final float PIN_DOWN = 2.6F, PIN_BITE = 0.7F, STRING = 2.2F, HOOK = 0.9F;

    private LineVisuals() {
    }

    /** Une piece accrochee : position t (0..1) et sorte d'attache (pince pour les vetements, ficelle sinon). */
    public record Load(double t, boolean clip) {
    }

    /** Point d'attache (px) du haut d'une piece et pente de la corde (degres, vers b) en ce point. */
    public record Attach(float x, float y, float z, float slope) {
    }

    static double[] ts(Load[] loads) {
        double[] t = new double[loads.length];
        for (int i = 0; i < loads.length; i++) {
            t[i] = loads[i].t();
        }
        return t;
    }

    /** Point de la corde chargee (px). */
    static float[] at(float[] a, float[] b, double sagPx, double t, double[] loads) {
        return new float[]{(float) (a[0] + (b[0] - a[0]) * t), (float) LineShape.y(a[1], b[1], sagPx, t, loads, 16), (float) (a[2] + (b[2] - a[2]) * t)};
    }

    /** Corde (texture {@code tex}, largeur {@code w}) entre {@code a} et {@code b} (px), fleche {@code sagPx}, chargee de {@code loads}. */
    public static void rope(KDraw d, float[] a, float[] b, double sagPx, Load[] loads, String tex, float w) {
        double[] lt = ts(loads);
        double[] pts = new double[SEGMENTS + 1 + lt.length];
        for (int i = 0; i <= SEGMENTS; i++) {
            pts[i] = i / (double) SEGMENTS;
        }
        System.arraycopy(lt, 0, pts, SEGMENTS + 1, lt.length);
        Arrays.sort(pts);
        float[] prev = at(a, b, sagPx, 0, lt);
        for (int i = 1; i < pts.length; i++) {
            if (pts[i] - pts[i - 1] < 1e-4) {
                continue;
            }
            float[] cur = at(a, b, sagPx, pts[i], lt);
            d.beam(prev[0], prev[1], prev[2], cur[0], cur[1], cur[2], w, tex);
            prev = cur;
        }
    }

    /** Pente (degres) de la corde chargee en {@code t}, comptee vers {@code b} (positive si elle monte vers b). */
    static float slope(float[] a, float[] b, double sagPx, double t, double[] loads) {
        double e = 0.004;
        float[] p0 = at(a, b, sagPx, Math.max(0, t - e), loads), p1 = at(a, b, sagPx, Math.min(1, t + e), loads);
        double h = Math.hypot(p1[0] - p0[0], p1[2] - p0[2]);
        return (float) Math.toDegrees(Math.atan2(p1[1] - p0[1], Math.max(1e-6, h)));
    }

    /** Attache de la piece {@code i} (pince ou ficelle) dessinee sur la corde ; renvoie ou tenir le haut de la piece. */
    public static Attach attach(KDraw d, float[] a, float[] b, double sagPx, Load[] loads, int i, String ropeTex) {
        double[] lt = ts(loads);
        Load l = loads[i];
        float[] p = at(a, b, sagPx, l.t(), lt);
        double hx = b[0] - a[0], hz = b[2] - a[2], hl = Math.max(1e-6, Math.hypot(hx, hz));
        float nx = (float) (-hz / hl), nz = (float) (hx / hl);                    //en travers de la corde (horizontal)
        float sl = slope(a, b, sagPx, l.t(), lt);
        if (l.clip()) {
            //pince a linge : deux machoires de bois a cheval sur la corde, ressort de fer, la piece pincee entre les machoires
            for (int s = -1; s <= 1; s += 2) {
                float ox = nx * 0.42F * s, oz = nz * 0.42F * s;
                d.beam(p[0] + ox, p[1] + 1.2F, p[2] + oz, p[0] + ox, p[1] - PIN_DOWN, p[2] + oz, 0.55F, "oak");
            }
            d.beam(p[0] - nx * 0.75F, p[1] + 0.1F, p[2] - nz * 0.75F, p[0] + nx * 0.75F, p[1] + 0.1F, p[2] + nz * 0.75F, 0.32F, "iron");
            return new Attach(p[0], p[1] - PIN_DOWN + PIN_BITE, p[2], sl);
        }
        //ficelle nouee a la corde, petit crochet de fer au bout
        d.beam(p[0], p[1] + 0.25F, p[2], p[0], p[1] - STRING, p[2], 0.3F, ropeTex);
        d.beam(p[0], p[1] - STRING, p[2], p[0] + nx * 0.35F, p[1] - STRING - HOOK, p[2] + nz * 0.35F, 0.3F, "iron");
        return new Attach(p[0] + nx * 0.35F, p[1] - STRING - HOOK + 0.3F, p[2] + nz * 0.35F, sl);
    }
}
