package fr.pioneers.kinetics.sources;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticSpecs;
import fr.pioneers.kinetics.KineticTile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Moyeu de moulin a vent : on y monte des ailes (toile ou planches, 4 ou 8) ; la cle regle la longueur des ailes (3 a 7 blocs). Les ailes
 * balaient le plan du moyeu : il doit rester degage. L'arbre sort par l'arriere. Puissance : loi des eoliennes, P = 1/2 rho pi R2 v3 Cp.
 */
public class WindmillBlock extends KineticBlock implements KineticSpecs.Specified {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindmillBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WindmillTile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        int type = SailItem.typeOf(held);
        if (type > 0) {
            if (!level.isClientSide) {
                if (tile.addSail(type)) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                } else {
                    player.displayClientMessage(Component.translatable("kinetics.pioneers.windmill.full").withStyle(ChatFormatting.YELLOW), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack removed = tile.removeSail();
                if (!removed.isEmpty() && !player.addItem(removed)) {
                    player.drop(removed, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult onWrench(BlockState state, Level level, BlockPos pos, Player player, KineticTile tile) {
        if (tile instanceof WindmillTile windmill) {
            windmill.cycleRadius();
            player.displayClientMessage(Component.translatable("kinetics.pioneers.windmill.radius", windmill.radius()), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WindmillTile tile) {
            for (ItemStack stack : tile.allSails()) {
                Block.popResource(level, pos, stack);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.windmill");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WindmillTile(pos, state);
    }
}
