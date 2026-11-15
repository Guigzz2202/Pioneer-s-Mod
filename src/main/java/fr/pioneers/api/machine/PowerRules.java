package fr.pioneers.api.machine;

/**
 * API Machine (lot 1) : SEULE source Java des regles des tiers (decisions de Guillaume, 23/10/2026 ; parallelisme 1.10.1) : 1 024 FE/t
 * au MKI, x4 d'energie par tier pour x2 de vitesse ; multibloc : 80 % du temps et 75 % de l'energie par operation, generateurs x1,25,
 * parallelisme 4 / 8 / 16 / 32, temperature nominale des carcasses 800 / 1 100 / 1 500 / 2 100 K. {@code industry.Tiers} les lit ici.
 * Miroir Python : tools/gc/api/rules.py (tools/check_machine_api.py verifie que les deux disent la meme chose). 1.0.5-beta (lot 38) : tiers
 * globaux (duree ÷ 2 par tier d'ecart jusqu'au tier 0 mecanique, table de rendement R) ; miroir Python : rules.py global_duration / yield_factor.
 */
public final class PowerRules {

    public static final long BASE_POWER = 1_024;
    /** x4 d'energie par tier ({@link #power} : decalage de 2 bits par tier) et x2 de vitesse ({@link #speed} : 1 bit). */
    public static final int POWER_STEP = 4;
    public static final int SPEED_STEP = 2;
    public static final double INDUSTRIAL_TIME = 0.8;
    public static final double INDUSTRIAL_ENERGY = 0.75;
    public static final double INDUSTRIAL_YIELD = 1.25;
    private static final int[] PARALLEL = {4, 8, 16, 32};
    private static final int[] NOMINAL = {800, 1_100, 1_500, 2_100};

    private PowerRules() {
    }

    /** Consommation d'une machine du tier (FE/t) : 1 024 x 4^tier. */
    public static long power(int tier) {
        return BASE_POWER << (2 * tier);
    }

    /** Multiplicateur de vitesse d'un tier par rapport au tier de la recette (x2 par tier d'ecart). */
    public static int speed(int machineTier, int recipeTier) {
        return 1 << Math.max(0, machineTier - recipeTier);
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 38, specification age du bronze §2.2) : TIERS GLOBAUX (T0 mecanique, T1 bronze, T2 MKI .. T5 MKIV)
    // ------------------------------------------------------------------

    /** Table absolue des rendements R(tier global) : T0 1 · T1 1,5 · T2 1,75 · T3 2,1875 · T4 2,625 · T5 3,5 (source : tier_families.py). */
    public static final double[] GLOBAL_YIELD = fr.pioneers.api.machine.tier.GeneratedTierFamilies.YIELD;

    /** Tier global d'un tier MK (0 = MKI -> 2). */
    public static int global(int mkTier) {
        return mkTier + 2;
    }

    /** Duree (ticks) d'une recette de duree de base {@code time} et de tier minimal {@code minTier} a ce tier global : ÷ 2 par tier d'ecart. */
    public static int globalDuration(int time, int tier, int minTier) {
        return Math.max(1, time >> Math.max(0, Math.min(30, tier - minTier)));
    }

    /** Rendement d'une recette A RENDEMENT de tier minimal {@code minTier} faite au tier global {@code tier} : R(tier) / R(minTier), >= 1. */
    public static double yieldFactor(int tier, int minTier) {
        int t = Math.max(0, Math.min(GLOBAL_YIELD.length - 1, tier)), m = Math.max(0, Math.min(GLOBAL_YIELD.length - 1, minTier));
        return t <= m ? 1 : GLOBAL_YIELD[t] / GLOBAL_YIELD[m];
    }

    public static int parallelCap(int tier) {
        return PARALLEL[Math.max(0, Math.min(3, tier))];
    }

    public static int nominal(int tier) {
        return NOMINAL[Math.max(0, Math.min(3, tier))];
    }
}
