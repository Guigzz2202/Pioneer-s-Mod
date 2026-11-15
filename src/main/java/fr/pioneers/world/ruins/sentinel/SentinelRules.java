package fr.pioneers.world.ruins.sentinel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.9 (MAJ RP, bloc 1) : chiffres des Sentinelles lus dans {@code data/pioneers/sentinels/*.json} ({@code rules} : regles communes,
 * {@code spawn} : composition par tier, un fichier par role : valeurs du role puis surcharges par tier). Sans classe Minecraft : le banc
 * hors jeu lit les memes fichiers. Si un fichier manque : regles communes et composition par defaut de la specification, valeurs neutres
 * pour un role (40 PV, 4 degats).
 */
public final class SentinelRules {

    /** Composition d'une ruine d'un tier : min..max Sentinelles tirees selon les poids, roles exiges d'abord, plus les Contremaitres. */
    public static final class Composition {
        public final int min, max, foremen;
        public final Map<String, Double> weights;
        public final List<String> require;

        public Composition(int min, int max, Map<String, Double> weights, List<String> require, int foremen) {
            this.min = Math.max(0, min);
            this.max = Math.max(this.min, max);
            this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
            this.require = List.copyOf(require);
            this.foremen = Math.max(0, foremen);
        }
    }

    /** Valeurs d'un role a un tier (valeurs du role, puis celles du tier). */
    public static final class Stats {
        private final Map<String, Double> values;

        Stats(Map<String, Double> values) {
            this.values = values;
        }

        public double get(String key, double fallback) {
            Double v = values.get(key);
            return v == null ? fallback : v;
        }

        public int ticks(String key, int fallback) {
            return (int) Math.round(get(key, fallback));
        }

        public boolean has(String key) {
            return values.containsKey(key);
        }
    }

    private static final Stats EMPTY = new Stats(Map.of());
    private final Map<String, Double> common;
    private final Map<String, Map<Integer, Stats>> roles;
    private final Map<Integer, Composition> spawn;
    private final double patrollerLift;

    private SentinelRules(Map<String, Double> common, Map<String, Map<Integer, Stats>> roles, Map<Integer, Composition> spawn, double lift) {
        this.common = common;
        this.roles = roles;
        this.spawn = spawn;
        this.patrollerLift = lift;
    }

    /** Regles sans fichier (repli de la specification, journalise par {@link SentinelData}). */
    public static SentinelRules defaults() {
        return parse(Map.of());
    }

    /** Construit les regles depuis les fichiers lus (cle = nom du fichier sans extension). */
    public static SentinelRules parse(Map<String, JsonObject> files) {
        Map<String, Double> common = numbers(files.get("rules"));
        Map<String, Map<Integer, Stats>> roles = new HashMap<>();
        for (SentinelRole role : SentinelRole.values()) {
            JsonObject o = files.get(role.id());
            Map<Integer, Stats> byTier = new HashMap<>();
            if (o != null) {
                Map<String, Double> base = numbers(o);
                JsonObject tiers = o.has("tiers") && o.get("tiers").isJsonObject() ? o.getAsJsonObject("tiers") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : tiers.entrySet()) {
                    int t = parseInt(e.getKey());
                    if (t >= 1 && t <= 3 && e.getValue().isJsonObject()) {
                        Map<String, Double> all = new HashMap<>(base);
                        all.putAll(numbers(e.getValue().getAsJsonObject()));
                        byTier.put(t, new Stats(all));
                    }
                }
            }
            roles.put(role.id(), byTier);
        }
        Map<Integer, Composition> spawn = new HashMap<>();
        JsonObject s = files.get("spawn");
        double lift = 3;
        if (s != null) {
            lift = s.has("patroller_lift") ? s.get("patroller_lift").getAsDouble() : lift;
            JsonObject tiers = s.has("tiers") ? s.getAsJsonObject("tiers") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : tiers.entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                Map<String, Double> w = new LinkedHashMap<>();
                if (c.has("weights")) {
                    c.getAsJsonObject("weights").entrySet().forEach(x -> w.put(x.getKey(), x.getValue().getAsDouble()));
                }
                List<String> req = new ArrayList<>();
                if (c.has("require")) {
                    c.getAsJsonArray("require").forEach(x -> req.add(x.getAsString()));
                }
                spawn.put(parseInt(e.getKey()), new Composition(intOf(c, "min", 1), intOf(c, "max", 1), w, req, intOf(c, "foreman", 0)));
            }
        }
        if (spawn.isEmpty()) {
            spawn.put(1, new Composition(1, 2, Map.of("guardian", 3.0, "patroller", 2.0), List.of(), 0));
            spawn.put(2, new Composition(3, 5, Map.of("guardian", 3.0, "patroller", 2.0, "turret", 2.0, "repairer", 1.0), List.of("turret", "repairer"),
                  0));
            spawn.put(3, new Composition(5, 8, Map.of("guardian", 3.0, "patroller", 2.0, "turret", 2.0, "repairer", 2.0), List.of("turret", "repairer"),
                  1));
        }
        return new SentinelRules(common, roles, spawn, lift);
    }

    // ------------------------------------------------------------------ lecture
    public double common(String key, double fallback) {
        Double v = common.get(key);
        return v == null ? fallback : v;
    }

    public int commonTicks(String key, int fallback) {
        return (int) Math.round(common(key, fallback));
    }

    public Stats stats(SentinelRole role, int tier) {
        Map<Integer, Stats> m = roles.get(role.id());
        if (m == null || m.isEmpty()) {
            return EMPTY;
        }
        Stats s = m.get(tier);
        if (s != null) {
            return s;
        }
        //Tier absent du fichier (Sentinelle posee a la main) : le plus proche
        int best = -1;
        for (int t : m.keySet()) {
            if (best < 0 || Math.abs(t - tier) < Math.abs(best - tier)) {
                best = t;
            }
        }
        return m.get(best);
    }

    public Composition composition(int tier) {
        Composition c = spawn.get(tier);
        if (c != null) {
            return c;
        }
        return spawn.get(Math.max(1, Math.min(3, tier)));
    }

    public double patrollerLift() {
        return patrollerLift;
    }

    // --- chiffres communs nommes
    public int wakeInterval() {
        return Math.max(1, commonTicks("wake_interval", 10));
    }

    public int alertTicks() {
        return commonTicks("alert_ticks", 30);
    }

    public int sleepAfter() {
        return commonTicks("sleep_after", 600);
    }

    public int giveUp() {
        return commonTicks("give_up", 1200);
    }

    public double leash() {
        return common("leash", 40);
    }

    public double home() {
        return common("home", 1.5);
    }

    public int stunTicks() {
        return commonTicks("stun_ticks", 60);
    }

    // ------------------------------------------------------------------ outils
    private static Map<String, Double> numbers(JsonObject o) {
        Map<String, Double> out = new HashMap<>();
        if (o == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                out.put(e.getKey(), v.getAsDouble());
            }
        }
        return out;
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
