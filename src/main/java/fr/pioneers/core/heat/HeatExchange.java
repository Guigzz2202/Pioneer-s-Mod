package fr.pioneers.core.heat;

import fr.pioneers.api.heat.IHeatHandler;

/**
 * Echanges de chaleur du socle. Le flux entre deux corps vaut l'ecart de temperature multiplie par la plus faible des deux conductances,
 * borne pour ne jamais depasser l'equilibre (pas d'oscillation, quel que soit le tier).
 */
public final class HeatExchange {

    /** Temperature ambiante (K). */
    public static final double AMBIENT = 300;

    private HeatExchange() {
    }

    /** Echange entre deux corps ; renvoie les joules passes de {@code a} vers {@code b}. */
    public static double exchange(IHeatHandler a, IHeatHandler b) {
        double dT = a.getTemperature() - b.getTemperature();
        if (Math.abs(dT) < 1e-6) {
            return 0;
        }
        double k = Math.min(a.getConductance(), b.getConductance());
        double ca = a.getHeatCapacity(), cb = b.getHeatCapacity();
        //Joules qui amenent les deux corps a l'equilibre
        double equilibrium = Math.abs(dT) * ca * cb / (ca + cb);
        double flow = Math.signum(dT) * Math.min(Math.abs(dT) * k, equilibrium * 0.5);
        a.addHeat(-flow);
        b.addHeat(flow);
        return flow;
    }

    /** Pertes vers l'air ambiant (coefficient en J par tick et par kelvin d'ecart). */
    public static void ambient(IHeatHandler body, double loss) {
        double dT = body.getTemperature() - AMBIENT;
        if (Math.abs(dT) < 1e-6 || loss <= 0) {
            return;
        }
        double flow = Math.signum(dT) * Math.min(Math.abs(dT) * loss, Math.abs(dT) * body.getHeatCapacity() * 0.5);
        body.addHeat(-flow);
    }
}
