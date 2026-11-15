package fr.pioneers.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Chateau d'entreposage a sec (0.17.0) : beton et acier autour d'assemblages uses refroidis (ou de fragments de corium). Puits final du
 * combustible en attendant le retraitement ; faible source de radiation. Facade orientee vers le joueur a la pose.
 */
public class DryStorageCaskBlock extends HorizontalDirectionalBlock {

    public DryStorageCaskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(state.getBlock())) {
            RadioactiveBlock.register(level, pos, RadiationData.CASK);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!next.is(state.getBlock())) {
            RadioactiveBlock.unregister(level, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
