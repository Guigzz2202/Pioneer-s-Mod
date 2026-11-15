package fr.pioneers.kinetics.rope;

import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticSpecs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Treuil : un tambour sur un arbre horizontal deroule une corde vers le bas (jusqu'a 48 blocs). Au fond : de l'eau, c'est un puits (un seau
 * par aller-retour, dans le reservoir du treuil) ; un coffre, c'est un monte-charge (16 objets par voyage, livres au coffre pose sur le treuil).
 * Le couple demande a la montee vaut m.g.r ; la descente est libre.
 */
public class WinchBlock extends AxisKineticBlock implements KineticSpecs.Specified {

    public WinchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction.Axis axis = context.getClickedFace().getAxis();
        if (axis == Direction.Axis.Y) {
            axis = context.getHorizontalDirection().getAxis();
        }
        return defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WinchTile tile)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        //1.0.1 : tout seau vide qui prend l'eau (vanilla, en argile, d'un autre mod), plus seulement le seau vanilla
        if (tile.water().getFluidAmount() >= Buckets.VOLUME && Buckets.canFill(held, tile.water().getFluid().getFluid())) {
            if (!level.isClientSide && Buckets.fillHeld(player, hand, tile.water().getFluid())) {
                tile.water().drain(Buckets.VOLUME, IFluidHandler.FluidAction.EXECUTE);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1F, 1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            player.displayClientMessage(tile.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.winch");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WinchTile(pos, state);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WinchTile tile) {
            tile.dropCargo(stack -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
