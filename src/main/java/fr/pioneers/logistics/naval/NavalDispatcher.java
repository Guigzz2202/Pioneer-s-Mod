package fr.pioneers.logistics.naval;

import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.StationLogistics;
import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.Schedule;
import fr.pioneers.logistics.rail.train.Train;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : repartiteur naval, sur le modele de celui du rail. Chaque seconde : pour chaque port qui DEMANDE, ce qui manque (niveau vise -
 * stock du port - deja en route) ; s'il depasse le seuil et qu'un port FOURNIT la ressource, le navire logistique libre de la bonne sorte le
 * plus proche du fournisseur (vide, a quai dans un port DEPOT, accepte par les deux ports) recoit trois arrets temporaires : charger au
 * fournisseur, decharger au demandeur, revenir au depot.
 */
public final class NavalDispatcher {

    private NavalDispatcher() {
    }

    /** Livraison en cours : navire, ressource, ports et quantite prevue. */
    public record Delivery(UUID ship, Cargo cargo, long from, long to, long amount) {

        public CompoundTag save() {
            CompoundTag t = cargo.save();
            t.putUUID("ship", ship);
            t.putLong("from", from);
            t.putLong("to", to);
            t.putLong("amount", amount);
            return t;
        }

        @Nullable
        public static Delivery load(CompoundTag t) {
            if (!t.hasUUID("ship")) {
                return null;
            }
            return new Delivery(t.getUUID("ship"), Cargo.load(t), t.getLong("from"), t.getLong("to"), t.getLong("amount"));
        }
    }

    /** Consigne du repartiteur pour un navire a ce port : charger ou decharger cette ressource. */
    public record Plan(boolean load, Cargo cargo) {}

    @Nullable
    public static Plan plan(NavalWorld w, Ship s, long harbor) {
        Delivery d = w.deliveries.get(s.id);
        if (d == null || s.tempKind != Train.TempKind.DELIVERY || !s.inTemp()) {
            return null;
        }
        if (s.tempIndex == 0 && harbor == d.from) {
            return new Plan(true, d.cargo);
        }
        if (s.tempIndex == 1 && harbor == d.to) {
            return new Plan(false, d.cargo);
        }
        return null;
    }

    static void tick(NavalWorld w, long now) {
        for (Harbor demand : w.harbors()) {
            StationLogistics l = demand.logistics;
            if (!l.role.demands()) {
                continue;
            }
            for (StationLogistics.Entry e : l.entries) {
                Cargo c = e.cargo();
                if (c.any()) {
                    continue;
                }
                long missing = e.amount() - w.stockOf(demand.pos, c) - inFlight(w, demand.pos, c);
                if (missing < l.threshold(c)) {
                    continue;
                }
                Harbor supply = supplier(w, demand, c);
                if (supply == null) {
                    continue;
                }
                Ship ship = freeShip(w, supply, demand, c);
                if (ship == null) {
                    continue;
                }
                assign(w, ship, supply, demand, c, missing);
            }
        }
    }

    private static long inFlight(NavalWorld w, long to, Cargo c) {
        long n = 0;
        for (Delivery d : w.deliveries.values()) {
            if (d.to == to && d.cargo.covers(c)) {
                n += d.amount;
            }
        }
        return n;
    }

    @Nullable
    private static Harbor supplier(NavalWorld w, Harbor demand, Cargo c) {
        Harbor best = null;
        double bd = Double.MAX_VALUE;
        int bp = Integer.MIN_VALUE;
        for (Harbor h : w.harbors()) {
            if (h == demand || !h.logistics.role.supplies() || !h.logistics.lists(c)) {
                continue;
            }
            if (w.stockOf(h.pos, c) < h.logistics.threshold(c)) {
                continue;
            }
            BlockPos a = h.blockPos(), b = demand.blockPos();
            double d = Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
            int p = h.logistics.priority;
            if (p > bp || p == bp && d < bd) {
                best = h;
                bd = d;
                bp = p;
            }
        }
        return best;
    }

    @Nullable
    private static Ship freeShip(NavalWorld w, Harbor supply, Harbor demand, Cargo c) {
        Ship best = null;
        double bd = Double.MAX_VALUE;
        for (Ship s : w.ships()) {
            if (!s.logistic || !s.auto || s.inTemp() || s.kind.cargo != c.kind() || s.state != Ship.State.DOCKED || !s.holdsEmpty()) {
                continue;
            }
            Harbor at = w.harbor(s.harbor);
            if (at == null || !NavalWorld.isDepot(at) || !supply.accepts(s.tier) || !demand.accepts(s.tier)) {
                continue;
            }
            String group = demand.logistics.group;
            if (!group.isEmpty() && !group.equals(s.name)) {
                //Groupe du port : seuls les navires de ce nom
                continue;
            }
            if (w.fuelPercent(s) < 10) {
                continue;
            }
            BlockPos p = supply.blockPos();
            double d = Math.hypot(p.getX() - s.x, p.getZ() - s.z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    private static void assign(NavalWorld w, Ship s, Harbor supply, Harbor demand, Cargo c, long missing) {
        long depot = s.harbor;
        long cap = 0;
        for (Car h : s.holds) {
            cap += c.room(h);
        }
        s.temp.clear();
        Schedule.Stop load = stop(supply);
        load.conditions.add(cond(Schedule.Type.FULL, false, 0));
        load.conditions.add(cond(Schedule.Type.INACTIVITY, true, 15));
        load.conditions.add(cond(Schedule.Type.TIME, true, 120));
        Schedule.Stop unload = stop(demand);
        unload.conditions.add(cond(Schedule.Type.EMPTY, false, 0));
        unload.conditions.add(cond(Schedule.Type.INACTIVITY, true, 15));
        Harbor home = w.harbor(depot);
        Schedule.Stop back = home == null ? null : stop(home);
        s.temp.add(load);
        s.temp.add(unload);
        if (back != null) {
            s.temp.add(back);
        }
        s.tempIndex = 0;
        s.tempKind = Train.TempKind.DELIVERY;
        w.deliveries.put(s.id, new Delivery(s.id, c, supply.pos, demand.pos, Math.min(missing, cap)));
        w.setDirty();
    }

    private static Schedule.Stop stop(Harbor h) {
        Schedule.Stop st = new Schedule.Stop();
        st.station = h.name;
        st.target = h.pos;
        return st;
    }

    private static Schedule.Condition cond(Schedule.Type type, boolean or, int value) {
        Schedule.Condition c = new Schedule.Condition();
        c.type = type;
        c.or = or;
        c.value = value;
        return c;
    }

    /** Navire logistique sans tache hors d'un depot : il rejoint le depot le plus proche qui l'accepte. */
    static void toDepot(NavalWorld w, Ship s) {
        Harbor best = null;
        double bd = Double.MAX_VALUE;
        for (Harbor h : w.harbors()) {
            if (!NavalWorld.isDepot(h) || !h.accepts(s.tier)) {
                continue;
            }
            BlockPos p = h.blockPos();
            double d = Math.hypot(p.getX() - s.x, p.getZ() - s.z);
            if (d < bd) {
                bd = d;
                best = h;
            }
        }
        if (best != null) {
            s.temp.clear();
            s.temp.add(stop(best));
            s.tempIndex = 0;
            s.tempKind = Train.TempKind.ORDER;
            w.setDirty();
        }
    }
}
