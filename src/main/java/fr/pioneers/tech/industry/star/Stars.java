package fr.pioneers.tech.industry.star;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (lot 12) : les MULTIBLOCS EXTRAORDINAIRES (★) : acces a leur table generee ({@link GeneratedStars}, source T:au_dela/multiblocs.py
 * STARS) et etape en cours d'une recette (les parts de sa duree : allumage, fusion, affinage...), lue par l'interface et le rendu.
 */
public final class Stars {

    private static final Map<String, GeneratedStars.Star> BY_KEY = new HashMap<>();
    /** Captures du jeu (J:space/sky/client/SkyShots, client) : rendu des extraordinaires montre en marche. */
    public static boolean forceActive;

    static {
        for (GeneratedStars.Star s : GeneratedStars.ALL) {
            BY_KEY.put(s.key(), s);
        }
    }

    private Stars() {
    }

    /** Fiche de l'extraordinaire de cette machine (cle d'IndustryMachine), ou null. */
    @Nullable
    public static GeneratedStars.Star of(String machineKey) {
        return BY_KEY.get(machineKey);
    }

    public static boolean has(String machineKey) {
        return BY_KEY.containsKey(machineKey);
    }

    /** Etape d'une recette a l'avancement {@code progress} (0..1). */
    public static int stage(GeneratedStars.Star s, double progress) {
        double acc = 0;
        for (int i = 0; i < s.stages().length; i++) {
            acc += s.stages()[i];
            if (progress < acc) {
                return i;
            }
        }
        return s.stages().length - 1;
    }

    /** Avancement dans l'etape en cours (0..1). */
    public static double inStage(GeneratedStars.Star s, double progress) {
        double acc = 0;
        for (int i = 0; i < s.stages().length; i++) {
            double share = s.stages()[i];
            if (progress < acc + share || i == s.stages().length - 1) {
                return share <= 0 ? 1 : Math.max(0, Math.min(1, (progress - acc) / share));
            }
            acc += share;
        }
        return 1;
    }
}
