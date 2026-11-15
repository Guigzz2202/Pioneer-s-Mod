package fr.pioneers.core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc fantome d'une grande machine du socle (remplace les « bounding blocks » de Mekanism) : invisible, il prend la forme de la machine,
 * renvoie le clic droit, la casse et l'objet choisi (pioche du milieu) a son bloc principal, et ses capacites selon
 * {@link MachineTile#boundCapability} (ports des structures d'assemblage). Pose et retrait : {@link MachineBlock#bounds}.
 */
public class BoundingBlock extends Block implements EntityBlock {

    public BoundingBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    static BlockPos main(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BoundingTile tile ? tile.getMain() : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BoundingTile(pos, state);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        BlockPos main = main(level, pos);
        if (main == null) {
            return Shapes.block();
        }
        BlockState mainState = level.getBlockState(main);
        if (!(mainState.getBlock() instanceof MachineBlock)) {
            return Shapes.block();
        }
        VoxelShape shape = mainState.getShape(level, main, context).move(main.getX() - pos.getX(), main.getY() - pos.getY(), main.getZ() - pos.getZ());
        //Rognee au bloc : une forme qui deborde gene les collisions
        VoxelShape clipped = Shapes.join(shape, Shapes.block(), BooleanOp.AND);
        return clipped.isEmpty() ? Shapes.empty() : clipped;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        BlockPos main = main(level, pos);
        if (main == null) {
            return InteractionResult.PASS;
        }
        BlockState mainState = level.getBlockState(main);
        return mainState.use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), main, hit.isInside()));
    }

    @Override
    public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        BlockPos main = main(level, pos);
        super.playerWillDestroy(level, pos, state, player);
        if (main != null && !level.isClientSide && level.getBlockState(main).getBlock() instanceof MachineBlock) {
            level.destroyBlock(main, !player.isCreative(), player);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        BlockPos main = main(level, pos);
        return main == null ? super.getDestroyProgress(state, player, level, pos) : level.getBlockState(main).getDestroyProgress(player, level, main);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        BlockPos main = main(level, pos);
        return main == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(main).getBlock());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return true;
    }

    /** Bloc non plein : il laisse passer la lumiere (sans quoi le modele et ses voisins ressortent noirs). */
    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 0;
    }

    /** Pas d'ombrage ambiant d'un bloc plein (le modele 3D deborde sur les blocs voisins). */
    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }
}
