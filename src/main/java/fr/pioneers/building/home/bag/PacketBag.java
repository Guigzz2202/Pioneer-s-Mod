package fr.pioneers.building.home.bag;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.5-beta (lot 31, retour 42) : geste sur un sac de rangement (client -> serveur) : {@code op} ({@link StorageBagMenu#TAKE} ...
 * {@link StorageBagMenu#DUMP_OPEN}), case visee {@code index} et son objet {@code id} (le serveur revoit la case par l'objet), argument
 * {@code arg} (prise : 0 pile, 1 moitie ; depot : 0 tout, 1 un ; vidage dans un conteneur ouvert : case du menu ouvert qui tient le sac).
 * Enregistre EN FIN de {@code PiNetwork} (VERSION 21 inchangee).
 */
public record PacketBag(int op, int index, String id, int arg) {

    public static final int ID_MAX = 128;

    public boolean valid() {
        return op >= StorageBagMenu.TAKE && op <= StorageBagMenu.DUMP_OPEN && index >= -1 && index < 4096 && id.length() <= ID_MAX && arg >= 0
               && arg < 4096;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(op);
        buf.writeVarInt(index + 1);
        buf.writeUtf(id, ID_MAX);
        buf.writeVarInt(arg);
    }

    public static PacketBag decode(FriendlyByteBuf buf) {
        return new PacketBag(buf.readByte(), buf.readVarInt() - 1, buf.readUtf(ID_MAX), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && !player.isSpectator() && valid()) {
            if (op == StorageBagMenu.DUMP_OPEN) {
                BagDeposit.intoOpenMenu(player, arg);
            } else if (player.containerMenu instanceof StorageBagMenu menu && menu.stillValid(player)) {
                menu.act(player, op, index, id, arg);
            }
        }
        context.get().setPacketHandled(true);
    }
}
