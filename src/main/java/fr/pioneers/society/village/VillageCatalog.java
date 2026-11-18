package fr.pioneers.society.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : donnees des villages : doctrines (catalogues de commerce par peuple, {@code village/doctrines/*.json}), treize
 * metiers ({@code village/professions.json}, versions « standard » et « rural »), noms ({@code village/names/*.json}), paroles
 * ({@code dialogue/*.json}) et dictons ({@code lore/dictons.json}). Un fichier invalide est refuse avec un message ; l'ancien reste.
 */
public final class VillageCatalog {

    private VillageCatalog() {
    }

    public record Offer(ItemStack stack) {
    }

    public record Version(List<ItemStack> sells, List<Item> buys, List<ItemStack> workshopSells, @Nullable String service) {

        static final Version EMPTY = new Version(List.of(), List.of(), List.of(), null);
    }

    public record Profession(String key, Map<String, Version> versions) {

        public Version version(String v) {
            return versions.getOrDefault(v, versions.getOrDefault("standard", Version.EMPTY));
        }
    }

    public record Bonus(Set<String> families, double factor) {
    }

    public record Doctrine(String id, String version, int maxAge, int workshopMaxAge, Set<String> accept, Set<String> refuse, List<Bonus> buyBonus,
                           List<ItemStack> scraps, List<ItemStack> exclusive, boolean workers, int notebookAffinity, int librarianNotebookAffinity,
                           double caravanFactor, Set<String> guaranteed) {

        public boolean rural() {
            return "rural".equals(version);
        }
    }

    static final Doctrine FALLBACK = new Doctrine("earthbound", "rural", 1, -1, Set.of(), Set.of(), List.of(), List.of(), List.of(), false, 0, 0, 1,
          Set.of());

    private static volatile Map<String, Doctrine> doctrines = Map.of();
    private static volatile Map<String, Profession> professions = Map.of();
    private static volatile Map<String, List<String>> names = Map.of();
    private static volatile Map<String, Boolean> nameKeys = Map.of();
    private static volatile JsonObject dialogue = new JsonObject();
    private static volatile Map<String, List<String>> dictons = Map.of();

    // ------------------------------------------------------------------ lecture
    public static Doctrine doctrine(String id) {
        return doctrines.getOrDefault(id, doctrines.getOrDefault("earthbound", FALLBACK));
    }

    @Nullable
    public static Profession profession(String key) {
        return professions.get(key);
    }

    public static List<String> names(String people) {
        return names.getOrDefault(people, List.of());
    }

    /** Les noms de ce peuple sont des cles de langue (surnoms de Colporteur) plutot que des noms litteraux. */
    public static boolean namesAreKeys(String people) {
        return nameKeys.getOrDefault(people, false);
    }

    /** Une liste de cles de paroles : registre d'un peuple ({@code people/register}) ou section commune ({@code common/section/sub}). */
    public static List<String> lines(String path) {
        String[] parts = path.split("/");
        JsonElement e = dialogue;
        for (String p : parts) {
            if (e == null || !e.isJsonObject() || !e.getAsJsonObject().has(p)) {
                return List.of();
            }
            e = e.getAsJsonObject().get(p);
        }
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
        }
        return out;
    }

    public static List<String> dictons(String people) {
        return dictons.getOrDefault(people, List.of());
    }

    // ------------------------------------------------------------------ chargement
    private static ItemStack stack(JsonObject o) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(o.get("item").getAsString()));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1);
    }

    private static List<ItemStack> stacks(@Nullable JsonArray a) {
        List<ItemStack> out = new ArrayList<>();
        if (a != null) {
            a.forEach(x -> {
                ItemStack s = x.isJsonObject() ? stack(x.getAsJsonObject()) : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(x.getAsString())));
                if (!s.isEmpty()) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    private static Set<String> strings(@Nullable JsonArray a) {
        Set<String> out = new HashSet<>();
        if (a != null) {
            a.forEach(x -> out.add(x.getAsString()));
        }
        return out;
    }

    /** Ecouteur du dossier {@code data/pioneers/village}. */
    public static final class VillageListener extends SimpleJsonResourceReloadListener {

        public static final VillageListener INSTANCE = new VillageListener();

        private VillageListener() {
            super(new com.google.gson.Gson(), "village");
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            Map<String, Doctrine> d = new HashMap<>();
            Map<String, List<String>> n = new HashMap<>();
            Map<String, Boolean> nk = new HashMap<>();
            Map<String, Profession> pr = new HashMap<>();
            map.forEach((id, json) -> {
                if (!id.getNamespace().equals(Pioneers.MODID)) {
                    return;
                }
                String path = id.getPath();
                try {
                    JsonObject o = json.getAsJsonObject();
                    if (path.startsWith("doctrines/")) {
                        List<Bonus> bonus = new ArrayList<>();
                        if (o.has("buy_bonus")) {
                            o.getAsJsonArray("buy_bonus").forEach(x -> bonus.add(new Bonus(strings(x.getAsJsonObject().getAsJsonArray("families")),
                                  x.getAsJsonObject().get("factor").getAsDouble())));
                        }
                        Doctrine doc = new Doctrine(o.get("id").getAsString(), o.has("version") ? o.get("version").getAsString() : "standard",
                              o.has("max_age") ? o.get("max_age").getAsInt() : 1,
                              o.has("workshop_max_age") && !o.get("workshop_max_age").isJsonNull() ? o.get("workshop_max_age").getAsInt() : -1,
                              strings(o.getAsJsonArray("accept_families")), strings(o.getAsJsonArray("refuse_families")), bonus,
                              stacks(o.getAsJsonArray("scraps")), stacks(o.getAsJsonArray("exclusive")), o.has("workers") && o.get("workers").getAsBoolean(),
                              o.has("notebook_affinity") ? o.get("notebook_affinity").getAsInt() : 5,
                              o.has("librarian_notebook_affinity") ? o.get("librarian_notebook_affinity").getAsInt() : 15,
                              o.has("caravan_factor") ? o.get("caravan_factor").getAsDouble() : 1, strings(o.getAsJsonArray("guaranteed")));
                        d.put(doc.id(), doc);
                    } else if (path.startsWith("names/")) {
                        String people = path.substring("names/".length());
                        List<String> list = new ArrayList<>();
                        boolean keys = o.has("keys");
                        (keys ? o.getAsJsonArray("keys") : o.getAsJsonArray("names")).forEach(x -> list.add(x.getAsString()));
                        n.put(people, List.copyOf(list));
                        nk.put(people, keys);
                    } else if (path.equals("repairs")) {
                        //1.9.1 (retours de Guillaume) : seuils d'hostilite et regles des reparations
                        RepairRules.set(VillageRepairs.parse(o));
                    } else if (path.equals("professions")) {
                        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                            if (e.getKey().startsWith("_")) {
                                continue;
                            }
                            JsonObject po = e.getValue().getAsJsonObject();
                            Map<String, Version> versions = new HashMap<>();
                            for (String v : List.of("standard", "rural")) {
                                if (po.has(v)) {
                                    JsonObject vo = po.getAsJsonObject(v);
                                    List<Item> buys = new ArrayList<>();
                                    if (vo.has("buys")) {
                                        vo.getAsJsonArray("buys").forEach(x -> {
                                            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(x.isJsonObject()
                                                  ? x.getAsJsonObject().get("item").getAsString() : x.getAsString()));
                                            if (it != Items.AIR) {
                                                buys.add(it);
                                            }
                                        });
                                    }
                                    versions.put(v, new Version(stacks(vo.getAsJsonArray("sells")), List.copyOf(buys), stacks(vo.getAsJsonArray("workshop_sells")),
                                          vo.has("service") && !vo.get("service").isJsonNull() ? vo.get("service").getAsString() : null));
                                }
                            }
                            String key = po.has("key") ? po.get("key").getAsString() : e.getKey();
                            pr.put(e.getKey(), new Profession(key, versions));
                        }
                    }
                } catch (RuntimeException ex) {
                    Pioneers.LOGGER.error("Donnees de village illisibles ({}) : {}", id, ex.toString());
                }
            });
            if (!d.isEmpty()) {
                doctrines = d;
            }
            if (!n.isEmpty()) {
                names = n;
                nameKeys = nk;
            }
            if (!pr.isEmpty()) {
                professions = pr;
            }
        }
    }

    /** Ecouteur du dossier {@code data/pioneers/dialogue}. */
    public static final class DialogueListener extends SimpleJsonResourceReloadListener {

        public static final DialogueListener INSTANCE = new DialogueListener();

        private DialogueListener() {
            super(new com.google.gson.Gson(), "dialogue");
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            JsonObject o = new JsonObject();
            map.forEach((id, json) -> {
                if (id.getNamespace().equals(Pioneers.MODID) && json.isJsonObject()) {
                    o.add(id.getPath(), json);
                }
            });
            dialogue = o;
        }
    }

    /** Ecouteur des dictons ({@code data/pioneers/lore/dictons.json}). */
    public static final class DictonListener extends SimpleJsonResourceReloadListener {

        public static final DictonListener INSTANCE = new DictonListener();

        private DictonListener() {
            super(new com.google.gson.Gson(), "lore");
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
            JsonElement e = map.get(Pioneers.rl("dictons"));
            if (e == null || !e.isJsonObject()) {
                return;
            }
            Map<String, List<String>> m = new HashMap<>();
            e.getAsJsonObject().entrySet().forEach(x -> {
                List<String> l = new ArrayList<>();
                x.getValue().getAsJsonArray().forEach(y -> l.add(y.getAsString()));
                m.put(x.getKey(), List.copyOf(l));
            });
            dictons = m;
        }
    }
}
