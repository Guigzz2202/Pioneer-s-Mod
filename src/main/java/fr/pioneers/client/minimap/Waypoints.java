package fr.pioneers.client.minimap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * 1.10.3 : waypoints du monde (ou du serveur) en cours, toutes dimensions, sauves cote client a chaque changement (fichier ecrit a cote
 * puis renomme : jamais de fichier a moitie ecrit). Points de mort : les {@code deathKeep} plus recents sont gardes.
 */
public final class Waypoints {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Couleurs proposees (pastilles de l'ecran d'edition), la premiere sert par defaut. */
    public static final int[] PALETTE = {0x3FD0A0, 0x5FB8E8, 0xF2D45C, 0xF07A3C, 0xE5484D, 0xC86CE0, 0xFFFFFF, 0x8FA3A0, 0x6CD46C, 0x3E7FD8,
          0xD8B070, 0xFF8FC8};

    private static final List<Waypoint> ALL = new ArrayList<>();
    @Nullable
    private static Path file;
    private static int version;

    private Waypoints() {
    }

    static void open(@Nullable Path dir) {
        ALL.clear();
        version++;
        file = dir == null ? null : dir.resolve("waypoints.json");
        if (file == null || !Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            JsonArray arr = root.isJsonObject() ? root.getAsJsonObject().getAsJsonArray("waypoints") : root.getAsJsonArray();
            if (arr != null) {
                for (JsonElement e : arr) {
                    if (e.isJsonObject()) {
                        ALL.add(Waypoint.load(e.getAsJsonObject()));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Pioneers.LOGGER.warn("Minimap : waypoints illisibles ({})", file, e);
        }
    }

    static void close() {
        ALL.clear();
        file = null;
        version++;
    }

    public static void save() {
        version++;
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonArray arr = new JsonArray();
        for (Waypoint w : ALL) {
            arr.add(w.save());
        }
        root.add("waypoints", arr);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("waypoints.json.tmp");
            try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, out);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Pioneers.LOGGER.warn("Minimap : waypoints non sauves ({})", file, e);
        }
    }

    /** Waypoints d'une dimension (copie de la liste, dans l'ordre de creation). */
    public static List<Waypoint> in(String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : ALL) {
            if (w.dim.equals(dim)) {
                out.add(w);
            }
        }
        return out;
    }

    private static List<Waypoint> cache = List.of();
    private static String cacheDim = "";
    private static int cacheVersion = -1;

    /** Waypoints d'une dimension, gardes jusqu'au prochain changement (le dessin les lit a chaque image). */
    public static List<Waypoint> cached(String dim) {
        if (cacheVersion != version || !cacheDim.equals(dim)) {
            cache = in(dim);
            cacheDim = dim;
            cacheVersion = version;
        }
        return cache;
    }

    /** Liste triee par distance au joueur. */
    public static List<Waypoint> sorted(String dim, double x, double y, double z) {
        List<Waypoint> out = in(dim);
        out.sort(Comparator.comparingDouble(w -> w.distanceTo(x, y, z)));
        return out;
    }

    public static void add(Waypoint w) {
        ALL.add(w);
        save();
    }

    public static void remove(Waypoint w) {
        ALL.remove(w);
        save();
    }

    /** Remplace {@code old} (meme objet) par {@code updated} apres une edition. */
    public static void replace(Waypoint old, Waypoint updated) {
        int i = ALL.indexOf(old);
        if (i >= 0) {
            ALL.set(i, updated);
        } else {
            ALL.add(updated);
        }
        save();
    }

    /** Point de mort : un waypoint de plus, les plus anciens au-dela de {@code keep} retires. */
    static void death(Waypoint w, int keep) {
        w.death = true;
        ALL.add(w);
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint o : ALL) {
            if (o.death) {
                deaths.add(o);
            }
        }
        deaths.sort(Comparator.comparingLong(o -> o.created));
        for (int i = 0; i < deaths.size() - keep; i++) {
            ALL.remove(deaths.get(i));
        }
        save();
    }

    public static int version() {
        return version;
    }

    public static boolean ready() {
        return file != null;
    }
}
