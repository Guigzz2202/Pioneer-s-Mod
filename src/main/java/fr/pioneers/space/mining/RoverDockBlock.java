package fr.pioneers.space.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Dock du rover minier : plateforme de recharge et de dechargement au centre du hangar.
 */
public class RoverDockBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 3, 16), box(0.5, 3, 13.5, 2.5, 10, 15.5),
          box(13.5, 3, 13.5, 15.5, 10, 15.5), box(6, 3, 13.4, 10, 6, 15.4));

    public RoverDockBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
}
