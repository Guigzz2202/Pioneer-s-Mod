package fr.pioneers.space.mining;

import fr.pioneers.space.base.BaseLayout;
import fr.pioneers.space.base.BaseStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Garage du rover construit (1.0, lot 4) : dalle en carcasses, poteaux et poutres en cadres, bardage et toit en toles, une porte et un
 * dock par travee (1 a 4 : autant de rovers en convoi), mat radar sur le toit ; tier = la piece la plus faible. Plan : {@link BaseLayout}.
 * Le controleur est sur le pignon gauche ; les portes regardent le joueur qui a pose le plan.
 */
public final class MiningOutpostStructure {

    private MiningOutpostStructure() {
    }

    /** Hauteur du hangar (le radar est 4 blocs plus haut). */
    public static final int HEIGHT = 5;

    /**
     * Centre du sol du garage, direction des portes, ports, nombre de docks, tier et taille, demi-longueur du hangar (le long des
     * portes, en blocs, marge comprise).
     */
    public record Result(BlockPos center, Direction door, List<BlockPos> ports, int docks, int tier, int size, double half,
                         BaseStructure.Result base) {
    }

    @Nullable
    public static Result find(Level level, BlockPos controller, Direction facing) {
        BaseStructure.Result base = BaseStructure.find(level, controller, facing, BaseLayout.Kind.GARAGE);
        if (base == null) {
            return null;
        }
        return new Result(base.origin(), base.front(), base.ports(), base.functional().size(), base.tier(), base.size(), 2 * (base.size() + 1) + 0.5,
              base);
    }

    // ------------------------------------------------------------------
    // Trajet du rover : dock -> seuil -> devant la porte -> gisement (coordonnees relatives au centre du sol)
    // ------------------------------------------------------------------

    /**
     * Points du trajet (x, z relatifs au centre, au milieu des blocs) : dock, seuil, devant la porte, eventuellement un ou deux
     * coins pour contourner le hangar, puis le gisement (le dernier point s'arrete juste avant, pour que le foret l'atteigne).
     */
    public static double[][] path(Direction door, BlockPos center, BlockPos deposit, double half) {
        double ox = door.getStepX(), oz = door.getStepZ();
        //Cote lateral (vers la droite de la porte)
        double sx = -oz, sz = ox;
        double tx = deposit.getX() - center.getX(), tz = deposit.getZ() - center.getZ();
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{0, 0});
        points.add(new double[]{ox * 3, oz * 3});
        double[] out = {ox * 4.5, oz * 4.5};
        points.add(out);
        double[] last = out;
        if (crossesHangar(last, tx, tz, ox, oz, half)) {
            double side = Math.signum(tx * sx + tz * sz);
            if (side == 0) {
                side = 1;
            }
            double w = half + 1;
            double[] corner = {ox * 4.5 + sx * side * w, oz * 4.5 + sz * side * w};
            points.add(corner);
            last = corner;
            if (crossesHangar(last, tx, tz, ox, oz, half)) {
                double[] back = {-ox * 4.5 + sx * side * w, -oz * 4.5 + sz * side * w};
                points.add(back);
                last = back;
            }
        }
        double dx = tx - last[0], dz = tz - last[1];
        double len = Math.sqrt(dx * dx + dz * dz);
        double stop = Math.min(1.1, len);
        points.add(len < 1E-3 ? last.clone() : new double[]{tx - dx / len * stop, tz - dz / len * stop});
        return points.toArray(new double[0][]);
    }

    /** Le segment traverse-t-il l'emprise du hangar (avec une marge) ? Profondeur 3,5 vers les portes, {@code half} le long. */
    private static boolean crossesHangar(double[] from, double tx, double tz, double ox, double oz, double half) {
        for (int i = 1; i < 24; i++) {
            double t = i / 24.0;
            double x = from[0] + (tx - from[0]) * t, z = from[1] + (tz - from[1]) * t;
            double depth = x * ox + z * oz, along = x * -oz + z * ox;
            if (Math.abs(depth) < 4 && Math.abs(along) < half + 0.5) {
                return true;
            }
        }
        return false;
    }

    public static double length(double[][] path) {
        double total = 0;
        for (int i = 1; i < path.length; i++) {
            total += Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
        }
        return total;
    }

    /** Position (x, z) et direction (dx, dz) a une fraction du trajet. */
    public static double[] along(double[][] path, double fraction) {
        double total = length(path), target = Math.max(0, Math.min(1, fraction)) * total;
        for (int i = 1; i < path.length; i++) {
            double sx = path[i][0] - path[i - 1][0], sz = path[i][1] - path[i - 1][1];
            double seg = Math.hypot(sx, sz);
            if (seg < 1E-6) {
                continue;
            }
            if (target <= seg || i == path.length - 1) {
                double t = Math.min(1, target / seg);
                return new double[]{path[i - 1][0] + sx * t, path[i - 1][1] + sz * t, sx / seg, sz / seg};
            }
            target -= seg;
        }
        double[] last = path[path.length - 1];
        return new double[]{last[0], last[1], 0, 1};
    }
}
