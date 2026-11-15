package fr.pioneers.forge.casting;

import fr.pioneers.forge.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creuset d'argile pose. Sur un feu de camp (allume ou non), {@link #STAND} = vrai : le modele ajoute trois pieds d'argile qui descendent
 * jusqu'aux buches, le pot est dans les flammes. Le reste (fusion, pince, charbon, soufflet) est dans {@link ClayCrucibleTile}. Pas de
 * table de butin : la tuile rend le creuset avec son usure ({@link ClayCrucibleTile#dropContents}).
 */
public class ClayCrucibleBlock extends ForgeBlock {

    /** Pose sur un feu de camp (pieds visibles). */
    public static final BooleanProperty STAND = BooleanProperty.create("stand");

    public ClayCrucibleBlock(Properties properties) {
        super(properties, ClayCrucibleTile::new, Block.box(4, 0, 4, 12, 8, 12));
        registerDefaultState(defaultBlockState().setValue(STAND, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAND);
    }

    public static boolean standsOn(BlockState below) {
        return below.getBlock() instanceof CampfireBlock;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(STAND, standsOn(context.getLevel().getBlockState(context.getClickedPos().below())));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        if (direction == Direction.DOWN) {
            boolean stand = standsOn(neighbour);
            if (stand != state.getValue(STAND)) {
                return state.setValue(STAND, stand);
            }
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ClayCrucibleTile tile) {
            tile.setCrucible(stack.copyWithCount(1), null);
        }
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull BlockState state) {
        return new ItemStack(this);
    }
}
