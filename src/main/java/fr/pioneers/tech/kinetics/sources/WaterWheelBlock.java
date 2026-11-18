package fr.pioneers.tech.kinetics.sources;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticSpecs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Roue a aubes (3 ou 5 blocs de diametre, axe horizontal) : l'eau qui coule contre ses aubes la pousse. Roue de dessous : un courant sous la
 * roue ; roue de dessus : une chute d'eau sur un cote du haut, deux fois et demie plus efficace (le poids de l'eau). L'eau dormante ne fait rien.
 */
public class WaterWheelBlock extends AxisKineticBlock implements KineticSpecs.Specified {

    public final boolean large;

    public WaterWheelBlock(boolean large, Properties properties) {
        super(properties);
        this.large = large;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis();
        if (axis == Direction.Axis.Y) {
            axis = context.getHorizontalDirection().getClockWise().getAxis();
        }
        return defaultBlockState().setValue(AXIS, axis);
    }

    public int radius() {
        return large ? 2 : 1;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return axis(state) == Direction.Axis.X ? Block.box(0, 1, 1, 16, 15, 15) : Block.box(1, 1, 0, 15, 15, 16);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.hint(large ? "kinetics.pioneers.spec.large_water_wheel" : "kinetics.pioneers.spec.water_wheel");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WaterWheelTile(pos, state);
    }
}
