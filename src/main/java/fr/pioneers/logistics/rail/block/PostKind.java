package fr.pioneers.logistics.rail.block;

import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.CarType;
import java.util.Locale;

/**
 * 0.20.2 : postes du quai. Chacun sert le vehicule arrete devant lui (la fosse : celui qui est au-dessus). Les postes « en tiers » existent
 * en MKI a MKIV (debit x4 par tier), les postes de ravitaillement en un seul modele.
 */
public enum PostKind {
    /** Quai a objets (0.20.0) : tout vehicule a cases (wagons, tender, combustible de la vapeur, fourgon robotique). */
    ITEM(true, "item_loader"),
    /** Silo de chargement : tremies et wagonnets, par le dessus. */
    SILO(true, "loading_silo"),
    /** Fosse de dechargement (sous la voie) : tremies et wagonnets. */
    PIT(true, "unloading_pit"),
    /** Charbonnier : combustible de la vapeur et des tenders. */
    COALING(false, "coaling_tower"),
    /** Grue de quai : le bloc du wagon plat. */
    CRANE(false, "platform_crane"),
    /** Poste de combustible blinde : assemblages de la locomotive nucleaire (et reprise des uses). */
    NUCLEAR(false, "nuclear_fuel_station"),
    /** Bras de remplissage : citernes a fluide et a gaz. */
    FLUID(true, "fluid_loader"),
    /** Pompe a carburant : diesel et locomotive a gaz. */
    FUEL(false, "fuel_pump"),
    /** Borne de vapeur : locomotive sans foyer. */
    STEAM(false, "steam_charger"),
    /** Prise de quai : wagon-batterie et batteries des electriques. */
    ENERGY(true, "energy_loader"),
    /** Borne de recharge : electriques et maglev. */
    CHARGER(false, "charging_post"),
    /** Fibre de quai : wagon-serveur. */
    DATA(true, "data_loader"),
    /** Raccord pneumatique : wagon a air comprime. */
    AIR(true, "air_loader"),
    /** Quai voyageurs : villageois et passengers des voitures. */
    PASSENGER(false, "passenger_platform");

    public final boolean tiered;
    public final String baseId;

    PostKind(boolean tiered, String baseId) {
        this.tiered = tiered;
        this.baseId = baseId;
    }

    public String id(int tier) {
        return tiered ? CarType.tierId(tier) + "_" + baseId : baseId;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Postes a cases d'objets ({@link ItemLoaderTile}). */
    public boolean items() {
        return ordinal() <= NUCLEAR.ordinal();
    }

    /** Le poste peut decharger (bouton charger / decharger). */
    public boolean reversible() {
        return this == ITEM || this == CRANE || this == FLUID || this == ENERGY || this == DATA || this == AIR || this == PASSENGER;
    }

    /** Le poste ne fait que decharger. */
    public boolean unloadOnly() {
        return this == PIT;
    }

    /** Le poste sert ce vehicule. */
    public boolean serves(Car c) {
        CarType t = c.type;
        return switch (this) {
            case ITEM -> c.items.getSlots() > 0 && t != CarType.NUCLEAR && t != CarType.FLAT;
            case SILO, PIT -> t == CarType.HOPPER || t == CarType.MINE_WAGON;
            case COALING -> t == CarType.STEAM || t == CarType.TENDER;
            case CRANE -> t == CarType.FLAT;
            case NUCLEAR -> t == CarType.NUCLEAR;
            case FLUID -> t == CarType.TANK || t == CarType.GAS_TANK;
            case FUEL -> t == CarType.DIESEL || t == CarType.GAS_LOCO;
            case STEAM -> t == CarType.FIRELESS;
            case ENERGY -> t == CarType.BATTERY || t.electric();
            case CHARGER -> t.electric();
            case DATA -> t == CarType.SERVER;
            case AIR -> t == CarType.AIR;
            case PASSENGER -> t == CarType.PASSENGER;
        };
    }

    /** Debit par tick de base (objets : par seconde). */
    public int rate(int tier) {
        int x = tiered ? 1 << (2 * Math.max(0, Math.min(3, tier))) : 4;
        return switch (this) {
            case ITEM, SILO, PIT -> 16 * x;
            case COALING, CRANE, NUCLEAR -> 64;
            case FLUID -> 500 * x;
            case FUEL, STEAM -> 2_000;
            case ENERGY -> 32_768 * x;
            case CHARGER -> 131_072;
            case DATA -> 2_000 * x;
            case AIR -> 40 * x;
            case PASSENGER -> 1;
        };
    }
}
