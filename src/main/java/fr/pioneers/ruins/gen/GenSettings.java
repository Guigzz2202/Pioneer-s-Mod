package fr.pioneers.ruins.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.9 (MAJ RP, bloc 1) : reglages de la generation des ruines ({@code data/pioneers/ruins/generation.json}, ecrit par
 * {@code tools/gc/ruins/gen_data.py}) : grille par tier, usure, tailles, terrain accepte, modes de pose, annexes. Objet immuable, partage
 * entre les fils de generation. Les interrupteurs (ruines, densite, usure, annexes, Sentinelles) restent dans la configuration commune.
 * Java pur (aucun registre Minecraft) : le banc hors jeu lit le meme fichier ; les biomes exclus ({@code #tag} ou id) sont resolus par
 * {@link GenData}.
 */
public record GenSettings(int candidates, int exclusion, int refX, int refZ, int maxHalf, int margin, int annexMin, int annexMax, int annexClearance,
                          int secondCabinet, Tier[] tiers) {

    /** Reglages d'un tier. {@code modes} : surface, sunken (fosse a ciel ouvert), flank (flanc de montagne), underground (salle). */
    public record Tier(int tier, RuinGrid.TierGrid grid, double wearMin, double wearMax, double worn, int minCells, int maxCells, int maxHeight,
                       double rubble, int relief, int centerWater, int deepCorners, List<String> excludedBiomes, Map<String, Integer> modes,
                       int depthMin, int depthMax) {

        /** Mode de pose tire au sort selon les poids ("surface" par defaut). */
        public String mode(Rng rng) {
            int total = 0;
            for (int w : modes.values()) {
                total += Math.max(0, w);
            }
            if (total <= 0) {
                return "surface";
            }
            int r = rng.nextInt(total);
            for (Map.Entry<String, Integer> e : modes.entrySet()) {
                r -= Math.max(0, e.getValue());
                if (r < 0) {
                    return e.getKey();
                }
            }
            return "surface";
        }
    }

    /** Valeurs de la specification (utilisees si le fichier manque ou est illisible). */
    public static final GenSettings DEFAULT = new GenSettings(3, 4, 0, 0, 15, 3, 24, 80, 22, 60, new Tier[]{
          new Tier(1, new RuinGrid.TierGrid(24, 8, 0), 0.25, 0.40, 0.2, 40, 450, 22, 0.10, 10, 0, 1, List.of(), Map.of("surface", 1), 16, 30),
          new Tier(2, new RuinGrid.TierGrid(40, 14, 600), 0.35, 0.50, 0.32, 150, 820, 26, 0.11, 16, 1, 2, List.of(), Map.of("surface", 1), 16, 30),
          new Tier(3, new RuinGrid.TierGrid(64, 24, 1500), 0.45, 0.60, 0.45, 240, 1400, 30, 0.12, 999, 999, 5, List.of(), Map.of("underground", 1), 16,
                30)});

    public Tier tier(int tier) {
        return tiers[Math.max(1, Math.min(RuinGrid.TIERS, tier)) - 1];
    }

    /** Grille a la densite de la configuration (1 = normale). */
    public RuinGrid.Layout layout(int density) {
        RuinGrid.TierGrid[] g = new RuinGrid.TierGrid[RuinGrid.TIERS];
        for (int t = 0; t < RuinGrid.TIERS; t++) {
            g[t] = tiers[t].grid().scaled(density);
        }
        return new RuinGrid.Layout(g, candidates, exclusion, refX, refZ);
    }

    public static GenSettings parse(JsonObject o) {
        int[] ref = ints(o.get("reference"), 0, 0);
        int[] annex = ints(o.get("annex_distance"), DEFAULT.annexMin, DEFAULT.annexMax);
        Tier[] tiers = DEFAULT.tiers.clone();
        if (o.has("tiers")) {
            for (JsonElement e : o.getAsJsonArray("tiers")) {
                Tier t = tier(e.getAsJsonObject());
                tiers[t.tier() - 1] = t;
            }
        }
        return new GenSettings(i(o, "candidates", DEFAULT.candidates), i(o, "exclusion_chunks", DEFAULT.exclusion), ref[0], ref[1],
              i(o, "max_half_extent", DEFAULT.maxHalf), i(o, "margin", DEFAULT.margin), annex[0], annex[1],
              i(o, "annex_clearance", DEFAULT.annexClearance), i(o, "cabinet_second_interior", DEFAULT.secondCabinet), tiers);
    }

    private static Tier tier(JsonObject o) {
        int tier = Math.max(1, Math.min(RuinGrid.TIERS, i(o, "tier", 1)));
        Tier d = DEFAULT.tiers[tier - 1];
        double[] wear = doubles(o.get("wear"), d.wearMin(), d.wearMax());
        int[] cells = ints(o.get("cells"), d.minCells(), d.maxCells());
        int[] depth = ints(o.get("depth"), d.depthMin(), d.depthMax());
        List<String> biomes = new ArrayList<>();
        if (o.has("exclude_biomes")) {
            for (JsonElement e : o.getAsJsonArray("exclude_biomes")) {
                biomes.add(e.getAsString());
            }
        }
        Map<String, Integer> modes = new LinkedHashMap<>();
        if (o.has("modes")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("modes").entrySet()) {
                modes.put(e.getKey(), e.getValue().getAsInt());
            }
        } else {
            modes.putAll(d.modes());
        }
        RuinGrid.TierGrid grid = new RuinGrid.TierGrid(i(o, "spacing", d.grid().spacing()), i(o, "separation", d.grid().separation()),
              i(o, "spawn_distance", d.grid().spawnDistance()));
        if (grid.separation() >= grid.spacing()) {
            grid = new RuinGrid.TierGrid(grid.spacing(), Math.max(0, grid.spacing() - 1), grid.spawnDistance());
        }
        return new Tier(tier, grid, wear[0], wear[1], dd(o, "worn", d.worn()), cells[0], cells[1], i(o, "max_height", d.maxHeight()),
              dd(o, "rubble", d.rubble()), i(o, "relief", d.relief()), i(o, "center_water", d.centerWater()), i(o, "deep_corners", d.deepCorners()),
              List.copyOf(biomes), modes.isEmpty() ? d.modes() : Collections.unmodifiableMap(modes), depth[0], depth[1]);
    }

    private static int i(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static double dd(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static int[] ints(JsonElement e, int a, int b) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 2) {
            return new int[]{a, b};
        }
        JsonArray arr = e.getAsJsonArray();
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    private static double[] doubles(JsonElement e, double a, double b) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 2) {
            return new double[]{a, b};
        }
        JsonArray arr = e.getAsJsonArray();
        return new double[]{arr.get(0).getAsDouble(), arr.get(1).getAsDouble()};
    }
}
