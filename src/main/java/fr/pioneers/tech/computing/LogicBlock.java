package fr.pioneers.tech.computing;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.registries.PiComputing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Bloc d'un automate (armoire a relais, automate programmable) : machine du socle (FACING + ACTIVE) qui emet de la redstone par ses faces
 * selon ses regles. Le configurateur de liaison tenu en main passe la main a l'objet (liaison) au lieu d'ouvrir l'interface.
 */
public class LogicBlock extends MachineBlock {

    public final LogicKind kind;

    public LogicBlock(LogicKind kind, Properties properties) {
        super(properties, () -> PiComputing.LOGIC_TILE.get(), LogicTile::new);
        this.kind = kind;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(@NotNull BlockState state) {
        return true;
    }

    /** {@code side} : face du voisin tournee vers nous ; on emet par la face opposee. */
    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction side) {
        return level.getBlockEntity(pos) instanceof LogicTile tile ? tile.output(side.getOpposite()) : 0;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof DataLinkItem) {
            return InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
