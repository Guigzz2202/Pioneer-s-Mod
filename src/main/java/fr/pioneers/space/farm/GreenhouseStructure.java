package fr.pioneers.space.farm;

import fr.pioneers.space.base.BaseLayout;
import fr.pioneers.space.base.BaseStructure;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Serre spatiale construite (1.0, lot 4) : socle rond en carcasses, bacs de substrat hydroponique dans le sol (rangees et allees du robot),
 * station de recharge au centre, dome de verre a 4 nervures en cadres ; 4 tailles (diametre 9 a 15), tier = la piece la plus faible.
 * Plan : {@link BaseLayout}. Cases : grille de {@link #GRID} x {@link #GRID} centree sur le centre du sol (decalages monde).
 */
public final class GreenhouseStructure {

    private GreenhouseStructure() {
    }

    public static final int HALF = 7, GRID = 2 * HALF + 1;

    /** Resultat : centre du sol, case de la station, bacs (masque des cases), ports, taille et tier. */
    public record Result(BlockPos center, int padCell, boolean[] beds, List<BlockPos> ports, int size, int tier, BaseStructure.Result base) {
    }

    @Nullable
    public static Result find(Level level, BlockPos controller, Direction facing) {
        BaseStructure.Result base = BaseStructure.find(level, controller, facing, BaseLayout.Kind.GREENHOUSE);
        if (base == null || base.pad() == null) {
            return null;
        }
        BlockPos center = base.origin();
        boolean[] beds = new boolean[GRID * GRID];
        for (BlockPos bed : base.functional()) {
            int dx = bed.getX() - center.getX(), dz = bed.getZ() - center.getZ();
            if (Math.abs(dx) <= HALF && Math.abs(dz) <= HALF) {
                beds[cell(dx, dz)] = true;
            }
        }
        return new Result(center, cell(base.pad().getX() - center.getX(), base.pad().getZ() - center.getZ()), beds, base.ports(), base.size(),
              base.tier(), base);
    }

    /** Case du sol (dx, dz dans -7..7) -> index. */
    public static int cell(int dx, int dz) {
        return (dx + HALF) * GRID + (dz + HALF);
    }

    public static int cellX(int cell) {
        return cell / GRID - HALF;
    }

    public static int cellZ(int cell) {
        return cell % GRID - HALF;
    }
}
