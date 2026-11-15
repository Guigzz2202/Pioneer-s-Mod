package fr.pioneers.kinetics.kitchen;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticSpecs;
import fr.pioneers.kinetics.KineticTile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 1.6 : bloc d'un poste mecanique de la cuisine ({@link KitchenMachineType}) : facade vers le joueur, arbre par les flancs (ou par dessous).
 * Clic droit : l'interface (1.0.5-beta lot 41 : plus de seau ni de bouteille au clic ; paires « Remplir » / « Vider » de l'interface).
 */
public class KitchenMachineBlock extends KineticBlock implements KineticSpecs.Specified {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final KitchenMachineType type;

    public KitchenMachineBlock(KitchenMachineType type, Properties properties) {
        super(properties);
        this.type = type;
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

    /** Axe de l'arbre d'entree. */
    public static Direction.Axis axis(BlockState state) {
        if (state.getBlock() instanceof KitchenMachineBlock block && block.type.vertical) {
            return Direction.Axis.Y;
        }
        return state.getValue(FACING).getClockWise().getAxis();
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof KitchenMachineTile tile)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KitchenMachineTile tile) {
            tile.dropContents(stack -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.machine", Math.round(type.torque), Math.round(type.minSpeed), Math.round(type.nominalSpeed),
              KineticTile.watts(type.nominalPower()));
        tips.spec("kitchen_machine.pioneers.spec.stroke", String.valueOf(type.turnsPerStroke).replace(".0", ""));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new KitchenMachineTile(pos, state);
    }
}
