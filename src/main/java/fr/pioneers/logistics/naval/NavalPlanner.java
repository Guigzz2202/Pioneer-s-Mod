package fr.pioneers.logistics.naval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.27 : planificateur des routes maritimes (demande de Guillaume : « a chaque fois No sea route ; un navire doit aller au port quelle
 * que soit la source de l'ordre, en restant dans l'eau a 20 blocs des cotes, contourner la terre, et n'abandonner que si c'est vraiment
 * impossible »). Tourne dans un fil a part (le jeu ne rame pas pendant un long calcul) ; le navire attend au statut « Releve de la route ».
 * <ol>
 *     <li><b>Couloir direct</b> : on sonde les eaux jamais chargees du couloir autour de la ligne droite depart -> port ({@link SeaSounder}),
 *     puis A* au large des cotes dans ce couloir ({@link ShipPathfinder}). Couloir de {@link #MARGINS} 200 puis 600 blocs : la terre
 *     rencontree en chemin est contournee si un passage existe dans ce rayon.</li>
 *     <li><b>Balises maritimes</b> ({@link SeaBeacon}) : depuis les balises que le navire voit (a {@link SeaBeacon#VIEW} blocs de son depart),
 *     les routes des liaisons menent aux balises proches du port : on prend la suite la plus courte (Dijkstra), raccordee par deux petits
 *     couloirs.</li>
 *     <li><b>Grand detour</b> : couloir de 1 600 blocs.</li>
 *     <li>Sinon : aucune route maritime faisable (port enclave, mer fermee, ou terre trop large a contourner sans balises).</li>
 * </ol>
 */
public final class NavalPlanner {

    /** Demi-largeurs des couloirs successifs (blocs) et cases developpees au plus pour chacun. */
    static final double[] MARGINS = {200, 600, 1600};
    static final int[] NODES = {300_000, 600_000, 600_000};
    /** Au-dela (blocs a vol d'oiseau), on essaie les balises avant le couloir direct de 600 blocs. */
    static final double FAR = 3000;
    /** Etendue d'eau (cases) en dessous de laquelle une eau fermee est reconnue sans chercher de route. */
    static final int ENCLOSED = 60_000;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Pioneers sea planner");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private final WaterChart chart;
    /** Sonde des eaux inconnues (null : banc d'essai, dimension sans mer). */
    @Nullable
    volatile SeaSounder.Probe probe;
    /** Fil des calculs (banc : le fil courant). */
    Executor executor = POOL;

    NavalPlanner(WaterChart chart) {
        this.chart = chart;
    }

    /** Resultat : route (null : aucune), balises empruntees, chunks sondes. */
    public record Plan(@Nullable List<double[]> route, int beacons, int charted) {}

    /** Balise (copie pour le fil de calcul). */
    public record BeaconView(long pos, double x, double z) {}

    /** Liaison praticable (copie). */
    public record LaneView(long a, long b, List<double[]> points, double length) {}

    /** Route d'un navire vers un port. */
    public CompletableFuture<Plan> route(double sx, double sz, double gx, double gz, boolean heatproof, @Nullable ShipPathfinder.Approach approach,
          List<BeaconView> beacons, List<LaneView> lanes) {
        return CompletableFuture.supplyAsync(() -> plan(sx, sz, gx, gz, heatproof, approach, beacons, lanes), executor);
    }

    /** Route d'une liaison entre deux balises (null : impossible). */
    public CompletableFuture<Plan> lane(double ax, double az, double bx, double bz) {
        return CompletableFuture.supplyAsync(() -> {
            int[] charted = new int[1];
            for (int i = 0; i < MARGINS.length; i++) {
                List<double[]> r = direct(ax, az, bx, bz, false, null, i, charted);
                if (r != null) {
                    return new Plan(r, 0, charted[0]);
                }
            }
            return new Plan(null, 0, charted[0]);
        }, executor);
    }

    // ------------------------------------------------------------------ calcul (fil du planificateur)

    private Plan plan(double sx, double sz, double gx, double gz, boolean heatproof, @Nullable ShipPathfinder.Approach approach,
          List<BeaconView> beacons, List<LaneView> lanes) {
        int[] charted = new int[1];
        double d = Math.hypot(gx - sx, gz - sz);
        //Port (ou navire) au fond d'une eau fermee : inutile de chercher plus loin
        WaterChart.Reader probeReader = chart.reader(probe);
        if (ShipPathfinder.enclosed(probeReader, sx, sz, gx, gz, heatproof, ENCLOSED) || ShipPathfinder.enclosed(probeReader, gx, gz, sx, sz, heatproof,
              ENCLOSED)) {
            return new Plan(null, 0, probeReader.sounded);
        }
        charted[0] += probeReader.sounded;
        List<double[]> r = direct(sx, sz, gx, gz, heatproof, approach, 0, charted);
        if (r != null) {
            return new Plan(r, 0, charted[0]);
        }
        if (d <= FAR) {
            r = direct(sx, sz, gx, gz, heatproof, approach, 1, charted);
            if (r != null) {
                return new Plan(r, 0, charted[0]);
            }
        }
        int[] used = new int[1];
        r = viaBeacons(sx, sz, gx, gz, heatproof, approach, beacons, lanes, charted, used);
        if (r != null) {
            return new Plan(r, used[0], charted[0]);
        }
        if (d > FAR) {
            r = direct(sx, sz, gx, gz, heatproof, approach, 1, charted);
            if (r != null) {
                return new Plan(r, 0, charted[0]);
            }
        }
        r = direct(sx, sz, gx, gz, heatproof, approach, 2, charted);
        return new Plan(r, 0, charted[0]);
    }

    /** Couloir direct numero {@code level} : A* dans le couloir, eaux inconnues sondees a la volee. */
    @Nullable
    private List<double[]> direct(double sx, double sz, double gx, double gz, boolean heatproof, @Nullable ShipPathfinder.Approach approach, int level,
          int[] charted) {
        ShipPathfinder.Window w = new ShipPathfinder.Window(MARGINS[level], sx, sz, gx, gz);
        //Les eaux jamais vues sont sondees a la volee, au fil de la recherche (seulement la ou elle passe)
        WaterChart.Reader r = chart.reader(probe);
        List<double[]> route = ShipPathfinder.find(r, sx, sz, gx, gz, heatproof, approach, w, NODES[level]);
        charted[0] += r.sounded;
        return route;
    }

    /**
     * Par les balises : entrees = balises a portee de vue du depart, sorties = balises a portee du port ; Dijkstra sur les liaisons
     * praticables ; on verifie les deux raccords (petits couloirs) en commencant par la suite la plus courte.
     */
    @Nullable
    private List<double[]> viaBeacons(double sx, double sz, double gx, double gz, boolean heatproof, @Nullable ShipPathfinder.Approach approach,
          List<BeaconView> beacons, List<LaneView> lanes, int[] charted, int[] used) {
        if (beacons.isEmpty() || lanes.isEmpty()) {
            return null;
        }
        Map<Long, BeaconView> byPos = new HashMap<>();
        for (BeaconView b : beacons) {
            byPos.put(b.pos(), b);
        }
        Map<Long, List<LaneView>> adj = new HashMap<>();
        for (LaneView l : lanes) {
            if (byPos.containsKey(l.a()) && byPos.containsKey(l.b())) {
                adj.computeIfAbsent(l.a(), k -> new ArrayList<>()).add(l);
                adj.computeIfAbsent(l.b(), k -> new ArrayList<>()).add(l);
            }
        }
        List<BeaconView> entries = near(beacons, sx, sz), exits = near(beacons, gx, gz);
        if (entries.isEmpty() || exits.isEmpty()) {
            return null;
        }
        Set<Long> exitSet = new HashSet<>();
        exits.forEach(b -> exitSet.add(b.pos()));
        Set<String> failedLegs = new HashSet<>();
        Set<LaneView> blocked = new HashSet<>();
        WaterChart.Reader reader = chart.reader(probe);
        //Plusieurs essais : une suite dont un raccord ou une liaison est coupe(e) est ecartee, puis on recommence
        for (int attempt = 0; attempt < 8; attempt++) {
            //Dijkstra multi-sources : cout d'entree = distance a vol d'oiseau x 1,3
            Map<Long, Double> dist = new HashMap<>();
            Map<Long, LaneView> via = new HashMap<>();
            Map<Long, Long> origin = new HashMap<>();
            PriorityQueue<Q> open = new PriorityQueue<>(Comparator.comparingDouble(Q::cost));
            for (BeaconView e : entries) {
                if (failedLegs.contains("s" + e.pos())) {
                    continue;
                }
                double c = Math.hypot(e.x() - sx, e.z() - sz) * 1.3;
                dist.put(e.pos(), c);
                origin.put(e.pos(), e.pos());
                open.add(new Q(c, e.pos()));
            }
            while (!open.isEmpty()) {
                Q top = open.poll();
                long at = top.pos();
                if (top.cost() > dist.getOrDefault(at, Double.MAX_VALUE) + 1e-6) {
                    continue;
                }
                for (LaneView l : adj.getOrDefault(at, List.of())) {
                    if (blocked.contains(l)) {
                        continue;
                    }
                    long next = l.a() == at ? l.b() : l.a();
                    double nc = top.cost() + l.length();
                    if (nc < dist.getOrDefault(next, Double.MAX_VALUE)) {
                        dist.put(next, nc);
                        via.put(next, l);
                        origin.put(next, origin.get(at));
                        open.add(new Q(nc, next));
                    }
                }
            }
            //Meilleure sortie
            BeaconView best = null;
            double bc = Double.MAX_VALUE;
            for (BeaconView x : exits) {
                Double c = dist.get(x.pos());
                if (c == null || failedLegs.contains("g" + x.pos())) {
                    continue;
                }
                double total = c + Math.hypot(gx - x.x(), gz - x.z()) * 1.3;
                if (total < bc) {
                    bc = total;
                    best = x;
                }
            }
            if (best == null) {
                return null;
            }
            //Suite des liaisons, de l'entree vers la sortie
            List<LaneView> chain = new ArrayList<>();
            long at = best.pos();
            while (via.containsKey(at)) {
                LaneView l = via.get(at);
                chain.add(0, l);
                at = l.a() == at ? l.b() : l.a();
            }
            BeaconView entry = byPos.get(at);
            //Liaisons encore praticables ?
            boolean ok = true;
            for (LaneView l : chain) {
                if (!ShipPathfinder.routeClear(reader, l.points(), 1, heatproof)) {
                    blocked.add(l);
                    ok = false;
                }
            }
            if (!ok) {
                continue;
            }
            List<double[]> leg1 = Math.hypot(entry.x() - sx, entry.z() - sz) < 4 ? List.of(new double[]{sx, sz})
                                                                                   : leg(sx, sz, entry.x(), entry.z(), heatproof, null, charted);
            if (leg1 == null) {
                failedLegs.add("s" + entry.pos());
                continue;
            }
            List<double[]> leg2 = leg(best.x(), best.z(), gx, gz, heatproof, approach, charted);
            if (leg2 == null) {
                failedLegs.add("g" + best.pos());
                continue;
            }
            List<double[]> out = new ArrayList<>(leg1);
            long from = entry.pos();
            for (LaneView l : chain) {
                List<double[]> pts = new ArrayList<>(l.points());
                if (l.a() != from) {
                    Collections.reverse(pts);
                }
                out.addAll(pts.subList(Math.min(1, pts.size()), pts.size()));
                from = l.a() == from ? l.b() : l.a();
            }
            out.addAll(leg2.subList(Math.min(1, leg2.size()), leg2.size()));
            used[0] = chain.size() + 1;
            return out;
        }
        return null;
    }

    private record Q(double cost, long pos) {}

    /** Raccord court (couloir de 200 blocs). */
    @Nullable
    private List<double[]> leg(double ax, double az, double bx, double bz, boolean heatproof, @Nullable ShipPathfinder.Approach approach, int[] charted) {
        return direct(ax, az, bx, bz, heatproof, approach, 0, charted);
    }

    /** Balises a portee de vue d'un point, les plus proches d'abord (4 au plus). */
    private static List<BeaconView> near(List<BeaconView> all, double x, double z) {
        List<BeaconView> out = new ArrayList<>();
        for (BeaconView b : all) {
            if (Math.hypot(b.x() - x, b.z() - z) <= SeaBeacon.VIEW) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingDouble(b -> Math.hypot(b.x() - x, b.z() - z)));
        return out.size() > 4 ? out.subList(0, 4) : out;
    }
}
