package fr.pioneers.logistics.pneumatic.network;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.39 : courbe d'un hypertube (demande de Guillaume : « le tube doit etre courbe, arrondi, et etre une entite, plus des blocs ») : Bezier
 * cubique de A a B, comme les voies de Create. Les poignees partent de chaque bout dans sa direction imposee (face d'une station, ancrage, suite
 * d'un tube deja pose) ; longueur des poignees : {@link #handle}. Echantillonnage regulier en longueur d'arc (pas {@link #STEP}) : rendu,
 * verification des obstacles et trajet des cabines utilisent les memes points. Code commun client / serveur.
 * <p>
 * 0.20.43 (Guillaume : « moins severe, qu'il se deforme un peu pour suivre la forme d'un obstacle ») : a la Bezier s'ajoute une deformation
 * douce {@code dev} : trois modes {@code b_k(t) = sin(k pi t) sin(pi t)} (nuls aux bouts, de pente nulle aux bouts : le tube sort toujours
 * de l'ancrage dans son axe), chacun pondere par un vecteur. {@link #fit} cherche les vecteurs qui ecartent le tube des obstacles,
 * {@value #MAX_DEVIATION} blocs au plus.
 */
public final class HypertubeCurve {

    /** Longueur maximale d'un troncon (blocs), rayon de courbure minimal (blocs), rayon du tube (blocs), pas d'echantillonnage. */
    public static final double MAX_LENGTH = 96, MIN_RADIUS = 0.9, RADIUS = 0.47, STEP = 0.25;
    /** Ecart maximal de la deformation (blocs) et pas des iterations d'ajustement. */
    public static final double MAX_DEVIATION = 3.0;
    private static final int FIT_ITERATIONS = 24;
    static boolean DEBUG = Boolean.getBoolean("pioneers.hyperDebug");
    /** Points ignores pres des bouts (ancrages, stations : le tube y entre). */
    private static final double END_SKIP = 0.6;

    public final Vec3 a, b, ta, tb;
    private final Vec3 p1, p2;
    private final Vec3[] dev;
    private final List<Vec3> points;
    /** Parametre de Bezier de chaque point regulier. */
    private final double[] params;
    private final double length;

    /** Courbe de {@code a} a {@code b} ; {@code ta}, {@code tb} : tangentes unitaires dans le sens A vers B. */
    public HypertubeCurve(Vec3 a, Vec3 b, Vec3 ta, Vec3 tb) {
        this(a, b, ta, tb, null);
    }

    /** Courbe deformee ({@code dev} : 3 vecteurs, ou null). */
    public HypertubeCurve(Vec3 a, Vec3 b, Vec3 ta, Vec3 tb, @Nullable Vec3[] dev) {
        this.a = a;
        this.b = b;
        this.ta = ta;
        this.tb = tb;
        this.dev = dev == null ? new Vec3[]{Vec3.ZERO, Vec3.ZERO, Vec3.ZERO} : dev.clone();
        double d = a.distanceTo(b);
        double h = d * handle(ta, tb);
        p1 = a.add(ta.scale(h));
        p2 = b.subtract(tb.scale(h));
        //Longueur et points reguliers : polyligne fine puis reechantillonnage
        int fine = Math.max(16, (int) Math.ceil((d + 2 * maxDev()) * 24));
        List<Vec3> dense = new ArrayList<>(fine + 1);
        double[] cum = new double[fine + 1];
        dense.add(a);
        for (int i = 1; i <= fine; i++) {
            Vec3 p = at((double) i / fine);
            cum[i] = cum[i - 1] + p.distanceTo(dense.get(i - 1));
            dense.add(p);
        }
        length = cum[fine];
        int n = Math.max(1, (int) Math.ceil(length / STEP));
        points = new ArrayList<>(n + 1);
        params = new double[n + 1];
        int j = 0;
        for (int k = 0; k <= n; k++) {
            double s = length * k / n;
            while (j + 1 < fine && cum[j + 1] < s) {
                j++;
            }
            double span = cum[j + 1] - cum[j];
            double f = span <= 1e-9 ? 0 : (s - cum[j]) / span;
            points.add(span <= 1e-9 ? dense.get(j) : dense.get(j).lerp(dense.get(j + 1), f));
            params[k] = (j + f) / fine;
        }
    }

    private double maxDev() {
        return dev[0].length() + dev[1].length() + dev[2].length();
    }

    /** Courbe entre deux ancres (directions imposees ; un bout libre prend la direction d'un arc de cercle, ou la corde). */
    public static HypertubeCurve of(HypertubeAnchor start, HypertubeAnchor end) {
        Vec3[] t = tangents(start, end);
        return new HypertubeCurve(start.pos(), end.pos(), t[0], t[1]);
    }

    /** Tangentes (sens A vers B) aux deux bouts, directions des ancrages resolues vers l'autre bout. */
    public static Vec3[] tangents(HypertubeAnchor start, HypertubeAnchor end) {
        Vec3 chord = end.pos().subtract(start.pos());
        Vec3 dir = chord.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : chord.normalize();
        Vec3 sOut = start.resolvedOut(end.pos()), eOut = end.resolvedOut(start.pos());
        Vec3 ta, tb;
        if (sOut != null && eOut != null) {
            ta = sOut;
            tb = eOut.scale(-1);
        } else if (sOut != null) {
            ta = sOut;
            tb = dir.scale(2 * ta.dot(dir)).subtract(ta).normalize();          //arc de cercle : symetrique de ta par rapport a la corde
        } else if (eOut != null) {
            tb = eOut.scale(-1);
            ta = dir.scale(2 * tb.dot(dir)).subtract(tb).normalize();
        } else {
            ta = tb = dir;
        }
        return new Vec3[]{ta, tb};
    }

    /**
     * Longueur des poignees rapportee a la corde : celle d'un arc de cercle qui tourne de l'angle entre les deux tangentes (un tiers en ligne
     * droite ou en S, deux tiers pour un demi-tour) : les virages restent ronds au lieu de se pincer.
     */
    static double handle(Vec3 ta, Vec3 tb) {
        double theta = Math.acos(Math.max(-1, Math.min(1, ta.dot(tb))));
        if (theta < 1e-3) {
            return 1.0 / 3;
        }
        return 2.0 / 3 * Math.tan(theta / 4) / Math.sin(theta / 2);
    }

    /** Modes de deformation au parametre t (nuls et de pente nulle aux deux bouts). */
    static double mode(int k, double t) {
        double s = Math.sin(Math.PI * t);
        return Math.sin((k + 1) * Math.PI * t) * s;
    }

    private Vec3 at(double t) {
        double u = 1 - t;
        Vec3 p = a.scale(u * u * u).add(p1.scale(3 * u * u * t)).add(p2.scale(3 * u * t * t)).add(b.scale(t * t * t));
        for (int k = 0; k < 3; k++) {
            if (dev[k] != Vec3.ZERO) {
                p = p.add(dev[k].scale(mode(k, t)));
            }
        }
        return p;
    }

    public double length() {
        return length;
    }

    /** Vecteurs de deformation (copie). */
    public Vec3[] deviation() {
        return dev.clone();
    }

    public boolean deformed() {
        return maxDev() > 1e-6;
    }

    /** Points reguliers (pas {@link #STEP}), de A a B compris. */
    public List<Vec3> points() {
        return points;
    }

    /** Tangente unitaire (sens A vers B) au point regulier {@code i}. */
    public Vec3 tangent(int i) {
        Vec3 t = points.get(Math.min(points.size() - 1, i + 1)).subtract(points.get(Math.max(0, i - 1)));
        return t.lengthSqr() < 1e-12 ? ta : t.normalize();
    }

    /** Tubes consommes : un par bloc de longueur (arrondi au-dessus). */
    public int tubes() {
        return Math.max(1, (int) Math.ceil(length));
    }

    /** Plus petit rayon de courbure (blocs) mesure sur les points (cercle par trois points espaces d'un bloc). */
    public double minRadius() {
        int k = Math.max(1, (int) Math.round(1 / STEP));
        double min = Double.MAX_VALUE;
        for (int i = k; i + k < points.size(); i++) {
            Vec3 p = points.get(i - k), q = points.get(i), r = points.get(i + k);
            double ab = p.distanceTo(q), bc = q.distanceTo(r), ca = r.distanceTo(p);
            double cross = q.subtract(p).cross(r.subtract(p)).length();
            if (cross < 1e-9) {
                continue;
            }
            min = Math.min(min, ab * bc * ca / (2 * cross));
        }
        return min;
    }

    // ------------------------------------------------------------------
    // Obstacles et deformation
    // ------------------------------------------------------------------

    /** Point du tube qui heurte un bloc : indice du point, bloc, poussee (unitaire, perpendiculaire au tube, loin du bloc). */
    public record Hit(int index, BlockPos block, Vec3 push) {
    }

    /** Ce qui gene le tube, case par case (le monde, ou un monde d'essai pour le banc). */
    @FunctionalInterface
    public interface Solid {

        boolean at(BlockPos pos);

        static Solid of(BlockGetter level) {
            return pos -> blocks(level, pos);
        }
    }

    private static boolean blocks(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PneumaticTubeBlock) {
            return false;
        }
        if (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /** Repere (n, b) perpendiculaire a la tangente {@code t} (n vers le haut si possible). */
    private static Vec3[] normals(Vec3 t) {
        Vec3 n = Math.abs(t.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).subtract(t.scale(t.y)).normalize();
        return new Vec3[]{n, t.cross(n)};
    }

    /** Le tube tient-il a ce point (axe et anneau de sondes libres) ? */
    private static boolean free(Solid level, Vec3 p, Vec3 n, Vec3 bn) {
        double r = RADIUS * 0.7;
        for (int q = -1; q < 8; q++) {
            Vec3 o = q < 0 ? Vec3.ZERO : n.scale(Math.cos(q * Math.PI / 4) * r).add(bn.scale(Math.sin(q * Math.PI / 4) * r));
            if (level.at(BlockPos.containing(p.add(o)))) {
                return false;
            }
        }
        return true;
    }

    /** Tous les points du tube qui heurtent un bloc (corps plein, fluide autre que l'eau), hors des bouts. */
    public List<Hit> hits(Solid level) {
        double r = RADIUS * 0.7;
        List<Hit> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            double s = length * i / Math.max(1, points.size() - 1);
            if (s < END_SKIP || length - s < END_SKIP) {
                continue;
            }
            Vec3 p = points.get(i);
            Vec3 t = tangent(i);
            Vec3[] nb = normals(t);
            for (int q = -1; q < 8; q++) {
                Vec3 o = q < 0 ? Vec3.ZERO : nb[0].scale(Math.cos(q * Math.PI / 4) * r).add(nb[1].scale(Math.sin(q * Math.PI / 4) * r));
                BlockPos pos = BlockPos.containing(p.add(o));
                if (level.at(pos)) {
                    Vec3 away = p.subtract(Vec3.atCenterOf(pos));
                    away = away.subtract(t.scale(away.dot(t)));
                    if (away.lengthSqr() < 1e-6) {
                        away = o.lengthSqr() > 1e-6 ? o.scale(-1) : nb[0];
                    }
                    out.add(new Hit(i, pos, away.normalize()));
                    break;
                }
            }
        }
        return out;
    }

    /** Premier bloc qui gene le passage du tube, ou null. */
    @Nullable
    public BlockPos obstacle(Solid level) {
        List<Hit> h = hits(level);
        return h.isEmpty() ? null : h.get(h.size() / 2).block();
    }

    /** Resultat d'un ajustement : la courbe retenue et le premier obstacle restant (null : libre). */
    public record Fit(HypertubeCurve curve, @Nullable BlockPos obstacle) {
    }

    /**
     * Direction d'echappee commune aux points qui heurtent : parmi 16 directions du plan perpendiculaire au tube (au milieu des chocs), celle
     * qui libere tous les points avec le plus petit deplacement (par quart de bloc, {@code cap} + 1 au plus) ; a egalite, plutot par-dessus.
     * Rend les deplacements par point, ou null si aucune direction ne libere tout.
     */
    @Nullable
    private Vec3[] escape(Solid level, List<Hit> hits, double cap) {
        Hit mid = hits.get(hits.size() / 2);
        Vec3[] nb = normals(tangent(mid.index()));
        Vec3[] best = null;
        double bestCost = Double.MAX_VALUE;
        for (int q = 0; q < 16; q++) {
            Vec3 dir = nb[0].scale(Math.cos(q * Math.PI / 8)).add(nb[1].scale(Math.sin(q * Math.PI / 8)));
            Vec3[] moves = new Vec3[hits.size()];
            double worst = 0;
            boolean ok = true;
            for (int h = 0; h < hits.size() && ok; h++) {
                Hit hit = hits.get(h);
                Vec3 p = points.get(hit.index());
                Vec3[] hn = normals(tangent(hit.index()));
                ok = false;
                for (double d = 0.25; d <= cap + 1.0; d += 0.25) {
                    if (free(level, p.add(dir.scale(d)), hn[0], hn[1])) {
                        moves[h] = dir.scale(d + 0.12);
                        worst = Math.max(worst, d);
                        ok = true;
                        break;
                    }
                }
            }
            double cost = worst - 0.15 * dir.y;
            if (ok && cost < bestCost) {
                bestCost = cost;
                best = moves;
            }
        }
        return best;
    }

    /** Deplacement de la deformation au parametre t. */
    private Vec3 displacement(double t) {
        Vec3 d = Vec3.ZERO;
        for (int k = 0; k < 3; k++) {
            d = d.add(dev[k].scale(mode(k, t)));
        }
        return d;
    }

    /**
     * Courbe de A a B qui s'ecarte doucement des obstacles : a chaque iteration, les points qui heurtent choisissent une direction d'echappee
     * commune ({@link #escape}) ; les trois modes sont ajustes aux moindres carres pour que la deformation y atteigne ces deplacements
     * (modes d'ordre eleve penalises : le tube reste souple et rond). Ecart borne par {@link #MAX_DEVIATION} et un quart de la corde. Rend la
     * premiere courbe libre et assez ronde, sinon la Bezier d'origine et son obstacle.
     */
    public static Fit fit(BlockGetter level, Vec3 a, Vec3 b, Vec3 ta, Vec3 tb) {
        return fit(Solid.of(level), a, b, ta, tb);
    }

    public static Fit fit(Solid level, Vec3 a, Vec3 b, Vec3 ta, Vec3 tb) {
        HypertubeCurve base = new HypertubeCurve(a, b, ta, tb);
        List<Hit> hits = base.hits(level);
        if (hits.isEmpty()) {
            return new Fit(base, null);
        }
        double cap = Math.min(MAX_DEVIATION, 0.25 * a.distanceTo(b) + 0.5);
        double[] lambda = {0.02, 0.25, 0.5};
        HypertubeCurve c = base;
        for (int it = 0; it < FIT_ITERATIONS; it++) {
            Vec3[] moves = c.escape(level, hits, cap);
            if (moves == null) {
                break;
            }
            //Moindres carres : sum_h |sum_k c_k b_k(t_h) - e_h|^2 + sum_k lambda_k |c_k|^2 (meme matrice pour x, y, z)
            double[][] m = new double[3][3];
            Vec3[] rhs = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
            for (int h = 0; h < hits.size(); h++) {
                double t = c.params[hits.get(h).index()];
                Vec3 target = c.displacement(t).add(moves[h]);
                double[] w = {mode(0, t), mode(1, t), mode(2, t)};
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        m[i][j] += w[i] * w[j];
                    }
                    rhs[i] = rhs[i].add(target.scale(w[i]));
                }
            }
            //Les points deja libres gardent a peu pres leur place
            for (int k = 1; k < 10; k++) {
                double t = k / 10.0;
                double[] w = {mode(0, t), mode(1, t), mode(2, t)};
                Vec3 keep = c.displacement(t);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        m[i][j] += 0.05 * w[i] * w[j];
                    }
                    rhs[i] = rhs[i].add(keep.scale(0.05 * w[i]));
                }
            }
            for (int k = 0; k < 3; k++) {
                m[k][k] += lambda[k];
            }
            Vec3[] dev = solve(m, rhs);
            if (dev == null) {
                break;
            }
            for (int k = 0; k < 3; k++) {
                double len = dev[k].length();
                dev[k] = len > cap ? dev[k].scale(cap / len) : dev[k];
            }
            c = new HypertubeCurve(a, b, ta, tb, dev);
            hits = c.hits(level);
            if (DEBUG) {
                Pioneers.LOGGER.debug("fit " + it + " dev=" + Arrays.toString(dev) + " hits=" + hits.size() + " r=" + c.minRadius());
            }
            if (hits.isEmpty()) {
                return c.minRadius() >= MIN_RADIUS ? new Fit(c, null) : new Fit(base, base.obstacle(level));
            }
        }
        return new Fit(base, base.obstacle(level));
    }

    /** Systeme 3 x 3 (Cramer), second membre vectoriel ; null si singulier. */
    @Nullable
    private static Vec3[] solve(double[][] m, Vec3[] r) {
        double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                     + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        if (Math.abs(det) < 1e-12) {
            return null;
        }
        Vec3[] out = new Vec3[3];
        for (int k = 0; k < 3; k++) {
            double[][] mk = {m[0].clone(), m[1].clone(), m[2].clone()};
            double[] col = new double[3];
            Vec3 x = Vec3.ZERO;
            for (int axis = 0; axis < 3; axis++) {
                for (int i = 0; i < 3; i++) {
                    col[i] = axis == 0 ? r[i].x : axis == 1 ? r[i].y : r[i].z;
                }
                for (int i = 0; i < 3; i++) {
                    mk[i][k] = col[i];
                }
                double dk = mk[0][0] * (mk[1][1] * mk[2][2] - mk[1][2] * mk[2][1]) - mk[0][1] * (mk[1][0] * mk[2][2] - mk[1][2] * mk[2][0])
                            + mk[0][2] * (mk[1][0] * mk[2][1] - mk[1][1] * mk[2][0]);
                double v = dk / det;
                x = axis == 0 ? new Vec3(v, x.y, x.z) : axis == 1 ? new Vec3(x.x, v, x.z) : new Vec3(x.x, x.y, v);
            }
            out[k] = x;
        }
        return out;
    }

    /** Distance du point {@code p} a la courbe (au point echantillonne le plus proche). */
    public double distanceTo(Vec3 p) {
        double best = Double.MAX_VALUE;
        for (Vec3 q : points) {
            best = Math.min(best, q.distanceToSqr(p));
        }
        return Math.sqrt(best);
    }

    /** Distance minimale entre la courbe et un rayon (origine, direction unitaire, longueur) ; parametre du rayon dans {@code hit[0]}. */
    public double rayDistance(Vec3 origin, Vec3 dir, double reach, double[] hit) {
        double best = Double.MAX_VALUE;
        for (Vec3 q : points) {
            double t = Math.max(0, Math.min(reach, q.subtract(origin).dot(dir)));
            double d = origin.add(dir.scale(t)).distanceTo(q);
            if (d < best) {
                best = d;
                hit[0] = t;
            }
        }
        return best;
    }
}
