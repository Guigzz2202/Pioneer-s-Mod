package fr.pioneers.rail.track;

import net.minecraft.core.Direction;

/**
 * 0.20.0 : un chemin de roulement d'une piece de voie, dans le repere du bloc d'ancrage de la piece, en px (16 par bloc ; x vers l'est,
 * y vers le haut a partir du bas du bloc, z vers le sud). Une droite (en plan, avec une pente eventuelle) ou un arc de cercle horizontal.
 * <p>
 * Ses deux bouts ({@link End}) sont des milieux de face de bloc, a mi-hauteur 0 ou 8 px : c'est par eux que les pieces se raccordent.
 * L'abscisse curviligne {@code s} va de 0 (bout A) a {@link #length} (bout B). Un chemin peut finir en impasse ({@link End#open} faux :
 * butoir), son bout B est alors au milieu du bloc.
 * <p>
 * 0.20.6 : courbe de Bezier cubique a plat (raccords droite / diagonale) ; un bout peut etre decale du milieu de sa face
 * ({@link End#lane}, de {@link #LANE} px) : point de raccord propre aux deux moities d'un raccord.
 */
public final class TrackPath {

    /** Decalage (px) du point qui relie les deux moities d'un raccord droite / diagonale, le long de la face. */
    public static final float LANE;

    /**
     * Bout d'un chemin : bloc (decalage depuis l'ancre), face de sortie (vers l'exterieur du bloc), demi-hauteur (0 : bas du bloc, 1 : 8 px),
     * decalage le long de la face ({@code lane} : 0 milieu, +1 vers +x ou +z, -1 vers -x ou -z). Le haut d'une pente se note sur le bloc du
     * dessus, demi-hauteur 0.
     */
    public record End(int dx, int dy, int dz, Direction face, int half, boolean open, int lane) {

        public End(int dx, int dy, int dz, Direction face, int half, boolean open) {
            this(dx, dy, dz, face, half, open, 0);
        }

        /** Position (px, repere de l'ancre) du point de raccord. */
        public float x() {
            return dx * 16 + 8 + face.getStepX() * 8 + (face.getAxis() == Direction.Axis.Z ? lane * LANE : 0);
        }

        public float y() {
            return dy * 16 + half * 8;
        }

        public float z() {
            return dz * 16 + 8 + face.getStepZ() * 8 + (face.getAxis() == Direction.Axis.X ? lane * LANE : 0);
        }

        End rotate() {
            Direction f = face.getClockWise();
            //(x, z) -> (16 - z, x) : un decalage en +x (face nord ou sud) devient +z ; un decalage en +z (face est ou ouest) devient -x
            int l = face.getAxis() == Direction.Axis.X ? -lane : lane;
            return new End(-dz, dy, dx, f, half, open, l);
        }

        End mirror() {
            //x -> 16 - x : est <-> ouest, un decalage le long de x change de signe
            Direction f = face.getAxis() == Direction.Axis.X ? face.getOpposite() : face;
            int l = face.getAxis() == Direction.Axis.Z ? -lane : lane;
            return new End(-dx, dy, dz, f, half, open, l);
        }
    }

    private enum Kind {LINE, ARC, BEZIER}

    public final End a, b;
    public final float length;
    private final Kind kind;
    /** Arc : centre (cx, cz), rayon, angles de depart et d'arrivee (radians, x = cos, z = sin). */
    private final float cx, cz, r, t0, t1;
    private final float ax, ay, az, bx, by, bz;
    /** Bezier : points de controle (x0 z0 x1 z1 x2 z2 x3 z3) et parametre t pour chaque abscisse (table de {@link #SAMPLES} pas). */
    private final float[] ctrl;
    private final float[] tAt;
    /** Vitesse maximale propre au trace (px/tick), 0 = celle de la voie. */
    public final float limit;

    private static final int SAMPLES = 32;

    private TrackPath(End a, End b, float ax, float ay, float az, float bx, float by, float bz, Kind kind, float cx, float cz, float r, float t0,
          float t1, float[] ctrl, float limit) {
        this.a = a;
        this.b = b;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.r = r;
        this.t0 = t0;
        this.t1 = t1;
        this.ctrl = ctrl;
        this.limit = limit;
        if (kind == Kind.ARC) {
            float horiz = Math.abs(t1 - t0) * r;
            length = (float) Math.hypot(horiz, by - ay);
            tAt = null;
        } else if (kind == Kind.BEZIER) {
            //Longueur par petits pas, puis table s -> t
            int n = 256;
            float[] acc = new float[n + 1];
            float[] p = new float[2], q = new float[2];
            bez(ctrl, 0, p);
            for (int i = 1; i <= n; i++) {
                bez(ctrl, i / (float) n, q);
                acc[i] = acc[i - 1] + (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
                p[0] = q[0];
                p[1] = q[1];
            }
            length = acc[n];
            tAt = new float[SAMPLES + 1];
            int j = 0;
            for (int k = 0; k <= SAMPLES; k++) {
                float s = length * k / SAMPLES;
                while (j < n && acc[j + 1] < s) {
                    j++;
                }
                float span = j < n ? acc[j + 1] - acc[j] : 1;
                float f = span <= 0 ? 0 : (s - acc[j]) / span;
                tAt[k] = Math.min(1, (j + f) / n);
            }
        } else {
            length = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
            tAt = null;
        }
    }

    /** Droite d'un bout a l'autre (pente comprise). */
    public static TrackPath line(End a, End b) {
        return new TrackPath(a, b, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), Kind.LINE, 0, 0, 0, 0, 0, null, 0);
    }

    /** Droite qui finit en impasse au milieu du bloc (butoir). */
    public static TrackPath deadEnd(End a, End b, float stopX, float stopZ) {
        return new TrackPath(a, b, a.x(), a.y(), a.z(), stopX, a.y(), stopZ, Kind.LINE, 0, 0, 0, 0, 0, null, 0);
    }

    /** Arc horizontal de centre (cx, cz) entre deux bouts situes sur le cercle (le plus court des deux sens). */
    public static TrackPath arc(End a, End b, float cx, float cz, float limit) {
        float t0 = (float) Math.atan2(a.z() - cz, a.x() - cx);
        float t1 = (float) Math.atan2(b.z() - cz, b.x() - cx);
        float d = t1 - t0;
        while (d > Math.PI) {
            d -= (float) (2 * Math.PI);
        }
        while (d < -Math.PI) {
            d += (float) (2 * Math.PI);
        }
        float r = (float) Math.hypot(a.x() - cx, a.z() - cz);
        return new TrackPath(a, b, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), Kind.ARC, cx, cz, r, t0, t0 + d, null, limit);
    }

    /** 0.20.6 : courbe de Bezier cubique a plat ; {@code ctrl} = x0 z0 x1 z1 x2 z2 x3 z3 (le premier et le dernier point sont les bouts). */
    public static TrackPath bezier(End a, End b, float[] ctrl, float limit) {
        return new TrackPath(a, b, ctrl[0], a.y(), ctrl[1], ctrl[6], b.y(), ctrl[7], Kind.BEZIER, 0, 0, minRadius(ctrl), 0, 0, ctrl.clone(), limit);
    }

    /** Courbe a l'ecran : arc ou Bezier (dessin en petits troncons). */
    public boolean isArc() {
        return kind != Kind.LINE;
    }

    /** Rayon de l'arc, ou plus petit rayon de courbure de la Bezier. */
    public float radius() {
        return r;
    }

    /** Pente (montee par px parcouru, signee de A vers B). */
    public float grade() {
        return length <= 0 ? 0 : (by - ay) / length;
    }

    private float paramAt(float s) {
        float u = length <= 0 ? 0 : Math.max(0, Math.min(1, s / length)) * SAMPLES;
        int i = Math.min(SAMPLES - 1, (int) u);
        float f = u - i;
        return tAt[i] + (tAt[i + 1] - tAt[i]) * f;
    }

    /** Point a l'abscisse s : (x, y, z) en px. */
    public void point(float s, float[] out) {
        float t = length <= 0 ? 0 : Math.max(0, Math.min(1, s / length));
        switch (kind) {
            case ARC -> {
                float th = t0 + (t1 - t0) * t;
                out[0] = cx + r * (float) Math.cos(th);
                out[2] = cz + r * (float) Math.sin(th);
            }
            case BEZIER -> {
                float[] p = new float[2];
                bez(ctrl, paramAt(s), p);
                out[0] = p[0];
                out[2] = p[1];
            }
            default -> {
                out[0] = ax + (bx - ax) * t;
                out[2] = az + (bz - az) * t;
            }
        }
        out[1] = ay + (by - ay) * t;
    }

    /** Direction (unitaire) du parcours de A vers B a l'abscisse s. */
    public void tangent(float s, float[] out) {
        float t = length <= 0 ? 0 : Math.max(0, Math.min(1, s / length));
        float hx, hz, hl;
        switch (kind) {
            case ARC -> {
                float th = t0 + (t1 - t0) * t;
                float sign = Math.signum(t1 - t0);
                hx = -(float) Math.sin(th) * sign;
                hz = (float) Math.cos(th) * sign;
                hl = Math.abs(t1 - t0) * r;
            }
            case BEZIER -> {
                float[] d = new float[2];
                bezDerivative(ctrl, paramAt(s), d);
                float l = (float) Math.hypot(d[0], d[1]);
                hx = l > 0 ? d[0] / l : 0;
                hz = l > 0 ? d[1] / l : 0;
                hl = length;
            }
            default -> {
                hx = bx - ax;
                hz = bz - az;
                hl = (float) Math.hypot(hx, hz);
                if (hl > 0) {
                    hx /= hl;
                    hz /= hl;
                }
            }
        }
        float dy = by - ay;
        float horiz = hl;
        float n = (float) Math.hypot(horiz, dy);
        if (n <= 0) {
            out[0] = hx;
            out[1] = 0;
            out[2] = hz;
            return;
        }
        out[0] = hx * horiz / n;
        out[1] = dy / n;
        out[2] = hz * horiz / n;
    }

    /** Direction a plat (x, z) qui sort du chemin par ce bout (vers l'exterieur). */
    public void outward(boolean atB, float[] out) {
        float[] t = new float[3];
        tangent(atB ? length : 0, t);
        float sign = atB ? 1 : -1;
        float l = (float) Math.hypot(t[0], t[2]);
        out[0] = l > 0 ? sign * t[0] / l : 0;
        out[1] = l > 0 ? sign * t[2] / l : 0;
    }

    /** Tourne le chemin d'un quart de tour (sens horaire vu de dessus) autour du centre du bloc d'ancrage. */
    TrackPath rotate() {
        End ra = a.rotate(), rb = b.rotate();
        if (kind == Kind.ARC) {
            float ncx = 16 - cz, ncz = cx;
            return new TrackPath(ra, rb, 16 - az, ay, ax, 16 - bz, by, bx, Kind.ARC, ncx, ncz, r, t0 + (float) (Math.PI / 2), t1 + (float) (Math.PI / 2),
                  null, limit);
        }
        if (kind == Kind.BEZIER) {
            float[] c = new float[8];
            for (int i = 0; i < 4; i++) {
                c[2 * i] = 16 - ctrl[2 * i + 1];
                c[2 * i + 1] = ctrl[2 * i];
            }
            return bezier(ra, rb, c, limit);
        }
        return new TrackPath(ra, rb, 16 - az, ay, ax, 16 - bz, by, bx, Kind.LINE, 0, 0, 0, 0, 0, null, limit);
    }

    /** Symetrique par rapport a l'axe nord-sud du bloc (x -> 16 - x). Droites et Bezier seulement. */
    TrackPath mirror() {
        End ma = a.mirror(), mb = b.mirror();
        if (kind == Kind.BEZIER) {
            float[] c = ctrl.clone();
            for (int i = 0; i < 4; i++) {
                c[2 * i] = 16 - ctrl[2 * i];
            }
            return bezier(ma, mb, c, limit);
        }
        return new TrackPath(ma, mb, 16 - ax, ay, az, 16 - bx, by, bz, Kind.LINE, 0, 0, 0, 0, 0, null, limit);
    }

    // ------------------------------------------------------------------ Bezier

    static void bez(float[] c, float t, float[] out) {
        float u = 1 - t;
        float b0 = u * u * u, b1 = 3 * u * u * t, b2 = 3 * u * t * t, b3 = t * t * t;
        out[0] = b0 * c[0] + b1 * c[2] + b2 * c[4] + b3 * c[6];
        out[1] = b0 * c[1] + b1 * c[3] + b2 * c[5] + b3 * c[7];
    }

    static void bezDerivative(float[] c, float t, float[] out) {
        float u = 1 - t;
        out[0] = 3 * (u * u * (c[2] - c[0]) + 2 * u * t * (c[4] - c[2]) + t * t * (c[6] - c[4]));
        out[1] = 3 * (u * u * (c[3] - c[1]) + 2 * u * t * (c[5] - c[3]) + t * t * (c[7] - c[5]));
    }

    private static float minRadius(float[] c) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i <= 64; i++) {
            float t = i / 64F, u = 1 - t;
            float dx = 3 * (u * u * (c[2] - c[0]) + 2 * u * t * (c[4] - c[2]) + t * t * (c[6] - c[4]));
            float dz = 3 * (u * u * (c[3] - c[1]) + 2 * u * t * (c[5] - c[3]) + t * t * (c[7] - c[5]));
            float ddx = 6 * (u * (c[4] - 2 * c[2] + c[0]) + t * (c[6] - 2 * c[4] + c[2]));
            float ddz = 6 * (u * (c[5] - 2 * c[3] + c[1]) + t * (c[7] - 2 * c[5] + c[3]));
            float cross = Math.abs(dx * ddz - dz * ddx);
            float sp = (float) Math.pow(Math.hypot(dx, dz), 3);
            if (cross > 1e-6F) {
                min = Math.min(min, sp / cross);
            }
        }
        return min == Float.MAX_VALUE ? 1000 : min;
    }

    /** Coupe une Bezier au parametre t (de Casteljau) : moities [0, t] et [t, 1]. */
    static float[][] split(float[] c, float t) {
        float[] p01 = lerp(c, 0, 2, t), p12 = lerp(c, 2, 4, t), p23 = lerp(c, 4, 6, t);
        float[] q0 = {p01[0] + (p12[0] - p01[0]) * t, p01[1] + (p12[1] - p01[1]) * t};
        float[] q1 = {p12[0] + (p23[0] - p12[0]) * t, p12[1] + (p23[1] - p12[1]) * t};
        float[] m = {q0[0] + (q1[0] - q0[0]) * t, q0[1] + (q1[1] - q0[1]) * t};
        float[] left = {c[0], c[1], p01[0], p01[1], q0[0], q0[1], m[0], m[1]};
        float[] right = {m[0], m[1], q1[0], q1[1], p23[0], p23[1], c[6], c[7]};
        return new float[][]{left, right};
    }

    private static float[] lerp(float[] c, int i, int j, float t) {
        return new float[]{c[i] + (c[j] - c[i]) * t, c[i + 1] + (c[j + 1] - c[i + 1]) * t};
    }

    /**
     * Raccord droite -> diagonale de reference (repere du bloc A, celui de la droite) : on entre par le milieu de la face sud en allant vers
     * le nord, on sort par le milieu de la face est du bloc au nord (z de -16 a 0) en allant vers le nord-est. Rayon mini ~ 25 px.
     */
    static final float[] EASE = {8, 16, 8, 16 - 6.25F, 16 - 11.25F / (float) Math.sqrt(2), -8 + 11.25F / (float) Math.sqrt(2), 16, -8};
    /** Parametre ou le raccord passe du bloc A au bloc B (z = 0). */
    static final float EASE_SPLIT;

    static {
        float lo = 0, hi = 1;
        float[] p = new float[2];
        for (int i = 0; i < 60; i++) {
            float m = (lo + hi) / 2;
            bez(EASE, m, p);
            if (p[1] > 0) {
                lo = m;
            } else {
                hi = m;
            }
        }
        EASE_SPLIT = (lo + hi) / 2;
        bez(EASE, EASE_SPLIT, p);
        LANE = p[0] - 8;
    }
}
