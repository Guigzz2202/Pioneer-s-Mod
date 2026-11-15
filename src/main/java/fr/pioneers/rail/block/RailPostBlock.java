package fr.pioneers.rail.block;

import fr.pioneers.core.machine.TieredMachineBlock;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 0.20.2 : bloc d'un poste de quai (sorte {@link PostKind}, tier 0..3 ; 0 pour les postes sans tier). */
public class RailPostBlock extends TieredMachineBlock {

    public final PostKind kind;

    public RailPostBlock(PostKind kind, int tier, Properties properties, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(tier, properties, type, factory);
        this.kind = kind;
    }

    /** Sorte du poste d'un etat (ITEM pour un ancien bloc sans sorte). */
    public static PostKind kindOf(BlockState state) {
        return state.getBlock() instanceof RailPostBlock b ? b.kind : PostKind.ITEM;
    }

    /** 0.20.8 : forme d'apres le maillage, tournee vers la voie voisine. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return kind == PostKind.PIT ? Shapes.block() : RailShapes.outline("post_" + kind.name().toLowerCase(Locale.ROOT) + "_" + tier(), RailShapes.postFacing(level, pos, state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
          CollisionContext ctx) {
        return kind == PostKind.PIT ? Shapes.block() : RailShapes.get("post_" + kind.name().toLowerCase(Locale.ROOT) + "_" + tier(), RailShapes.postFacing(level, pos, state.getValue(FACING)));
    }
}
