package fr.pioneers.logistics.rail;

import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.Delivery;
import fr.pioneers.logistics.rail.logistics.RailAlert;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.Schedule;
import fr.pioneers.logistics.rail.train.Train;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : le repartiteur (comme LTN dans Factorio). Chaque seconde il rapproche les demandes des gares logistiques (niveau vise - stock -
 * deja en route) des offres des gares fournisseuses (contenu du quai - deja reserve), au-dela du seuil de livraison ; il choisit le train
 * logistique libre (arrete a un depot, vide, du groupe autorise, aux wagons compatibles, assez de carburant) le plus proche du fournisseur et
 * lui donne trois arrets temporaires : charger, decharger, revenir au depot. Les livraisons et les alertes sont suivies jusqu'au bout.
 */
public final class RailDispatcher {

    /** Attente sans activite avant de repartir avec un chargement partiel ou de decharger (ticks). */
    static final int PARTIAL = 15 * 20, GIVE_UP = 120 * 20, STUCK = 60 * 20;

    private final RailWorld world;
    final Map<UUID, Delivery> deliveries = new LinkedHashMap<>();
    private final Map<String, RailAlert> alerts = new LinkedHashMap<>();

    RailDispatcher(RailWorld world) {
        this.world = world;
    }

    public Iterable<Delivery> deliveries() {
        return deliveries.values();
    }

    @Nullable
    public Delivery delivery(@Nullable UUID id) {
        return id == null ? null : deliveries.get(id);
    }

    public Iterable<RailAlert> alerts() {
        return alerts.values();
    }

    private void alert(RailAlert.Type type, long pos, String subject, long now) {
        String key = type + "|" + pos + "|" + subject;
        RailAlert old = alerts.get(key);
        alerts.put(key, old == null ? new RailAlert(type, pos, subject, now, now) : old.seen(now));
    }

    // ------------------------------------------------------------------ cycle

    void tick(long now) {
        //Alertes eteintes 30 s apres la derniere fois
        alerts.values().removeIf(a -> now - a.last() > 600);
        //Livraisons orphelines (train disparu, coupe, plus logistique)
        for (Iterator<Delivery> it = deliveries.values().iterator(); it.hasNext(); ) {
            Delivery d = it.next();
            Train t = world.train(d.train);
            if (t == null || !t.auto || !d.id.equals(t.delivery) || t.tempKind != Train.TempKind.DELIVERY) {
                if (t != null && d.id.equals(t.delivery)) {
                    t.delivery = null;
                    if (t.tempKind == Train.TempKind.DELIVERY) {
                        t.clearTemp();
                    }
                }
                alert(RailAlert.Type.ABANDONED, d.to, d.cargo.toString(), now);
                it.remove();
                world.setDirty();
            }
        }
        if (now % 20 != 7) {
            return;
        }
        List<RailNetwork.Station> demands = new ArrayList<>(), supplies = new ArrayList<>();
        for (RailNetwork.Station s : world.net.stations()) {
            if (s.logistics.role.demands()) {
                demands.add(s);
            }
            if (s.logistics.role.supplies()) {
                supplies.add(s);
            }
        }
        if (demands.isEmpty() || supplies.isEmpty()) {
            return;
        }
        //Priorite haute d'abord
        demands.sort((a, b) -> b.logistics.priority - a.logistics.priority);
        Map<Long, Map<Cargo, Long>> stockCache = new HashMap<>();
        for (RailNetwork.Station d : demands) {
            for (Map.Entry<Cargo, Long> want : wants(d).entrySet()) {
                Cargo cargo = want.getKey();
                long threshold = d.logistics.threshold(cargo);
                long have = world.stocks.network(d, cargo);
                if (have < 0) {
                    have = stockOf(stockCache, d, cargo);
                }
                long deficit = want.getValue() - have - inFlightTo(d, cargo);
                if (deficit < threshold) {
                    continue;
                }
                RailNetwork.Station best = null;
                long bestAvail = 0;
                double bestScore = Double.MAX_VALUE;
                for (RailNetwork.Station s : supplies) {
                    if (s == d || !s.logistics.lists(cargo)) {
                        continue;
                    }
                    long avail = stockOf(stockCache, s, cargo) - reservedOut(s, cargo);
                    if (avail < threshold) {
                        continue;
                    }
                    double score = -s.logistics.priority * 1e6 + Math.sqrt(s.pos.distSqr(d.pos));
                    if (score < bestScore) {
                        best = s;
                        bestAvail = avail;
                        bestScore = score;
                    }
                }
                if (best == null) {
                    continue;
                }
                Train train = freeTrain(cargo, best, d, threshold);
                if (train == null) {
                    alert(RailAlert.Type.NO_TRAIN, d.pos.asLong(), cargo.toString(), now);
                    continue;
                }
                long qty = Math.min(deficit, Math.min(bestAvail, capacity(train, cargo)));
                if (qty < threshold) {
                    continue;
                }
                assign(train, cargo, qty, best, d, now);
            }
        }
    }

    /** Ce que la gare demande : ses ressources, plus ce qui manque aux commandes de ses terminaux. */
    private Map<Cargo, Long> wants(RailNetwork.Station d) {
        Map<Cargo, Long> out = new LinkedHashMap<>();
        for (StationLogistics.Entry e : d.logistics.entries) {
            if (!e.cargo().any() || e.cargo().kind() == Cargo.Kind.ENERGY || e.cargo().kind() == Cargo.Kind.AIR) {
                out.merge(e.cargo(), e.amount(), Long::sum);
            }
        }
        if (d.logistics.terminal) {
            for (Map.Entry<Cargo, Long> m : world.stocks.terminalMissing(d).entrySet()) {
                //Le manque s'ajoute au niveau vise : le stock du reseau ne le couvre pas
                long have = world.stocks.network(d, m.getKey());
                out.merge(m.getKey(), Math.max(0, have) + m.getValue(), Math::max);
            }
        }
        return out;
    }

    private long stockOf(Map<Long, Map<Cargo, Long>> cache, RailNetwork.Station s, Cargo c) {
        Map<Cargo, Long> m = cache.computeIfAbsent(s.pos.asLong(), k -> world.stocks.stock(s));
        long n = 0;
        for (Map.Entry<Cargo, Long> e : m.entrySet()) {
            if (c.covers(e.getKey())) {
                n += e.getValue();
            }
        }
        return n;
    }

    long inFlightTo(RailNetwork.Station s, Cargo c) {
        long n = 0;
        for (Delivery d : deliveries.values()) {
            if (d.to == s.pos.asLong() && c.covers(d.cargo)) {
                n += d.inFlight();
            }
        }
        return n;
    }

    long reservedOut(RailNetwork.Station s, Cargo c) {
        long n = 0;
        for (Delivery d : deliveries.values()) {
            if (d.from == s.pos.asLong() && c.covers(d.cargo)) {
                n += d.reservedOut();
            }
        }
        return n;
    }

    /** Place totale du train pour la ressource. */
    static long capacity(Train t, Cargo c) {
        long n = 0;
        for (Car car : t.cars) {
            n += c.room(car);
        }
        return n;
    }

    static long carried(Train t, Cargo c) {
        long n = 0;
        for (Car car : t.cars) {
            n += c.in(car);
        }
        return n;
    }

    /** Train logistique libre le plus proche du fournisseur. */
    @Nullable
    private Train freeTrain(Cargo cargo, RailNetwork.Station from, RailNetwork.Station to, long threshold) {
        Train best = null;
        double bestD = Double.MAX_VALUE;
        for (Train t : world.trains()) {
            if (!available(t)) {
                continue;
            }
            String g1 = from.logistics.group, g2 = to.logistics.group;
            if (!g1.isBlank() && !g1.equalsIgnoreCase(t.group) || !g2.isBlank() && !g2.equalsIgnoreCase(t.group)) {
                continue;
            }
            if (capacity(t, cargo) < threshold || world.fuelPercent(t) < 20) {
                continue;
            }
            RailNetwork.Station depot = world.net.station(t.destination);
            double dist = depot == null ? 1e9 : Math.sqrt(depot.pos.distSqr(from.pos));
            if (dist < bestD) {
                best = t;
                bestD = dist;
            }
        }
        return best;
    }

    /** Arrete a un depot, vide, sans tache. */
    boolean available(Train t) {
        if (!t.logistic || !t.auto || t.inTemp() || t.delivery != null || t.status != Train.Status.AT_STATION) {
            return false;
        }
        if (!world.atDepot(t)) {
            return false;
        }
        for (Car c : t.cars) {
            if (RailWorld.cargo(c) && !c.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void assign(Train t, Cargo cargo, long qty, RailNetwork.Station from, RailNetwork.Station to, long now) {
        RailNetwork.Station depot = world.net.station(t.destination);
        String depotName = depot == null ? "" : depot.name;
        Delivery d = new Delivery(UUID.randomUUID(), cargo, qty, from.pos.asLong(), to.pos.asLong(), t.id, depotName, now);
        deliveries.put(d.id, d);
        List<Schedule.Stop> stops = new ArrayList<>();
        stops.add(stop(from));
        stops.add(stop(to));
        Schedule.Stop back = new Schedule.Stop();
        back.station = depotName;
        stops.add(back);
        t.delivery = d.id;
        world.setTemp(t, stops, Train.TempKind.DELIVERY, "");
        world.setDirty();
    }

    private static Schedule.Stop stop(RailNetwork.Station s) {
        Schedule.Stop st = new Schedule.Stop();
        st.station = s.name;
        st.target = s.pos.asLong();
        return st;
    }

    // ------------------------------------------------------------------ deroulement (appele par le pilote automatique)

    /** Arrivee a un arret de livraison. */
    void arrived(Train t, long now) {
        Delivery d = delivery(t.delivery);
        if (d == null) {
            return;
        }
        switch (t.tempIndex) {
            case 0 -> d.state = Delivery.State.LOADING;
            case 1 -> d.state = Delivery.State.UNLOADING;
            default -> {
                //Retour au depot : livraison terminee
                deliveries.remove(d.id);
                t.delivery = null;
                t.clearTemp();
            }
        }
        world.setDirty();
    }

    /** Le train peut-il repartir de cet arret de livraison ? */
    boolean ready(Train t, long now) {
        Delivery d = delivery(t.delivery);
        if (d == null) {
            return true;
        }
        long have = carried(t, d.cargo);
        long idle = now - t.lastActivity;
        if (t.tempIndex == 0) {
            if (have >= d.amount || have > 0 && idle >= PARTIAL) {
                return true;
            }
            if (idle >= GIVE_UP) {
                //Rien a charger : on rentre au depot
                deliveries.remove(d.id);
                t.delivery = null;
                t.tempIndex = Math.max(0, t.temp.size() - 2);
                alert(RailAlert.Type.ABANDONED, d.from, d.cargo.toString(), now);
                return true;
            }
            return false;
        }
        return have == 0 || idle >= PARTIAL;
    }

    /** Depart d'un arret de livraison. */
    void departed(Train t) {
        Delivery d = delivery(t.delivery);
        if (d == null) {
            return;
        }
        if (t.tempIndex == 0) {
            d.loaded = carried(t, d.cargo);
            d.state = Delivery.State.TO_DEMAND;
        } else if (t.tempIndex == 1) {
            d.state = Delivery.State.RETURN;
        }
        world.setDirty();
    }

    /** Train bloque pendant une livraison : abandon (alerte) apres une minute sans chemin. */
    void stuck(Train t, long now) {
        if (t.stuckTicks >= STUCK && t.delivery != null) {
            Delivery d = delivery(t.delivery);
            if (d != null) {
                deliveries.remove(d.id);
                alert(RailAlert.Type.ABANDONED, d.to, d.cargo.toString(), now);
            }
            t.delivery = null;
            t.clearTemp();
            world.setDirty();
        }
        if (t.stuckTicks >= 600) {
            alert(RailAlert.Type.STUCK, t.headBlock(world.net).asLong(), t.name.isEmpty() ? t.id.toString().substring(0, 8) : t.name, now);
        }
    }

    /** Plan de travail d'un poste pour un vehicule (null : reglage du joueur). */
    @Nullable
    RailWorld.PostPlan plan(long station, Train t) {
        RailNetwork.Station s = world.net.station(station);
        if (s == null || !s.logistics.active()) {
            return null;
        }
        Delivery d = delivery(t.delivery);
        if (d != null) {
            if (d.from == station && d.state == Delivery.State.LOADING) {
                return new RailWorld.PostPlan(false, d.cargo, Math.max(0, d.amount - carried(t, d.cargo)));
            }
            if (d.to == station && d.state == Delivery.State.UNLOADING) {
                return new RailWorld.PostPlan(true, d.cargo, Long.MAX_VALUE);
            }
            return RailWorld.PostPlan.IDLE;
        }
        if (t.logistic) {
            return RailWorld.PostPlan.IDLE;
        }
        return switch (s.logistics.role) {
            case SUPPLY -> new RailWorld.PostPlan(false, null, Long.MAX_VALUE);
            case DEMAND -> new RailWorld.PostPlan(true, null, Long.MAX_VALUE);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ sauvegarde

    ListTag save() {
        ListTag list = new ListTag();
        deliveries.values().forEach(d -> list.add(d.save()));
        return list;
    }

    void load(ListTag list) {
        deliveries.clear();
        for (Tag t : list) {
            Delivery d = Delivery.load((CompoundTag) t);
            deliveries.put(d.id, d);
        }
    }

}
