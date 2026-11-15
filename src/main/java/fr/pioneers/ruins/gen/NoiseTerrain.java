package fr.pioneers.ruins.gen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * 1.9 (MAJ RP, bloc 1) : le terrain convient-il a un site ? Lu dans le BRUIT du generateur (hauteurs de base, biome), jamais dans les chunks :
 * la reponse est la meme quel que soit le chunk qui la demande (la ruine dans son chunk, ses annexes dans les leurs) et l'ordre de
 * generation. Cinq colonnes (centre et quatre coins a 10 blocs) : eau au centre, coins sous l'eau profonde, relief, biome exclu ; MKIII :
 * assez de roche sous la surface pour creuser une salle. Objet ephemere (un par appel de feature), sans etat partage.
 */
final class NoiseTerrain implements RuinGrid.Terrain {

    private static final int[][] POINTS = {{0, 0}, {-10, -10}, {10, -10}, {-10, 10}, {10, 10}};
    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final GenSettings settings;
    private final GenData.BiomeRules biomes;

    NoiseTerrain(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights, GenData.Snapshot data) {
        this.generator = generator;
        this.random = random;
        this.heights = heights;
        this.settings = data.settings();
        this.biomes = data.biomes();
    }

    @Override
    public boolean suits(RuinGrid.Site c) {
        GenSettings.Tier ts = settings.tier(c.tier());
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, deep = 0, centerWater = 0, centerFloor = 0;
        for (int k = 0; k < POINTS.length; k++) {
            int x = c.centerX() + POINTS[k][0], z = c.centerZ() + POINTS[k][1];
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - 1;
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random) - 1;
            int water = Math.max(0, surface - floor);
            if (k == 0) {
                centerWater = water;
                centerFloor = floor;
                if (water > ts.centerWater()) {
                    return false;
                }
            } else if (water > 2) {
                deep++;
            }
            min = Math.min(min, floor);
            max = Math.max(max, floor);
        }
        if (deep > ts.deepCorners() || max - min > ts.relief()) {
            return false;
        }
        if (c.tier() >= 3 && centerFloor - ts.depthMin() - ts.maxHeight() - 8 < heights.getMinBuildHeight() + 6 && max - min < 10) {
            //Ni salle possible (trop pres du fond du monde) ni flanc de montagne
            return false;
        }
        int y = centerFloor + Math.min(centerWater, 1);
        return !biomes.excluded(c.tier(), generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(c.centerX()), QuartPos.fromBlock(y),
              QuartPos.fromBlock(c.centerZ()), random.sampler()));
    }
}
