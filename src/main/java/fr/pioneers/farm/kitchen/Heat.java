package fr.pioneers.farm.kitchen;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Sources de chaleur des postes poses dessus (marmite, poele, chaudron de brassage, alambic) : fourneau allume, feu de camp allume, feu,
 * lave, bloc de magma, et tout bloc du tag {@code pioneers:heat_sources} allume (propriete {@code lit}) ou sans propriete d'allumage.
 */
public final class Heat {

    public static final TagKey<Block> HEAT_SOURCES = BlockTags.create(Pioneers.rl("heat_sources"));

    private Heat() {
    }

    public static boolean below(BlockGetter level, BlockPos pos) {
        return isHeat(level, pos.below());
    }

    public static boolean isHeat(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof StationBlock station) {
            return station.kind.heats() && s.getValue(StationBlock.LIT);
        }
        if (CampfireBlock.isLitCampfire(s) || s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
        }
        if (s.is(HEAT_SOURCES)) {
            return !s.hasProperty(BlockStateProperties.LIT) || s.getValue(BlockStateProperties.LIT);
        }
        return false;
    }

    /** Fumee pour le sechoir : feu de camp ou feu allume 1 a 3 blocs sous lui. */
    public static boolean smoke(BlockGetter level, BlockPos pos) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockState s = level.getBlockState(pos.below(dy));
            if (CampfireBlock.isLitCampfire(s) || s.is(BlockTags.FIRE)) {
                return true;
            }
            if (!s.isAir() && !CampfireBlock.isLitCampfire(s)) {
                return false;
            }
        }
        return false;
    }
}
