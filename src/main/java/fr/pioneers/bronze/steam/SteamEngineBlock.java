package fr.pioneers.bronze.steam;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticSpecs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
 * Machine a vapeur a cylindre horizontal : la vapeur entre par l'arriere ou le dessus, l'arbre sort par les flancs ; l'echappement souffle a
 * l'avant (un condenseur pose devant recupere l'eau et ajoute 25 % de couple).
 */
public class SteamEngineBlock extends KineticBlock implements KineticSpecs.Specified {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SteamEngineBlock(Properties properties) {
        super(properties);
        registerDefaultState(SteamPorts.defaults(defaultBlockState().setValue(FACING, Direction.NORTH)));
    }

    /** 0.20.44 : vapeur par l'arriere ou le dessous (voir {@link SteamEngineTile#steam}). */
    private static final SteamPorts.Faces FACES = (s, side) -> side == s.getValue(FACING).getOpposite() || side == Direction.DOWN;

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbour,
          @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        return SteamPorts.update(super.updateShape(state, side, neighbour, level, pos, neighbourPos), side, neighbour, FACES);
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        SteamPorts.add(builder);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return SteamPorts.refresh(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(),
              context.getClickedPos(), FACES);
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SteamEngineTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void specs(List<Component> tooltip) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("steam.pioneers.engine_spec");
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new SteamEngineTile(pos, state);
    }
}
