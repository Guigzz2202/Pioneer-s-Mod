package fr.pioneers.bronze.machines;

import fr.pioneers.bronze.SteamBronzeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import fr.pioneers.bronze.steam.SteamPorts;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Machine de traitement a la vapeur : facade (FACING), etat de marche (ACTIVE : bouffees de vapeur, lueur du four). 1.0.5-beta (lot 38) : les
 * brides de vapeur suivent les faces de vapeur REGLEES de la machine (panneau des faces) ; a la pose, les reglages par defaut (toutes les faces
 * sauf la facade et le dessus).
 */
public class SteamProcessBlock extends SteamBronzeBlock {

    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public final SteamMachine machine;

    public SteamProcessBlock(SteamMachine machine, Properties properties) {
        super(properties.lightLevel(s -> s.getValue(ACTIVE) && machine == SteamMachine.FURNACE ? 9 : 0),
              SteamProcessTile::new, null, (s, side) -> side != s.getValue(FACING) && side != Direction.UP);
        this.machine = machine;
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    /** 1.0.3-beta (lot 11b) : le melangeur a vapeur se remplit d'eau au seau (clic droit), sinon l'interface s'ouvre. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (machine == SteamMachine.MIXER && FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent()
              && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        BlockState next = super.updateShape(state, side, neighbour, level, pos, neighbourPos);
        if (level.getBlockEntity(pos) instanceof SteamProcessTile tile) {
            next = SteamPorts.update(next, side, neighbour, (s, d) -> tile.steamFace(d));
        }
        return next;
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) {
            return;
        }
        Direction back = state.getValue(FACING).getOpposite();
        level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + back.getStepX() * 0.3 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.05,
              pos.getZ() + 0.5 + back.getStepZ() * 0.3 + (random.nextDouble() - 0.5) * 0.3, 0, 0.04, 0);
    }
}
