package fr.pioneers.tech.forge.casting;

import fr.pioneers.tech.forge.BellowsLink;
import fr.pioneers.tech.forge.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Soufflet a main pose a plat a cote d'un feu de camp (FACING = vers le feu, la tuyere). A la pose, il se tourne de lui-meme vers un feu de
 * camp, une source de chaleur ou un recepteur d'air voisin (sinon dans le sens du regard) ; 1.0.4-beta (lot 26) : le bloc vise d'abord,
 * puis le seul voisin eligible, et il se relie a un bloc eligible pose a cote apres lui ({@link BellowsLink}). Clic droit = une bouffee
 * ({@link HandBellowsTile#puff}) ; il transmet aussi l'air d'un soufflet mecanique tourne vers lui. Planches animees : rendu client.
 */
public class HandBellowsBlock extends ForgeBlock {

    public HandBellowsBlock(Properties properties) {
        super(properties, HandBellowsTile::new, Block.box(1, 0, 1, 15, 7, 15));
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        //1.0.4-beta (lot 26) : bloc vise, sinon seul voisin eligible, sinon regard (BellowsLink)
        return defaultBlockState().setValue(FACING, BellowsLink.placement(context, false, context.getHorizontalDirection()));
    }

    /** 1.0.4-beta (lot 26) : un bloc eligible pose (ou allume) a cote d'un soufflet qui n'alimente rien : il se tourne vers lui. */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        Direction d = BellowsLink.relink(level, pos, state.getValue(FACING), from, false, false);
        if (d != null) {
            level.setBlock(pos, state.setValue(FACING, d), Block.UPDATE_ALL);
        }
    }
}
