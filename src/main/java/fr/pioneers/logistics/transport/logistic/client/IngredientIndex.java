package fr.pioneers.logistics.transport.logistic.client;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.guide.client.GuideSearch;
import fr.pioneers.logistics.transport.logistic.ChannelKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.12.1 (F1, demande de Guillaume : « une mini frame JEI dans la config pour voir ce qu'on recherche ») : index des ingredients que le filtre
 * d'un canal peut viser, construit cote client depuis les registres (marche sans JEI) : objets, fluides (sources) ou gaz selon le type du
 * canal. Une entree = cle de filtre ({@code mod:id}), nom affiche, nom replie (sans accents, minuscules), espace de noms, tags.
 * <p>
 * Recherche facon JEI : mots separes par des espaces, tous exiges ; {@code @mod} = espace de noms ; {@code #tag} = un tag qui contient le
 * texte ; sinon le nom ou l'identifiant contient le texte. Index construit a la premiere ouverture (par type et par langue), garde ensuite.
 */
@OnlyIn(Dist.CLIENT)
final class IngredientIndex {

    /** Ingredient : cle de filtre, nom, nom replie, mod, tags ({@code mod:chemin}), icone (objet ou seau) ou texture teintee (fluide, gaz). */
    record Entry(String key, Component name, String folded, String namespace, List<String> tags, ItemStack icon, @Nullable FluidStack fluid,
                 @Nullable Gas gas) {
    }

    private static final Map<ChannelKind, List<Entry>> CACHE = new EnumMap<>(ChannelKind.class);
    private static String language = "";

    private IngredientIndex() {
    }

    /** Le type de canal a-t-il des ingredients a choisir (objets, fluides, gaz) ? */
    static boolean supports(ChannelKind kind) {
        return kind == ChannelKind.ITEM || kind == ChannelKind.FLUID || kind == ChannelKind.GAS;
    }

    static List<Entry> all(ChannelKind kind) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!lang.equals(language)) {
            CACHE.clear();
            language = lang;
        }
        return CACHE.computeIfAbsent(kind, IngredientIndex::build);
    }

    private static List<Entry> build(ChannelKind kind) {
        List<Entry> out = new ArrayList<>();
        switch (kind) {
            case ITEM -> {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR) {
                        continue;
                    }
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    ItemStack stack = new ItemStack(item);
                    List<String> tags = new ArrayList<>();
                    stack.getTags().forEach(t -> tags.add(t.location().toString()));
                    Component name = stack.getHoverName();
                    out.add(new Entry(id.toString(), name, fold(name.getString() + " " + id.getPath()), id.getNamespace(), tags, stack, null, null));
                }
            }
            case FLUID -> {
                for (Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
                    if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
                        continue;
                    }
                    ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
                    if (id == null) {
                        continue;
                    }
                    FluidStack stack = new FluidStack(fluid, 1000);
                    List<String> tags = new ArrayList<>();
                    var tagManager = ForgeRegistries.FLUIDS.tags();
                    if (tagManager != null) {
                        tagManager.getReverseTag(fluid).ifPresent(r -> r.getTagKeys().forEach(t -> tags.add(t.location().toString())));
                    }
                    Item bucket = fluid.getBucket();
                    Component name = stack.getDisplayName();
                    out.add(new Entry(id.toString(), name, fold(name.getString() + " " + id.getPath()), id.getNamespace(), tags,
                          bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket), stack, null));
                }
            }
            case GAS -> {
                for (Gas gas : PioneersAPI.allGases()) {
                    ResourceLocation id = gas.getRegistryName();
                    if (id == null) {
                        continue;
                    }
                    Component name = gas.getName();
                    out.add(new Entry(id.toString(), name, fold(name.getString() + " " + id.getPath()), id.getNamespace(), List.of(), ItemStack.EMPTY,
                          null, gas));
                }
            }
            default -> {
            }
        }
        out.sort((a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    /** Texte replie : minuscules, sans accents (meme repli que la recherche du guide). */
    static String fold(String text) {
        return GuideSearch.fold(text.toLowerCase(Locale.ROOT));
    }

    /** Ingredients qui repondent a la recherche (tous les mots exiges). */
    static List<Entry> search(ChannelKind kind, String query) {
        List<Entry> all = all(kind);
        String[] words = fold(query.trim()).split("\\s+");
        if (query.isBlank()) {
            return all;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            boolean ok = true;
            for (String w : words) {
                if (w.isEmpty()) {
                    continue;
                }
                if (w.startsWith("@")) {
                    ok = e.namespace().contains(w.substring(1));
                } else if (w.startsWith("#")) {
                    String t = w.substring(1);
                    ok = false;
                    for (String tag : e.tags()) {
                        if (tag.contains(t)) {
                            ok = true;
                            break;
                        }
                    }
                } else {
                    ok = e.folded().contains(w) || e.key().contains(w);
                }
                if (!ok) {
                    break;
                }
            }
            if (ok) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Tags proposes : ceux qui contiennent le texte d'un mot {@code #...} de la recherche ; sinon ceux de l'ingredient survole (ou choisi).
     * Au plus {@code max}, les plus courts d'abord (les plus generaux).
     */
    static List<String> tags(ChannelKind kind, String query, @Nullable Entry focus, int max) {
        TreeSet<String> found = new TreeSet<>((a, b) -> a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b));
        String tagWord = null;
        for (String w : fold(query.trim()).split("\\s+")) {
            if (w.startsWith("#") && w.length() > 1) {
                tagWord = w.substring(1);
            }
        }
        if (tagWord != null) {
            for (Entry e : all(kind)) {
                for (String tag : e.tags()) {
                    if (tag.contains(tagWord)) {
                        found.add(tag);
                    }
                }
            }
        } else if (focus != null) {
            found.addAll(focus.tags());
        }
        List<String> out = new ArrayList<>();
        for (String t : found) {
            if (out.size() >= max) {
                break;
            }
            out.add(t);
        }
        return out;
    }

    /** Ingredient d'une cle de filtre (icone et nom des entrees deja choisies), null si inconnu (tag, mod, id absent). */
    @Nullable
    static Entry find(ChannelKind kind, String key) {
        if (!supports(kind) || key.startsWith("#") || key.startsWith("@")) {
            return null;
        }
        for (Entry e : all(kind)) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }
}
