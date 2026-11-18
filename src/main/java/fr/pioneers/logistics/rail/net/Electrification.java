package fr.pioneers.logistics.rail.net;

import fr.pioneers.core.Directions;
import fr.pioneers.logistics.rail.track.TrackPath;
import fr.pioneers.logistics.rail.track.TrackQuality;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.2 : electrification des voies, logique (comme le reseau : elle marche aussi dans les chunks non charges). Des conducteurs : poteaux
 * de catenaire (relies entre eux jusqu'a 16 blocs, couvrent la voie a 6 blocs), troisiemes rails (relies a 3 blocs, couvrent la voie
 * voisine) et sous-stations (reliees aux conducteurs a 16 blocs ; leur reserve d'energie est ici). Les conducteurs relies forment une
 * grille ; une locomotive electrique couverte tire l'energie des sous-stations de sa grille. La voie MKIV porte l'energie elle-meme :
 * toutes les pieces MKIV raccordees forment une grille avec les sous-stations posees contre elles.
 */
public final class Electrification {

    public enum Kind {
        POLE(16, 6), THIRD_RAIL(3, 1.5F), SUBSTATION(16, 0);

        /** Distance de liaison avec un autre conducteur (blocs). */
        public final int link;
        /** Rayon de couverture de la voie (blocs). */
        public final float cover;

        Kind(int link, float cover) {
            this.link = link;
            this.cover = cover;
        }

        public static Kind byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public record Conductor(BlockPos pos, Kind kind, int tier) {}

    /** Reserve d'une sous-station selon son tier (FE) et debit vers les trains (FE/tick). */
    public static long capacity(int tier) {
        return 8_000_000L << (3 * Math.max(0, Math.min(3, tier)));
    }

    public static long rate(int tier) {
        return 8_192L << (2 * Math.max(0, Math.min(3, tier)));
    }

    private final Long2ObjectOpenHashMap<Conductor> conductors = new Long2ObjectOpenHashMap<>();
    private final Map<Long, List<Conductor>> byChunk = new HashMap<>();
    private final Long2LongOpenHashMap stored = new Long2LongOpenHashMap();
    //Grilles calculees a la demande
    private boolean dirty = true;
    private int netVersion = -1;
    private final Long2IntOpenHashMap gridOf = new Long2IntOpenHashMap();
    /** Piece MKIV -> grille. */
    private final Long2IntOpenHashMap railGrid = new Long2IntOpenHashMap();
    private final List<List<Conductor>> stations = new ArrayList<>();
    /** Energie tiree ce tick par sous-station (borne par son debit). */
    private final Long2LongOpenHashMap drawnThisTick = new Long2LongOpenHashMap();
    private long tick = Long.MIN_VALUE;

    public void add(Conductor c) {
        remove(c.pos());
        conductors.put(c.pos().asLong(), c);
        byChunk.computeIfAbsent(ChunkPos.asLong(c.pos().getX() >> 4, c.pos().getZ() >> 4), k -> new ArrayList<>()).add(c);
        dirty = true;
    }

    public void remove(BlockPos pos) {
        Conductor c = conductors.remove(pos.asLong());
        if (c != null) {
            List<Conductor> list = byChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (list != null) {
                list.removeIf(o -> o.pos().equals(pos));
            }
            stored.remove(pos.asLong());
            dirty = true;
        }
    }

    @Nullable
    public Conductor conductor(BlockPos pos) {
        return conductors.get(pos.asLong());
    }

    public Collection<Conductor> conductors() {
        return conductors.values();
    }

    /** Reserve d'une sous-station. */
    public long stored(BlockPos pos) {
        return stored.get(pos.asLong());
    }

    /** Energie apportee a une sous-station par ses cables ; renvoie ce qui a ete pris. */
    public long receive(BlockPos pos, long fe) {
        Conductor c = conductors.get(pos.asLong());
        if (c == null || c.kind() != Kind.SUBSTATION || fe <= 0) {
            return 0;
        }
        long have = stored.get(pos.asLong());
        long n = Math.min(fe, capacity(c.tier()) - have);
        if (n > 0) {
            stored.put(pos.asLong(), have + n);
        }
        return Math.max(0, n);
    }

    /** Casse d'une sous-station : sa reserve passe aux autres sous-stations de sa grille, puis elle est retiree. */
    public void removeSubstation(BlockPos pos, RailNetwork net) {
        rebuild(net);
        long left = stored.get(pos.asLong());
        int grid = gridOf.getOrDefault(pos.asLong(), -1);
        if (left > 0 && grid >= 0 && grid < stations.size()) {
            for (Conductor s : stations.get(grid)) {
                if (left <= 0) {
                    break;
                }
                if (!s.pos().equals(pos)) {
                    left -= receive(s.pos(), left);
                }
            }
        }
        remove(pos);
    }

    /** Energie retiree d'une sous-station (casse : rendue au bloc). */
    public long take(BlockPos pos) {
        return stored.remove(pos.asLong());
    }

    /** Stocke directement (chargement du bloc pose avec son energie). */
    public void restore(BlockPos pos, long fe) {
        Conductor c = conductors.get(pos.asLong());
        if (c != null && c.kind() == Kind.SUBSTATION) {
            stored.put(pos.asLong(), Math.min(capacity(c.tier()), fe));
        }
    }

    // ------------------------------------------------------------------ grilles

    private void rebuild(RailNetwork net) {
        if (!dirty && netVersion == net.version()) {
            return;
        }
        dirty = false;
        netVersion = net.version();
        gridOf.clear();
        railGrid.clear();
        stations.clear();
        List<Conductor> all = new ArrayList<>(conductors.values());
        int n = all.size();
        int[] parent = new int[n];
        Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            index.put(all.get(i).pos().asLong(), i);
        }
        for (int i = 0; i < n; i++) {
            Conductor a = all.get(i);
            int reach = a.kind().link;
            int cx0 = (a.pos().getX() - reach) >> 4, cx1 = (a.pos().getX() + reach) >> 4;
            int cz0 = (a.pos().getZ() - reach) >> 4, cz1 = (a.pos().getZ() + reach) >> 4;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    List<Conductor> list = byChunk.get(ChunkPos.asLong(cx, cz));
                    if (list == null) {
                        continue;
                    }
                    for (Conductor b : list) {
                        int link = Math.min(a.kind().link, b.kind().link);
                        if (a.kind() == Kind.SUBSTATION || b.kind() == Kind.SUBSTATION) {
                            link = Math.max(a.kind().link, b.kind().link);
                        }
                        if (b == a || Math.abs(b.pos().getY() - a.pos().getY()) > 4) {
                            continue;
                        }
                        double d = Math.sqrt(sq(a.pos().getX() - b.pos().getX()) + sq(a.pos().getZ() - b.pos().getZ()));
                        if (d <= link) {
                            union(parent, i, index.get(b.pos().asLong()));
                        }
                    }
                }
            }
        }
        //Voie MKIV : composantes des pieces raccordees
        Long2IntOpenHashMap rail = new Long2IntOpenHashMap();
        List<Piece> ultimate = new ArrayList<>();
        for (Piece p : net.pieces()) {
            if (p.quality == TrackQuality.ULTIMATE) {
                rail.put(p.key(), n + ultimate.size());
                ultimate.add(p);
            }
        }
        int total = n + ultimate.size();
        int[] par = new int[total];
        System.arraycopy(parent, 0, par, 0, n);
        for (int i = n; i < total; i++) {
            par[i] = i;
        }
        for (int i = 0; i < ultimate.size(); i++) {
            Piece p = ultimate.get(i);
            for (TrackPath path : p.paths()) {
                for (TrackPath.End e : new TrackPath.End[]{path.a, path.b}) {
                    if (!e.open()) {
                        continue;
                    }
                    for (RailNetwork.Link l : net.links(PortKey.of(p.anchor, e))) {
                        if (l.piece().quality == TrackQuality.ULTIMATE && l.piece() != p) {
                            union(par, n + i, rail.get(l.piece().key()));
                        }
                    }
                }
            }
        }
        //Sous-stations posees contre une voie MKIV
        for (int i = 0; i < n; i++) {
            Conductor c = all.get(i);
            if (c.kind() != Kind.SUBSTATION) {
                continue;
            }
            for (Direction d : Directions.ALL) {
                if (d == Direction.UP) {
                    continue;
                }
                Piece p = net.at(c.pos().relative(d));
                if (p != null && p.quality == TrackQuality.ULTIMATE) {
                    union(par, i, rail.get(p.key()));
                }
            }
        }
        //Numerotation des grilles : seules celles qui ont une sous-station comptent
        Map<Integer, Integer> ids = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (all.get(i).kind() == Kind.SUBSTATION) {
                int root = find(par, i);
                int id = ids.computeIfAbsent(root, k -> {
                    stations.add(new ArrayList<>());
                    return stations.size() - 1;
                });
                stations.get(id).add(all.get(i));
            }
        }
        for (int i = 0; i < n; i++) {
            Integer id = ids.get(find(par, i));
            if (id != null) {
                gridOf.put(all.get(i).pos().asLong(), (int) id);
            }
        }
        for (int i = 0; i < ultimate.size(); i++) {
            Integer id = ids.get(find(par, n + i));
            if (id != null) {
                railGrid.put(ultimate.get(i).key(), (int) id);
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) {
            p[ra] = rb;
        }
    }

    /**
     * Grille qui alimente une locomotive (position du vehicule, piece sous lui), ou -1. {@code railOnly} : le maglev ne prend que la voie
     * MKIV.
     */
    public int gridAt(RailNetwork net, double x, double y, double z, @Nullable Piece piece, boolean railOnly) {
        rebuild(net);
        if (piece != null && piece.quality == TrackQuality.ULTIMATE && railGrid.containsKey(piece.key())) {
            return railGrid.get(piece.key());
        }
        if (railOnly) {
            return -1;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int best = -1;
        for (int cx = (bx - 6) >> 4; cx <= (bx + 6) >> 4; cx++) {
            for (int cz = (bz - 6) >> 4; cz <= (bz + 6) >> 4; cz++) {
                List<Conductor> list = byChunk.get(ChunkPos.asLong(cx, cz));
                if (list == null) {
                    continue;
                }
                for (Conductor c : list) {
                    if (c.kind() == Kind.SUBSTATION || Math.abs(c.pos().getY() - y) > 3) {
                        continue;
                    }
                    double dx = c.pos().getX() + 0.5 - x, dz = c.pos().getZ() + 0.5 - z;
                    if (dx * dx + dz * dz <= c.kind().cover * c.kind().cover && gridOf.containsKey(c.pos().asLong())) {
                        best = gridOf.get(c.pos().asLong());
                    }
                }
            }
        }
        return best;
    }

    /** Tire jusqu'a {@code need} FE de la grille (sous-stations a tour de role, chacune bornee a son debit par tick). */
    public long draw(int grid, long need, long now) {
        if (grid < 0 || grid >= stations.size() || need <= 0) {
            return 0;
        }
        if (now != tick) {
            tick = now;
            drawnThisTick.clear();
        }
        long got = 0;
        for (Conductor s : stations.get(grid)) {
            long key = s.pos().asLong();
            long room = rate(s.tier()) - drawnThisTick.get(key);
            long have = stored.get(key);
            long n = Math.min(need - got, Math.min(room, have));
            if (n > 0) {
                stored.put(key, have - n);
                drawnThisTick.addTo(key, n);
                got += n;
            }
            if (got >= need) {
                break;
            }
        }
        return got;
    }

    /** Energie que la grille peut encore donner ce tick (reserve et debit des sous-stations). */
    public long drawable(int grid, long now) {
        if (grid < 0 || grid >= stations.size()) {
            return 0;
        }
        if (now != tick) {
            tick = now;
            drawnThisTick.clear();
        }
        long sum = 0;
        for (Conductor s : stations.get(grid)) {
            sum += Math.max(0, Math.min(rate(s.tier()) - drawnThisTick.get(s.pos().asLong()), stored.get(s.pos().asLong())));
        }
        return sum;
    }

    /** Energie disponible dans une grille. */
    public long available(int grid) {
        if (grid < 0 || grid >= stations.size()) {
            return 0;
        }
        long sum = 0;
        for (Conductor s : stations.get(grid)) {
            sum += stored.get(s.pos().asLong());
        }
        return sum;
    }

    // ------------------------------------------------------------------ sauvegarde

    public ListTag save() {
        ListTag list = new ListTag();
        for (Conductor c : conductors.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", c.pos().asLong());
            t.putByte("kind", (byte) c.kind().ordinal());
            t.putByte("tier", (byte) c.tier());
            long e = stored.get(c.pos().asLong());
            if (e > 0) {
                t.putLong("e", e);
            }
            list.add(t);
        }
        return list;
    }

    public void load(ListTag list) {
        conductors.clear();
        byChunk.clear();
        stored.clear();
        for (Tag tag : list) {
            CompoundTag t = (CompoundTag) tag;
            Conductor c = new Conductor(BlockPos.of(t.getLong("pos")), Kind.byId(t.getByte("kind")), t.getByte("tier"));
            add(c);
            if (t.getLong("e") > 0) {
                stored.put(c.pos().asLong(), t.getLong("e"));
            }
        }
        dirty = true;
    }
}
