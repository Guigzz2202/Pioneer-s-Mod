package fr.pioneers.season;

import fr.pioneers.season.SeasonClimate.Family;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.0.5-beta (lot 1) : famille climatique d'un biome du jeu ({@link SeasonClimate#classify} sur ses tags et son id), gardee en cache par
 * biome (vide a chaque rechargement des tags). Serveur et client ont chacun leurs biomes : le cache est partage sans risque (cle = l'objet).
 */
public final class BiomeClimates {

    /** Famille et saison des pluies d'un biome. */
    public record Info(Family family, boolean savanna) {
    }

    private static final Map<Biome, Info> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TagKey<Biome>> TAGS = new ConcurrentHashMap<>();

    private BiomeClimates() {
    }

    /** Vide le cache (rechargement des tags : {@link SeasonEvents#onTags} ; lot 2 : plus d'abonnement dans l'initialisation statique, que les
     *  bancs hors jeu ne peuvent pas executer). */
    public static void clear() {
        CACHE.clear();
    }

    /** Famille deja calculee pour ce biome, sinon null (lot 2 : resolveurs de couleur, appeles sans Holder). */
    @javax.annotation.Nullable
    public static Info cached(Biome biome) {
        return CACHE.get(biome);
    }

    public static Info of(Holder<Biome> biome) {
        Info info = CACHE.get(biome.value());
        if (info == null) {
            String path = biome.unwrapKey().map(k -> k.location().getPath()).orElse("");
            float base = biome.value().getModifiedClimateSettings().temperature();
            info = new Info(SeasonClimate.classify(path, t -> biome.is(tag(t)), base), SeasonClimate.savanna(path, t -> biome.is(tag(t))));
            CACHE.put(biome.value(), info);
        }
        return info;
    }

    private static TagKey<Biome> tag(String id) {
        return TAGS.computeIfAbsent(id, s -> TagKey.create(Registries.BIOME, new ResourceLocation(s)));
    }
}
