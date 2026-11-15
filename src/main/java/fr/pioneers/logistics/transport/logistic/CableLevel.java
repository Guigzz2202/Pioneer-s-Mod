package fr.pioneers.logistics.transport.logistic;

import fr.pioneers.logistics.transport.CableTier;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (cable logistique, spec §1.2 a §1.4) : niveau d'un cable. Le niveau fixe les types permis, les debits (par canal) et les limites
 * physiques ; tous les niveaux logistiques se raccordent entre eux et le reseau prend le plus faible. Le supraconducteur ({@link #SUPER}) ne
 * se raccorde qu'a lui-meme (energie seule, reseau a part).
 * <p>
 * Debits par type (ordre de {@link ChannelKind}) : objets/s, mB/t, mB/t, FE/t, conductance J/t/K, conductance L/t/bar, Mo/t ; 0 = refuse.
 * Pure (sans Minecraft) : utilisee par les bancs hors jeu.
 */
public enum CableLevel {
    //                 objets  fluides  gaz      energie        chaleur pression donnees    ; C(J/K) pertes  V(L) pmax fuites ; T max fluide
    WOODEN("wooden", new long[]{4, 160, 0, 0, 0, 0, 0}, 0, 0, 0, 0, 0, 400, false),
    BRONZE("bronze", new long[]{12, 400, 640, 0, 32, 20, 0}, 30, 1.0, 30, 3, 0.1, 1_473, false),
    BASIC("basic", new long[]{32, 1_000, 2_000, 16_384, 64, 40, 2_000}, 50, 0.5, 50, 5, 0.05, 2_500, false),
    ADVANCED("advanced", new long[]{128, 4_000, 8_000, 131_072, 256, 120, 16_000}, 100, 0.1, 100, 10, 0.02, Double.MAX_VALUE, true),
    ELITE("elite", new long[]{512, 16_000, 32_000, 1_048_576, 1_024, 360, 128_000}, 200, 0.02, 200, 20, 0.01, Double.MAX_VALUE, true),
    ULTIMATE("ultimate", new long[]{2_048, 64_000, 128_000, 8_388_608, 4_096, 1_080, 1_024_000}, 400, 0.004, 400, 40, 0.005, Double.MAX_VALUE, true),
    /** Supraconducteur : energie seule, x128 du MKIV, sans perte (2^30 FE par appel : borne int de IEnergyStorage respectee). */
    SUPER("superconducting", new long[]{0, 0, 0, 1_073_741_824L, 0, 0, 0}, 0, 0, 0, 0, 0, 0, false);

    private static final CableLevel[] VALUES = values();

    private final String id;
    private final long[] rates;
    public final double heatCapacity, heatLoss, airVolume, maxPressure, airLeak;
    /** Temperature maximale d'un fluide (K) ; aussi la temperature au-dela de laquelle le corps d'un canal de chaleur n'accepte plus rien. */
    public final double maxTemperature;
    /** Gaine PTFE : corrosifs et cryogeniques acceptes (MKII et mieux). */
    public final boolean lined;

    CableLevel(String id, long[] rates, double heatCapacity, double heatLoss, double airVolume, double maxPressure, double airLeak,
          double maxTemperature, boolean lined) {
        this.id = id;
        this.rates = rates;
        this.heatCapacity = heatCapacity;
        this.heatLoss = heatLoss;
        this.airVolume = airVolume;
        this.maxPressure = maxPressure;
        this.airLeak = airLeak;
        this.maxTemperature = maxTemperature;
        this.lined = lined;
    }

    public String id() {
        return id;
    }

    /** Identifiant du bloc : {@code <niveau>_logistic_cable[_clear]}, {@code superconducting_cable}. */
    public String blockId(boolean clear) {
        return this == SUPER ? "superconducting_cable" : id + "_logistic_cable" + (clear ? "_clear" : "");
    }

    /** Debit du niveau pour ce type (0 : type refuse). */
    public long rate(ChannelKind kind) {
        return rates[kind.ordinal()];
    }

    public boolean permits(ChannelKind kind) {
        return rates[kind.ordinal()] > 0;
    }

    /** Masque des types permis (bits = ordinaux de {@link ChannelKind}). */
    public int permittedMask() {
        int mask = 0;
        for (ChannelKind kind : ChannelKind.all()) {
            if (permits(kind)) {
                mask |= kind.bit();
            }
        }
        return mask;
    }

    /** Debit par tick (objets : fraction d'objet). */
    public double perTick(ChannelKind kind) {
        return kind.perSecond() ? rates[kind.ordinal()] / 20.0 : rates[kind.ordinal()];
    }

    /**
     * Filtre physique commun (spec §1.4) : jamais de fuite ni d'explosion, un refus simple. {@code temperature} en K (fluides ; ignoree pour
     * les autres types), {@code lining} : corrosif ou cryogenique (gaine PTFE requise).
     */
    public boolean accepts(ChannelKind kind, double temperature, boolean lining) {
        if (!permits(kind)) {
            return false;
        }
        if (kind == ChannelKind.FLUID && temperature > maxTemperature) {
            return false;
        }
        return !lining || lined || kind == ChannelKind.ITEM || kind == ChannelKind.ENERGY || kind == ChannelKind.DATA;
    }

    /** Tier general des machines pour les quatre MK (null : bois, bronze, supraconducteur). */
    @Nullable
    public CableTier tier() {
        return switch (this) {
            case BASIC -> CableTier.BASIC;
            case ADVANCED -> CableTier.ADVANCED;
            case ELITE -> CableTier.ELITE;
            case ULTIMATE -> CableTier.ULTIMATE;
            default -> null;
        };
    }

    /** Niveau qui suit la pression maximale de l'ancien {@code CableType.MAX_PRESSURE} (MKI..MKIV : 5 / 10 / 20 / 40 bar). */
    public static double maxPressure(CableTier tier) {
        return byTier(tier).maxPressure;
    }

    public static CableLevel byTier(CableTier tier) {
        return switch (tier) {
            case BASIC -> BASIC;
            case ADVANCED -> ADVANCED;
            case ELITE -> ELITE;
            case ULTIMATE -> ULTIMATE;
        };
    }

    /** Les deux niveaux se raccordent-ils (logistique entre eux, supraconducteur avec lui-meme) ? */
    public boolean joins(CableLevel other) {
        return (this == SUPER) == (other == SUPER);
    }

    /** Le plus faible des deux (le reseau prend le niveau de son cable le plus faible). */
    public static CableLevel weakest(CableLevel a, CableLevel b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    public static CableLevel byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    public static CableLevel byId(String id) {
        for (CableLevel level : VALUES) {
            if (level.id.equals(id.toLowerCase(Locale.ROOT))) {
                return level;
            }
        }
        return BASIC;
    }

    public static CableLevel[] all() {
        return VALUES;
    }
}
