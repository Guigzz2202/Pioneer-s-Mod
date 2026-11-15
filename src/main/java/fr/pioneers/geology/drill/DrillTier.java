package fr.pioneers.geology.drill;

/**
 * Niveaux de foreuse : chacun ouvre les gisements de son niveau et en dessous. Seule la foreuse a vapeur (niveau 1) existe pour l'instant ;
 * les suivantes (electrique...) viendront avec leurs ages.
 */
public enum DrillTier {
    STEAM(1, fr.pioneers.bronze.steam.SteamRules.DRILL_TICKS, fr.pioneers.bronze.steam.SteamRules.DRILL_USE);

    public final int level;
    /** 1.0.2-beta (lot 3, plus de pression) : ticks par minerai et vapeur par tick (debit fixe). */
    public final int ticksPerOre;
    public final double steamPerTick;

    DrillTier(int level, int ticksPerOre, double steamPerTick) {
        this.level = level;
        this.ticksPerOre = ticksPerOre;
        this.steamPerTick = steamPerTick;
    }
}
