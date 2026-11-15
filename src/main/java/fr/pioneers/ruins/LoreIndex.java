package fr.pioneers.ruins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : index serveur du Codex ({@code data/pioneers/lore/codex.json}, ecrit par {@code tools/gc/rp/lore_data.py}) : palier
 * de chaque entree (mk1, mk2, mk3, servitor, trade) et liste des notes. Sert aux tirages des Armoires et des coffres (les ecrits que le joueur
 * n'a pas lus passent d'abord).
 */
public final class LoreIndex extends SimpleJsonResourceReloadListener {

    public static final LoreIndex INSTANCE = new LoreIndex();
    private static volatile Map<String, String> tiers = Map.of();
    private static volatile Map<String, List<String>> byTier = Map.of();
    private static volatile List<String> notes = List.of();

    private LoreIndex() {
        super(new com.google.gson.Gson(), "lore");
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, String> t = new HashMap<>();
        Map<String, List<String>> b = new HashMap<>();
        List<String> n = new ArrayList<>();
        JsonElement e = map.get(Pioneers.rl("codex"));
        if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            o.getAsJsonObject("entries").entrySet().forEach(en -> {
                String tier = en.getValue().getAsString();
                t.put(en.getKey(), tier);
                b.computeIfAbsent(tier, k -> new ArrayList<>()).add(en.getKey());
            });
            o.getAsJsonArray("notes").forEach(x -> n.add(x.getAsString()));
        } else {
            Pioneers.LOGGER.warn("Codex serveur absent (data/pioneers/lore/codex.json)");
        }
        tiers = t;
        byTier = b;
        notes = n;
    }

    /** 1.0.5-beta (lot 19) : nombre d'ecrits du Codex (entrees et notes) : le bac d'archives doit pouvoir tous les ranger. */
    public static int size() {
        return tiers.size() + notes.size();
    }

    public static String tier(String entry) {
        return tiers.getOrDefault(entry, "");
    }

    /** Une entree du palier, de preference parmi celles que le joueur n'a pas lues ({@code read} peut etre vide). */
    @Nullable
    public static String pick(String tier, Set<String> read, RandomSource random) {
        return pickFrom(byTier.getOrDefault(tier, List.of()), read, random);
    }

    @Nullable
    public static String pickNote(Set<String> read, RandomSource random) {
        return pickFrom(notes, read, random);
    }

    @Nullable
    private static String pickFrom(List<String> all, Set<String> read, RandomSource random) {
        if (all.isEmpty()) {
            return null;
        }
        List<String> unread = all.stream().filter(s -> !read.contains(s)).toList();
        List<String> from = unread.isEmpty() ? all : unread;
        return from.get(random.nextInt(from.size()));
    }
}
