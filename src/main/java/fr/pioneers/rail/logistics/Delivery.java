package fr.pioneers.rail.logistics;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * 0.20.5 : une livraison du repartiteur : une quantite d'une ressource, d'une gare fournisseuse a une gare demandeuse, confiee a un train
 * logistique qui revient ensuite a son depot.
 */
public final class Delivery {

    public enum State {
        /** En route vers le fournisseur. */
        TO_SUPPLY,
        LOADING,
        TO_DEMAND,
        UNLOADING,
        /** Livre, retour au depot. */
        RETURN;

        public static State byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public final UUID id;
    public final Cargo cargo;
    public final long amount;
    /** Panneaux des gares (positions). */
    public final long from, to;
    public final UUID train;
    public final String depot;
    public State state = State.TO_SUPPLY;
    /** Quantite effectivement chargee (au depart du fournisseur). */
    public long loaded;
    public final long created;

    public Delivery(UUID id, Cargo cargo, long amount, long from, long to, UUID train, String depot, long created) {
        this.id = id;
        this.cargo = cargo;
        this.amount = amount;
        this.from = from;
        this.to = to;
        this.train = train;
        this.depot = depot;
        this.created = created;
    }

    /** Encore attendu a la gare demandeuse (la quantite chargee si elle est connue). */
    public long inFlight() {
        return state == State.RETURN ? 0 : state.ordinal() >= State.TO_DEMAND.ordinal() ? loaded : amount;
    }

    /** Encore a prendre au fournisseur. */
    public long reservedOut() {
        return state.ordinal() <= State.LOADING.ordinal() ? amount : 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.put("cargo", cargo.save());
        t.putLong("amount", amount);
        t.putLong("from", from);
        t.putLong("to", to);
        t.putUUID("train", train);
        t.putString("depot", depot);
        t.putByte("state", (byte) state.ordinal());
        t.putLong("loaded", loaded);
        t.putLong("created", created);
        return t;
    }

    public static Delivery load(CompoundTag t) {
        Delivery d = new Delivery(t.getUUID("id"), Cargo.load(t.getCompound("cargo")), t.getLong("amount"), t.getLong("from"), t.getLong("to"),
              t.getUUID("train"), t.getString("depot"), t.getLong("created"));
        d.state = State.byId(t.getByte("state"));
        d.loaded = t.getLong("loaded");
        return d;
    }
}
