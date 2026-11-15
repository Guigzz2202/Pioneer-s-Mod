package fr.pioneers.world.pet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.0.5-beta (lot 5) : client -> serveur : touche Rappel : rappeler TOUS ses animaux (appui court), UN seul (choisi dans la roue), ou
 * demander la liste pour la roue (maintien ; reponse {@link PacketPetList}, au plus une demande par seconde). Le serveur decide de tout.
 * <p>
 * 1.0.5-beta (lot 33) : une demande trop rapprochee n'est plus jetee mais DUE ({@link PetListSync}) : elle part des que le delai le permet
 * (avant : sans reponse, la roue restait vide jusqu'a la redemande du client 1,25 s plus tard).
 */
public record PacketPetRecall(int op, UUID pet) {

    public static final int ALL = 0, ONE = 1, LIST = 2;
    /** 1.0.5-beta (lot 12) : vide a l'arret du serveur ({@link PetRecall#stopping}) : rien ne passe d'un monde a l'autre en solo. */
    static void clear() {
        PetListSync.SERVER.clear();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(op);
        buf.writeUUID(pet);
    }

    public static PacketPetRecall decode(FriendlyByteBuf buf) {
        return new PacketPetRecall(buf.readByte(), buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (p == null) {
            return;
        }
        switch (op) {
            case ALL -> PetRecall.request(p, null);
            case ONE -> PetRecall.request(p, pet);
            case LIST -> {
                //1.0.5-beta (lot 12) : heure d'un autre monde (solo : changement de monde, temps plus petit) -> pas de blocage (PetRules.waiting) ;
                //lot 33 : trop tot -> envoi DU (PetRecall.tick), plus jamais de demande sans reponse
                if (PetListSync.SERVER.ask(p.getUUID(), p.server.overworld().getGameTime())) {
                    send(p);
                }
            }
            default -> {
            }
        }
    }

    /** Envoie la liste au joueur (reponse, envoi du ou pousse). */
    static void send(ServerPlayer p) {
        fr.pioneers.core.network.PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), list(p));
    }

    static PacketPetList list(ServerPlayer p) {
        List<PacketPetList.Row> rows = new ArrayList<>();
        for (PetRegistry.Entry e : PetRegistry.get(p.server).list(p.getUUID())) {
            Entity live = PetRecall.find(p.server, e.id());
            String dim = live != null ? live.level().dimension().location().toString() : e.dim().toString();
            String name = live != null && live.hasCustomName() ? live.getCustomName().getString() : e.name();
            int dist = -1;
            if (dim.equals(p.level().dimension().location().toString())) {
                dist = (int) Math.round(live != null ? live.distanceTo(p) : Math.sqrt(p.distanceToSqr(e.x(), e.y(), e.z())));
            }
            rows.add(new PacketPetList.Row(e.id(), e.kind(), e.type(), name, dim, dist, live != null));
        }
        return new PacketPetList(rows);
    }
}
