package fr.pioneers.client.rp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

/**
 * 1.9 (MAJ RP) : index client du Codex ({@code assets/pioneers/codex/entries.json}, ecrit par {@code tools/gc/rp/lore_data.py}) : chapitre,
 * tier, genre et date de chaque entree, liste des notes de parchemin. Les textes sont dans les langues ({@code codex.pioneers.<id>.*}).
 */
public final class CodexBook {

    public record Entry(String id, int chapter, String tier, String genre, @Nullable Integer date) {
    }

    private static Map<String, Entry> entries;
    private static List<String> notes;
    public static final int CHAPTERS = 13, NOTES_CHAPTER = 11, TRADES_CHAPTER = 12;

    private CodexBook() {
    }

    private static void load() {
        if (entries != null) {
            return;
        }
        entries = new LinkedHashMap<>();
        notes = new ArrayList<>();
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(Pioneers.rl("codex/entries.json")).orElse(null);
            if (res == null) {
                return;
            }
            try (Reader r = res.openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                for (JsonElement e : o.getAsJsonArray("entries")) {
                    JsonObject x = e.getAsJsonObject();
                    Integer date = x.has("date") && !x.get("date").isJsonNull() ? x.get("date").getAsInt() : null;
                    entries.put(x.get("id").getAsString(), new Entry(x.get("id").getAsString(), x.get("chapter").getAsInt(),
                          x.get("tier").getAsString(), x.get("genre").getAsString(), date));
                }
                JsonArray n = o.getAsJsonArray("notes");
                n.forEach(e -> notes.add(e.getAsString()));
            }
        } catch (Exception ex) {
            Pioneers.LOGGER.warn("Codex illisible : {}", ex.toString());
        }
    }

    /** A vider au rechargement des ressources (langue, pack). */
    public static void invalidate() {
        entries = null;
        notes = null;
    }

    public static Map<String, Entry> entries() {
        load();
        return entries;
    }

    public static List<String> notes() {
        load();
        return notes;
    }

    public static List<Entry> chapter(int chapter) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries().values()) {
            if (e.chapter() == chapter) {
                out.add(e);
            }
        }
        return out;
    }

    @Nullable
    public static Entry get(String id) {
        return entries().get(id);
    }
}
