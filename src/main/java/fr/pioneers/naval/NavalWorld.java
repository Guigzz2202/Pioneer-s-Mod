package fr.pioneers.naval;

import fr.pioneers.Pioneers;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.logistics.StationLogistics;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import fr.pioneers.rail.train.RailFuels;
import fr.pioneers.rail.train.Schedule;
import fr.pioneers.rail.train.Train;
import fr.pioneers.registries.PiNaval;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : la marine d'une dimension (SavedData {@code pioneers_naval}) : carte marine, ports inscrits et navires. Les navires naviguent ici,
 * meme dans les chunks non charges ; leurs vues ({@link ShipEntity}) suivent la ou le monde est charge.
 * <p>
 * Cycle d'un navire en automatique : a quai (conditions de l'arret) -> appareillage (glisse au large) -> route (A* sur la carte, refaite si
 * la carte change ou si le chemin est bouche) -> mouillage devant le port vise -> attente si le poste est pris -> amarrage le long du quai.
 */
public final class NavalWorld extends SavedData {

    public static final String NAME = "pioneers_naval";

    public final WaterChart chart = new WaterChart();
    private final Map<UUID, Ship> ships = new LinkedHashMap<>();
    private final Map<Long, Harbor> harbors = new LinkedHashMap<>();
    private final Map<UUID, ShipEntity> views = new HashMap<>();
    /** Livraisons du repartiteur en cours (navire -> livraison). */
    final Map<UUID, NavalDispatcher.Delivery> deliveries = new HashMap<>();
    /** Stock des ports (releve par leur bloc chaque seconde, quand il est charge). */
    final Map<Long, Map<Cargo, Long>> stock = new HashMap<>();
    final Map<Long, Integer> redstone = new HashMap<>();
    @Nullable
    private ServerLevel level;
    /** 0.20.27 : planificateur des routes (fil a part) et balises maritimes avec leurs liaisons. */
    final NavalPlanner planner = new NavalPlanner(chart);
    private boolean probed;
    private final Map<Long, SeaBeacon> beacons = new LinkedHashMap<>();
    private final Map<SeaBeacon.Pair, SeaBeacon.Lane> lanes = new LinkedHashMap<>();
    /** Calculs de liaison en cours, et joueur a prevenir du resultat. */
    private final Map<SeaBeacon.Pair, CompletableFuture<NavalPlanner.Plan>> laneJobs = new HashMap<>();
    private final Map<SeaBeacon.Pair, UUID> laneAsker = new HashMap<>();
    /** Augmente a chaque balise ou liaison changee (les navires sans route reessaient). */
    public int beaconVersion;
    /** 0.20.46 (audit) : changements de la carte deja sauves ({@link WaterChart#edits()} a la derniere sauvegarde). */
    private int savedChartEdits;

    public NavalWorld() {
    }

    public static NavalWorld get(ServerLevel level) {
        NavalWorld w = level.getDataStorage().computeIfAbsent(NavalWorld::load, NavalWorld::new, NAME);
        w.level = level;
        if (!w.probed) {
            w.probed = true;
            w.planner.probe = SeaSounder.of(level);
        }
        return w;
    }

    /**
     * 0.20.46 (audit) : la marine peut-elle servir ici ? Mer dans le generateur de la dimension (eau ou lave navigable), ou deja un port, un
     * navire ou une balise. Sinon (la plupart des astres) la carte marine n'est pas relevee.
     */
    public boolean charting() {
        return planner.probe != null || !harbors.isEmpty() || !ships.isEmpty() || !beacons.isEmpty();
    }

    /** 0.20.46 (audit) : premier port, navire ou balise d'une dimension sans mer : on releve les chunks charges autour. */
    private void startCharting(boolean was, BlockPos around) {
        if (!was && level != null && charting()) {
            NavalEvents.queueAround(level, around);
        }
    }

    public Iterable<Ship> ships() {
        return ships.values();
    }

    public Iterable<Harbor> harbors() {
        return harbors.values();
    }

    @Nullable
    public Ship ship(UUID id) {
        return id == null ? null : ships.get(id);
    }

    @Nullable
    public Harbor harbor(long pos) {
        return harbors.get(pos);
    }

    @Nullable
    public ShipEntity view(UUID ship) {
        ShipEntity e = views.get(ship);
        return e == null || e.isRemoved() ? null : e;
    }

    public List<String> harborNames() {
        List<String> out = new ArrayList<>();
        for (Harbor h : harbors.values()) {
            if (!h.name.isEmpty() && !out.contains(h.name)) {
                out.add(h.name);
            }
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    // ------------------------------------------------------------------ ports

    /** Le bloc du port s'inscrit (pose, chargement) ou se met a jour. */
    public Harbor registerHarbor(BlockPos pos, Direction facing, int tier) {
        Harbor h = harbors.get(pos.asLong());
        if (h == null) {
            boolean was = charting();
            h = new Harbor(pos.asLong(), facing, tier);
            h.name = defaultName();
            harbors.put(pos.asLong(), h);
            setDirty();
            startCharting(was, pos);
        } else if (h.facing != facing || h.tier != tier) {
            h.facing = facing;
            h.tier = tier;
            setDirty();
        }
        return h;
    }

    private String defaultName() {
        int n = 1;
        while (true) {
            String name = "Port " + n;
            boolean used = false;
            for (Harbor h : harbors.values()) {
                if (h.name.equals(name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return name;
            }
            n++;
        }
    }

    public void removeHarbor(BlockPos pos) {
        Harbor h = harbors.remove(pos.asLong());
        if (h != null) {
            for (Ship s : ships.values()) {
                if (s.harbor == h.pos) {
                    s.harbor = 0;
                    if (s.state == Ship.State.DOCKED || s.state == Ship.State.DOCKING || s.state == Ship.State.WAITING) {
                        s.state = Ship.State.STOPPED;
                    }
                }
            }
            stock.remove(h.pos);
            setDirty();
        }
    }

    /** Navire amarre a ce port (null : poste vide ou navire en manoeuvre). */
    @Nullable
    public Ship docked(long harborPos) {
        Harbor h = harbors.get(harborPos);
        if (h == null || h.occupant == null) {
            return null;
        }
        Ship s = ships.get(h.occupant);
        return s != null && s.state == Ship.State.DOCKED && s.harbor == harborPos ? s : null;
    }

    public void reportStock(long harborPos, Map<Cargo, Long> s, int power) {
        stock.put(harborPos, s);
        redstone.put(harborPos, power);
    }

    long stockOf(long harborPos, Cargo c) {
        Map<Cargo, Long> m = stock.get(harborPos);
        if (m == null) {
            return 0;
        }
        long n = 0;
        for (Map.Entry<Cargo, Long> e : m.entrySet()) {
            if (c.covers(e.getKey())) {
                n += e.getValue();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ navires

    /** Un navire est mis a l'eau (objet pose). */
    public void launch(Ship s) {
        boolean was = charting();
        ships.put(s.id, s);
        s.state = Ship.State.STOPPED;
        s.status = Train.Status.MANUAL;
        setDirty();
        startCharting(was, BlockPos.containing(s.x, s.y, s.z));
    }

    /** Navire repris en objet. */
    @Nullable
    public Ship remove(UUID id) {
        Ship s = ships.remove(id);
        if (s != null) {
            freeBerth(s);
            deliveries.remove(id);
            ShipEntity e = views.remove(id);
            if (e != null) {
                e.discard();
            }
            setDirty();
        }
        return s;
    }

    private void freeBerth(Ship s) {
        for (Harbor h : harbors.values()) {
            if (s.id.equals(h.occupant)) {
                h.occupant = null;
            }
        }
    }

    public void setMode(Ship s, boolean auto) {
        s.auto = auto;
        if (!auto) {
            s.logistic = false;
            s.temp.clear();
            s.tempKind = Train.TempKind.NONE;
            deliveries.remove(s.id);
            if (s.state == Ship.State.SAILING || s.state == Ship.State.WAITING) {
                s.state = Ship.State.STOPPED;
            }
        } else if (s.state == Ship.State.STOPPED) {
            s.blockedSince = 0;
        }
        setDirty();
    }

    public void setLogistic(Ship s, boolean on) {
        s.logistic = on && s.kind.cargo != null;
        if (s.logistic) {
            s.auto = true;
        } else {
            deliveries.remove(s.id);
        }
        setDirty();
    }

    public void setSchedule(Ship s, Schedule schedule, int index) {
        s.schedule = schedule;
        s.stopIndex = Math.max(0, Math.min(index, Math.max(0, schedule.stops.size() - 1)));
        if (s.state != Ship.State.DOCKED && s.auto && !s.inTemp()) {
            s.state = Ship.State.STOPPED;
        }
        setDirty();
    }

    /** Aller directement a l'arret {@code i} de l'horaire. */
    public void skipTo(Ship s, int i) {
        if (s.schedule.isEmpty()) {
            return;
        }
        s.temp.clear();
        s.tempKind = Train.TempKind.NONE;
        s.stopIndex = Math.floorMod(i, s.schedule.stops.size());
        leave(s);
        setDirty();
    }

    /** Ordre d'aller a un port (arret temporaire). */
    public void sendTo(Ship s, String harborName) {
        if (harborName.isEmpty()) {
            return;
        }
        Schedule.Stop stop = new Schedule.Stop();
        stop.station = harborName;
        s.temp.clear();
        s.temp.add(stop);
        s.tempIndex = 0;
        s.tempKind = Train.TempKind.ORDER;
        s.auto = true;
        leave(s);
        setDirty();
    }

    public void cancelTemp(Ship s) {
        s.temp.clear();
        s.tempIndex = 0;
        s.tempKind = Train.TempKind.NONE;
        deliveries.remove(s.id);
        leave(s);
    }

    /** Quitte le port (ou repart du mouillage) vers l'arret courant. */
    private void leave(Ship s) {
        if (s.state == Ship.State.DOCKED) {
            startUndock(s);
        } else if (s.state != Ship.State.UNDOCKING) {
            freeBerth(s);
            s.harbor = 0;
            s.route.clear();
            s.state = Ship.State.STOPPED;
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick(ServerLevel level) {
        this.level = level;
        long now = level.getGameTime();
        for (Ship s : new ArrayList<>(ships.values())) {
            tickShip(s, now);
        }
        tickLanes(level, now);
        if (now % 20 == 0) {
            NavalDispatcher.tick(this, now);
        }
        syncViews(level, now);
        //0.20.46 (audit) : plus de sauvegarde forcee sans rien a sauver (navires qui bougent, ports, ou carte changee)
        if (now % 200 == 0 && (!ships.isEmpty() || !harbors.isEmpty() || chart.edits() != savedChartEdits)) {
            setDirty();
        }
    }

    private void tickShip(Ship s, long now) {
        s.effort = 0;
        switch (s.state) {
            case DOCKED -> docked(s, now);
            case UNDOCKING, DOCKING -> manoeuvre(s, now);
            case SAILING -> sail(s, now);
            case WAITING -> waiting(s, now);
            default -> stopped(s, now);
        }
        s.odometer += Math.abs(s.speed) * 16;
        if (s.effort > 0) {
            burn(s, s.effort);
        }
    }

    private void docked(Ship s, long now) {
        s.speed = 0;
        Harbor h = harbors.get(s.harbor);
        if (h == null) {
            s.state = Ship.State.STOPPED;
            return;
        }
        s.effort = 0;
        if (!s.auto) {
            s.status = Train.Status.MANUAL;
            return;
        }
        s.status = Train.Status.AT_STATION;
        Schedule.Stop stop = s.currentStop();
        if (stop == null) {
            if (s.logistic) {
                if (!isDepot(h) && now % 100 == 0) {
                    NavalDispatcher.toDepot(this, s);
                }
                return;
            }
            s.status = Train.Status.NO_SCHEDULE;
            return;
        }
        //Arret courant ici ? Sinon on part tout de suite vers lui
        boolean here = stop.target != 0 ? stop.target == h.pos : h.name.equals(stop.station);
        if (!here) {
            startUndock(s);
            return;
        }
        if (Schedule.ready(stop.conditions, context(s, h, now))) {
            advance(s);
            if (s.logistic && !s.inTemp() && isDepot(h)) {
                //Navire logistique de retour au depot : il y attend la prochaine livraison
                return;
            }
            Schedule.Stop next = s.currentStop();
            boolean stay = next != null && (next.target != 0 ? next.target == h.pos : h.name.equals(next.station)) && next != stop;
            if (!stay) {
                startUndock(s);
            } else {
                s.arrivedAt = now;
                s.lastActivity = now;
            }
        }
    }

    static boolean isDepot(Harbor h) {
        return h.logistics.role == StationLogistics.Role.DEPOT;
    }

    /** Arret suivant (temporaires d'abord ; fin des temporaires : retour a l'horaire). */
    private void advance(Ship s) {
        if (s.inTemp()) {
            s.tempIndex++;
            if (s.tempIndex >= s.temp.size()) {
                s.temp.clear();
                s.tempIndex = 0;
                s.tempKind = Train.TempKind.NONE;
                deliveries.remove(s.id);
            }
        } else if (!s.schedule.isEmpty()) {
            s.stopIndex = (s.stopIndex + 1) % s.schedule.stops.size();
        }
        setDirty();
    }

    private void startUndock(Ship s) {
        Harbor h = harbors.get(s.harbor);
        s.fromX = s.x;
        s.fromZ = s.z;
        s.fromHeading = s.heading;
        if (h != null) {
            double[] a = h.anchorage(s.tier);
            s.toX = a[0];
            s.toZ = a[1];
        } else {
            s.toX = s.x - s.forwardZ() * 4;
            s.toZ = s.z + s.forwardX() * 4;
        }
        s.toHeading = s.heading;
        s.manoeuvre = 0;
        s.state = Ship.State.UNDOCKING;
        freeBerth(s);
        s.harbor = 0;
        s.route.clear();
        s.status = Train.Status.RUNNING;
    }

    /** Amarrage / appareillage : glisse en douceur d'une pose a l'autre (remorqueurs invisibles). */
    private void manoeuvre(Ship s, long now) {
        double dist = Math.hypot(s.toX - s.fromX, s.toZ - s.fromZ);
        float dur = (float) Math.max(60, dist / (ShipKind.speed(s.tier) * 0.3));
        if (hasFuel(s)) {
            s.manoeuvre = Math.min(1, s.manoeuvre + 1 / dur);
            s.effort = 0.35F;
        } else {
            s.status = Train.Status.NO_FUEL;
        }
        float k = s.manoeuvre * s.manoeuvre * (3 - 2 * s.manoeuvre);
        double px = s.x, pz = s.z;
        s.x = s.fromX + (s.toX - s.fromX) * k;
        s.z = s.fromZ + (s.toZ - s.fromZ) * k;
        s.heading = s.fromHeading + Harbor.wrap(s.toHeading - s.fromHeading) * k;
        s.speed = (float) Math.hypot(s.x - px, s.z - pz);
        surface(s);
        if (s.manoeuvre >= 1) {
            if (s.state == Ship.State.DOCKING) {
                s.state = Ship.State.DOCKED;
                s.speed = 0;
                s.arrivedAt = now;
                s.lastActivity = now;
                s.status = s.auto ? Train.Status.AT_STATION : Train.Status.MANUAL;
                setDirty();
            } else {
                s.state = Ship.State.SAILING;
                s.route.clear();
            }
        }
    }

    private void stopped(Ship s, long now) {
        s.speed *= 0.9F;
        if (s.speed < 0.002F) {
            s.speed = 0;
        }
        s.x += s.forwardX() * s.speed;
        s.z += s.forwardZ() * s.speed;
        surface(s);
        if (!s.auto) {
            s.status = Train.Status.MANUAL;
            return;
        }
        if (s.currentStop() == null) {
            s.status = s.logistic ? Train.Status.IDLE : Train.Status.NO_SCHEDULE;
            if (s.logistic && now % 100 == 0) {
                NavalDispatcher.toDepot(this, s);
            }
            return;
        }
        s.state = Ship.State.SAILING;
        s.route.clear();
    }

    /** Port vise par l'arret courant : celui de l'arret precis, sinon le plus proche du nom voulu qui accepte le navire. */
    @Nullable
    private Harbor target(Ship s) {
        Schedule.Stop stop = s.currentStop();
        if (stop == null) {
            return null;
        }
        if (stop.target != 0) {
            Harbor h = harbors.get(stop.target);
            return h != null && h.accepts(s.tier) ? h : null;
        }
        Harbor best = null;
        double bd = Double.MAX_VALUE;
        for (Harbor h : harbors.values()) {
            if (!h.name.equals(stop.station) || !h.accepts(s.tier)) {
                continue;
            }
            if (h.limit >= 0 && assigned(h, s) >= h.limit) {
                continue;
            }
            BlockPos p = h.blockPos();
            double d = Math.hypot(p.getX() - s.x, p.getZ() - s.z) + (h.occupant != null && !h.occupant.equals(s.id) ? 64 : 0);
            if (d < bd) {
                bd = d;
                best = h;
            }
        }
        return best;
    }

    /** Navires en route vers ce port ou a quai (sans compter {@code except}). */
    int assigned(Harbor h, Ship except) {
        int n = 0;
        for (Ship o : ships.values()) {
            if (o != except && o.harbor == h.pos) {
                n++;
            }
        }
        return n;
    }

    private void sail(Ship s, long now) {
        Harbor h = s.harbor != 0 ? harbors.get(s.harbor) : null;
        if (h == null || !h.accepts(s.tier)) {
            h = target(s);
            if (h == null) {
                s.status = s.currentStop() == null ? Train.Status.NO_SCHEDULE : Train.Status.DEST_FULL;
                slowDown(s);
                return;
            }
            s.harbor = h.pos;
            s.route.clear();
        }
        double[] goal = h.anchorage(s.tier);
        if (!s.route.isEmpty() && s.routeVersion != chart.version && s.routeIndex < s.route.size() && blockedAhead(s)) {
            //La carte a change et la route est coupee devant : on la refait
            s.route.clear();
        }
        if (!s.route.isEmpty() && s.sailed > 2 * s.routeLength + 300) {
            //0.20.27 : le navire tourne en rond (routes refaites sans cesse, terre qui apparait en chemin) : il abandonne
            giveUp(s, now);
            slowDown(s);
            return;
        }
        if (s.route.isEmpty() && !plan(s, h, goal, now)) {
            slowDown(s);
            return;
        }
        s.status = Train.Status.RUNNING;
        double[] p = s.route.get(Math.min(s.routeIndex, s.route.size() - 1));
        double dx = p[0] - s.x, dz = p[1] - s.z, dist = Math.hypot(dx, dz);
        boolean last = s.routeIndex >= s.route.size() - 1;
        if (!last && dist < Math.max(2.5, ShipKind.length(s.tier) * 0.35)) {
            s.routeIndex++;
            return;
        }
        if (last && dist < 1.2) {
            arrive(s, h, now);
            return;
        }
        float want = (float) Math.toDegrees(Math.atan2(dx, dz));
        float diff = Harbor.wrap(want - s.heading);
        float turn = new float[]{2.2F, 1.9F, 1.5F, 1.3F}[s.tier];
        s.heading += Math.max(-turn, Math.min(turn, diff));
        s.heading = Harbor.wrap(s.heading);
        double cruise = ShipKind.speed(s.tier);
        double v = cruise * Math.max(0.25, 1 - Math.abs(diff) / 110.0);
        double remaining = dist;
        if (last) {
            v = Math.min(v, Math.sqrt(2 * (cruise / 80.0) * remaining) + 0.01);
        }
        if (!hasFuel(s)) {
            v = 0;
            s.status = Train.Status.NO_FUEL;
        }
        float accel = (float) (cruise / 60.0);
        s.speed += (float) Math.max(-accel * 2, Math.min(accel, v - s.speed));
        s.speed = Math.max(0, s.speed);
        s.effort = (float) Math.min(1, 0.2 + 0.8 * s.speed / cruise);
        s.x += s.forwardX() * s.speed;
        s.z += s.forwardZ() * s.speed;
        s.sailed += s.speed;
        surface(s);
    }

    /**
     * 0.20.27 : route vers le port, calculee dans le fil du planificateur. Vrai quand la route est prete ; sinon le navire attend (statut
     * releve de la route, ou aucune route maritime jusqu'au prochain essai : 30 s, ou des que la carte ou les balises changent).
     */
    private boolean plan(Ship s, Harbor h, double[] goal, long now) {
        if (s.planning != null && s.planHarbor != h.pos) {
            s.planning.cancel(false);
            s.planning = null;
        }
        if (s.planning == null) {
            if (now < s.retryAt && chart.version == s.failChart && beaconVersion == s.failBeacons) {
                s.status = Train.Status.NO_PATH;
                return false;
            }
            if (s.replans > 25) {
                giveUp(s, now);
                return false;
            }
            s.planHarbor = h.pos;
            s.planning = planner.route(s.x, s.z, goal[0], goal[1], s.heatproof(), approach(h, s.tier), beaconViews(), laneViews());
        }
        if (!s.planning.isDone()) {
            s.status = Train.Status.CHARTING;
            return false;
        }
        NavalPlanner.Plan p;
        try {
            p = s.planning.join();
        } catch (RuntimeException e) {
            //0.20.46 (audit) : une erreur du planificateur n'est plus muette (« pas de route » sans explication) ; une fois par navire
            //toutes les 5 minutes au plus (il reessaie a chaque changement de la carte : pas de journal inonde)
            if (now >= s.planWarnAt) {
                s.planWarnAt = now + 6000;
                Pioneers.LOGGER.warn("Pioneer's : echec du calcul de route maritime pour un navire", e);
            }
            p = new NavalPlanner.Plan(null, 0, 0);
        }
        s.planning = null;
        s.routeVersion = chart.version;
        if (p.route() == null || p.route().size() < 2) {
            giveUp(s, now);
            return false;
        }
        s.blockedSince = 0;
        s.route = new ArrayList<>(p.route());
        s.routeIndex = 1;
        s.routeLength = ShipPathfinder.length(p.route());
        s.sailed = 0;
        s.replans++;
        s.viaBeacons = p.beacons();
        return true;
    }

    /** Aucune route maritime faisable : statut, nouvel essai dans 30 s (plus tot si la carte ou les balises changent). */
    private void giveUp(Ship s, long now) {
        s.status = Train.Status.NO_PATH;
        s.route.clear();
        s.planning = null;
        s.retryAt = now + 600;
        s.failChart = chart.version;
        s.failBeacons = beaconVersion;
        s.replans = 0;
        s.sailed = 0;
        //Un autre port du meme nom (plus proche, accessible) peut etre choisi au prochain essai
        s.harbor = 0;
    }

    private void slowDown(Ship s) {
        s.speed *= 0.92F;
        if (s.speed < 0.002F) {
            s.speed = 0;
        }
        s.x += s.forwardX() * s.speed;
        s.z += s.forwardZ() * s.speed;
        surface(s);
    }

    /** 0.20.25 : chenal d'approche du port : de la capitainerie vers le large, au-dela du mouillage, un peu plus large que le bassin. */
    static ShipPathfinder.Approach approach(Harbor h, int tier) {
        double[] q = h.quayCenter();
        double[] a = h.anchorage(tier);
        double length = Math.hypot(a[0] - q[0], a[1] - q[1]) + 24;
        return new ShipPathfinder.Approach(q[0], q[1], h.facing.getStepX(), h.facing.getStepZ(), length, HarborLayout.halfWidth(h.tier) + 6);
    }

    /** Le prochain troncon de la route traverse-t-il une case devenue impraticable ? */
    private boolean blockedAhead(Ship s) {
        s.routeVersion = chart.version;
        double[] p = s.route.get(Math.min(s.routeIndex, s.route.size() - 1));
        //0.20.46 (audit) : avec la sonde : une estimation oubliee (plafond) est refaite a l'identique au lieu de passer pour de la terre
        return !ShipPathfinder.sightBlocks(chart.reader(planner.probe), s.x, s.z, p[0], p[1], s.heatproof());
    }

    /** Au mouillage : on se range si le poste est libre, sinon on attend. */
    private void arrive(Ship s, Harbor h, long now) {
        s.route.clear();
        s.replans = 0;
        s.sailed = 0;
        if (h.occupant == null || h.occupant.equals(s.id) || ship(h.occupant) == null) {
            h.occupant = s.id;
            double[] b = h.berthPose(s.tier, s.heading);
            s.fromX = s.x;
            s.fromZ = s.z;
            s.fromHeading = s.heading;
            s.toX = b[0];
            s.toZ = b[1];
            s.toHeading = (float) b[2];
            s.manoeuvre = 0;
            s.state = Ship.State.DOCKING;
            s.status = Train.Status.RUNNING;
        } else {
            s.state = Ship.State.WAITING;
            s.status = Train.Status.WAIT_TRAIN;
        }
    }

    private void waiting(Ship s, long now) {
        slowDown(s);
        s.effort = 0.1F;
        Harbor h = harbors.get(s.harbor);
        if (h == null || !h.accepts(s.tier)) {
            s.state = Ship.State.STOPPED;
            s.harbor = 0;
            return;
        }
        if (now % 20 == 0 && (h.occupant == null || ship(h.occupant) == null)) {
            arrive(s, h, now);
        }
    }

    /** Hauteur de l'eau sous le navire (carte marine), y = haut du bloc de fluide. */
    private void surface(Ship s) {
        int y = chart.surface(WaterChart.cell(s.x), WaterChart.cell(s.z));
        if (y != Integer.MIN_VALUE) {
            s.y = y + 0.875;
        }
    }

    // ------------------------------------------------------------------ carburant

    public boolean hasFuel(Ship s) {
        for (Car c : s.engines) {
            if (c.heat > 0 || c.energy > 0 || c.fluidAmount > 0) {
                return true;
            }
            for (int i = 0; i < c.items.getSlots(); i++) {
                if (!c.items.getStackInSlot(i).isEmpty() && (c.type != CarType.NUCLEAR || i == 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Brule le carburant d'un tick ({@code effort} 0..1 de la consommation en route). */
    private void burn(Ship s, float effort) {
        long need = (long) (ShipKind.fuelUse(s.tier) * effort);
        if (s.tier == 2) {
            //Electrique : les batteries des deux machines, la plus pleine d'abord
            for (int k = 0; k < 2 && need > 0; k++) {
                Car best = null;
                for (Car c : s.engines) {
                    if (best == null || c.energy > best.energy) {
                        best = c;
                    }
                }
                if (best == null || best.energy <= 0) {
                    break;
                }
                long used = Math.min(need, best.energy);
                best.energy -= used;
                need -= used;
            }
            return;
        }
        Car engine = s.engines.get(0);
        if (engine.heat < need) {
            refuel(s, engine, need);
        }
        engine.heat = Math.max(0, engine.heat - need);
    }

    private void refuel(Ship s, Car engine, long need) {
        switch (engine.type) {
            case STEAM -> {
                //Combustible : la machine puis la soute (tender)
                for (Car c : s.engines) {
                    for (int i = 0; i < c.items.getSlots(); i++) {
                        ItemStack st = c.items.getStackInSlot(i);
                        int burn = st.isEmpty() ? 0 : ForgeHooks.getBurnTime(st, RecipeType.SMELTING);
                        if (burn > 0) {
                            ItemStack rest = st.getCraftingRemainingItem();
                            c.items.extractItem(i, 1, false);
                            if (!rest.isEmpty() && c.items.getStackInSlot(i).isEmpty()) {
                                c.items.setStackInSlot(i, rest);
                            }
                            engine.heat += burn * 1024L;
                            return;
                        }
                    }
                }
            }
            case DIESEL -> {
                int per = RailFuels.liquid(engine.fluid);
                if (per > 0) {
                    int mb = (int) Math.min(engine.fluidAmount, Math.max(1, (need * 8 - engine.heat) / per + 1));
                    engine.drainFluid(mb, false);
                    engine.heat += (long) mb * per;
                }
            }
            case NUCLEAR -> {
                ItemStack fuel = engine.items.getStackInSlot(0);
                ItemStack spentSlot = engine.items.getStackInSlot(1);
                long fe = RailFuels.nuclear(fuel);
                ItemStack spent = spentOf(fuel);
                boolean room = spent.isEmpty() || spentSlot.isEmpty()
                      || ItemStack.isSameItemSameTags(spentSlot, spent) && spentSlot.getCount() < spentSlot.getMaxStackSize();
                if (fe > 0 && room) {
                    engine.items.extractItem(0, 1, false);
                    if (!spent.isEmpty()) {
                        if (spentSlot.isEmpty()) {
                            engine.items.setStackInSlot(1, spent);
                        } else {
                            spentSlot.grow(1);
                            engine.items.setStackInSlot(1, spentSlot);
                        }
                    }
                    engine.heat += fe;
                }
            }
            default -> {
            }
        }
    }

    private static ItemStack spentOf(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String spent = RailFuels.NUCLEAR_SPENT.get(BuiltInRegistries.ITEM.getKey(fuel.getItem()).toString());
        if (spent == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spent));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** Reserve de carburant (%) pour l'horaire et la fiche. */
    public int fuelPercent(Ship s) {
        float f;
        switch (s.tier) {
            case 0 -> {
                float items = 0;
                int slots = 0;
                for (Car c : s.engines) {
                    for (int i = 0; i < c.items.getSlots(); i++) {
                        ItemStack st = c.items.getStackInSlot(i);
                        items += st.isEmpty() ? 0 : st.getCount() / (float) st.getMaxStackSize();
                        slots++;
                    }
                }
                f = slots == 0 ? 0 : items / slots;
            }
            case 1 -> f = s.engines.get(0).fluidAmount / (float) Math.max(1, s.engines.get(0).fluidCapacity());
            case 2 -> {
                long e = 0, cap = 0;
                for (Car c : s.engines) {
                    e += c.energy;
                    cap += c.energyCapacity();
                }
                f = cap == 0 ? 0 : e / (float) cap;
            }
            default -> {
                ItemStack st = s.engines.get(0).items.getStackInSlot(0);
                f = Math.min(1, st.getCount() / 4F + (s.engines.get(0).heat > 0 ? 0.25F : 0));
            }
        }
        return Math.round(Math.max(0, Math.min(1, f)) * 100);
    }

    // ------------------------------------------------------------------ horaire

    Schedule.Context context(Ship s, Harbor h, long now) {
        return new Schedule.Context() {
            @Override
            public int secondsAtStop() {
                return (int) ((now - s.arrivedAt) / 20);
            }

            @Override
            public int secondsIdle() {
                return (int) ((now - s.lastActivity) / 20);
            }

            @Override
            public boolean full() {
                return s.holdsFull();
            }

            @Override
            public boolean empty() {
                return s.holdsEmpty();
            }

            @Override
            public int count(Item item) {
                int n = 0;
                for (Car c : s.holds) {
                    for (int i = 0; i < c.items.getSlots(); i++) {
                        ItemStack st = c.items.getStackInSlot(i);
                        if (st.is(item)) {
                            n += st.getCount();
                        }
                    }
                }
                return n;
            }

            @Override
            public boolean passenger() {
                ShipEntity e = view(s.id);
                return e != null && e.getPassengers().stream().anyMatch(p -> p instanceof Player);
            }

            @Override
            public int fuel() {
                return fuelPercent(s);
            }

            @Override
            public long fluid(@Nullable ResourceLocation id, boolean gas) {
                Cargo c = id == null ? new Cargo(gas ? Cargo.Kind.GAS : Cargo.Kind.FLUID, null) : gas ? Cargo.gas(id) : Cargo.fluid(id);
                return carried(s, c);
            }

            @Override
            public boolean cargo() {
                return !s.holdsEmpty();
            }

            @Override
            public long stock(int kind, @Nullable ResourceLocation id, boolean network) {
                Cargo c = new Cargo(kind == Schedule.FLUID ? Cargo.Kind.FLUID : kind == Schedule.GAS ? Cargo.Kind.GAS : Cargo.Kind.ITEM, id);
                return stockOf(h.pos, c);
            }

            @Override
            public int redstone() {
                return redstone.getOrDefault(h.pos, 0);
            }
        };
    }

    /** Quantite d'une ressource dans les cales. */
    public static long carried(Ship s, Cargo c) {
        long n = 0;
        for (Car car : s.holds) {
            n += c.in(car);
        }
        return n;
    }

    /** Le port note une activite (chargement, dechargement) : pour la condition d'inactivite. */
    public void activity(Ship s) {
        if (level != null) {
            s.lastActivity = level.getGameTime();
        }
    }

    // ------------------------------------------------------------------ vues

    private void syncViews(ServerLevel level, long now) {
        for (Ship s : ships.values()) {
            ShipEntity e = views.get(s.id);
            BlockPos bp = BlockPos.containing(s.x, s.y, s.z);
            boolean loaded = level.isLoaded(bp) && level.areEntitiesLoaded(ChunkPos.asLong(bp)) && level.isPositionEntityTicking(bp);
            if (!loaded) {
                if (e != null) {
                    e.discard();
                    views.remove(s.id);
                }
                continue;
            }
            if (e == null || e.isRemoved()) {
                e = PiNaval.SHIP_ENTITY.get().create(level);
                if (e == null) {
                    continue;
                }
                e.sync(this, s, now);
                e.setOldPosAndRot();
                if (level.addFreshEntity(e)) {
                    views.put(s.id, e);
                }
            } else {
                e.sync(this, s, now);
            }
            if (s.speed > 0.03F) {
                push(level, s, e);
            }
        }
        views.values().removeIf(v -> v.isRemoved() || ships.get(v.ship) == null);
    }

    /** Les entites dans l'eau devant l'etrave sont poussees de cote (le navire ne s'arrete pas). */
    private void push(ServerLevel level, Ship s, ShipEntity e) {
        double hl = ShipKind.length(s.tier) / 2.0, hw = ShipKind.beam(s.tier) / 2.0;
        Vec3 c = new Vec3(s.x, s.y, s.z);
        AABB box = new AABB(c, c).inflate(hl + 1, 1.5, hl + 1);
        for (Entity o : level.getEntities(e, box, o -> !(o instanceof ShipEntity) && !o.isPassenger() && !e.hasPassenger(o))) {
            double rx = o.getX() - s.x, rz = o.getZ() - s.z;
            double along = rx * s.forwardX() + rz * s.forwardZ(), across = rx * s.forwardZ() - rz * s.forwardX();
            if (Math.abs(along) < hl && Math.abs(across) < hw + 0.4) {
                double sign = across >= 0 ? 1 : -1;
                o.push(s.forwardZ() * sign * 0.25 + s.forwardX() * s.speed, 0.05, -s.forwardX() * sign * 0.25 + s.forwardZ() * s.speed);
                o.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    public static NavalWorld load(CompoundTag tag) {
        NavalWorld w = new NavalWorld();
        //0.20.28 : les releves d'avant (version 0) se trompaient d'un bloc de hauteur et notaient toute l'eau en terre : on les oublie
        if (tag.getInt("chartVersion") >= WaterChart.FORMAT) {
            //0.20.46 (audit) : chunks de terre sous chartLand (les anciennes sauvegardes les ont dans chart : convertis au chargement)
            w.chart.load(tag.getList("chart", Tag.TAG_COMPOUND), tag.getLongArray("chartLand"));
        }
        w.savedChartEdits = w.chart.edits();
        for (Tag t : tag.getList("harbors", Tag.TAG_COMPOUND)) {
            Harbor h = Harbor.load((CompoundTag) t);
            w.harbors.put(h.pos, h);
        }
        for (Tag t : tag.getList("ships", Tag.TAG_COMPOUND)) {
            Ship s = Ship.load((CompoundTag) t);
            w.ships.put(s.id, s);
        }
        for (Tag t : tag.getList("deliveries", Tag.TAG_COMPOUND)) {
            NavalDispatcher.Delivery d = NavalDispatcher.Delivery.load((CompoundTag) t);
            if (d != null && w.ships.containsKey(d.ship())) {
                w.deliveries.put(d.ship(), d);
            }
        }
        for (Tag t : tag.getList("beacons", Tag.TAG_COMPOUND)) {
            SeaBeacon b = SeaBeacon.load((CompoundTag) t);
            w.beacons.put(b.pos, b);
        }
        for (SeaBeacon.Lane l : SeaBeacon.loadLanes(tag.getList("lanes", Tag.TAG_COMPOUND))) {
            w.lanes.put(l.pair, l);
        }
        return w;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("chart", chart.save());
        tag.putLongArray("chartLand", chart.saveLand());
        tag.putInt("chartVersion", WaterChart.FORMAT);
        savedChartEdits = chart.edits();
        ListTag hl = new ListTag();
        for (Harbor h : harbors.values()) {
            hl.add(h.save());
        }
        tag.put("harbors", hl);
        ListTag sl = new ListTag();
        for (Ship s : ships.values()) {
            sl.add(s.save());
        }
        tag.put("ships", sl);
        ListTag dl = new ListTag();
        for (NavalDispatcher.Delivery d : deliveries.values()) {
            dl.add(d.save());
        }
        tag.put("deliveries", dl);
        ListTag bl = new ListTag();
        for (SeaBeacon b : beacons.values()) {
            bl.add(b.save());
        }
        tag.put("beacons", bl);
        tag.put("lanes", SeaBeacon.saveLanes(lanes.values()));
        return tag;
    }

    // ------------------------------------------------------------------ balises maritimes (0.20.27)

    public Iterable<SeaBeacon> beacons() {
        return beacons.values();
    }

    public Iterable<SeaBeacon.Lane> lanes() {
        return lanes.values();
    }

    @Nullable
    public SeaBeacon beacon(long pos) {
        return beacons.get(pos);
    }

    @Nullable
    public SeaBeacon.Lane lane(long a, long b) {
        return lanes.get(SeaBeacon.Pair.of(a, b));
    }

    /** Ports a portee de vue ({@link SeaBeacon#VIEW}) des balises du reseau de {@code from} (liaisons tracees), noms tries. */
    public List<String> destinations(SeaBeacon from) {
        Set<Long> seen = new HashSet<>();
        Deque<SeaBeacon> todo = new ArrayDeque<>();
        todo.add(from);
        seen.add(from.pos);
        Set<String> names = new TreeSet<>();
        while (!todo.isEmpty()) {
            SeaBeacon b = todo.poll();
            for (Harbor h : harbors.values()) {
                double[] a = h.anchorage(h.tier);
                if (Math.hypot(a[0] - b.ax, a[1] - b.az) <= SeaBeacon.VIEW) {
                    names.add(h.name.isEmpty() ? h.blockPos().toShortString() : h.name);
                }
            }
            for (long o : b.links) {
                SeaBeacon.Lane l = lane(b.pos, o);
                SeaBeacon n = beacons.get(o);
                if (n != null && l != null && l.state == SeaBeacon.Lane.State.OK && seen.add(o)) {
                    todo.add(n);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** Balise posee : inscrite, point d'eau = la case navigable la plus proche (32 blocs), sinon la balise elle-meme. */
    public SeaBeacon addBeacon(BlockPos pos) {
        boolean was = charting();
        SeaBeacon b = beacons.computeIfAbsent(pos.asLong(), SeaBeacon::new);
        anchor(b);
        beaconVersion++;
        setDirty();
        startCharting(was, pos);
        return b;
    }

    void anchor(SeaBeacon b) {
        BlockPos p = b.blockPos();
        int cx = WaterChart.cell(p.getX()), cz = WaterChart.cell(p.getZ());
        double best = Double.MAX_VALUE;
        for (int dz = -16; dz <= 16; dz++) {
            for (int dx = -16; dx <= 16; dx++) {
                double d = dx * dx + dz * dz;
                if (d < best && chart.clear(cx + dx, cz + dz, 0, true)) {
                    best = d;
                    b.ax = (cx + dx) * 2 + 1.0;
                    b.az = (cz + dz) * 2 + 1.0;
                }
            }
        }
    }

    /** Balise cassee : ses liaisons disparaissent. */
    public void removeBeacon(BlockPos pos) {
        SeaBeacon b = beacons.remove(pos.asLong());
        if (b == null) {
            return;
        }
        for (long o : b.links) {
            SeaBeacon other = beacons.get(o);
            if (other != null) {
                other.links.remove(b.pos);
            }
            SeaBeacon.Pair pair = SeaBeacon.Pair.of(b.pos, o);
            lanes.remove(pair);
            CompletableFuture<NavalPlanner.Plan> job = laneJobs.remove(pair);
            if (job != null) {
                job.cancel(false);
            }
        }
        beaconVersion++;
        setDirty();
    }

    /** Resultat d'une demande de liaison. */
    public enum LinkResult { LINKED, UNLINKED, SAME, TOO_FAR, MISSING }

    /**
     * Relie deux balises (ou les delie si elles l'etaient). La route de la liaison est calculee dans le fil du planificateur ; le joueur
     * {@code asker} est prevenu du resultat.
     */
    public LinkResult toggleLink(long a, long b, @Nullable UUID asker) {
        SeaBeacon x = beacons.get(a), y = beacons.get(b);
        if (x == null || y == null) {
            return LinkResult.MISSING;
        }
        if (a == b) {
            return LinkResult.SAME;
        }
        SeaBeacon.Pair pair = SeaBeacon.Pair.of(a, b);
        if (x.links.contains(b)) {
            x.links.remove(b);
            y.links.remove(a);
            lanes.remove(pair);
            CompletableFuture<NavalPlanner.Plan> job = laneJobs.remove(pair);
            if (job != null) {
                job.cancel(false);
            }
            beaconVersion++;
            setDirty();
            return LinkResult.UNLINKED;
        }
        if (Math.hypot(x.ax - y.ax, x.az - y.az) > SeaBeacon.MAX_LINK) {
            return LinkResult.TOO_FAR;
        }
        x.links.add(b);
        y.links.add(a);
        anchor(x);
        anchor(y);
        startLane(pair, asker);
        setDirty();
        return LinkResult.LINKED;
    }

    private void startLane(SeaBeacon.Pair pair, @Nullable UUID asker) {
        SeaBeacon x = beacons.get(pair.a()), y = beacons.get(pair.b());
        if (x == null || y == null) {
            return;
        }
        lanes.put(pair, new SeaBeacon.Lane(pair));
        if (asker != null) {
            laneAsker.put(pair, asker);
        }
        laneJobs.put(pair, planner.lane(x.ax, x.az, y.ax, y.az));
    }

    /** Liaisons : resultats des calculs, liaisons sans route (chargement d'un monde) relancees. */
    private void tickLanes(@Nullable ServerLevel level, long now) {
        if (!laneJobs.isEmpty()) {
            for (var it = laneJobs.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (!e.getValue().isDone()) {
                    continue;
                }
                it.remove();
                SeaBeacon.Lane lane = lanes.get(e.getKey());
                if (lane == null || e.getValue().isCancelled()) {
                    continue;
                }
                NavalPlanner.Plan p;
                try {
                    p = e.getValue().join();
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.warn("Pioneer's : echec du calcul d'une voie maritime", ex);
                    p = new NavalPlanner.Plan(null, 0, 0);
                }
                if (p.route() != null) {
                    lane.points = p.route();
                    lane.length = ShipPathfinder.length(p.route());
                    lane.state = SeaBeacon.Lane.State.OK;
                } else {
                    lane.points = List.of();
                    lane.state = SeaBeacon.Lane.State.NONE;
                }
                beaconVersion++;
                setDirty();
                UUID asker = laneAsker.remove(e.getKey());
                if (asker != null && level != null) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(asker);
                    if (player != null) {
                        player.displayClientMessage(lane.state == SeaBeacon.Lane.State.OK
                              ? Component.translatable("naval.pioneers.beacon.lane_ok", Math.round(lane.length))
                              : Component.translatable("naval.pioneers.beacon.lane_none"), false);
                    }
                }
            }
        }
        if (now % 100 == 0) {
            for (SeaBeacon b : beacons.values()) {
                for (long o : b.links) {
                    SeaBeacon.Pair pair = SeaBeacon.Pair.of(b.pos, o);
                    if (!lanes.containsKey(pair) && !laneJobs.containsKey(pair)) {
                        startLane(pair, null);
                    }
                }
            }
        }
    }

    /** Copie des balises pour le fil du planificateur. */
    private List<NavalPlanner.BeaconView> beaconViews() {
        List<NavalPlanner.BeaconView> out = new ArrayList<>();
        for (SeaBeacon b : beacons.values()) {
            out.add(new NavalPlanner.BeaconView(b.pos, b.ax, b.az));
        }
        return out;
    }

    /** Copie des liaisons praticables pour le fil du planificateur. */
    private List<NavalPlanner.LaneView> laneViews() {
        List<NavalPlanner.LaneView> out = new ArrayList<>();
        for (SeaBeacon.Lane l : lanes.values()) {
            if (l.state == SeaBeacon.Lane.State.OK && l.points.size() >= 2) {
                out.add(new NavalPlanner.LaneView(l.pair.a(), l.pair.b(), l.points, l.length));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ banc d'essai

    /** Banc : calculs dans le fil courant, sonde des eaux inconnues au choix. */
    public void benchMode(@Nullable SeaSounder.Probe probe) {
        planner.executor = Runnable::run;
        planner.probe = probe;
        probed = true;
    }

    public NavalPlanner planner() {
        return planner;
    }

    /** Banc : place directement un navire (sans entite). */
    public void put(Ship s) {
        ships.put(s.id, s);
    }

    /** Banc : un tick sans monde (pas de vues). */
    public void tickBench(long now) {
        planner.executor = Runnable::run;
        for (Ship s : new ArrayList<>(ships.values())) {
            tickShip(s, now);
        }
        tickLanes(null, now);
        if (now % 20 == 0) {
            NavalDispatcher.tick(this, now);
        }
    }

    public void putHarbor(Harbor h) {
        harbors.put(h.pos, h);
    }
}
