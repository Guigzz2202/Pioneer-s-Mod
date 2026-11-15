package fr.pioneers.tech.nuclear;

/**
 * Commande et lecture d'un reacteur (0.17.0) : ce que voient l'interface du controleur, l'automate programmable et les ecrans de
 * supervision. Implementee par le coeur du reacteur a fission ({@code FissionCore}) ; obtenue par {@code ControllerTile#reactor()}.
 */
public interface ReactorControl {

    /** Puissance thermique instantanee (J/t). */
    double thermalPower();

    /** Puissance relative a la puissance maximale du coeur charge (0..1). */
    double powerFraction();

    /** Temperature de la cuve (K). */
    double temperature();

    /** Temperature d'arret d'urgence automatique (K). */
    double scramTemperature();

    /** Insertion des barres de commande demandee (0 = sorties, 100 = inserees a fond). */
    int rods();

    void setRods(int percent);

    /** Arret d'urgence en cours (barres inserees a fond, verrouillees jusqu'au rearmement). */
    boolean scrammed();

    void scram();

    /** Rearme apres un arret d'urgence (refuse tant que la temperature depasse la limite). */
    boolean resetScram();

    /** Arret d'urgence automatique a la temperature limite. */
    boolean autoScram();

    void setAutoScram(boolean on);

    /** Assemblages charges / places disponibles (canaux de combustible). */
    int fuelLoaded();

    int fuelCapacity();

    /** Taux de combustion moyen des assemblages charges (0..1). */
    double burnup();
}
