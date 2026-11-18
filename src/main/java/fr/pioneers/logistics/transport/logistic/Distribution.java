package fr.pioneers.logistics.transport.logistic;

/**
 * 1.11 (spec §3.4) : repartition d'un canal entre ses sorties. Les sources sont toujours videes par priorite decroissante puis tour a tour.
 * La chaleur et la pression n'ont pas de repartition (echanges physiques).
 */
public enum Distribution {
    /** Sorties servies a la suite, point de depart qui tourne a chaque operation (defaut). */
    ROUND_ROBIN,
    /** Sorties triees par priorite decroissante, la plus haute remplie avant la suivante ; egalites : tour a tour. */
    PRIORITY,
    /** Quantite partagee selon les poids ; reste en tour a tour. */
    PROPORTIONAL,
    /** La sortie la moins remplie d'abord (niveaux relus au plus toutes les 20 ticks). */
    BALANCED;

    private static final Distribution[] VALUES = values();

    public static Distribution byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
