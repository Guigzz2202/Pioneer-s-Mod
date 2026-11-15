package fr.pioneers.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/** Bloc plein a textures connectees (voir {@link Connections}). */
public class ConnectedBlock extends Block implements Connections.Connected {

    private final TagKey<Block> family;

    public ConnectedBlock(Properties properties, TagKey<Block> family) {
        super(properties);
        this.family = family;
        registerDefaultState(Connections.unconnected(defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        Connections.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), family);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(super.updateShape(state, side, neighbor, level, pos, neighborPos), side, neighbor, family);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return family;
    }

    /** 1.0.4-beta (lot 23) : textures connectees recalculees a toute pose ({@link Connections#heal}). */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }
}
