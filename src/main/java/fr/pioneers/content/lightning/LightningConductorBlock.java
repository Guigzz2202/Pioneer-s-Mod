package fr.pioneers.content.lightning;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Conducteur du mat du paratonnerre : une colonne de cuivre et d'acier, a empiler sur le collecteur (4 a 24) sous un paratonnerre.
 */
public class LightningConductorBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public LightningConductorBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
}
