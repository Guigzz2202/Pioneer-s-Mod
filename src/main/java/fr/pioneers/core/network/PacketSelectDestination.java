package fr.pioneers.core.network;

import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.SolarSystem;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Choix d'une destination sur la carte, par le passager d'une fusee en orbite : la surface de l'astre, ou sa station orbitale.
 */
public record PacketSelectDestination(SolarSystem.Body body, boolean station) {

    public PacketSelectDestination(SolarSystem.Body body) {
        this(body, false);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(body);
        buffer.writeBoolean(station);
    }

    public static PacketSelectDestination decode(FriendlyByteBuf buffer) {
        return new PacketSelectDestination(buffer.readEnum(SolarSystem.Body.class), buffer.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof RocketEntity rocket) {
            if (station) {
                rocket.selectStation(player, body);
            } else {
                rocket.selectDestination(player, body);
            }
        }
    }
}
