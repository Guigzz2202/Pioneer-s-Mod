package fr.pioneers.logistics.robotics.logistics;

import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.RoboticsWorld;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ce que la robotique connait d'une dimension : les points logistiques charges, les noeuds (hubs formes, relais alimentes, qui se
 * signalent a chaque tick) et les reseaux qu'ils forment. Une instance cote serveur et une cote client (affichage) par dimension.
 */
public final class LogisticsRegistry {

    private static final Map<ResourceKey<Level>, LogisticsRegistry> SERVER = new HashMap<>();
    private static final Map<ResourceKey<Level>, LogisticsRegistry> CLIENT = new HashMap<>();

    public static LogisticsRegistry get(Level level) {
        return (level.isClientSide ? CLIENT : SERVER).computeIfAbsent(level.dimension(), key -> new LogisticsRegistry());
    }

    public static void clearServer() {
        SERVER.clear();
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    private final Set<LogisticPoint> points = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<NetworkNode, Long> nodes = new IdentityHashMap<>();
    private List<LogisticNetwork> networks = List.of();
    private long networksAt;
    /** Noeuds ou points changes : recalcul au prochain appel. */
    private boolean dirty = true;

    // ------------------------------------------------------------------
    // Points
    // ------------------------------------------------------------------

    public void addPoint(LogisticPoint point) {
        points.add(point);
        dirty = true;
    }

    public void removePoint(LogisticPoint point) {
        points.remove(point);
        dirty = true;
    }

    public Set<LogisticPoint> allPoints() {
        return points;
    }

    // ------------------------------------------------------------------
    // Noeuds et reseaux
    // ------------------------------------------------------------------

    public void registerNode(NetworkNode node, long now) {
        if (nodes.put(node, now) == null) {
            dirty = true;
        }
    }

    public void unregisterNode(NetworkNode node) {
        if (nodes.remove(node) != null) {
            dirty = true;
        }
    }

    /** Hubs formes et actifs. */
    public List<RoboticHubData> hubs(long now) {
        List<RoboticHubData> list = new ArrayList<>();
        for (LogisticNetwork network : networks(now)) {
            list.addAll(network.hubs);
        }
        return list;
    }

    /** Reseaux (recalcules au plus une fois par seconde, ou des que les noeuds ou les points changent). */
    public List<LogisticNetwork> networks(long now) {
        if (!dirty && now >= networksAt && now - networksAt < 20) {
            return networks;
        }
        dirty = false;
        networksAt = now;
        nodes.entrySet().removeIf(entry -> !entry.getKey().nodeValid() || now - entry.getValue() > 40);
        List<NetworkNode> list = new ArrayList<>(nodes.keySet());
        int n = list.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                NetworkNode a = list.get(i), b = list.get(j);
                int reach = a.coverage() + b.coverage();
                BlockPos pa = a.nodeCenter(), pb = b.nodeCenter();
                if (Math.abs(pa.getX() - pb.getX()) <= reach && Math.abs(pa.getZ() - pb.getZ()) <= reach && Math.abs(pa.getY() - pb.getY()) <= 160) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        Map<Integer, List<NetworkNode>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(list.get(i));
        }
        Map<Long, LogisticNetwork> old = new HashMap<>();
        for (LogisticNetwork network : networks) {
            old.put(network.id, network);
        }
        List<LogisticNetwork> result = new ArrayList<>();
        for (List<NetworkNode> group : groups.values()) {
            long id = Long.MAX_VALUE;
            for (NetworkNode node : group) {
                id = Math.min(id, node.nodeCenter().asLong());
            }
            LogisticNetwork network = new LogisticNetwork(id);
            LogisticNetwork previous = old.get(id);
            if (previous != null) {
                network.totals = previous.totals;
            }
            network.nodes.addAll(group);
            for (NetworkNode node : group) {
                if (node instanceof RoboticHubData hub) {
                    network.hubs.add(hub);
                }
            }
            for (LogisticPoint point : points) {
                if (!point.isPointRemoved() && network.covers(point.getPointPos())) {
                    network.points.add(point);
                }
            }
            result.add(network);
        }
        networks = result;
        return networks;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Reseau qui couvre cette position, ou null. */
    @Nullable
    public LogisticNetwork networkAt(BlockPos pos, long now) {
        for (LogisticNetwork network : networks(now)) {
            if (network.covers(pos)) {
                return network;
            }
        }
        return null;
    }

    /** Reseau d'un hub. */
    @Nullable
    public LogisticNetwork networkOf(NetworkNode node, long now) {
        for (LogisticNetwork network : networks(now)) {
            if (network.nodes.contains(node)) {
                return network;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Recherches pour l'ordonnanceur
    // ------------------------------------------------------------------

    /**
     * Rang d'une source : d'abord ce qu'il faut de toute facon vider (a vider) et les stockages, puis le surplus des tampons, et les
     * fournisseurs seulement en dernier (ils sont la reserve qu'on met a disposition, entamee quand le reste est vide).
     */
    private static int sourceTier(MarkerRole role) {
        return switch (role) {
            case UNLOAD, STORAGE -> 0;
            case BUFFER -> 1;
            default -> 2;
        };
    }

    /**
     * Ce qu'un point peut donner d'une ressource : tout pour un stockage, un fournisseur ou un point a vider ; pour un tampon, seulement
     * ce qui depasse le niveau qu'il demande (il garde ses ressources requises).
     */
    public static long available(ServerLevel level, LogisticPoint point, LogisticResource res) {
        PointConfig config = point.getConfig();
        Endpoint endpoint = point.endpointFor(res);
        long extractable = RoboticsWorld.extractable(level, endpoint, res);
        if (extractable <= 0 || config.role != MarkerRole.BUFFER) {
            return extractable;
        }
        return Math.max(0, Math.min(extractable, RoboticsWorld.count(level, endpoint, res) - kept(config, res)));
    }

    /** Niveau qu'un tampon garde d'une ressource (sa demande), 0 si elle ne fait pas partie de ses ressources. */
    public static long kept(PointConfig config, LogisticResource res) {
        long kept = 0;
        for (PointConfig.Entry entry : config.entries) {
            if (entry.res().covers(res)) {
                kept = Math.max(kept, entry.amount());
            }
        }
        return kept;
    }

    /**
     * Source d'une ressource : le rang le plus bas qui en a (stockages et points a vider, puis surplus des tampons, puis fournisseurs),
     * et dans ce rang la plus proche (priorite comprise). {@code exclude} : bloc a ne pas prendre (la destination).
     */
    @Nullable
    public LogisticPoint findSource(ServerLevel level, LogisticNetwork network, LogisticResource res, Vec3 near, @Nullable BlockPos exclude) {
        LogisticPoint best = null;
        double bestScore = Double.MAX_VALUE;
        int bestTier = Integer.MAX_VALUE;
        for (LogisticPoint point : network.points) {
            PointConfig config = point.getConfig();
            if (point.isPointRemoved() || !config.role.source) {
                continue;
            }
            int tier = sourceTier(config.role);
            if (tier > bestTier) {
                continue;
            }
            Endpoint endpoint = point.endpointFor(res);
            if (endpoint.pos().equals(exclude) || !level.isLoaded(endpoint.pos())) {
                continue;
            }
            if ((config.role == MarkerRole.PROVIDER || config.role == MarkerRole.UNLOAD) && config.isFiltered() && !config.accepts(res)) {
                continue;
            }
            double score = endpoint.accessPoint().distanceToSqr(near) / (1 + 0.5 * config.priority);
            if ((tier < bestTier || score < bestScore) && available(level, point, res) > 0) {
                best = point;
                bestScore = score;
                bestTier = tier;
            }
        }
        return best;
    }

    /** Stockage le plus proche qui peut recevoir la ressource (les stockages filtres dessus passent devant, puis la priorite). */
    @Nullable
    public LogisticPoint findStorage(ServerLevel level, LogisticNetwork network, LogisticResource res, long amount, Vec3 near) {
        return findStorage(level, network, res, amount, near, false);
    }

    /**
     * Ou deposer : le stockage le plus proche qui accepte ; {@code buffers} : les tampons aussi (depot de proximite pour la soute des
     * unites qui travaillent autour : un tampon recoit tout, sa liste ne fixe que ce qu'il demande).
     */
    @Nullable
    public LogisticPoint findStorage(ServerLevel level, LogisticNetwork network, LogisticResource res, long amount, Vec3 near, boolean buffers) {
        LogisticPoint best = null;
        double bestScore = Double.MAX_VALUE;
        for (LogisticPoint point : network.points) {
            PointConfig config = point.getConfig();
            boolean buffer = buffers && config.role == MarkerRole.BUFFER;
            if (point.isPointRemoved() || !buffer && (config.role != MarkerRole.STORAGE || !config.accepts(res))) {
                continue;
            }
            Endpoint endpoint = point.endpointFor(res);
            if (!level.isLoaded(endpoint.pos())) {
                continue;
            }
            double score = endpoint.accessPoint().distanceToSqr(near) * (config.role == MarkerRole.STORAGE && config.isFiltered() ? 0.25 : 1) / (1 + 0.5 * config.priority);
            if (score < bestScore && RoboticsWorld.room(level, endpoint, res, Math.max(1, amount)) > 0) {
                best = point;
                bestScore = score;
            }
        }
        return best;
    }

    /** Points qui conviennent a un filtre (outil de la carte, statistiques). */
    public List<LogisticPoint> points(Predicate<LogisticPoint> filter) {
        List<LogisticPoint> list = new ArrayList<>();
        for (LogisticPoint point : points) {
            if (!point.isPointRemoved() && filter.test(point)) {
                list.add(point);
            }
        }
        return list;
    }
}
