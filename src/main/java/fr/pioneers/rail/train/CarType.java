package fr.pioneers.rail.train;

import java.util.Locale;

/**
 * Sortes de vehicules (0.20.0, toutes les tractions et tous les wagons en 0.20.2). Chacun fait un bloc de long (16 px, tampon contre
 * tampon). Les tiers (0 = MKI ... 3 = MKIV) augmentent la puissance (x1,5), la charge tiree (x1,5) et les contenances (x2). L'ordre est
 * celui des sauvegardes : on ajoute a la fin.
 */
public enum CarType {
    /** Wagonnet de mine pousse a la main (age mecanique). */
    MINE_WAGON(false, 1, false, Store.ITEMS),
    /** Draisine a bras : le conducteur pompe (age mecanique). */
    DRAISINE(true, 1, false, Store.NONE),
    /** Locomotive a vapeur (age du bronze) : combustible et eau, pris dans ses propres reserves puis dans les tenders. */
    STEAM(true, 4, true, Store.ITEMS),
    /** Tender : combustible et eau de la vapeur. */
    TENDER(false, 4, true, Store.ITEMS),
    /** Wagon couvert : objets. */
    BOXCAR(false, 4, true, Store.ITEMS),
    /** Wagon-tremie : vrac d'une seule sorte. */
    HOPPER(false, 4, true, Store.ITEMS),
    //0.20.2 : tractions
    /** Vapeur sans foyer (industrie) : reservoir de vapeur rempli en gare, aucun combustible. */
    FIRELESS(true, 4, true, Store.GAS),
    /** Diesel (chimie) : carburants liquides. */
    DIESEL(true, 4, true, Store.FLUID),
    /** Locomotive a gaz (chimie) : gaz combustibles, hydrogene x1,5. */
    GAS_LOCO(true, 4, true, Store.GAS),
    /** Electrique (atome) : catenaire, troisieme rail ou voie MKIV ; batterie de secours, freinage qui rend l'energie. */
    ELECTRIC(true, 4, true, Store.ENERGY),
    /** Nucleaire (atome) : assemblages de combustible, assemblages uses rendus. */
    NUCLEAR(true, 4, true, Store.ITEMS),
    /** Maglev (espace) : seulement sur la voie MKIV, qui porte l'energie. */
    MAGLEV(true, 4, true, Store.ENERGY),
    //0.20.2 : wagons
    /** Wagon-citerne : un fluide. */
    TANK(false, 4, true, Store.FLUID),
    /** Wagon-citerne a gaz : un gaz sous pression. */
    GAS_TANK(false, 4, true, Store.GAS),
    /** Wagon-batterie : energie (FE). */
    BATTERY(false, 4, true, Store.ENERGY),
    /** Wagon-serveur : donnees (un type). */
    SERVER(false, 4, true, Store.DATA),
    /** Wagon a air comprime. */
    AIR(false, 4, true, Store.AIR),
    /** Wagon plat : un bloc pose dessus (machine, buffer, conteneur). */
    FLAT(false, 4, true, Store.ITEMS),
    /** Fourgon robotique : unites robotiques. */
    ROBOT_VAN(false, 4, true, Store.ITEMS),
    /** Voiture voyageurs : joueurs et villageois. */
    PASSENGER(false, 4, true, Store.PASSENGERS),
    //0.20.6
    /** Wagon de pose : pose devant la tete du train les blocs d'un chantier de voie (materiaux dans ses cases). */
    LAYER(false, 4, true, Store.ITEMS);

    /** Ce que le vehicule transporte (ou brule, pour une locomotive). */
    public enum Store {
        NONE, ITEMS, FLUID, GAS, ENERGY, DATA, AIR, PASSENGERS
    }

    public final boolean locomotive;
    public final int tiers;
    /** L'id commence par le tier ({@code basic_steam_locomotive}). */
    public final boolean tiered;
    public final Store store;

    CarType(boolean locomotive, int tiers, boolean tiered, Store store) {
        this.locomotive = locomotive;
        this.tiers = tiers;
        this.tiered = tiered;
        this.store = store;
    }

    private static final String[] TIER_IDS = {"basic", "advanced", "elite", "ultimate"};

    public String baseId() {
        return switch (this) {
            case MINE_WAGON -> "mine_wagon";
            case DRAISINE -> "draisine";
            case STEAM -> "steam_locomotive";
            case TENDER -> "tender";
            case BOXCAR -> "boxcar";
            case HOPPER -> "hopper_car";
            case FIRELESS -> "fireless_locomotive";
            case DIESEL -> "diesel_locomotive";
            case GAS_LOCO -> "gas_locomotive";
            case ELECTRIC -> "electric_locomotive";
            case NUCLEAR -> "nuclear_locomotive";
            case MAGLEV -> "maglev_locomotive";
            case TANK -> "tank_car";
            case GAS_TANK -> "gas_tank_car";
            case BATTERY -> "battery_car";
            case SERVER -> "server_car";
            case AIR -> "air_car";
            case FLAT -> "flat_car";
            case ROBOT_VAN -> "robot_van";
            case PASSENGER -> "passenger_car";
            case LAYER -> "track_layer";
        };
    }

    public String id(int tier) {
        return tiered ? TIER_IDS[Math.max(0, Math.min(3, tier))] + "_" + baseId() : baseId();
    }

    public static String tierId(int tier) {
        return TIER_IDS[Math.max(0, Math.min(3, tier))];
    }

    public static CarType byId(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Locomotive a vapeur, tender : combustible solide et eau. */
    public boolean burnsSolid() {
        return this == STEAM || this == TENDER;
    }

    /** Traction electrique (catenaire, troisieme rail, voie MKIV). */
    public boolean electric() {
        return this == ELECTRIC || this == MAGLEV;
    }

    // ------------------------------------------------------------------ caracteristiques

    private static int pick(int tier, int... values) {
        return values[Math.max(0, Math.min(values.length - 1, tier))];
    }

    /** Cases d'objets (combustible pour le tender et la locomotive ; nucleaire : combustible neuf puis use). */
    public int slots(int tier) {
        return switch (this) {
            case MINE_WAGON -> 9;
            case STEAM -> 3;
            case TENDER -> 4 << tier;
            case BOXCAR, HOPPER -> pick(tier, 9, 18, 36, 54);
            case NUCLEAR -> 2;
            case FLAT -> 1;
            case ROBOT_VAN -> pick(tier, 4, 6, 8, 12);
            case LAYER -> 18;
            default -> 0;
        };
    }

    /** Reserve d'eau (mB). */
    public int water(int tier) {
        return switch (this) {
            case STEAM -> 2_000 << tier;
            case TENDER -> 8_000 << tier;
            default -> 0;
        };
    }

    /** Reservoir de fluide (mB) : citerne, carburant du diesel. */
    public int fluidCapacity(int tier) {
        return switch (this) {
            case TANK -> 16_000 << tier;
            case DIESEL -> 8_000 << tier;
            default -> 0;
        };
    }

    /** Reservoir de gaz (mB) : citerne a gaz, gaz de la locomotive, vapeur de la locomotive sans foyer. */
    public int gasCapacity(int tier) {
        return switch (this) {
            case GAS_TANK -> 32_000 << tier;
            case GAS_LOCO -> 16_000 << tier;
            case FIRELESS -> 24_000 << tier;
            default -> 0;
        };
    }

    /** Batterie (FE). */
    public long energyCapacity(int tier) {
        return switch (this) {
            case BATTERY -> 8_000_000L << tier;
            case ELECTRIC -> 2_000_000L << tier;
            case MAGLEV -> 4_000_000L << tier;
            default -> 0;
        };
    }

    /** Donnees (Mo). */
    public long dataCapacity(int tier) {
        return this == SERVER ? 4_000_000L << tier : 0;
    }

    /** Air comprime (L). */
    public int airCapacity(int tier) {
        return this == AIR ? 2_000 << tier : 0;
    }

    /** Places assises. */
    public int seats() {
        return switch (this) {
            case PASSENGER -> 2;
            case DRAISINE -> 2;
            default -> 1;
        };
    }

    /** Masse a vide (unites de wagon). */
    public float mass() {
        return switch (this) {
            case STEAM, DIESEL, GAS_LOCO, ELECTRIC -> 3;
            case NUCLEAR -> 4;
            case FIRELESS -> 2.5F;
            case MAGLEV -> 2;
            case TENDER -> 1.5F;
            case DRAISINE, MINE_WAGON -> 0.6F;
            default -> 1;
        };
    }

    /** Masse de la charge pleine. */
    public float loadMass(int tier) {
        return switch (this) {
            case BOXCAR, HOPPER, TANK -> 1.0F + tier * 0.5F;
            case GAS_TANK, ROBOT_VAN -> 0.6F + tier * 0.3F;
            case BATTERY -> 1.5F;
            case FLAT, LAYER -> 1.0F;
            case SERVER, AIR, PASSENGER -> 0.4F;
            case TENDER -> 1.0F;
            case MINE_WAGON -> 0.6F;
            default -> 0;
        };
    }

    /** Vitesse propre (px/tick). */
    public float maxSpeed(int tier) {
        return 0.8F * switch (this) {
            case DRAISINE -> 4;
            case STEAM -> 8 + tier;
            case FIRELESS -> 10 + tier;
            case DIESEL, GAS_LOCO -> 14 + 2 * tier;
            case ELECTRIC, NUCLEAR -> 22 + 2 * tier;
            default -> 34;
        };
    }

    /** Effort de traction au demarrage (acceleration d'une masse unite, px/tick^2). */
    public float tractive(int tier) {
        float base = switch (this) {
            case DRAISINE -> 0.035F;
            case STEAM -> 0.55F;
            case FIRELESS -> 0.7F;
            case DIESEL, GAS_LOCO -> 0.9F;
            case ELECTRIC -> 1.3F;
            case NUCLEAR -> 1.6F;
            case MAGLEV -> 2.2F;
            default -> 0;
        };
        return this == DRAISINE ? base : base * (float) Math.pow(1.5, tier);
    }

    /** Nombre de wagons tires a pleine vitesse. */
    public int haul(int tier) {
        return switch (this) {
            case DRAISINE -> 2;
            case STEAM -> pick(tier, 4, 6, 9, 13);
            case FIRELESS -> pick(tier, 6, 9, 13, 19);
            case DIESEL, GAS_LOCO -> pick(tier, 8, 12, 18, 27);
            case ELECTRIC -> pick(tier, 12, 18, 27, 40);
            case NUCLEAR -> pick(tier, 16, 24, 36, 54);
            case MAGLEV -> pick(tier, 24, 36, 54, 81);
            default -> 0;
        };
    }

    /** Consommation a pleine traction (FE/tick ; 1 tick de combustion = 1 024 FE). */
    public int fuelUse(int tier) {
        int base = switch (this) {
            case STEAM, FIRELESS -> 2_048;
            case DIESEL, GAS_LOCO -> 3_072;
            case ELECTRIC -> 4_096;
            case NUCLEAR -> 6_144;
            case MAGLEV -> 8_192;
            default -> 0;
        };
        return (int) (base * Math.pow(1.5, tier));
    }

    /** Eau consommee a pleine traction (mB/tick, en milliemes). */
    public int waterUse(int tier) {
        return this == STEAM ? 500 + 250 * tier : 0;
    }
}
