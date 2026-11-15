package fr.pioneers.bronze;

import fr.pioneers.bronze.steam.SteamPorts;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 0.20.44 : bloc oriente de l'age du bronze qui prend la vapeur par certaines faces : brides affichees la ou se branche un cable a gaz. */
public class SteamBronzeBlock extends BronzeBlock {

    private final SteamPorts.Faces faces;

    public SteamBronzeBlock(Properties properties, BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory, @Nullable VoxelShape shape,
          SteamPorts.Faces faces) {
        super(properties, factory, shape);
        this.faces = faces;
        registerDefaultState(SteamPorts.defaults(defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        SteamPorts.add(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : SteamPorts.refresh(state, context.getLevel(), context.getClickedPos(), faces);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        return SteamPorts.update(super.updateShape(state, side, neighbour, level, pos, neighbourPos), side, neighbour, faces);
    }
}
