package fr.pioneers.forge.smithing;

import fr.pioneers.forge.SmithingGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Regles du minijeu de l'enclume (sans dependance a Minecraft : executees par le serveur, l'ecran et le banc d'essai
 * {@code tools/smithing}). Trois coups sur la grille 16 x 16 ({@link SmithingGrid}), dans une direction {@code 0} haut (z - 1), {@code 1}
 * droite (x + 1), {@code 2} bas (z + 1), {@code 3} gauche (x - 1) :
 * <ul>
 * <li>{@link #strike frapper} : le pixel vise part sur la premiere case libre dans la direction choisie (il traverse la matiere : une
 * rangee pleine s'allonge d'un coup) ; contre le bord, il s'etale sur un cote ;</li>
 * <li>{@link #draw etirer} : la rangee s'allonge d'un pixel au bout et la piece s'amincit sous le coup (le pixel du bord de l'epaisseur
 * part) ;</li>
 * <li>{@link #cut couper} : le pixel est retire (chute : 4 pixels = 1 pepite a la fin).</li>
 * </ul>
 * Le {@link #plan plan} de reference (etirements qui comblent un trou en retirant un pixel en trop, puis frappes trouvees par recherche
 * en largeur, relais compris) donne le budget de coups du bonus "Forge main" et sert au banc pour verifier que chaque forme est atteignable
 * (0.21 : pioche ~80 coups, tetes de 2 lingots 50-60, tiges 16, fil 24, ressort 39).
 */
public final class SmithingMoves {

    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;
    public static final int OP_STRIKE = 1, OP_DRAW = 2, OP_CUT = 3;
    /** Lingots au plus sur une piece (bandes de {@link SmithingGrid#initial}) ; 1.0.2-beta : 8 (avant 4 : le marteau de minage, 5 lingots, etait infaisable). */
    public static final int MAX_INGOTS = SmithingGrid.MAX_INGOTS;

    private static final int N = SmithingGrid.SIZE;

    private SmithingMoves() {
    }

    public static int dx(int dir) {
        return dir == RIGHT ? 1 : dir == LEFT ? -1 : 0;
    }

    public static int dz(int dir) {
        return dir == DOWN ? 1 : dir == UP ? -1 : 0;
    }

    private static boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < N && z < N;
    }

    private static boolean free(SmithingGrid g, int x, int z) {
        return inside(x, z) && !g.get(x, z);
    }

    /**
     * Frapper : le pixel vise part sur la premiere case libre dans la direction choisie (la voisine si elle est libre, sinon il traverse la
     * matiere : on chasse le metal devant le marteau). Contre le bord, il s'etale sur un cote (sens horaire d'abord). Vrai si la grille a
     * change.
     */
    public static boolean strike(SmithingGrid g, int x, int z, int dir) {
        if (!g.get(x, z)) {
            return false;
        }
        int nx = x + dx(dir), nz = z + dz(dir);
        while (inside(nx, nz) && g.get(nx, nz)) {
            nx += dx(dir);
            nz += dz(dir);
        }
        if (inside(nx, nz)) {
            g.set(x, z, false);
            g.set(nx, nz, true);
            return true;
        }
        for (int d : new int[]{(dir + 1) & 3, (dir + 3) & 3}) {
            if (free(g, x + dx(d), z + dz(d))) {
                g.set(x, z, false);
                g.set(x + dx(d), z + dz(d), true);
                return true;
            }
        }
        return false;
    }

    /**
     * Etirer : la rangee qui passe par le pixel vise s'allonge d'un pixel au bout, dans la direction choisie, et la piece s'amincit la ou l'on
     * frappe : le pixel retire est celui du bord de l'epaisseur (bout de la travee perpendiculaire passant par la case visee, cote horaire ;
     * la case visee elle-meme si la travee n'a qu'un pixel). Faux si la rangee touche le bord.
     */
    public static boolean draw(SmithingGrid g, int x, int z, int dir) {
        if (!g.get(x, z)) {
            return false;
        }
        int nx = x, nz = z;
        while (inside(nx, nz) && g.get(nx, nz)) {
            nx += dx(dir);
            nz += dz(dir);
        }
        if (!inside(nx, nz)) {
            return false;
        }
        int side = (dir + 1) & 3;
        int ex = x, ez = z;
        while (inside(ex + dx(side), ez + dz(side)) && g.get(ex + dx(side), ez + dz(side))) {
            ex += dx(side);
            ez += dz(side);
        }
        g.set(ex, ez, false);
        g.set(nx, nz, true);
        return true;
    }

    /** Couper : vrai si un pixel a ete retire. */
    public static boolean cut(SmithingGrid g, int x, int z) {
        if (!g.get(x, z)) {
            return false;
        }
        g.set(x, z, false);
        return true;
    }

    /** Applique un coup ({@code op} = {@link #OP_STRIKE}, {@link #OP_DRAW}, {@link #OP_CUT}). */
    public static boolean apply(SmithingGrid g, int op, int x, int z, int dir) {
        return switch (op) {
            case OP_STRIKE -> strike(g, x, z, dir);
            case OP_DRAW -> draw(g, x, z, dir);
            case OP_CUT -> cut(g, x, z);
            default -> false;
        };
    }

    /**
     * Soudure du lingot numero {@code index} (0 = le premier) : ses 32 pixels se posent sur sa bande de depart ({@link SmithingGrid#initial})
     * ; une case deja prise est remplacee par la case libre la plus proche. Renvoie le nombre de pixels poses.
     */
    public static int weld(SmithingGrid g, int index) {
        if (index < 0 || index >= MAX_INGOTS) {
            return 0;
        }
        SmithingGrid before = SmithingGrid.initial(index), after = SmithingGrid.initial(index + 1);
        int added = 0;
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                if (!after.get(x, z) || before.get(x, z)) {
                    continue;
                }
                if (!g.get(x, z)) {
                    g.set(x, z, true);
                    added++;
                    continue;
                }
                //Case prise : la case libre la plus proche (distance de Manhattan croissante)
                found:
                for (int r = 1; r < 2 * N; r++) {
                    for (int ddx = -r; ddx <= r; ddx++) {
                        int ddz = r - Math.abs(ddx);
                        if (free(g, x + ddx, z + ddz)) {
                            g.set(x + ddx, z + ddz, true);
                            added++;
                            break found;
                        }
                        if (ddz != 0 && free(g, x + ddx, z - ddz)) {
                            g.set(x + ddx, z - ddz, true);
                            added++;
                            break found;
                        }
                    }
                }
            }
        }
        return added;
    }

    // ------------------------------------------------------------------
    // Plan de reference et budget
    // ------------------------------------------------------------------

    /** Coup code sur un int : op (bits 0-1), x (2-5), z (6-9), direction (10-11). */
    public static int encode(int op, int x, int z, int dir) {
        return op & 3 | (x & 15) << 2 | (z & 15) << 6 | (dir & 3) << 10;
    }

    public static int op(int move) {
        return move & 3;
    }

    public static int x(int move) {
        return move >> 2 & 15;
    }

    public static int z(int move) {
        return move >> 6 & 15;
    }

    public static int dir(int move) {
        return move >> 10 & 3;
    }

    /**
     * Suite de coups qui mene de {@code start} a {@code target} : on coupe les pixels en trop les plus eloignes des trous (appariement
     * glouton), puis, tant que possible, un etirement qui comble un trou en retirant un pixel en trop, sinon le trou le moins cher en frappes
     * ({@code route}) ; en dernier recours, poussee en chaine le long d'un chemin en L. Null si la matiere manque. Quelques millisecondes :
     * le budget est mis en cache par l'appelant.
     */
    @Nullable
    public static int[] plan(SmithingGrid start, SmithingGrid target) {
        SmithingGrid g = start.copy();
        List<int[]> excess = new ArrayList<>(), holes = new ArrayList<>();
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                boolean a = g.get(x, z), b = target.get(x, z);
                if (a && !b) {
                    excess.add(new int[]{x, z});
                } else if (b && !a) {
                    holes.add(new int[]{x, z});
                }
            }
        }
        if (excess.size() < holes.size()) {
            return null;
        }
        //Appariement glouton : toutes les paires triees par distance (puis par position : resultat stable)
        int ne = excess.size(), nh = holes.size();
        long[] pairs = new long[ne * nh];
        int k = 0;
        for (int i = 0; i < ne; i++) {
            for (int j = 0; j < nh; j++) {
                int d = Math.abs(excess.get(i)[0] - holes.get(j)[0]) + Math.abs(excess.get(i)[1] - holes.get(j)[1]);
                pairs[k++] = (long) d << 32 | (long) i << 16 | j;
            }
        }
        Arrays.sort(pairs);
        int[] matchOf = new int[nh];
        Arrays.fill(matchOf, -1);
        boolean[] used = new boolean[ne];
        int matched = 0;
        for (long p : pairs) {
            if (matched == nh) {
                break;
            }
            int i = (int) (p >> 16 & 0xFFFF), j = (int) (p & 0xFFFF);
            if (!used[i] && matchOf[j] < 0) {
                used[i] = true;
                matchOf[j] = i;
                matched++;
            }
        }
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < ne; i++) {
            if (!used[i]) {
                int[] c = excess.get(i);
                cut(g, c[0], c[1]);
                moves.add(encode(OP_CUT, c[0], c[1], 0));
            }
        }
        //Puis : etirer tant qu'un pixel en trop est dans le prolongement d'une rangee qui mene a un trou (un coup par pixel, quelle que soit
        //la distance) ; sinon la paire en trop / trou la plus proche est poussee en chaine
        //Chaque fois, le pixel en trop qui atteint un trou en le moins de frappes (recherche en largeur, les autres pixels immobiles)
        int guard = 0;
        while (!g.matches(target) && guard++ < N * N && (drawStep(g, target, moves) || route(g, target, moves))) {
            //un pixel de plus a sa place
        }
        int[] pair;
        //Reste : chaque trou recoit le pixel en trop le plus proche, pousse en chaine (progres garanti)
        while ((pair = nearestPair(g, target)) != null) {
            chain(g, pair[0], pair[1], pair[2], pair[3], moves);
        }
        if (!g.matches(target)) {
            return null;
        }
        int[] out = new int[moves.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = moves.get(i);
        }
        return out;
    }

    /**
     * Remplit le trou le moins cher : recherche en largeur A REBOURS depuis les trous ("il faut un pixel ici") : une case ou une frappe peut
     * arriver depuis q coute 1 coup ; si q porte un pixel en trop, c'est gagne ; si q porte un pixel de la forme ou est vide, il faudra a
     * son tour y amener un pixel (relais : on frappe d'abord le pixel le plus proche du trou, puis on comble derriere). Faux si rien n'a
     * bouge.
     */
    private static boolean route(SmithingGrid g, SmithingGrid target, List<Integer> moves) {
        int[] next = new int[N * N], how = new int[N * N], queue = new int[N * N];
        Arrays.fill(next, -2);
        int head = 0, tail = 0, source = -1;
        for (int c = 0; c < N * N; c++) {
            if (!g.get(c % N, c / N) && target.get(c % N, c / N)) {
                next[c] = -1;
                queue[tail++] = c;
            }
        }
        while (head < tail && source < 0) {
            int c = queue[head++];
            int cx = c % N, cz = c / N;
            //Un relais sera vide quand on y amenera le pixel suivant
            boolean held = g.get(cx, cz);
            g.set(cx, cz, false);
            //Toutes les cases q d'ou une frappe arrive en c : sur les 4 lignes qui partent de c
            for (int d = 0; d < 4 && source < 0; d++) {
                int qx = cx - dx(d), qz = cz - dz(d);
                while (inside(qx, qz)) {
                    int q = qz * N + qx;
                    if (next[q] == -2 && landing(g, qx, qz, d) == c) {
                        next[q] = c;
                        how[q] = d;
                        if (g.get(qx, qz) && !target.get(qx, qz)) {
                            source = q;
                            break;
                        }
                        queue[tail++] = q;
                    }
                    if (!g.get(qx, qz) && !(qx == cx - dx(d) && qz == cz - dz(d))) {
                        break;
                    }
                    qx -= dx(d);
                    qz -= dz(d);
                }
            }
            g.set(cx, cz, held);
        }
        if (source < 0) {
            return false;
        }
        //Chemin source -> trou, joue depuis le bout : le dernier pixel present sur le chemin avance jusqu'a la case suivante a remplir
        List<Integer> path = new ArrayList<>();
        for (int c = source; c >= 0; c = next[c]) {
            path.add(c);
        }
        int before = moves.size();
        int end = path.size() - 1;
        while (end > 0) {
            int j = end - 1;
            while (j > 0 && !g.get(path.get(j) % N, path.get(j) / N)) {
                j--;
            }
            for (int i = j; i < end; i++) {
                int c = path.get(i);
                int land = landing(g, c % N, c / N, how[c]);
                if (land != path.get(i + 1) || !g.get(c % N, c / N)) {
                    return moves.size() > before;
                }
                strike(g, c % N, c / N, how[c]);
                moves.add(encode(OP_STRIKE, c % N, c / N, how[c]));
            }
            end = j;
        }
        return true;
    }

    /** Un etirement qui, d'un seul coup, comble un trou ET retire un pixel en trop (le pixel aminci) ; faux s'il n'y en a pas. */
    private static boolean drawStep(SmithingGrid g, SmithingGrid target, List<Integer> moves) {
        int before = mismatch(g, target);
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                if (!g.get(x, z)) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    SmithingGrid c = g.copy();
                    if (draw(c, x, z, d) && mismatch(c, target) <= before - 2) {
                        draw(g, x, z, d);
                        moves.add(encode(OP_DRAW, x, z, d));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int mismatch(SmithingGrid g, SmithingGrid target) {
        return g.excess(target) + g.missing(target);
    }

    /** Case ou finit une frappe depuis (x, z) dans la direction {@code dir} (meme regle que {@link #strike}) ; -1 si elle ne fait rien. */
    private static int landing(SmithingGrid g, int x, int z, int dir) {
        int nx = x + dx(dir), nz = z + dz(dir);
        while (inside(nx, nz) && g.get(nx, nz)) {
            nx += dx(dir);
            nz += dz(dir);
        }
        if (inside(nx, nz)) {
            return nz * N + nx;
        }
        for (int d : new int[]{(dir + 1) & 3, (dir + 3) & 3}) {
            if (free(g, x + dx(d), z + dz(d))) {
                return (z + dz(d)) * N + x + dx(d);
            }
        }
        return -1;
    }

    /** Pixel en trop et trou les plus proches (x, z du pixel puis du trou) ; null s'il n'y en a pas. */
    @Nullable
    private static int[] nearestPair(SmithingGrid g, SmithingGrid target) {
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int ez = 0; ez < N; ez++) {
            for (int ex = 0; ex < N; ex++) {
                if (!g.get(ex, ez) || target.get(ex, ez)) {
                    continue;
                }
                for (int tz = 0; tz < N; tz++) {
                    for (int tx = 0; tx < N; tx++) {
                        if (g.get(tx, tz) || !target.get(tx, tz)) {
                            continue;
                        }
                        int d = Math.abs(ex - tx) + Math.abs(ez - tz);
                        if (d < bestD) {
                            bestD = d;
                            best = new int[]{ex, ez, tx, tz};
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Pousse en chaine : la case (sx, sz) se vide, la case (tx, tz) se remplit ; une frappe par pas du chemin. */
    private static void chain(SmithingGrid g, int sx, int sz, int tx, int tz, List<Integer> moves) {
        int len = Math.abs(tx - sx) + Math.abs(tz - sz);
        int[] px = new int[len + 1], pz = new int[len + 1], pd = new int[len + 1];
        int x = sx, z = sz;
        px[0] = x;
        pz[0] = z;
        for (int i = 1; i <= len; i++) {
            int d;
            if (x != tx) {
                d = tx > x ? RIGHT : LEFT;
            } else {
                d = tz > z ? DOWN : UP;
            }
            x += dx(d);
            z += dz(d);
            px[i] = x;
            pz[i] = z;
            pd[i - 1] = d;
        }
        int end = len;
        while (end > 0) {
            int j = end - 1;
            while (j > 0 && !g.get(px[j], pz[j])) {
                j--;
            }
            for (int i = j; i < end; i++) {
                strike(g, px[i], pz[i], pd[i]);
                moves.add(encode(OP_STRIKE, px[i], pz[i], pd[i]));
            }
            end = j;
        }
    }

    /**
     * Budget de coups du bonus "Forge main" : le plan de reference (coupes + frappes) plus un quart des frappes et 8 coups de marge.
     * Atteignable par construction (verifie au banc). -1 si la forme est hors de portee.
     */
    public static int budget(SmithingGrid start, SmithingGrid target) {
        int[] plan = plan(start, target);
        if (plan == null) {
            return -1;
        }
        int cuts = 0;
        for (int m : plan) {
            if (op(m) == OP_CUT) {
                cuts++;
            }
        }
        int strikes = plan.length - cuts;
        return cuts + strikes + strikes / 4 + 8;
    }
}
