package fr.pioneers.mechanics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Couche de cendre laissee par les buches consumees d'une fosse : donne de la cendre de bois. 1.1.3 : tombe comme du sable (elle flottait
 * quand la buche dessous avait brule ou ete ramassee).
 */
public class AshLayerBlock extends FallingBlock {

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public AshLayerBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
}
