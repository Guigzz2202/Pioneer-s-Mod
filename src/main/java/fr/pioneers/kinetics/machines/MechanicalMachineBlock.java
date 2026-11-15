package fr.pioneers.kinetics.machines;

import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.forge.BellowsLink;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/** Bloc d'une machine mecanique (voir {@link MachineType}) : facade vers le joueur, arbre par les flancs. */
public class MechanicalMachineBlock extends KineticBlock implements KineticSpecs.Specified {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final MachineType type;

    public MechanicalMachineBlock(MachineType type, Properties properties) {
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
        Direction toward = context.getHorizontalDirection().getOpposite();
        if (type == MachineType.BELLOWS) {
            //1.0.4-beta (lot 26) : soufflet tourne vers le bloc vise, sinon le seul voisin qu'il peut alimenter (BellowsLink)
            toward = BellowsLink.placement(context, true, toward);
        }
        return defaultBlockState().setValue(FACING, toward);
    }

    /**
     * 1.0.4-beta (lot 26) : un bloc eligible pose a cote d'un soufflet mecanique qui n'alimente rien : il se tourne vers lui, en gardant
     * l'axe de son arbre s'il est branche a une transmission ({@link BellowsLink#relink}).
     */
    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (type != MachineType.BELLOWS || level.isClientSide) {
            return;
        }
        Direction.Axis shaft = axis(state);
        boolean geared = level.getBlockState(pos.relative(Direction.fromAxisAndDirection(shaft, Direction.AxisDirection.POSITIVE))).getBlock()
                               instanceof KineticBlock
                         || level.getBlockState(pos.relative(Direction.fromAxisAndDirection(shaft, Direction.AxisDirection.NEGATIVE))).getBlock()
                               instanceof KineticBlock;
        Direction d = BellowsLink.relink(level, pos, state.getValue(FACING), from, true, geared);
        if (d != null) {
            level.setBlock(pos, state.setValue(FACING, d), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof KineticTile tile) {
                tile.invalidateNetwork();
            }
        }
    }

    /** Axe de l'arbre d'entree. */
    public static Direction.Axis axis(BlockState state) {
        if (state.getBlock() instanceof MechanicalMachineBlock block && block.type.vertical()) {
            return Direction.Axis.Y;
        }
        return state.getValue(FACING).getClockWise().getAxis();
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!type.hasGui()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MechanicalMachineTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MechanicalMachineTile tile) {
            tile.dropContents(stack -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            tile.onBroken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("kinetics.pioneers.spec.machine", Math.round(type.torque), Math.round(type.minSpeed), Math.round(type.nominalSpeed),
              KineticTile.watts(type.nominalPower()));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new MechanicalMachineTile(pos, state);
    }

    /** MAJ Recettes (lot 7) : la machine cassee garde sa recette verrouillee (dans l'objet, {@code BlockEntityTag}, relu a la pose). */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(@NotNull BlockState state, @NotNull LootParams.Builder builder) {
        return RecipeLock.stampDrops(super.getDrops(state, builder), state, builder);
    }
}
