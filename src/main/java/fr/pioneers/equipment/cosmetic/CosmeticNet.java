package fr.pioneers.equipment.cosmetic;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 1.12.0 (lot E4) : paquets de la garde-robe (canal principal {@code PiNetwork}, VERSION 15). Serveur -> clients : la garde-robe d'un
 * joueur ({@link Sync}) a lui et a ceux qui le voient (a la connexion, a la reapparition, au changement de dimension, au debut du suivi,
 * a chaque changement). Client -> serveur : une intention ({@link Action}), revue par {@link CosmeticActions}.
 */
public final class CosmeticNet {

    private static SimpleChannel channel;

    private CosmeticNet() {
    }

    /** Enregistre les deux paquets a la suite ; renvoie le prochain numero libre. */
    public static int register(SimpleChannel ch, int id) {
        channel = ch;
        ch.messageBuilder(Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Sync::encode).decoder(Sync::decode)
              .consumerMainThread(Sync::handle).add();
        ch.messageBuilder(Action.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(Action::encode).decoder(Action::decode)
              .consumerMainThread(Action::handle).add();
        return id;
    }

    static Sync of(Player p) {
        Wardrobe w = CosmeticCap.get(p);
        ItemStack[] items = new ItemStack[Wardrobe.SIZE];
        for (int i = 0; i < items.length; i++) {
            items[i] = w.get(i).copy();
        }
        return new Sync(p.getId(), items, w.mask());
    }

    /** Garde-robe du joueur a lui et a ceux qui le suivent. */
    public static void sync(ServerPlayer p) {
        if (channel != null && p.connection != null) {
            channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), of(p));
        }
    }

    /** Garde-robe de {@code target} a un seul joueur (debut du suivi). */
    public static void syncTo(ServerPlayer viewer, Player target) {
        if (channel != null && viewer.connection != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> viewer), of(target));
        }
    }

    /** Client -> serveur : intention sur la garde-robe. */
    public static void toServer(int op, int index) {
        if (channel != null) {
            channel.sendToServer(new Action(op, index));
        }
    }

    public record Sync(int entity, ItemStack[] items, int mask) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeByte(mask);
            for (ItemStack s : items) {
                buf.writeItem(s);
            }
        }

        public static Sync decode(FriendlyByteBuf buf) {
            int entity = buf.readVarInt();
            int mask = buf.readByte();
            ItemStack[] items = new ItemStack[Wardrobe.SIZE];
            for (int i = 0; i < items.length; i++) {
                items[i] = buf.readItem();
            }
            return new Sync(entity, items, mask);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> fr.pioneers.equipment.cosmetic.client.CosmeticClient.receive(entity, items, mask)));
            context.get().setPacketHandled(true);
        }
    }

    public record Action(int op, int index) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte(op);
            buf.writeByte(index);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readByte(), buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    CosmeticActions.handle(player, op, index);
                }
            });
            context.get().setPacketHandled(true);
        }
    }
}
