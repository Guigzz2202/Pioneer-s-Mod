package fr.pioneers.tech.kinetics;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Piece a un axe (arbre, engrenage, boite en ligne...) : l'axe suit la face cliquee a la pose, comme une buche ; Maj : l'axe du regard.
 * 0.20.42 : sans Maj, l'axe qui raccorde le plus de voisins l'emporte (bouts d'arbre, engrenages qui engreneraient) : voir {@link KineticPlacement}.
 */
public abstract class AxisKineticBlock extends KineticBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    protected AxisKineticBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, KineticPlacement.axis(this, context));
    }

    @NotNull
    @Override
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            Direction.Axis axis = state.getValue(AXIS);
            return axis == Direction.Axis.X ? state.setValue(AXIS, Direction.Axis.Z) : axis == Direction.Axis.Z ? state.setValue(AXIS, Direction.Axis.X) : state;
        }
        return state;
    }

    public static Direction.Axis axis(BlockState state) {
        return state.hasProperty(AXIS) ? state.getValue(AXIS) : Direction.Axis.Y;
    }
}
