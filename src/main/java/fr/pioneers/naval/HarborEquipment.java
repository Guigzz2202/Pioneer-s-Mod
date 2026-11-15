package fr.pioneers.naval;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.rail.train.CarType;
import java.util.Locale;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * 0.20.14 : equipements de quai du port multibloc. Chacun se pose dans la rangee du quai (a la place d'une dalle) et sert une ressource :
 * il ouvre les echanges de cette ressource avec le navire amarre (sans lui, rien ne passe) et raccorde aux cables la reserve correspondante
 * de la capitainerie. Plusieurs equipements de la meme sorte additionnent leur debit (4 au plus).
 */
public enum HarborEquipment {
    /** Grue de quai : objets (cargo). */
    CRANE("harbor_crane", 0xB07A3E),
    /** Bras de chargement : fluides (petrolier). */
    FLUID_ARM("harbor_fluid_arm", 0x3F76E4),
    /** Bras de chargement de gaz (methanier). */
    GAS_ARM("harbor_gas_arm", 0xE0E6EE),
    /** Borne de recharge : energie (barge a batteries) et batteries des navires electriques. */
    CHARGER("harbor_charger", 0x3FB6C4),
    /** Bras d'air comprime (transporteur d'air). */
    AIR_ARM("harbor_air_arm", 0x9FB8C8),
    /** Mat de donnees : faisceau vers le navire-serveur. */
    DATA_MAST("harbor_data_mast", 0xA460D9),
    /** Passerelle : voyageurs (ferry). */
    GANGWAY("harbor_gangway", 0xF0C040),
    /** Poste de soutage : combustible, carburant et assemblages des machines. */
    BUNKER("harbor_bunker", 0xE07A2A);

    public final String id;
    /** Teinte de la ressource (voyants, bagues). */
    public final int color;

    HarborEquipment(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static HarborEquipment byId(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
    }

    /** Equipement qui sert une cale ou une machine du navire (null : aucun). */
    public static HarborEquipment serving(CarType car) {
        return switch (car) {
            case BOXCAR -> CRANE;
            case TANK -> FLUID_ARM;
            case GAS_TANK -> GAS_ARM;
            case BATTERY, ELECTRIC -> CHARGER;
            case AIR -> AIR_ARM;
            case SERVER -> DATA_MAST;
            case PASSENGER -> GANGWAY;
            case STEAM, TENDER, DIESEL, NUCLEAR -> BUNKER;
            default -> null;
        };
    }

    /** Capacites de la capitainerie que cet equipement expose aux cables. */
    public boolean exposes(Capability<?> cap) {
        return switch (this) {
            case CRANE -> cap == ForgeCapabilities.ITEM_HANDLER;
            case FLUID_ARM -> cap == ForgeCapabilities.FLUID_HANDLER;
            case GAS_ARM -> cap == PioneersAPI.GAS_HANDLER;
            case CHARGER -> cap == ForgeCapabilities.ENERGY;
            case AIR_ARM -> cap == PioneersAPI.PRESSURE_HANDLER;
            case DATA_MAST -> cap == PioneersAPI.DATA_HANDLER;
            case BUNKER -> cap == ForgeCapabilities.ITEM_HANDLER || cap == ForgeCapabilities.FLUID_HANDLER;
            case GANGWAY -> false;
        };
    }
}
