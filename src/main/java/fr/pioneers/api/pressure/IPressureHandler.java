package fr.pioneers.api.pressure;

import fr.pioneers.core.pressure.PressureExchange;

/**
 * Capacite « pression » du socle Pioneer's : un volume (litres) contient de l'air comprime, compte en litres d'air a pression ambiante.
 * La pression (bar, au-dessus de l'ambiante) vaut l'air divise par le volume. Chaque corps supporte une pression maximale et a une
 * conductance : l'air passe d'un corps a un voisin en proportion de l'ecart de pression (litres par tick et par bar), jusqu'a l'equilibre.
 * Les echanges sont faits par {@link PressureExchange}.
 */
public interface IPressureHandler {

    /** Volume (litres). */
    double getVolume();

    /** Air contenu (litres a pression ambiante). */
    double getAir();

    /** Pression maximale supportee (bar) : au-dela, l'air n'entre plus. */
    double getMaxPressure();

    /** Conductance maximale vers un voisin (litres par tick et par bar d'ecart). */
    double getConductance();

    /** Pression (bar au-dessus de l'ambiante). */
    default double getPressure() {
        double volume = getVolume();
        return volume <= 0 ? 0 : getAir() / volume;
    }

    /** Air que le corps peut encore recevoir avant sa pression maximale (litres). */
    default double getRoom() {
        return Math.max(0, getMaxPressure() * getVolume() - getAir());
    }

    /** Fait entrer de l'air (borne par la pression maximale) ; renvoie ce qui est entre. */
    double insertAir(double liters, boolean simulate);

    /** Fait sortir de l'air (borne par ce qui est contenu) ; renvoie ce qui est sorti. */
    double extractAir(double liters, boolean simulate);
}
