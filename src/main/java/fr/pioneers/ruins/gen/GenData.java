package fr.pioneers.ruins.gen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : donnees de la generation des ruines, rechargees avec les donnees du serveur : reglages
 * ({@code ruins/generation.json}) et gabarits d'annexes ({@code ruins/annexes/*.json}). Un instantane immuable publie d'un coup (lu par les
 * fils de generation sans verrou), vide a l'arret du serveur.
 */
public final class GenData extends SimpleJsonResourceReloadListener {

    /** Instantane : reglages, biomes exclus par tier, gabarits d'annexes par sorte (ordre stable : tri par nom), sortes dans l'ordre. */
    public record Snapshot(GenSettings settings, BiomeRules biomes, Map<String, List<AnnexTemplate>> annexes, List<String> types) {

        /** Sortes d'annexes possibles a ce tier (ordre stable). */
        public List<String> typesFor(int tier) {
            List<String> out = new ArrayList<>();
            for (String t : types) {
                List<AnnexTemplate> l = annexes.get(t);
                if (l != null && !l.isEmpty() && l.get(0).minTier() <= tier) {
                    out.add(t);
                }
            }
            return out;
        }
    }

    /** Biomes exclus par tier (tags et ids resolus une fois au chargement). */
    public record BiomeRules(List<List<TagKey<Biome>>> tags, List<List<ResourceKey<Biome>>> keys) {

        static BiomeRules of(GenSettings settings) {
            List<List<TagKey<Biome>>> tags = new ArrayList<>();
            List<List<ResourceKey<Biome>>> keys = new ArrayList<>();
            for (int t = 1; t <= RuinGrid.TIERS; t++) {
                List<TagKey<Biome>> tt = new ArrayList<>();
                List<ResourceKey<Biome>> kk = new ArrayList<>();
                for (String id : settings.tier(t).excludedBiomes()) {
                    if (id.startsWith("#")) {
                        tt.add(TagKey.create(Registries.BIOME, new ResourceLocation(id.substring(1))));
                    } else {
                        kk.add(ResourceKey.create(Registries.BIOME, new ResourceLocation(id)));
                    }
                }
                tags.add(List.copyOf(tt));
                keys.add(List.copyOf(kk));
            }
            return new BiomeRules(List.copyOf(tags), List.copyOf(keys));
        }

        public boolean excluded(int tier, Holder<Biome> biome) {
            int t = Math.max(1, Math.min(RuinGrid.TIERS, tier)) - 1;
            for (TagKey<Biome> k : tags.get(t)) {
                if (biome.is(k)) {
                    return true;
                }
            }
            for (ResourceKey<Biome> k : keys.get(t)) {
                if (biome.is(k)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final GenData INSTANCE = new GenData();
    private static volatile Snapshot current;

    private GenData() {
        super(new Gson(), "ruins");
    }

    @Nullable
    public static Snapshot current() {
        return current;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> map, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        GenSettings settings = GenSettings.DEFAULT;
        JsonElement g = map.get(Pioneers.rl("generation"));
        if (g != null && g.isJsonObject()) {
            try {
                settings = GenSettings.parse(g.getAsJsonObject());
            } catch (RuntimeException ex) {
                Pioneers.LOGGER.warn("Reglages des ruines illisibles, valeurs par defaut : {}", ex.toString());
            }
        }
        Map<String, List<AnnexTemplate>> annexes = new TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(map).entrySet()) {
            if (!e.getKey().getPath().startsWith("annexes/") || !e.getValue().isJsonObject()) {
                continue;
            }
            try {
                AnnexTemplate t = AnnexTemplate.parse(e.getValue().getAsJsonObject());
                annexes.computeIfAbsent(t.type(), k -> new ArrayList<>()).add(t);
            } catch (Exception ex) {
                Pioneers.LOGGER.warn("Annexe ignoree ({}) : {}", e.getKey(), ex.toString());
            }
        }
        Map<String, List<AnnexTemplate>> frozen = new LinkedHashMap<>();
        annexes.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        BiomeRules biomes;
        try {
            biomes = BiomeRules.of(settings);
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.warn("Biomes exclus des ruines illisibles : {}", ex.toString());
            settings = GenSettings.DEFAULT;
            biomes = BiomeRules.of(settings);
        }
        current = new Snapshot(settings, biomes, Collections.unmodifiableMap(frozen), List.copyOf(frozen.keySet()));
    }

    static void onReload(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    static void onStopped(ServerStoppedEvent event) {
        current = null;
    }
}
