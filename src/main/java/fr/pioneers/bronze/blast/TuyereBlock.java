package fr.pioneers.bronze.blast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Tuyere du haut fourneau : brique a buse de cuivre, la buse vers l'exterieur (face au soufflet). Incandescente quand l'air passe. */
public class TuyereBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** 1.0.5-beta (lot 23) : palier de chauffe de la structure (assises de briques de la tuyere : {@link fr.pioneers.bronze.HeatPalette}). */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty HEAT = fr.pioneers.bronze.HeatPalette.HEAT;

    public TuyereBlock(Properties properties) {
        super(properties.lightLevel(s -> Math.max(s.getValue(LIT) ? 9 : 0, s.getValue(HEAT) * fr.pioneers.bronze.HeatPalette.LIGHT_PER_STEP)));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, HEAT);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TuyereTile(pos, state);
    }
}
