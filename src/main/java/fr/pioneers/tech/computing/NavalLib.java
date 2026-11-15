package fr.pioneers.tech.computing;

import fr.pioneers.tech.computing.script.ScriptError;
import fr.pioneers.tech.computing.script.Value.PDict;
import fr.pioneers.tech.computing.script.Value.PList;
import fr.pioneers.tech.computing.script.Value.PObject;
import fr.pioneers.tech.computing.script.Value;
import fr.pioneers.tech.computing.script.Vm;
import fr.pioneers.logistics.naval.Harbor;
import fr.pioneers.logistics.naval.NavalWorld;
import fr.pioneers.logistics.naval.Ship;
import fr.pioneers.logistics.naval.ShipKind;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : bibliotheque {@code naval} de PioScript (la marine de la dimension de l'ordinateur) :
 * <ul>
 *     <li>{@code naval.ships()} : fiches des navires (nom, id, sorte, tier, statut, auto, port vise, logistique, vitesse, remplissage, carburant) ;</li>
 *     <li>{@code naval.ship(nom)} : la fiche d'un navire (nom ou debut de l'id), ou none ;</li>
 *     <li>{@code naval.send(nom, port)} : envoie un navire a un port (arret temporaire) ;</li>
 *     <li>{@code naval.ports()} : ports (nom, position, tier, poste, plus grand navire accepte, role, navire a quai).</li>
 * </ul>
 */
final class NavalLib {

    private NavalLib() {
    }

    private static void arity(String name, Object[] a, int n) {
        if (a.length != n) {
            throw new ScriptError("arity", name, n, a.length);
        }
    }

    static void install(Vm vm, ComputerTile tile) {
        PObject naval = new PObject("naval")
              .fn("ships", a -> {
                  PList out = new PList();
                  NavalWorld w = world(tile);
                  if (w != null) {
                      for (Ship s : w.ships()) {
                          out.add(ship(w, s));
                      }
                  }
                  return out;
              })
              .fn("ship", a -> {
                  arity("naval.ship", a, 1);
                  NavalWorld w = world(tile);
                  Ship s = w == null ? null : find(w, Value.str(a[0]));
                  return s == null ? Value.NONE : ship(w, s);
              })
              .fn("send", a -> {
                  arity("naval.send", a, 2);
                  NavalWorld w = world(tile);
                  Ship s = w == null ? null : find(w, Value.str(a[0]));
                  if (s == null) {
                      return false;
                  }
                  w.sendTo(s, Value.str(a[1]).trim());
                  return true;
              })
              .fn("ports", a -> {
                  PList out = new PList();
                  NavalWorld w = world(tile);
                  if (w != null) {
                      for (Harbor h : w.harbors()) {
                          PDict d = new PDict();
                          BlockPos p = h.blockPos();
                          d.put("name", h.name);
                          d.put("x", (long) p.getX());
                          d.put("y", (long) p.getY());
                          d.put("z", (long) p.getZ());
                          d.put("tier", (long) h.tier);
                          d.put("berth", h.berth.name().toLowerCase(Locale.ROOT));
                          d.put("max_tier", (long) h.maxTier);
                          d.put("role", h.logistics.role.name().toLowerCase(Locale.ROOT));
                          Ship s = w.docked(h.pos);
                          d.put("docked", s == null ? "" : label(s));
                          out.add(d);
                      }
                  }
                  return out;
              });
        vm.define("naval", naval);
    }

    @Nullable
    private static NavalWorld world(ComputerTile tile) {
        return tile.getLevel() instanceof ServerLevel sl ? NavalWorld.get(sl) : null;
    }

    private static String label(Ship s) {
        return s.name.isEmpty() ? s.id.toString().substring(0, 8) : s.name;
    }

    @Nullable
    private static Ship find(NavalWorld w, String name) {
        String n = name.trim();
        for (Ship s : w.ships()) {
            if (s.name.equalsIgnoreCase(n)) {
                return s;
            }
        }
        if (n.length() >= 4) {
            for (Ship s : w.ships()) {
                if (s.id.toString().startsWith(n.toLowerCase(Locale.ROOT))) {
                    return s;
                }
            }
        }
        return null;
    }

    private static PDict ship(NavalWorld w, Ship s) {
        PDict d = new PDict();
        d.put("name", label(s));
        d.put("id", s.id.toString());
        d.put("kind", s.kind.lower());
        d.put("tier", (long) s.tier);
        d.put("status", s.status.name().toLowerCase(Locale.ROOT));
        d.put("auto", s.auto);
        Harbor h = s.harbor == 0 ? null : w.harbor(s.harbor);
        d.put("port", h == null ? "" : h.name);
        d.put("logistic", s.logistic);
        d.put("speed", Math.round(s.speed * 20 * 10) / 10.0);
        d.put("fill", Math.round(s.fill() * 1000) / 1000.0);
        d.put("fuel", (long) w.fuelPercent(s));
        d.put("length", (long) ShipKind.length(s.tier));
        return d;
    }
}
