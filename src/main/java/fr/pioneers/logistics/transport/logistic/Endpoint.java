package fr.pioneers.logistics.transport.logistic;

import org.jetbrains.annotations.Nullable;

/**
 * 1.11 : une machine raccordee au reseau, vue du moteur. Minecraft : {@link PortCache} (capacites gardees en cache, invalidees par les
 * {@code LazyOptional}, relues au plus toutes les 200 ticks) ; banc : fausses machines.
 */
public interface Endpoint {

    /** Cle du raccord : position de la machine ({@code BlockPos.asLong}), ramenee au controleur ou au bloc principal. */
    long key();

    /**
     * Gestionnaire de ce type sur une face d'acces : {@link PointConfig#TOUCHED} = la face touchee par la fiche, sinon une face relative a la
     * facade ({@code RelativeSide} 0..5). Jamais l'acces interne (face nulle). Null : la machine n'offre rien sur cette face.
     */
    @Nullable
    Object handler(ChannelKind kind, int face);

    /** Groupe du diagramme (controleur d'un multibloc, bloc principal d'une grande machine) ; la cle sinon. */
    default long group() {
        return key();
    }

    /** Types offerts sur au moins une face (bits de {@link ChannelKind}) : canaux automatiques et diagramme. */
    int kinds();

    /**
     * Ce que chaque face offre pour un type (patron du cube, spec §3.1) : 2 bits par face relative (0..5) : 1 = accepte l'insertion, 2 =
     * offre l'extraction, 3 = les deux, 0 = fermee. Calcule a la demande (diagramme), jamais par tick.
     */
    default int faces(ChannelKind kind) {
        return 0;
    }

    /** 1.0.5-beta (lot 45, retour 66) : la machine sort de pause (chunk recharge) : capacites a relire au prochain appel. */
    default void wake() {
    }
}
