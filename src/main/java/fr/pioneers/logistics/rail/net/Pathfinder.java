package fr.pioneers.logistics.rail.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : recherche de chemin des trains (A* sur les etapes du reseau). Cout = temps de parcours (longueur / vitesse limite) + penalites
 * (piece occupee par un autre train) ; un point dote d'un signal pour l'autre sens seulement est infranchissable (voie a sens unique).
 * Plusieurs buts possibles (gares de meme nom) : le premier atteint gagne.
 */
public final class Pathfinder {

    /** But : la tete sort par {@code port} en roulant vers {@code heading}. */
    public record Goal(PortKey port, Direction heading, long id) {}

    public record Result(List<Step> steps, Goal goal, boolean reversed, double cost) {}

    private static final int BUDGET = 120_000;
    /** Vitesse la plus elevee du reseau (px/tick) : borne de l'heuristique. */
    private static final double TOP = 34 * 0.8;

    private record Node(Step step, double g, double f, @Nullable Node parent, boolean reversed) {}

    private Pathfinder() {
    }

    /**
     * @param starts    etapes de depart (tete, et queue retournee si le train peut repartir en arriere)
     * @param penalty   penalite (ticks) pour entrer dans une piece (occupation par un autre train)
     */
    @Nullable
    public static Result find(RailNetwork net, List<Step> starts, List<Boolean> reversedStarts, List<Goal> goals, ToDoubleFunction<Piece> penalty) {
        if (goals.isEmpty() || starts.isEmpty()) {
            return null;
        }
        Map<PortKey, List<Goal>> byPort = new HashMap<>();
        double gx = 0, gy = 0, gz = 0;
        for (Goal g : goals) {
            byPort.computeIfAbsent(g.port, k -> new ArrayList<>()).add(g);
        }
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<Step, Double> best = new HashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            Step s = starts.get(i);
            open.add(new Node(s, i == 0 ? 0 : 40, h(net, s, goals), null, reversedStarts.get(i)));
        }
        int budget = BUDGET;
        while (!open.isEmpty() && budget-- > 0) {
            Node n = open.poll();
            Double seen = best.get(n.step);
            if (seen != null && seen <= n.g) {
                continue;
            }
            best.put(n.step, n.g);
            PortKey exit = net.exitPort(n.step);
            if (exit == null) {
                continue;
            }
            var end = net.exit(n.step);
            Direction heading = end == null ? Direction.NORTH : end.face();
            List<Goal> hit = byPort.get(exit);
            if (hit != null) {
                for (Goal g : hit) {
                    if (g.heading == heading) {
                        return new Result(unwind(n), g, n.reversed, n.g);
                    }
                }
            }
            //Voie a sens unique : signal pour l'autre sens seulement
            if (net.hasSignal(exit) && net.signalFor(exit, heading) == null) {
                continue;
            }
            for (Step nx : net.next(n.step)) {
                Piece p = net.piece(nx.anchor());
                if (p == null) {
                    continue;
                }
                double extra = penalty.applyAsDouble(p);
                if (Double.isInfinite(extra)) {
                    //Piece interdite a ce train (voie non MKIV pour un maglev)
                    continue;
                }
                double cost = p.path(nx.path()).length / Math.max(0.4, p.limit(nx.path())) + extra;
                double g = n.g + cost;
                Double prev = best.get(nx);
                if (prev != null && prev <= g) {
                    continue;
                }
                open.add(new Node(nx, g, g + h(net, nx, goals), n, n.reversed));
            }
        }
        return null;
    }

    private static double h(RailNetwork net, Step s, List<Goal> goals) {
        PortKey p = net.exitPort(s);
        if (p == null) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        for (Goal g : goals) {
            double dx = g.port.x() - p.x(), dy = g.port.y() - p.y(), dz = g.port.z() - p.z();
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz) * 16 / TOP);
        }
        return best;
    }

    private static List<Step> unwind(Node n) {
        List<Step> out = new ArrayList<>();
        //Le premier noeud est l'etape de depart (deja sous la tete) : il n'est pas rendu
        while (n != null && n.parent != null) {
            out.add(n.step);
            n = n.parent;
        }
        Collections.reverse(out);
        return out;
    }
}
