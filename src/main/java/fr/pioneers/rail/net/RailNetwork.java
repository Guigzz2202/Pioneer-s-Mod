package fr.pioneers.rail.net;

import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.rail.track.TrackPath;
import fr.pioneers.rail.track.TrackQuality;
import fr.pioneers.rail.track.TrackShape;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
 * 0.20.0 : le reseau ferre d'une dimension, independant du chargement des chunks : pieces (copie logique des blocs de voie), points de
 * raccord, signaux, gares, et cantons (sections separees par les signaux).
 * <ul>
 *     <li>Les blocs de voie s'y inscrivent a la pose et s'en retirent a la casse ; a chaque chargement de chunk, ses pieces sont verifiees
 *     contre le monde ({@code RailWorld.validate}).</li>
 *     <li>Parcours : un {@link Step} sort par un bout ; les suites sont les bouts des pieces voisines au meme point, face a face
 *     ({@link #next}). Plusieurs suites = aiguillage pris par la pointe.</li>
 *     <li>Cantons : union des pieces raccordees par un point sans signal ; recalcules apres tout changement ({@link #sections}).</li>
 * </ul>
 */
public final class RailNetwork {

    /** Bout de chemin a un point de raccord. */
    public record Link(Piece piece, int path, boolean atB) {

        public TrackPath.End end() {
            TrackPath p = piece.path(path);
            return atB ? p.b : p.a;
        }

        /** Etape qui entre dans la piece par ce bout. */
        public Step entering() {
            return new Step(piece.key(), path, !atB);
        }
    }

    public enum SignalType {
        BLOCK, CHAIN;

        public static SignalType byId(int i) {
            return values()[Math.max(0, Math.min(1, i))];
        }
    }

    /** Signal : bloc pose a droite de la voie dans le sens qu'il commande ; il coupe le canton au point {@code port}. */
    public static final class Signal {

        public final BlockPos pos;
        public final SignalType type;
        public final Direction heading;
        public final PortKey port;
        /** Aspect calcule : 0 vert, 1 orange (reserve), 2 rouge, 3 bleu (chaine, sortie partielle). */
        public int aspect;

        public Signal(BlockPos pos, SignalType type, Direction heading, PortKey port) {
            this.pos = pos.immutable();
            this.type = type;
            this.heading = heading;
            this.port = port;
        }
    }

    /** Gare : panneau pose a droite de la voie ; la tete du train s'arrete au point {@code port} en roulant vers {@code heading}. */
    public static final class Station {

        public final BlockPos pos;
        public final Direction heading;
        public final PortKey port;
        public String name;
        /** Limite de trains (-1 : sans limite, 0 : fermee). */
        public int limit = -1;
        /** 0.20.5 : role logistique (depot, fournisseur, demandeur). */
        public StationLogistics logistics = new StationLogistics();

        public Station(BlockPos pos, Direction heading, PortKey port, String name) {
            this.pos = pos.immutable();
            this.heading = heading;
            this.port = port;
            this.name = name;
        }
    }

    private final Long2ObjectOpenHashMap<Piece> anchors = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Piece> cover = new Long2ObjectOpenHashMap<>();
    private final Map<PortKey, List<Link>> ports = new HashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Signal> signals = new Long2ObjectOpenHashMap<>();
    private final Map<PortKey, List<Signal>> signalsAt = new HashMap<>();
    private final Long2ObjectOpenHashMap<Station> stations = new Long2ObjectOpenHashMap<>();
    private boolean sectionsDirty = true;
    private int sectionCount;
    /** Augmente a chaque changement de voie ou de signal (les trains refont leur chemin). */
    private int version;

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    @Nullable
    public Piece piece(long anchor) {
        return anchors.get(anchor);
    }

    /** Piece qui couvre ce bloc. */
    @Nullable
    public Piece at(BlockPos pos) {
        return cover.get(pos.asLong());
    }

    public Collection<Piece> pieces() {
        return anchors.values();
    }

    public void add(Piece piece) {
        remove(piece.anchor);
        for (BlockPos off : piece.shape.blocks()) {
            long b = piece.anchor.offset(off).asLong();
            Piece old = cover.get(b);
            if (old != null && old != piece) {
                remove(old.anchor);
            }
        }
        anchors.put(piece.key(), piece);
        for (BlockPos off : piece.shape.blocks()) {
            BlockPos b = piece.anchor.offset(off);
            cover.put(b.asLong(), piece);
            byChunk.computeIfAbsent(ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4), k -> new LongOpenHashSet()).add(piece.key());
        }
        List<TrackPath> paths = piece.paths();
        for (int i = 0; i < paths.size(); i++) {
            TrackPath p = paths.get(i);
            ports.computeIfAbsent(PortKey.of(piece.anchor, p.a), k -> new ArrayList<>(2)).add(new Link(piece, i, false));
            if (p.b.open()) {
                ports.computeIfAbsent(PortKey.of(piece.anchor, p.b), k -> new ArrayList<>(2)).add(new Link(piece, i, true));
            }
        }
        changed();
    }

    @Nullable
    public Piece remove(BlockPos anchor) {
        Piece piece = anchors.remove(anchor.asLong());
        if (piece == null) {
            return null;
        }
        for (BlockPos off : piece.shape.blocks()) {
            BlockPos b = piece.anchor.offset(off);
            if (cover.get(b.asLong()) == piece) {
                cover.remove(b.asLong());
            }
            LongOpenHashSet set = byChunk.get(ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4));
            if (set != null) {
                set.remove(piece.key());
            }
        }
        for (TrackPath p : piece.paths()) {
            for (TrackPath.End e : new TrackPath.End[]{p.a, p.b}) {
                PortKey k = PortKey.of(piece.anchor, e);
                List<Link> links = ports.get(k);
                if (links != null) {
                    links.removeIf(l -> l.piece == piece);
                    if (links.isEmpty()) {
                        ports.remove(k);
                    }
                }
            }
        }
        changed();
        return piece;
    }

    /** Ancres des pieces qui touchent ce chunk. */
    public long[] inChunk(long chunk) {
        LongOpenHashSet set = byChunk.get(chunk);
        return set == null ? new long[0] : set.toLongArray();
    }

    public List<Link> links(PortKey key) {
        List<Link> list = ports.get(key);
        return list == null ? List.of() : list;
    }

    private void changed() {
        sectionsDirty = true;
        version++;
    }

    public int version() {
        return version;
    }

    // ------------------------------------------------------------------
    // Parcours
    // ------------------------------------------------------------------

    public float length(Step s) {
        Piece p = anchors.get(s.anchor());
        return p == null ? 16 : p.path(s.path()).length;
    }

    /** Bout de sortie d'une etape. */
    @Nullable
    public TrackPath.End exit(Step s) {
        Piece p = anchors.get(s.anchor());
        if (p == null) {
            return null;
        }
        TrackPath path = p.path(s.path());
        return s.forward() ? path.b : path.a;
    }

    @Nullable
    public PortKey exitPort(Step s) {
        Piece p = anchors.get(s.anchor());
        if (p == null) {
            return null;
        }
        TrackPath path = p.path(s.path());
        TrackPath.End e = s.forward() ? path.b : path.a;
        return e.open() ? PortKey.of(p.anchor, e) : null;
    }

    /** Etapes qui suivent (bouts voisins au point de sortie, face a face). Vide : bout de voie ou butoir. */
    public List<Step> next(Step s) {
        Piece p = anchors.get(s.anchor());
        if (p == null) {
            return List.of();
        }
        TrackPath path = p.path(s.path());
        TrackPath.End e = s.forward() ? path.b : path.a;
        if (!e.open()) {
            return List.of();
        }
        List<Link> links = ports.get(PortKey.of(p.anchor, e));
        if (links == null) {
            return List.of();
        }
        List<Step> out = new ArrayList<>(2);
        Direction facing = e.face().getOpposite();
        //0.20.6 : les deux bouts doivent aussi se suivre (pas de coude entre une corde a 45 degres et une droite)
        float[] mine = new float[2], other = new float[2];
        path.outward(s.forward(), mine);
        for (Link l : links) {
            if (l.piece != p && l.end().face() == facing) {
                l.piece.path(l.path).outward(l.atB, other);
                if (mine[0] * other[0] + mine[1] * other[1] < -0.9F) {
                    out.add(l.entering());
                }
            }
        }
        return out;
    }

    /** Etapes qui precedent (pour reculer). */
    public List<Step> previous(Step s) {
        List<Step> back = next(s.reverse());
        List<Step> out = new ArrayList<>(back.size());
        for (Step b : back) {
            out.add(b.reverse());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Signaux et gares
    // ------------------------------------------------------------------

    public void addSignal(Signal signal) {
        removeSignal(signal.pos);
        signals.put(signal.pos.asLong(), signal);
        signalsAt.computeIfAbsent(signal.port, k -> new ArrayList<>(1)).add(signal);
        changed();
    }

    public void removeSignal(BlockPos pos) {
        Signal s = signals.remove(pos.asLong());
        if (s != null) {
            List<Signal> list = signalsAt.get(s.port);
            if (list != null) {
                list.remove(s);
                if (list.isEmpty()) {
                    signalsAt.remove(s.port);
                }
            }
            changed();
        }
    }

    public Collection<Signal> signals() {
        return signals.values();
    }

    @Nullable
    public Signal signal(BlockPos pos) {
        return signals.get(pos.asLong());
    }

    /** Signal qui commande le passage du point dans ce sens, ou null. */
    @Nullable
    public Signal signalFor(PortKey port, Direction heading) {
        List<Signal> list = signalsAt.get(port);
        if (list != null) {
            for (Signal s : list) {
                if (s.heading == heading) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Un signal existe-t-il a ce point (dans un sens ou l'autre) ? */
    public boolean hasSignal(PortKey port) {
        return signalsAt.containsKey(port);
    }

    public void addStation(Station station) {
        stations.put(station.pos.asLong(), station);
    }

    public void removeStation(BlockPos pos) {
        stations.remove(pos.asLong());
    }

    @Nullable
    public Station station(BlockPos pos) {
        return stations.get(pos.asLong());
    }

    @Nullable
    public Station station(long pos) {
        return stations.get(pos);
    }

    public Collection<Station> stations() {
        return stations.values();
    }

    public List<Station> stationsNamed(String name) {
        List<Station> out = new ArrayList<>();
        for (Station s : stations.values()) {
            if (s.name.equalsIgnoreCase(name)) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Cantons
    // ------------------------------------------------------------------

    /** Recalcule les cantons si besoin ; renvoie leur nombre. */
    public int sections() {
        if (!sectionsDirty) {
            return sectionCount;
        }
        sectionsDirty = false;
        List<Piece> list = new ArrayList<>(anchors.values());
        Map<Piece, Integer> index = new HashMap<>(list.size() * 2);
        for (int i = 0; i < list.size(); i++) {
            index.put(list.get(i), i);
        }
        int[] parent = new int[list.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (Map.Entry<PortKey, List<Link>> e : ports.entrySet()) {
            if (signalsAt.containsKey(e.getKey())) {
                continue;
            }
            List<Link> links = e.getValue();
            for (int i = 1; i < links.size(); i++) {
                union(parent, index.get(links.get(0).piece), index.get(links.get(i).piece));
            }
        }
        Map<Integer, Integer> ids = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            int root = find(parent, i);
            list.get(i).section = ids.computeIfAbsent(root, r -> ids.size());
        }
        sectionCount = ids.size();
        return sectionCount;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        long[] keys = new long[anchors.size()];
        byte[] data = new byte[anchors.size() * 4];
        int i = 0;
        for (Piece p : anchors.values()) {
            keys[i] = p.key();
            data[i * 4] = (byte) p.shape.ordinal();
            data[i * 4 + 1] = (byte) p.kind.ordinal();
            data[i * 4 + 2] = (byte) p.quality.ordinal();
            data[i * 4 + 3] = (byte) p.route;
            i++;
        }
        tag.putLongArray("pieces", keys);
        tag.putByteArray("data", data);
        ListTag sig = new ListTag();
        for (Signal s : signals.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", s.pos.asLong());
            t.putByte("type", (byte) s.type.ordinal());
            t.putByte("heading", (byte) s.heading.get3DDataValue());
            t.putLong("port", s.port.block());
            t.putByte("bits", (byte) s.port.bits());
            sig.add(t);
        }
        tag.put("signals", sig);
        ListTag st = new ListTag();
        for (Station s : stations.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", s.pos.asLong());
            t.putByte("heading", (byte) s.heading.get3DDataValue());
            t.putLong("port", s.port.block());
            t.putByte("bits", (byte) s.port.bits());
            t.putString("name", s.name);
            t.putInt("limit", s.limit);
            if (s.logistics.active() || !s.logistics.entries.isEmpty()) {
                t.put("logi", s.logistics.save());
            }
            st.add(t);
        }
        tag.put("stations", st);
        return tag;
    }

    public void load(CompoundTag tag) {
        long[] keys = tag.getLongArray("pieces");
        byte[] data = tag.getByteArray("data");
        TrackShape[] shapes = TrackShape.values();
        for (int i = 0; i < keys.length && i * 4 + 3 < data.length; i++) {
            int shape = data[i * 4] & 255;
            if (shape >= shapes.length) {
                continue;
            }
            add(new Piece(BlockPos.of(keys[i]), shapes[shape], Piece.Kind.byId(data[i * 4 + 1]), TrackQuality.byId(data[i * 4 + 2]), data[i * 4 + 3]));
        }
        for (Tag t : tag.getList("signals", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            addSignal(new Signal(BlockPos.of(c.getLong("pos")), SignalType.byId(c.getByte("type")), Direction.from3DDataValue(c.getByte("heading")),
                  new PortKey(c.getLong("port"), c.getByte("bits"))));
        }
        for (Tag t : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Station s = new Station(BlockPos.of(c.getLong("pos")), Direction.from3DDataValue(c.getByte("heading")), new PortKey(c.getLong("port"), c.getByte("bits")),
                  c.getString("name"));
            s.limit = c.getInt("limit");
            if (c.contains("logi")) {
                s.logistics = StationLogistics.load(c.getCompound("logi"));
            }
            addStation(s);
        }
    }
}
