package fr.pioneers.core.network;

import fr.pioneers.space.CapsuleEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Commandes du passager d'une capsule en descente : propulseurs (avant/arriere, gauche/droite), retrofusee, et bascules du
 * parachute et du pilote automatique.
 */
public record PacketCapsuleInput(float forward, float strafe, boolean thrust, boolean chute, boolean autopilot) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(forward);
        buffer.writeFloat(strafe);
        buffer.writeByte((thrust ? 1 : 0) | (chute ? 2 : 0) | (autopilot ? 4 : 0));
    }

    public static PacketCapsuleInput decode(FriendlyByteBuf buffer) {
        float forward = buffer.readFloat(), strafe = buffer.readFloat();
        int flags = buffer.readByte();
        return new PacketCapsuleInput(forward, strafe, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && player.getVehicle() instanceof CapsuleEntity capsule && Float.isFinite(forward) && Float.isFinite(strafe)) {
            capsule.handleInput(player, forward, strafe, thrust, chute, autopilot);
        }
    }
}
