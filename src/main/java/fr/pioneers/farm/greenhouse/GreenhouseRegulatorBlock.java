package fr.pioneers.farm.greenhouse;

import fr.pioneers.core.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 3) : REGULATEUR DE SERRE : bloc plein pose DANS une paroi de la serre (il compte comme paroi), facade vers le joueur (le
 * dehors). {@code active} = serre formee (voyant vert ; rouge sinon). Clic droit : l'interface (la serre est relue si le dernier releve date
 * de plus d'une seconde) ; Maj + clic droit main vide : le plan des fautes, puis l'exemple de serre ({@code common/projector/Projections}).
 */
public class GreenhouseRegulatorBlock extends MachineBlock implements GreenhouseWall {

    public GreenhouseRegulatorBlock(Properties properties) {
        super(properties, PiGreenhouse.REGULATOR_TILE, GreenhouseRegulatorTile::new);
    }

    @Override
    public GreenhouseMaterial greenhouseMaterial() {
        return GreenhouseMaterial.TREATED;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GreenhouseRegulatorTile tile) {
            tile.refresh();
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
