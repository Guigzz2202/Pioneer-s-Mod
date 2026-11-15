package fr.pioneers.core.network;

import fr.pioneers.space.tile.TileEntityLaunchPad;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Renommage d'un complexe de lancement (nom de station logistique).
 */
public record PacketSetStationName(BlockPos pos, String name) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(name, 64);
    }

    public static PacketSetStationName decode(FriendlyByteBuf buffer) {
        return new PacketSetStationName(buffer.readBlockPos(), buffer.readUtf(64));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        TileEntityLaunchPad pad = PacketGuard.tile(context.get().getSender(), pos, TileEntityLaunchPad.class);
        if (pad != null) {
            pad.setStationName(PacketGuard.sanitize(name, TileEntityLaunchPad.STATION_NAME_LENGTH));
        }
    }
}
