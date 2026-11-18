package fr.pioneers.world.ruins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/** 1.9 (MAJ RP, bloc 1) : gravats technologiques (tas bas ; 30 % de chance d'un scrap Structure a la casse). */
public class TechRubbleBlock extends DerelictBlock {

    private static final VoxelShape LOW = Block.box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape HIGH = Block.box(0, 0, 0, 16, 10, 16);

    public TechRubbleBlock(int tier, Properties properties) {
        super("rubble", tier, properties);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return state.getValue(WORN) ? HIGH : LOW;
    }
}
