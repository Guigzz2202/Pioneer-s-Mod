package fr.pioneers.tech.kinetics.rope;

import net.minecraft.core.Direction;

/**
 * 1.0.3-beta (lot 11, Guillaume : « corriger le sens de rotation de la corde sur les poulies, elle semble tourner a contresens des
 * poulies ») : regles du defilement d'une corde ou courroie entre deux poulies, sans rien du client (dessin : {@code J:tech/kinetics/client/
 * MachineVisuals.rope} ; verifie par le GameTest du lot 11 avec les memes rotations que le dessin des poulies).
 * <p>
 * Une poulie tourne autour de +axe (regle de la main droite : le dessin aligne l'axe y local sur l'axe puis tourne en y). Dans le plan
 * (u, v) de la corde (axe X : u = z, v = y ; axe Y : u = x, v = z ; axe Z : u = x, v = y), la vitesse de la jante en (u, v) est (v, -u)
 * pour les axes X et Y, (-v, u) pour l'axe Z. Au point de tangence du brin {@code side} (normale a l'angle base + side (pi/2 - phi)), le
 * brin va donc de A vers B quand {@code side x signe de l'axe} > 0 ; avant le lot 11, les deux brins defilaient a l'envers sur les axes X
 * et Y.
 */
public final class RopeMotion {

    private RopeMotion() {
    }

    /** Signe de l'axe dans le plan de la corde : +1 pour X et Y, -1 pour Z. */
    public static int axisSign(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? -1 : 1;
    }

    /** Sens du brin {@code side} (-1 / +1) quand l'angle de la poulie A croit : +1 = de A vers B. */
    public static int strandSign(Direction.Axis axis, int side) {
        return side * axisSign(axis);
    }

    /** Defilement (px) d'une corde dans une gorge de rayon {@code ropeRadius} px pour {@code degrees} de rotation : la longueur d'arc. */
    public static double travel(double degrees, double ropeRadius) {
        return degrees / 360.0 * 2 * Math.PI * ropeRadius;
    }
}
