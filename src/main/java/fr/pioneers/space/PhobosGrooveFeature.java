package fr.pioneers.space;

import fr.pioneers.core.registries.PiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Sillons de Phobos : longues rainures paralleles (chaines de petits cratères alignes), toutes dans la meme direction.
 * Le motif depend des coordonnees du monde : les sillons se raccordent d'un chunk a l'autre.
 */
public class PhobosGrooveFeature extends Feature<NoneFeatureConfiguration> {

    private static final double ANGLE = Math.toRadians(28), SPACING = 7.0;

    public PhobosGrooveFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int cx = context.origin().getX() & ~15, cz = context.origin().getZ() & ~15;
        double cos = Math.cos(ANGLE), sin = Math.sin(ANGLE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = cx + dx, z = cz + dz;
                double across = x * cos + z * sin, along = -x * sin + z * cos;
                double phase = across / SPACING - Math.floor(across / SPACING);
                //Rainure de 2 blocs, interrompue par endroits (chaines de cratères)
                long lane = Mth.floor(across / SPACING);
                boolean gap = Math.floorMod((long) Math.floor(along / 11.0) * 31 + lane * 17, 5) == 0;
                if (phase > 0.28 || gap) {
                    continue;
                }
                int depth = phase < 0.14 ? 2 : 1;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int i = 0; i < depth; i++) {
                    level.setBlock(pos.set(x, top - i, z), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(pos.set(x, top - depth, z), PiBlocks.PHOBOS_DUST.getBlock().defaultBlockState(), 2);
            }
        }
        return true;
    }
}
