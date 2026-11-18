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
    /** 1.0.7 : MKV (indice 4) : 64 operations, carcasses MKV a 3 000 K. */
    private static final int[] PARALLEL = {4, 8, 16, 32, 64};
    private static final int[] NOMINAL = {800, 1_100, 1_500, 2_100, 3_000};

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

    /** Table absolue des rendements R(tier global) : T0 1 · T1 1,5 · T2 1,75 · T3 2,1875 · T4 2,625 · T5 3,5 · T6 (MKV) 4,375 (source : tier_families.py). */
    public static final double[] GLOBAL_YIELD = fr.pioneers.api.machine.tier.GeneratedTierFamilies.YIELD;

    /** Tier global d'un tier MK (0 = MKI -> 2). */
    public static int global(int mkTier) {
        return mkTier + 2;
    }

    /** Duree (ticks) d'une recette de duree de base {@code time} et de tier minimal {@code minTier} a ce tier global : ÷ 2 par tier d'ecart. */
    public static int globalDuration(int time, int tier, int minTier) {
        return Math.max(Math.max(1, time >> Math.max(0, Math.min(30, tier - minTier))), Math.min(time, floorTicks(tier, minTier - 2)));
    }

    // ------------------------------------------------------------------
    // 1.0.6 (Guillaume, 18/11/2026 : « si une recette MKIV atteint 0,2 s alors on doit augmenter pour qu'elle atteigne 1 s ; cela se repercute
    // sur les tiers precedents ; les materiaux les plus avances tendent vers 2 ou 4 s pour les materiaux specifiques au MKIV » ; « aucune
    // recette ne depasse 8 s sauf justification, 20 s au plus ») : PLANCHER DES DUREES. Les durees de base des recettes JSON sont posees par
    // tools/gc/balance/recipe_times.py ; le plancher tient aussi les recettes faites en Java (bas tiers, cuisson, taille, concassage).
    // ------------------------------------------------------------------

    /** Duree minimale au MKIV (ticks) selon le tier de la recette (0 MKI .. 3 MKIV) : 1 s, 1 s, 2 s, 4 s. */
    public static final int[] MKIV_MIN = {20, 20, 40, 80};
    /**
     * 1.0.7 (brief « Plus loin et au-dela » §4) : duree minimale au MKV (tier global 6) selon le tier de la recette (0 MKI .. 4 MKV) : deux
     * fois plus vite qu'au MKIV, jamais sous 10 ticks (0,5 s) ; une recette MKV au MKV, 4 s (comme une recette MKIV au MKIV).
     */
    public static final int[] MKV_MIN = {10, 10, 20, 40, 80};
    /** Tier global du MKV. */
    public static final int MKV = 6;
    /** Machines dont la duree n'est pas celle d'une fabrication (production de vapeur ou de chaleur, croissance, fermentation...). */
    public static final java.util.Set<String> NO_FLOOR = java.util.Set.of("boiler", "cooling_tower", "hot_blast_stove", "exhaust_stack",
          "item_silo", "aquafarm", "hydroponic_farm", "tree_plantation", "fermenter", "spent_fuel_pool", "dryer", "coke_oven");

    /**
     * Plancher (ticks) d'une recette de tier industriel {@code recipeTier} (0..3), a tout tier global {@code globalTier} : aucune operation sous
     * {@link #MKIV_MIN}. La repercussion sur les tiers precedents est portee par les durees de base (recipe_times.py : 8 s au MKI pour une
     * recette MKI...) ; la vitesse x 2 par tier (regle des tiers) reste entiere jusqu'au plancher.
     */
    public static int floorTicks(int globalTier, int recipeTier) {
        if (globalTier >= MKV) {
            return MKV_MIN[Math.max(0, Math.min(4, recipeTier))];
        }
        return MKIV_MIN[Math.max(0, Math.min(3, recipeTier))];
    }

    /** Rendement d'une recette A RENDEMENT de tier minimal {@code minTier} faite au tier global {@code tier} : R(tier) / R(minTier), >= 1. */
    public static double yieldFactor(int tier, int minTier) {
        int t = Math.max(0, Math.min(GLOBAL_YIELD.length - 1, tier)), m = Math.max(0, Math.min(GLOBAL_YIELD.length - 1, minTier));
        return t <= m ? 1 : GLOBAL_YIELD[t] / GLOBAL_YIELD[m];
    }

    public static int parallelCap(int tier) {
        return PARALLEL[Math.max(0, Math.min(PARALLEL.length - 1, tier))];
    }

    public static int nominal(int tier) {
        return NOMINAL[Math.max(0, Math.min(NOMINAL.length - 1, tier))];
    }
}
