package fr.pioneers.space.cargo;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Une fusee logistique en vol : aller (soutes pleines) ou retour (vide) vers son complexe d'attache.
 * Elle emporte ses modules ; {@code type} est sa soute dominante (couleur sur la carte).
 */
public class CargoFlight {

    public enum State {TRANSIT, LANDING}

    public final UUID id;
    /** Spatioport d'attache (proprietaire de la fusee). */
    public final UUID home;
    /** Point de depart du trajet en cours (1.0.7 : le retour d'un chantier de megastructure part du chantier). */
    public UUID from;
    public UUID to;
    public final CargoType type;
    /** Cargaison (vide au retour, sauf le reste d'une livraison a un chantier de megastructure). */
    public CompoundTag cargo;
    public long depart;
    public long arrive;
    public boolean returning;
    public State state = State.TRANSIT;
    public long landingDone;
    /** Fusee (index de RocketTier) et modules embarques. */
    public int tier;
    public ListTag modules = new ListTag();

    public CargoFlight(UUID id, UUID home, UUID from, UUID to, CargoType type, CompoundTag cargo, long depart, long arrive, boolean returning) {
        this.id = id;
        this.home = home;
        this.from = from;
        this.to = to;
        this.type = type;
        this.cargo = cargo;
        this.depart = depart;
        this.arrive = arrive;
        this.returning = returning;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("home", home);
        tag.putUUID("from", from);
        tag.putUUID("to", to);
        tag.putInt("type", type.ordinal());
        tag.put("cargo", cargo);
        tag.putLong("depart", depart);
        tag.putLong("arrive", arrive);
        tag.putBoolean("returning", returning);
        tag.putInt("state", state.ordinal());
        tag.putLong("landingDone", landingDone);
        tag.putInt("tier", tier);
        tag.put("modules", modules);
        return tag;
    }

    public static CargoFlight load(CompoundTag tag) {
        CargoFlight flight = new CargoFlight(tag.getUUID("id"), tag.getUUID("home"), tag.getUUID("from"), tag.getUUID("to"),
              CargoType.byIndex(tag.getInt("type")), tag.getCompound("cargo"), tag.getLong("depart"), tag.getLong("arrive"), tag.getBoolean("returning"));
        flight.state = State.values()[Math.max(0, Math.min(1, tag.getInt("state")))];
        flight.landingDone = tag.getLong("landingDone");
        flight.tier = tag.getInt("tier");
        flight.modules = tag.getList("modules", Tag.TAG_COMPOUND);
        return flight;
    }
}
