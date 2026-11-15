package fr.pioneers.naval;

import fr.pioneers.rail.logistics.Cargo;
import fr.pioneers.rail.train.CarType;
import java.util.Locale;

/**
 * 0.20.11 : sortes de navires. Un navire porte UNE ressource ; sa cale est faite de « cales » qui ont exactement la reserve du wagon du meme
 * tier (le code de transfert, les conditions d'horaire et l'equilibrage du rail servent tels quels). L'ordre est celui des sauvegardes.
 */
public enum ShipKind {
    FERRY("ferry", CarType.PASSENGER, null),
    CARGO("cargo_ship", CarType.BOXCAR, Cargo.Kind.ITEM),
    TANKER("tanker", CarType.TANK, Cargo.Kind.FLUID),
    GAS_CARRIER("gas_carrier", CarType.GAS_TANK, Cargo.Kind.GAS),
    POWER_BARGE("power_barge", CarType.BATTERY, Cargo.Kind.ENERGY),
    AIR_CARRIER("air_carrier", CarType.AIR, Cargo.Kind.AIR),
    DATA_SHIP("data_ship", CarType.SERVER, Cargo.Kind.DATA);

    public final String base;
    /** Reserve d'une cale = celle de ce wagon, au meme tier. */
    public final CarType hold;
    /** Famille de ressource transportee (null : voyageurs). */
    public final Cargo.Kind cargo;

    ShipKind(String base, CarType hold, Cargo.Kind cargo) {
        this.base = base;
        this.hold = hold;
        this.cargo = cargo;
    }

    public String id(int tier) {
        return CarType.tierId(tier) + "_" + base;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShipKind byId(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
    }

    // ------------------------------------------------------------------ caracteristiques par tier (0 = MKI ... 3 = MKIV)

    private static int pick(int tier, int... v) {
        return v[Math.max(0, Math.min(3, tier))];
    }

    /** Longueur (blocs). 0.20.13 : navires plus elances (environ 4 fois plus longs que larges). */
    public static int length(int tier) {
        return pick(tier, 8, 10, 12, 16);
    }

    /** Largeur (blocs). */
    public static int beam(int tier) {
        return pick(tier, 2, 3, 3, 4);
    }

    /** Franc-bord : hauteur du pont principal au-dessus de la ligne de flottaison (px). */
    public static float freeboard(int tier) {
        return pick(tier, 8, 10, 12, 14);
    }

    /**
     * Bouches de fumee (cheminees) dans le repere du navire, en blocs : {x, z, y} par cheminee ; vide sans cheminee (MKIII electrique,
     * MKIV : event du reacteur sans fumee). Doit suivre les cheminees dessinees par ShipModels.
     */
    public static double[][] funnels(int tier) {
        return switch (tier) {
            case 0 -> new double[][]{{-0.25, -0.4, 2.55}, {0.25, -0.4, 2.55}};
            case 1 -> new double[][]{{0, -4.42, 2.2}};
            default -> new double[0][];
        };
    }

    /** Nombre de cales. */
    public static int holds(int tier) {
        return pick(tier, 2, 4, 6, 8);
    }

    /** Vitesse de croisiere (blocs par seconde). */
    public static int knots(int tier) {
        return pick(tier, 3, 5, 7, 9);
    }

    /** Vitesse de croisiere (blocs par tick). */
    public static double speed(int tier) {
        return knots(tier) / 20.0;
    }

    /** Consommation en route (FE/tick) ; a quai : 1/16. */
    public static int fuelUse(int tier) {
        return 2_048 << Math.max(0, Math.min(3, tier));
    }

    /** Tirant d'eau (px sous la ligne de flottaison). */
    public static float draft(int tier) {
        return pick(tier, 6, 8, 9, 11);
    }

    /** Places des voyageurs (ferry). */
    public int seats(int tier) {
        return this == FERRY ? 2 * holds(tier) : 0;
    }

    /** Peut naviguer sur la lave (coque en tungstene). */
    public static boolean heatproof(int tier) {
        return tier >= 3;
    }
}
