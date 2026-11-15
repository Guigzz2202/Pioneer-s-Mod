package fr.pioneers.farm.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Culture fanee (climat hostile) : tiges seches sur la terre labouree, ne rend qu'un peu de fibre (table de butin). */
public class WitheredCropBlock extends BushBlock {

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);

    public WitheredCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        //1.12.3 : aussi sur le substrat hydroponique beche
        return state.getBlock() instanceof FarmBlock || fr.pioneers.space.farm.HydroponicSubstrateBlock.isTilled(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
