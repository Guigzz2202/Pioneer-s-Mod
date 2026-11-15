package fr.pioneers.farm.aquarium;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.5-beta (lot 25) : pose ou retrait d'un element de la composition (client -> serveur) : bassin ouvert {@code opened} (l'interface
 * {@link AquariumMenu} doit etre ouverte sur lui), case du fond {@code cell}, emplacement 0..3, couche (0 sol, 1 element), sorte (index de
 * {@link AquariumDecor#KINDS}, -1 = retirer). Enregistre EN FIN de {@code PiNetwork} (VERSION 21 inchangee).
 */
public record PacketAquarium(BlockPos opened, BlockPos cell, int slot, int layer, int kind) {

    /** Bornes : emplacement, couche, sorte (le reste est verifie par {@link AquariumCompose#edit}). */
    public boolean valid() {
        return slot >= 0 && slot < AquariumDecor.SLOTS && (layer == 0 || layer == 1) && kind >= -1 && kind < AquariumDecor.KINDS.length
               && opened.distManhattan(cell) <= 3 * Aquariums.MAX_TANK;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(opened);
        buf.writeBlockPos(cell);
        buf.writeByte(slot);
        buf.writeByte(layer);
        buf.writeVarInt(kind + 1);
    }

    public static PacketAquarium decode(FriendlyByteBuf buf) {
        return new PacketAquarium(buf.readBlockPos(), buf.readBlockPos(), buf.readByte(), buf.readByte(), buf.readVarInt() - 1);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && !player.isSpectator() && valid() && player.containerMenu instanceof AquariumMenu menu && menu.pos().equals(opened)
            && menu.stillValid(player)) {
            String why = AquariumCompose.edit(player, opened, cell, slot, layer, kind);
            if (why != null) {
                AquariumCompose.tell(player, why, AquariumDecor.byIndex(kind));
            }
        }
        context.get().setPacketHandled(true);
    }
}
