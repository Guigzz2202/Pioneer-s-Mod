package fr.pioneers.logistics.warehouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * 1.0.5-beta (lot 17, retour de Guillaume : « Maj + clic sur une recette JEI = demande des ingredients au reseau ») : DEMANDE D'UNE RECETTE
 * au comptoir, calcul PUR (sans Minecraft : les cles sont des {@link StockKey} au jeu, des chaines au banc). Une recette = des LIGNES : les
 * objets acceptes par une case (alternatives : un tag de planches, de lingots...) et le nombre par fabrication. Le PLAN, pour {@code times}
 * fabrications et ce qui est DISPONIBLE (stock reel de l'entrepot moins la file et le panier), dit ce qu'on prend de chaque alternative
 * (les plus fournies d'abord, plusieurs si une seule ne suffit pas) et ce qui MANQUE. Lignes aux alternatives identiques fusionnees
 * (huit cases de pierre = une ligne de 8) ; lignes aux alternatives les moins nombreuses servies d'abord (une case qui n'accepte que du
 * chene passe avant une case « toutes planches »). Le meme calcul sert au client (cases en rouge dans JEI, nombre manquant) et au serveur
 * (source de verite : il refait le plan sur son stock).
 */
public final class RecipeAsk {

    /** Bornes du paquet : lignes, alternatives par ligne, fabrications, objets par fabrication et par ligne. */
    public static final int MAX_LINES = 16, MAX_ALTERNATIVES = 16, MAX_TIMES = 64, MAX_PER_CRAFT = 64;

    private RecipeAsk() {
    }

    /** Une ligne de la recette : objets acceptes (dans l'ordre de la recette), nombre par fabrication. */
    public record Line<K>(List<K> alternatives, int perCraft) {}

    /** Ce qu'on prend d'une alternative. */
    public record Take<K>(K key, long count) {}

    /**
     * Plan : pour chaque ligne (meme ordre que les lignes donnees), ce qu'on prend et ce qui manque ; {@code times} fabrications.
     */
    public record Plan<K>(List<Line<K>> lines, List<List<Take<K>>> takes, long[] missing, int times) {

        public boolean complete() {
            for (long m : missing) {
                if (m > 0) {
                    return false;
                }
            }
            return true;
        }

        /** Objets manquants en tout. */
        public long missingTotal() {
            long n = 0;
            for (long m : missing) {
                n += m;
            }
            return n;
        }

        /** Lignes qui manquent (au moins un objet). */
        public int missingLines() {
            int n = 0;
            for (long m : missing) {
                n += m > 0 ? 1 : 0;
            }
            return n;
        }

        /** Objets pris en tout. */
        public long taken() {
            long n = 0;
            for (List<Take<K>> l : takes) {
                for (Take<K> t : l) {
                    n += t.count();
                }
            }
            return n;
        }

        /** Ce qu'on prend, par cle (toutes lignes confondues). */
        public Map<K, Long> byKey() {
            Map<K, Long> out = new java.util.LinkedHashMap<>();
            for (List<Take<K>> l : takes) {
                for (Take<K> t : l) {
                    out.merge(t.key(), t.count(), Long::sum);
                }
            }
            return out;
        }
    }

    /**
     * Lignes nettoyees : alternatives sans doublon et bornees ({@link #MAX_ALTERNATIVES}), lignes vides retirees, lignes aux memes
     * alternatives fusionnees (nombres additionnes, bornes a {@link #MAX_PER_CRAFT}), au plus {@link #MAX_LINES} lignes.
     */
    public static <K> List<Line<K>> merge(List<Line<K>> lines) {
        Map<Set<K>, Integer> at = new HashMap<>();
        List<Line<K>> out = new ArrayList<>();
        for (Line<K> l : lines) {
            if (l == null || l.alternatives() == null || l.perCraft() <= 0) {
                continue;
            }
            LinkedHashSet<K> alts = new LinkedHashSet<>();
            for (K k : l.alternatives()) {
                if (k != null && alts.size() < MAX_ALTERNATIVES) {
                    alts.add(k);
                }
            }
            if (alts.isEmpty()) {
                continue;
            }
            Integer i = at.get(alts);
            if (i != null) {
                Line<K> old = out.get(i);
                out.set(i, new Line<>(old.alternatives(), Math.min(MAX_PER_CRAFT, old.perCraft() + l.perCraft())));
            } else if (out.size() < MAX_LINES) {
                at.put(alts, out.size());
                out.add(new Line<>(List.copyOf(alts), Math.min(MAX_PER_CRAFT, l.perCraft())));
            }
        }
        return out;
    }

    /**
     * Plan pour {@code times} fabrications ({@link #merge} d'abord si les lignes viennent de dehors). {@code available} : ce qu'on peut encore
     * prendre de chaque cle (lu une fois par cle).
     */
    public static <K> Plan<K> plan(List<Line<K>> lines, int times, ToLongFunction<K> available) {
        int t = Math.max(0, Math.min(MAX_TIMES, times));
        Map<K, Long> left = new HashMap<>();
        List<List<Take<K>>> takes = new ArrayList<>();
        long[] missing = new long[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            takes.add(new ArrayList<>());
        }
        //Les lignes les plus exigeantes (moins d'alternatives) d'abord ; a egalite, l'ordre de la recette
        Integer[] order = new Integer[lines.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingInt((Integer i) -> lines.get(i).alternatives().size()).thenComparingInt(i -> i));
        for (int i : order) {
            Line<K> l = lines.get(i);
            long need = (long) l.perCraft() * t;
            List<K> alts = new ArrayList<>(l.alternatives());
            for (K k : alts) {
                left.computeIfAbsent(k, key -> Math.max(0, available.applyAsLong(key)));
            }
            //Les plus fournies d'abord (a egalite, l'ordre de la recette : celle que JEI montre en premier)
            alts.sort(Comparator.comparingLong((K k) -> -left.get(k)));
            for (K k : alts) {
                if (need <= 0) {
                    break;
                }
                long have = left.get(k);
                long take = Math.min(need, have);
                if (take > 0) {
                    takes.get(i).add(new Take<>(k, take));
                    left.put(k, have - take);
                    need -= take;
                }
            }
            missing[i] = Math.max(0, need);
        }
        return new Plan<>(lines, takes, missing, t);
    }

    /** Plus grand nombre de fabrications (au plus {@code cap}) que le disponible permet en entier ; 0 si une seule ne passe pas. */
    public static <K> int maxTimes(List<Line<K>> lines, ToLongFunction<K> available, int cap) {
        int hi = Math.max(0, Math.min(MAX_TIMES, cap));
        if (lines.isEmpty() || hi == 0 || !plan(lines, 1, available).complete()) {
            return 0;
        }
        int lo = 1;
        //Recherche par dichotomie : le plan est monotone (plus de fabrications ne manque jamais moins)
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (plan(lines, mid, available).complete()) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
