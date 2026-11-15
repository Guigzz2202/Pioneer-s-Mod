package fr.pioneers.logistics.rail.block;

import fr.pioneers.core.machine.MachineBlock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 0.20.8 : grue a eau (forme d'apres le maillage, tournee vers la voie). */
public class WaterCraneBlock extends MachineBlock {

    public WaterCraneBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties, type, factory);
    }

    /** 0.20.8 : forme d'apres le maillage, tournee vers la voie voisine. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return RailShapes.outline("water_crane", RailShapes.postFacing(level, pos, state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return RailShapes.get("water_crane", RailShapes.postFacing(level, pos, state.getValue(FACING)));
    }
}
