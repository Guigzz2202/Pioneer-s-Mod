package fr.pioneers.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 0.20.22 : cables, tuyaux et tubes etanches : ils se posent dans l'eau (bloc gorge d'eau, {@link #WATERLOGGED}) et l'eau ne les emporte
 * jamais (le bloc doit aussi refuser d'etre remplace par un fluide : {@code canBeReplaced(state, fluid)} faux). Aides communes aux blocs
 * qui implementent {@code SimpleWaterloggedBlock}.
 */
public final class Waterlogging {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private Waterlogging() {
    }

    /** Etat pose : gorge d'eau si la case contient de l'eau source. */
    public static BlockState placed(BlockState state, LevelAccessor level, BlockPos pos) {
        return state.hasProperty(WATERLOGGED) ? state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER) : state;
    }

    /** A appeler dans {@code updateShape} : l'eau du bloc continue de couler vers les voisins. */
    public static void tick(BlockState state, LevelAccessor level, BlockPos pos) {
        if (state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }

    public static FluidState fluid(BlockState state) {
        return state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }
}
