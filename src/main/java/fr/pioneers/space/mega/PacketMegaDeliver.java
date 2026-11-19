package fr.pioneers.space.mega;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : bouton « Livrer depuis l'inventaire » de l'ecran du chantier : le serveur prend dans
 * l'inventaire du joueur tout ce qui manque encore au chantier (joueur a portee du bloc, bloc de chantier verifie).
 */
public record PacketMegaDeliver(BlockPos pos) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    public static PacketMegaDeliver decode(FriendlyByteBuf buffer) {
        return new PacketMegaDeliver(buffer.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (player != null && player.level().isLoaded(pos) && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && player.level().getBlockState(pos).getBlock() instanceof MegaSiteBlock) {
                MegaSiteBlock.deliverFrom(player, pos);
            }
        });
        context.get().setPacketHandled(true);
    }
}
