package fr.pioneers.core.network;

import fr.pioneers.space.client.ClientCargoMap;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.cargo.CargoFlight;
import fr.pioneers.space.cargo.CargoNetwork;
import fr.pioneers.space.cargo.CargoRoutes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Carte logistique : le client demande, le serveur repond avec les spatioports et les vols en cours.
 */
public final class PacketCargoMap {

    private PacketCargoMap() {
    }

    public record StationInfo(int body, String name, int type, boolean receiver) {
    }

    public record FlightInfo(int fromBody, int toBody, int type, float progress, boolean waiting, boolean returning, boolean landing,
                             String fromName, String toName, int secondsLeft) {
    }

    /** Client -> serveur : "envoie-moi l'etat du reseau". */
    public record Request() {

        public void encode(FriendlyByteBuf buffer) {
        }

        public static Request decode(FriendlyByteBuf buffer) {
            return new Request();
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            //0.20.46 (audit) : au plus une demande toutes les 250 ms par joueur
            if (player == null || !PacketRate.allow(player, PacketRate.CARGO_MAP)) {
                return;
            }
            CargoNetwork network = CargoNetwork.get(player.server);
            long now = player.server.overworld().getGameTime();
            List<StationInfo> stations = new ArrayList<>();
            for (CargoNetwork.Station station : network.getStations()) {
                Body body = CargoRoutes.bodyOf(station);
                if (body != null) {
                    stations.add(new StationInfo(body.ordinal(), station.name, 0, station.receiver));
                }
            }
            List<FlightInfo> flights = new ArrayList<>();
            for (CargoFlight flight : network.getFlights()) {
                CargoNetwork.Station from = network.getStation(flight.from), to = network.getStation(flight.to);
                if (from == null || to == null) {
                    continue;
                }
                Body a = CargoRoutes.bodyOf(from), b = CargoRoutes.bodyOf(to);
                if (a == null || b == null) {
                    continue;
                }
                float progress = flight.arrive <= flight.depart ? 1 : Math.min(1, (now - flight.depart) / (float) (flight.arrive - flight.depart));
                boolean waiting = flight.state == CargoFlight.State.TRANSIT && now >= flight.arrive;
                int secondsLeft = (int) Math.max(0, (flight.arrive - now) / 20);
                flights.add(new FlightInfo(a.ordinal(), b.ordinal(), flight.type.ordinal(), progress, waiting, flight.returning,
                      flight.state == CargoFlight.State.LANDING, from.name, to.name, secondsLeft));
            }
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Data(stations, flights));
        }
    }

    /** Serveur -> client : l'etat du reseau. */
    public record Data(List<StationInfo> stations, List<FlightInfo> flights) {

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(stations.size());
            for (StationInfo station : stations) {
                buffer.writeVarInt(station.body());
                buffer.writeUtf(station.name(), 32);
                buffer.writeVarInt(station.type());
                buffer.writeBoolean(station.receiver());
            }
            buffer.writeVarInt(flights.size());
            for (FlightInfo flight : flights) {
                buffer.writeVarInt(flight.fromBody());
                buffer.writeVarInt(flight.toBody());
                buffer.writeVarInt(flight.type());
                buffer.writeFloat(flight.progress());
                buffer.writeBoolean(flight.waiting());
                buffer.writeBoolean(flight.returning());
                buffer.writeBoolean(flight.landing());
                buffer.writeUtf(flight.fromName(), 32);
                buffer.writeUtf(flight.toName(), 32);
                buffer.writeVarInt(flight.secondsLeft());
            }
        }

        public static Data decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<StationInfo> stations = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                stations.add(new StationInfo(buffer.readVarInt(), buffer.readUtf(32), buffer.readVarInt(), buffer.readBoolean()));
            }
            count = buffer.readVarInt();
            List<FlightInfo> flights = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                flights.add(new FlightInfo(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readBoolean(),
                      buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32), buffer.readUtf(32), buffer.readVarInt()));
            }
            return new Data(stations, flights);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            Data data = this;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                  () -> () -> ClientCargoMap.update(data));
        }
    }
}
