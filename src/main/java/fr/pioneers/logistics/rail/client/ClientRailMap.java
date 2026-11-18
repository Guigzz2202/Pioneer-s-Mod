package fr.pioneers.logistics.rail.client;

import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.Delivery;
import fr.pioneers.logistics.rail.logistics.RailAlert;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.track.TrackPath;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.track.TrackShape;
import fr.pioneers.logistics.rail.train.Train;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : copie client du chemin de fer pour la carte O : voies (segments a plat, par qualite), occupation, signaux, gares, trains (centres
 * des vehicules), livraisons et alertes. Recue avec l'etat de la carte ({@code RailMapData}).
 */
public final class ClientRailMap {

    private ClientRailMap() {
    }

    /** Segment de voie a plat (monde) : couleur de la qualite, piece. */
    public record Segment(float x0, float z0, float x1, float z1, long anchor, TrackQuality quality, Piece.Kind kind) {}

    public record MapTrain(UUID id, String name, Train.Status status, boolean auto, boolean logistic, String group, String dest, float speed,
                           Train.TempKind temp, float[] xz) {

        public float headX() {
            return xz.length == 0 ? 0 : xz[0];
        }

        public float headZ() {
            return xz.length < 2 ? 0 : xz[1];
        }
    }

    public record MapStation(BlockPos pos, String name, StationLogistics.Role role, int limit, int coming, boolean harbor) {

        /** 0.20.31 : « n train(s) attendu(s) », ou « n navire(s) attendu(s) » pour un port. */
        public String comingKey() {
            return harbor ? "naval.pioneers.map.coming" : "rail.pioneers.map.coming";
        }
    }

    public record MapDelivery(Delivery delivery, String from, String to, String train, long carried) {}

    public record MapAlert(RailAlert.Type type, BlockPos pos, String subject, int age) {}

    public static List<Segment> segments = List.of();
    public static LongOpenHashSet busy = new LongOpenHashSet();
    public static List<MapTrain> trains = List.of();
    public static List<MapStation> stations = List.of();
    public static long[] signals = new long[0];
    public static int[] aspects = new int[0];
    public static List<MapDelivery> deliveries = List.of();
    public static List<MapAlert> alerts = List.of();
    /** 0.20.27 : liaisons des balises maritimes, routes restantes des navires (x, z en blocs, a la suite), balises. */
    public static List<float[]> seaLanes = List.of(), seaRoutes = List.of();
    public static long[] seaBeacons = new long[0];
    /** 1.10.3 : dimension de la derniere reception (la minimap ne reprend ces donnees que dans la meme dimension), vide sinon. */
    public static String dim = "";

    public static void receive(CompoundTag tag) {
        ClientLevel here = Minecraft.getInstance().level;
        dim = here == null ? "" : here.dimension().location().toString();
        if (tag.contains("tracks")) {
            long[] anchors = tag.getLongArray("tracks");
            int[] info = tag.getIntArray("trackInfo");
            List<Segment> out = new ArrayList<>();
            TrackShape[] shapes = TrackShape.values();
            TrackQuality[] qualities = TrackQuality.values();
            Piece.Kind[] kinds = Piece.Kind.values();
            float[] p = new float[3];
            for (int i = 0; i < anchors.length && i < info.length; i++) {
                int v = info[i];
                TrackShape shape = shapes[Mth.clamp(v & 0xFF, 0, shapes.length - 1)];
                TrackQuality q = qualities[Mth.clamp(v >> 8 & 0xF, 0, qualities.length - 1)];
                Piece.Kind k = kinds[Mth.clamp(v >> 12 & 0x1F, 0, kinds.length - 1)];
                BlockPos a = BlockPos.of(anchors[i]);
                for (TrackPath path : shape.paths()) {
                    int n = path.isArc() ? 4 : 1;
                    float px = 0, pz = 0;
                    for (int s = 0; s <= n; s++) {
                        path.point(path.length * s / n, p);
                        float wx = a.getX() + p[0] / 16F, wz = a.getZ() + p[2] / 16F;
                        if (s > 0) {
                            out.add(new Segment(px, pz, wx, wz, anchors[i], q, k));
                        }
                        px = wx;
                        pz = wz;
                    }
                }
            }
            segments = out;
        }
        busy = new LongOpenHashSet(tag.getLongArray("busy"));
        List<MapTrain> ts = new ArrayList<>();
        Train.TempKind[] temps = Train.TempKind.values();
        for (Tag t : tag.getList("trains", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int[] raw = c.getIntArray("cars");
            float[] xz = new float[raw.length];
            for (int i = 0; i < raw.length; i++) {
                xz[i] = raw[i] / 8F;
            }
            ts.add(new MapTrain(c.getUUID("id"), c.getString("name"), Train.Status.byId(c.getByte("status")), c.getBoolean("auto"), c.getBoolean("logistic"),
                  c.getString("group"), c.getString("dest"), c.getFloat("speed"), temps[Mth.clamp(c.getByte("temp"), 0, temps.length - 1)], xz));
        }
        trains = ts;
        List<MapStation> ss = new ArrayList<>();
        for (Tag t : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ss.add(new MapStation(BlockPos.of(c.getLong("pos")), c.getString("name"), StationLogistics.Role.byId(c.getByte("role")), c.getInt("limit"),
                  c.getInt("coming"), c.getBoolean("harbor")));
        }
        stations = ss;
        signals = tag.getLongArray("signals");
        aspects = tag.getIntArray("aspects");
        List<MapDelivery> ds = new ArrayList<>();
        for (Tag t : tag.getList("deliveries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ds.add(new MapDelivery(Delivery.load(c), c.getString("fromName"), c.getString("toName"), c.getString("trainName"), c.getLong("carried")));
        }
        deliveries = ds;
        List<MapAlert> as = new ArrayList<>();
        for (Tag t : tag.getList("alerts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            as.add(new MapAlert(RailAlert.Type.byId(c.getByte("type")), BlockPos.of(c.getLong("pos")), c.getString("subject"), c.getInt("age")));
        }
        alerts = as;
        seaLanes = polylines(tag.getList("seaLanes", Tag.TAG_INT_ARRAY));
        seaRoutes = polylines(tag.getList("seaRoutes", Tag.TAG_INT_ARRAY));
        seaBeacons = tag.getLongArray("seaBeacons");
    }

    private static List<float[]> polylines(ListTag list) {
        List<float[]> out = new ArrayList<>();
        for (Tag t : list) {
            int[] raw = ((IntArrayTag) t).getAsIntArray();
            float[] xz = new float[raw.length];
            for (int i = 0; i < raw.length; i++) {
                xz[i] = raw[i] / 8F;
            }
            out.add(xz);
        }
        return out;
    }

    @Nullable
    public static MapTrain train(UUID id) {
        for (MapTrain t : trains) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    @Nullable
    public static MapStation station(long pos) {
        for (MapStation s : stations) {
            if (s.pos().asLong() == pos) {
                return s;
            }
        }
        return null;
    }

    /** Nom de la ressource d'une alerte (sujet « item:minecraft:stone ») ou du train. */
    public static String subject(MapAlert a) {
        String s = a.subject();
        int colon = s.indexOf(':');
        if (colon > 0 && s.indexOf(':', colon + 1) > 0) {
            try {
                Cargo.Kind k = Cargo.Kind.valueOf(s.substring(0, colon).toUpperCase(Locale.ROOT));
                ResourceLocation id = ResourceLocation.tryParse(s.substring(colon + 1));
                return RailText.name(new Cargo(k, id)).getString();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    public static void clear() {
        segments = List.of();
        busy = new LongOpenHashSet();
        trains = List.of();
        stations = List.of();
        signals = new long[0];
        aspects = new int[0];
        deliveries = List.of();
        alerts = List.of();
        seaLanes = List.of();
        seaRoutes = List.of();
        seaBeacons = new long[0];
        dim = "";
    }
}
