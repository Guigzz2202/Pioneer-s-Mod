package fr.pioneers.society.clan.vote;

import fr.pioneers.society.clan.core.ClanDefs.Majority;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.9 (MAJ RP, bloc 4) : arithmetique PURE des votes (banc) : quorum (part des membres actifs du corps electoral), majorites (sur les voix
 * exprimees ou sur le corps electoral actif), fermeture anticipee quand le resultat est acquis quoi que fassent les absents, ponderation
 * d'une faction (aucun groupe au-dessus d'une part donnee des voix), elections a un ou plusieurs sieges.
 */
public final class VoteMath {

    public enum Outcome {
        OPEN, PASSED, REJECTED
    }

    private static final double EPS = 1e-9;

    private VoteMath() {
    }

    /** Bulletins necessaires au quorum : au moins un. */
    public static int quorumNeeded(double quorum, int active) {
        return Math.max(1, (int) Math.ceil(quorum * active - EPS));
    }

    /** La majorite est-elle atteinte ? {@code base} = corps electoral actif (majorites « electorate » et unanimite). */
    public static boolean passes(Majority m, boolean electorateBase, double yes, double no, double base) {
        if (m == Majority.UNANIMITY) {
            return no <= EPS && base > 0 && yes >= base - EPS;
        }
        double total = electorateBase ? base : yes + no;
        if (total <= EPS) {
            return false;
        }
        double need = m.share * total;
        return m.strict ? yes > need + EPS : yes >= need - EPS;
    }

    /**
     * Etat d'un vote oui / non. {@code yes}, {@code no} : voix ponderees ; {@code ballots} : bulletins deposes (abstentions comprises, pour le
     * quorum) ; {@code eligible} : corps electoral ; {@code active} : electeurs actifs (connectes dans les 7 jours) ; {@code remaining} :
     * electeurs qui n'ont pas vote, en voix ponderees ; {@code deadline} : la duree est ecoulee.
     */
    public static Outcome evaluate(Majority m, boolean electorateBase, double yes, double no, int ballots, int eligible, int active,
                                   double remaining, double quorum, boolean deadline) {
        int need = quorumNeeded(quorum, Math.max(active, ballots));
        double activeBase = Math.max(active, 0);
        if (deadline) {
            return ballots >= need && passes(m, electorateBase, yes, no, activeBase) ? Outcome.PASSED : Outcome.REJECTED;
        }
        if (m == Majority.UNANIMITY && no > EPS) {
            return Outcome.REJECTED;
        }
        //acquis : meme si tous les absents votent contre (et que tous deviennent actifs)
        if (ballots >= need && passes(m, electorateBase, yes, no + remaining, electorateBase ? Math.max(activeBase, eligible) : 0)) {
            return Outcome.PASSED;
        }
        //perdu : meme si tous les absents votent pour (voix exprimees seulement : la base « electorate » attend l'echeance)
        if (!electorateBase && remaining <= EPS && !passes(m, false, yes, no, 0)) {
            return Outcome.REJECTED;
        }
        if (!electorateBase && !passes(m, false, yes + remaining, no, 0)) {
            return Outcome.REJECTED;
        }
        if (remaining <= EPS) {
            return ballots >= need && passes(m, electorateBase, yes, no, activeBase) ? Outcome.PASSED : Outcome.REJECTED;
        }
        return Outcome.OPEN;
    }

    /**
     * Poids de chaque electeur d'une faction : un joueur une voix, puis aucun groupe (clan) au-dessus de {@code cap} du TOTAL des voix (le
     * surplus d'un groupe plafonne est reparti entre les autres au prorata de leurs electeurs ; si le plafond est impossible a tenir, trop
     * peu de groupes, chacun pese autant). La part d'un groupe est partagee egalement entre ses electeurs ; somme des poids = nombre
     * d'electeurs. {@code cap} &lt;= 0 ou &gt;= 1 : tous a 1.
     */
    public static <K> Map<K, Double> weights(Map<K, String> groupOf, double cap) {
        Map<K, Double> out = new LinkedHashMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();
        groupOf.values().forEach(g -> sizes.merge(g, 1, Integer::sum));
        if (cap <= 0 || cap >= 1 || sizes.size() <= 1) {
            groupOf.keySet().forEach(k -> out.put(k, 1.0));
            return out;
        }
        int total = groupOf.size();
        Map<String, Double> share = new HashMap<>();
        if (sizes.size() * cap < 1 - EPS) {
            sizes.keySet().forEach(g -> share.put(g, 1.0 / sizes.size()));
        } else {
            java.util.Set<String> capped = new java.util.HashSet<>();
            for (int pass = 0; pass <= sizes.size(); pass++) {
                double free = 1 - cap * capped.size();
                int uncapped = 0;
                for (Map.Entry<String, Integer> e : sizes.entrySet()) {
                    if (!capped.contains(e.getKey())) {
                        uncapped += e.getValue();
                    }
                }
                boolean changed = false;
                for (Map.Entry<String, Integer> e : sizes.entrySet()) {
                    double s = capped.contains(e.getKey()) ? cap : free * e.getValue() / uncapped;
                    if (s > cap + EPS) {
                        capped.add(e.getKey());
                        changed = true;
                    }
                    share.put(e.getKey(), Math.min(cap, s));
                }
                if (!changed) {
                    break;
                }
            }
        }
        for (Map.Entry<K, String> e : groupOf.entrySet()) {
            String g = e.getValue();
            out.put(e.getKey(), share.get(g) * total / sizes.get(g));
        }
        return out;
    }

    /**
     * Gagnants d'une election a {@code seats} sieges : les plus hauts totaux (egalite : ordre d'anciennete {@code tieOrder}). Renvoie la liste
     * des gagnants quand le resultat est acquis (echeance, ou les places ne peuvent plus changer quoi que fassent les absents), sinon
     * {@code null}. Seuls les candidats qui ont recu une voix sont elus ; quorum non atteint a l'echeance : liste vide.
     */
    public static <K> List<K> elect(Map<K, Double> tally, int seats, List<K> tieOrder, int ballots, int active, double remaining, double quorum,
                                    boolean deadline) {
        List<K> sorted = new ArrayList<>(tally.keySet());
        sorted.removeIf(k -> tally.get(k) <= EPS);
        sorted.sort((a, b) -> {
            int c = Double.compare(tally.get(b), tally.get(a));
            if (c != 0) {
                return c;
            }
            int ia = tieOrder.indexOf(a), ib = tieOrder.indexOf(b);
            return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia, ib < 0 ? Integer.MAX_VALUE : ib);
        });
        int need = quorumNeeded(quorum, Math.max(active, ballots));
        List<K> winners = new ArrayList<>(sorted.subList(0, Math.min(seats, sorted.size())));
        if (deadline || remaining <= EPS) {
            return ballots >= need ? winners : new ArrayList<>();
        }
        if (ballots < need) {
            return null;
        }
        //places figees : le dernier elu garde son avance meme si tous les absents votent pour le premier battu (ou pour un nouveau venu)
        if (sorted.size() < seats) {
            return null;
        }
        double last = tally.get(sorted.get(seats - 1));
        double next = sorted.size() > seats ? tally.get(sorted.get(seats)) : 0;
        return last > next + remaining + EPS ? winners : null;
    }
}
