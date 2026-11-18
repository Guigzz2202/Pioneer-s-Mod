package fr.pioneers.logistics.robotics.works;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.6 : trace d'une voie ferree entre deux points, sur un releve du terrain ({@link RoadPlanner.Terrain}), hors du fil du jeu. A* sur
 * (x, z, hauteur, cap par 45 degres, cases droites depuis le dernier virage, cases a plat depuis le dernier changement de niveau) :
 * <ul>
 *     <li>un virage de 45 degres seulement apres 2 cases droites et 2 cases a plat (les diagonales se posent en escalier, la pose
 *     automatique en fait des cordes et des raccords) ;</li>
 *     <li>pentes douces (1 bloc sur 2 cases : la pente basse et la pente haute) seulement en ligne droite nord-sud ou est-ouest : pour
 *     monter, les 3 cases d'avant sont droites et a plat ; pour descendre, les 2 cases d'apres le sont (ce sont les pentes) ;</li>
 *     <li>le cout mele la longueur, le terrassement (viaduc au-dessus du sol, tranchee ou tunnel dessous), l'eau et les virages.</li>
 * </ul>
 * Le resultat est la liste des cases ou la voie est posee, escaliers des diagonales deplies (nord ou sud d'abord, puis est ou ouest).
 */
public final class RailPlanner {

    public record Settings(int maxCut, int maxFill, int maxNodes) {}

    public record Result(List<BlockPos> cells, boolean complete) {}

    /** Caps : 0 = nord, 1 = nord-est ... 7 = nord-ouest (pair : cardinal). */
    static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    static final int[] DZ = {-1, -1, 0, 1, 1, 1, 0, -1};
    public static final int MIN_STRAIGHT = 2;
    private static final double DIAG = Math.sqrt(2);

    private record Node(int x, int y, int z, int dir, int straight, int flat, double g, double f, @Nullable Node parent) {}

    private RailPlanner() {
    }

    private static long key(RoadPlanner.Terrain t, int x, int y, int z, int dir, int straight, int flat) {
        long dx = x - t.minX, dz = z - t.minZ;
        return (dx & 0x3FF) << 54 | (dz & 0x3FF) << 44 | ((long) ((y + 1024) & 0x7FF) << 33) | (long) (dir & 0xF) << 29 | (long) (straight & 0x7) << 26
               | (long) ((flat + 2) & 0x7) << 23;
    }

    public static Result plan(RoadPlanner.Terrain terrain, BlockPos from, BlockPos to, Settings settings, BooleanSupplier cancelled) {
        Result best = null;
        for (double weight : new double[]{1.3, 2.2, 4}) {
            Result r = plan(terrain, from, to, settings, cancelled, weight);
            if (r.complete() || cancelled.getAsBoolean()) {
                return r;
            }
            if (best == null || r.cells().size() > best.cells().size()) {
                best = r;
            }
        }
        return best;
    }

    private static Result plan(RoadPlanner.Terrain terrain, BlockPos from, BlockPos to, Settings settings, BooleanSupplier cancelled, double weight) {
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        best.defaultReturnValue(Double.MAX_VALUE);
        //Depart : n'importe quel cap cardinal ou diagonal, comme apres une longue ligne droite
        for (int d = 0; d < 8; d++) {
            open.add(new Node(from.getX(), from.getY(), from.getZ(), d, 3, 3, 0, weight * heuristic(from.getX(), from.getY(), from.getZ(), to), null));
        }
        Node closest = null;
        double closestH = Double.MAX_VALUE;
        int expanded = 0;
        while (!open.isEmpty()) {
            Node n = open.poll();
            if (n.g > best.get(key(terrain, n.x, n.y, n.z, n.dir, n.straight, n.flat))) {
                continue;
            }
            if (n.x == to.getX() && n.z == to.getZ() && n.y == to.getY() && n.flat >= 1) {
                return new Result(unwind(n), true);
            }
            double h = heuristic(n.x, n.y, n.z, to);
            if (h < closestH) {
                closestH = h;
                closest = n;
            }
            if (++expanded > settings.maxNodes() || (expanded & 1023) == 0 && cancelled.getAsBoolean()) {
                break;
            }
            for (int turn = -1; turn <= 1; turn++) {
                if (turn != 0 && (n.straight < MIN_STRAIGHT || n.flat < 2)) {
                    continue;
                }
                int dir = (n.dir + turn) & 7;
                boolean diagonal = (dir & 1) == 1;
                int nx = n.x + DX[dir], nz = n.z + DZ[dir];
                int ground = terrain.ground(nx, nz);
                if (ground == Integer.MIN_VALUE || near(n, nx, nz)) {
                    continue;
                }
                //Diagonale : la case de l'escalier entre les deux doit aussi etre dans le releve
                if (diagonal && terrain.ground(n.x, nz) == Integer.MIN_VALUE) {
                    continue;
                }
                int straight = turn != 0 ? 0 : Math.min(3, n.straight + 1);
                for (int dy = -1; dy <= 1; dy++) {
                    int flat;
                    if (dy == 0) {
                        //Une descente impose ses deux pentes droites (flat negatif) avant tout virage ou autre pente
                        if (n.flat < 0 && turn != 0) {
                            continue;
                        }
                        flat = Math.min(3, n.flat + 1);
                    } else {
                        if (diagonal || turn != 0) {
                            continue;
                        }
                        if (dy > 0 && (n.flat < 3 || n.straight < 3)) {
                            continue;
                        }
                        if (dy < 0 && (n.flat < 2 || n.straight < 2)) {
                            continue;
                        }
                        flat = dy > 0 ? 1 : -1;
                    }
                    int ny = n.y + dy;
                    int depth = ny - ground;
                    if (depth > settings.maxFill() || -depth > settings.maxCut()) {
                        continue;
                    }
                    double cost = diagonal ? DIAG : 1;
                    int gap = Math.abs(depth);
                    if (depth > 0) {
                        //Remblai bas, puis viaduc (piliers) : le prix monte peu avec la hauteur
                        cost += depth <= 2 ? 0.35 * depth : 0.7 + 0.08 * depth;
                    } else if (depth < 0) {
                        //Tranchee, puis tunnel
                        cost += gap <= 3 ? 0.4 * gap : 1.2 + 0.1 * gap;
                    }
                    if (terrain.water[terrain.index(nx, nz)] && depth <= 0) {
                        cost += 2;
                    }
                    if (turn != 0) {
                        cost += 0.4;
                    }
                    if (dy != 0) {
                        cost += 0.2;
                    }
                    double g = n.g + cost;
                    long k = key(terrain, nx, ny, nz, dir, straight, flat);
                    if (g >= best.get(k)) {
                        continue;
                    }
                    best.put(k, g);
                    open.add(new Node(nx, ny, nz, dir, straight, flat, g, g + weight * heuristic(nx, ny, nz, to), n));
                }
            }
        }
        return new Result(closest == null ? List.of(from) : unwind(closest), false);
    }

    /** La voie ne repasse pas sur elle-meme (au-dela des dernieres cases). */
    private static boolean near(Node node, int x, int z) {
        Node at = node;
        for (int i = 0; i < 400 && at != null; i++, at = at.parent) {
            if (i >= 3 && Math.abs(at.x - x) <= 1 && Math.abs(at.z - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static double heuristic(int x, int y, int z, BlockPos to) {
        int dx = Math.abs(to.getX() - x), dz = Math.abs(to.getZ() - z);
        double flat = Math.max(dx, dz) + (DIAG - 1) * Math.min(dx, dz);
        return Math.max(flat, Math.abs(to.getY() - y) * 2.0);
    }

    /** Cases de la voie : chaque pas en diagonale deplie en escalier (d'abord en z, puis en x). */
    private static List<BlockPos> unwind(Node node) {
        List<Node> nodes = new ArrayList<>();
        for (Node at = node; at != null; at = at.parent) {
            nodes.add(at);
        }
        Collections.reverse(nodes);
        List<BlockPos> cells = new ArrayList<>();
        Node prev = null;
        for (Node n : nodes) {
            if (prev != null && prev.x != n.x && prev.z != n.z) {
                cells.add(new BlockPos(prev.x, prev.y, n.z));
            }
            BlockPos c = new BlockPos(n.x, n.y, n.z);
            if (cells.isEmpty() || !cells.get(cells.size() - 1).equals(c)) {
                cells.add(c);
            }
            prev = n;
        }
        return cells;
    }
}
