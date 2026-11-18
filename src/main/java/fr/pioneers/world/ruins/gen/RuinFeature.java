package fr.pioneers.world.ruins.gen;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.world.ruins.RuinCatalog;
import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : feature {@code pioneers:ruin}, appelee une fois par chunk de l'Overworld (etape des structures de surface). Si un
 * site de ruine commence dans ce chunk (grille deterministe {@link RuinGrid}, terrain lu dans le bruit), la ruine y est posee, centree sur le
 * chunk ({@link RuinBuilder}). Tier le plus haut d'abord : un site d'un tier inferieur trop proche d'un tier superieur n'existe pas.
 */
public class RuinFeature extends Feature<NoneFeatureConfiguration> {

    public RuinFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<NoneFeatureConfiguration> context) {
        Context c = Context.of(context);
        if (c == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        try {
            for (int tier = RuinGrid.TIERS; tier >= 1; tier--) {
                RuinGrid.Site site = RuinGrid.siteInChunk(c.seed, c.layout, tier, chunk.x, chunk.z, c.terrain);
                if (site == null) {
                    continue;
                }
                RuinPlan plan = RuinPlan.of(site, c.data.settings());
                return plan != null && new RuinBuilder(context.level(), chunk, plan, c.data.settings()).build();
            }
        } catch (RuntimeException ex) {
            Pioneers.LOGGER.warn("Ruine non generee en {} : {}", chunk, ex.toString());
        }
        return false;
    }

    /**
     * 1.9 (MAJ RP, GameTests) : pose la ruine d'un site choisi (plan tire de sa graine) dans le chunk de son centre, sans la grille ni le
     * bruit du generateur (le monde des GameTests est plat). Seul usage : {@code fr.pioneers.gametest}. Renvoie le plan pose, ou null.
     */
    @Nullable
    public static RuinPlan placeForTest(WorldGenLevel level, RuinGrid.Site site) {
        GenData.Snapshot data = GenData.current();
        if (data == null || RuinCatalog.entries().isEmpty()) {
            return null;
        }
        RuinPlan plan = RuinPlan.of(site, data.settings());
        return plan != null && new RuinBuilder(level, new ChunkPos(site.chunkX(), site.chunkZ()), plan, data.settings()).build() ? plan : null;
    }

    /** Ce dont les features des ruines ont besoin, ou null si la generation est coupee (reglage, dimension, donnees absentes). */
    record Context(WorldGenLevel level, GenData.Snapshot data, RuinGrid.Layout layout, long seed, NoiseTerrain terrain) {

        @Nullable
        static Context of(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            WorldGenLevel level = context.level();
            if (!PiCommonConfig.get(RpConfig.RUINS, true) || level.getLevel().dimension() != Level.OVERWORLD) {
                return null;
            }
            int density = PiCommonConfig.get(RpConfig.RUIN_DENSITY, 1);
            GenData.Snapshot data = GenData.current();
            if (density <= 0 || data == null || RuinCatalog.entries().isEmpty()) {
                return null;
            }
            NoiseTerrain terrain = new NoiseTerrain(context.chunkGenerator(), level.getLevel().getChunkSource().randomState(), level, data);
            return new Context(level, data, data.settings().layout(density), level.getSeed(), terrain);
        }
    }
}
