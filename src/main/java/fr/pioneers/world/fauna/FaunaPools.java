package fr.pioneers.world.fauna;

import fr.pioneers.world.season.BiomeClimates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * 1.0.5-beta (lot 4) : especes possibles dans un biome, par lieu ({@link FaunaSpecies.Where}). Especes « listees » : poids et bandes lus
 * dans les listes d'apparition du biome (toutes categories, modificateurs de biome compris) ; especes declarees : leurs biomes, poids et
 * bandes. Les especes « persistantes seulement » n'y sont jamais. Cache par biome, vide au rechargement des tags et a chaque inscription.
 */
public final class FaunaPools {

    private FaunaPools() {
    }

    /** Une espece possible dans un biome : poids et bande. */
    public record Entry(FaunaSpecies species, EntityType<?> type, FaunaSpecies.Where where, int weight, int min, int max) {
    }

    private static final Map<Biome, List<Entry>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TagKey<Biome>> TAGS = new ConcurrentHashMap<>();

    public static void clear() {
        CACHE.clear();
    }

    /** Toutes les especes possibles dans ce biome. */
    public static List<Entry> of(Holder<Biome> biome) {
        List<Entry> l = CACHE.get(biome.value());
        if (l == null) {
            l = Collections.unmodifiableList(build(biome));
            CACHE.put(biome.value(), l);
        }
        return l;
    }

    /** Especes de ce biome pour un lieu (budget de la generation). */
    public static List<Entry> of(Holder<Biome> biome, FaunaSpecies.Where where) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : of(biome)) {
            if (e.where == where) {
                out.add(e);
            }
        }
        return out;
    }

    /** Especes de ce biome pour un groupe (repeuplement). */
    public static List<Entry> of(Holder<Biome> biome, FaunaGroup group) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : of(biome)) {
            if (e.species.group == group) {
                out.add(e);
            }
        }
        return out;
    }

    /** Probabilite d'une bande par troncon (et par tirage) pour un lieu : terre = probabilite de creatures du biome (comme le vanilla). */
    public static double chance(Holder<Biome> biome, FaunaSpecies.Where where) {
        return switch (where) {
            case LAND -> biome.value().getMobSettings().getCreatureProbability();
            case WATER -> FaunaRules.WATER_CHANCE;
            case CAVE -> FaunaRules.CAVE_CHANCE;
        };
    }

    private static List<Entry> build(Holder<Biome> biome) {
        List<Entry> out = new ArrayList<>();
        MobSpawnSettings mobs = biome.value().getMobSettings();
        for (FaunaSpecies s : FaunaRegistry.all()) {
            if (s.persistOnly || s.weight <= 0 && !s.listed) {
                continue;
            }
            EntityType<?> type = FaunaRegistry.type(s);
            if (type == null) {
                continue;
            }
            if (s.listed) {
                for (MobCategory cat : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData d : mobs.getMobs(cat).unwrap()) {
                        if (d.type == type && d.getWeight().asInt() > 0) {
                            out.add(new Entry(s, type, s.where != null ? s.where : whereOf(type, cat), d.getWeight().asInt(), Math.max(1, d.minCount),
                                  Math.max(Math.max(1, d.minCount), d.maxCount)));
                        }
                    }
                }
            } else if (matches(biome, s.biomes)) {
                out.add(new Entry(s, type, s.where != null ? s.where : whereOf(type, type.getCategory()), s.weight, s.min, s.max));
            }
        }
        return out;
    }

    /** Lieu d'un type d'apres sa categorie et son placement : grottes (axolotls, calmars luisants), eau, sinon terre. */
    static FaunaSpecies.Where whereOf(EntityType<?> type, MobCategory cat) {
        if (cat == MobCategory.UNDERGROUND_WATER_CREATURE || cat == MobCategory.AXOLOTLS) {
            return FaunaSpecies.Where.CAVE;
        }
        return SpawnPlacements.getPlacementType(type) == SpawnPlacements.Type.IN_WATER ? FaunaSpecies.Where.WATER : FaunaSpecies.Where.LAND;
    }

    /** Biome dans la liste d'une espece : {@code *}, {@code #tag}, {@code family:<id>} ou id. */
    public static boolean matches(Holder<Biome> biome, String[] list) {
        for (String b : list) {
            if (b.equals("*")) {
                if (biome.is(tag("minecraft:is_overworld"))) {
                    return true;
                }
            } else if (b.startsWith("#")) {
                if (biome.is(tag(b.substring(1)))) {
                    return true;
                }
            } else if (b.startsWith("family:")) {
                if (BiomeClimates.of(biome).family().id().equals(b.substring(7))) {
                    return true;
                }
            } else if (biome.unwrapKey().map(k -> k.location().toString().equals(b)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static TagKey<Biome> tag(String id) {
        return TAGS.computeIfAbsent(id, s -> TagKey.create(Registries.BIOME, new ResourceLocation(s)));
    }

    /** Id d'un type (journal, commande). */
    static String id(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? "?" : id.toString();
    }
}
