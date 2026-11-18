package fr.pioneers.tech.bronze;

/**
 * Recoit l'air d'un soufflet mecanique tourne vers lui (ou d'une soufflerie a vapeur) : {@code strength} 0..1 par source, appele a chaque
 * tick tant que l'air arrive.
 */
public interface AirBlastReceiver {

    void blast(double strength);
}
