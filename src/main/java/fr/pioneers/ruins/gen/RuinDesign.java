package fr.pioneers.ruins.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 1.9 (MAJ RP, bloc 1) : degradation d'un plan de multibloc en ruine (Java pur : le banc hors jeu et l'apercu rejouent le meme code). Entree :
 * une grille alignee sur le monde (plan deja tourne) avec le role de chaque case ; sortie : le bloc delabre ou de decor de chaque case.
 * <ol>
 * <li>usure : une part des blocs disparait, surtout en hauteur, a l'interieur et autour de quelques "impacts" ; jamais le controleur ni ce
 * qui le porte ;</li>
 * <li>tout ce qui ne tient plus au sol tombe (gravats) ;</li>
 * <li>carcasses, cadres et toles : casing delabre ; ports : port delabre ; pieces et modules : bloc de metier de la categorie dominante (ou
 * suggeree par la piece, ou voisine) ; verre : vitre brisee (air), quelques vitres et verres teintes ;</li>
 * <li>variante tres abimee (WORN) d'autant plus frequente que le tier monte et pres des impacts ;</li>
 * <li>gravats au sol, cables arraches (chaines) sous les plafonds, Console de perimetre devant le controleur, Armoires d'archives au fond
 * de l'interieur, postes libres pour les Sentinelles.</li>
 * </ol>
 * Une ruine ne contient donc que des blocs delabres ou de decor : elle ne peut jamais former de multibloc.
 */
public final class RuinDesign {

    // ------------------------------------------------------------------ roles d'entree
    public static final byte NONE = 0, FREE = 1, CASING = 2, PORT = 3, PART = 4, MODULE = 5, GLASS = 6, FRAME = 7, SHEET = 8, CONTROLLER = 9;
    // ------------------------------------------------------------------ sorties (bits 0..4 : sorte ; 5 : abime ; 8..11 : categorie ; 12..13 : facade)
    public static final int AIR = 0, O_CASING = 1, O_PORT = 2, O_TRADE = 3, O_CONTROLLER = 4, O_PANE = 5, O_TINTED = 6, O_RUBBLE = 7, O_CHAIN = 8,
          O_CABINET = 9, O_CONSOLE = 10;
    public static final int WORN = 1 << 5;
    /** Pas 2D des directions (index {@code Direction.get2DDataValue} : sud, ouest, nord, est). */
    public static final int[][] STEP = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    /** Categories voisines (ordinaux de ScrapCategory : structure, mouvement, fluides, thermique, electrique, electronique, capteurs, robotique). */
    private static final int[][] RELATED = {{1, 7, 4}, {7, 4, 2}, {3, 1, 6}, {2, 4, 1}, {5, 3, 1}, {6, 4, 7}, {5, 7, 4}, {1, 5, 6}};

    public static int kind(int out) {
        return out & 31;
    }

    public static boolean worn(int out) {
        return (out & WORN) != 0;
    }

    public static int category(int out) {
        return out >> 8 & 15;
    }

    public static int facing(int out) {
        return out >> 12 & 3;
    }

    /** Ce code est-il un bloc plein (support, mur) ? */
    public static boolean solid(int out) {
        int k = kind(out);
        return k == O_CASING || k == O_PORT || k == O_TRADE || k == O_CONTROLLER || k == O_TINTED || k == O_CABINET;
    }

    /** Donnees d'entree. Coordonnees locales : x, z dans la grille ; y = indice de rangee (0 = rangee la plus basse de la grille). */
    public static final class Input {
        public final int w, h, d;
        public final byte[] role;
        /** Categorie suggeree par une piece signature (-1 : aucune). */
        public final byte[] hint;
        /** Rangee du sol : hors du plan, les cases y <= ground sont du terrain. */
        public int ground;
        public int ctrlX, ctrlY, ctrlZ;
        /** Direction 2D vers laquelle regarde la facade du controleur (exterieur). */
        public int facade;
        public int tier = 1;
        /** Ordinal de la categorie dominante. */
        public int dominant = 1;
        public double wear = 0.3, worn = 0.25, rubble = 0.12;
        public int cabinets = 1;

        public Input(int w, int h, int d) {
            this.w = w;
            this.h = h;
            this.d = d;
            role = new byte[w * h * d];
            hint = new byte[w * h * d];
            java.util.Arrays.fill(hint, (byte) -1);
        }

        public int index(int x, int y, int z) {
            return (y * d + z) * w + x;
        }

        public boolean in(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < d;
        }
    }

    /** Resultat : code de chaque case, postes des Sentinelles (indices), Armoires posees (indices), Console (indice ou -1). */
    public record Output(int[] block, List<Integer> posts, List<Integer> cabinets, int console, int removed) {
    }

    private RuinDesign() {
    }

    public static Output design(Input in, Rng rng) {
        int n = in.w * in.h * in.d;
        int[] out = new int[n];
        boolean[] alive = new boolean[n];
        boolean[] protect = new boolean[n];
        List<Integer> solid = new ArrayList<>();
        int planMin = Integer.MAX_VALUE, planMax = Integer.MIN_VALUE;
        boolean[] foot = new boolean[in.w * in.d];
        for (int y = 0; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int i = in.index(x, y, z);
                    byte r = in.role[i];
                    if (r == NONE) {
                        continue;
                    }
                    foot[z * in.w + x] = true;
                    if (r != FREE) {
                        solid.add(i);
                        alive[i] = true;
                        planMin = Math.min(planMin, y);
                        planMax = Math.max(planMax, y);
                    }
                }
            }
        }
        if (solid.isEmpty()) {
            return new Output(out, List.of(), List.of(), -1, 0);
        }
        //Le controleur et ce qui le porte ne s'usent jamais
        for (int y = in.ctrlY; y >= 0; y--) {
            int i = in.index(in.ctrlX, y, in.ctrlZ);
            if (in.role[i] != NONE && in.role[i] != FREE) {
                protect[i] = true;
            } else if (y < in.ctrlY) {
                break;
            }
        }
        //Impacts : centres d'usure concentree
        int holes = 1 + in.tier + rng.nextInt(2);
        double[][] centers = new double[holes][];
        int span = Math.max(1, planMax - planMin);
        for (int k = 0; k < holes; k++) {
            centers[k] = new double[]{rng.range(0, in.w), rng.range(planMin + span / 3.0, planMax + 1.0), rng.range(0, in.d),
                                      rng.range(3.0, 3.0 + Math.max(in.w, in.d) / 3.0)};
        }
        double[] score = new double[n];
        List<Integer> removable = new ArrayList<>();
        for (int i : solid) {
            if (protect[i]) {
                continue;
            }
            int y = i / (in.w * in.d);
            double s = rng.nextDouble() * 0.45 + 0.55 * (y - planMin) / span + 0.85 * impact(in, centers, i);
            if (in.role[i] == MODULE) {
                s += 0.05;
            } else if (exterior(in, i)) {
                s += 0.2;                                                                  //l'enveloppe se dechire : l'interieur se voit
            }
            if (y == planMin) {
                s -= 0.35;
            }
            score[i] = s;
            removable.add(i);
        }
        removable.sort(Comparator.comparingDouble((Integer i) -> -score[i]));
        int remove = (int) Math.round(Math.max(0, Math.min(0.95, in.wear)) * solid.size());
        int removed = 0;
        for (int k = 0; k < removable.size() && removed < remove; k++) {
            alive[removable.get(k)] = false;
            removed++;
        }
        //Ce qui ne tient plus au sol tombe
        boolean[] held = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i : solid) {
            int y = i / (in.w * in.d);
            if (alive[i] && (y - 1 <= in.ground || protect[i])) {
                held[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % in.w, z = i / in.w % in.d, y = i / (in.w * in.d);
            int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] o : nb) {
                int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                if (!in.in(nx, ny, nz)) {
                    continue;
                }
                int j = in.index(nx, ny, nz);
                if (alive[j] && !held[j]) {
                    held[j] = true;
                    queue.add(j);
                }
            }
        }
        for (int i : solid) {
            if (alive[i] && !held[i]) {
                alive[i] = false;
                removed++;
            }
        }
        //Blocs restants
        int dom = in.dominant <= 0 || in.dominant >= RELATED.length ? 1 : in.dominant;
        for (int i : solid) {
            if (!alive[i]) {
                continue;
            }
            double hit = impact(in, centers, i);
            boolean worn = rng.nextDouble() < Math.min(0.95, in.worn + 0.35 * hit);
            int code;
            switch (in.role[i]) {
                case CONTROLLER -> code = O_CONTROLLER;
                case PORT -> code = O_PORT;
                case GLASS -> {
                    double r = rng.nextDouble();
                    code = r < 0.6 ? AIR : r < 0.9 ? O_PANE : O_TINTED;
                    worn = false;
                }
                case PART -> {
                    int hint = in.hint[i];
                    double r = rng.nextDouble();
                    int cat = hint > 0 && r < 0.6 ? hint : r < 0.85 ? dom : RELATED[dom][rng.nextInt(3)];
                    code = O_TRADE | cat << 8;
                }
                case MODULE -> code = O_TRADE | (rng.nextDouble() < 0.75 ? dom : RELATED[dom][rng.nextInt(3)]) << 8;
                default -> code = O_CASING;
            }
            if (code != AIR && worn) {
                code |= WORN;
            }
            out[i] = code;
        }
        //Gravats au sol (davantage pres des impacts)
        List<Integer> floor = new ArrayList<>();
        List<Double> weight = new ArrayList<>();
        for (int y = in.ground + 1; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int i = in.index(x, y, z);
                    if (out[i] != AIR || !supported(in, out, x, y, z)) {
                        continue;
                    }
                    floor.add(i);
                    weight.add(1.0 + 3.0 * impact(in, centers, i) + (foot[z * in.w + x] ? 1.0 : 0.0) - 0.08 * (y - in.ground - 1));
                }
            }
        }
        int rubble = (int) Math.max(3, Math.round(removed * in.rubble));
        for (int k = 0; k < rubble && !floor.isEmpty(); k++) {
            int pick = weighted(weight, rng);
            int i = floor.remove(pick);
            weight.remove(pick);
            out[i] = O_RUBBLE | (rng.nextDouble() < 0.4 ? WORN : 0);
        }
        //Cables arraches : chaines sous les plafonds
        for (int y = in.ground + 4; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int i = in.index(x, y, z);
                    if (out[i] != AIR || y + 1 >= in.h || !solid(out[in.index(x, y + 1, z)]) || !in.in(x, y - 2, z)
                        || out[in.index(x, y - 1, z)] != AIR || out[in.index(x, y - 2, z)] != AIR || rng.nextDouble() >= 0.04) {
                        continue;
                    }
                    int len = rng.range(1, 3);
                    for (int k = 0; k < len && y - k > in.ground + 1 && out[in.index(x, y - k, z)] == AIR; k++) {
                        out[in.index(x, y - k, z)] = O_CHAIN;
                    }
                }
            }
        }
        //Console de perimetre : dehors, devant le controleur
        int console = -1;
        int[] f = STEP[in.facade & 3];
        for (int dist = 2; dist <= 3 && console < 0; dist++) {
            int x = in.ctrlX + f[0] * dist, z = in.ctrlZ + f[1] * dist, y = in.ground + 1;
            if (!in.in(x, y, z) || in.role[in.index(x, y, z)] != NONE) {
                continue;
            }
            console = in.index(x, y, z);
            out[console] = O_CONSOLE | (in.facade & 3) << 12;
            if (in.in(x, y + 1, z) && kind(out[in.index(x, y + 1, z)]) != O_CASING) {
                out[in.index(x, y + 1, z)] = AIR;
            }
        }
        //Armoires d'archives : au fond de l'interieur
        List<Integer> cabinets = placeCabinets(in, out, foot, rng);
        //Postes libres pour les Sentinelles
        List<Integer> posts = new ArrayList<>();
        for (int y = in.ground + 1; y < in.h; y++) {
            for (int z = 0; z < in.d; z++) {
                for (int x = 0; x < in.w; x++) {
                    int i = in.index(x, y, z);
                    if (out[i] != AIR || !supported(in, out, x, y, z) || y + 1 < in.h && out[in.index(x, y + 1, z)] != AIR) {
                        continue;
                    }
                    posts.add(i);
                }
            }
        }
        return new Output(out, posts, cabinets, console, removed);
    }

    private static List<Integer> placeCabinets(Input in, int[] out, boolean[] foot, Rng rng) {
        List<int[]> cand = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        for (int pass = 0; pass < 2 && cand.isEmpty(); pass++) {
            for (int y = in.ground + 1; y < in.h; y++) {
                for (int z = 0; z < in.d; z++) {
                    for (int x = 0; x < in.w; x++) {
                        int i = in.index(x, y, z);
                        byte r = in.role[i];
                        boolean interior = r == FREE || r == MODULE;
                        //2e passe : sous l'emprise, au ras du sol, derriere le controleur
                        boolean fallback = pass == 1 && y == in.ground + 1 && foot[z * in.w + x] && (r == NONE || r == FREE || r == MODULE);
                        if (!(pass == 0 ? interior : fallback) || !(out[i] == AIR || kind(out[i]) == O_TRADE || kind(out[i]) == O_RUBBLE)) {
                            continue;
                        }
                        if (!(y - 1 <= in.ground || in.role[in.index(x, y - 1, z)] != NONE)) {
                            continue;
                        }
                        if (openSide(in, out, x, y, z) < 0 && pass == 0) {
                            continue;
                        }
                        cand.add(new int[]{x, y, z});
                        lowest = Math.min(lowest, y);
                    }
                }
            }
        }
        List<Integer> placed = new ArrayList<>();
        if (cand.isEmpty()) {
            return placed;
        }
        final int floorY = lowest;
        cand.removeIf(c -> c[1] > floorY + 1);
        //Le plus loin possible du controleur (endroit garde), un peu de hasard
        double[] key = new double[cand.size()];
        for (int k = 0; k < cand.size(); k++) {
            int[] c = cand.get(k);
            key[k] = Math.hypot(c[0] - in.ctrlX, c[2] - in.ctrlZ) + rng.nextDouble() * 2.5 - (c[1] - floorY) * 2.0;
        }
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < cand.size(); k++) {
            order.add(k);
        }
        order.sort(Comparator.comparingDouble((Integer k) -> -key[k]));
        for (int k : order) {
            if (placed.size() >= Math.max(1, in.cabinets)) {
                break;
            }
            int[] c = cand.get(k);
            boolean far = true;
            for (int p : placed) {
                int px = p % in.w, pz = p / in.w % in.d;
                far &= Math.abs(px - c[0]) + Math.abs(pz - c[2]) >= 3;
            }
            if (!far) {
                continue;
            }
            int i = in.index(c[0], c[1], c[2]);
            int face = openSide(in, out, c[0], c[1], c[2]);
            if (face < 0) {
                face = towards(c[0], c[2], in.ctrlX, in.ctrlZ);
                int[] s = STEP[face];
                if (in.in(c[0] + s[0], c[1], c[2] + s[1])) {
                    out[in.index(c[0] + s[0], c[1], c[2] + s[1])] = AIR;
                    if (in.in(c[0] + s[0], c[1] + 1, c[2] + s[1])) {
                        out[in.index(c[0] + s[0], c[1] + 1, c[2] + s[1])] = AIR;
                    }
                }
            }
            out[i] = O_CABINET | face << 12;
            //Ce qui porte l'Armoire tient
            if (c[1] - 1 > in.ground) {
                int below = in.index(c[0], c[1] - 1, c[2]);
                if (!solid(out[below])) {
                    out[below] = O_CASING | WORN;
                }
            }
            placed.add(i);
        }
        return placed;
    }

    /** Cote ouvert d'une case (air de part et d'autre en hauteur), de preference vers le controleur ; -1 si aucun. */
    private static int openSide(Input in, int[] out, int x, int y, int z) {
        int best = -1;
        double bestDot = -1e9;
        for (int f = 0; f < 4; f++) {
            int nx = x + STEP[f][0], nz = z + STEP[f][1];
            if (!in.in(nx, y, nz) || out[in.index(nx, y, nz)] != AIR) {
                continue;
            }
            double dot = STEP[f][0] * (in.ctrlX - x) + STEP[f][1] * (in.ctrlZ - z);
            if (dot > bestDot) {
                bestDot = dot;
                best = f;
            }
        }
        return best;
    }

    private static int towards(int x, int z, int tx, int tz) {
        int dx = tx - x, dz = tz - z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 3 : 1;
        }
        return dz >= 0 ? 0 : 2;
    }

    /** La case repose-t-elle sur quelque chose (terrain ou bloc plein) ? */
    private static boolean supported(Input in, int[] out, int x, int y, int z) {
        if (y - 1 <= in.ground) {
            return true;
        }
        return y - 1 >= 0 && solid(out[in.index(x, y - 1, z)]);
    }

    /** Case d'enveloppe au contact de l'exterieur (un voisin hors du plan). */
    private static boolean exterior(Input in, int i) {
        int x = i % in.w, z = i / in.w % in.d, y = i / (in.w * in.d);
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : nb) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            if (!in.in(nx, ny, nz) || in.role[in.index(nx, ny, nz)] == NONE) {
                return true;
            }
        }
        return false;
    }

    /** Proximite (0..1) du plus proche impact. */
    private static double impact(Input in, double[][] centers, int i) {
        int x = i % in.w, z = i / in.w % in.d, y = i / (in.w * in.d);
        double best = 0;
        for (double[] c : centers) {
            double dist = Math.sqrt((x - c[0]) * (x - c[0]) + (y - c[1]) * (y - c[1]) * 1.4 + (z - c[2]) * (z - c[2]));
            best = Math.max(best, 1.0 - dist / c[3]);
        }
        return best;
    }

    private static int weighted(List<Double> weight, Rng rng) {
        double total = 0;
        for (double w : weight) {
            total += Math.max(0.05, w);
        }
        double r = rng.nextDouble() * total;
        for (int k = 0; k < weight.size(); k++) {
            r -= Math.max(0.05, weight.get(k));
            if (r <= 0) {
                return k;
            }
        }
        return weight.size() - 1;
    }
}
