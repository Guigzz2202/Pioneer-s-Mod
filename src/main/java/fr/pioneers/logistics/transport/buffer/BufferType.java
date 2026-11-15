package fr.pioneers.logistics.transport.buffer;

import fr.pioneers.logistics.transport.CableTier;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import java.util.Locale;

/**
 * Familles de buffers et leur capacite par tier (1.0 : relevees avec les cables, x4 a chaque tier) ; buffers creatifs (contenu infini) :
 * {@code creative_<famille>_buffer}. Anciennes valeurs :
 * <ul>
 *     <li>objets : 9 / 18 / 36 / 54 cases ;</li>
 *     <li>fluides : 16 000 / 64 000 / 256 000 / 1 024 000 mB ;</li>
 *     <li>gaz : 32 000 / 128 000 / 512 000 / 2 048 000 mB ;</li>
 *     <li>chaleur : capacite 1 000 / 4 000 / 16 000 / 64 000 J/K, conductance 10 / 25 / 60 / 150 J/t/K, pertes a l'air
 *     0,05 / 0,01 / 0,002 / 0,0004 J/t/K ;</li>
 *     <li>energie : 1 / 4 / 16 / 64 millions de FE ;</li>
 *     <li>pression (reservoirs d'air) : volume 2 000 / 8 000 / 32 000 / 128 000 L, pression maximale 5 / 10 / 20 / 40 bar (celle des tuyaux
 *     du meme tier), conductance 50 / 120 / 300 / 750 L/t/bar.</li>
 * </ul>
 * La donnee a deja ses banques de donnees (pas de buffer de donnees ici).
 */
public enum BufferType {
    ITEM(new long[]{18, 27, 45, 54}),
    FLUID(new long[]{64_000, 256_000, 1_024_000, 4_096_000}),
    GAS(new long[]{128_000, 512_000, 2_048_000, 8_192_000}),
    HEAT(new long[]{4_000, 16_000, 64_000, 256_000}),
    ENERGY(new long[]{16_000_000, 128_000_000, 1_024_000_000, 8_192_000_000L}),
    PRESSURE(new long[]{4_000, 16_000, 64_000, 256_000});

    private final long[] capacity;
    /**
     * 1.12.3 (lot F3, demande de Guillaume) : une case d'un buffer d'objets garde plusieurs piles normales du meme objet : x2 (MKI), x4
     * (MKII), x6 (MKIII), x8 (MKIV) ; un objet non empilable reste a 1 par case. Buffer creatif : {@link #CREATIVE_STACK_MULTIPLIER}.
     */
    private static final int[] STACK_MULTIPLIER = {2, 4, 6, 8};
    /** 1.0.3-beta : jusqu'a ~2,1 milliards d'objets par case (64 x ce multiplicateur tient dans un int). */
    public static final int CREATIVE_STACK_MULTIPLIER = Integer.MAX_VALUE / 64;
    /**
     * 1.0.3-beta (correctif, Guillaume : « les buffers creatifs doivent avoir une capacite de plusieurs milliards de debit, seuls les cables
     * brident le debit ») : contenance des buffers creatifs (avant : celle du tier ultime). Fluides : la limite d'un reservoir Forge (int,
     * ~2,1 milliards de mB) ; gaz et energie : 1 000 milliards ; chaleur : 1 000 milliards de J/K ; air : 1 milliard de litres. Leur
     * conductance (chaleur, air) est enorme : l'echange prend la plus faible des deux, celle du cable ou de la machine voisine.
     */
    private static final long[] CREATIVE_CAPACITY = {54, Integer.MAX_VALUE, 1_000_000_000_000L, 1_000_000_000_000L, 1_000_000_000_000L,
                                                     1_000_000_000L};
    public static final double CREATIVE_CONDUCTANCE = 1.0E12;
    private static final double[] HEAT_CONDUCTANCE = {20, 50, 120, 300};
    private static final double[] HEAT_LOSS = {0.05, 0.01, 0.002, 0.0004};
    private static final double[] PRESSURE_CONDUCTANCE = {100, 240, 600, 1_500};

    BufferType(long[] capacity) {
        this.capacity = capacity;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Identifiant du bloc : {@code <tier>_<famille>_buffer}. */
    public String bufferId(CableTier tier) {
        return tier.id() + "_" + id() + "_buffer";
    }

    public double pressureConductance(CableTier tier) {
        return PRESSURE_CONDUCTANCE[tier.ordinal()];
    }

    public double pressureConductance(CableTier tier, boolean creative) {
        return creative ? CREATIVE_CONDUCTANCE : pressureConductance(tier);
    }

    public double maxPressure(CableTier tier) {
        return CableLevel.maxPressure(tier);
    }

    /** Cases (objets), mB (fluides, gaz), J/K (chaleur), FE (energie) ou litres (pression : volume du reservoir). */
    public long capacity(CableTier tier) {
        return capacity[tier.ordinal()];
    }

    /** Contenance d'un buffer, creatif ou non. */
    public long capacity(CableTier tier, boolean creative) {
        return creative ? CREATIVE_CAPACITY[ordinal()] : capacity(tier);
    }

    /** Multiplicateur de pile des cases (objets seulement ; 1 pour les autres familles). */
    public int stackMultiplier(CableTier tier, boolean creative) {
        if (this != ITEM) {
            return 1;
        }
        return creative ? CREATIVE_STACK_MULTIPLIER : STACK_MULTIPLIER[tier.ordinal()];
    }

    public double heatConductance(CableTier tier) {
        return HEAT_CONDUCTANCE[tier.ordinal()];
    }

    public double heatConductance(CableTier tier, boolean creative) {
        return creative ? CREATIVE_CONDUCTANCE : heatConductance(tier);
    }

    public double heatLoss(CableTier tier) {
        return HEAT_LOSS[tier.ordinal()];
    }
}
