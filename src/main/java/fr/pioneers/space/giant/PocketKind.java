package fr.pioneers.space.giant;

import fr.pioneers.Pioneers;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.registries.PiItems;
import fr.pioneers.space.SolarSystem.Body;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Contenu des poches des geantes gazeuses : un gaz (mB) ou des cristaux (objets) pour les poches profondes.
 */
public enum PocketKind {
    HYDROGEN(0xE6F2FF, PiGasTypes.HYDROGEN, null),
    HELIUM_3(PiGasTypes.Colors.HELIUM_3, PiGasTypes.HELIUM_3, null),
    METHANE(PiGasTypes.Colors.METHANE, PiGasTypes.METHANE, null),
    AMMONIA(PiGasTypes.Colors.AMMONIA, PiGasTypes.AMMONIA, null),
    METALLIC_HYDROGEN(0xA8B8FF, null, () -> PiItems.METALLIC_HYDROGEN.get()),
    RAIN_DIAMOND(0x8CF2FF, null, () -> PiItems.RAIN_DIAMOND.get());

    public final int color;
    @Nullable
    private final Supplier<Gas> gas;
    @Nullable
    private final Supplier<Item> item;

    PocketKind(int color, @Nullable Supplier<Gas> gas, @Nullable Supplier<Item> item) {
        this.color = color;
        this.gas = gas;
        this.item = item;
    }

    public boolean isGas() {
        return gas != null;
    }

    @Nullable
    public Gas gas() {
        return gas == null ? null : gas.get();
    }

    /** Identifiant du gaz (meme id dans le registre de gaz du socle), null pour les cristaux. */
    @Nullable
    public ResourceLocation gasId() {
        if (gas == null) {
            return null;
        }
        return Pioneers.rl(name().toLowerCase(Locale.ROOT));
    }

    @Nullable
    public Item item() {
        return item == null ? null : item.get();
    }

    /** Bit du filtre de la baie de drones. */
    public int bit() {
        return 1 << ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "pocket.pioneers." + id();
    }

    /** Quantite d'une poche neuve (mB, ou objets). */
    public int rollAmount(RandomSource random, boolean rich) {
        int base = isGas() ? 4000 + random.nextInt(8001) : 4 + random.nextInt(9);
        return rich ? base * 3 : base;
    }

    /** Quantite qu'un drone emporte par voyage. */
    public int tripCapacity(boolean armored) {
        double scale = PiCommonConfig.get(PiCommonConfig.DRONE_CARGO, 1.0);
        return Math.max(1, (int) Math.round((isGas() ? armored ? 1500 : 1000 : armored ? 6 : 4) * scale));
    }

    /** Couches de l'atmosphere (altitudes de la generation des nuages). */
    public enum Layer {
        /** Brume superieure (au-dessus du pont des aerostats et du plafond nuageux). */
        HAZE(268, 345),
        /** Pont nuageux principal (bandes colorees). */
        DECK(170, 265),
        /** Couche des orages : poches les plus riches, foudre ; seuls les drones blindes y descendent. */
        STORM(75, 165);

        public final int minY, maxY;

        Layer(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        public static Layer at(double y) {
            return y >= HAZE.minY ? HAZE : y >= DECK.minY - 5 ? DECK : STORM;
        }

        public String getTranslationKey() {
            return "pocket.pioneers.layer." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Ce que l'on trouve dans une couche d'une geante gazeuse. */
    public static PocketKind roll(Body body, Layer layer, RandomSource random) {
        return roll(body, layer, random.nextFloat());
    }

    /** Tirage deterministe ({@code r} dans [0, 1[) : sert aussi a JEI pour calculer les chances. */
    public static PocketKind roll(Body body, Layer layer, float r) {
        return switch (layer) {
            case HAZE -> r < 0.75F ? HYDROGEN : switch (body) {
                case SATURN -> HELIUM_3;
                case URANUS -> METHANE;
                case NEPTUNE -> AMMONIA;
                default -> HYDROGEN;
            };
            case DECK -> switch (body) {
                case JUPITER -> r < 0.55F ? HYDROGEN : r < 0.85F ? AMMONIA : HELIUM_3;
                case SATURN -> r < 0.55F ? HELIUM_3 : r < 0.85F ? HYDROGEN : AMMONIA;
                case URANUS -> r < 0.6F ? METHANE : r < 0.85F ? HYDROGEN : AMMONIA;
                default -> r < 0.5F ? AMMONIA : r < 0.85F ? METHANE : HYDROGEN;
            };
            case STORM -> switch (body) {
                case JUPITER, SATURN -> r < 0.45F ? METALLIC_HYDROGEN : r < 0.75F ? HELIUM_3 : HYDROGEN;
                default -> r < 0.45F ? RAIN_DIAMOND : r < 0.75F ? METHANE : AMMONIA;
            };
        };
    }
}
