package fr.pioneers.core.side;

import net.minecraft.core.Direction;

/**
 * Bloc a reglages de faces mais sans orientation dans son etat (buffers, quelques machines) : sa face « avant » est retenue a la pose, tournee
 * vers le joueur ({@link FrontEvents}). Sans cela, l'avant du panneau des faces etait toujours le nord.
 */
public interface Fronted {

    void setFront(Direction front);
}
