package fr.pioneers.world.season;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.5-beta (lot 1) : le calendrier du serveur pour le client (12 octets : decalage et duree d'une saison). Le client en deduit la date a
 * chaque instant d'apres le temps du jour qu'il recoit deja ({@link Seasons#setClient}). Envoye a la connexion, a chaque changement de jour
 * ou de saison, de duree (config) ou de decalage (commande).
 */
public record PacketSeason(long offset, int length) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(offset);
        buffer.writeInt(length);
    }

    public static PacketSeason decode(FriendlyByteBuf buffer) {
        return new PacketSeason(buffer.readLong(), buffer.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> Seasons.setClient(offset, length));
        context.get().setPacketHandled(true);
    }
}
