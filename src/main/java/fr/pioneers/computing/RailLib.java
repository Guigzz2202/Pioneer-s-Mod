package fr.pioneers.computing;

import fr.pioneers.computing.script.ScriptError;
import fr.pioneers.computing.script.Value.PDict;
import fr.pioneers.computing.script.Value.PList;
import fr.pioneers.computing.script.Value.PObject;
import fr.pioneers.computing.script.Value;
import fr.pioneers.computing.script.Vm;
import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.logistics.Delivery;
import fr.pioneers.rail.net.RailNetwork;
import fr.pioneers.rail.train.Train;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : bibliotheque {@code rail} de PioScript (le chemin de fer de la dimension de l'ordinateur) :
 * <ul>
 *     <li>{@code rail.trains()} : liste de fiches (nom, id, statut, auto, gare visee, groupe, logistique, vitesse, vehicules, carburant) ;</li>
 *     <li>{@code rail.train(nom)} : la fiche d'un train (nom ou debut de l'id), ou none ;</li>
 *     <li>{@code rail.send(nom, gare)} : envoie un train automatique a une gare (arret temporaire, puis il reprend son horaire) ;</li>
 *     <li>{@code rail.stations()}, {@code rail.station(nom)} : gares (nom, position, limite, role, trains en route, stock du quai) ;</li>
 *     <li>{@code rail.set_limit(nom, n)} : limite de trains de toutes les gares de ce nom (-1 : sans limite) ;</li>
 *     <li>{@code rail.signal(nom, valeur)}, {@code rail.get_signal(nom)} : signaux nommes lus par la condition d'horaire « Signal » ;</li>
 *     <li>{@code rail.weigh(x, y, z)} : pesee du pont-bascule (masse, vehicules, remplissage, train), ou none ;</li>
 *     <li>{@code rail.deliveries()} : livraisons du repartiteur en cours ;</li>
 *     <li>1.9.2 (retours de Guillaume) : {@code rail.turn} et {@code rail.table} retires avec les plaques tournantes et les transbordeurs.</li>
 * </ul>
 */
final class RailLib {

    private RailLib() {
    }

    private static void arity(String name, Object[] a, int n) {
        if (a.length != n) {
            throw new ScriptError("arity", name, n, a.length);
        }
    }

    static void install(Vm vm, ComputerTile tile) {
        PObject rail = new PObject("rail")
              .fn("trains", a -> {
                  PList out = new PList();
                  RailWorld w = world(tile);
                  if (w != null) {
                      for (Train t : w.trains()) {
                          out.add(train(w, t));
                      }
                  }
                  return out;
              })
              .fn("train", a -> {
                  arity("rail.train", a, 1);
                  RailWorld w = world(tile);
                  Train t = w == null ? null : find(w, Value.str(a[0]));
                  return t == null ? Value.NONE : train(w, t);
              })
              .fn("send", a -> {
                  arity("rail.send", a, 2);
                  RailWorld w = world(tile);
                  Train t = w == null ? null : find(w, Value.str(a[0]));
                  return t != null && w.sendTo(t, Value.str(a[1]).trim());
              })
              .fn("stations", a -> {
                  PList out = new PList();
                  RailWorld w = world(tile);
                  if (w != null) {
                      for (RailNetwork.Station s : w.net.stations()) {
                          out.add(station(w, s, false));
                      }
                  }
                  return out;
              })
              .fn("station", a -> {
                  arity("rail.station", a, 1);
                  RailWorld w = world(tile);
                  if (w == null) {
                      return Value.NONE;
                  }
                  for (RailNetwork.Station s : w.net.stationsNamed(Value.str(a[0]).trim())) {
                      return station(w, s, true);
                  }
                  return Value.NONE;
              })
              .fn("set_limit", a -> {
                  arity("rail.set_limit", a, 2);
                  RailWorld w = world(tile);
                  if (w == null) {
                      return 0L;
                  }
                  int limit = (int) Math.max(-1, Math.min(99, Value.integer(a[1])));
                  long n = 0;
                  for (RailNetwork.Station s : w.net.stationsNamed(Value.str(a[0]).trim())) {
                      if (s.limit != limit) {
                          s.limit = limit;
                          n++;
                      }
                  }
                  if (n > 0) {
                      w.setDirty();
                  }
                  return n;
              })
              .fn("signal", a -> {
                  arity("rail.signal", a, 2);
                  RailWorld w = world(tile);
                  if (w != null) {
                      w.setSignal(Value.str(a[0]), (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Value.integer(a[1]))));
                  }
                  return Value.NONE;
              })
              .fn("get_signal", a -> {
                  arity("rail.get_signal", a, 1);
                  RailWorld w = world(tile);
                  return w == null ? 0L : (long) w.signals.getOrDefault(Value.str(a[0]).trim().toLowerCase(Locale.ROOT), 0);
              })
              .fn("weigh", a -> {
                  arity("rail.weigh", a, 3);
                  RailWorld w = world(tile);
                  BlockPos p = new BlockPos((int) Value.integer(a[0]), (int) Value.integer(a[1]), (int) Value.integer(a[2]));
                  RailWorld.Weigh r = w == null ? null : w.weigh(p);
                  if (r == null) {
                      return Value.NONE;
                  }
                  PDict d = new PDict();
                  d.put("mass", Math.round(r.mass() * 10) / 10.0);
                  d.put("cars", (long) r.cars());
                  d.put("fill", Math.round(r.fill() * 1000) / 1000.0);
                  d.put("train", r.train());
                  return d;
              })
              .fn("deliveries", a -> {
                  PList out = new PList();
                  RailWorld w = world(tile);
                  if (w != null) {
                      for (Delivery d : w.dispatcher.deliveries()) {
                          PDict o = new PDict();
                          o.put("cargo", d.cargo.toString());
                          o.put("amount", d.amount);
                          o.put("state", d.state.name().toLowerCase(Locale.ROOT));
                          RailNetwork.Station from = w.net.station(d.from), to = w.net.station(d.to);
                          o.put("from", from == null ? "" : from.name);
                          o.put("to", to == null ? "" : to.name);
                          Train t = w.train(d.train);
                          o.put("train", t == null ? "" : label(t));
                          out.add(o);
                      }
                  }
                  return out;
              });
        vm.define("rail", rail);
    }

    @Nullable
    private static RailWorld world(ComputerTile tile) {
        return tile.getLevel() instanceof ServerLevel sl ? RailWorld.get(sl) : null;
    }

    private static String label(Train t) {
        return t.name.isEmpty() ? t.id.toString().substring(0, 8) : t.name;
    }

    @Nullable
    private static Train find(RailWorld w, String name) {
        String n = name.trim();
        for (Train t : w.trains()) {
            if (t.name.equalsIgnoreCase(n)) {
                return t;
            }
        }
        if (n.length() >= 4) {
            for (Train t : w.trains()) {
                if (t.id.toString().startsWith(n.toLowerCase(Locale.ROOT))) {
                    return t;
                }
            }
        }
        return null;
    }

    private static PDict train(RailWorld w, Train t) {
        PDict d = new PDict();
        d.put("name", label(t));
        d.put("id", t.id.toString());
        d.put("status", t.status.name().toLowerCase(Locale.ROOT));
        d.put("auto", t.auto);
        RailNetwork.Station dest = w.net.station(t.destination);
        d.put("station", dest == null ? "" : dest.name);
        d.put("group", t.group);
        d.put("logistic", t.logistic);
        d.put("speed", Math.round(Math.abs(t.speed) / 0.8 * 10) / 10.0);
        d.put("cars", (long) t.cars.size());
        d.put("fuel", (long) w.fuelPercent(t));
        d.put("mass", Math.round(t.mass() * 10) / 10.0);
        return d;
    }

    private static PDict station(RailWorld w, RailNetwork.Station s, boolean stock) {
        PDict d = new PDict();
        d.put("name", s.name);
        d.put("x", (long) s.pos.getX());
        d.put("y", (long) s.pos.getY());
        d.put("z", (long) s.pos.getZ());
        d.put("limit", (long) s.limit);
        d.put("role", s.logistics.role.name().toLowerCase(Locale.ROOT));
        long coming = 0;
        for (Train t : w.trains()) {
            if (t.auto && t.destination == s.pos.asLong()) {
                coming++;
            }
        }
        d.put("trains", coming);
        if (stock) {
            PDict st = new PDict();
            for (Map.Entry<Cargo, Long> e : w.stocks.stock(s).entrySet()) {
                st.put(e.getKey().toString(), e.getValue());
            }
            d.put("stock", st);
        }
        return d;
    }
}
