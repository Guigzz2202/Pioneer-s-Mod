package fr.pioneers.fauna;

import fr.pioneers.common.PiCommonConfig;

/**
 * 1.0.5-beta (lot 4) : reglages serveur de la faune (section {@code fauna} de la config commune), lus d'un coup. Valeurs par defaut =
 * {@link FaunaRules} (aussi quand le fichier n'est pas encore charge).
 */
public record FaunaSettings(boolean enabled, int period, double threshold, int playerDistance, int villageDistance, boolean lightAi,
                            int aiDistance, int[] caps, boolean wildBreeding, double wildBreedChance, int wildBreedDistance) {

    public static final FaunaSettings DEFAULT = new FaunaSettings(true, FaunaRules.PERIOD, FaunaRules.THRESHOLD, FaunaRules.PLAYER_DISTANCE,
          FaunaRules.VILLAGE_DISTANCE, true, FaunaRules.AI_DISTANCE, defaultCaps());

    /** Reglages du lot 4 (reproduction sauvage du lot 6 a ses valeurs par defaut). */
    public FaunaSettings(boolean enabled, int period, double threshold, int playerDistance, int villageDistance, boolean lightAi, int aiDistance,
          int[] caps) {
        this(enabled, period, threshold, playerDistance, villageDistance, lightAi, aiDistance, caps, true, FaunaRules.WILD_BREED_CHANCE,
              FaunaRules.WILD_BREED_DISTANCE);
    }

    /** Memes reglages, reproduction sauvage changee (GameTests, banc). */
    public FaunaSettings withWildBreeding(boolean on, double chance, int distance) {
        return new FaunaSettings(enabled, period, threshold, playerDistance, villageDistance, lightAi, aiDistance, caps, on, chance, distance);
    }

    static int[] defaultCaps() {
        int[] c = new int[FaunaGroup.COUNT];
        for (FaunaGroup g : FaunaGroup.ALL) {
            c[g.ordinal()] = g.defaultCap;
        }
        return c;
    }

    private static volatile FaunaSettings current = DEFAULT;

    /** Reglages en cours (relus chaque seconde par {@link FaunaEvents}). */
    public static FaunaSettings current() {
        return current;
    }

    static void refresh() {
        current = read();
    }

    public static FaunaSettings read() {
        int[] caps = new int[FaunaGroup.COUNT];
        for (FaunaGroup g : FaunaGroup.ALL) {
            caps[g.ordinal()] = PiCommonConfig.FAUNA_CAPS[g.ordinal()] == null ? g.defaultCap
                                                                                 : PiCommonConfig.get(PiCommonConfig.FAUNA_CAPS[g.ordinal()], g.defaultCap);
        }
        return new FaunaSettings(PiCommonConfig.get(PiCommonConfig.FAUNA_ENABLED, true), PiCommonConfig.get(PiCommonConfig.FAUNA_PERIOD, FaunaRules.PERIOD),
              PiCommonConfig.get(PiCommonConfig.FAUNA_THRESHOLD, FaunaRules.THRESHOLD),
              PiCommonConfig.get(PiCommonConfig.FAUNA_PLAYER_DISTANCE, FaunaRules.PLAYER_DISTANCE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_VILLAGE_DISTANCE, FaunaRules.VILLAGE_DISTANCE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_LIGHT_AI, true), PiCommonConfig.get(PiCommonConfig.FAUNA_AI_DISTANCE, FaunaRules.AI_DISTANCE),
              caps, PiCommonConfig.get(PiCommonConfig.FAUNA_WILD_BREEDING, true),
              PiCommonConfig.get(PiCommonConfig.FAUNA_WILD_BREED_CHANCE, FaunaRules.WILD_BREED_CHANCE),
              PiCommonConfig.get(PiCommonConfig.FAUNA_WILD_BREED_DISTANCE, FaunaRules.WILD_BREED_DISTANCE));
    }

    public int cap(FaunaGroup g) {
        return caps[g.ordinal()];
    }
}
