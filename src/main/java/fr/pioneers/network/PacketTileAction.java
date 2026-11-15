package fr.pioneers.network;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Bouton d'une interface : transmet une action (numero) a la machine.
 */
public record PacketTileAction(BlockPos pos, int action) {

    /** Machine qui reagit aux boutons de son interface. */
    public interface Handler {

        void handleAction(ServerPlayer player, int action);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(action);
    }

    public static PacketTileAction decode(FriendlyByteBuf buffer) {
        return new PacketTileAction(buffer.readBlockPos(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        Handler handler = PacketGuard.tile(player, pos, Handler.class);
        if (handler != null) {
            handler.handleAction(player, action);
        }
    }
}
