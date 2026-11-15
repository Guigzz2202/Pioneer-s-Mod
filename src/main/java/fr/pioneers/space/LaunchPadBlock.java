package fr.pioneers.space;

import fr.pioneers.common.block.ConnectedBlock;
import fr.pioneers.common.block.Connections;
import fr.pioneers.registries.PiBaseMachines;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Plaque du pas de tir : un clic droit ouvre l'interface du cœur.
 */
public class LaunchPadBlock extends ConnectedBlock {

    public LaunchPadBlock(Properties properties) {
        super(properties, Connections.LAUNCH_PAD);
        registerDefaultState(defaultBlockState().setValue(LaunchPadStructure.TIER, 0));
    }

    /** 0.20.34 : couleur du tier du complexe (peinte par {@link LaunchPadStructure}). */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LaunchPadStructure.TIER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return openCore(level, pos, player, hand, hit);
    }

    /** Transmet le clic au cœur du pas de tir (qui ouvre son interface). */
    @SuppressWarnings("deprecation")
    static InteractionResult openCore(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive() || !player.getItemInHand(hand).isEmpty() && (player.getItemInHand(hand).getItem() instanceof BlockItem
              || player.getItemInHand(hand).getItem() instanceof ItemRocket && level.getBlockState(Objects.requireNonNullElse(LaunchPadStructure.findCore(level, pos), pos)).getBlock()
                                                                               == PiBaseMachines.LAUNCH_PAD_CORE.getBlock())) {
            return InteractionResult.PASS;
        }
        BlockPos core = LaunchPadStructure.findCore(level, pos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        return level.getBlockState(core).use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), core, false));
    }
}
