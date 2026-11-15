package fr.pioneers.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

/**
 * 1.9 (MAJ RP, bloc 3) : choix d'une replique de *Parler*, fonction PURE (profil du joueur, villageois, jour, hasard) : le registre vient du
 * palier d'affinite (insulte, mepris, froid, banalites, chaleur, lieux) ; un dicton du peuple un *Parler* sur quatre au palier 2 ou plus
 * (des le palier 1 pour le Niais) ; l'evenement du village, ses services et la vocation du joueur colorent parfois la replique. Jamais une des
 * trois dernieres.
 */
public final class Dialogue {

    private Dialogue() {
    }

    /** Ce qui decide d'une replique. {@code lines} lit une liste de cles (« people/register » ou « common/section/sub »). */
    public record Context(String people, int affinity, boolean niais, @Nullable String event, Set<String> services, @Nullable String vocation,
                         Collection<String> recent, Function<String, List<String>> lines, Function<String, List<String>> dictons) {
    }

    /** Registre du palier d'affinite. */
    public static String register(int affinity) {
        if (affinity <= -60) {
            return "insult";
        }
        if (affinity <= -20) {
            return "contempt";
        }
        if (affinity < 0) {
            return "cold";
        }
        if (affinity < 40) {
            return "smalltalk";
        }
        if (affinity < 60) {
            return "warm";
        }
        return "places";
    }

    /** Palier d'affinite (1 Gestes .. 5 Confidences ; 0 Reserve ; -1 Meprisant ; -2 Hostile). */
    public static int trust(int affinity) {
        if (affinity <= -60) {
            return -2;
        }
        if (affinity <= -20) {
            return -1;
        }
        if (affinity < 0) {
            return 0;
        }
        return Math.min(5, 1 + affinity / 20);
    }

    public static String pick(Context c, RandomSource random) {
        List<String> pool = new ArrayList<>();
        int a = c.affinity();
        if (a >= 0) {
            //colorations rares : evenement, services, vocation
            if (c.event() != null && random.nextInt(5) == 0) {
                pool.addAll(c.lines().apply("common/events/" + c.event()));
            } else if (!c.services().isEmpty() && random.nextInt(6) == 0) {
                for (String s : c.services()) {
                    pool.addAll(c.lines().apply("common/services/" + s));
                }
            } else if (c.vocation() != null && random.nextInt(10) == 0) {
                pool.addAll(c.lines().apply("common/vocation/" + c.vocation()));
            }
        }
        if (pool.isEmpty() && (c.niais() ? a >= 0 : a >= 20) && random.nextInt(4) == 0) {
            pool.addAll(c.dictons().apply(c.people()));
        }
        if (pool.isEmpty() && c.niais() && a >= -19) {
            pool.addAll(c.lines().apply("common/niais"));
        }
        if (pool.isEmpty()) {
            String reg = register(a);
            pool.addAll(c.lines().apply(c.people() + "/" + reg));
            if (a >= 80) {
                pool.addAll(c.lines().apply(c.people() + "/warm"));
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(c.lines().apply(c.people() + "/smalltalk"));
        }
        if (pool.isEmpty()) {
            return "";
        }
        List<String> fresh = new ArrayList<>(pool);
        fresh.removeAll(c.recent());
        List<String> from = fresh.isEmpty() ? pool : fresh;
        return from.get(random.nextInt(from.size()));
    }
}
