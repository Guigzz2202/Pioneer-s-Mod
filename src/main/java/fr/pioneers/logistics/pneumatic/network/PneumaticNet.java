package fr.pioneers.logistics.pneumatic.network;

import fr.pioneers.Pioneers;
import fr.pioneers.logistics.pneumatic.client.ClientPneumatic;
import fr.pioneers.core.network.PacketGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

/**
 * Canal reseau des tubes pneumatiques (separe du canal principal) : ecran d'une station (destinations joignables, distance, air), nom de
 * la station et demande de depart.
 */
public final class PneumaticNet {

    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Pioneers.rl("pneumatic"), () -> VERSION, VERSION::equals,
          VERSION::equals);
    /** Destinations envoyees au plus. */
    private static final int MAX_DESTINATIONS = 64;
    private static boolean registered;

    private PneumaticNet() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(StationData.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(StationData::encode).decoder(StationData::decode)
              .consumerMainThread(StationData::handle).add();
        CHANNEL.messageBuilder(StationEdit.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(StationEdit::encode).decoder(StationEdit::decode)
              .consumerMainThread(StationEdit::handle).add();
        CHANNEL.messageBuilder(Travel.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(Travel::encode).decoder(Travel::decode)
              .consumerMainThread(Travel::handle).add();
        CHANNEL.messageBuilder(HyperClick.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(HyperClick::encode).decoder(HyperClick::decode)
              .consumerMainThread(HyperClick::handle).add();
        CHANNEL.messageBuilder(HyperBreak.class, id, NetworkDirection.PLAY_TO_SERVER).encoder(HyperBreak::encode).decoder(HyperBreak::decode)
              .consumerMainThread(HyperBreak::handle).add();
    }

    /** Ouvre l'ecran d'une station chez le joueur : stations nommees joignables, triees par distance. */
    static void openStation(ServerPlayer player, TubeStationTile tile) {
        List<Destination> destinations = new ArrayList<>();
        for (TubeRoutes.Route route : TubeRoutes.explore(player.level(), tile.getBlockPos())) {
            if (!route.station().equals(tile.getBlockPos()) && player.level().getBlockEntity(route.station()) instanceof TubeStationTile other
                && !other.name().isEmpty()) {
                int blocks = route.blocks();
                destinations.add(new Destination(other.name(), route.station(), blocks, (int) Math.ceil(TubeStationTile.cost(blocks)),
                      tile.canAfford(blocks)));
            }
        }
        destinations.sort(Comparator.comparingInt(Destination::distance).thenComparing(Destination::name));
        if (destinations.size() > MAX_DESTINATIONS) {
            destinations = new ArrayList<>(destinations.subList(0, MAX_DESTINATIONS));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StationData(tile.getBlockPos(), tile.name(), destinations,
              (float) tile.air().getPressure(), (int) tile.air().getAir()));
    }

    /** Une destination : nom, position, longueur du trajet (blocs), air necessaire (L), assez d'air au depart. */
    public record Destination(String name, BlockPos pos, int distance, int cost, boolean affordable) {
    }

    /** Ecran d'une station, vers le client. */
    public record StationData(BlockPos pos, String name, List<Destination> destinations, float pressure, int air) {

        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(name, TubeStationTile.NAME_LENGTH * 4);
            buf.writeVarInt(destinations.size());
            for (Destination d : destinations) {
                buf.writeUtf(d.name(), TubeStationTile.NAME_LENGTH * 4);
                buf.writeBlockPos(d.pos());
                buf.writeVarInt(d.distance());
                buf.writeVarInt(d.cost());
                buf.writeBoolean(d.affordable());
            }
            buf.writeFloat(pressure);
            buf.writeVarInt(air);
        }

        static StationData decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readUtf(TubeStationTile.NAME_LENGTH * 4);
            int n = Math.min(MAX_DESTINATIONS, buf.readVarInt());
            List<Destination> destinations = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                destinations.add(new Destination(buf.readUtf(TubeStationTile.NAME_LENGTH * 4), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                      buf.readBoolean()));
            }
            return new StationData(pos, name, destinations, buf.readFloat(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPneumatic.openStation(this));
        }
    }

    /** Nouveau nom d'une station, vers le serveur. */
    public record StationEdit(BlockPos pos, String name) {

        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(name, TubeStationTile.NAME_LENGTH * 4);
        }

        static StationEdit decode(FriendlyByteBuf buf) {
            return new StationEdit(buf.readBlockPos(), buf.readUtf(TubeStationTile.NAME_LENGTH * 4));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            TubeStationTile tile = PacketGuard.tile(context.get().getSender(), pos, TubeStationTile.class);
            if (tile != null) {
                tile.rename(PacketGuard.sanitize(name, TubeStationTile.NAME_LENGTH));
            }
        }
    }

    /** Demande de depart de la station {@code pos} vers la station {@code target}, vers le serveur. */
    public record Travel(BlockPos pos, BlockPos target) {

        void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeBlockPos(target);
        }

        static Travel decode(FriendlyByteBuf buf) {
            return new Travel(buf.readBlockPos(), buf.readBlockPos());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            TubeStationTile tile = PacketGuard.tile(player, pos, TubeStationTile.class);
            if (tile != null && player != null) {
                TubeTravel.start(player, tile, target);
            }
        }
    }

    /** 0.20.39 : clic droit avec l'hypertube (ancre visee par le client, revue par le serveur) ou annulation (Maj), vers le serveur. */
    public record HyperClick(@Nullable HypertubeAnchor anchor, boolean cancel) {

        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(cancel);
            buf.writeBoolean(anchor != null);
            if (anchor != null) {
                anchor.write(buf);
            }
        }

        static HyperClick decode(FriendlyByteBuf buf) {
            boolean cancel = buf.readBoolean();
            return new HyperClick(buf.readBoolean() ? HypertubeAnchor.read(buf) : null, cancel);
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                HypertubeItem.click(player, anchor, cancel);
            }
        }
    }

    /** 0.20.39 : demontage d'un troncon d'hypertube vise, vers le serveur. */
    public record HyperBreak(int id) {

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        static HyperBreak decode(FriendlyByteBuf buf) {
            return new HyperBreak(buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                HypertubeItem.dismantle(player, id);
            }
        }
    }
}
