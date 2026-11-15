package fr.pioneers.rail.logistics;

import fr.pioneers.rail.block.TrackSide;
import fr.pioneers.rail.net.RailNetwork;
import fr.pioneers.robotics.LogisticResource;
import fr.pioneers.robotics.logistics.LogisticNetwork;
import fr.pioneers.robotics.logistics.LogisticPoint;
import fr.pioneers.robotics.logistics.LogisticsRegistry;
import fr.pioneers.robotics.logistics.TileEntityLogisticTerminal;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : stocks des gares logistiques dans le monde. Le quai d'une gare : les postes a 2 blocs au plus de la voie (dessous compris, pour la
 * fosse), sur {@link #LENGTH} blocs derriere le panneau (la ou s'arrete le train). Releve chaque seconde, seulement dans les chunks charges.
 * 0.20.46 (audit) : seules les cases des postes charges ({@link #LOADED_POSTS}) sont lues, chacune verifiee chargee.
 */
public final class WorldStationStock implements StationStock {

    public static final int LENGTH = 48;

    private final ServerLevel level;
    private final Supplier<Iterable<RailNetwork.Station>> stations;
    private long scanned = Long.MIN_VALUE;
    /** Poste -> gare. */
    private final Map<Long, Long> postStation = new HashMap<>();
    /** Gare -> postes. */
    private final Map<Long, List<BlockPos>> posts = new HashMap<>();
    private final Map<Long, Map<Cargo, Long>> stock = new HashMap<>();

    public WorldStationStock(ServerLevel level, Supplier<Iterable<RailNetwork.Station>> stations) {
        this.level = level;
        this.stations = stations;
    }

    /**
     * 0.20.46 (audit) : postes de quai charges, par monde serveur (inscrits par leur {@link PostPoint} au chargement de la tuile et chaque
     * seconde, retires au dechargement et a la casse) : le releve ne lit plus que ces cases au lieu de 960 tuiles par gare.
     */
    private static final Map<Level, LongOpenHashSet> LOADED_POSTS = new WeakHashMap<>();

    /** 0.20.46 (audit) : un poste de quai est charge en {@code pos}. */
    static void postLoaded(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            LOADED_POSTS.computeIfAbsent(level, k -> new LongOpenHashSet()).add(pos.asLong());
        }
    }

    /** 0.20.46 (audit) : le poste de quai de {@code pos} est decharge ou retire. */
    static void postUnloaded(@Nullable Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            LongOpenHashSet known = LOADED_POSTS.get(level);
            if (known != null) {
                known.remove(pos.asLong());
            }
        }
    }

    private void refresh() {
        long now = level.getGameTime();
        if (now - scanned < 20 && now >= scanned) {
            return;
        }
        scanned = now;
        postStation.clear();
        posts.clear();
        stock.clear();
        LongOpenHashSet known = LOADED_POSTS.get(level);
        boolean any = known != null && !known.isEmpty();
        for (RailNetwork.Station s : stations.get()) {
            if (!s.logistics.active() || !level.isLoaded(s.pos)) {
                continue;
            }
            List<BlockPos> found = new ArrayList<>();
            Map<Cargo, Long> content = new LinkedHashMap<>();
            BlockPos track = TrackSide.track(s.pos, s.heading);
            Direction back = s.heading.getOpposite(), side = s.heading.getClockWise();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int d = 0; any && d < LENGTH; d++) {
                BlockPos t = track.relative(back, d);
                if (!level.isLoaded(t)) {
                    break;
                }
                for (int l = -2; l <= 2; l++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        p.set(t.getX() + side.getStepX() * l, t.getY() + dy, t.getZ() + side.getStepZ() * l);
                        //0.20.46 (audit) : seulement les postes connus, et jamais une case d'un chunk non charge (un quai le long d'un bord de
                        //chunk faisait charger le chunk voisin)
                        if (!known.contains(p.asLong()) || !level.isLoaded(p)) {
                            continue;
                        }
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof StationPost post && !post.holds().isEmpty() && postStation.putIfAbsent(p.asLong(), s.pos.asLong()) == null) {
                            found.add(p.immutable());
                            post.addStock(content);
                        }
                    }
                }
            }
            posts.put(s.pos.asLong(), found);
            stock.put(s.pos.asLong(), content);
        }
    }

    /** Postes du quai d'une gare. */
    public List<BlockPos> posts(RailNetwork.Station station) {
        refresh();
        return posts.getOrDefault(station.pos.asLong(), List.of());
    }

    @Override
    public Map<Cargo, Long> stock(RailNetwork.Station station) {
        refresh();
        return stock.getOrDefault(station.pos.asLong(), Map.of());
    }

    @Nullable
    private LogisticNetwork network(RailNetwork.Station station) {
        return LogisticsRegistry.get(level).networkAt(station.pos, level.getGameTime());
    }

    @Override
    public long network(RailNetwork.Station station, Cargo cargo) {
        LogisticNetwork n = network(station);
        if (n == null) {
            return -1;
        }
        LogisticResource r = RailResources.toLogistic(cargo);
        return r == null ? 0 : n.stock(r);
    }

    @Override
    public Map<Cargo, Long> terminalMissing(RailNetwork.Station station) {
        LogisticNetwork n = network(station);
        if (n == null) {
            return Map.of();
        }
        Map<LogisticResource, Long> want = new LinkedHashMap<>();
        for (LogisticPoint point : n.points) {
            if (point instanceof TileEntityLogisticTerminal term && !point.isPointRemoved()) {
                term.pending().forEach((r, v) -> want.merge(r, v, Long::sum));
            }
        }
        Map<Cargo, Long> out = new LinkedHashMap<>();
        want.forEach((r, v) -> {
            long missing = v - n.stock(r);
            Cargo c = RailResources.fromLogistic(r);
            if (missing > 0 && c != null) {
                out.merge(c, missing, Long::sum);
            }
        });
        return out;
    }

    @Override
    public long stationOf(BlockPos post) {
        refresh();
        return postStation.getOrDefault(post.asLong(), 0L);
    }

    @Override
    public int redstone(RailNetwork.Station station) {
        return level.isLoaded(station.pos) ? level.getBestNeighborSignal(station.pos) : 0;
    }
}
