package fr.pioneers.guide.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.pioneers.Pioneers;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Contenu du guide, lu dans {@code assets/pioneers/guide/<langue>.json} (repli sur l'anglais) : un pack de ressources peut le
 * remplacer ou le completer. Format :
 * <pre>
 * {"chapters": [{"id": "intro", "icon": "pioneers:rocket_tier_1", "title": "...", "short": "...", "group": "ages",
 *                "pages": [{"section": "...", "title": "...", "items": ["mekanism:steel_casing"], "text": "Paragraphe 1\n\nParagraphe 2"}]}]}
 * </pre>
 * Le texte accepte les codes de mise en forme de Minecraft (§l, §6...). Une page trop longue est decoupee automatiquement.
 * <p>
 * Diagramme facultatif {@code "flow"} : une liste de rangees, chaque rangee une chaine de noeuds relies par des fleches
 * ({@code {"item": "...", "label": "..."}}, ou une etiquette seule {@code {"label": "..."}} = etape ; {@code "done": true} = etape cle,
 * encadree en or). Les rangees s'empilent, reliees par une fleche vers le bas si la rangee suivante a {@code "next": true} sur son
 * premier noeud. Le diagramme s'affiche en haut de la page, le texte en dessous.
 * <p>
 * Refonte du guide (1.0.1-beta), champs facultatifs d'une page : {@code "links": [{"chapter": "id", "page": n}]} (renvois cliquables, leur
 * libelle est le titre de la page visee), {@code "advancement": "pioneers:bronze/cast"} (progres qui coche une etape du Periple),
 * {@code "special": "progress"} (page « Ou en suis-je ? », dessinee d'apres les progres du joueur : {@link GuideProgress}). 1.0.3-beta (lot 2) :
 * {@code "goal"} (objets du but d'une etape du Periple), {@code "special": "hud"} (reglages de l'affichage a l'ecran : {@link JourneyHud}).
 */
public final class GuideBook {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Une page ; {@code section} : sous-partie du chapitre (vide si aucune), rappelee en haut a droite ; {@code links} : renvois ;
     * {@code advancement} : progres qui la coche (vide si aucun) ; {@code special} : page dessinee a part (vide, ou « progress »).
     */
    public record Page(String section, String title, List<ItemStack> items, String text, List<List<Node>> flow, List<Link> links,
                       String advancement, String special, List<ItemStack> goal) {}

    /** Renvoi vers une page : chapitre (identifiant) et indice de la page. */
    public record Link(String chapter, int page) {}

    /** Noeud de diagramme : objet (ou rien), legende, etape cle, relie a la rangee precedente. */
    public record Node(ItemStack stack, String label, boolean key, boolean next) {}

    /**
     * Chapitre ; 1.4.2 : {@code group} = groupe du sommaire (start, ages, space, mechanics, help), {@code shortTitle} = titre sans le prefixe
     * « Mécanique : » (sommaire).
     */
    public record Chapter(String id, ItemStack icon, String title, String shortTitle, String group, List<Page> pages) {}

    private static List<Chapter> cache;
    private static String cachedLanguage;

    private GuideBook() {
    }

    public static List<Chapter> chapters() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cache == null || !language.equals(cachedLanguage)) {
            cachedLanguage = language;
            cache = load(language);
            if (cache.isEmpty() && !"en_us".equals(language)) {
                cache = load("en_us");
            }
        }
        return cache;
    }

    /** Recharge au prochain affichage (changement de langue ou de pack de ressources). */
    public static void invalidate() {
        cache = null;
    }

    private static List<Chapter> load(String language) {
        List<Chapter> chapters = new ArrayList<>();
        ResourceLocation location = Pioneers.rl("guide/" + language + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            return chapters;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement chapterElement : GsonHelper.getAsJsonArray(root, "chapters")) {
                JsonObject chapter = chapterElement.getAsJsonObject();
                List<Page> pages = new ArrayList<>();
                for (JsonElement pageElement : GsonHelper.getAsJsonArray(chapter, "pages")) {
                    JsonObject page = pageElement.getAsJsonObject();
                    List<ItemStack> items = new ArrayList<>();
                    JsonArray array = GsonHelper.getAsJsonArray(page, "items", new JsonArray());
                    for (JsonElement id : array) {
                        ItemStack stack = stack(id.getAsString());
                        if (!stack.isEmpty()) {
                            items.add(stack);
                        }
                    }
                    List<List<Node>> flow = new ArrayList<>();
                    for (JsonElement rowElement : GsonHelper.getAsJsonArray(page, "flow", new JsonArray())) {
                        List<Node> row = new ArrayList<>();
                        for (JsonElement nodeElement : rowElement.getAsJsonArray()) {
                            JsonObject node = nodeElement.getAsJsonObject();
                            row.add(new Node(stack(GsonHelper.getAsString(node, "item", "")), GsonHelper.getAsString(node, "label", ""),
                                  GsonHelper.getAsBoolean(node, "done", false), GsonHelper.getAsBoolean(node, "next", false)));
                        }
                        if (!row.isEmpty()) {
                            flow.add(row);
                        }
                    }
                    List<Link> links = new ArrayList<>();
                    for (JsonElement linkElement : GsonHelper.getAsJsonArray(page, "links", new JsonArray())) {
                        JsonObject link = linkElement.getAsJsonObject();
                        links.add(new Link(GsonHelper.getAsString(link, "chapter", ""), GsonHelper.getAsInt(link, "page", 0)));
                    }
                    //1.0.3-beta (lot 2) : but d'une etape du Periple (objets montres par l'affichage a l'ecran)
                    List<ItemStack> goal = new ArrayList<>();
                    for (JsonElement id : GsonHelper.getAsJsonArray(page, "goal", new JsonArray())) {
                        ItemStack stack = stack(id.getAsString());
                        if (!stack.isEmpty()) {
                            goal.add(stack);
                        }
                    }
                    pages.add(new Page(GsonHelper.getAsString(page, "section", ""), GsonHelper.getAsString(page, "title", ""), items,
                          GsonHelper.getAsString(page, "text", ""), flow, links, GsonHelper.getAsString(page, "advancement", ""),
                          GsonHelper.getAsString(page, "special", ""), goal));
                }
                ItemStack icon = stack(GsonHelper.getAsString(chapter, "icon", "minecraft:book"));
                String title = GsonHelper.getAsString(chapter, "title");
                chapters.add(new Chapter(GsonHelper.getAsString(chapter, "id"), icon.isEmpty() ? new ItemStack(Items.BOOK) : icon, title,
                      GsonHelper.getAsString(chapter, "short", title), GsonHelper.getAsString(chapter, "group", "ages"), pages));
            }
        } catch (Exception e) {
            LOGGER.error("Guide de Pioneer's illisible : {}", location, e);
        }
        return chapters;
    }

    /** Indice du chapitre d'identifiant {@code id}, ou -1. */
    public static int indexOf(List<Chapter> chapters, String id) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack stack(String id) {
        if (id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : ForgeRegistries.ITEMS.getValue(location);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
