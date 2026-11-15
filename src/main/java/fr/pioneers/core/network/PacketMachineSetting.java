package fr.pioneers.core.network;

import fr.pioneers.core.machine.MachineGui;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.2-beta : reglage a valeur d'une interface standard (curseur de la boite de vitesses, du regulateur a boules...) : numero du reglage et
 * valeur choisie. Garde commune {@link PacketGuard#tile} (bloc charge, interface ouverte ou a portee, pas de spectateur) puis
 * {@link MachineGui#handleSetting}, qui borne la valeur (elle vient du client).
 */
public record PacketMachineSetting(BlockPos pos, int setting, int value) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeVarInt(setting);
        buffer.writeVarInt(value);
    }

    public static PacketMachineSetting decode(FriendlyByteBuf buffer) {
        return new PacketMachineSetting(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        MachineGui machine = PacketGuard.tile(player, pos, MachineGui.class);
        if (machine != null) {
            machine.handleSetting(player, setting, value);
        }
    }
}
