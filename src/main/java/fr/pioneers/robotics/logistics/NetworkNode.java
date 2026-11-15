package fr.pioneers.robotics.logistics;

import fr.pioneers.robotics.RobotUnitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Noeud du reseau robotique (hub ou relais) : il couvre un carre autour de lui, et recharge les unites (places reservees).
 */
public interface NetworkNode {

    BlockPos nodeCenter();

    /** Couverture : demi-cote du carre couvert (blocs). */
    int coverage();

    boolean nodeValid();

    /** Couvre-t-il cette position ? */
    default boolean covers(BlockPos pos) {
        BlockPos c = nodeCenter();
        int r = coverage();
        return Math.abs(pos.getX() - c.getX()) <= r && Math.abs(pos.getZ() - c.getZ()) <= r && Math.abs(pos.getY() - c.getY()) <= 128;
    }

    // ------------------------------------------------------------------
    // Recharge
    // ------------------------------------------------------------------

    /** Place de recharge de l'unite (reservee au premier appel), ou null si tout est pris. */
    @Nullable
    Vec3 chargeSpot(RobotUnitEntity unit);

    /** Donne de l'energie a l'unite (sur place) ; renvoie les FE donnes. */
    long charge(RobotUnitEntity unit, long wanted);

    /** L'unite libere sa place. */
    void leave(RobotUnitEntity unit);

    /** Places occupees / places totales (choix du chargeur le moins encombre). */
    float crowd();

    /** Place ou attendre sans rien faire (autour du noeud). */
    Vec3 idleSpot(RobotUnitEntity unit);
}
