package fr.pioneers.space.content.spacesim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Forme du Simulateur spatial, en coordonnees relatives au centre du globe :
 * <ul>
 *     <li>un globe parfaitement rond de {@link #SIZE} blocs de diametre : la coque est la couche exterieure (tout bloc du globe qui touche
 *     l'exterieur par une face) ; l'interieur est vide ;</li>
 *     <li>un projecteur de faille au centre de chacune des six faces (sur la coque) ;</li>
 *     <li>le controleur, dans la coque, en bas au centre d'une des quatre faces laterales ({@link #controllerSpot}) ;</li>
 *     <li>des aretes de boitier ({@link #isRib}) : l'equateur et deux meridiens croises, ni verre ni port ;</li>
 *     <li>l'anneau de bobines supremes dans le plan de l'equateur (rayon {@link #RING_INNER} a {@link #RING_OUTER}), relie au globe par un
 *     rayon de bobines au centre de chacune des quatre faces laterales.</li>
 * </ul>
 * Partagee par la verification ({@link SpaceSimStructure}), le projecteur de structure et le rendu.
 */
public final class SpaceSimShape {

    public static final int SIZE = 25, RADIUS = SIZE / 2;
    public static final double RING_INNER = 15.5, RING_OUTER = 17.5;
    /** Hauteur (sous le centre) et distance au centre du controleur. */
    public static final int CONTROLLER_Y = -8, CONTROLLER_OUT = 9, RIB_Y = 7;
    private static final double R2 = (RADIUS + 0.5) * (RADIUS + 0.5);

    public static final List<BlockPos> SHELL, INTERIOR, PROJECTORS, COILS;

    static {
        List<BlockPos> shell = new ArrayList<>(), interior = new ArrayList<>(), coils = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    if (!inside(x, y, z)) {
                        continue;
                    }
                    boolean border = !inside(x + 1, y, z) || !inside(x - 1, y, z) || !inside(x, y + 1, z) || !inside(x, y - 1, z) || !inside(x, y, z + 1)
                                     || !inside(x, y, z - 1);
                    (border ? shell : interior).add(new BlockPos(x, y, z));
                }
            }
        }
        int reach = (int) Math.ceil(RING_OUTER);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (isCoil(x, z)) {
                    coils.add(new BlockPos(x, 0, z));
                }
            }
        }
        SHELL = Collections.unmodifiableList(shell);
        INTERIOR = Collections.unmodifiableList(interior);
        COILS = Collections.unmodifiableList(coils);
        PROJECTORS = List.of(new BlockPos(RADIUS, 0, 0), new BlockPos(-RADIUS, 0, 0), new BlockPos(0, RADIUS, 0), new BlockPos(0, -RADIUS, 0),
              new BlockPos(0, 0, RADIUS), new BlockPos(0, 0, -RADIUS));
    }

    private SpaceSimShape() {
    }

    public static boolean inside(int x, int y, int z) {
        return x * x + y * y + z * z <= R2;
    }

    /** Case de l'anneau ou d'un des quatre rayons qui le relient au globe (plan de l'equateur). */
    public static boolean isCoil(int x, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (r >= RING_INNER && r < RING_OUTER) {
            return true;
        }
        //Rayons : sur les axes, entre la coque et l'anneau
        return (x == 0 || z == 0) && r > RADIUS + 0.5 && r < RING_INNER;
    }

    /** Place du controleur sur la face laterale {@code side} : en bas au centre (case de coque). */
    public static BlockPos controllerSpot(Direction side) {
        return new BlockPos(side.getStepX() * CONTROLLER_OUT, CONTROLLER_Y, side.getStepZ() * CONTROLLER_OUT);
    }

    /** Arete de boitier : l'equateur et les deux meridiens des faces (trois grands cercles croises, comme une sphere armillaire). */
    public static boolean isRib(BlockPos relative) {
        return relative.getX() == 0 || relative.getY() == 0 || relative.getZ() == 0;
    }

    public static boolean isProjector(BlockPos relative) {
        return PROJECTORS.contains(relative);
    }
}
