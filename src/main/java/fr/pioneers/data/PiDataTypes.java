package fr.pioneers.data;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ProbeDestination;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Registre des types de donnees : les douze types des sondes, un par origine (orbite terrestre, Lune, espace profond, Soleil, planetes
 * et Pluton), et les donnees d'analyse (geologie et biologie de chaque astre etudie, composition des asteroides).
 */
public final class PiDataTypes {

    private PiDataTypes() {
    }

    public static final DeferredRegister<DataType> DATA_TYPES = DeferredRegister.create(PioneersAPI.DATA_TYPE_REGISTRY_NAME, Pioneers.MODID);

    public static final Supplier<IForgeRegistry<DataType>> REGISTRY = DATA_TYPES.makeRegistry(() -> new RegistryBuilder<DataType>()
          .setDefaultKey(Pioneers.rl("empty")));

    public static final RegistryObject<DataType> EMPTY = DATA_TYPES.register("empty", () -> DataType.EMPTY);
    /** 0.17.2 : telemetrie des centres de donnees (proportionnelle au calcul servi) ; sert a entrainer les reseaux de neurones. */
    public static final RegistryObject<DataType> TELEMETRY = DATA_TYPES.register("telemetry", () -> new DataType(0x50DCA0));

    private static final Map<ProbeDestination, RegistryObject<DataType>> BY_ORIGIN = new EnumMap<>(ProbeDestination.class);

    /** Donnees d'analyse : geologie et biologie de chaque astre etudie, composition des quatre familles d'asteroides. */
    private static final Map<Body, RegistryObject<DataType>> GEOLOGICAL = new EnumMap<>(Body.class);
    private static final Map<Body, RegistryObject<DataType>> BIOLOGICAL = new EnumMap<>(Body.class);
    public static final String[] ASTEROID_TYPES = {"rock", "iron", "ice", "volcanic"};
    private static final int[] ASTEROID_COLORS = {0x8C8070, 0xB0703C, 0x8FD3F0, 0xD0502A};
    private static final List<RegistryObject<DataType>> ASTEROIDS = new ArrayList<>();
    /** Donnees de biome (analyseur de biomes), une par biome d'astre du mod. */
    private static final Map<String, RegistryObject<DataType>> BIOMES = new LinkedHashMap<>();

    static {
        for (ProbeDestination origin : ProbeDestination.values()) {
            BY_ORIGIN.put(origin, DATA_TYPES.register(id(origin), () -> new DataType(origin.color)));
        }
        for (List<Body> chapter : BodyResearch.CHAPTERS) {
            for (Body body : chapter) {
                String name = body.name().toLowerCase(Locale.ROOT);
                GEOLOGICAL.put(body, DATA_TYPES.register("geological_" + name, () -> new DataType(mix(body.color, 0x6B4A2E, 0.35F))));
                BIOLOGICAL.put(body, DATA_TYPES.register("biological_" + name, () -> new DataType(mix(body.color, 0x3FD46A, 0.55F))));
            }
        }
        for (int i = 0; i < ASTEROID_TYPES.length; i++) {
            int color = ASTEROID_COLORS[i];
            ASTEROIDS.add(DATA_TYPES.register("asteroid_" + ASTEROID_TYPES[i], () -> new DataType(color)));
        }
        for (Map.Entry<String, Body> biome : SpaceBiomes.BODIES.entrySet()) {
            int color = mix(biome.getValue().color, 0x5FD0FF, 0.3F);
            BIOMES.put(biome.getKey(), DATA_TYPES.register("biome_" + biome.getKey(), () -> new DataType(color)));
        }
    }

    /** Donnees d'un biome d'astre ({@code pioneers:lunar_maria}), null pour un autre biome. */
    @Nullable
    public static DataType biome(String biome) {
        String prefix = "pioneers:";
        RegistryObject<DataType> type = biome.startsWith(prefix) ? BIOMES.get(biome.substring(prefix.length())) : null;
        return type == null ? null : type.get();
    }

    /** Biome de donnees de biome ({@code pioneers:lunar_maria}), null si ce n'en sont pas. */
    @Nullable
    public static String biomeOf(DataType type) {
        for (Map.Entry<String, RegistryObject<DataType>> entry : BIOMES.entrySet()) {
            if (entry.getValue().isPresent() && entry.getValue().get() == type) {
                return "pioneers:" + entry.getKey();
            }
        }
        return null;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Donnees geologiques d'un astre (null s'il n'est pas etudie). */
    @Nullable
    public static DataType geological(Body body) {
        RegistryObject<DataType> type = GEOLOGICAL.get(body);
        return type == null ? null : type.get();
    }

    /** Donnees biologiques d'un astre (null s'il n'est pas etudie). */
    @Nullable
    public static DataType biological(Body body) {
        RegistryObject<DataType> type = BIOLOGICAL.get(body);
        return type == null ? null : type.get();
    }

    /** Donnees de composition d'une famille d'asteroides (0 roche, 1 metal, 2 glace, 3 volcanique). */
    public static DataType asteroid(int type) {
        return ASTEROIDS.get(type).get();
    }

    /** Astre des donnees geologiques (null si ce n'en sont pas). */
    @Nullable
    public static Body geologicalBody(DataType type) {
        return find(GEOLOGICAL, type);
    }

    /** Astre des donnees biologiques (null si ce n'en sont pas). */
    @Nullable
    public static Body biologicalBody(DataType type) {
        return find(BIOLOGICAL, type);
    }

    /** Famille d'asteroides des donnees (-1 si ce n'en sont pas). */
    public static int asteroidType(DataType type) {
        for (int i = 0; i < ASTEROIDS.size(); i++) {
            if (ASTEROIDS.get(i).isPresent() && ASTEROIDS.get(i).get() == type) {
                return i;
            }
        }
        return -1;
    }

    /** Donnees produites par une machine d'analyse (geologie, biologie, asteroides). */
    public static boolean isAnalysis(DataType type) {
        return geologicalBody(type) != null || biologicalBody(type) != null || asteroidType(type) >= 0 || biomeOf(type) != null;
    }

    @Nullable
    private static Body find(Map<Body, RegistryObject<DataType>> map, DataType type) {
        for (Map.Entry<Body, RegistryObject<DataType>> entry : map.entrySet()) {
            if (entry.getValue().isPresent() && entry.getValue().get() == type) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Identifiant du type de donnees d'une origine : {@code lunar}, {@code martian}... */
    public static String id(ProbeDestination origin) {
        return switch (origin) {
            case EARTH_ORBIT -> "orbital";
            case MOON -> "lunar";
            case DEEP_SPACE -> "sidereal";
            case MARS -> "martian";
            case VENUS -> "venusian";
            case MERCURY -> "mercurian";
            case SUN -> "solar";
            case JUPITER -> "jovian";
            case SATURN -> "saturnian";
            case URANUS -> "uranian";
            case NEPTUNE -> "neptunian";
            case PLUTO -> "plutonian";
            case CENTAURI -> "centaurian";
            case TRAPPIST -> "trappist";
        };
    }

    public static DataType of(ProbeDestination origin) {
        return BY_ORIGIN.get(origin).get();
    }

    /** Origine d'un type de donnees du mod (null pour un type ajoute par un autre mod). */
    @Nullable
    public static ProbeDestination origin(DataType type) {
        for (Map.Entry<ProbeDestination, RegistryObject<DataType>> entry : BY_ORIGIN.entrySet()) {
            if (entry.getValue().isPresent() && entry.getValue().get() == type) {
                return entry.getKey();
            }
        }
        return null;
    }
}
