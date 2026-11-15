package fr.pioneers.naval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.27 : balise maritime (demande de Guillaume : « le joueur les relie lui-meme pour les longs trajets ; un navire qui voit une balise
 * sur sa route connait les destinations possibles via ces balises »). La verite est dans {@link NavalWorld} : position du bloc, point d'eau
 * ou les navires la passent ({@link #ax}, {@link #az}), balises reliees. Chaque liaison est une {@link Lane} : la route maritime entre les
 * deux balises, calculee une fois par le planificateur (au large des cotes) et gardee.
 */
public final class SeaBeacon {

    /** Distance (blocs) a laquelle un navire « voit » une balise depuis son depart ou son port d'arrivee. */
    public static final double VIEW = 256;
    /** Liaison la plus longue (blocs, a vol d'oiseau). */
    public static final double MAX_LINK = 6000;

    public final long pos;
    /** Point d'eau de la balise (blocs) : la ou les routes la passent. */
    public double ax, az;
    public final Set<Long> links = new LinkedHashSet<>();

    public SeaBeacon(long pos) {
        this.pos = pos;
        BlockPos p = BlockPos.of(pos);
        ax = p.getX() + 0.5;
        az = p.getZ() + 0.5;
    }

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putDouble("ax", ax);
        t.putDouble("az", az);
        t.putLongArray("links", links.stream().mapToLong(Long::longValue).toArray());
        return t;
    }

    public static SeaBeacon load(CompoundTag t) {
        SeaBeacon b = new SeaBeacon(t.getLong("pos"));
        if (t.contains("ax")) {
            b.ax = t.getDouble("ax");
            b.az = t.getDouble("az");
        }
        for (long l : t.getLongArray("links")) {
            b.links.add(l);
        }
        return b;
    }

    /** Cle d'une liaison (ordre indifferent). */
    public record Pair(long a, long b) {

        public static Pair of(long x, long y) {
            return x < y ? new Pair(x, y) : new Pair(y, x);
        }
    }

    /** Route maritime d'une liaison : en calcul, trouvee (points de {@link Pair#a} vers {@link Pair#b}), ou impossible. */
    public static final class Lane {

        public enum State { PENDING, OK, NONE }

        public final Pair pair;
        public State state = State.PENDING;
        public List<double[]> points = List.of();
        public double length;

        public Lane(Pair pair) {
            this.pair = pair;
        }

        /** Points dans le sens de {@code from} vers l'autre balise. */
        public List<double[]> from(long from) {
            if (from == pair.a()) {
                return points;
            }
            List<double[]> r = new ArrayList<>(points);
            Collections.reverse(r);
            return r;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("a", pair.a());
            t.putLong("b", pair.b());
            t.putByte("state", (byte) (state == State.PENDING ? 0 : state.ordinal()));
            int[] xz = new int[points.size() * 2];
            for (int i = 0; i < points.size(); i++) {
                xz[2 * i] = (int) Math.round(points.get(i)[0] * 8);
                xz[2 * i + 1] = (int) Math.round(points.get(i)[1] * 8);
            }
            t.putIntArray("xz", xz);
            return t;
        }

        @Nullable
        public static Lane load(CompoundTag t) {
            Lane l = new Lane(Pair.of(t.getLong("a"), t.getLong("b")));
            int st = t.getByte("state");
            l.state = st == 1 ? State.OK : st == 2 ? State.NONE : State.PENDING;
            int[] xz = t.getIntArray("xz");
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i + 1 < xz.length; i += 2) {
                pts.add(new double[]{xz[i] / 8.0, xz[i + 1] / 8.0});
            }
            l.points = pts;
            l.length = ShipPathfinder.length(pts);
            return l;
        }
    }

    static ListTag saveLanes(Iterable<Lane> lanes) {
        ListTag l = new ListTag();
        for (Lane lane : lanes) {
            if (lane.state != Lane.State.PENDING) {
                l.add(lane.save());
            }
        }
        return l;
    }

    static List<Lane> loadLanes(ListTag list) {
        List<Lane> out = new ArrayList<>();
        for (Tag t : list) {
            Lane l = Lane.load((CompoundTag) t);
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }
}
