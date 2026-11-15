package fr.pioneers.clan.rep;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 1.9 (MAJ RP, bloc 4) : regles de la reputation de clan (micro-peuple), lues dans {@code data/pioneers/clans/reputation.json}. Classe PURE
 * (aucune classe de Minecraft) : le banc {@code tools/rptest/WarBench} l'exerce. Cascade : un acte compte 100 % au joueur (bloc 3), 50 % a
 * son clan et 25 % a sa faction ou a son peuple de joueurs ; les fractions de point sont gardees d'un acte a l'autre (deux actes de +1 font
 * +1 au clan). Gains plafonnes a +30 par peuple et par jour, pertes jamais ; aucun pardon par le temps.
 */
public final class ClanRepRules {

    /** Un acte collectif : points par peuple ({@code own} = peuple du village concerne), sans plafond journalier si {@code uncapped}. */
    public record Act(Map<String, Integer> deltas, boolean uncapped) {
    }

    public record Values(int share, int upperShare, int dailyCap, int min, int max, Map<String, Integer> effect, int effectiveCap,
                         int hostileRadius, Map<String, Double> workers, Map<String, Integer> peddlers, Map<String, Act> collective,
                         Set<String> skipCascade) {

        public int effect(String tier) {
            return effect.getOrDefault(tier, 0);
        }
    }

    public static final Values DEFAULTS = new Values(50, 25, 30, -300, 300,
          Map.of("hostile", -15, "wary", -5, "unknown", 0, "esteemed", 5, "ally", 10), 15, 128, Map.of("ally", 0.8),
          Map.of("esteemed", 5, "ally", 3), Map.of("restore", new Act(Map.of("restorers", 20, "bare_hands", -15), true)), Set.of("restore"));

    private static volatile Values current = DEFAULTS;

    private ClanRepRules() {
    }

    public static Values get() {
        return current;
    }

    public static void set(Values v) {
        current = v == null ? DEFAULTS : v;
    }

    public static Values parse(JsonObject o) {
        Values d = DEFAULTS;
        Map<String, Integer> effect = new LinkedHashMap<>(d.effect());
        int cap = d.effectiveCap(), radius = d.hostileRadius();
        if (o.has("effective")) {
            JsonObject e = o.getAsJsonObject("effective");
            for (Map.Entry<String, JsonElement> x : e.entrySet()) {
                if (x.getKey().startsWith("_")) {
                    continue;
                }
                switch (x.getKey()) {
                    case "cap" -> cap = x.getValue().getAsInt();
                    case "hostile_radius" -> radius = x.getValue().getAsInt();
                    default -> effect.put(x.getKey(), x.getValue().getAsInt());
                }
            }
        }
        Map<String, Double> workers = new HashMap<>(d.workers());
        if (o.has("workers")) {
            workers.clear();
            o.getAsJsonObject("workers").entrySet().stream().filter(x -> !x.getKey().startsWith("_"))
                  .forEach(x -> workers.put(x.getKey(), x.getValue().getAsDouble()));
        }
        Map<String, Integer> peddlers = new HashMap<>(d.peddlers());
        if (o.has("peddlers")) {
            peddlers.clear();
            o.getAsJsonObject("peddlers").entrySet().stream().filter(x -> !x.getKey().startsWith("_"))
                  .forEach(x -> peddlers.put(x.getKey(), x.getValue().getAsInt()));
        }
        Map<String, Act> collective = new LinkedHashMap<>(d.collective());
        if (o.has("collective")) {
            collective.clear();
            for (Map.Entry<String, JsonElement> x : o.getAsJsonObject("collective").entrySet()) {
                if (x.getKey().startsWith("_")) {
                    continue;
                }
                JsonObject a = x.getValue().getAsJsonObject();
                Map<String, Integer> deltas = new LinkedHashMap<>();
                boolean uncapped = false;
                for (Map.Entry<String, JsonElement> y : a.entrySet()) {
                    if ("uncapped".equals(y.getKey())) {
                        uncapped = y.getValue().getAsBoolean();
                    } else if (!y.getKey().startsWith("_")) {
                        deltas.put(y.getKey(), y.getValue().getAsInt());
                    }
                }
                collective.put(x.getKey(), new Act(Map.copyOf(deltas), uncapped));
            }
        }
        Set<String> skip = new HashSet<>(d.skipCascade());
        if (o.has("skip_cascade")) {
            skip.clear();
            o.getAsJsonArray("skip_cascade").forEach(x -> skip.add(x.getAsString()));
        }
        return new Values(o.has("share") ? o.get("share").getAsInt() : d.share(), o.has("upper_share") ? o.get("upper_share").getAsInt()
              : d.upperShare(), o.has("daily_cap") ? o.get("daily_cap").getAsInt() : d.dailyCap(), o.has("min") ? o.get("min").getAsInt() : d.min(),
              o.has("max") ? o.get("max").getAsInt() : d.max(), Map.copyOf(effect), cap, radius, Map.copyOf(workers), Map.copyOf(peddlers),
              Map.copyOf(collective), Set.copyOf(skip));
    }

    // ------------------------------------------------------------------ calculs
    /**
     * Part d'un acte pour un niveau superieur : {@code delta} x {@code percent} %, en centiemes de point avec le reste garde. Renvoie
     * {points appliques, nouveau reste (centiemes, meme signe que le cumul)}.
     */
    public static int[] share(int delta, int percent, int remainder) {
        int total = delta * percent + remainder;
        int applied = total / 100;
        return new int[]{applied, total - applied * 100};
    }

    /** Gain borne par le plafond journalier (une perte passe toujours). */
    public static int capGain(int delta, int usedToday, int cap) {
        if (delta <= 0) {
            return delta;
        }
        return Math.max(0, Math.min(delta, cap - usedToday));
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Effet cumule des paliers (clan, faction ou peuple) borne a +-cap, puis affinite effective bornee a -100..+100. */
    public static int effective(int affinity, int cap, int... bonuses) {
        int sum = 0;
        for (int b : bonuses) {
            sum += b;
        }
        return clamp(affinity + clamp(sum, -cap, cap), -100, 100);
    }

    /** Prochain arret des Colporteurs au Marche du clan : un jour sur {@code interval} (decale par le clan), jamais si 0 ou moins. */
    public static boolean peddlerDay(long day, int interval, long seed) {
        return interval > 0 && Math.floorMod(day + seed, interval) == 0;
    }
}
