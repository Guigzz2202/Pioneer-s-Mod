package fr.pioneers.tech.compute;

import net.minecraft.core.BlockPos;

/**
 * Etat d'un centre de donnees ou d'un ordinateur quantique vu par le client (0.17.2) : ce que dessinent les rendus animes (allees de baies
 * qui clignotent, air chaud, lustre du refrigerateur a dilution, qubits). Synchronise par le controleur (au plus 4 fois par seconde).
 * Obtenu par {@code ControllerTile#computeView()} des deux cotes.
 */
public interface ComputeView {

    /** 1 = centre de donnees, 2 = ordinateur quantique. */
    int kind();

    /** Charge de calcul (0..1) : part de la capacite effectivement demandee par le reseau. */
    float load();

    /** Temperature relative (0 = froid, 1 = limite de bridage ; quantique : 0 = millikelvins atteints, 1 = decoherence). */
    float heat();

    /** Baies (ou matrices de qubits) installees. */
    int units();

    /** Bornes de la structure (coins inclus). */
    BlockPos min();

    BlockPos max();
}
