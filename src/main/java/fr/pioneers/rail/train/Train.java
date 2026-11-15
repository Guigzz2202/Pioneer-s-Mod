package fr.pioneers.rail.train;

import fr.pioneers.rail.net.Piece;
import fr.pioneers.rail.net.RailNetwork;
import fr.pioneers.rail.net.Step;
import fr.pioneers.rail.track.TrackPath;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : un train (un ou plusieurs vehicules attelees) : sa trace sur le reseau, sa vitesse, son mode (manuel ou automatique) et son
 * horaire.
 * <p>
 * <b>Trace</b> : la suite des chemins parcourus de la queue a la tete ({@link #trail}), avec la position de la tete dans le dernier
 * ({@link #front}) et celle de la queue dans le premier ({@link #back}) ; la longueur entre les deux vaut toujours 16 px par vehicule. La
 * queue suit donc exactement le chemin pris par la tete (aiguillages compris). Reculer allonge la trace cote queue.
 * <p>
 * Le vehicule 0 est en tete ; le centre du vehicule i est a 8 + 16 i px derriere la tete.
 */
public final class Train {

    public enum Status {
        IDLE, MANUAL, RUNNING, WAIT_SIGNAL, WAIT_TRAIN, AT_STATION, NO_PATH, DEST_FULL, NO_FUEL, DERAILED, NO_SCHEDULE,
        /** 0.20.27 : navire : route en cours de calcul (sondage des eaux, recherche au large des cotes). */
        CHARTING;

        public static Status byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    /** Choix de la suite quand plusieurs chemins s'offrent (aiguillage pris par la pointe). */
    public interface Router {

        @Nullable
        Step choose(Train train, Step from, List<Step> options, boolean ahead);
    }

    public static final float CAR = 16;

    public final UUID id;
    public String name = "";
    public final List<Car> cars = new ArrayList<>();
    /** Ce tick : vitesse perdue aux freins (px/t) et acceleration a pleine puissance (recuperation des electriques). */
    public transient float brakeLoss, accel;
    final List<Step> trail = new ArrayList<>();
    float front, back;
    /** Vitesse (px/tick), positive vers la tete. */
    public float speed;
    /** Distance parcourue (px), pour l'animation des roues. */
    public float odometer;
    public boolean auto;
    public Schedule schedule = new Schedule();
    public int stopIndex;
    public Status status = Status.IDLE;
    /** Gare visee (position du panneau), 0 = aucune. */
    public long destination;
    /** Chemin prevu apres l'etape de tete. */
    public final List<Step> plan = new ArrayList<>();
    public int planIndex;
    public int planVersion = -1;
    /** Arrivee a quai (tick du monde) et derniere activite de chargement. */
    public long arrivedAt, lastActivity;
    /** Ticks d'arret devant un signal ou un train. */
    public int blockedTicks;
    /** Commande du conducteur : -1..1 (avant / frein, puis marche arriere) et choix d'aiguille (-1 gauche, 0 tout droit, 1 droite). */
    public float throttle;
    /** 1.9 (MAJ RP) : conducteur a bord (non sauve) : facteur de vitesse de la Logistique, distance conduite pour l'XP (px). */
    public float driverBoost = 1;
    @javax.annotation.Nullable
    public java.util.UUID driver;
    public float drivenPx;
    public int steer;
    /** Ticks depuis la derniere commande du conducteur (au-dela : roue libre). */
    public int driverIdle = 1000;
    /** Cantons reserves (ordre du chemin) et ceux deja entres. */
    public final LongArrayList reserved = new LongArrayList();
    public final LongOpenHashSet entered = new LongOpenHashSet();
    /** Pieces occupees (ancres), recalculees apres chaque deplacement. */
    public final LongOpenHashSet occupied = new LongOpenHashSet();
    public int retry;
    /** Traction utilisee ce tick (0..1) : fumee, animation. */
    public float effort;

    // ------------------------------------------------------------------ 0.20.5 : groupes, interruptions, livraisons

    /** Arrets temporaires : interruption, livraison du repartiteur, envoi (carte, PioScript). */
    public enum TempKind {
        NONE, INTERRUPT, DELIVERY, ORDER
    }

    /** Groupe (horaire partage), "" : aucun. */
    public String group = "";
    /** Interruptions activees (train sans groupe ; sinon celles du groupe). */
    public final List<String> interrupts = new ArrayList<>();
    /** Train logistique : le repartiteur peut l'envoyer livrer depuis un depot. */
    public boolean logistic;
    public final List<Schedule.Stop> temp = new ArrayList<>();
    public int tempIndex;
    public TempKind tempKind = TempKind.NONE;
    /** Nom de l'interruption en cours. */
    public String tempName = "";
    @Nullable
    public UUID delivery;
    /** Ticks passes sans chemin ni place (declenche les interruptions). */
    public int stuckTicks;
    /** Deja coupe par un rail de detelage a cet arret. */
    public transient boolean uncoupled;

    public boolean inTemp() {
        return !temp.isEmpty() && tempIndex < temp.size();
    }

    /** Arret vise : temporaire, sinon celui de l'horaire (null : rien). */
    @Nullable
    public Schedule.Stop currentStop() {
        if (inTemp()) {
            return temp.get(tempIndex);
        }
        if (schedule.isEmpty()) {
            return null;
        }
        return schedule.stops.get(Math.floorMod(stopIndex, schedule.stops.size()));
    }

    public void clearTemp() {
        temp.clear();
        tempIndex = 0;
        tempKind = TempKind.NONE;
        tempName = "";
    }

    public Train(UUID id) {
        this.id = id;
    }

    public Train() {
        this(UUID.randomUUID());
    }

    public float length() {
        return cars.size() * CAR;
    }

    public List<Step> trail() {
        return Collections.unmodifiableList(trail);
    }

    public Step head() {
        return trail.get(trail.size() - 1);
    }

    public Step tail() {
        return trail.get(0);
    }

    public float headOffset() {
        return front;
    }

    public float tailOffset() {
        return back;
    }

    public boolean hasLocomotive() {
        for (Car c : cars) {
            if (c.type.locomotive) {
                return true;
            }
        }
        return false;
    }

    /**
     * 0.20.31 : locomotive d'un bout du train (le vehicule du bout, ou le suivant si le bout est un tender), ou null. La draisine ne compte
     * pas.
     */
    @Nullable
    public Car endLocomotive(boolean tail) {
        int n = cars.size();
        for (int k = 0; k < Math.min(2, n); k++) {
            Car c = cars.get(tail ? n - 1 - k : k);
            if (c.type.locomotive && c.type != CarType.DRAISINE) {
                return c;
            }
            if (c.type != CarType.TENDER) {
                return null;
            }
        }
        return null;
    }

    /** 0.20.31 : une locomotive a chaque bout (quel que soit le sens dans lequel on les a posees). */
    public boolean bothEnds() {
        Car h = endLocomotive(false), t = endLocomotive(true);
        return h != null && t != null && h != t;
    }

    /**
     * 0.20.31 (Guillaume : « gerer les trains a double sens : une locomotive a l'avant et une a l'arriere ») : les locomotives des deux bouts
     * regardent vers l'exterieur (celle de queue est retournee), comme une rame reversible. Sans effet sinon.
     */
    public boolean orientEnds() {
        if (!bothEnds()) {
            return false;
        }
        Car h = endLocomotive(false), t = endLocomotive(true);
        boolean changed = h.flipped || !t.flipped;
        h.flipped = false;
        t.flipped = true;
        return changed;
    }

    /** Peut-il partir vers l'arriere en automatique (locomotive retournee, ou une locomotive a chaque bout) ? */
    public boolean bidirectional() {
        if (bothEnds()) {
            return true;
        }
        boolean fwd = false, rev = false;
        for (Car c : cars) {
            if (c.type.locomotive && c.type != CarType.DRAISINE) {
                if (c.flipped) {
                    rev = true;
                } else {
                    fwd = true;
                }
            }
        }
        return fwd && rev;
    }

    public boolean canRunAuto() {
        for (Car c : cars) {
            if (c.type.locomotive && c.type != CarType.DRAISINE && !c.flipped) {
                return true;
            }
        }
        return bidirectional();
    }

    public float mass() {
        float m = 0;
        for (Car c : cars) {
            m += c.mass();
        }
        return Math.max(0.5F, m);
    }

    // ------------------------------------------------------------------
    // Trace
    // ------------------------------------------------------------------

    /** Pose initiale : un chemin, tete a {@code headAlong} px du debut de l'etape. */
    public void place(Step step, float headAlong) {
        trail.clear();
        trail.add(step);
        front = headAlong;
        back = headAlong;
    }

    /** Allonge la tete de d px (choix des suites par le routeur) ; renvoie la distance faite. */
    public float extendHead(RailNetwork net, float d, Router router) {
        float done = 0;
        while (d > 1e-4F) {
            Step last = head();
            float len = net.length(last);
            float room = len - front;
            if (d <= room) {
                front += d;
                done += d;
                break;
            }
            front = len;
            done += Math.max(0, room);
            d -= Math.max(0, room);
            List<Step> options = net.next(last);
            Step nx = options.isEmpty() ? null : router.choose(this, last, options, true);
            if (nx == null) {
                break;
            }
            trail.add(nx);
            front = 0;
        }
        return done;
    }

    /** Allonge la queue de d px (vers l'arriere) ; renvoie la distance faite. */
    public float extendTail(RailNetwork net, float d, Router router) {
        float done = 0;
        while (d > 1e-4F) {
            if (d <= back) {
                back -= d;
                done += d;
                break;
            }
            done += back;
            d -= back;
            back = 0;
            Step first = tail();
            List<Step> options = net.previous(first);
            Step pv = options.isEmpty() ? null : router.choose(this, first, options, false);
            if (pv == null) {
                break;
            }
            trail.add(0, pv);
            back = net.length(pv);
        }
        return done;
    }

    void trimTail(RailNetwork net, float d) {
        back += d;
        while (trail.size() > 1 && back >= net.length(trail.get(0))) {
            back -= net.length(trail.get(0));
            trail.remove(0);
        }
    }

    void trimHead(RailNetwork net, float d) {
        front -= d;
        while (trail.size() > 1 && front < 0) {
            trail.remove(trail.size() - 1);
            front += net.length(head());
        }
        front = Math.max(0, front);
    }

    /** Avance (d > 0) ou recule (d < 0) ; renvoie la distance faite (signee). */
    public float move(RailNetwork net, float d, Router router) {
        if (d > 0) {
            float m = extendHead(net, d, router);
            trimTail(net, m);
            odometer += m;
            return m;
        }
        if (d < 0) {
            float m = extendTail(net, -d, router);
            trimHead(net, m);
            odometer -= m;
            return -m;
        }
        return 0;
    }

    /** 0.20.6 : plaque tournante, transbordeur : chaque etape du trace remplacee par une etape de meme longueur ; le chemin est a refaire. */
    public void remap(UnaryOperator<Step> f) {
        for (int i = 0; i < trail.size(); i++) {
            trail.set(i, f.apply(trail.get(i)));
        }
        plan.clear();
        planIndex = 0;
        planVersion = -1;
    }

    /** Toutes les etapes existent-elles encore ? */
    public boolean intact(RailNetwork net) {
        for (Step s : trail) {
            Piece p = net.piece(s.anchor());
            if (p == null || s.path() >= p.paths().size()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Positions
    // ------------------------------------------------------------------

    /** Position sur la trace a {@code dist} px derriere la tete : point (blocs du monde) et direction vers la tete (unitaire). */
    public boolean locate(RailNetwork net, float dist, double[] pos, float[] dir) {
        int i = trail.size() - 1;
        float along = front;
        float rem = dist;
        while (rem > along && i > 0) {
            rem -= along;
            i--;
            along = net.length(trail.get(i));
        }
        float s = Math.max(0, along - rem);
        Step step = trail.get(i);
        Piece piece = net.piece(step.anchor());
        if (piece == null) {
            return false;
        }
        TrackPath path = piece.path(step.path());
        float param = step.forward() ? s : path.length - s;
        float[] p = new float[3];
        path.point(param, p);
        pos[0] = piece.anchor.getX() + p[0] / 16.0;
        pos[1] = piece.anchor.getY() + p[1] / 16.0;
        pos[2] = piece.anchor.getZ() + p[2] / 16.0;
        if (dir != null) {
            path.tangent(param, dir);
            if (!step.forward()) {
                dir[0] = -dir[0];
                dir[1] = -dir[1];
                dir[2] = -dir[2];
            }
        }
        return true;
    }

    /** Etape et abscisse (le long du chemin, depuis A) a {@code dist} px derriere la tete. */
    @Nullable
    public Step stepAt(RailNetwork net, float dist, float[] alongOut) {
        int i = trail.size() - 1;
        float along = front;
        float rem = dist;
        while (rem > along && i > 0) {
            rem -= along;
            i--;
            along = net.length(trail.get(i));
        }
        Step step = trail.get(i);
        float s = Math.max(0, along - rem);
        if (alongOut != null) {
            alongOut[0] = step.forward() ? s : net.length(step) - s;
        }
        return step;
    }

    /** Pose d'un vehicule : centre (entre ses deux essieux), cap (degres, 0 = vers +z) et tangage (degres, positif = monte vers l'avant). */
    /** {@code valid} faux : une piece de la trace manque (voie cassee sous le train) ; la pose n'est pas a suivre (0.20.4). */
    public record Pose(Vec3 center, float yaw, float pitch, Vec3 dir, boolean valid) {}

    public Pose pose(RailNetwork net, int index) {
        double[] f = new double[3], r = new double[3];
        float c = 8 + CAR * index;
        boolean valid = !trail.isEmpty() && locate(net, Math.max(0, c - 5), f, null) & locate(net, c + 5, r, null);
        double dx = f[0] - r[0], dy = f[1] - r[1], dz = f[2] - r[2];
        double h = Math.sqrt(dx * dx + dz * dz);
        Car car = index < cars.size() ? cars.get(index) : null;
        if (car != null && car.flipped) {
            dx = -dx;
            dy = -dy;
            dz = -dz;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.max(1e-6, h)));
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Vec3 dir = len > 1e-9 ? new Vec3(dx / len, dy / len, dz / len) : new Vec3(0, 0, 1);
        return new Pose(new Vec3((f[0] + r[0]) / 2, (f[1] + r[1]) / 2, (f[2] + r[2]) / 2), yaw, pitch, dir, valid);
    }

    /** Pieces sous le train. */
    public void computeOccupied() {
        occupied.clear();
        for (Step s : trail) {
            occupied.add(s.anchor());
        }
    }

    /** Pente moyenne sous le train (montee vers la tete, en sinus). */
    public float grade(RailNetwork net) {
        float sum = 0, n = 0;
        for (Step s : trail) {
            Piece p = net.piece(s.anchor());
            if (p != null) {
                float g = p.path(s.path()).grade();
                sum += s.forward() ? g : -g;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    /** Vitesse limite des pieces sous le train (px/tick). */
    public float trackLimit(RailNetwork net) {
        float lim = Float.MAX_VALUE;
        for (Step s : trail) {
            Piece p = net.piece(s.anchor());
            if (p != null) {
                lim = Math.min(lim, p.limit(s.path()));
            }
        }
        return lim == Float.MAX_VALUE ? 0 : lim;
    }

    // ------------------------------------------------------------------
    // Retournement, coupe, attelage
    // ------------------------------------------------------------------

    /** Retourne le train : la queue devient la tete (vehicules dans l'ordre inverse, chacun retourne). */
    public void flip(RailNetwork net) {
        List<Step> rev = new ArrayList<>(trail.size());
        for (int i = trail.size() - 1; i >= 0; i--) {
            rev.add(trail.get(i).reverse());
        }
        float newFront = net.length(trail.get(0)) - back;
        float newBack = net.length(head()) - front;
        trail.clear();
        trail.addAll(rev);
        front = newFront;
        back = newBack;
        Collections.reverse(cars);
        for (Car c : cars) {
            c.flipped = !c.flipped;
        }
        speed = -speed;
        plan.clear();
        planIndex = 0;
    }

    /**
     * Reconstruit la trace d'un train de {@code length} px dont la tete est en ({@code head}, {@code headAlong}), en reculant sur les chemins
     * de {@code guide} (ancre + chemin, dans un sens ou l'autre).
     */
    public boolean rebuild(RailNetwork net, Step head, float headAlong, float length, Set<Long> guide) {
        trail.clear();
        trail.add(head);
        front = headAlong;
        back = headAlong;
        Router byGuide = (t, from, options, ahead) -> {
            for (Step o : options) {
                if (guide.contains(guideKey(o))) {
                    return o;
                }
            }
            return options.get(0);
        };
        float made = extendTail(net, length, byGuide);
        return made >= length - 0.01F;
    }

    public static long guideKey(Step s) {
        return s.anchor() * 31 + s.path();
    }

    public void guide(Set<Long> out) {
        for (Step s : trail) {
            out.add(guideKey(s));
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        ListTag cl = new ListTag();
        for (Car c : cars) {
            cl.add(c.save());
        }
        tag.put("cars", cl);
        ListTag tl = new ListTag();
        for (Step s : trail) {
            tl.add(s.save());
        }
        tag.put("trail", tl);
        tag.putFloat("front", front);
        tag.putFloat("back", back);
        tag.putFloat("speed", speed);
        tag.putFloat("odo", odometer % 100_000);
        tag.putBoolean("auto", auto);
        tag.put("schedule", schedule.save());
        tag.putInt("stop", stopIndex);
        tag.putLong("dest", destination);
        tag.putLong("arrived", arrivedAt);
        tag.putLong("activity", lastActivity);
        tag.putByte("status", (byte) status.ordinal());
        if (!group.isEmpty()) {
            tag.putString("group", group);
        }
        if (!interrupts.isEmpty()) {
            ListTag il = new ListTag();
            interrupts.forEach(n -> il.add(StringTag.valueOf(n)));
            tag.put("interrupts", il);
        }
        tag.putBoolean("logistic", logistic);
        if (inTemp()) {
            tag.put("temp", Schedule.saveStops(temp));
            tag.putInt("tempIndex", tempIndex);
            tag.putByte("tempKind", (byte) tempKind.ordinal());
            tag.putString("tempName", tempName);
        }
        if (delivery != null) {
            tag.putUUID("delivery", delivery);
        }
        return tag;
    }

    public static Train load(CompoundTag tag) {
        Train t = new Train(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID());
        t.name = tag.getString("name");
        for (Tag c : tag.getList("cars", Tag.TAG_COMPOUND)) {
            t.cars.add(Car.load((CompoundTag) c));
        }
        for (Tag s : tag.getList("trail", Tag.TAG_COMPOUND)) {
            t.trail.add(Step.load((CompoundTag) s));
        }
        t.front = tag.getFloat("front");
        t.back = tag.getFloat("back");
        t.speed = tag.getFloat("speed");
        t.odometer = tag.getFloat("odo");
        t.auto = tag.getBoolean("auto");
        t.schedule = Schedule.load(tag.getCompound("schedule"));
        t.stopIndex = tag.getInt("stop");
        t.destination = tag.getLong("dest");
        t.arrivedAt = tag.getLong("arrived");
        t.lastActivity = tag.getLong("activity");
        t.status = Status.byId(tag.getByte("status"));
        t.group = tag.getString("group");
        for (Tag n : tag.getList("interrupts", Tag.TAG_STRING)) {
            if (t.interrupts.size() < 16) {
                t.interrupts.add(n.getAsString());
            }
        }
        t.logistic = tag.getBoolean("logistic");
        t.temp.addAll(Schedule.loadStops(tag.getList("temp", Tag.TAG_COMPOUND), 16));
        t.tempIndex = tag.getInt("tempIndex");
        t.tempKind = TempKind.values()[Math.max(0, Math.min(TempKind.values().length - 1, tag.getByte("tempKind")))];
        t.tempName = tag.getString("tempName");
        if (!t.inTemp()) {
            t.clearTemp();
        }
        t.delivery = tag.hasUUID("delivery") ? tag.getUUID("delivery") : null;
        return t;
    }

    /** Bloc de la tete (pour les alertes, la carte). */
    public BlockPos headBlock(RailNetwork net) {
        double[] p = new double[3];
        locate(net, 0, p, null);
        return BlockPos.containing(p[0], p[1] + 0.1, p[2]);
    }
}
