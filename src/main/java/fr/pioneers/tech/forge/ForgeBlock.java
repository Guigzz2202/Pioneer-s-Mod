package fr.pioneers.tech.forge;

import fr.pioneers.tech.bronze.BronzeBlock;
import fr.pioneers.tech.bronze.BronzeTile;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc oriente (FACING horizontal) de la forge primaire porteur d'un {@link ForgeTile} : le clic droit va a {@link ForgeTile#use}, puis a
 * l'interface si la tuile en a une. Contenu lache a la casse, tick serveur et client (voir {@link BronzeBlock}).
 */
public class ForgeBlock extends BronzeBlock {

    public ForgeBlock(Properties properties, BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory, @Nullable VoxelShape shape) {
        super(properties, factory, shape);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ForgeTile tile) {
            InteractionResult result = tile.use(player, hand, hit);
            if (result != InteractionResult.PASS || !tile.hasMenu()) {
                return result;
            }
            return super.use(state, level, pos, player, hand, hit);
        }
        return InteractionResult.PASS;
    }
}
