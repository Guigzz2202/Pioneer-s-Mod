package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.network.PiNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 1.9 (MAJ RP) : deux paquets generiques pour tous les blocs de la MAJ (profil, ruines, economie, villages, clans) : une action (entier,
 * {@link RpActions}) et une charge NBT bornee. Le client ne demande que des ACTIONS (jamais un montant, un prix, un gain) : le serveur
 * recalcule et valide tout. Cadence bornee par joueur (rafales de {@link #BURST} demandes, recharge de {@link #PER_SECOND} par seconde).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RpNet {

    /** Taille maximale d'une charge (octets NBT). */
    public static final int MAX_BYTES = 30_000;
    public static final int BURST = 20, PER_SECOND = 10;

    private static final Map<Integer, BiConsumer<ServerPlayer, CompoundTag>> SERVER = new HashMap<>();
    private static final Map<UUID, double[]> BUCKETS = new HashMap<>();

    private RpNet() {
    }

    /** Enregistre un traitement cote serveur (appele au chargement des classes de chaque bloc). */
    public static void onServer(int action, BiConsumer<ServerPlayer, CompoundTag> handler) {
        SERVER.put(action, handler);
    }

    /**
     * 1.9 (MAJ RP, GameTests) : rejoue une action du client cote serveur, comme {@link C2S#handle} mais sans la cadence (Console, chantier,
     * echange...). Seuls les GameTests ({@code fr.pioneers.gametest}) l'appellent ; le jeu ne passe que par le paquet. Vrai si l'action a un
     * traitement.
     */
    public static boolean dispatch(ServerPlayer player, int action, CompoundTag tag) {
        BiConsumer<ServerPlayer, CompoundTag> h = SERVER.get(action);
        if (h == null) {
            return false;
        }
        h.accept(player, tag);
        return true;
    }

    public static int register(SimpleChannel ch, int id) {
        ch.messageBuilder(C2S.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(C2S::encode).decoder(C2S::decode)
              .consumerMainThread(C2S::handle).add();
        ch.messageBuilder(S2C.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(S2C::encode).decoder(S2C::decode)
              .consumerMainThread(S2C::handle).add();
        return id;
    }

    public static void send(ServerPlayer player, int action, CompoundTag tag) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2C(action, tag));
    }

    /** Aux joueurs qui suivent l'entite, et a elle-meme si c'est un joueur. */
    public static void sendTracking(Entity entity, int action, CompoundTag tag) {
        PiNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new S2C(action, tag));
    }

    public static void sendAll(int action, CompoundTag tag) {
        PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2C(action, tag));
    }

    /** Cote client : demande au serveur. */
    public static void toServer(int action, CompoundTag tag) {
        PiNetwork.CHANNEL.sendToServer(new C2S(action, tag));
    }

    static boolean allow(ServerPlayer player) {
        long now = Util.getMillis();
        double[] b = BUCKETS.computeIfAbsent(player.getUUID(), k -> new double[]{BURST, now});
        b[0] = Math.min(BURST, b[0] + (now - b[1]) / 1000.0 * PER_SECOND);
        b[1] = now;
        if (b[0] < 1) {
            return false;
        }
        b[0] -= 1;
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BUCKETS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        BUCKETS.clear();
    }

    /** Client -> serveur. */
    public record C2S(int action, CompoundTag tag) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeNbt(tag);
        }

        public static C2S decode(FriendlyByteBuf buf) {
            int action = buf.readVarInt();
            if (buf.readableBytes() > MAX_BYTES) {
                buf.skipBytes(buf.readableBytes());
                return new C2S(-1, new CompoundTag());
            }
            CompoundTag t = buf.readNbt();
            return new C2S(action, t == null ? new CompoundTag() : t);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().setPacketHandled(true);
            if (player == null || action < 0 || !allow(player)) {
                return;
            }
            BiConsumer<ServerPlayer, CompoundTag> h = SERVER.get(action);
            if (h != null) {
                try {
                    h.accept(player, tag);
                } catch (RuntimeException e) {
                    Pioneers.LOGGER.warn("Action RP {} refusee pour {} : {}", action, player.getGameProfile().getName(), e.toString());
                }
            }
        }
    }

    /** Serveur -> client. */
    public record S2C(int action, CompoundTag tag) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeNbt(tag);
        }

        public static S2C decode(FriendlyByteBuf buf) {
            int action = buf.readVarInt();
            CompoundTag t = buf.readAnySizeNbt();
            return new S2C(action, t == null ? new CompoundTag() : t);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.client.rp.RpClient.receive(action, tag));
        }
    }
}
