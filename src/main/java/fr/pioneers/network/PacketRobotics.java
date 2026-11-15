package fr.pioneers.network;

import fr.pioneers.client.robotics.ClientRobotics;
import fr.pioneers.robotics.Endpoint;
import fr.pioneers.robotics.RoboticOrder;
import fr.pioneers.robotics.RoboticsOrders;
import fr.pioneers.robotics.RoboticsScheduler;
import fr.pioneers.robotics.hub.RoboticHubData;
import fr.pioneers.robotics.logistics.LogisticsRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Robotique : le client demande l'etat des ordres et des hubs (menu ouvert), le serveur l'envoie ; le client cree, modifie, met en
 * pause ou supprime un ordre.
 */
public final class PacketRobotics {

    private PacketRobotics() {
    }

    /** Distance maximale (blocs) entre le joueur et les points d'un ordre qu'il cree. */
    private static final int MAX_DISTANCE = 192;

    /** Etat complet de la dimension (ordres avec leur etat, hubs). */
    public static CompoundTag snapshot(ServerLevel level) {
        CompoundTag tag = new CompoundTag();
        ListTag orders = new ListTag();
        for (RoboticOrder order : RoboticsOrders.get(level).all()) {
            orders.add(order.save(true));
        }
        tag.put("orders", orders);
        ListTag hubs = new ListTag();
        for (RoboticHubData hub : LogisticsRegistry.get(level).hubs(level.getGameTime())) {
            CompoundTag entry = new CompoundTag();
            entry.put("center", NbtUtils.writeBlockPos(hub.getCenter()));
            entry.putByte("kind", (byte) hub.getKind().ordinal());
            entry.putByte("units", (byte) hub.unitCount());
            entry.putByte("capacity", (byte) hub.capacity());
            entry.putByte("active", (byte) hub.getActive());
            entry.putShort("range", (short) hub.getRange());
            entry.putByte("status", (byte) hub.getStatus().ordinal());
            hubs.add(entry);
        }
        tag.put("hubs", hubs);
        return tag;
    }

    /** 0.20.46 (audit) : joueurs qui ont demande l'etat (menu ouvert, outil en main : une demande toutes les 2 s) -> derniere demande (ms). */
    private static final Map<UUID, Long> INTERESTED = new HashMap<>();
    /** Dimensions dont l'etat a change depuis le dernier envoi, et moment de ce dernier envoi (ms). */
    private static final Set<ResourceKey<Level>> PENDING = new HashSet<>();
    private static final Map<ResourceKey<Level>, Long> SENT = new HashMap<>();
    /** Interet garde 10 s apres la derniere demande ; au plus un envoi par seconde et par dimension. */
    private static final long INTEREST_MS = 10_000, PERIOD_MS = 1_000;

    static void forget(UUID player) {
        INTERESTED.remove(player);
    }

    /** Le joueur regarde la carte robotique (paquets Watch reguliers) : il recoit toujours l'etat des ordres et des hubs. */
    static void interested(ServerPlayer player) {
        INTERESTED.put(player.getUUID(), Util.getMillis());
    }

    static void forgetAll() {
        INTERESTED.clear();
        PENDING.clear();
        SENT.clear();
    }

    /**
     * L'etat de la dimension a change : il part (au plus une fois par seconde, voir {@link #flush}) vers les joueurs qui l'ont demande
     * recemment. 0.20.46 (audit) : avant, tout l'etat partait vers tous les joueurs de la dimension, jusqu'a chaque tick.
     */
    public static void broadcast(ServerLevel level) {
        PENDING.add(level.dimension());
    }

    /** Un joueur vient de modifier un ordre : il recoit l'etat tout de suite, les autres au prochain envoi. */
    public static void changed(ServerLevel level, ServerPlayer player) {
        PENDING.add(level.dimension());
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(snapshot(level)));
    }

    /** A chaque tick de la dimension (ordonnanceur) : envoie l'etat s'il a change et que la derniere fois date d'une seconde. */
    public static void flush(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        if (!PENDING.contains(dimension)) {
            return;
        }
        long now = Util.getMillis();
        Long last = SENT.get(dimension);
        if (last != null && now - last < PERIOD_MS) {
            return;
        }
        PENDING.remove(dimension);
        SENT.put(dimension, now);
        Sync sync = null;
        for (ServerPlayer player : level.players()) {
            Long asked = INTERESTED.get(player.getUUID());
            if (asked == null || now - asked > INTEREST_MS) {
                continue;
            }
            if (sync == null) {
                sync = new Sync(snapshot(level));
            }
            Sync packet = sync;
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
        INTERESTED.values().removeIf(asked -> now - asked > INTEREST_MS);
    }

    /**
     * 0.20.46 (audit) : un joueur (pas spectateur) n'agit sur un ordre existant que s'il est a portee de la carte : un de ses points ou
     * son hub a moins de {@link PacketRoboticsMap#REMOTE} blocs (les ordres n'ont pas de proprietaire : ils sont partages).
     */
    public static boolean mayEdit(ServerPlayer player, RoboticOrder order) {
        if (player.isSpectator()) {
            return false;
        }
        double max = (double) PacketRoboticsMap.REMOTE * PacketRoboticsMap.REMOTE;
        BlockPos at = player.blockPosition();
        if (order.anchor().distSqr(at) <= max || order.hub != null && order.hub.distSqr(at) <= max) {
            return true;
        }
        for (Object point : new Object[]{order.min, order.max, order.source, order.target}) {
            BlockPos pos = point instanceof Endpoint endpoint ? endpoint.pos() : point instanceof BlockPos block ? block : null;
            if (pos != null && pos.distSqr(at) <= max) {
                return true;
            }
        }
        return false;
    }

    /** Client -> serveur : "envoie-moi l'etat". */
    public record Request() {

        public void encode(FriendlyByteBuf buffer) {
        }

        public static Request decode(FriendlyByteBuf buffer) {
            return new Request();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                INTERESTED.put(player.getUUID(), Util.getMillis());
                if (PacketRate.allow(player, PacketRate.ROBOTICS)) {
                    PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(snapshot(player.serverLevel())));
                }
            }
        }
    }

    /** Serveur -> client : l'etat. */
    public record Sync(CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(tag);
        }

        public static Sync decode(FriendlyByteBuf buffer) {
            //0.20.46 (audit) : serveur -> client, sans le plafond de 2 Mio (128 ordres complets + hubs)
            CompoundTag tag = buffer.readAnySizeNbt();
            return new Sync(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            CompoundTag data = tag;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.receive(data));
        }
    }

    /** Client -> serveur : enregistrer, supprimer ou (de)mettre en pause un ordre. */
    public record Edit(int action, CompoundTag order) {

        public static final int SAVE = 0, DELETE = 1, TOGGLE_PAUSE = 2;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(action);
            buffer.writeNbt(order);
        }

        public static Edit decode(FriendlyByteBuf buffer) {
            int action = buffer.readVarInt();
            CompoundTag tag = buffer.readNbt();
            return new Edit(action, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            ServerLevel level = player.serverLevel();
            RoboticsOrders orders = RoboticsOrders.get(level);
            RoboticOrder incoming = RoboticOrder.load(order);
            //0.20.46 (audit) : un ordre existant (remplace, supprime, mis en pause) doit etre a portee du joueur
            RoboticOrder existing = incoming.id > 0 ? orders.get(incoming.id) : null;
            if (existing != null && !mayEdit(player, existing)) {
                return;
            }
            switch (action) {
                case SAVE -> {
                    if (!near(player, incoming)) {
                        return;
                    }
                    incoming.status = RoboticOrder.Status.WAITING;
                    if (orders.put(incoming)) {
                        RoboticsScheduler.forget(level, incoming.id);
                    }
                }
                case DELETE -> {
                    orders.remove(incoming.id);
                    RoboticsScheduler.forget(level, incoming.id);
                }
                case TOGGLE_PAUSE -> {
                    if (existing != null) {
                        existing.paused = !existing.paused;
                        orders.setDirty();
                    }
                }
                default -> {
                    return;
                }
            }
            changed(level, player);
        }

        private static boolean near(ServerPlayer player, RoboticOrder order) {
            return close(player, order.min) && close(player, order.max) && close(player, order.source) && close(player, order.target)
                   && close(player, order.hub);
        }

        private static boolean close(ServerPlayer player, Object point) {
            BlockPos pos = point instanceof Endpoint endpoint ? endpoint.pos() : point instanceof BlockPos block ? block : null;
            return pos == null || pos.distSqr(player.blockPosition()) <= (double) MAX_DISTANCE * MAX_DISTANCE;
        }
    }
}
