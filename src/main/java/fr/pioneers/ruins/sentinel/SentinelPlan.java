package fr.pioneers.ruins.sentinel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * 1.9 (MAJ RP, bloc 1) : composition des Sentinelles d'une ruine (sans classe Minecraft, banc hors jeu). Contremaitres d'abord (ils
 * prennent le premier poste assez grand), puis les roles exiges, puis un tirage pondere jusqu'au nombre tire entre min et max. Un role
 * absent a ce tier (Tourelle ou Reparateur en MKI) n'est jamais tire.
 */
public final class SentinelPlan {

    private SentinelPlan() {
    }

    /** @param random {@code n -> entier dans [0, n)} */
    public static List<SentinelRole> compose(SentinelRules rules, int tier, IntUnaryOperator random) {
        SentinelRules.Composition c = rules.composition(tier);
        List<SentinelRole> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        for (int i = 0; i < c.foremen; i++) {
            if (fits(SentinelRole.FOREMAN, tier)) {
                out.add(SentinelRole.FOREMAN);
            }
        }
        int n = c.min + (c.max > c.min ? random.applyAsInt(c.max - c.min + 1) : 0);
        List<SentinelRole> drawn = new ArrayList<>();
        for (String id : c.require) {
            SentinelRole r = SentinelRole.byId(id);
            if (r != null && r != SentinelRole.FOREMAN && fits(r, tier) && drawn.size() < n) {
                drawn.add(r);
            }
        }
        double total = 0;
        for (Map.Entry<String, Double> e : c.weights.entrySet()) {
            SentinelRole r = SentinelRole.byId(e.getKey());
            if (r != null && r != SentinelRole.FOREMAN && fits(r, tier) && e.getValue() > 0) {
                total += e.getValue();
            }
        }
        while (drawn.size() < n && total > 0) {
            double pick = random.applyAsInt(1_000_000) / 1_000_000.0 * total;
            SentinelRole chosen = null;
            for (Map.Entry<String, Double> e : c.weights.entrySet()) {
                SentinelRole r = SentinelRole.byId(e.getKey());
                if (r == null || r == SentinelRole.FOREMAN || !fits(r, tier) || e.getValue() <= 0) {
                    continue;
                }
                chosen = r;
                pick -= e.getValue();
                if (pick < 0) {
                    break;
                }
            }
            drawn.add(chosen);
        }
        out.addAll(drawn);
        return out;
    }

    private static boolean fits(SentinelRole r, int tier) {
        return tier >= r.minTier && tier <= r.maxTier;
    }
}
