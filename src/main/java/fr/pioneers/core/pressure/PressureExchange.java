package fr.pioneers.core.pressure;

import fr.pioneers.api.pressure.IPressureHandler;

/**
 * Echanges d'air du socle. Le flux entre deux volumes vaut l'ecart de pression multiplie par la plus faible des deux conductances, borne
 * pour ne jamais depasser l'equilibre (pas d'oscillation) et par la place du volume qui recoit (pression maximale). Un sens peut etre
 * interdit (clapet : fiche en entree ou en sortie d'un tuyau).
 */
public final class PressureExchange {

    private PressureExchange() {
    }

    /** Echange dans les deux sens ; renvoie les litres passes de {@code a} vers {@code b}. */
    public static double exchange(IPressureHandler a, IPressureHandler b) {
        return exchange(a, b, true, true);
    }

    /**
     * Echange avec clapets ; renvoie les litres passes de {@code a} vers {@code b} (negatif : de b vers a).
     *
     * @param toB l'air peut-il aller de a vers b ?
     * @param toA l'air peut-il aller de b vers a ?
     */
    public static double exchange(IPressureHandler a, IPressureHandler b, boolean toB, boolean toA) {
        double dP = a.getPressure() - b.getPressure();
        if (Math.abs(dP) < 1e-6 || (dP > 0 && !toB) || (dP < 0 && !toA)) {
            return 0;
        }
        IPressureHandler from = dP > 0 ? a : b, to = dP > 0 ? b : a;
        double k = Math.min(a.getConductance(), b.getConductance());
        double va = from.getVolume(), vb = to.getVolume();
        //Litres qui amenent les deux volumes a la meme pression
        double equilibrium = Math.abs(dP) * va * vb / (va + vb);
        double flow = Math.min(Math.abs(dP) * k, equilibrium * 0.5);
        flow = Math.min(flow, to.insertAir(flow, true));
        flow = from.extractAir(flow, false);
        to.insertAir(flow, false);
        return dP > 0 ? flow : -flow;
    }

    /** Fuites vers l'air libre (litres par tick et par bar). */
    public static void leak(IPressureHandler body, double loss) {
        double p = body.getPressure();
        if (p <= 1e-6 || loss <= 0) {
            return;
        }
        body.extractAir(Math.min(p * loss, body.getAir() * 0.5), false);
    }
}
