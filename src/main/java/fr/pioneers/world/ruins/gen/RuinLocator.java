package fr.pioneers.world.ruins.gen;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.RpConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, blocs 1 et 3) : ou sont les ruines prevues autour d'un point, SANS charger un seul tronçon (grille et bruit du generateur,
 * comme la generation elle-meme). Sert aux Confidences des villageois et a la carte de ruines du Cartographe.
 */
public final class RuinLocator {

    private RuinLocator() {
    }

    /** Centre et tier d'une ruine prevue. */
    public record Spot(BlockPos pos, int tier) {
    }

    /** Ruines prevues a moins de {@code radius} blocs, de la plus proche a la plus lointaine (Overworld seulement). */
    public static List<Spot> near(ServerLevel level, BlockPos center, int radius) {
        List<Spot> out = new ArrayList<>();
        if (level.dimension() != Level.OVERWORLD || !PiCommonConfig.get(RpConfig.RUINS, true)) {
            return out;
        }
        int density = PiCommonConfig.get(RpConfig.RUIN_DENSITY, 1);
        GenData.Snapshot data = GenData.current();
        if (density <= 0 || data == null) {
            return out;
        }
        NoiseTerrain terrain = new NoiseTerrain(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level, data);
        RuinGrid.Layout layout = data.settings().layout(density);
        long seed = level.getSeed();
        for (RuinGrid.Site c : RuinGrid.candidatesNear(seed, layout, center.getX(), center.getZ(), radius)) {
            if (RuinGrid.isSite(seed, layout, c, terrain)) {
                out.add(new Spot(new BlockPos(c.centerX(), level.getSeaLevel(), c.centerZ()), c.tier()));
            }
        }
        out.sort(Comparator.comparingDouble(s -> s.pos().distSqr(new BlockPos(center.getX(), level.getSeaLevel(), center.getZ()))));
        return out;
    }
}
