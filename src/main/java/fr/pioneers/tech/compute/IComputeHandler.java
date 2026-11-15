package fr.pioneers.tech.compute;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Noeud du reseau de calcul (0.17.2) : un fournisseur (centre de donnees, ordinateur quantique) offre une capacite, un consommateur
 * (laboratoire d'IA, table de recherche, ordinateur) formule une demande. Deux grandeurs independantes : GOPS (calcul classique) et QOPS
 * (calcul quantique). Le reseau ({@link ComputeNetwork}) reunit les noeuds relies par les memes cables de donnees, ou colles a la structure
 * d'un fournisseur, et repartit l'offre au prorata des demandes chaque seconde.
 * <p>
 * Un noeud s'inscrit a chaque tick ou il est actif ({@link ComputeNetwork#register}) ; il disparait du reseau s'il ne s'inscrit plus.
 */
public interface IComputeHandler {

    /** Capacite offerte (GOPS). */
    default double supplyGops() {
        return 0;
    }

    /** Capacite offerte (QOPS). */
    default double supplyQops() {
        return 0;
    }

    /** Demande (GOPS). */
    default double demandGops() {
        return 0;
    }

    /** Demande (QOPS). */
    default double demandQops() {
        return 0;
    }

    /** Consommateur : calcul accorde par la derniere repartition (valable jusqu'a la suivante). */
    default void grant(double gops, double qops) {
    }

    /** Fournisseur : calcul effectivement servi par la derniere repartition (charge). */
    default void serve(double gops, double qops) {
    }

    /** Cables de donnees touches par le noeud (positions des cables). */
    Collection<BlockPos> cables();

    /** Fournisseur a structure : coins de sa structure (un consommateur colle a cette boite y est relie sans cable) ; null sinon. */
    @Nullable
    default BlockPos boxMin() {
        return null;
    }

    @Nullable
    default BlockPos boxMax() {
        return null;
    }
}
