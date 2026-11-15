package fr.pioneers.naval;

import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.Schedule;
import fr.pioneers.rail.train.Train;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 0.20.11 : un navire (la verite est dans {@link NavalWorld} ; l'entite n'est qu'une vue). Machines et cales sont des {@link Car} du rail :
 * machines = locomotive (et tender pour la vapeur), cales = le wagon de la ressource au meme tier. Position en blocs (x, z), cap en degres
 * (avant = (sin cap, cos cap)), vitesse en blocs/tick.
 */
public final class Ship {

    public enum State {
        /** A quai (amarre a {@link #harbor}). */
        DOCKED,
        /** Quitte le poste (glisse vers le large). */
        UNDOCKING,
        /** En route vers le mouillage du port vise. */
        SAILING,
        /** Au mouillage : poste occupe. */
        WAITING,
        /** Se range le long du quai. */
        DOCKING,
        /** Arrete en mer (manuel, plus de carburant, pas de route). */
        STOPPED;

        public static State byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public final UUID id;
    public final ShipKind kind;
    public final int tier;
    public String name = "";
    public double x, z, y;
    public float heading, speed, odometer;
    public State state = State.STOPPED;
    /** Statut affiche (celui des trains : en route, a quai, pas de chemin, plus de carburant...). */
    public Train.Status status = Train.Status.IDLE;
    public final List<Car> engines = new ArrayList<>();
    public final List<Car> holds = new ArrayList<>();
    public boolean auto, logistic;
    public Schedule schedule = new Schedule();
    public int stopIndex;
    /** Arrets temporaires (repartiteur) : passent avant l'horaire. */
    public final List<Schedule.Stop> temp = new ArrayList<>();
    public int tempIndex;
    public Train.TempKind tempKind = Train.TempKind.NONE;
    /** Port vise ou d'amarrage (position du bloc, 0 : aucun). */
    public long harbor;
    /** Itineraire en cours (points en blocs) et point suivant. */
    public transient List<double[]> route = new ArrayList<>();
    public transient int routeIndex;
    public transient int routeVersion = -1;
    /**
     * 0.20.27 : calcul de route en cours (fil du planificateur) et port pour lequel il a ete lance ; reessai apres un echec (tick, versions
     * de la carte et des balises au moment de l'echec) ; recalculs pendant le trajet, distance parcourue et longueur de la route (un navire
     * qui tourne en rond abandonne).
     */
    public transient CompletableFuture<NavalPlanner.Plan> planning;
    public transient long planHarbor, retryAt;
    public transient int failChart = -1, failBeacons = -1, replans;
    public transient double sailed, routeLength;
    /** Balises empruntees par la route en cours (0 : couloir direct). */
    public transient int viaBeacons;
    /** 0.20.46 (audit) : prochain avertissement possible d'une erreur du planificateur (temps de jeu ; pas sauve). */
    public transient long planWarnAt;
    /** Manoeuvre (amarrage, appareillage) : poses de depart et d'arrivee, avancement 0..1. */
    public double fromX, fromZ, toX, toZ;
    public float fromHeading, toHeading, manoeuvre;
    public long arrivedAt, lastActivity, blockedSince;
    /** Ce tick : effort de propulsion 0..1 (fumee, consommation). */
    public transient float effort;

    public Ship(UUID id, ShipKind kind, int tier) {
        this.id = id;
        this.kind = kind;
        this.tier = Math.max(0, Math.min(3, tier));
        buildCars();
    }

    public Ship(ShipKind kind, int tier) {
        this(UUID.randomUUID(), kind, tier);
    }

    private void buildCars() {
        switch (tier) {
            case 0 -> {
                engines.add(new Car(CarType.STEAM, 0));
                engines.add(new Car(CarType.TENDER, 0));
            }
            case 1 -> engines.add(new Car(CarType.DIESEL, 1));
            case 2 -> {
                engines.add(new Car(CarType.ELECTRIC, 2));
                engines.add(new Car(CarType.ELECTRIC, 2));
            }
            default -> engines.add(new Car(CarType.NUCLEAR, 3));
        }
        for (int i = 0; i < ShipKind.holds(tier); i++) {
            holds.add(new Car(kind.hold, tier));
        }
    }

    public List<Car> allCars() {
        List<Car> all = new ArrayList<>(engines);
        all.addAll(holds);
        return all;
    }

    public boolean heatproof() {
        return ShipKind.heatproof(tier);
    }

    public double forwardX() {
        return Math.sin(Math.toRadians(heading));
    }

    public double forwardZ() {
        return Math.cos(Math.toRadians(heading));
    }

    public boolean inTemp() {
        return !temp.isEmpty() && tempIndex < temp.size();
    }

    /** Arret courant (temporaire d'abord, sinon l'horaire). */
    public Schedule.Stop currentStop() {
        if (inTemp()) {
            return temp.get(tempIndex);
        }
        if (schedule.isEmpty()) {
            return null;
        }
        return schedule.stops.get(Math.floorMod(stopIndex, schedule.stops.size()));
    }

    public boolean holdsEmpty() {
        for (Car c : holds) {
            if (!c.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean holdsFull() {
        for (Car c : holds) {
            if (!c.isFull()) {
                return false;
            }
        }
        return true;
    }

    /** Remplissage moyen des cales 0..1. */
    public float fill() {
        float f = 0;
        for (Car c : holds) {
            f += c.fill();
        }
        return holds.isEmpty() ? 0 : f / holds.size();
    }

    // ------------------------------------------------------------------ sauvegarde

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putByte("kind", (byte) kind.ordinal());
        t.putByte("tier", (byte) tier);
        t.putString("name", name);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putFloat("h", heading);
        t.putFloat("odo", odometer % 100_000F);
        t.putByte("state", (byte) state.ordinal());
        t.putBoolean("auto", auto);
        t.putBoolean("logistic", logistic);
        t.put("schedule", schedule.save());
        t.putInt("stop", stopIndex);
        t.put("temp", Schedule.saveStops(temp));
        t.putInt("tempIndex", tempIndex);
        t.putByte("tempKind", (byte) tempKind.ordinal());
        t.putLong("harbor", harbor);
        t.putDouble("fx", fromX);
        t.putDouble("fz", fromZ);
        t.putDouble("tx", toX);
        t.putDouble("tz", toZ);
        t.putFloat("fh", fromHeading);
        t.putFloat("th", toHeading);
        t.putFloat("m", manoeuvre);
        t.putLong("arrived", arrivedAt);
        t.putLong("activity", lastActivity);
        ListTag e = new ListTag();
        for (Car c : engines) {
            e.add(c.save());
        }
        t.put("engines", e);
        ListTag h = new ListTag();
        for (Car c : holds) {
            h.add(c.save());
        }
        t.put("holds", h);
        return t;
    }

    public static Ship load(CompoundTag t) {
        Ship s = new Ship(t.getUUID("id"), ShipKind.byId(t.getByte("kind")), t.getByte("tier"));
        s.name = t.getString("name");
        s.x = t.getDouble("x");
        s.y = t.getDouble("y");
        s.z = t.getDouble("z");
        s.heading = t.getFloat("h");
        s.odometer = t.getFloat("odo");
        s.state = State.byId(t.getByte("state"));
        s.auto = t.getBoolean("auto");
        s.logistic = t.getBoolean("logistic");
        s.schedule = Schedule.load(t.getCompound("schedule"));
        s.stopIndex = t.getInt("stop");
        s.temp.addAll(Schedule.loadStops(t.getList("temp", Tag.TAG_COMPOUND), 16));
        s.tempIndex = t.getInt("tempIndex");
        s.tempKind = Train.TempKind.values()[Math.max(0, Math.min(Train.TempKind.values().length - 1, t.getByte("tempKind")))];
        s.harbor = t.getLong("harbor");
        s.fromX = t.getDouble("fx");
        s.fromZ = t.getDouble("fz");
        s.toX = t.getDouble("tx");
        s.toZ = t.getDouble("tz");
        s.fromHeading = t.getFloat("fh");
        s.toHeading = t.getFloat("th");
        s.manoeuvre = t.getFloat("m");
        s.arrivedAt = t.getLong("arrived");
        s.lastActivity = t.getLong("activity");
        copyCars(s.engines, t.getList("engines", Tag.TAG_COMPOUND));
        copyCars(s.holds, t.getList("holds", Tag.TAG_COMPOUND));
        return s;
    }

    /** Recopie les reserves sauvees dans les vehicules neufs (meme sorte, meme ordre). */
    private static void copyCars(List<Car> into, ListTag list) {
        for (int i = 0; i < Math.min(into.size(), list.size()); i++) {
            Car saved = Car.load(list.getCompound(i));
            if (saved.type == into.get(i).type) {
                into.set(i, saved);
            }
        }
    }

    /** Contenu complet (pour l'objet d'un navire ramasse). */
    public CompoundTag saveContents() {
        CompoundTag t = save();
        t.remove("id");
        return t;
    }

    /** Navire neuf d'apres un objet ramasse (nouvel id). */
    public static Ship fromContents(ShipKind kind, int tier, CompoundTag t) {
        Ship s = new Ship(kind, tier);
        if (t != null && !t.isEmpty()) {
            s.name = t.getString("name");
            s.schedule = Schedule.load(t.getCompound("schedule"));
            s.logistic = t.getBoolean("logistic");
            copyCars(s.engines, t.getList("engines", Tag.TAG_COMPOUND));
            copyCars(s.holds, t.getList("holds", Tag.TAG_COMPOUND));
        }
        return s;
    }
}
