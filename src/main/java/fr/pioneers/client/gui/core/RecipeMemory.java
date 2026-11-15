package fr.pioneers.client.gui.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : MEMOIRE DU SELECTEUR DE RECETTE, cote client seulement (rien ne part au serveur) : recettes verrouillees
 * recemment (communes a toutes les machines, la plus recente d'abord) et FAVORIS par machine (cle = source des recettes de la machine,
 * la meme pour ses quatre tiers). Un seul fichier, celui des recentes de la 1.11.2 : {@code <jeu>/pioneers/recipe_recents.txt} — une
 * recette recente par ligne (format d'avant, relu tel quel), puis les favoris en lignes {@code fav <source> <recette>} (ignorees par un
 * jeu d'avant le lot 2 : elles ne sont pas des identifiants). Fichier illisible ou absent : on repart a vide, jamais d'erreur.
 * <p>
 * Lot 3 : etat OUVERT / FERME du carre du selecteur, choisi par le joueur, par machine (source des recettes) et selon qu'une recette est
 * verrouillee ou non : lignes {@code square <source> <locked|free> <open|closed>} (ignorees par les jeux d'avant : pas des identifiants).
 * Sans choix memorise, le carre s'ouvre si aucune recette n'est verrouillee (il faut en choisir une), et reste ferme sinon (la machine est
 * reglee : on veut la voir).
 */
public final class RecipeMemory {

    private static final int RECENT_KEEP = 32;
    private static final List<ResourceLocation> RECENT = new ArrayList<>();
    private static final Map<String, Set<ResourceLocation>> FAVORITES = new LinkedHashMap<>();
    /** Lot 3 : carre ouvert (vrai) ou ferme, par « source verrou » ({@code basic_crusher locked}). */
    private static final Map<String, Boolean> SQUARE = new LinkedHashMap<>();
    private static boolean loaded;

    private RecipeMemory() {
    }

    /** Recettes verrouillees recemment (la plus recente d'abord). */
    public static List<ResourceLocation> recent() {
        load();
        return RECENT;
    }

    public static boolean isRecent(ResourceLocation id) {
        return recent().contains(id);
    }

    /** Une recette vient d'etre verrouillee : en tete des recentes. */
    public static void remember(@Nullable ResourceLocation id) {
        if (id == null) {
            return;
        }
        load();
        RECENT.remove(id);
        RECENT.add(0, id);
        while (RECENT.size() > RECENT_KEEP) {
            RECENT.remove(RECENT.size() - 1);
        }
        save();
    }

    public static boolean isFavorite(String source, ResourceLocation id) {
        load();
        Set<ResourceLocation> set = FAVORITES.get(source);
        return set != null && set.contains(id);
    }

    /** Favoris de la machine (source de ses recettes). */
    public static Set<ResourceLocation> favorites(String source) {
        load();
        return FAVORITES.getOrDefault(source, Set.of());
    }

    /** Ajoute ou retire un favori ; vrai si la recette est maintenant favorite. */
    public static boolean toggleFavorite(String source, ResourceLocation id) {
        load();
        Set<ResourceLocation> set = FAVORITES.computeIfAbsent(source, s -> new LinkedHashSet<>());
        boolean now = !set.remove(id);
        if (now) {
            set.add(id);
        }
        if (set.isEmpty()) {
            FAVORITES.remove(source);
        }
        save();
        return now;
    }

    /**
     * Lot 3 : le carre du selecteur doit-il s'ouvrir ? Choix memorise du joueur pour cette machine dans ce cas (recette verrouillee ou
     * non), sinon : ouvert si aucune recette n'est verrouillee. 1.0.2-beta : plus lu (le carre est toujours ferme a l'ouverture,
     * {@code GuiMachine.init}) ; les lignes « square » du fichier restent relues sans effet.
     */
    public static boolean squareOpen(String source, boolean locked) {
        load();
        Boolean open = SQUARE.get(squareKey(source, locked));
        return open != null ? open : !locked;
    }

    /** Lot 3 : le joueur vient d'ouvrir ou de fermer le carre (bouton, touche, Echap) : retenu pour cette machine dans ce cas. */
    public static void setSquareOpen(String source, boolean locked, boolean open) {
        load();
        Boolean was = SQUARE.put(squareKey(source, locked), open);
        if (was == null || was != open) {
            save();
        }
    }

    private static String squareKey(String source, boolean locked) {
        return source + " " + (locked ? "locked" : "free");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path file = file();
            if (file != null && Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String l = line.trim();
                    if (l.startsWith("square ")) {
                        String[] p = l.split("\\s+");
                        if (p.length == 4 && (p[2].equals("locked") || p[2].equals("free"))) {
                            SQUARE.put(p[1] + " " + p[2], p[3].equals("open"));
                        }
                        continue;
                    }
                    if (l.startsWith("fav ")) {
                        String[] p = l.split("\\s+");
                        ResourceLocation id = p.length == 3 ? ResourceLocation.tryParse(p[2]) : null;
                        if (id != null) {
                            FAVORITES.computeIfAbsent(p[1], s -> new LinkedHashSet<>()).add(id);
                        }
                        continue;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(l);
                    if (id != null && !RECENT.contains(id) && RECENT.size() < RECENT_KEEP) {
                        RECENT.add(id);
                    }
                }
            }
        } catch (Exception ignored) {
            //Fichier illisible : on repart sans recentes ni favoris
        }
    }

    private static void save() {
        try {
            Path file = file();
            if (file == null) {
                return;
            }
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            RECENT.forEach(r -> lines.add(r.toString()));
            FAVORITES.forEach((source, ids) -> ids.forEach(id -> lines.add("fav " + source + " " + id)));
            SQUARE.forEach((key, open) -> lines.add("square " + key + " " + (open ? "open" : "closed")));
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            //Rien de grave : la memoire restera celle de la partie
        }
    }

    @Nullable
    private static Path file() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.gameDirectory == null ? null : mc.gameDirectory.toPath().resolve("pioneers").resolve("recipe_recents.txt");
    }
}
