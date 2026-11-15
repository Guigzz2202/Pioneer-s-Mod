package fr.pioneers.transport.logistic;

/**
 * 1.11 : acces a un gestionnaire d'energie sans boite (FE en {@code long}) ; Minecraft : {@code IEnergyStorage} ({@link Accesses#ENERGY}),
 * banc : faux generateurs et machines.
 *
 * @param <H> gestionnaire
 */
public interface EnergyAccess<H> {

    /** Energie que la machine peut donner (jusqu'a {@code max}). */
    long extract(H handler, long max, boolean simulate);

    /** Energie que la machine accepte (jusqu'a {@code max}). */
    long receive(H handler, long max, boolean simulate);

    /** Remplissage 0..1 (mode Au plus juste). */
    double fill(H handler);
}
