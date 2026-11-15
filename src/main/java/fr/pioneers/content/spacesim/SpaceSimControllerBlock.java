package fr.pioneers.content.spacesim;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.registries.PiSpaceSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Controleur du Simulateur spatial : le socle pose juste sous le globe. Il porte la logique, les cases et les reservoirs ; clic droit :
 * l'interface (un bloc en main se pose normalement).
 */
public class SpaceSimControllerBlock extends MachineBlock {

    public SpaceSimControllerBlock(Properties properties) {
        super(properties, () -> PiSpaceSim.CONTROLLER_TILE.get(), SpaceSimControllerTile::new);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(state.getBlock())) {
            SpaceSimStructure.queue(server, pos);
        }
    }
}
