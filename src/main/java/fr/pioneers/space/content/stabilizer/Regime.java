package fr.pioneers.space.content.stabilizer;

import java.util.Locale;

/**
 * Regime du Stabilisateur spatial, fixe par le noyau pose au centre :
 * <ul>
 *     <li>{@link #SINGULARITY} (noyau de singularite) : un trou noir convertit l'antimatiere en Void Singularity Gas ;</li>
 *     <li>{@link #STAR} (Stellar Core) : une etoile captive brule de la lave et donne du plasma stellaire.</li>
 * </ul>
 */
public enum Regime {
    //entree par point de debit et par tick, entree par unite de produit, energie du champ (FE/t par point de debit)
    SINGULARITY(1, 500, 250_000L),
    STAR(10, 500, 150_000L);

    public final long inputPerRatePerTick;
    public final long inputPerOutput;
    public final long energyPerRatePerTick;

    Regime(long inputPerRatePerTick, long inputPerOutput, long energyPerRatePerTick) {
        this.inputPerRatePerTick = inputPerRatePerTick;
        this.inputPerOutput = inputPerOutput;
        this.energyPerRatePerTick = energyPerRatePerTick;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Entree liquide : lave (etoile) ; eau en attendant l'equilibrage (singularite, l'antimatiere est retiree). */
    public boolean fluidInput() {
        return true;
    }
}
