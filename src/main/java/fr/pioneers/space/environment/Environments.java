package fr.pioneers.space.environment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import fr.pioneers.api.environment.BodyEnvironment.Hazard;
import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Environnement de chaque dimension : gravite, air, combinaison exigee, azote disponible, temperature.
 * <p>
 * Valeurs par defaut ci-dessous ; chaque dimension peut etre redefinie par un datapack (ou KubeJS) avec un fichier
 * {@code data/<namespace>/pioneers/environments/<nom>.json} :
 * <pre>
 * {"dimension": "pioneers:moon", "gravity": 0.17, "breathable": false, "suit_tier": 1, "hazard": "vacuum", "nitrogen": 0, "temperature": -20}
 * </pre>
 * La gravite de jeu suit la gravite reelle ; pour les petits corps elle est ramenee a une valeur jouable
 * ({@code 0,08 + 0,4 x reelle} sous 0,13 g), en gardant l'ordre reel des astres.
 */
public final class Environments extends SimpleJsonResourceReloadListener {

    public static final Environments INSTANCE = new Environments();
    /** Station orbitale : microgravite et vide. */
    public static final BodyEnvironment ORBIT = new BodyEnvironment(0.08, false, 1, Hazard.VACUUM, 0, -100);

    private static final Map<ResourceKey<Level>, BodyEnvironment> OVERRIDES = new HashMap<>();

    private Environments() {
        super(new com.google.gson.Gson(), "pioneers/environments");
    }

    /** Gravite de jeu a partir de la gravite reelle (g terrestres). */
    public static double playableGravity(double real) {
        return real >= 0.13 ? real : 0.08 + 0.4 * real;
    }

    /** Valeurs par defaut d'un astre. */
    public static BodyEnvironment defaults(Body body) {
        double gravity = playableGravity(body.surfaceGravity());
        return switch (body) {
            case EARTH -> BodyEnvironment.EARTH;
            case MOON -> new BodyEnvironment(gravity, false, 1, Hazard.VACUUM, 0, -20);
            case MARS -> new BodyEnvironment(gravity, false, 2, Hazard.COLD, 0.5, -63);
            case PHOBOS, DEIMOS -> new BodyEnvironment(gravity, false, 2, Hazard.VACUUM, 0, -40);
            case VENUS -> new BodyEnvironment(gravity, false, 3, Hazard.ACID, 1, 464);
            case MERCURY -> new BodyEnvironment(gravity, false, 3, Hazard.HEAT, 0, 167);
            case IO, EUROPA -> new BodyEnvironment(gravity, false, 3, Hazard.RADIATION, 0, -150);
            case TITAN -> new BodyEnvironment(gravity, false, 3, Hazard.COLD, 6, -179);
            case TRITON -> new BodyEnvironment(gravity, false, 3, Hazard.COLD, 1, -235);
            case PLUTO -> new BodyEnvironment(gravity, false, 3, Hazard.COLD, 0.5, -229);
            case CERES, VESTA, PALLAS, HYGIEA -> new BodyEnvironment(gravity, false, 3, Hazard.VACUUM, 0, -105);
            default -> new BodyEnvironment(gravity, false, 3, Hazard.COLD, 0, -190);
        };
    }

    /**
     * Densite de l'atmosphere (Terre = 1) : freine la capsule et rend le parachute utile (a partir de 0,05).
     * Dimensions inconnues : comme la Terre.
     */
    public static double atmosphereDensity(ResourceKey<Level> dimension) {
        if (dimension == PiDimensions.ORBIT) {
            return 0;
        }
        Body body = SolarSystem.fromDimension(dimension);
        if (body == null) {
            return 1;
        }
        return switch (body) {
            case EARTH -> 1;
            case VENUS -> 3;
            case MARS -> 0.12;
            case TITAN -> 1.5;
            case TRITON, PLUTO -> 0.05;
            case JUPITER, SATURN, URANUS, NEPTUNE -> 2;
            case TOLIMAN_B -> 1.2;
            case PROXIMA_B -> 0.6;
            case TRAPPIST_1D -> 1.1;
            case TRAPPIST_1E -> 0.9;
            case TRAPPIST_1F -> 0.35;
            //1.0.7 (lot 11) : systemes lointains (T:au_dela/far_systems.py atmo)
            case POLTERGEIST -> 0.7;
            case PHOBETOR -> 0.2;
            case VAN_MAANEN_D -> 0.1;
            case GAIA_BH1_B -> 0.8;
            case GAIA_BH1_D -> 1.0;
            case FYLGJA, VAN_MAANEN_E, GAIA_BH1_E -> 1.0;
            default -> 0;
        };
    }

    /** Environnement d'une dimension (Terre pour les dimensions inconnues : Nether, End, autres mods). */
    public static BodyEnvironment get(Level level) {
        return get(level.dimension());
    }

    public static BodyEnvironment get(ResourceKey<Level> dimension) {
        BodyEnvironment override = OVERRIDES.get(dimension);
        if (override != null) {
            return override;
        }
        if (dimension == PiDimensions.ORBIT) {
            return ORBIT;
        }
        Body body = SolarSystem.fromDimension(dimension);
        return body == null ? BodyEnvironment.EARTH : DEFAULTS.computeIfAbsent(body, Environments::defaults);
    }

    /** Valeurs par defaut deja calculees (immuables), pour ne pas en recreer a chaque appel. */
    private static final Map<Body, BodyEnvironment> DEFAULTS = new ConcurrentHashMap<>();

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> files, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        OVERRIDES.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "environment");
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(GsonHelper.getAsString(json, "dimension")));
                BodyEnvironment base = get(dimension);
                OVERRIDES.put(dimension, new BodyEnvironment(
                      GsonHelper.getAsDouble(json, "gravity", base.gravity()),
                      GsonHelper.getAsBoolean(json, "breathable", base.breathable()),
                      GsonHelper.getAsInt(json, "suit_tier", base.suitTier()),
                      hazard(GsonHelper.getAsString(json, "hazard", base.hazard().name()), base.hazard()),
                      GsonHelper.getAsDouble(json, "nitrogen", base.nitrogen()),
                      GsonHelper.getAsInt(json, "temperature", base.temperature())));
            } catch (RuntimeException e) {
                Pioneers.LOGGER.error("Environnement invalide {} : {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private static Hazard hazard(String name, Hazard fallback) {
        try {
            return Hazard.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

}
