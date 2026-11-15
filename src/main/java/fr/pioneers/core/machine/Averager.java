package fr.pioneers.core.machine;

/** Moyenne glissante (sur environ une seconde) d'un debit mesure a chaque tick, pour l'affichage. */
public final class Averager {

    private double value;

    public void add(double sample) {
        value += (sample - value) * 0.1;
    }

    public double get() {
        return Math.abs(value) < 1e-3 ? 0 : value;
    }
}
