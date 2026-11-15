package fr.pioneers.api.heat;

import fr.pioneers.core.heat.HeatExchange;

/**
 * Capacite « chaleur » du socle Pioneer's : un corps a une temperature (K), une capacite thermique (J/K) et une conductance (J par tick et
 * par kelvin d'ecart, au plus, vers un voisin). Les echanges sont faits par {@link HeatExchange}.
 */
public interface IHeatHandler {

    /** Temperature (kelvins). */
    double getTemperature();

    /** Capacite thermique (joules par kelvin). */
    double getHeatCapacity();

    /** Conductance maximale vers un voisin (joules par tick et par kelvin). */
    double getConductance();

    /** Ajoute (ou retire, si negatif) de la chaleur, en joules. */
    void addHeat(double joules);
}
