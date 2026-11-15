package fr.pioneers.society.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : donnees de l'economie, {@code data/pioneers/economy/*.json} (generees par {@code tools/gc/economy/gen_values.py}) :
 * valeur de base de chaque objet en centiemes de jeton, classe de quota, age, familles ; quotas ; pourcentages des partenaires ; stock des
 * Colporteurs. Rien d'equilibrage en dur : un fichier invalide est refuse au chargement avec un message, l'ancienne table reste.
 */
public final class Values extends SimpleJsonResourceReloadListener {

    public static final Values INSTANCE = new Values();

    /** Une ligne de la table : valeur (centiemes), classe de quota, age (0 primitif .. 6 cosmique), familles. */
    public record Entry(long value, String quota, int age, Set<String> families) {
    }

    /** Un tirage du stock des Colporteurs. */
    public record Stock(Item item, int weight, int min, int max) {
    }

    private static volatile Map<Item, Entry> table = Map.of();
    private static volatile Map<String, Integer> quotas = Map.of("raw", 64, "crafted", 16, "component", 4, "scrap_mk3", 1);
    private static volatile JsonObject partners = new JsonObject();
    private static volatile Map<String, List<Stock>> peddler = Map.of();
    private static volatile Map<String, Integer> peddlerSlots = Map.of();
    private static volatile Set<String> peddlerRefuses = Set.of("component", "machine");

    private Values() {
        super(new com.google.gson.Gson(), "economy");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        try {
            JsonElement v = map.get(Pioneers.rl("values"));
            if (v != null) {
                Map<Item, Entry> t = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().getAsJsonObject("items").entrySet()) {
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                    if (item == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    JsonObject o = e.getValue().getAsJsonObject();
                    Set<String> fam = new java.util.HashSet<>();
                    if (o.has("f")) {
                        o.getAsJsonArray("f").forEach(x -> fam.add(x.getAsString()));
                    }
                    t.put(item, new Entry(Math.max(1, o.get("v").getAsLong()), o.has("q") ? o.get("q").getAsString() : "crafted",
                          o.has("a") ? o.get("a").getAsInt() : 0, Set.copyOf(fam)));
                }
                table = t;
            }
            JsonElement q = map.get(Pioneers.rl("quotas"));
            if (q != null) {
                Map<String, Integer> m = new HashMap<>();
                q.getAsJsonObject().entrySet().forEach(e -> m.put(e.getKey(), e.getValue().getAsInt()));
                quotas = m;
            }
            JsonElement p = map.get(Pioneers.rl("partners"));
            if (p != null) {
                partners = p.getAsJsonObject();
            }
            JsonElement s = map.get(Pioneers.rl("peddler_stock"));
            if (s != null) {
                JsonObject o = s.getAsJsonObject();
                Map<String, List<Stock>> st = new HashMap<>();
                Map<String, Integer> slots = new HashMap<>();
                o.getAsJsonObject("slots").entrySet().forEach(e -> slots.put(e.getKey(), e.getValue().getAsInt()));
                for (String cat : slots.keySet()) {
                    List<Stock> list = new ArrayList<>();
                    if (o.has(cat)) {
                        o.getAsJsonArray(cat).forEach(x -> {
                            JsonObject xo = x.getAsJsonObject();
                            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(xo.get("item").getAsString()));
                            if (item != net.minecraft.world.item.Items.AIR) {
                                int[] c = {1, 1};
                                if (xo.has("count")) {
                                    c[0] = xo.getAsJsonArray("count").get(0).getAsInt();
                                    c[1] = xo.getAsJsonArray("count").get(1).getAsInt();
                                }
                                list.add(new Stock(item, xo.has("weight") ? xo.get("weight").getAsInt() : 1, c[0], c[1]));
                            }
                        });
                    }
                    st.put(cat, List.copyOf(list));
                }
                peddler = st;
                peddlerSlots = slots;
                if (o.has("buys_except_families")) {
                    Set<String> r = new java.util.HashSet<>();
                    o.getAsJsonArray("buys_except_families").forEach(x -> r.add(x.getAsString()));
                    peddlerRefuses = Set.copyOf(r);
                }
            }
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.error("Donnees de l'economie illisibles (data/pioneers/economy) : {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static Entry entry(Item item) {
        return table.get(item);
    }

    @Nullable
    public static Entry entry(ItemStack stack) {
        return stack.isEmpty() ? null : table.get(stack.getItem());
    }

    /** Valeur de base en centiemes de jeton (0 : invendable). */
    public static long value(ItemStack stack) {
        Entry e = entry(stack);
        return e == null ? 0 : e.value();
    }

    public static boolean has(Item item, String family) {
        Entry e = table.get(item);
        return e != null && e.families().contains(family);
    }

    public static int quota(String quotaClass) {
        return quotas.getOrDefault(quotaClass, 16);
    }

    public static double partner(String partner, String key, double fallback) {
        JsonObject o = partners.getAsJsonObject(partner);
        return o != null && o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    /** Valeur simple de {@code partners.json} (montants en centiemes). */
    public static double setting(String key, double fallback) {
        return partners.has(key) && partners.get(key).isJsonPrimitive() ? partners.get(key).getAsDouble() : fallback;
    }

    public static List<Stock> peddlerStock(String category) {
        return peddler.getOrDefault(category, List.of());
    }

    public static int peddlerSlots(String category) {
        return peddlerSlots.getOrDefault(category, 0);
    }

    /** Les Colporteurs achetent tout objet de la table sauf ces familles (composants du catalogue, machines). */
    public static boolean peddlerBuys(Item item) {
        Entry e = table.get(item);
        if (e == null || "component".equals(e.quota())) {
            return false;
        }
        for (String f : peddlerRefuses) {
            if (e.families().contains(f)) {
                return false;
            }
        }
        return true;
    }

    public static int size() {
        return table.size();
    }
}
