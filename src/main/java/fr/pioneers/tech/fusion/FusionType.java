package fr.pioneers.tech.fusion;

import org.jetbrains.annotations.Nullable;

/**
 * Types de reacteurs a fusion (0.17.2), choisis par la cle de la machine du controleur.
 * <ul>
 *     <li>TOKAMAK (MKI, vue mark 4) : deuterium + tritium, pic de reactivite vers 150 millions de K, 4 000 000 J/t thermiques au plus,
 *     solenoide central, couvertures tritigenes ;</li>
 *     <li>STELLARATOR (MKII, vue mark 5) : deuterium + helium 3, pic vers 600 millions de K, 10 000 000 FE/t au plus dont 70 % (jusqu'a 90 %
 *     avec les convertisseurs directs) en electricite.</li>
 * </ul>
 * La puissance maximale depend de la taille : {@link #perCell} J/t par case de plasma (1.0 : case vide de la chambre fine), plafonnee a
 * {@link #maxPower}.
 */
public enum FusionType {

    TOKAMAK("tokamak", 4, 150, 4_000_000, 20_000, 0.5, "fusion_ignition_mk1"),
    STELLARATOR("stellarator", 5, 600, 10_000_000, 50_000, 0.1, "fusion_ignition_mk2");

    public final String machine;
    /** Numero de la vue du reacteur ({@code ReactorView#mark()}). */
    public final int mark;
    /** Temperature du pic de reactivite (millions de K). */
    public final double peak;
    /** Puissance de fusion maximale (J/t) et par case de plasma. */
    public final double maxPower, perCell;
    /** Combustible brule par MJ de fusion (mB de chaque gaz). */
    public final double fuelPerMj;
    /** Evenement de progres de la premiere ignition. */
    public final String ignitionEvent;

    FusionType(String machine, int mark, double peak, double maxPower, double perCell, double fuelPerMj, String ignitionEvent) {
        this.machine = machine;
        this.mark = mark;
        this.peak = peak;
        this.maxPower = maxPower;
        this.perCell = perCell;
        this.fuelPerMj = fuelPerMj;
        this.ignitionEvent = ignitionEvent;
    }

    /** Temperature d'ignition (millions de K) : au-dela, le chauffage par les particules alpha l'emporte sur les pertes. */
    public double ignition() {
        return peak * 0.55;
    }

    /** Le tokamak brule du tritium (et le fabrique dans ses couvertures) ; le stellarator de l'helium 3. */
    public boolean tritium() {
        return this == TOKAMAK;
    }

    @Nullable
    public static FusionType of(String machineKey) {
        for (FusionType t : values()) {
            if (t.machine.equals(machineKey)) {
                return t;
            }
        }
        return null;
    }

    /** Couvertures tritigenes necessaires a un tokamak pour le rapport de surgeneration plein (1,1). */
    public static int blanketsNeeded(int interior) {
        return Math.max(4, Math.round(interior / 200F));
    }

    /** Modules conseilles par les plans projetes : couvertures (tokamak) ou convertisseurs directs (stellarator : 4 = 90 %). */
    public static int suggestedModules(String machineKey, int interior) {
        FusionType t = of(machineKey);
        if (t == null) {
            return 0;
        }
        return t == TOKAMAK ? blanketsNeeded(interior) : FusionCore.CONVERTERS_MAX;
    }
}
