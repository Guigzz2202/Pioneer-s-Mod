package fr.pioneers.pneumatic.network;

import fr.pioneers.Pioneers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.39 : les hypertubes d'une dimension (verite, sauvegardee) : troncons courbes ({@link HypertubeCurve}) raccordes par leurs bouts, aux
 * stations ou entre eux. Les {@link HypertubeEntity} n'en sont que les vues (jamais sauvegardees) : recreees toutes les secondes la ou le
 * milieu du troncon est charge, comme les vehicules du rail. Chemins des cabines : {@link #routes} (de station en station, a travers les
 * raccords, embranchements compris).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HypertubeWorld extends SavedData {

    private static final String NAME = "pioneers_hypertubes";
    /** Deux bouts a moins de cette distance (blocs) sont raccordes. */
    static final double JOIN = 0.05;
    /** Tubes au plus par cote d'un ancrage (2 : un embranchement). */
    public static final int PORT_LIMIT = 2;
    /** Deux troncons s'enchainent si la direction d'arrivee et celle de depart font moins de 60 degres (pas de demi-tour a un embranchement). */
    private static final double CONTINUE = 0.5;

    /**
     * Un troncon : bouts, tangentes (sens A vers B), stations raccordees, tubes consommes ; 0.20.43 : deformation {@code d0..d2} (voir
     * {@link HypertubeCurve}).
     */
    public record Segment(int id, Vec3 a, Vec3 b, Vec3 ta, Vec3 tb, @Nullable BlockPos stationA, @Nullable BlockPos stationB, int tubes, Vec3 d0, Vec3 d1,
                          Vec3 d2) {

        public HypertubeCurve curve() {
            return new HypertubeCurve(a, b, ta, tb, new Vec3[]{d0, d1, d2});
        }

        Vec3[] dev() {
            return new Vec3[]{d0, d1, d2};
        }

        Vec3 mid() {
            return a.add(b).scale(0.5);
        }

        /** Direction de sortie du bout A (vers l'exterieur du troncon) ou B. */
        Vec3 out(boolean atA) {
            return atA ? ta.scale(-1) : tb;
        }

        /** Direction dans laquelle le tube quitte le bout A (vers l'interieur du troncon) ou B. */
        Vec3 port(boolean atA) {
            return atA ? ta : tb.scale(-1);
        }

        Segment withStations(@Nullable BlockPos sa, @Nullable BlockPos sb) {
            return new Segment(id, a, b, ta, tb, sa, sb, tubes, d0, d1, d2);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            put(t, "a", a);
            put(t, "b", b);
            put(t, "ta", ta);
            put(t, "tb", tb);
            put(t, "d0", d0);
            put(t, "d1", d1);
            put(t, "d2", d2);
            if (stationA != null) {
                t.putLong("sa", stationA.asLong());
            }
            if (stationB != null) {
                t.putLong("sb", stationB.asLong());
            }
            t.putInt("tubes", tubes);
            return t;
        }

        static Segment load(CompoundTag t) {
            return new Segment(t.getInt("id"), get(t, "a"), get(t, "b"), get(t, "ta"), get(t, "tb"), t.contains("sa") ? BlockPos.of(t.getLong("sa")) : null,
                  t.contains("sb") ? BlockPos.of(t.getLong("sb")) : null, t.getInt("tubes"), get(t, "d0"), get(t, "d1"), get(t, "d2"));
        }

        private static void put(CompoundTag t, String k, Vec3 v) {
            t.putDouble(k + "x", v.x);
            t.putDouble(k + "y", v.y);
            t.putDouble(k + "z", v.z);
        }

        private static Vec3 get(CompoundTag t, String k) {
            return new Vec3(t.getDouble(k + "x"), t.getDouble(k + "y"), t.getDouble(k + "z"));
        }
    }

    /** Chemin d'une cabine : station d'arrivee, points de l'axe (station de depart -> arrivee), troncons traverses, longueur. */
    public record Route(BlockPos station, List<Vec3> points, List<Integer> segments, double length) {
    }

    private final Map<Integer, Segment> segments = new LinkedHashMap<>();
    private int nextId = 1;
    /** Courbes deja calculees (non sauvees). */
    private final Map<Integer, HypertubeCurve> curves = new HashMap<>();
    /** Vues vivantes (serveur, non sauvees) : id du troncon -> entite. */
    private final Map<Integer, HypertubeEntity> views = new HashMap<>();

    public static HypertubeWorld get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HypertubeWorld::load, HypertubeWorld::new, NAME);
    }

    private static HypertubeWorld load(CompoundTag tag) {
        HypertubeWorld w = new HypertubeWorld();
        w.nextId = Math.max(1, tag.getInt("next"));
        for (Tag e : tag.getList("segments", Tag.TAG_COMPOUND)) {
            Segment s = Segment.load((CompoundTag) e);
            w.segments.put(s.id(), s);
            w.nextId = Math.max(w.nextId, s.id() + 1);
        }
        return w;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("next", nextId);
        ListTag list = new ListTag();
        segments.values().forEach(s -> list.add(s.save()));
        tag.put("segments", list);
        return tag;
    }

    @Nullable
    public Segment segment(int id) {
        return segments.get(id);
    }

    /** Courbe d'un troncon (mise en cache). */
    HypertubeCurve curve(Segment s) {
        return curves.computeIfAbsent(s.id(), k -> s.curve());
    }

    public boolean exists(int id) {
        return segments.containsKey(id);
    }

    /** Nouveau troncon entre deux ancres, sur la courbe donnee. */
    public Segment add(ServerLevel level, HypertubeAnchor start, HypertubeAnchor end, HypertubeCurve curve) {
        Vec3[] d = curve.deviation();
        Segment s = new Segment(nextId++, curve.a, curve.b, curve.ta, curve.tb, start.station(), end.station(), curve.tubes(), d[0], d[1], d[2]);
        segments.put(s.id(), s);
        setDirty();
        TubeRoutes.changed(level);
        spawnView(level, s);
        return s;
    }

    /** Retire un troncon (et sa vue). */
    @Nullable
    public Segment remove(ServerLevel level, int id) {
        Segment s = segments.remove(id);
        curves.remove(id);
        if (s != null) {
            setDirty();
            TubeRoutes.changed(level);
            HypertubeEntity view = views.remove(id);
            if (view != null) {
                view.discard();
            }
        }
        return s;
    }

    /** Station cassee : ses raccords sont retires de ses troncons (ils deviennent des bouts libres). */
    void stationRemoved(ServerLevel level, BlockPos station) {
        boolean changed = false;
        for (Segment s : new ArrayList<>(segments.values())) {
            if (station.equals(s.stationA()) || station.equals(s.stationB())) {
                Segment n = s.withStations(station.equals(s.stationA()) ? null : s.stationA(), station.equals(s.stationB()) ? null : s.stationB());
                segments.put(s.id(), n);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
            TubeRoutes.changed(level);
        }
    }

    /** Tubes qui quittent le point {@code p} dans la direction {@code dir} (un cote d'un ancrage). */
    public int portCount(Vec3 p, Vec3 dir) {
        int n = 0;
        for (Segment s : segments.values()) {
            if (s.a().distanceTo(p) < JOIN && s.port(true).dot(dir) > 0.9 || s.b().distanceTo(p) < JOIN && s.port(false).dot(dir) > 0.9) {
                n++;
            }
        }
        return n;
    }

    /** Bouts de troncon au point {@code p} (ancrage). */
    public int attached(Vec3 p) {
        int n = 0;
        for (Segment s : segments.values()) {
            if (s.a().distanceTo(p) < JOIN || s.b().distanceTo(p) < JOIN) {
                n++;
            }
        }
        return n;
    }

    /** Un troncon raccorde deja une station a ce point (une face de station ne prend qu'un tube). */
    boolean faceUsed(Vec3 p) {
        for (Segment s : segments.values()) {
            if (s.stationA() != null && s.a().distanceTo(p) < JOIN || s.stationB() != null && s.b().distanceTo(p) < JOIN) {
                return true;
            }
        }
        return false;
    }

    /** Troncon le plus proche d'un rayon (a moins de {@code tolerance} blocs de son axe), ou null. */
    @Nullable
    Segment aimed(Vec3 origin, Vec3 dir, double reach, double tolerance) {
        Segment best = null;
        double bestT = Double.MAX_VALUE;
        double[] hit = new double[1];
        for (Segment s : segments.values()) {
            if (curve(s).rayDistance(origin, dir, reach, hit) <= tolerance && hit[0] < bestT) {
                bestT = hit[0];
                best = s;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Vues
    // ------------------------------------------------------------------

    void viewAdded(HypertubeEntity e) {
        if (segments.containsKey(e.segmentId())) {
            HypertubeEntity old = views.put(e.segmentId(), e);
            if (old != null && old != e) {
                old.discard();
            }
        } else {
            e.discard();
        }
    }

    void viewRemoved(HypertubeEntity e) {
        views.remove(e.segmentId(), e);
    }

    private void spawnView(ServerLevel level, Segment s) {
        HypertubeEntity e = HypertubeEntity.create(level, s);
        level.addFreshEntity(e);
    }

    private void tickViews(ServerLevel level) {
        for (Segment s : segments.values()) {
            HypertubeEntity v = views.get(s.id());
            if ((v == null || v.isRemoved()) && level.hasChunkAt(BlockPos.containing(s.mid()))) {
                views.remove(s.id());
                spawnView(level, s);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
            HypertubeWorld w = level.getDataStorage().get(HypertubeWorld::load, NAME);
            if (w != null && !w.segments.isEmpty()) {
                w.tickViews(level);
            }
        }
    }

    // ------------------------------------------------------------------
    // Chemins
    // ------------------------------------------------------------------

    /** Troncon parcouru dans un sens (depuis A si {@code forward}). */
    private record Leg(int segment, boolean forward) {
    }

    /** Stations atteintes depuis {@code from} par les hypertubes (plus court chemin vers chacune). */
    public List<Route> routes(BlockPos from) {
        Map<Leg, Leg> parent = new HashMap<>();
        Map<Leg, Double> dist = new HashMap<>();
        ArrayDeque<Leg> queue = new ArrayDeque<>();
        for (Segment s : segments.values()) {
            if (from.equals(s.stationA())) {
                Leg l = new Leg(s.id(), true);
                parent.put(l, null);
                dist.put(l, curve(s).length());
                queue.add(l);
            }
            if (from.equals(s.stationB())) {
                Leg l = new Leg(s.id(), false);
                parent.put(l, null);
                dist.put(l, curve(s).length());
                queue.add(l);
            }
        }
        Map<BlockPos, Leg> best = new HashMap<>();
        Set<Integer> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            Leg leg = queue.poll();
            Segment s = segments.get(leg.segment());
            if (s == null) {
                continue;
            }
            seen.add(s.id());
            BlockPos station = leg.forward() ? s.stationB() : s.stationA();
            if (station != null) {
                if (!station.equals(from) && (!best.containsKey(station) || dist.get(leg) < dist.get(best.get(station)))) {
                    best.put(station, leg);
                }
                continue;
            }
            Vec3 end = leg.forward() ? s.b() : s.a();
            Vec3 arrive = leg.forward() ? s.tb() : s.ta().scale(-1);
            for (Segment o : segments.values()) {
                if (o.id() == s.id() || seen.contains(o.id())) {
                    continue;
                }
                //0.20.43 : on ne repart que dans le prolongement (traversee d'un ancrage, embranchement), jamais en demi-tour
                Leg next = o.a().distanceTo(end) < JOIN && o.port(true).dot(arrive) > CONTINUE ? new Leg(o.id(), true)
                           : o.b().distanceTo(end) < JOIN && o.port(false).dot(arrive) > CONTINUE ? new Leg(o.id(), false) : null;
                if (next != null && !parent.containsKey(next)) {
                    parent.put(next, leg);
                    dist.put(next, dist.get(leg) + curve(o).length());
                    queue.add(next);
                }
            }
        }
        List<Route> out = new ArrayList<>();
        for (Map.Entry<BlockPos, Leg> e : best.entrySet()) {
            List<Leg> legs = new ArrayList<>();
            for (Leg l = e.getValue(); l != null; l = parent.get(l)) {
                legs.add(0, l);
            }
            List<Vec3> points = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            points.add(Vec3.atCenterOf(from));
            for (Leg l : legs) {
                List<Vec3> pts = curve(segments.get(l.segment())).points();
                ids.add(l.segment());
                if (l.forward()) {
                    points.addAll(pts);
                } else {
                    for (int i = pts.size() - 1; i >= 0; i--) {
                        points.add(pts.get(i));
                    }
                }
            }
            points.add(Vec3.atCenterOf(e.getKey()));
            double length = 0;
            for (int i = 1; i < points.size(); i++) {
                length += points.get(i).distanceTo(points.get(i - 1));
            }
            out.add(new Route(e.getKey(), List.copyOf(points), List.copyOf(ids), length));
        }
        return out;
    }
}
