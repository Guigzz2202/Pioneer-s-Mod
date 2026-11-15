package fr.pioneers.network;

import fr.pioneers.space.RocketEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Le passager d'une fusee demande le decollage (touche Espace).
 */
public record PacketRocketLaunch() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public static PacketRocketLaunch decode(FriendlyByteBuf buffer) {
        return new PacketRocketLaunch();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof RocketEntity rocket) {
            rocket.tryLaunch(player);
        }
    }
}
