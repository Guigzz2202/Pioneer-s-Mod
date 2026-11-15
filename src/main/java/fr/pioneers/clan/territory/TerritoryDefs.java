package fr.pioneers.clan.territory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : reglages du territoire, du chunkloading, des points de clan ({@code data/pioneers/clans/territory.json}) et les
 * sept fonctions de Sceau ({@code data/pioneers/clans/functions.json}), relus a chaque {@code /reload}. Un fichier invalide est refuse
 * avec un message clair et les valeurs de la specification restent en place (elles servent aussi au banc).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TerritoryDefs {

    /** Une fonction de Sceau : niveau requis, nombre maximal, rayon d'effet (blocs), reglages propres. */
    public record Function(String id, int level, int max, int radius, JsonObject extra) {

        public int extraInt(String key, int def) {
            return extra.has(key) ? extra.get(key).getAsInt() : def;
        }
    }

    /** Valeurs de la specification (hypotheses par defaut) : outposts 2 (poids 2), apparition 8 chunks, cloche 96 blocs... */
    public int outposts = 2, outpostWeight = 2, spawnChunks = 8, bellBlocks = 96;
    public double cooperativeBonus = 0.10;
    public int declineHours = 72, releaseHours = 168;
    public long chunkloadCost = 200;
    public int chunkloadNoticeHours = 48, chunkloadCap = 6, soloChunkloadCap = 3;
    public int pointsPerTokens = 100, productionMemberCap = 50, chunkPoints = 2, chunkCap = 100, functionPoints = 10, functionCap = 80,
          ruinPoints = 500;
    public int borderRadius = 8;
    public Map<String, Function> functions = defaults();

    private static volatile TerritoryDefs current = new TerritoryDefs();

    public static TerritoryDefs get() {
        return current;
    }

    @Nullable
    public Function function(String id) {
        return functions.get(id);
    }

    public List<String> functionIds() {
        return new ArrayList<>(functions.keySet());
    }

    private static Map<String, Function> defaults() {
        Map<String, Function> m = new LinkedHashMap<>();
        Object[][] rows = {{"town_hall", 1, 1, 0}, {"warehouse", 1, 4, 0}, {"workshop", 2, 4, 48}, {"market", 2, 3, 32}, {"watchtower", 3, 3, 128},
              {"barracks", 4, 2, 16}, {"archives", 5, 1, 0}};
        for (Object[] r : rows) {
            m.put((String) r[0], new Function((String) r[0], (Integer) r[1], (Integer) r[2], (Integer) r[3], new JsonObject()));
        }
        return Collections.unmodifiableMap(m);
    }

    // ------------------------------------------------------------------ lecture
    public static TerritoryDefs parse(@Nullable JsonObject t, @Nullable JsonObject f, List<String> errors) {
        TerritoryDefs d = new TerritoryDefs();
        if (t != null) {
            try {
                JsonObject c = obj(t, "claims");
                d.outposts = c.has("outposts") ? positive(c, "outposts", 0) : d.outposts;
                d.outpostWeight = c.has("outpost_weight") ? positive(c, "outpost_weight", 1) : d.outpostWeight;
                d.spawnChunks = c.has("spawn_chunks") ? positive(c, "spawn_chunks", 0) : d.spawnChunks;
                d.bellBlocks = c.has("bell_blocks") ? positive(c, "bell_blocks", 0) : d.bellBlocks;
                d.cooperativeBonus = c.has("cooperative_bonus") ? c.get("cooperative_bonus").getAsDouble() : d.cooperativeBonus;
                d.declineHours = c.has("decline_hours") ? positive(c, "decline_hours", 0) : d.declineHours;
                d.releaseHours = c.has("dissolved_release_hours") ? positive(c, "dissolved_release_hours", 0) : d.releaseHours;
                d.borderRadius = c.has("border_radius") ? Math.min(16, positive(c, "border_radius", 1)) : d.borderRadius;
                JsonObject l = obj(t, "chunkload");
                d.chunkloadCost = l.has("cost_per_day") ? Math.max(0, l.get("cost_per_day").getAsLong()) : d.chunkloadCost;
                d.chunkloadNoticeHours = l.has("notice_hours") ? positive(l, "notice_hours", 0) : d.chunkloadNoticeHours;
                d.chunkloadCap = l.has("max_slots") ? positive(l, "max_slots", 0) : d.chunkloadCap;
                d.soloChunkloadCap = l.has("solo_max_slots") ? positive(l, "solo_max_slots", 0) : d.soloChunkloadCap;
                JsonObject p = obj(t, "points");
                d.pointsPerTokens = p.has("tokens_per_point") ? positive(p, "tokens_per_point", 1) : d.pointsPerTokens;
                d.productionMemberCap = p.has("production_member_cap") ? positive(p, "production_member_cap", 0) : d.productionMemberCap;
                d.chunkPoints = p.has("per_chunk") ? positive(p, "per_chunk", 0) : d.chunkPoints;
                d.chunkCap = p.has("chunk_cap") ? positive(p, "chunk_cap", 0) : d.chunkCap;
                d.functionPoints = p.has("per_function") ? positive(p, "per_function", 0) : d.functionPoints;
                d.functionCap = p.has("function_cap") ? positive(p, "function_cap", 0) : d.functionCap;
                d.ruinPoints = p.has("ruin") ? positive(p, "ruin", 0) : d.ruinPoints;
            } catch (RuntimeException ex) {
                errors.add("territory.json : " + ex.getMessage());
                d = new TerritoryDefs();
            }
        } else {
            errors.add("territory.json : absent");
        }
        if (f != null) {
            try {
                Map<String, Function> m = new LinkedHashMap<>();
                for (JsonElement e : f.getAsJsonArray("functions")) {
                    JsonObject o = e.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    int level = positive(o, "level", 1), max = positive(o, "max", 1);
                    int radius = o.has("radius") ? positive(o, "radius", 0) : 0;
                    if (!id.matches("[a-z_]+") || m.containsKey(id)) {
                        throw new IllegalArgumentException("fonction '" + id + "' invalide ou en double");
                    }
                    m.put(id, new Function(id, level, max, radius, o));
                }
                if (!m.keySet().containsAll(defaults().keySet())) {
                    throw new IllegalArgumentException("il manque une des sept fonctions");
                }
                d.functions = Collections.unmodifiableMap(m);
            } catch (RuntimeException ex) {
                errors.add("functions.json : " + ex.getMessage());
            }
        } else {
            errors.add("functions.json : absent");
        }
        return d;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonObject(key) : new JsonObject();
    }

    private static int positive(JsonObject o, String key, int min) {
        int v = o.get(key).getAsInt();
        if (v < min) {
            throw new IllegalArgumentException(key + " doit valoir au moins " + min);
        }
        return v;
    }

    // ------------------------------------------------------------------ rechargement
    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    static final class Loader extends SimplePreparableReloadListener<TerritoryDefs> {

        @Override
        protected @NotNull TerritoryDefs prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            List<String> errors = new ArrayList<>();
            TerritoryDefs d = parse(read(manager, "territory", errors), read(manager, "functions", errors), errors);
            for (String e : errors) {
                Pioneers.LOGGER.error("Clans (territoire) : fichier refuse, {}", e);
            }
            return d;
        }

        @Override
        protected void apply(@NotNull TerritoryDefs defs, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            current = defs;
        }

        @Nullable
        private static JsonObject read(ResourceManager manager, String name, List<String> errors) {
            return manager.getResource(Pioneers.rl("clans/" + name + ".json")).map(r -> {
                try (Reader in = r.openAsReader()) {
                    return JsonParser.parseReader(in).getAsJsonObject();
                } catch (Exception ex) {
                    errors.add(name + ".json : JSON illisible (" + ex.getMessage() + ")");
                    return null;
                }
            }).orElse(null);
        }
    }
}
