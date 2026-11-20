package fr.pioneers.space.cargo;

import fr.pioneers.space.mega.MegaEvents;
import fr.pioneers.space.mega.MegaWorld;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.module.RocketCargo;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.item.ItemStack;

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
 * <p>
 * 1.0.7 : les CHANTIERS DE MEGASTRUCTURE ouverts depuis la carte ({@link #openMegaSite}) sont des destinations du reseau, sans spatioport :
 * une fusee qui y arrive livre ce qui manque au chantier ({@link #deliverToMegaSite}) et repart chez elle avec le reste. Le chantier
 * termine (megastructure debloquee) quitte le reseau : les fusees en route font demi-tour.
 */
public class CargoNetwork extends SavedData {

    private static final String NAME = "pioneers_cargo";

    public static class Station {

        public final UUID id;
        public ResourceKey<Level> dimension;
        public BlockPos pos;
        public String name;
        public boolean receiver;
        /** 1.0.7 : chantier de la megastructure de cet index (-1 : spatioport ordinaire). Nom : {@code @mega:<id>} (traduit a l'affichage). */
        public int mega = -1;

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

    /** 1.0.7 : identifiant fixe du chantier d'une megastructure dans le reseau. */
    public static UUID megaSiteId(int i) {
        return UUID.nameUUIDFromBytes(("pioneers:mega_site:" + Megastructures.id(i)).getBytes(StandardCharsets.UTF_8));
    }

    /** 1.0.7 : nom d'un chantier de megastructure (traduit par l'affichage, {@code CargoNames}). */
    public static String megaSiteName(int i) {
        return "@mega:" + Megastructures.id(i);
    }

    /** 1.0.7 : ouvre le chantier de la megastructure comme destination des fusees cargo (rien si deja ouvert). */
    public void openMegaSite(int i) {
        UUID id = megaSiteId(i);
        if (!stations.containsKey(id)) {
            Station station = new Station(id, Level.OVERWORLD, BlockPos.ZERO, megaSiteName(i), true);
            station.mega = i;
            stations.put(id, station);
            setDirty();
        }
    }

    /** 1.0.7 : chantier termine (ou remis a zero) : il quitte le reseau, les fusees en route rentrent chez elles. */
    public void closeMegaSite(int i) {
        removeStation(megaSiteId(i));
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
                if (target.mega >= 0) {
                    //1.0.7 : chantier de megastructure (en orbite) : livraison, puis retour avec le reste de la cargaison
                    deliverToMegaSite(server, flight, target);
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
        //le point de depart du retour : la ou la fusee devait arriver (chantier, spatioport detruit), sinon son depart
        Station from = stations.get(flight.to);
        if (from == null) {
            from = stations.get(flight.from);
        }
        if (flight.returning || home == null) {
            return false;
        }
        flight.from = from == null ? home.id : from.id;
        flight.to = home.id;
        flight.returning = true;
        flight.depart = now;
        flight.arrive = now + CargoRoutes.transitTicks(from == null ? CargoRoutes.bodyOf(home) : CargoRoutes.bodyOf(from), CargoRoutes.bodyOf(home));
        return true;
    }

    /**
     * 1.0.7 : une fusee cargo arrive au chantier d'une megastructure : ses soutes a objets livrent ce qui manque (piles prises en tout ou en
     * partie) ; le reste (objets en trop, fluides, gaz, donnees) repart avec elle.
     */
    public static int deliverToMegaSite(MinecraftServer server, CargoFlight flight, Station site) {
        MegaWorld world = MegaWorld.get(server);
        RocketCargo cargo = RocketCargo.read(flight.cargo);
        int taken = 0;
        for (ItemStack stack : cargo.getItems()) {
            if (!stack.isEmpty()) {
                int n = world.accept(server, site.mega, stack, false);
                if (n > 0) {
                    stack.shrink(n);
                    taken += n;
                }
            }
        }
        flight.cargo = cargo.save();
        if (taken > 0) {
            MegaEvents.delivered(server, site.mega, taken);
        }
        return taken;
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
            if (s.contains("mega")) {
                station.mega = Megastructures.byId(s.getString("mega"));
                if (station.mega < 0) {
                    //megastructure disparue : le chantier n'est plus une destination
                    continue;
                }
            }
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
            if (station.mega >= 0) {
                s.putString("mega", Megastructures.id(station.mega));
            }
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
