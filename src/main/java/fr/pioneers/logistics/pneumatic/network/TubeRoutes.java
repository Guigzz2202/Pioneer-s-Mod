package fr.pioneers.logistics.pneumatic.network;

import fr.pioneers.core.Directions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Chemins des capsules : parcours en largeur dans les tubes, d'une station a une autre (les stations sont des terminus : une capsule ne
 * traverse jamais une autre station). Chaque monde a une version de reseau, incrementee a chaque pose ou casse de tube ou de station ; les
 * stations gardent leurs chemins tant que la version ne change pas.
 */
public final class TubeRoutes {

    /** Taille maximale d'un reseau parcouru (tubes et stations). */
    public static final int MAX_NODES = 8192;

    private static final Map<Level, int[]> VERSIONS = new WeakHashMap<>();

    private TubeRoutes() {
    }

    public static synchronized int version(Level level) {
        return VERSIONS.computeIfAbsent(level, l -> new int[1])[0];
    }

    public static synchronized void changed(Level level) {
        VERSIONS.computeIfAbsent(level, l -> new int[1])[0]++;
    }

    /**
     * Station atteinte et chemin : points de l'axe (centre de la station de depart -> centre de la station d'arrivee), troncons d'hypertube
     * traverses, anciens tubes en blocs traverses (station d'arrivee comprise), longueur (blocs). 0.20.39 : hypertubes courbes en plus des
     * anciens tubes en blocs (toujours valables).
     */
    public record Route(BlockPos station, List<Vec3> points, List<Integer> segments, List<BlockPos> tubes, double length) {

        /** Longueur arrondie (blocs) : cout en air, duree affichee. */
        public int blocks() {
            return Math.max(1, (int) Math.ceil(length));
        }
    }

    /**
     * Toutes les stations reliees a {@code from} par les tubes, avec le plus court chemin vers chacune. La station de depart n'y figure pas.
     */
    public static List<Route> explore(Level level, BlockPos from) {
        Map<BlockPos, Route> best = new HashMap<>();
        for (Route r : exploreBlocks(level, from)) {
            best.merge(r.station(), r, (x, y) -> x.length() <= y.length() ? x : y);
        }
        if (level instanceof ServerLevel server) {
            for (HypertubeWorld.Route r : HypertubeWorld.get(server).routes(from)) {
                best.merge(r.station(), new Route(r.station(), r.points(), r.segments(), List.of(r.station()), r.length()),
                      (x, y) -> x.length() <= y.length() ? x : y);
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Anciens tubes en blocs : parcours en largeur. */
    private static List<Route> exploreBlocks(Level level, BlockPos from) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> stations = new ArrayList<>();
        parent.put(from, from);
        queue.add(from);
        while (!queue.isEmpty() && parent.size() < MAX_NODES) {
            BlockPos at = queue.poll();
            for (Direction d : Directions.ALL) {
                BlockPos next = at.relative(d);
                if (parent.containsKey(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof PneumaticTubeBlock) {
                    parent.put(next, at);
                    queue.add(next);
                } else if (state.getBlock() instanceof TubeStationBlock) {
                    //Station touchee par un tube (ou collee a la station de depart) : terminus, on n'explore pas au-dela
                    parent.put(next, at);
                    stations.add(next);
                }
            }
        }
        List<Route> routes = new ArrayList<>();
        for (BlockPos station : stations) {
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos p = station; !p.equals(from); p = parent.get(p)) {
                path.add(p);
            }
            Collections.reverse(path);
            List<Vec3> points = new ArrayList<>();
            points.add(Vec3.atCenterOf(from));
            path.forEach(p -> points.add(Vec3.atCenterOf(p)));
            double length = 0;
            for (int i = 1; i < points.size(); i++) {
                length += points.get(i).distanceTo(points.get(i - 1));
            }
            routes.add(new Route(station, List.copyOf(points), List.of(), List.copyOf(path), length));
        }
        return routes;
    }

    /** Plus court chemin vers la station nommee {@code name} (la plus proche si plusieurs portent ce nom), ou null. */
    @Nullable
    public static Route find(Level level, BlockPos from, String name) {
        Route best = null;
        for (Route route : explore(level, from)) {
            if (level.getBlockEntity(route.station()) instanceof TubeStationTile station && name.equals(station.name())
                && (best == null || route.length() < best.length())) {
                best = route;
            }
        }
        return best;
    }
}
