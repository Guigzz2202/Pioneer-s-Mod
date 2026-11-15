package fr.pioneers.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Segment de la tour de lancement (5 empiles a cote du pas de tir).
 * Le pas de tir oriente automatiquement la tour vers la fusee, deploie le bras d'alimentation sur le 3e segment
 * et pose la plateforme sur le 5e.
 */
public class LaunchTowerBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty ARM = BooleanProperty.create("arm");
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public LaunchTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARM, false).setValue(TOP, false)
              .setValue(LaunchPadStructure.TIER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARM, TOP, LaunchPadStructure.TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
          Player player, InteractionHand hand, BlockHitResult hit) {
        return LaunchPadBlock.openCore(level, pos, player, hand, hit);
    }

    static BlockState configure(BlockState state, Direction facing, boolean arm, boolean top) {
        return state.setValue(FACING, facing).setValue(ARM, arm).setValue(TOP, top);
    }

    public static boolean is(BlockState state) {
        return state.getBlock() instanceof LaunchTowerBlock;
    }

}
