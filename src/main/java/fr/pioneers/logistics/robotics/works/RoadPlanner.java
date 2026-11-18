package fr.pioneers.logistics.robotics.works;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Trace d'une route lisse entre deux points, calcule sur un releve du terrain (hauteur du sol et eau par colonne), donc hors du fil du
 * jeu. A* sur (x, z, hauteur, cases depuis la derniere marche) : la route ne monte ou ne descend que d'un bloc a la fois, en ligne droite,
 * et au plus d'un bloc toutes les {@code run} cases (pente maximale 1 / run). Le cout mele la longueur, les deblais et remblais (ecart a la pente
 * naturelle), l'eau et les virages : quand la difference de hauteur est trop forte pour la distance, le chemin le moins cher serpente
 * (lacets) au lieu de couper en escalier.
 */
public final class RoadPlanner {

    /** Releve du terrain : sol (hauteur du premier bloc libre au-dessus du terrain) et eau, colonne par colonne. */
    public static final class Terrain {

        public final int minX, minZ, sizeX, sizeZ;
        final int[] ground;
        final boolean[] water;

        public Terrain(int minX, int minZ, int sizeX, int sizeZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            ground = new int[sizeX * sizeZ];
            water = new boolean[sizeX * sizeZ];
            Arrays.fill(ground, Integer.MIN_VALUE);
        }

        public void set(int x, int z, int groundY, boolean wet) {
            int i = index(x, z);
            if (i >= 0) {
                ground[i] = groundY;
                water[i] = wet;
            }
        }

        int index(int x, int z) {
            int dx = x - minX, dz = z - minZ;
            return dx < 0 || dz < 0 || dx >= sizeX || dz >= sizeZ ? -1 : dx * sizeZ + dz;
        }

        /** Sol de la colonne, ou MIN_VALUE (hors releve, ou inconnu). */
        public int ground(int x, int z) {
            int i = index(x, z);
            return i < 0 ? Integer.MIN_VALUE : ground[i];
        }
    }

    /**
     * Reglages du trace : pente (1 bloc toutes les {@code run} cases), deblai et remblai maximaux, largeur de la route (rayon des virages et
     * ecart entre deux lacets), budget de calcul.
     */
    public record Settings(int run, int maxCut, int maxFill, int width, int maxNodes) {}

    /** Cases de marche de la route (le revetement est dessous), du depart a l'arrivee ; complete = arrivee atteinte. */
    public record Result(List<BlockPos> cells, boolean complete) {}

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final double DIAG = Math.sqrt(2);
    /** Ancetres verifies pour qu'une route ne se recroise jamais (ni boucle, ni lacet colle au precedent). */
    private static final int SELF_CHECK = 160;

    /**
     * Noeud : position, cases depuis la derniere marche ({@code flat}), cap (0..7, 8 au depart) et cases droites depuis le dernier
     * virage ({@code straight}).
     */
    private record Node(int x, int y, int z, int flat, int dir, int straight, double g, double f, @Nullable Node parent, int depth) {}

    private RoadPlanner() {
    }

    private static long key(Terrain terrain, int x, int y, int z, int flat, int dir, int straight) {
        long dx = x - terrain.minX, dz = z - terrain.minZ;
        return (dx & 0x3FF) << 54 | (dz & 0x3FF) << 44 | ((long) ((y + 1024) & 0x7FF) << 33) | (long) (flat & 0xF) << 29 | (long) (dir & 0xF) << 25
               | (straight & 0x7);
    }

    /**
     * Trace de {@code from} a {@code to} (cases de marche). Le cap ne tourne que de 45 degres a la fois, et seulement apres quelques cases
     * droites : les virages sont amples (plus encore pour une route large), et un lacet tourne en arc. La route ne repasse jamais sur
     * elle-meme ni trop pres d'elle-meme (pas de noeud). {@code cancelled} est consulte de temps en temps : un nouveau calcul demande
     * entre-temps abandonne celui-ci.
     */
    public static Result plan(Terrain terrain, BlockPos from, BlockPos to, Settings settings, BooleanSupplier cancelled) {
        //D'abord le meilleur trace ; s'il est trop long a trouver, une recherche plus directe (un peu moins optimale)
        Result result = null;
        for (double weight : new double[]{1.4, 2.5, 4}) {
            Result attempt = plan(terrain, from, to, settings, cancelled, weight);
            if (attempt.complete() || cancelled.getAsBoolean()) {
                return attempt;
            }
            if (result == null || attempt.cells().size() > result.cells().size()) {
                result = attempt;
            }
        }
        return result;
    }

    private static Result plan(Terrain terrain, BlockPos from, BlockPos to, Settings settings, BooleanSupplier cancelled, double weight) {
        int run = Math.max(1, settings.run());
        int width = Math.max(1, settings.width());
        //Cases droites entre deux virages de 45 degres (rayon de courbure) et ecart minimal entre deux branches de la route
        int minStraight = width >= 3 ? 3 : 2;
        int clearance = width / 2 * 2 + 2;
        int skip = clearance * 3 + minStraight * 4;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Long2DoubleOpenHashMap best = new Long2DoubleOpenHashMap();
        best.defaultReturnValue(Double.MAX_VALUE);
        Node start = new Node(from.getX(), from.getY(), from.getZ(), run, 8, minStraight, 0, 0, null, 0);
        open.add(start);
        Node closest = start;
        double closestH = Double.MAX_VALUE;
        int expanded = 0;
        while (!open.isEmpty()) {
            Node node = open.poll();
            if (node.g > best.get(key(terrain, node.x, node.y, node.z, node.flat, node.dir, node.straight))) {
                continue;
            }
            if (node.x == to.getX() && node.z == to.getZ() && node.y == to.getY()) {
                return new Result(unwind(node), true);
            }
            double h = heuristic(node.x, node.y, node.z, to, run);
            if (h < closestH) {
                closestH = h;
                closest = node;
            }
            if (++expanded > settings.maxNodes() || (expanded & 1023) == 0 && cancelled.getAsBoolean()) {
                break;
            }
            for (int turn = -1; turn <= 1; turn++) {
                int dir;
                if (node.dir == 8) {
                    if (turn != 0) {
                        continue;
                    }
                    dir = -1;
                } else {
                    //Un virage (45 degres) seulement apres assez de cases droites
                    if (turn != 0 && node.straight < minStraight) {
                        continue;
                    }
                    dir = (node.dir + turn) & 7;
                }
                for (int d = dir < 0 ? 0 : dir; d <= (dir < 0 ? 7 : dir); d++) {
                    expand(terrain, settings, to, run, minStraight, clearance, skip, weight, node, d, turn != 0, open, best);
                }
            }
        }
        return new Result(unwind(closest), false);
    }

    private static void expand(Terrain terrain, Settings settings, BlockPos to, int run, int minStraight, int clearance, int skip, double weight, Node node,
          int dir, boolean turned, PriorityQueue<Node> open, Long2DoubleOpenHashMap best) {
        boolean diagonal = (dir & 1) == 1;
        int nx = node.x + DX[dir], nz = node.z + DZ[dir];
        int ground = terrain.ground(nx, nz);
        if (ground == Integer.MIN_VALUE || crosses(node, nx, nz, clearance, skip)) {
            return;
        }
        int straight = turned ? 0 : Math.min(minStraight, node.straight + 1);
        for (int dy = -1; dy <= 1; dy++) {
            //Une marche seulement en ligne droite et au plus une toutes les « run » cases
            if (dy != 0 && (diagonal || turned || node.flat < run - 1)) {
                continue;
            }
            int ny = node.y + dy;
            int depth = ny - ground;
            if (depth > settings.maxFill() || -depth > settings.maxCut()) {
                continue;
            }
            int flat = dy != 0 ? 0 : Math.min(run, node.flat + 1);
            double cost = diagonal ? DIAG : 1;
            //Terrassement : un ecart de quelques blocs passe, un grand ecart coute cher
            int gap = Math.abs(depth);
            cost += 0.3 * gap + 0.12 * gap * gap;
            if (terrain.water[terrain.index(nx, nz)] && depth >= 0) {
                cost += 1.5;
            }
            if (turned) {
                cost += 0.3;
            }
            if (dy != 0) {
                cost += 0.15;
            }
            double g = node.g + cost;
            long k = key(terrain, nx, ny, nz, flat, dir, straight);
            if (g >= best.get(k)) {
                continue;
            }
            best.put(k, g);
            open.add(new Node(nx, ny, nz, flat, dir, straight, g, g + weight * heuristic(nx, ny, nz, to, run), node, node.depth + 1));
        }
    }

    /**
     * La case touche-t-elle une partie plus ancienne du trace ? Les dernieres cases (le virage en cours) sont ignorees ; au-dela, il faut
     * garder un ecart (pas de boucle, pas de noeud, deux branches d'un lacet bien separees).
     */
    private static boolean crosses(Node node, int x, int z, int clearance, int skip) {
        Node at = node;
        for (int i = 0; i < skip && at != null; i++) {
            if (at.x == x && at.z == z) {
                return true;
            }
            at = at.parent;
        }
        for (int i = 0; i < SELF_CHECK && at != null; i++, at = at.parent) {
            if (Math.abs(at.x - x) < clearance && Math.abs(at.z - z) < clearance) {
                return true;
            }
        }
        return false;
    }

    /** Minimum de cases restantes : la distance au sol (octile), ou la longueur qu'impose la pente pour rattraper la hauteur. */
    private static double heuristic(int x, int y, int z, BlockPos to, int run) {
        int dx = Math.abs(to.getX() - x), dz = Math.abs(to.getZ() - z);
        double flat = Math.max(dx, dz) + (DIAG - 1) * Math.min(dx, dz);
        return Math.max(flat, Math.abs(to.getY() - y) * (double) run);
    }

    private static List<BlockPos> unwind(Node node) {
        List<BlockPos> cells = new ArrayList<>();
        for (Node at = node; at != null; at = at.parent) {
            cells.add(new BlockPos(at.x, at.y, at.z));
        }
        Collections.reverse(cells);
        return cells;
    }
}
