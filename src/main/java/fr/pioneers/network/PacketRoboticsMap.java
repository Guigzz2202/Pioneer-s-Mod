package fr.pioneers.network;

import fr.pioneers.client.robotics.ClientRoboticsMap;
import fr.pioneers.robotics.RoboticsMapService;
import fr.pioneers.robotics.RoboticsOrders;
import fr.pioneers.robotics.RoboticsScheduler;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.hub.RoboticHubData;
import fr.pioneers.robotics.logistics.LogisticPoint;
import fr.pioneers.robotics.logistics.LogisticsRegistry;
import fr.pioneers.robotics.logistics.PointConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Carte robotique : ouverture et maintien (le serveur envoie alors l'etat du reseau), tuiles du terrain, reglages d'un point a
 * distance, et actions sur un hub (rappel).
 */
public final class PacketRoboticsMap {

    private PacketRoboticsMap() {
    }

    /** Distance maximale (blocs) pour agir a distance depuis la carte. */
    public static final int REMOTE = 512;

    private static boolean reachable(ServerPlayer player, BlockPos pos) {
        return pos.distSqr(player.blockPosition()) <= (double) REMOTE * REMOTE && player.serverLevel().isLoaded(pos);
    }

    /** Client -> serveur : carte ouverte (ou toujours ouverte), ou fermee. */
    public record Watch(boolean open) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(open);
        }

        public static Watch decode(FriendlyByteBuf buffer) {
            return new Watch(buffer.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                if (open) {
                    //0.20.46 (audit) : l'envoi immediat de l'etat au plus toutes les 250 ms (le tick l'envoie de toute facon)
                    RoboticsMapService.watch(player, PacketRate.allow(player, PacketRate.MAP_WATCH));
                    RoboticsMapService.keepAlive(player);
                    PacketRobotics.interested(player);
                } else {
                    RoboticsMapService.unwatch(player);
                }
            }
        }
    }

    /** Serveur -> client : etat du reseau autour du joueur. */
    public record State(CompoundTag tag) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeNbt(tag);
        }

        public static State decode(FriendlyByteBuf buffer) {
            //0.20.46 (audit) : serveur -> client, sans le plafond de 2 Mio (un gros reseau le depassait : deconnexion)
            CompoundTag tag = buffer.readAnySizeNbt();
            return new State(tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            CompoundTag data = tag;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoboticsMap.receive(data));
        }
    }

    /** Tuile du terrain : un chunk, couleur et hauteur de chaque colonne. */
    public record Tile(int cx, int cz, byte[] colors, short[] heights) {}

    /** Client -> serveur : tuiles voulues (positions de chunks). */
    public record TileRequest(long[] chunks) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeLongArray(chunks);
        }

        public static TileRequest decode(FriendlyByteBuf buffer) {
            return new TileRequest(buffer.readLongArray(new long[0], 256));
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                //0.20.46 (audit) : les tuiles sont calculees et envoyees par petits lots a chaque tick (file bornee par joueur)
                RoboticsMapService.queueTiles(player, chunks);
            }
        }
    }

    /** Serveur -> client : tuiles. */
    public record Tiles(List<Tile> tiles) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(tiles.size());
            for (Tile tile : tiles) {
                buffer.writeInt(tile.cx());
                buffer.writeInt(tile.cz());
                buffer.writeByteArray(tile.colors());
                for (short h : tile.heights()) {
                    buffer.writeShort(h);
                }
            }
        }

        public static Tiles decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<Tile> tiles = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int cx = buffer.readInt(), cz = buffer.readInt();
                byte[] colors = buffer.readByteArray(256);
                short[] heights = new short[256];
                for (int k = 0; k < 256; k++) {
                    heights[k] = buffer.readShort();
                }
                tiles.add(new Tile(cx, cz, colors, heights));
            }
            return new Tiles(tiles);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            List<Tile> data = tiles;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoboticsMap.receiveTiles(data));
        }
    }

    /** Client -> serveur : nouveaux reglages d'un point logistique (sur place ou depuis la carte). */
    public record PointEdit(BlockPos pos, CompoundTag config) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeNbt(config);
        }

        public static PointEdit decode(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            CompoundTag tag = buffer.readNbt();
            return new PointEdit(pos, tag == null ? new CompoundTag() : tag);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || player.isSpectator() || !reachable(player, pos) || !player.serverLevel().mayInteract(player, pos)) {
                return;
            }
            if (player.serverLevel().getBlockEntity(pos) instanceof LogisticPoint point) {
                point.setConfig(PointConfig.load(config));
            }
        }
    }

    /** Client -> serveur : action sur un hub depuis la carte. */
    public record HubAction(BlockPos center, int action) {

        public static final int RECALL = 0, DISMISS_ALERT = 1, DISMISS_ALL = 2, REPAIRS_ON = 3, REPAIRS_OFF = 4, PASSIVE = 1000;

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(center);
            buffer.writeVarInt(action);
        }

        public static HubAction decode(FriendlyByteBuf buffer) {
            return new HubAction(buffer.readBlockPos(), buffer.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || player.isSpectator() || !reachable(player, center)) {
                return;
            }
            if (action >= PASSIVE && action < PASSIVE + UnitKind.values().length * 100) {
                //Reseau passif : PASSIVE + genre * 100 + limite
                int kind = (action - PASSIVE) / 100, limit = (action - PASSIVE) % 100;
                RoboticsOrders.get(player.serverLevel()).setPassiveLimit(UnitKind.values()[kind], limit);
                return;
            }
            if (action == REPAIRS_ON || action == REPAIRS_OFF) {
                RoboticsOrders.get(player.serverLevel()).setRepairs(action == REPAIRS_ON);
                return;
            }
            if (action == DISMISS_ALERT || action == DISMISS_ALL) {
                RoboticsScheduler.dismiss(player.serverLevel(), action == DISMISS_ALL ? null : center);
                return;
            }
            for (RoboticHubData hub : LogisticsRegistry.get(player.serverLevel())
                  .hubs(player.serverLevel().getGameTime())) {
                if (hub.getCenter().equals(center) && action == RECALL) {
                    hub.recallAll();
                }
            }
        }
    }
}
