package fr.pioneers.space.client;

import fr.pioneers.core.network.PacketCargoMap;
import java.util.List;

/**
 * Derniere copie (cote client) du reseau logistique, pour la carte.
 */
public final class ClientCargoMap {

    private ClientCargoMap() {
    }

    private static List<PacketCargoMap.StationInfo> stations = List.of();
    private static List<PacketCargoMap.FlightInfo> flights = List.of();
    private static long receivedAt;

    public static void update(PacketCargoMap.Data data) {
        stations = data.stations();
        flights = data.flights();
        receivedAt = System.currentTimeMillis();
    }

    public static List<PacketCargoMap.StationInfo> getStations() {
        return stations;
    }

    public static List<PacketCargoMap.FlightInfo> getFlights() {
        return flights;
    }

    /** Secondes ecoulees depuis la derniere mise a jour (pour extrapoler la position des fusees). */
    public static float age() {
        return (System.currentTimeMillis() - receivedAt) / 1000F;
    }
}
