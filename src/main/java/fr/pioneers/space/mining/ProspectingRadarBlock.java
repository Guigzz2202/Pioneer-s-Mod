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
 * Radar de prospection : le socle, pose au centre du toit de la base. L'antenne parabolique tourne au-dessus
 * (rendue par la base quand elle est formee, plus vite pendant une prospection).
 */
public class ProspectingRadarBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 3.2, 15), box(4, 3.2, 4, 12, 6.2, 12), box(5.5, 6.2, 5.5, 10.5, 13.2, 10.5));

    public ProspectingRadarBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
}
