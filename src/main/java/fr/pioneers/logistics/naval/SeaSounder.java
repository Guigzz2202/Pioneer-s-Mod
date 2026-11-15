package fr.pioneers.logistics.naval;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.27 : sondage des eaux jamais chargees (Guillaume : « No sea route » a chaque fois : la carte marine ne connaissait que les chunks deja
 * charges). Sans generer le monde, on demande au generateur de la dimension le <b>biome</b> de chaque groupe de 4 x 4 blocs a la hauteur de
 * la mer : un biome oceanique ({@code #minecraft:is_ocean}, {@code #is_deep_ocean}) ou une riviere est de l'eau navigable a la hauteur de la mer, le reste
 * (plages, terres) ne l'est pas. Rapide (bruit climatique seulement) et sans danger hors du fil serveur : c'est le
 * planificateur qui l'appelle, dans son fil. La feuille estimee est remplacee par le vrai releve au premier chargement du chunk.
 */
public final class SeaSounder {

    /** Estimation d'un chunk, ou null si la dimension ne s'y prete pas (pas de mer, generateur inconnu). */
    public interface Probe {

        @Nullable
        WaterChart.Sheet estimate(int chunkX, int chunkZ);
    }

    private SeaSounder() {
    }

    /** Sonde de la dimension (null : pas de mer navigable dans son generateur). */
    @Nullable
    public static Probe of(ServerLevel level) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (!(gen instanceof NoiseBasedChunkGenerator noise)) {
            return null;
        }
        FluidState fluid = noise.generatorSettings().value().defaultFluid().getFluidState();
        if (!WaterChart.navigableFluid(fluid)) {
            return null;
        }
        boolean hot = fluid.is(WaterChart.NAVIGABLE_HOT);
        int surface = gen.getSeaLevel() - 1;
        BiomeSource source = gen.getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int qy = surface >> 2;
        return (chunkX, chunkZ) -> {
            WaterChart.Sheet s = new WaterChart.Sheet();
            //Un echantillon par groupe de 4 x 4 blocs (2 x 2 cases), au centre
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    int qx = (chunkX << 2) + i, qz = (chunkZ << 2) + j;
                    Holder<Biome> b = source.getNoiseBiome(qx, qy, qz, sampler);
                    if (!b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_DEEP_OCEAN) && !b.is(BiomeTags.IS_RIVER)) {
                        continue;
                    }
                    for (int k = 0; k < 4; k++) {
                        int idx = (2 * i + (k & 1)) | (2 * j + (k >> 1)) << 3;
                        s.nav |= 1L << idx;
                        if (hot) {
                            s.hot |= 1L << idx;
                        }
                        s.y[idx] = (short) surface;
                    }
                }
            }
            return s;
        };
    }
}
