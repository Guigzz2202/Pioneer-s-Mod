package fr.pioneers.tech.industry.construction;

import fr.pioneers.core.common.block.Connections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 21) : vitre de salle blanche ; les vitres voisines forment une seule baie (textures connectees, famille
 * {@code pioneers:connects/cleanroom_glass} ; faces communes cachees comme tout verre).
 */
public class CleanroomGlassBlock extends GlassBlock implements Connections.Connected {

    public CleanroomGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(Connections.unconnected(stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), Connections.CLEANROOM_GLASS);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, Connections.CLEANROOM_GLASS);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return Connections.CLEANROOM_GLASS;
    }

    /** 1.0.4-beta (lot 23) : textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
