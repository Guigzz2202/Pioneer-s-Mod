package fr.pioneers.logistics.naval;

import fr.pioneers.logistics.naval.PiNaval;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.25 : interface du port (demande de Guillaume : brancher tuyaux et cables sans aller sur les flancs de la capitainerie) : une dalle de
 * quai (elle compte comme telle dans le U, sur n'importe quel rang du mur) qui donne acces, par chacune de ses faces, aux reserves de la
 * capitainerie (objets, fluide, gaz, energie, donnees, air comprime) avec les reglages de la face correspondante de la capitainerie.
 * Reliee par la capitainerie a chaque releve ({@link HarborInterfaceTile#link}).
 */
public class HarborInterfaceBlock extends QuayBlock implements EntityBlock {

    public HarborInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new HarborInterfaceTile(PiNaval.INTERFACE_TILE.get(), pos, state);
    }
}
