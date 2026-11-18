package fr.pioneers.tech.kinetics;

import fr.pioneers.core.Wrenches;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc d'une piece du reseau de force rotative. Cle + Maj : fiche du reseau (vitesse, couple, puissance, charge) ; cle seule : reglage propre
 * a la piece ({@link #onWrench}). Un voisin qui change les liens de la piece fait reconstruire le reseau (une piece posee a cote s'y raccorde).
 */
public abstract class KineticBlock extends BaseEntityBlock {

    protected KineticBlock(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof KineticTile tile) {
            onNeighbor(state, level, pos, tile);
            //0.20.46 (audit) : reconstruction seulement si les liens de la piece ont change (avant : a toute mise a jour d'un voisin, meme un
            //signal redstone ou l'allumage d'une chaudiere, le reseau entier, jusqu'a 4 096 pieces, etait reconstruit et renvoye aux clients)
            if (tile.linksChanged()) {
                tile.invalidateNetwork();
            }
        }
    }

    /** Voisin change (signal redstone...) ; appele avant la reconstruction eventuelle du reseau. */
    protected void onNeighbor(BlockState state, Level level, BlockPos pos, KineticTile tile) {
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (Wrenches.isWrench(player.getItemInHand(hand))) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof KineticTile tile) {
                if (player.isShiftKeyDown()) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(state.getBlock().getName().copy().withStyle(ChatFormatting.WHITE));
                    tile.describe(lines);
                    lines.forEach(player::sendSystemMessage);
                    return InteractionResult.CONSUME;
                }
                InteractionResult result = onWrench(state, level, pos, player, tile);
                if (result.consumesAction()) {
                    tile.invalidateNetwork();
                }
                return result;
            }
        }
        return interact(state, level, pos, player, hand, hit);
    }

    /** Reglage a la cle (sans Maj). */
    protected InteractionResult onWrench(BlockState state, Level level, BlockPos pos, Player player, KineticTile tile) {
        return InteractionResult.PASS;
    }

    /** Clic droit sans cle. */
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return null;
    }
}
