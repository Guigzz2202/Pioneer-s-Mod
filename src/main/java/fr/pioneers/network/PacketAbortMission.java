package fr.pioneers.network;

import fr.pioneers.space.RocketEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Abandon de la mission depuis la carte, fusee en orbite : le passager redescend en capsule a la verticale de son point de depart.
 */
public record PacketAbortMission() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public static PacketAbortMission decode(FriendlyByteBuf buffer) {
        return new PacketAbortMission();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof RocketEntity rocket) {
            rocket.abortMission(player);
        }
    }
}
