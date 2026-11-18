package fr.pioneers.logistics.naval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : itineraire d'un navire sur la carte marine. A* sur les cases de 2 x 2 blocs (8 directions, sans couper les angles), chaque case
 * du chemin libre sur une marge d'une case (le navire le plus large fait 5 blocs). Puis lissage : on garde les points d'ou l'on voit le
 * suivant en ligne droite. Points en coordonnees de blocs (centres des cases).
 * <p>
 * 0.20.25 (demande de Guillaume : « rester a 20 blocs des cotes sauf pile en face du port d'arrivee ») :
 * <ul>
 *     <li>chaque case coute plus cher pres des cotes ({@link #SHORE} cases = 20 blocs : distance euclidienne relevee par anneaux, gardee en
 *     cache le temps d'un calcul) ; la route reste au large tant qu'il y a de la place ;</li>
 *     <li>sauf dans le <b>chenal d'approche</b> du port d'arrivee ({@link Approach} : bande dans l'axe du bassin, devant lui) et pres du point
 *     de depart (le navire doit pouvoir sortir de la ou il est) ;</li>
 *     <li>pres du depart et de l'arrivee, les passages etroits (sans marge) sont admis, plus chers : un port au fond d'une crique reste
 *     accessible ;</li>
 *     <li>le lissage ne rapproche jamais la route des cotes plus que le chemin trouve ;</li>
 *     <li>A* pondere ({@link #GREED}) : beaucoup moins de cases explorees sur les longues routes.</li>
 * </ul>
 * 0.20.27 : le calcul tourne dans le fil du planificateur ({@link NavalPlanner}) : il lit la carte par son propre
 * {@link WaterChart.Reader}, se borne a une <b>fenetre</b> ({@link Window} : couloir autour de la ligne droite, elargi si besoin) et
 * travaille sur des tables primitives (des centaines de milliers de cases sans saturer la memoire).
 */
public final class ShipPathfinder {

    public static final int MAX_NODES = 250_000;
    /** Distance aux cotes recherchee (cases de 2 blocs : 20 blocs). */
    public static final int SHORE = 10;
    /** Surcout par case au ras des cotes (decroit lineairement jusqu'a {@link #SHORE}). */
    public static final double SHORE_COST = 3.0;
    /** Rayon (cases) autour du depart et de l'arrivee ou les passages sans marge sont admis. */
    public static final int NARROW = 16;
    public static final double GREED = 1.3;
    /** 0.20.27 : rayon (cases) ou l'on cherche l'eau la plus proche du depart et de l'arrivee (32 blocs). */
    public static final int SNAP = 16;

    private ShipPathfinder() {
    }

    /**
     * Chenal d'approche d'un port (en blocs) : depuis {@code (x, z)} (la capitainerie) dans la direction {@code (dx, dz)} (vers le large),
     * sur {@code length} blocs, demi-largeur {@code half} blocs. Les cotes n'y coutent rien.
     */
    public record Approach(double x, double z, double dx, double dz, double length, double half) {

        boolean contains(double bx, double bz) {
            double rx = bx - x, rz = bz - z;
            double along = rx * dx + rz * dz, across = Math.abs(rx * dz - rz * dx);
            return along >= -4 && along <= length && across <= half;
        }
    }

    /**
     * 0.20.27 : fenetre de recherche (en blocs) : les points a moins de {@code margin} blocs de la ligne brisee {@code xz} (x0, z0, x1, z1...).
     * Couloir autour de la ligne droite depart -> arrivee : la recherche ne s'egare pas dans tout l'ocean.
     */
    public static final class Window {

        final double[] xz;
        final double margin;
        final double minX, minZ, maxX, maxZ;

        public Window(double margin, double... xz) {
            this.xz = xz;
            this.margin = margin;
            double a = Double.MAX_VALUE, b = Double.MAX_VALUE, c = -Double.MAX_VALUE, d = -Double.MAX_VALUE;
            for (int i = 0; i + 1 < xz.length; i += 2) {
                a = Math.min(a, xz[i]);
                b = Math.min(b, xz[i + 1]);
                c = Math.max(c, xz[i]);
                d = Math.max(d, xz[i + 1]);
            }
            minX = a - margin;
            minZ = b - margin;
            maxX = c + margin;
            maxZ = d + margin;
        }

        public boolean contains(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && dist2(x, z) <= margin * margin;
        }

        /** Carre de la distance a la ligne brisee. */
        double dist2(double x, double z) {
            if (xz.length == 2) {
                double dx = x - xz[0], dz = z - xz[1];
                return dx * dx + dz * dz;
            }
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 3 < xz.length; i += 2) {
                best = Math.min(best, segDist2(x, z, xz[i], xz[i + 1], xz[i + 2], xz[i + 3]));
            }
            return best;
        }

        /** Chunks qui touchent la fenetre (pour le sondage). */
        public List<long[]> chunks() {
            List<long[]> out = new ArrayList<>();
            int x0 = (int) Math.floor(minX) >> 4, x1 = (int) Math.floor(maxX) >> 4, z0 = (int) Math.floor(minZ) >> 4, z1 = (int) Math.floor(maxZ) >> 4;
            double m = margin + 12;
            for (int cz = z0; cz <= z1; cz++) {
                for (int cx = x0; cx <= x1; cx++) {
                    if (dist2(cx * 16 + 8, cz * 16 + 8) <= m * m) {
                        out.add(new long[]{cx, cz});
                    }
                }
            }
            return out;
        }

        /** Nombre de chunks de la boite englobante (borne rapide, pour ne pas sonder un continent). */
        public long area() {
            return (long) ((maxX - minX) / 16 + 1) * (long) ((maxZ - minZ) / 16 + 1);
        }

        static double segDist2(double x, double z, double ax, double az, double bx, double bz) {
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double t = l2 <= 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double px = ax + dx * t - x, pz = az + dz * t - z;
            return px * px + pz * pz;
        }
    }

    /** Itineraire de (sx, sz) a (gx, gz) en blocs ; null si aucun (eaux inconnues, terre). */
    @Nullable
    public static List<double[]> find(WaterChart chart, double sx, double sz, double gx, double gz, boolean heatproof) {
        return find(chart, sx, sz, gx, gz, heatproof, null);
    }

    @Nullable
    public static List<double[]> find(WaterChart chart, double sx, double sz, double gx, double gz, boolean heatproof, @Nullable Approach approach) {
        return find(chart.reader(), sx, sz, gx, gz, heatproof, approach, null, MAX_NODES);
    }

    /** 0.20.27 : calcul complet, borne a {@code window} (null : partout) et a {@code maxNodes} cases developpees. */
    @Nullable
    public static List<double[]> find(WaterChart.Reader chart, double sx, double sz, double gx, double gz, boolean heatproof, @Nullable Approach approach,
          @Nullable Window window, int maxNodes) {
        Search q = new Search(chart, heatproof, approach, window);
        int[] s = q.nearest(WaterChart.cell(sx), WaterChart.cell(sz));
        int[] g = q.nearest(WaterChart.cell(gx), WaterChart.cell(gz));
        if (s == null || g == null) {
            return null;
        }
        q.start = s;
        q.goal = g;
        LongIntMap index = new LongIntMap(1 << 14);
        FloatList cost = new FloatList();
        LongList from = new LongList();
        Heap open = new Heap();
        long start = key(s[0], s[1]), goal = key(g[0], g[1]);
        index.put(start, cost.add(0));
        from.add(start);
        open.push(start, (float) (GREED * h(s[0], s[1], g[0], g[1])));
        int expanded = 0;
        boolean found = start == goal;
        while (open.size > 0 && !found && expanded < maxNodes) {
            long k = open.topKey();
            float f = open.topF();
            open.pop();
            int nx0 = (int) (k >> 32), nz0 = (int) k;
            double c = cost.get(index.get(k, 0));
            if (f - GREED * h(nx0, nz0, g[0], g[1]) > c + 1e-3) {
                continue;
            }
            expanded++;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = nx0 + dx, nz = nz0 + dz;
                    if (!q.passable(nx, nz)) {
                        continue;
                    }
                    if (dx != 0 && dz != 0 && (!q.passable(nx0 + dx, nz0) || !q.passable(nx0, nz0 + dz))) {
                        continue;
                    }
                    double step = (dx != 0 && dz != 0 ? 1.41421356 : 1.0) + q.extra(nx, nz);
                    long nk = key(nx, nz);
                    double nc = c + step;
                    int ni = index.get(nk, -1);
                    if (ni < 0 || nc < cost.get(ni) - 1e-4) {
                        if (ni < 0) {
                            ni = cost.add((float) nc);
                            from.add(k);
                            index.put(nk, ni);
                        } else {
                            cost.set(ni, (float) nc);
                            from.set(ni, k);
                        }
                        if (nk == goal) {
                            found = true;
                        }
                        open.push(nk, (float) (nc + GREED * h(nx, nz, g[0], g[1])));
                    }
                }
            }
        }
        if (!found) {
            return null;
        }
        List<long[]> cells = new ArrayList<>();
        long at = goal;
        for (int guard = 0; guard < 8_000_000; guard++) {
            cells.add(new long[]{(int) (at >> 32), (int) at});
            if (at == start) {
                break;
            }
            int ai = index.get(at, -1);
            if (ai < 0) {
                break;
            }
            at = from.get(ai);
        }
        Collections.reverse(cells);
        //Lissage : du point courant, on saute au plus loin visible en ligne droite, sans s'approcher des cotes plus que le chemin
        List<double[]> out = new ArrayList<>();
        int i = 0;
        out.add(center(cells.get(0)));
        while (i < cells.size() - 1) {
            int best = i + 1;
            int floor = SHORE;
            for (int j = i + 1; j < Math.min(cells.size(), i + 97); j++) {
                floor = Math.min(floor, q.shoreFree(cells.get(j)[0], cells.get(j)[1]));
            }
            for (int j = Math.min(cells.size() - 1, i + 96); j > i + 1; j--) {
                if (q.sight(cells.get(i), cells.get(j), floor)) {
                    best = j;
                    break;
                }
            }
            out.add(center(cells.get(best)));
            i = best;
        }
        out.add(new double[]{gx, gz});
        return out;
    }

    /**
     * 0.20.27 : etendue d'eau navigable (sans marge) autour de {@code (gx, gz)} : remplissage par cases jusqu'a {@code limit}. Rend vrai si
     * l'etendue est FERMEE (moins de {@code limit} cases) et ne contient pas {@code (sx, sz)} : aucune route possible, inutile de chercher.
     */
    public static boolean enclosed(WaterChart.Reader chart, double sx, double sz, double gx, double gz, boolean heatproof, int limit) {
        Search q = new Search(chart, heatproof, null, null);
        int[] g = q.nearest(WaterChart.cell(gx), WaterChart.cell(gz)), s = q.nearest(WaterChart.cell(sx), WaterChart.cell(sz));
        if (g == null || s == null) {
            return false;
        }
        long target = key(s[0], s[1]);
        LongIntMap seen = new LongIntMap(1 << 12);
        LongList todo = new LongList();
        long first = key(g[0], g[1]);
        seen.put(first, 1);
        todo.add(first);
        for (int i = 0; i < todo.n; i++) {
            if (todo.n >= limit) {
                return false;
            }
            long k = todo.get(i);
            if (k == target) {
                return false;
            }
            int x = (int) (k >> 32), z = (int) k;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = z + (d == 2 ? 1 : d == 3 ? -1 : 0);
                long nk = key(nx, nz);
                if (seen.get(nk, 0) == 0 && chart.navigable(nx, nz, heatproof)) {
                    seen.put(nk, 1);
                    todo.add(nk);
                }
            }
        }
        return true;
    }

    /** Longueur d'une route (blocs). */
    public static double length(List<double[]> route) {
        double l = 0;
        for (int i = 1; i < route.size(); i++) {
            l += Math.hypot(route.get(i)[0] - route.get(i - 1)[0], route.get(i)[1] - route.get(i - 1)[1]);
        }
        return l;
    }

    /** Etat d'un calcul : carte, cache des distances aux cotes, depart et arrivee. */
    private static final class Search {

        final WaterChart.Reader chart;
        final boolean heatproof;
        @Nullable
        final Approach approach;
        @Nullable
        final Window window;
        final LongIntMap shore = new LongIntMap(1 << 14), uniform = new LongIntMap(1 << 10);
        int[] start, goal;

        Search(WaterChart.Reader chart, boolean heatproof, @Nullable Approach approach, @Nullable Window window) {
            this.chart = chart;
            this.heatproof = heatproof;
            this.approach = approach;
            this.window = window;
        }

        boolean near(int cx, int cz, @Nullable int[] p) {
            return p != null && Math.max(Math.abs(cx - p[0]), Math.abs(cz - p[1])) <= NARROW;
        }

        /** Case praticable : libre avec marge, ou sans marge pres du depart et de l'arrivee ; dans la fenetre (ou pres des bouts). */
        boolean passable(int cx, int cz) {
            boolean ends = near(cx, cz, start) || near(cx, cz, goal) || start == null;
            if (!ends && window != null && !window.contains(cx * 2 + 1.0, cz * 2 + 1.0)) {
                return false;
            }
            if (chart.clear(cx, cz, 1, heatproof)) {
                return true;
            }
            return ends && chart.clear(cx, cz, 0, heatproof);
        }

        /** Surcout d'une case : passage etroit, proximite des cotes (sauf chenal d'approche et abords du depart). */
        double extra(int cx, int cz) {
            double e = 0;
            if (!chart.clear(cx, cz, 1, heatproof)) {
                e += 4;
            }
            if (exempt(cx, cz)) {
                return e;
            }
            int d = shoreFree(cx, cz);
            return e + SHORE_COST * (SHORE - d) / SHORE;
        }

        boolean exempt(int cx, int cz) {
            if (approach != null && approach.contains(cx * 2 + 1.0, cz * 2 + 1.0)) {
                return true;
            }
            return start != null && Math.max(Math.abs(cx - start[0]), Math.abs(cz - start[1])) <= NARROW / 2;
        }

        /** Distance (cases, au plus {@link #SHORE}) de la case a l'eau non navigable la plus proche : anneaux carres, distance euclidienne exacte. */
        int shoreFree(long cx, long cz) {
            long k = key((int) cx, (int) cz);
            int v = shore.get(k, -1);
            if (v >= 0) {
                return v;
            }
            int y = chart.surface((int) cx, (int) cz);
            if (openSea((int) cx, (int) cz, y)) {
                shore.put(k, SHORE);
                return SHORE;
            }
            double best = SHORE;
            for (int r = 1; r < best; r++) {
                for (int i = -r; i <= r; i++) {
                    for (int side = 0; side < 4; side++) {
                        int x = (int) cx + (side == 0 ? i : side == 1 ? i : side == 2 ? -r : r);
                        int z = (int) cz + (side == 0 ? -r : side == 1 ? r : i);
                        if (side >= 2 && (i == -r || i == r)) {
                            continue;
                        }
                        if (!chart.navigable(x, z, heatproof) || chart.surface(x, z) != y) {
                            best = Math.min(best, Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz)));
                        }
                    }
                }
            }
            int d = (int) Math.floor(best);
            shore.put(k, d);
            return d;
        }

        /** Toutes les feuilles qui touchent le carre de {@link #SHORE} cases autour de la case sont de la pleine mer a la meme hauteur. */
        boolean openSea(int cx, int cz, int y) {
            for (int sx = (cx - SHORE) >> 3; sx <= (cx + SHORE) >> 3; sx++) {
                for (int sz = (cz - SHORE) >> 3; sz <= (cz + SHORE) >> 3; sz++) {
                    long k = key(sx, sz);
                    int u = uniform.get(k, Integer.MAX_VALUE);
                    if (u == Integer.MAX_VALUE) {
                        u = chart.uniformSurface(sx, sz, heatproof);
                        uniform.put(k, u);
                    }
                    if (u != y) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Ligne droite praticable entre deux cases, jamais plus pres des cotes que {@code floor} (hors zones exemptes). */
        boolean sight(long[] a, long[] b, int floor) {
            double dx = b[0] - a[0], dz = b[1] - a[1];
            int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 3);
            for (int k = 1; k < n; k++) {
                double t = k / (double) n;
                int x = (int) Math.round(a[0] + dx * t), z = (int) Math.round(a[1] + dz * t);
                if (!passable(x, z)) {
                    return false;
                }
                if (floor > 1 && !exempt(x, z) && shoreFree(x, z) < floor) {
                    return false;
                }
            }
            return true;
        }

        /** Case praticable la plus proche (rayon {@link #SNAP} cases), ou null. */
        @Nullable
        int[] nearest(int cx, int cz) {
            for (int r = 0; r <= SNAP; r++) {
                int[] best = null;
                double bd = Double.MAX_VALUE;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        //Avec marge d'abord, puis sans marge (crique, bassin)
                        boolean ok = chart.clear(cx + dx, cz + dz, 1, heatproof) || r > 0 && chart.clear(cx + dx, cz + dz, 0, heatproof);
                        if (r == 0) {
                            ok = chart.clear(cx, cz, 0, heatproof);
                        }
                        if (ok) {
                            double d = dx * dx + dz * dz;
                            if (d < bd) {
                                bd = d;
                                best = new int[]{cx + dx, cz + dz};
                            }
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ tables primitives

    /** Table ouverte long -> int (cle vide : {@link Long#MIN_VALUE}). */
    static final class LongIntMap {

        private long[] keys;
        private int[] vals;
        private int size, mask;

        LongIntMap(int capacity) {
            keys = new long[capacity];
            vals = new int[capacity];
            Arrays.fill(keys, Long.MIN_VALUE);
            mask = capacity - 1;
        }

        private static int mix(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32);
        }

        int get(long k, int def) {
            int i = mix(k) & mask;
            while (true) {
                long c = keys[i];
                if (c == Long.MIN_VALUE) {
                    return def;
                }
                if (c == k) {
                    return vals[i];
                }
                i = i + 1 & mask;
            }
        }

        void put(long k, int v) {
            if (size * 2 >= keys.length) {
                grow();
            }
            int i = mix(k) & mask;
            while (true) {
                long c = keys[i];
                if (c == Long.MIN_VALUE) {
                    keys[i] = k;
                    vals[i] = v;
                    size++;
                    return;
                }
                if (c == k) {
                    vals[i] = v;
                    return;
                }
                i = i + 1 & mask;
            }
        }

        private void grow() {
            long[] ok = keys;
            int[] ov = vals;
            keys = new long[ok.length * 2];
            vals = new int[ok.length * 2];
            Arrays.fill(keys, Long.MIN_VALUE);
            mask = keys.length - 1;
            size = 0;
            for (int i = 0; i < ok.length; i++) {
                if (ok[i] != Long.MIN_VALUE) {
                    put(ok[i], ov[i]);
                }
            }
        }
    }

    static final class FloatList {

        float[] a = new float[1 << 12];
        int n;

        int add(float v) {
            if (n == a.length) {
                a = Arrays.copyOf(a, n * 2);
            }
            a[n] = v;
            return n++;
        }

        float get(int i) {
            return a[i];
        }

        void set(int i, float v) {
            a[i] = v;
        }
    }

    static final class LongList {

        long[] a = new long[1 << 12];
        int n;

        void add(long v) {
            if (n == a.length) {
                a = Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }

        long get(int i) {
            return a[i];
        }

        void set(int i, long v) {
            a[i] = v;
        }
    }

    /** Tas binaire (cle, priorite) en tableaux. */
    static final class Heap {

        long[] k = new long[1 << 12];
        float[] f = new float[1 << 12];
        int size;

        void push(long key, float pri) {
            if (size == k.length) {
                k = Arrays.copyOf(k, size * 2);
                f = Arrays.copyOf(f, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (f[p] <= pri) {
                    break;
                }
                k[i] = k[p];
                f[i] = f[p];
                i = p;
            }
            k[i] = key;
            f[i] = pri;
        }

        long topKey() {
            return k[0];
        }

        float topF() {
            return f[0];
        }

        void pop() {
            size--;
            if (size == 0) {
                return;
            }
            long key = k[size];
            float pri = f[size];
            int i = 0;
            while (true) {
                int c = 2 * i + 1;
                if (c >= size) {
                    break;
                }
                if (c + 1 < size && f[c + 1] < f[c]) {
                    c++;
                }
                if (f[c] >= pri) {
                    break;
                }
                k[i] = k[c];
                f[i] = f[c];
                i = c;
            }
            k[i] = key;
            f[i] = pri;
        }
    }

    // ------------------------------------------------------------------ outils

    private static double[] center(long[] c) {
        return new double[]{c[0] * 2 + 1.0, c[1] * 2 + 1.0};
    }

    private static double h(int x, int z, int gx, int gz) {
        int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
        return Math.max(dx, dz) + 0.41421356 * Math.min(dx, dz);
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    /** Ligne droite libre entre deux cases (echantillonnee tous les tiers de case). */
    static boolean sight(WaterChart.Reader chart, long[] a, long[] b, boolean heatproof) {
        double dx = b[0] - a[0], dz = b[1] - a[1];
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 3);
        for (int k = 1; k < n; k++) {
            double t = k / (double) n;
            int x = (int) Math.round(a[0] + dx * t), z = (int) Math.round(a[1] + dz * t);
            if (!chart.clear(x, z, 0, heatproof)) {
                return false;
            }
        }
        return true;
    }

    /** Ligne droite libre entre deux points en blocs (sans marge : sert a voir si une route est coupee). */
    public static boolean sightBlocks(WaterChart chart, double ax, double az, double bx, double bz, boolean heatproof) {
        return sightBlocks(chart.reader(), ax, az, bx, bz, heatproof);
    }

    public static boolean sightBlocks(WaterChart.Reader chart, double ax, double az, double bx, double bz, boolean heatproof) {
        return sight(chart, new long[]{WaterChart.cell(ax), WaterChart.cell(az)}, new long[]{WaterChart.cell(bx), WaterChart.cell(bz)}, heatproof);
    }

    /** Toute la route (a partir du point {@code from}) reste praticable ? */
    public static boolean routeClear(WaterChart.Reader chart, List<double[]> route, int from, boolean heatproof) {
        for (int i = Math.max(1, from); i < route.size(); i++) {
            if (!sightBlocks(chart, route.get(i - 1)[0], route.get(i - 1)[1], route.get(i)[0], route.get(i)[1], heatproof)) {
                return false;
            }
        }
        return true;
    }
}
