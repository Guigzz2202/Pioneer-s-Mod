package fr.pioneers.logistics.warehouse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1, Guillaume : « afin de former un reseau d'entrepot ») : le RESEAU d'un comptoir = tout ce que relient les cordes de ses
 * poulies d'entrepot (parcours en largeur, {@link #MAX_PULLEYS} poulies au plus) ; ses STATIONS = les poulies posees sur la trappe d'une
 * caisse formee ou sur un comptoir. Graphe garde par le comptoir tant que rien ne change ({@link #generation} : cordes, poulies, caisses) ;
 * {@link #contents} : un contenu de caisse a change (liste du comptoir). Aucun balayage par tick.
 */
public final class WarehouseNetwork {

    public static final int MAX_PULLEYS = 256;
    private static volatile int generation, contents;

    /** Une corde, une poulie ou une caisse a change : les reseaux sont a reconstruire. */
    public static void changed() {
        generation++;
    }

    /** Le contenu d'une caisse a change : les listes des comptoirs sont a refaire. */
    public static void contentsChanged() {
        contents++;
    }

    public static int generation() {
        return generation;
    }

    public static int contents() {
        return contents;
    }

    /**
     * 1.0.4-beta (lot 34) : une corde que le reseau n'a pas suivie : de {@code from} vers {@code to} ; {@code hit} : le bloc qu'un colis
     * toucherait (le long de la corde, ou sous la roue {@code to} si {@code wheel}), null si l'autre bout n'est pas charge ou pas reciproque.
     */
    public record Skipped(BlockPos from, BlockPos to, @Nullable BlockPos hit, boolean wheel) {}

    /**
     * Reseau trouve : voisins de chaque poulie, stations de caisse (poulie -> trappe), stations de comptoir. 1.0.4-beta (lot 34) : aussi les
     * cordes ecartees ({@link Skipped}) et les roues dont un bout manquait (chunk pas charge : le comptoir refait son reseau plus tard),
     * pour dire au joueur ce qui ne va pas ({@code CounterUnit.diagnose}).
     */
    public record Graph(Map<BlockPos, List<BlockPos>> links, Map<BlockPos, BlockPos> crates, List<BlockPos> counters, List<Skipped> skipped,
                        boolean partial) {

        public static final Graph EMPTY = new Graph(Map.of(), Map.of(), List.of(), List.of(), false);

        /** Trappes de station du reseau qui ne forment pas (encore) une caisse. */
        public List<BlockPos> unformed(Level level) {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos h : crates.values()) {
                if (level.isLoaded(h) && level.getBlockEntity(h) instanceof CrateHatchTile t && !t.formed()) {
                    out.add(h);
                }
            }
            return out;
        }

        public boolean contains(BlockPos pulley) {
            return links.containsKey(pulley);
        }

        /** Trappes formees du reseau (dans l'ordre de decouverte). */
        public List<CrateHatchTile> hatches(Level level) {
            List<CrateHatchTile> out = new ArrayList<>();
            for (BlockPos h : crates.values()) {
                if (level.isLoaded(h) && level.getBlockEntity(h) instanceof CrateHatchTile t && t.formed()) {
                    out.add(t);
                }
            }
            return out;
        }

        /** Station (poulie) d'une trappe, ou null. */
        @Nullable
        public BlockPos stationOf(BlockPos hatch) {
            for (Map.Entry<BlockPos, BlockPos> e : crates.entrySet()) {
                if (e.getValue().equals(hatch)) {
                    return e.getKey();
                }
            }
            return null;
        }

        /** Chemin le plus court (longueur des cordes) entre deux poulies du reseau, stations comprises ; null : aucun. */
        @Nullable
        public List<BlockPos> route(BlockPos from, BlockPos to) {
            if (!links.containsKey(from) || !links.containsKey(to)) {
                return null;
            }
            Map<BlockPos, Double> dist = new HashMap<>();
            Map<BlockPos, BlockPos> prev = new HashMap<>();
            PriorityQueue<Object[]> queue = new PriorityQueue<>((a, b) -> Double.compare((double) a[1], (double) b[1]));
            dist.put(from, 0.0);
            queue.add(new Object[]{from, 0.0});
            while (!queue.isEmpty()) {
                Object[] cur = queue.poll();
                BlockPos p = (BlockPos) cur[0];
                double d = (double) cur[1];
                if (d > dist.getOrDefault(p, Double.MAX_VALUE)) {
                    continue;
                }
                if (p.equals(to)) {
                    break;
                }
                for (BlockPos q : links.getOrDefault(p, List.of())) {
                    double nd = d + Math.sqrt(p.distSqr(q));
                    if (nd < dist.getOrDefault(q, Double.MAX_VALUE)) {
                        dist.put(q, nd);
                        prev.put(q, p);
                        queue.add(new Object[]{q, nd});
                    }
                }
            }
            if (!from.equals(to) && !prev.containsKey(to)) {
                return null;
            }
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos p = to; p != null; p = prev.get(p)) {
                path.add(p);
                if (p.equals(from)) {
                    break;
                }
            }
            Collections.reverse(path);
            return path;
        }
    }

    private WarehouseNetwork() {
    }

    /**
     * Reseau de la poulie {@code start} (vide si ce n'est pas une station). 1.0.4-beta (lot 2) : on traverse tout noeud de ligne
     * ({@link LineNode} : poulies a axe vertical) par les cordes reciproques et droites ; une corde n'est prise que si les colis peuvent la
     * suivre ({@link WarehouseLines#usable}, et rien sous la roue). 1.0.4-beta (lot 27) : plus de poulie d'entrepot : les stations sont les
     * petites poulies ({@link LineNode#stationWheel}) au-dessus d'une trappe ou d'un comptoir.
     */
    public static Graph build(Level level, BlockPos start) {
        LineNode first = LineNode.at(level, start);
        if (first == null || !first.stationWheel()) {
            return Graph.EMPTY;
        }
        Map<BlockPos, List<BlockPos>> links = new LinkedHashMap<>();
        Map<BlockPos, BlockPos> crates = new LinkedHashMap<>();
        List<BlockPos> counters = new ArrayList<>();
        Map<BlockPos, Boolean> clear = new HashMap<>();
        List<Skipped> skipped = new ArrayList<>();
        boolean partial = false;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        links.put(start.immutable(), new ArrayList<>());
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            LineNode node = LineNode.at(level, p);
            if (node == null) {
                continue;
            }
            if (node.stationWheel()) {
                WarehouseLines.Station st = WarehouseLines.station(level, p);
                if (st != null && st.tile() instanceof CrateHatchTile hatch) {
                    crates.put(p, hatch.getBlockPos());
                } else if (st != null && st.tile() instanceof CounterTile) {
                    counters.add(p);
                }
            }
            for (BlockPos q : node.lineLinks()) {
                //Corde reciproque, chargee, entre deux noeuds de ligne
                if (!level.isLoaded(q)) {
                    partial = true;
                    continue;
                }
                LineNode other = LineNode.at(level, q);
                if (other == null || !other.linkedTo(p) || !other.lineLinks().contains(p)) {
                    skipped.add(new Skipped(p, q, null, false));
                    continue;
                }
                if (!passable(level, p, node, clear)) {
                    //Seule la premiere roue (la station du comptoir) peut en etre la : les autres ne sont prises que degagees
                    skipped.add(new Skipped(q, p, WarehouseLines.underWheel(level, p), true));
                    continue;
                }
                if (!passable(level, q, other, clear)) {
                    skipped.add(new Skipped(p, q, WarehouseLines.underWheel(level, q), true));
                    continue;
                }
                BlockPos hit = WarehouseLines.alongRope(level, p, q, node.lineType(q));
                if (hit != null) {
                    skipped.add(new Skipped(p, q, hit, false));
                    continue;
                }
                links.get(p).add(q);
                if (!links.containsKey(q) && links.size() < MAX_PULLEYS) {
                    links.put(q, new ArrayList<>());
                    queue.add(q);
                }
            }
        }
        return new Graph(links, crates, counters, skipped, partial);
    }

    /** Une poulie laisse passer les colis sous sa roue (garde pour ce calcul). */
    private static boolean passable(Level level, BlockPos p, LineNode node, Map<BlockPos, Boolean> clear) {
        if (!(node instanceof fr.pioneers.tech.kinetics.rope.PulleyTile)) {
            return true;
        }
        return clear.computeIfAbsent(p, k -> WarehouseLines.underWheel(level, k) == null);
    }

    /** Angle de depose de la station de {@code pulley} ({@link WarehouseLines#dropAngle}, station posee dessous ou plus bas). */
    public static double dropAngle(Level level, BlockPos pulley) {
        return WarehouseLines.dropAngle(level, pulley);
    }
}
