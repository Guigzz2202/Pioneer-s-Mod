package fr.pioneers.logistics.naval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 0.20.23 : plan du port, SOURCE UNIQUE de sa forme (validation {@link HarborStructure}, plan projete {@link HarborBlueprint}) : un U de
 * dalles de quai a la taille fixe du navire du tier de la capitainerie (comme les autres multiblocs, mais le tier est celui du controleur,
 * pas des carcasses).
 * <p>
 * Repere local : {@code z} = rang, de la capitainerie (0) vers le large ; {@code x} = lateral, positif a droite en regardant le large.
 * <ul>
 *     <li>capitainerie en (0, 0), au milieu du fond ;</li>
 *     <li>fond : x de -(k + 1) a k + 1, z = 0 ;</li>
 *     <li>bras : x = +/-(k + 1), z de 1 a {@link #armLength} ;</li>
 *     <li>bassin : x de -k a k, z de 1 a {@link #armLength}, en eau navigable sur {@link #WATER_DEPTH} blocs de fond.</li>
 * </ul>
 * Largeur du bassin 2k + 1 = la plus petite largeur impaire qui laisse un bloc de jeu au navire ; bras = longueur du navire + 1.
 */
public final class HarborLayout {

    /** Profondeur d'eau exigee dans tout le bassin. */
    public static final int WATER_DEPTH = 2;
    /** 0.20.25 : hauteur des murs du quai (le dessus a la hauteur de la capitainerie, puis deux rangs dessous, sous elle aussi). */
    public static final int HEIGHT = 3;

    public enum Role {
        CONTROLLER, QUAY, WATER
    }

    /** Case du plan (repere local). */
    public record Cell(int x, int z, Role role) {

        /** Une piece du fond (sinon d'un bras). */
        public boolean back() {
            return z == 0;
        }
    }

    private static final List<List<Cell>> CELLS = new ArrayList<>();

    static {
        for (int t = 0; t < 4; t++) {
            int k = halfWidth(t), d = armLength(t);
            List<Cell> cells = new ArrayList<>();
            cells.add(new Cell(0, 0, Role.CONTROLLER));
            for (int x = -(k + 1); x <= k + 1; x++) {
                if (x != 0) {
                    cells.add(new Cell(x, 0, Role.QUAY));
                }
            }
            for (int z = 1; z <= d; z++) {
                cells.add(new Cell(-(k + 1), z, Role.QUAY));
                cells.add(new Cell(k + 1, z, Role.QUAY));
            }
            for (int z = 1; z <= d; z++) {
                for (int x = -k; x <= k; x++) {
                    cells.add(new Cell(x, z, Role.WATER));
                }
            }
            CELLS.add(Collections.unmodifiableList(cells));
        }
    }

    private HarborLayout() {
    }

    /** Demi-largeur k du bassin (largeur 2k + 1 >= bau + 1). */
    public static int halfWidth(int tier) {
        return (ShipKind.beam(clamp(tier)) + 1) / 2;
    }

    /** Longueur des bras = profondeur du bassin (longueur du navire + 1). */
    public static int armLength(int tier) {
        return ShipKind.length(clamp(tier)) + 1;
    }

    public static List<Cell> cells(int tier) {
        return CELLS.get(clamp(tier));
    }

    /** Position monde d'une case : {@code basin} = direction du large (dos de la capitainerie). */
    public static BlockPos at(BlockPos controller, Direction basin, int x, int z) {
        Direction right = basin.getClockWise();
        return controller.offset(basin.getStepX() * z + right.getStepX() * x, 0, basin.getStepZ() * z + right.getStepZ() * x);
    }

    /** Direction vers l'eau d'une piece de quai : le fond regarde le large, chaque bras regarde l'axe du bassin. */
    public static Direction facing(Cell cell, Direction basin) {
        if (cell.back()) {
            return basin;
        }
        Direction right = basin.getClockWise();
        return cell.x() > 0 ? right.getOpposite() : right;
    }

    /** Portee d'un equipement : distance a l'axe du navire (bras) ou a sa poupe (fond). */
    public static float reach(Cell cell, int tier) {
        return cell.back() ? 1.5F : halfWidth(tier) + 1;
    }

    private static int clamp(int tier) {
        return Math.max(0, Math.min(3, tier));
    }
}
