package fr.pioneers.space.mega;

import fr.pioneers.space.RocketEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.7 : « Demarrer le chantier de la megastructure » sur la carte de la fusee (client -> serveur). Le serveur verifie tout
 * ({@link RocketEntity#startMegaSite}) : passager d'une fusee en orbite, astre a portee de la fusee, megastructure pas encore debloquee.
 */
public record PacketMegaOpen(int mega) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(mega);
    }

    public static PacketMegaOpen decode(FriendlyByteBuf buffer) {
        return new PacketMegaOpen(buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof RocketEntity rocket) {
            rocket.startMegaSite(player, mega);
        }
    }
}
