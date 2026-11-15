package fr.pioneers.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 1.0.3-beta (lot 2, « progression du guide fiable a 100 % ») : la PROCHAINE ETAPE du Periple, calcul pur (sans le jeu), commun au guide
 * ({@code client/guide/GuideProgress}), a l'affichage a l'ecran ({@code client/guide/JourneyHud}), aux GameTests et au controle hors jeu
 * ({@code tools/guide/check_guide.py}, fonction {@code journey_next} : meme regle).
 * <ul>
 *     <li>les etapes sont prises dans l'ordre du Periple, tous ages confondus ({@link JourneySteps#ALL}) ;</li>
 *     <li>prochaine etape = la premiere etape non faite ;</li>
 *     <li>sauf une etape non faite que le joueur a nettement depassee : une etape au moins {@link #PASS} places plus loin est faite (il a pris
 *     un autre chemin) ; elle reste « A faire » dans le guide mais n'est plus montree comme la prochaine.</li>
 * </ul>
 * Sur le chemin reel de la partie (simulation de {@code check_progression --chemin}), les etapes se font dans l'ordre du Periple a moins de
 * {@link #PASS} places pres : la prochaine etape ne saute jamais une etape et ne revient jamais en arriere (controle bloquant).
 */
public final class JourneyProgress {

    /** Une etape non faite est depassee quand une etape au moins PASS places plus loin est faite. */
    public static final int PASS = 5;

    private JourneyProgress() {
    }

    /** Indice de la derniere etape faite (-1 : aucune). */
    public static int lastDone(int count, IntPredicate done) {
        for (int i = count - 1; i >= 0; i--) {
            if (done.test(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Indice de la prochaine etape parmi {@code count} etapes, ou -1 si toutes sont faites ou depassees. */
    public static int next(int count, IntPredicate done) {
        int floor = lastDone(count, done) - PASS + 1;
        for (int i = Math.max(0, floor); i < count; i++) {
            if (!done.test(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Les {@code max} prochaines etapes (indices), dans l'ordre : la prochaine, puis les suivantes non faites. */
    public static List<Integer> upcoming(int count, IntPredicate done, int max) {
        List<Integer> out = new ArrayList<>();
        int first = next(count, done);
        for (int i = first; i >= 0 && i < count && out.size() < max; i++) {
            if (!done.test(i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** Vrai si l'etape {@code i}, non faite, est depassee (une etape PASS places plus loin est faite). */
    public static boolean passed(int count, IntPredicate done, int i) {
        return !done.test(i) && i <= lastDone(count, done) - PASS;
    }
}
