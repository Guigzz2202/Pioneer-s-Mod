package fr.pioneers.space.base;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/** Aire d'atterrissage d'un drone (1.0, lot 4) : dalle marquee posee sur le pont de la baie ; 2 x 2 aires = un drone. */
public class DronePadBlock extends Block {

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 3, 16);

    public DronePadBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
}
