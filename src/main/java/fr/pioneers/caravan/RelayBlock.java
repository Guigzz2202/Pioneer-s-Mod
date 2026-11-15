package fr.pioneers.caravan;

import fr.pioneers.bronze.BronzeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : RELAIS DE CARAVANE : poteau d'attache, abreuvoir et mangeoire (facade vers le joueur a la pose : les betes
 * s'arretent devant). Clic droit : l'interface (ordre, filtre, depart, attente, mangeoire) ; avec la feuille de route en main, le clic va a
 * la feuille (l'etape s'ajoute : {@link RouteSheetItem#useOn}). Le poseur en est le proprietaire ; nom donne a l'enclume = nom de l'etape.
 * Casse : la mangeoire tombe ; les caravanes qui le visaient s'arretent ({@link Caravans}).
 */
public class RelayBlock extends BronzeBlock {

    public RelayBlock(Properties properties) {
        super(properties, RelayTile::new, Block.box(0.5, 0, 0.5, 15.5, 16, 15.5));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof RouteSheetItem) {
            return InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RelayTile tile) {
            tile.placedBy(placer instanceof Player p ? p : null);
            if (stack.hasCustomHoverName()) {
                tile.customName = stack.getHoverName().getString();
            }
        }
    }
}
