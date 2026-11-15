package fr.pioneers.tech.bronze.steam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Tout ce qui contient de la vapeur : chaudiere (par son dome), machines, organes. Deux noeuds colles echangent par le remplissage de leurs
 * reserves (1.0.2-beta, lot 3 : plus de pression, {@link Steam#exchange}) : les noeuds
 * « actifs » (machines) font eux-memes les echanges avec leurs voisins a chaque tick ; les « passifs » (chaudiere) se laissent puiser.
 * 0.20.40 : a distance, la vapeur passe par les cables a gaz ({@link SteamGas}) ; les tuyaux de vapeur sont retires.
 */
public interface SteamNode {

    /** Reserve de vapeur derriere cette face (ou null si cette face ne se raccorde pas). */
    SteamVolume steam(Direction side);

    /** Debit d'echange de la face avec une reserve collee (mB par tick). */
    default double conductance(Direction side) {
        return SteamRules.FACE_FLOW;
    }

    /** Fait ses echanges lui-meme (tuyau, vanne, machine). */
    default boolean active() {
        return true;
    }

    /** Le flux de {@code from} vers ce noeud par cette face est-il permis (clapet, vanne fermee...) ? */
    default boolean accepts(Direction side, boolean inward) {
        return true;
    }

    BlockPos nodePos();

    /** Source de vapeur (chaudiere) : les cables y puisent et ne la remplissent pas ({@link SteamGas}). */
    default boolean steamSource() {
        return false;
    }
}
