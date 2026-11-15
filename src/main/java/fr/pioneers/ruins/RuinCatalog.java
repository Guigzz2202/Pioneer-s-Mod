package fr.pioneers.ruins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : multiblocs ruinables ({@code data/pioneers/ruins/catalog.json}, ecrit par {@code tools/gc/ruins/ruin_catalog.py}) :
 * tiers possibles, categorie de scraps dominante, service rendu au village une fois restaure, poids de tirage. Jamais de reacteur, de MKIV ni
 * de machine spatiale.
 */
public final class RuinCatalog extends SimpleJsonResourceReloadListener {

    public record Entry(String machine, int minTier, ScrapCategory category, String service, int weight) {
    }

    public static final RuinCatalog INSTANCE = new RuinCatalog();
    private static volatile Map<String, Entry> entries = Map.of();

    private RuinCatalog() {
        super(new com.google.gson.Gson(), "ruins");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, Entry> out = new LinkedHashMap<>();
        JsonElement e = map.get(Pioneers.rl("catalog"));
        if (e != null && e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().getAsJsonObject("machines").entrySet()) {
                try {
                    JsonObject o = m.getValue().getAsJsonObject();
                    out.put(m.getKey(), new Entry(m.getKey(), o.get("min_tier").getAsInt(),
                          ScrapCategory.valueOf(o.get("category").getAsString().toUpperCase(java.util.Locale.ROOT)), o.get("service").getAsString(),
                          o.has("weight") ? o.get("weight").getAsInt() : 1));
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.warn("Ruine ignoree ({}) : {}", m.getKey(), ex.toString());
                }
            }
        }
        entries = out;
    }

    public static Map<String, Entry> entries() {
        return entries;
    }

    /** Multiblocs qu'une ruine de ce tier peut representer. */
    public static List<Entry> forTier(int tier) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.minTier() <= tier) {
                out.add(e);
            }
        }
        return out;
    }

    public static ScrapCategory category(String machine) {
        Entry e = entries.get(machine);
        return e == null ? ScrapCategory.STRUCTURE : e.category();
    }

    public static String service(String machine) {
        Entry e = entries.get(machine);
        return e == null ? "workshop" : e.service();
    }
}
