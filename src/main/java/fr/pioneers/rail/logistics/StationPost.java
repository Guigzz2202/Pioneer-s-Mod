package fr.pioneers.rail.logistics;

import java.util.Map;
import java.util.Set;

/**
 * 0.20.5 : poste de quai vu par la logistique du rail : son contenu compte dans le stock de la gare, et il dit quelles familles de ressources
 * il sait tenir (le pont avec les robots ne lui demande que celles-la).
 */
public interface StationPost {

    /** Ajoute le contenu de son tampon. */
    void addStock(Map<Cargo, Long> out);

    /** Familles de ressources que le poste charge ou decharge (vide : poste de ravitaillement, hors logistique). */
    Set<Cargo.Kind> holds();
}
