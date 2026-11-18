package fr.pioneers.space.cargo;

import fr.pioneers.space.LaunchVisualEntity;
import fr.pioneers.space.tile.TileEntityLaunchPad;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Le reseau logistique interplanetaire : tous les complexes de lancement (stations) et toutes les fusees logistiques en vol.
 * Un complexe en mode Reception accepte les fusees de n'importe quelle soute (objets, fluides, gaz, donnees).
 * <p>
 * Une fusee qui arrive attend en orbite tant que son spatioport de destination n'est pas charge (pensez au chunkloader)
 * ou qu'il est occupe par une autre fusee.
 */
public class CargoNetwork extends SavedData {

    private static final String NAME = "pioneers_cargo";

    public static class Station {

        public final UUID id;
        public ResourceKey<Level> dimension;
        public BlockPos pos;
        public String name;
        public boolean receiver;

        public Station(UUID id, ResourceKey<Level> dimension, BlockPos pos, String name, boolean receiver) {
            this.id = id;
            this.dimension = dimension;
            this.pos = pos;
            this.name = name;
            this.receiver = receiver;
        }
    }

    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private final List<CargoFlight> flights = new ArrayList<>();

    public static CargoNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CargoNetwork::load, CargoNetwork::new, NAME);
    }

    // ------------------------------------------------------------------
    // Spatioports
    // ------------------------------------------------------------------

    public void updateStation(UUID id, ResourceKey<Level> dimension, BlockPos pos, String name, boolean receiver) {
        Station station = stations.get(id);
        if (station == null) {
            stations.put(id, new Station(id, dimension, pos.immutable(), name, receiver));
            setDirty();
        } else if (station.dimension != dimension || !station.pos.equals(pos) || !station.name.equals(name) || station.receiver != receiver) {
            station.dimension = dimension;
            station.pos = pos.immutable();
            station.name = name;
            station.receiver = receiver;
            setDirty();
        }
    }

    public void removeStation(UUID id) {
        if (stations.remove(id) != null) {
            setDirty();
        }
    }

    @Nullable
    public Station getStation(@Nullable UUID id) {
        return id == null ? null : stations.get(id);
    }

    public List<Station> getStations() {
        return new ArrayList<>(stations.values());
    }

    /** Destinations possibles pour un expediteur : complexes en reception, tries par nom. */
    public List<Station> receiversFor(UUID exclude) {
        List<Station> list = new ArrayList<>();
        for (Station station : stations.values()) {
            if (station.receiver && !station.id.equals(exclude)) {
                list.add(station);
            }
        }
        list.sort(Comparator.comparing(station -> station.name));
        return list;
    }

    /** Nombre de spatioports deja existants sur un astre (pour les noms par defaut). */
    public int countOn(ResourceKey<Level> dimension) {
        int count = 0;
        for (Station station : stations.values()) {
            if (station.dimension == dimension) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Vols
    // ------------------------------------------------------------------

    public void addFlight(CargoFlight flight) {
        flights.add(flight);
        setDirty();
    }

    public List<CargoFlight> getFlights() {
        return flights;
    }

    /** Vols de/vers un spatioport (pour l'interface). */
    public int countFlightsOf(UUID home) {
        int count = 0;
        for (CargoFlight flight : flights) {
            if (flight.home.equals(home)) {
                count++;
            }
        }
        return count;
    }

    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<CargoFlight> iterator = flights.iterator();
        while (iterator.hasNext()) {
            CargoFlight flight = iterator.next();
            if (flight.state == CargoFlight.State.TRANSIT) {
                if (now < flight.arrive) {
                    continue;
                }
                Station target = stations.get(flight.to);
                if (target == null) {
                    //Destination detruite : demi-tour (ou perte si c'etait deja le retour)
                    if (!redirectHome(flight, now)) {
                        iterator.remove();
                    }
                    setDirty();
                    continue;
                }
                TileEntityLaunchPad pad = loadedPad(server, target);
                if (pad != null && pad.canAcceptLanding(flight)) {
                    pad.beginLanding(flight);
                    flight.state = CargoFlight.State.LANDING;
                    flight.landingDone = now + LaunchVisualEntity.DESCENT_TICKS;
                    setDirty();
                }
            } else if (now >= flight.landingDone) {
                Station target = stations.get(flight.to);
                TileEntityLaunchPad pad = target == null ? null : loadedPad(server, target);
                if (pad != null) {
                    pad.completeLanding(flight);
                    iterator.remove();
                } else {
                    //Le spatioport a disparu pendant la descente : la fusee remonte en orbite
                    flight.state = CargoFlight.State.TRANSIT;
                    if (target == null && !redirectHome(flight, now)) {
                        iterator.remove();
                    }
                }
                setDirty();
            }
        }
    }

    private boolean redirectHome(CargoFlight flight, long now) {
        Station home = stations.get(flight.home);
        Station from = stations.get(flight.from);
        if (flight.returning || home == null) {
            return false;
        }
        flight.to = home.id;
        flight.returning = true;
        flight.arrive = now + CargoRoutes.transitTicks(from == null ? home.dimension : from.dimension, home.dimension);
        return true;
    }

    @Nullable
    private static TileEntityLaunchPad loadedPad(MinecraftServer server, Station station) {
        ServerLevel level = server.getLevel(station.dimension);
        if (level == null || !level.isLoaded(station.pos)) {
            return null;
        }
        return level.getBlockEntity(station.pos) instanceof TileEntityLaunchPad pad ? pad : null;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    private static CargoNetwork load(CompoundTag tag) {
        CargoNetwork network = new CargoNetwork();
        for (Tag entry : tag.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) entry;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s.getString("dim")));
            Station station = new Station(s.getUUID("id"), dimension, BlockPos.of(s.getLong("pos")), s.getString("name"), s.getBoolean("receiver"));
            network.stations.put(station.id, station);
        }
        for (Tag entry : tag.getList("flights", Tag.TAG_COMPOUND)) {
            network.flights.add(CargoFlight.load((CompoundTag) entry));
        }
        return network;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Station station : stations.values()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("id", station.id);
            s.putString("dim", station.dimension.location().toString());
            s.putLong("pos", station.pos.asLong());
            s.putString("name", station.name);
            s.putBoolean("receiver", station.receiver);
            list.add(s);
        }
        tag.put("stations", list);
        ListTag flightList = new ListTag();
        for (CargoFlight flight : flights) {
            flightList.add(flight.save());
        }
        tag.put("flights", flightList);
        return tag;
    }
}
