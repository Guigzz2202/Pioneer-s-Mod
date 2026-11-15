package fr.pioneers.farm.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Milieu d'un point d'eau (habitat de {@link GeneratedFish#HABITATS}) et tirage d'une espece : oceans (froid, tempere, chaud, grands fonds
 * sous y 30), marais, eaux souterraines (sans ciel, sous y 50), eau douce selon la temperature du biome. Les poids sont les raretes ; un appat
 * aplanit les raretes (les especes rares sortent plus souvent).
 */
public final class FishSpawns {

    private FishSpawns() {
    }

    public static final int FRESH_COLD = 0, FRESH_TEMPERATE = 1, FRESH_WARM = 2, SWAMP = 3, CAVE = 4, SEA_COLD = 5, SEA_TEMPERATE = 6, SEA_WARM = 7,
          DEEP = 8;
    private static final TagKey<Biome> SWAMPS = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("forge", "is_swamp"));

    public static int habitat(Level level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        boolean sky = level.getBrightness(LightLayer.SKY, pos) > 0 || level.canSeeSky(pos);
        if (!sky && pos.getY() < 50) {
            return CAVE;
        }
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_BEACH)) {
            if (pos.getY() < 30) {
                return DEEP;
            }
            String key = b.unwrapKey().map(k -> k.location().getPath()).orElse("");
            if (key.contains("warm")) {
                return SEA_WARM;
            }
            if (key.contains("cold") || key.contains("frozen") || b.value().getBaseTemperature() < 0.2F) {
                return SEA_COLD;
            }
            return SEA_TEMPERATE;
        }
        if (b.is(SWAMPS) || b.unwrapKey().map(k -> k.location().getPath().contains("swamp")).orElse(false)) {
            return SWAMP;
        }
        float t = b.value().getBaseTemperature();
        return t < 0.3F ? FRESH_COLD : t > 0.9F ? FRESH_WARM : FRESH_TEMPERATE;
    }

    /** Espece tiree dans l'habitat du point (-1 si aucune) : a la PECHE (toutes les especes, requin gris compris). */
    public static int pick(Level level, BlockPos pos, RandomSource random, boolean bait) {
        return pickIn(habitat(level, pos), random, bait);
    }

    /**
     * 1.0.5-beta (lot 25) : espece d'un poisson qui APPARAIT dans le monde (jamais une espece de peche seulement, {@link #catchOnly} : le
     * requin gris du lot 9 nage deja dans les mers, en vraie bete).
     */
    public static int pickWild(Level level, BlockPos pos, RandomSource random) {
        return pickIn(habitat(level, pos), random, false, false);
    }

    /** 1.0.5-beta (lot 25) : espece de peche seulement (silhouette {@code shark}) : ni apparition comme poisson, ni ponte. */
    public static boolean catchOnly(FishDef d) {
        return d.shape() >= 0 && d.shape() < GeneratedFish.SHAPES.length && "shark".equals(GeneratedFish.SHAPES[d.shape()]);
    }

    public static int pickIn(int habitat, RandomSource random, boolean bait) {
        return pickIn(habitat, random, bait, true);
    }

    /** Tirage pondere par les raretes ; {@code fishing} faux : sans les especes de peche seulement. */
    public static int pickIn(int habitat, RandomSource random, boolean bait, boolean fishing) {
        FishDef[] all = GeneratedFish.SPECIES;
        double total = 0;
        for (FishDef d : all) {
            if (d.in(habitat) && (fishing || !catchOnly(d))) {
                total += weight(d, bait);
            }
        }
        if (total <= 0) {
            return -1;
        }
        double r = random.nextDouble() * total;
        int last = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i].in(habitat) && (fishing || !catchOnly(all[i]))) {
                last = i;
                r -= weight(all[i], bait);
                if (r <= 0) {
                    return i;
                }
            }
        }
        return last;
    }

    private static double weight(FishDef d, boolean bait) {
        return bait ? Math.pow(d.rarity(), 0.55) : d.rarity();
    }
}
