package fr.pioneers.core.network;

import fr.pioneers.space.client.ClientSpaceEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Evenements spatiaux la ou se trouve le joueur : evenement local (type, -1 aucun ; ticks restants ; intensite) et eruption de
 * son etoile (ticks restants, 0 aucune ; intensite).
 */
public record PacketSpaceEvent(int type, int remaining, float intensity, int flareRemaining, float flareIntensity) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(type + 1);
        buffer.writeVarInt(remaining);
        buffer.writeFloat(intensity);
        buffer.writeVarInt(flareRemaining);
        buffer.writeFloat(flareIntensity);
    }

    public static PacketSpaceEvent decode(FriendlyByteBuf buffer) {
        return new PacketSpaceEvent(buffer.readVarInt() - 1, buffer.readVarInt(), buffer.readFloat(), buffer.readVarInt(), buffer.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSpaceEvents.receive(this));
    }
}
