package fr.pioneers.tech.kinetics.rope;

import net.minecraft.core.Direction;

/**
 * 1.0.5-beta (lot 15, Guillaume : « poulies reliables sans etre dans le meme plan ») : regles PURES de pose d'une corde entre deux poulies
 * d'axes paralleles (axes paralleles toujours obligatoires). Les deux poulies peuvent etre DECALEES le long de leur axe : au plus
 * {@link #MAX_OFFSET} blocs, et pas plus de la moitie de leur ecart dans le plan perpendiculaire a l'axe (la corde reste oblique, elle ne
 * frotte pas sur les jantes). La portee ({@code KineticsConfig.ropeRange}) se mesure toujours d'un centre a l'autre ; la transmission (rapport
 * des rayons, sens) ne change pas. Les cordes posees avant (toutes dans un meme plan) restent valables.
 */
public final class RopeRules {

    /** Decalage maximal le long de l'axe (blocs). */
    public static final int MAX_OFFSET = 2;

    private RopeRules() {
    }

    /** Decalage le long de l'axe (blocs, signe) de la poulie b vue de a. */
    public static int along(Direction.Axis axis, int dx, int dy, int dz) {
        return switch (axis) {
            case X -> dx;
            case Y -> dy;
            case Z -> dz;
        };
    }

    /** Ecart (blocs) dans le plan perpendiculaire a l'axe. */
    public static double inPlane(Direction.Axis axis, int dx, int dy, int dz) {
        return switch (axis) {
            case X -> Math.hypot(dy, dz);
            case Y -> Math.hypot(dx, dz);
            case Z -> Math.hypot(dx, dy);
        };
    }

    /** Le decalage le long de l'axe est-il permis ? (0 toujours, si les poulies ne sont pas sur le meme axe). */
    public static boolean offsetAllowed(int along, double inPlane) {
        if (inPlane < 1e-9) {
            return false;
        }
        return Math.abs(along) <= MAX_OFFSET && 2.0 * Math.abs(along) <= inPlane + 1e-9;
    }
}
