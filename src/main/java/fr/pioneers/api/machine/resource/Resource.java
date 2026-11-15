package fr.pioneers.api.machine.resource;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.core.side.SideType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * API Machine (lot 3) : COUCHE RESSOURCE COMMUNE (decision 2 de Guillaume : « buffers = genre BUFFER, couche ressource commune »). Pour
 * chaque ressource du mod : son canal de faces ({@link SideType}), sa capacite Forge, son unite affichee et la cle de son contenu dans la
 * sauvegarde ({@code contents/<cle>} des buffers, format d'avant le lot 3). Le stockage lui-meme : {@link ResourceStore}.
 * <p>
 * Sert aujourd'hui aux buffers ({@code StorageUnit}) ; demain aux reservoirs des machines et aux ports des multiblocs (lots 4 a 6).
 */
public enum Resource {
    ITEMS(SideType.ITEMS, "", "items"),
    FLUIDS(SideType.FLUIDS, "mB", "fluid"),
    GASES(SideType.GASES, "mB", "gas"),
    ENERGY(SideType.ENERGY, "FE", "energy"),
    HEAT(SideType.HEAT, "K", "heat"),
    PRESSURE(SideType.PRESSURE, "L", "pressure"),
    DATA(SideType.DATA, "Mo", "data");

    /** Canal des faces. */
    public final SideType side;
    /** Unite affichee (quantites). */
    public final String unit;
    /** Cle du contenu dans la sauvegarde ({@code contents/<cle>}). */
    public final String nbtKey;

    Resource(SideType side, String unit, String nbtKey) {
        this.side = side;
        this.unit = unit;
        this.nbtKey = nbtKey;
    }

    /** Capacite Forge par laquelle la ressource se transporte (cables, entonnoirs, machines voisines). */
    public Capability<?> capability() {
        return switch (this) {
            case ITEMS -> ForgeCapabilities.ITEM_HANDLER;
            case FLUIDS -> ForgeCapabilities.FLUID_HANDLER;
            case GASES -> PioneersAPI.GAS_HANDLER;
            case ENERGY -> ForgeCapabilities.ENERGY;
            case HEAT -> PioneersAPI.HEAT_HANDLER;
            case PRESSURE -> PioneersAPI.PRESSURE_HANDLER;
            case DATA -> PioneersAPI.DATA_HANDLER;
        };
    }

    /** Ressource d'un canal de faces. */
    public static Resource of(SideType side) {
        for (Resource r : values()) {
            if (r.side == side) {
                return r;
            }
        }
        throw new IllegalArgumentException("ressource inconnue : " + side);
    }
}
