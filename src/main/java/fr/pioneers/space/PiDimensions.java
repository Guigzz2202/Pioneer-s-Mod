package fr.pioneers.space;

import fr.pioneers.Pioneers;
import fr.pioneers.space.environment.Environments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Dimensions du mod. La Lune est definie par le datapack (data/pioneers/dimension/moon.json).
 */
public final class PiDimensions {

    private PiDimensions() {
    }

    public static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("moon"));
    public static final ResourceKey<Level> MARS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("mars"));
    public static final ResourceKey<Level> VENUS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("venus"));
    public static final ResourceKey<Level> MERCURY = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("mercury"));
    public static final ResourceKey<Level> PHOBOS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("phobos"));
    public static final ResourceKey<Level> DEIMOS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("deimos"));
    public static final ResourceKey<Level> CERES = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("ceres"));
    public static final ResourceKey<Level> VESTA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("vesta"));
    public static final ResourceKey<Level> PALLAS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("pallas"));
    public static final ResourceKey<Level> HYGIEA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("hygiea"));
    public static final ResourceKey<Level> IO = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("io"));
    public static final ResourceKey<Level> EUROPA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("europa"));
    public static final ResourceKey<Level> GANYMEDE = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("ganymede"));
    public static final ResourceKey<Level> CALLISTO = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("callisto"));
    public static final ResourceKey<Level> ENCELADUS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("enceladus"));
    public static final ResourceKey<Level> TITAN = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("titan"));
    public static final ResourceKey<Level> MIRANDA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("miranda"));
    public static final ResourceKey<Level> TITANIA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("titania"));
    public static final ResourceKey<Level> TRITON = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("triton"));
    public static final ResourceKey<Level> PLUTO = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("pluto"));
    public static final ResourceKey<Level> CHARON = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("charon"));
    //Geantes gazeuses : la haute atmosphere, sans sol
    public static final ResourceKey<Level> JUPITER = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("jupiter"));
    public static final ResourceKey<Level> SATURN = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("saturn"));
    public static final ResourceKey<Level> URANUS = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("uranus"));
    public static final ResourceKey<Level> NEPTUNE = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("neptune"));
    //Alpha du Centaure
    public static final ResourceKey<Level> TOLIMAN_B = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("toliman_b"));
    public static final ResourceKey<Level> PROXIMA_B = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("proxima_b"));
    public static final ResourceKey<Level> PROXIMA_D = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("proxima_d"));
    //TRAPPIST-1
    public static final ResourceKey<Level> TRAPPIST_1B = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("trappist_1b"));
    public static final ResourceKey<Level> TRAPPIST_1D = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("trappist_1d"));
    public static final ResourceKey<Level> TRAPPIST_1E = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("trappist_1e"));
    public static final ResourceKey<Level> TRAPPIST_1F = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("trappist_1f"));
    //1.0.7 (lot 11) : planetes des trois systemes lointains
    public static final ResourceKey<Level> DRAUGR = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("draugr"));
    public static final ResourceKey<Level> POLTERGEIST = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("poltergeist"));
    public static final ResourceKey<Level> PHOBETOR = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("phobetor"));
    public static final ResourceKey<Level> VAN_MAANEN_B = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("van_maanen_b"));
    public static final ResourceKey<Level> VAN_MAANEN_C = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("van_maanen_c"));
    public static final ResourceKey<Level> VAN_MAANEN_D = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("van_maanen_d"));
    public static final ResourceKey<Level> GAIA_BH1_B = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("gaia_bh1_b"));
    public static final ResourceKey<Level> GAIA_BH1_C = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("gaia_bh1_c"));
    public static final ResourceKey<Level> GAIA_BH1_D = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("gaia_bh1_d"));
    //1.0.7 : mondes habitables des systemes lointains (relief et biomes de la Terre)
    public static final ResourceKey<Level> FYLGJA = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("fylgja"));
    public static final ResourceKey<Level> VAN_MAANEN_E = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("van_maanen_e"));
    public static final ResourceKey<Level> GAIA_BH1_E = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("gaia_bh1_e"));
    /** Orbite : les stations orbitales de tous les astres (une region par astre). */
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, Pioneers.rl("orbit"));

    /** Gravite relative (1 = Terre), selon l'environnement de la dimension (voir {@link Environments}). */
    public static double gravity(Level level) {
        return Environments.get(level).gravity();
    }

    /** Haute atmosphere d'une geante gazeuse (pas de sol). */
    public static boolean isGasGiant(ResourceKey<Level> dimension) {
        return dimension == JUPITER || dimension == SATURN || dimension == URANUS || dimension == NEPTUNE;
    }

    public static boolean isGasGiant(Level level) {
        return isGasGiant(level.dimension());
    }

    public static boolean isMoon(Level level) {
        return level.dimension() == MOON;
    }

}
