package fr.pioneers.tech.machine;

import fr.pioneers.logistics.transport.CableTier;

/**
 * Machines du kit de base (broyeur, four electrique, compresseur), chacune en quatre tiers (ceux des cables), sur la regle de l'age industriel : 1 024 FE/t au tier basique, x4 par
 * tier pour x2 de vitesse ; generateur : production x4 par tier, combustible brule 4 fois plus vite.
 */
public enum MachineKind {
    /** Broyeur (recettes {@code pioneers:crushing}). */
    CRUSHER("crusher", 1_024, 160),
    /**
     * Four electrique (recettes de cuisson vanilla et minerai des metaux de tier 1). 1.0.5-beta (lot 14, Guillaume : « Buffer les fours
     * electriques » = les renforcer) : 40 ticks au tier de base (avant 100) : MKI 2 s, MKII 1 s, MKIII 0,5 s, MKIV 0,25 s ; meme puissance par
     * tick, donc 2,5 fois moins d'energie par operation (MKI 40 960 FE au lieu de 102 400) ; le four a vapeur du bronze cuit en 100 ticks, le
     * four vanilla en 200. 1.0.5-beta (lot 21, Guillaume : « 4, 2, 1, 0,5 s ») : 80 ticks au tier de base : MKI 4 s, MKII 2 s, MKIII 1 s,
     * MKIV 0,5 s ; consommation dans la logique des tiers (x4 de puissance pour x2 de vitesse) : 1 024 / 4 096 / 16 384 / 65 536 FE/t, soit
     * 81 920 / 163 840 / 327 680 / 655 360 FE par operation (x2 par tier, comme toutes les machines).
     */
    FURNACE("furnace", 1_024, Rules.FURNACE_TICKS),
    /** Compresseur d'objets (recettes {@code pioneers:compressing}). */
    COMPRESSOR("compressor", 1_024, 200);

    /** 1.0.5-beta (lot 14) : chiffres lus aussi par les controles ({@code tools/check_p105_14.py}). */
    public static final class Rules {

        /** Four electrique : ticks d'une cuisson au tier de base (avant le lot 14 : 100 ; lot 14 : 40 ; lot 21 : 80 = 4 s au MKI). */
        public static final int FURNACE_TICKS = 80;

        private Rules() {
        }
    }

    public final String name;
    /** Tier de base : FE/t consommes (machines) ou produits (generateur). */
    private final int basePower;
    /** Tier de base : duree d'une operation en ticks. */
    private final int baseTicks;

    MachineKind(String name, int basePower, int baseTicks) {
        this.name = name;
        this.basePower = basePower;
        this.baseTicks = baseTicks;
    }

    public String id(CableTier tier) {
        return tier.id() + "_" + name;
    }

    /** 1.0 : plus de generateur dans le kit (generateur a combustion unique : machine d'atelier combustion_generator). */
    public boolean isGenerator() {
        return false;
    }

    /** Multiplicateur de vitesse du tier (1, 2, 4, 8). */
    public static int speed(CableTier tier) {
        return 1 << tier.ordinal();
    }

    /** Multiplicateur de consommation du tier (1, 4, 16, 64). */
    public static int cost(CableTier tier) {
        return 1 << 2 * tier.ordinal();
    }

    /** Machines : FE/t consommes ; generateur : FE/t produits (x4 par tier, combustible brule 4 fois plus vite : meme energie par combustible). */
    public long power(CableTier tier) {
        return (long) basePower * cost(tier);
    }

    /** Duree d'une operation (ticks). */
    public int ticks(CableTier tier) {
        return Math.max(1, baseTicks / speed(tier));
    }

    /** Generateur : ticks de combustion consommes par tick. */
    public int burnRate(CableTier tier) {
        return cost(tier);
    }

    /** Reserve interne (FE). */
    public long energyCapacity(CableTier tier) {
        return power(tier) * (isGenerator() ? 1_000 : 400);
    }
}
