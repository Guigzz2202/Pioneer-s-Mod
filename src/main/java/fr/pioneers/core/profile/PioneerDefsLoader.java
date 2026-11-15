package fr.pioneers.core.profile;

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
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9 (MAJ RP) : lit {@code data/<ns>/pioneer/skills/*.json}, {@code pioneer/vocations/*.json} et {@code pioneer/xp_sources.json}. Un fichier
 * invalide est refuse avec un message clair (journal) et ignore ; le reste se charge. Les definitions sont envoyees aux clients par
 * {@link ProfileEvents} (connexion et /reload).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PioneerDefsLoader extends SimplePreparableReloadListener<PioneerDefs> {

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new PioneerDefsLoader());
    }

    @Override
    protected @NotNull PioneerDefs prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<String, PioneerDefs.Skill> skills = new TreeMap<>();
        Map<String, PioneerDefs.Vocation> vocations = new TreeMap<>();
        Map<String, PioneerDefs.Source> sources = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("pioneer/skills", rl -> rl.getPath().endsWith(".json")).entrySet()) {
            String id = name(e.getKey());
            try {
                skills.put(id, skill(id, read(e.getValue()).getAsJsonObject()));
            } catch (Exception ex) {
                Pioneers.LOGGER.error("Skill refuse {} : {}", e.getKey(), ex.toString());
            }
        }
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("pioneer/vocations", rl -> rl.getPath().endsWith(".json")).entrySet()) {
            String id = name(e.getKey());
            try {
                vocations.put(id, vocation(id, read(e.getValue()).getAsJsonObject()));
            } catch (Exception ex) {
                Pioneers.LOGGER.error("Vocation refusee {} : {}", e.getKey(), ex.toString());
            }
        }
        manager.getResource(Pioneers.rl("pioneer/xp_sources.json")).ifPresent(res -> {
            try {
                JsonObject o = read(res).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    JsonObject s = e.getValue().getAsJsonObject();
                    sources.put(e.getKey(), new PioneerDefs.Source(s.get("skill").getAsString(), s.get("xp").getAsDouble()));
                }
            } catch (Exception ex) {
                Pioneers.LOGGER.error("Gains d'XP refuses (pioneer/xp_sources.json) : {}", ex.toString());
            }
        });
        //Coherence : une vocation ne cite que des skills connus ; une action d'XP aussi
        vocations.values().removeIf(v -> {
            String problem = problem(v, skills.keySet());
            if (problem != null) {
                Pioneers.LOGGER.error("Vocation refusee {} : {}", v.id(), problem);
            }
            return problem != null;
        });
        //1.0.5-beta (lot 13) : une action d'XP au skill inconnu n'est plus ecartee en silence
        sources.entrySet().removeIf(s -> {
            boolean bad = !skills.containsKey(s.getValue().skill());
            if (bad) {
                Pioneers.LOGGER.error("Action d'XP refusee {} : skill inconnu {}", s.getKey(), s.getValue().skill());
            }
            return bad;
        });
        return new PioneerDefs(new LinkedHashMap<>(skills), new LinkedHashMap<>(vocations), new LinkedHashMap<>(sources));
    }

    @Override
    protected void apply(@NotNull PioneerDefs defs, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        PioneerDefs.set(defs);
        manager.getResource(Pioneers.rl("pioneer/reconversion.json")).ifPresent(res -> {
            try {
                JsonObject o = read(res).getAsJsonObject();
                Reconversion.cost = new Reconversion.Cost(o.get("notebook").getAsString(), o.get("notebooks").getAsInt(),
                      o.get("scrap_tag").getAsString(), o.get("scraps").getAsInt(), o.get("categories").getAsInt(),
                      strings(o.getAsJsonArray("category_tags")), strings(o.getAsJsonArray("components_by_age")),
                      o.get("components").getAsInt(), strings(o.getAsJsonArray("age_markers")));
            } catch (Exception ex) {
                Pioneers.LOGGER.error("Cout de reconversion refuse (pioneer/reconversion.json) : {}", ex.toString());
            }
        });
        Pioneers.LOGGER.info("Pionnier : {} skills, {} vocations, {} actions d'XP", defs.skills.size(), defs.vocations.size(), defs.sources.size());
    }

    /** Nombre d'affinites permis d'une vocation non neutre (1.0.5-beta lot 10 : l'Eclaireur en a trois : Combat, Recuperation, Chasse). */
    public static final int MIN_AFFINITIES = 2, MAX_AFFINITIES = 3;

    /** Un skill lu dans son fichier (memes regles en jeu et au banc {@code tools/rp/DefsBench.java}). */
    public static PioneerDefs.Skill skill(String id, JsonObject o) {
        return new PioneerDefs.Skill(id, o.get("order").getAsInt(), str(o, "icon"), str(o, "feature"), mods(o.getAsJsonArray("modifiers")));
    }

    /** Une vocation lue dans son fichier (memes regles en jeu et au banc). */
    public static PioneerDefs.Vocation vocation(String id, JsonObject o) {
        List<String> aff = new ArrayList<>();
        for (JsonElement a : o.getAsJsonArray("affinities")) {
            aff.add(a.getAsString());
        }
        //1.9.1 (retours de Guillaume) : une vocation « neutral » (le Polyvalent) n'a ni affinite, ni avantage, ni defaut
        boolean neutral = o.has("neutral") && o.get("neutral").getAsBoolean();
        return new PioneerDefs.Vocation(id, o.get("order").getAsInt(), List.copyOf(aff), str(o, "feature"), str(o, "emblem"),
              neutral ? PioneerDefs.Trait.NONE : trait(o.getAsJsonObject("advantage")),
              neutral ? PioneerDefs.Trait.NONE : trait(o.getAsJsonObject("defect")));
    }

    /**
     * 1.0.5-beta (lot 13, journal de Guillaume : « Vocation refusee scout ») : PUR, raison du refus d'une vocation, null si elle est acceptee.
     * Une vocation non neutre a 2 ou 3 affinites distinctes, toutes des skills connus, un avantage et un defaut (avant : exactement 2, ce
     * qui refusait l'Eclaireur et ses trois affinites depuis le lot 10).
     */
    @Nullable
    public static String problem(PioneerDefs.Vocation v, Set<String> skills) {
        if (v.neutral()) {
            return null;
        }
        List<String> a = v.affinities();
        if (a.size() < MIN_AFFINITIES || a.size() > MAX_AFFINITIES) {
            return "affinites " + a + " : il en faut de " + MIN_AFFINITIES + " a " + MAX_AFFINITIES;
        }
        if (a.stream().distinct().count() != a.size()) {
            return "affinites en double " + a;
        }
        for (String s : a) {
            if (!skills.contains(s)) {
                return "affinite inconnue " + s + " (skills " + skills + ")";
            }
        }
        if (!v.advantage().present() || !v.defect().present()) {
            return "avantage ou defaut manquant";
        }
        return null;
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            a.forEach(e -> out.add(e.getAsString()));
        }
        return List.copyOf(out);
    }

    private static PioneerDefs.Trait trait(JsonObject t) {
        return new PioneerDefs.Trait(t.get("trait").getAsString(), mods(t.getAsJsonArray("modifiers")));
    }

    private static String name(ResourceLocation rl) {
        String p = rl.getPath();
        return p.substring(p.lastIndexOf('/') + 1, p.length() - 5);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static List<PioneerDefs.Mod> mods(JsonArray a) {
        List<PioneerDefs.Mod> out = new ArrayList<>();
        if (a != null) {
            for (JsonElement e : a) {
                JsonObject m = e.getAsJsonObject();
                out.add(new PioneerDefs.Mod(m.get("key").getAsString(), m.get("value").getAsDouble(), "abs".equals(str(m, "unit"))));
            }
        }
        return List.copyOf(out);
    }

    private static JsonElement read(Resource res) throws Exception {
        try (Reader r = res.openAsReader()) {
            return JsonParser.parseReader(r);
        }
    }
}
