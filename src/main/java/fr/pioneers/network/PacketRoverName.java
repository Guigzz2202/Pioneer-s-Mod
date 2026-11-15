package fr.pioneers.network;

import fr.pioneers.tile.TileEntityMiningOutpost;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Nom du rover d'une base d'extraction (champ de texte de l'interface).
 */
public record PacketRoverName(BlockPos pos, String name) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(name, TileEntityMiningOutpost.ROVER_NAME_LENGTH * 4);
    }

    public static PacketRoverName decode(FriendlyByteBuf buffer) {
        return new PacketRoverName(buffer.readBlockPos(), buffer.readUtf(TileEntityMiningOutpost.ROVER_NAME_LENGTH * 4));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        TileEntityMiningOutpost outpost = PacketGuard.tile(context.get().getSender(), pos, TileEntityMiningOutpost.class);
        if (outpost != null) {
            outpost.setRoverName(PacketGuard.sanitize(name, TileEntityMiningOutpost.ROVER_NAME_LENGTH));
        }
    }
}
