package fr.pioneers.industry.multi;

import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.transport.CableTier;
import fr.pioneers.transport.logistic.CableLevel;

/**
 * Reserve d'air comprime de procede d'un multibloc industriel (200 L ; pression maximale = celle des cables du tier de la structure), remplie
 * par les ports de pression ({@link #inlet} : entree seulement, l'air ne ressort pas par les ports). Lot 5 : sortie de {@link ControllerTile}
 * (cle de sauvegarde {@code air} inchangee, ecrite par le controleur).
 */
final class ProcessAir implements IPressureHandler {

    private final ControllerTile c;
    double stored;

    /** Vue des ports de pression : entree seulement (l'air ne ressort pas par les ports). */
    final IPressureHandler inlet = new IPressureHandler() {
        @Override
        public double getVolume() {
            return ProcessAir.this.getVolume();
        }

        @Override
        public double getAir() {
            return ProcessAir.this.getAir();
        }

        @Override
        public double getMaxPressure() {
            return ProcessAir.this.getMaxPressure();
        }

        @Override
        public double getConductance() {
            return ProcessAir.this.getConductance();
        }

        @Override
        public double insertAir(double liters, boolean simulate) {
            return ProcessAir.this.insertAir(liters, simulate);
        }

        @Override
        public double extractAir(double liters, boolean simulate) {
            return 0;
        }
    };

    ProcessAir(ControllerTile c) {
        this.c = c;
    }

    @Override
    public double getVolume() {
        return 200;
    }

    @Override
    public double getAir() {
        return stored;
    }

    @Override
    public double getMaxPressure() {
        return CableLevel.maxPressure(CableTier.values()[Math.max(0, Math.min(3, c.tier()))]);
    }

    @Override
    public double getConductance() {
        return 50.0 * (1 << c.tier());
    }

    @Override
    public double insertAir(double liters, boolean simulate) {
        double moved = Math.max(0, Math.min(liters, getRoom()));
        if (!simulate && moved > 0) {
            stored += moved;
            c.setChanged();
        }
        return moved;
    }

    @Override
    public double extractAir(double liters, boolean simulate) {
        double moved = Math.max(0, Math.min(liters, stored));
        if (!simulate && moved > 0) {
            stored -= moved;
            c.setChanged();
        }
        return moved;
    }
}
