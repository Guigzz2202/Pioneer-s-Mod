package fr.pioneers.farm.client.aquarium;

/**
 * 1.0.5-beta (lot 14, Guillaume : « Aquarium : meilleure animation des poissons ») puis (lot 25, Guillaume : « Les poissons dans l'aquarium
 * nagent avec beaucoup de peine ») : NAGE des poissons de l'aquarium, pure (aucune classe de Minecraft ; banc
 * {@code tools/aquarium/swim_bench.sh}). Tout derive du temps de l'horloge de l'aquarium ({@code AquariumClock} : ticks du client + fraction
 * de l'image) et de la graine de l'habitant : rien a sauver ni a synchroniser.
 * <p>
 * Vraies causes de la « peine » (lot 25, mesurees par le banc) :
 * <ol>
 *     <li>la boucle du lot 14 etait une ellipse parcourue a CAP constant (parametre = direction de la normale) : au bout d'un bassin long et
 *     etroit le rayon de courbure s'effondre, le poisson s'y arretait presque (jusqu'a 156 ticks a moins de 0,015 px / tick) et pivotait sur
 *     place ;</li>
 *     <li>pendant ce temps la QUEUE battait au rythme du temps propre ({@code tau x 1,3} rad / tick, ~2,5 battements / s) : un poisson qui
 *     bat de la queue sans avancer « nage avec peine » ;</li>
 *     <li>le temps etait {@code level.getGameTime()} : sur le client, il est remis a l'heure du serveur toutes les 20 ticks
 *     ({@code ClientPacketListener.handleSetTime}) ; un serveur a 15-18 ticks / s (mods lourds, sauvegardes) faisait reculer tous les
 *     poissons d'un coup chaque seconde.</li>
 * </ol>
 * Nage du lot 25 :
 * <ul>
 *     <li>VOLUME : la case du poisson, etendue d'une case pleine voisine par axe ({@link #cells}) seulement si toutes les cases du pave sont
 *     pleines d'eau ; le centre reste dans {@link #box} (verre a {@link #MARGIN} + demi-longueur, fond + sol + {@link #FLOOR_CLEAR}, surface
 *     - {@link #TOP_CLEAR}, plus la part du corps penche {@link #MAX_PITCH}) ;</li>
 *     <li>TRAJET : une boucle a coins arrondis propre a chaque poisson (centre, demi-axes, rayon des coins, sens tires de la graine),
 *     parcourue a VITESSE CONSTANTE LE LONG DE LA COURBE (abscisse curviligne : jamais d'arret, jamais de pivot sur
 *     place), legere ondulation laterale, montees et descentes lentes ; vitesse de croisiere selon la longueur du corps ({@link #cruise}),
 *     bornee par le rayon des coins (virage au plus {@link #TURN} rad / tick), petites accelerations ({@link #tau}) ;</li>
 *     <li>DECOR : chaque element compose ({@link Obstacle}) souleve le poisson par un relief lisse (plateau sur l'emprise de l'element + la
 *     demi-longueur du corps, raccord sur {@link #LIFT_BAND} px, maximum doux) : le corps ne traverse jamais le decor ;</li>
 *     <li>CAP : direction de la vitesse ({@link #pose} : differences sur +/- {@link #HEADING_SPAN} tick), tangage borne ;</li>
 *     <li>QUEUE : phase = distance parcourue / ({@link #STRIDE} longueur du corps) : un battement pour 0,6 longueur parcourue, amplitude qui
 *     suit l'acceleration ; un poisson ne bat jamais de la queue sans avancer.</li>
 * </ul>
 */
public final class AquariumSwim {

    private AquariumSwim() {
    }

    /** Ecart du corps au verre (px), marge au-dessus du sol / sous la surface (px), tangage maximal (rad), demi-fenetre du cap (ticks). */
    public static final float MARGIN = 2.5F, FLOOR_CLEAR = 1.0F, TOP_CLEAR = 1.5F, MAX_PITCH = 0.45F, HEADING_SPAN = 1.5F;
    /** Vitesse de croisiere : px / tick par px de corps, bornes ; accelerations (+/- BURST) ; virage maximal (rad / tick a la croisiere). */
    public static final float CRUISE_PER_PX = 0.035F, CRUISE_MIN = 0.10F, CRUISE_MAX = 0.28F, BURST = 0.4F, TURN = 0.16F;
    /** Longueurs de corps parcourues par battement de queue ; relief du decor : marge au-dessus (px), largeur du raccord (px), douceur. */
    public static final float STRIDE = 0.6F, DECOR_CLEAR = 0.5F, LIFT_BAND = 6.0F, SMOOTH = 1.0F;
    /** Demi-fenetre du tangage (ticks). */
    public static final float PITCH_SPAN = 8F;
    /** Ondulation laterale (px) au plus. */
    public static final float WOBBLE = 0.5F;

    /** Graine -> nombre dans [0, 1) (deterministe). */
    public static float rnd(int seed, int k) {
        int h = seed * 0x9E3779B1 + k * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Case pleine du meme bassin en (dx, dy, dz) (0, 0, 0 = la case du poisson). */
    public interface Wet {

        boolean at(int dx, int dy, int dz);
    }

    /**
     * Element du decor vu par un poisson : centre {@code x, z} (px, repere de la case du poisson), rayon d'emprise {@code r} (px), sommet
     * {@code top} (px, meme repere : negatif = sous la case).
     */
    public record Obstacle(float x, float z, float r, float top) {
    }

    public static final Obstacle[] NONE = new Obstacle[0];

    /**
     * Pave de cases {x0, x1, y0, y1, z0, z1} (en cases, relatives : -1..1) ou nage le poisson : extension d'une case par direction (x d'abord,
     * puis z, puis y), gardee seulement si toutes les cases du pave sont pleines.
     */
    public static int[] cells(Wet wet) {
        int[] c = {0, 0, 0, 0, 0, 0};
        int[][] tries = {{0, -1}, {1, 1}, {4, -1}, {5, 1}, {2, -1}, {3, 1}};
        for (int[] t : tries) {
            int[] n = c.clone();
            n[t[0]] = t[1];
            if (all(wet, n)) {
                c = n;
            }
        }
        return c;
    }

    static boolean all(Wet wet, int[] c) {
        for (int x = c[0]; x <= c[1]; x++) {
            for (int y = c[2]; y <= c[3]; y++) {
                for (int z = c[4]; z <= c[5]; z++) {
                    if (!wet.at(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Part verticale du corps penche au tangage maximal (px) pour une demi-longueur {@code half}. */
    public static float tilt(float half) {
        return half * (float) Math.sin(MAX_PITCH);
    }

    /**
     * Boite des CENTRES du poisson {x0, x1, y0, y1, z0, z1} (px, repere de sa case) : pave de cases {@code cells}, eau retiree de {@code gap}
     * contre le verre, {@link #MARGIN} + {@code half} (demi-longueur du corps) a l'horizontale ; en bas : {@code floor} (dessus du sol de la case
     * la plus basse) + {@link #FLOOR_CLEAR} + {@code halfH} + corps penche ; en haut : {@code top} (surface de la case la plus haute) -
     * {@link #TOP_CLEAR} - {@code halfH} - corps penche - {@link #SMOOTH} / 2 (place du maximum doux du relief).
     */
    public static float[] box(int[] cells, float gap, float floor, float top, float half, float halfH) {
        float m = gap + MARGIN + half, pa = tilt(half);
        float x0 = cells[0] * 16 + m, x1 = (cells[1] + 1) * 16 - m;
        float z0 = cells[4] * 16 + m, z1 = (cells[5] + 1) * 16 - m;
        float y0 = cells[2] * 16 + floor + FLOOR_CLEAR + halfH + pa, y1 = cells[3] * 16 + top - TOP_CLEAR - halfH - pa - SMOOTH / 2;
        if (x1 < x0) {
            x0 = x1 = (x0 + x1) / 2;
        }
        if (z1 < z0) {
            z0 = z1 = (z0 + z1) / 2;
        }
        if (y1 < y0) {
            y0 = y1 = (y0 + y1) / 2;
        }
        return new float[]{x0, x1, y0, y1, z0, z1};
    }

    /**
     * Echelle du modele (0..1) d'un poisson de {@code length} x {@code height} blocs (taille comprise) dans ce pave : longueur visible au plus
     * {@code maxLength} blocs et {@link #SPAN_SHARE} du plus petit cote horizontal libre ; hauteur (corps penche compris) dans l'eau libre au-dessus du sol
     * ET au-dessus du plus haut element du decor ({@code decorTop}, px, repere de la case du poisson ; {@code floor} s'il n'y en a pas) : un
     * gros poisson est montre plus petit dans un petit bassin ou un bassin tres garni, jamais coince ni dans le decor.
     */
    /**
     * 1.0.5-beta (lot 28) : part du plus petit cote libre que la longueur VISIBLE peut prendre. Avant : 0,55, appliquee a la largeur de la
     * boite de l'entite ({@code FishDef.width} = 0,55 x longueur) prise pour la longueur : le poisson visible occupait tout le cote (nez dans
     * le verre a la boucle) et un silure s'y montrait a 1,6 bloc pour {@code AquariumRenderer.MAX_LENGTH} x 1,6 = 0,88 annonce.
     */
    public static final float SPAN_SHARE = 0.62F;

    public static float fit(float length, float height, int[] cells, float gap, float floor, float top, float maxLength, float decorTop) {
        float span = Math.min(cells[1] - cells[0] + 1, cells[5] - cells[4] + 1) * 16F - 2 * (gap + MARGIN);
        float maxLen = Math.min(maxLength * 16F, span * SPAN_SHARE) / 16F;
        float k = Math.min(1F, maxLen / Math.max(0.05F, length));
        float surface = cells[3] * 16F + top - TOP_CLEAR - SMOOTH;
        float low = Math.max(cells[2] * 16F + floor + FLOOR_CLEAR, decorTop + DECOR_CLEAR);
        float room = surface - low;
        float need = 16F * (Math.max(0.05F, height) + 2 * Math.max(0.05F, length) / 2 * (float) Math.sin(MAX_PITCH));
        return Math.max(0.05F, Math.min(k, room * 0.98F / need));
    }

    /** Vitesse de croisiere (px / tick) d'un poisson dont le corps visible mesure {@code lengthPx} px. */
    public static float cruise(float lengthPx) {
        return Math.max(CRUISE_MIN, Math.min(CRUISE_MAX, lengthPx * CRUISE_PER_PX));
    }

    /** Temps propre : t + accelerations douces (derivee entre 1 - BURST et 1 + BURST : jamais d'arret ni de recul). */
    public static double tau(int seed, double t) {
        double w = 0.021 + 0.012 * rnd(seed, 11), w2 = 0.0083 + 0.004 * rnd(seed, 12);
        double a = BURST * 0.7 / w, b = BURST * 0.3 / w2;
        return t + a * Math.sin(w * t + 6.283 * rnd(seed, 13)) + b * Math.sin(w2 * t + 6.283 * rnd(seed, 14));
    }

    /** Derivee du temps propre (1 - BURST .. 1 + BURST) : l'allure du moment. */
    public static double pace(int seed, double t) {
        double w = 0.021 + 0.012 * rnd(seed, 11), w2 = 0.0083 + 0.004 * rnd(seed, 12);
        return 1 + BURST * 0.7 * Math.cos(w * t + 6.283 * rnd(seed, 13)) + BURST * 0.3 * Math.cos(w2 * t + 6.283 * rnd(seed, 14));
    }

    /**
     * Boucle du poisson dans la boite : {cx, cz, A, B, r, ondulation} (px). Demi-axes : presque tout le volume dans un petit bassin, 60 a 100 %
     * dans un grand (graine) ; centre decale dans la place restante ; rayon des coins 85 a 100 % du plus petit demi-axe (bouts en demi-cercle : virages amples) ; ondulation laterale
     * au plus {@link #WOBBLE} et 12 % du rayon des coins (la foulee reste celle de la queue meme dans les virages serres).
     */
    static double[] shape(int seed, float[] box) {
        double ax = (box[1] - box[0]) / 2, az = (box[5] - box[4]) / 2;
        double[] s = loop(seed, box, ax, az, 0);
        double wob = Math.min(WOBBLE, 0.12 * s[4]);
        s = loop(seed, box, ax, az, wob);
        s[5] = wob;
        return s;
    }

    static double[] loop(int seed, float[] box, double ax, double az, double wob) {
        double ex = Math.max(0, ax - wob), ez = Math.max(0, az - wob);
        double big = Math.max(0, Math.min(1, (Math.min(ex, ez) - 3) / 8));
        double fa = 1 - (1 - (0.6 + 0.4 * rnd(seed, 30))) * big - 0.02, fb = 1 - (1 - (0.6 + 0.4 * rnd(seed, 31))) * big - 0.02;
        double a = ex * fa, b = ez * fb;
        double cx = (box[0] + box[1]) / 2 + (ex - a) * (2 * rnd(seed, 32) - 1);
        double cz = (box[4] + box[5]) / 2 + (ez - b) * (2 * rnd(seed, 33) - 1);
        double r = Math.min(a, b) * (0.85 + 0.15 * rnd(seed, 36));
        return new double[]{cx, cz, a, b, r, wob};
    }

    /** Perimetre d'une boucle a coins arrondis de demi-axes a, b, rayon r. */
    static double perimeter(double a, double b, double r) {
        return 4 * (a - r) + 4 * (b - r) + 2 * Math.PI * r;
    }

    /** Point {x, z, nx, nz} (repere du centre ; n = normale sortante) a l'abscisse p (0..perimetre) de la boucle a coins arrondis. */
    static double[] stadium(double a, double b, double r, double p) {
        double sa = a - r, sb = b - r, q = Math.PI * r / 2;
        double[] lens = {2 * sb, q, 2 * sa, q, 2 * sb, q, 2 * sa, q};
        int k = 0;
        while (k < 7 && p > lens[k]) {
            p -= lens[k];
            k++;
        }
        p = Math.min(p, lens[k]);
        double th;
        switch (k) {
            case 0:
                return new double[]{a, -sb + p, 1, 0};
            case 2:
                return new double[]{sa - p, b, 0, 1};
            case 4:
                return new double[]{-a, sb - p, -1, 0};
            case 6:
                return new double[]{-sa + p, -b, 0, -1};
            default:
                break;
        }
        th = (k - 1) / 2 * Math.PI / 2 + (r > 1e-6 ? p / r : 0);
        double ox = k == 1 || k == 7 ? sa : -sa, oz = k == 1 || k == 3 ? sb : -sb;
        return new double[]{ox + r * Math.cos(th), oz + r * Math.sin(th), Math.cos(th), Math.sin(th)};
    }

    /** Vitesse de croisiere effective (px / tick) : celle du corps, bornee par le rayon des coins de la boucle (virage au plus {@link #TURN}). */
    public static float speed(int seed, float[] box, float lengthPx) {
        double[] sh = shape(seed, box);
        return (float) Math.max(0.02, Math.min(cruise(lengthPx), TURN * Math.max(0.05, sh[4])));
    }

    /** Distance parcourue (px) a l'instant t a la vitesse {@code v} (temps propre : petites accelerations). */
    public static double travel(int seed, double t, float v) {
        return v * tau(seed, t);
    }

    /**
     * Position du centre (px) a l'instant t : boucle parcourue a vitesse constante le long de la courbe ({@code travel} / perimetre de base),
     * ondulation laterale, hauteur lente (couloir propre au poisson) soulevee par le relief du decor ({@link #lift}).
     */
    public static double[] at(int seed, double t, float[] box, float v, float half, float halfH, Obstacle[] obs) {
        double s = travel(seed, t, v);
        double[] sh = shape(seed, box);
        double p0 = perimeter(sh[2], sh[3], sh[4]);
        double u = p0 > 1e-6 ? s / p0 : 0;
        u -= Math.floor(u);
        if (rnd(seed, 21) < 0.5) {
            u = 1 - u;
        }
        double[] pt = stadium(sh[2], sh[3], sh[4], u * p0);
        double lam = 18 + 10 * rnd(seed, 37);
        double w = sh[5] * Math.sin(2 * Math.PI * s / lam + 6.283 * rnd(seed, 38));
        double x = sh[0] + pt[0] + pt[2] * w, z = sh[1] + pt[1] + pt[3] * w;
        //hauteur : couloir propre (graine) et deux sinus lents ; jamais au-dela de la boite
        double ay = (box[3] - box[2]) / 2, cy = (box[2] + box[3]) / 2;
        double lane = 0.5 * (2 * rnd(seed, 39) - 1);
        double wy = v / Math.max(4.0, p0 / (2 * Math.PI)) * (0.21 + 0.1 * rnd(seed, 27));
        double sy = lane + 0.5 * (0.7 * Math.sin(wy * tau(seed, t) + 6.283 * rnd(seed, 28)) + 0.3 * Math.sin(2.31 * wy * tau(seed, t) + 6.283 * rnd(seed, 29)));
        double y = cy + ay * Math.max(-1, Math.min(1, sy));
        y = lift(y, x, z, half, halfH, obs);
        return new double[]{x, y, z};
    }

    /**
     * Relief du decor : hauteur minimale du centre au-dessus de chaque element (sommet + {@link #DECOR_CLEAR} + demi-hauteur + corps penche)
     * sur un plateau (emprise + demi-longueur du corps : aucun point du corps au-dessus de l'emprise ne peut etre plus bas), raccord lisse sur
     * {@link #LIFT_BAND} px ; maximum doux (jamais sous le plus grand des deux) avec la hauteur voulue.
     */
    public static double lift(double y, double x, double z, float half, float halfH, Obstacle[] obs) {
        //plancher de base sous la hauteur voulue (meme loin du decor : aucune marche quand un element entre dans le rayon)
        double req = y - SMOOTH;
        for (Obstacle o : obs) {
            double d = Math.sqrt((x - o.x()) * (x - o.x()) + (z - o.z()) * (z - o.z()));
            double plateau = o.r() + half + 0.3, need = o.top() + DECOR_CLEAR + halfH + tilt(half) + SMOOTH / 2;
            //raccord d'autant plus long que la montee est haute (pente <= ~1 : le poisson monte en nageant, sans etre aspire)
            double band = Math.max(LIFT_BAND, 1.2 * (need - y));
            double k = d <= plateau ? 1 : d >= plateau + band ? 0 : smooth(1 - (d - plateau) / band);
            if (k > 0) {
                req = Math.max(req, need * k + (y - SMOOTH) * (1 - k));
            }
        }
        return (y + req + Math.sqrt((y - req) * (y - req) + SMOOTH * SMOOTH)) / 2;
    }

    static double smooth(double k) {
        return k * k * (3 - 2 * k);
    }

    /** Pose d'un poisson : centre (px), cap et tangage (rad), phase de la queue (age du modele), amplitude, allure. */
    public record Pose(double x, double y, double z, float yaw, float pitch, float tail, float amp, float pace) {
    }

    /**
     * Pose complete a l'instant t : centre ({@link #at}), cap = direction de la vitesse (differences sur +/- {@link #HEADING_SPAN} tick),
     * tangage = pente moyenne sur +/- {@link #PITCH_SPAN} ticks, borne a {@link #MAX_PITCH} ; queue : un battement pour {@link #STRIDE} longueur parcourue ({@code lengthPx} : corps visible),
     * en age du modele ({@code FishModel.swim} : sin(0,6 age)) ; amplitude 0,81 a 1,09 selon l'allure.
     */
    public static Pose pose(int seed, double t, float[] box, float v, float half, float halfH, float lengthPx, Obstacle[] obs) {
        double[] p = at(seed, t, box, v, half, halfH, obs);
        double[] a = at(seed, t - HEADING_SPAN, box, v, half, halfH, obs), b = at(seed, t + HEADING_SPAN, box, v, half, halfH, obs);
        double vx = b[0] - a[0], vz = b[2] - a[2];
        float yaw = (float) Math.atan2(-vx, -vz);
        //tangage : pente moyenne sur +/- PITCH_SPAN ticks (les montees au-dessus du decor se prennent en douceur)
        double[] pa = at(seed, t - PITCH_SPAN, box, v, half, halfH, obs), pb = at(seed, t + PITCH_SPAN, box, v, half, halfH, obs);
        double h = Math.hypot(pb[0] - pa[0], pb[2] - pa[2]) + 1e-4;
        float pitch = (float) Math.max(-MAX_PITCH, Math.min(MAX_PITCH, Math.atan2(pb[1] - pa[1], h)));
        double beats = travel(seed, t, v) / (STRIDE * Math.max(1.0, lengthPx));
        float tail = (float) ((2 * Math.PI * beats + 13 * rnd(seed, 7)) / 0.6);
        double pc = pace(seed, t);
        return new Pose(p[0], p[1], p[2], yaw, pitch, tail, (float) (0.6 + 0.35 * pc), (float) pc);
    }
}
