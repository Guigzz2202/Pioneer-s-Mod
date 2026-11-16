package fr.pioneers.tech.industry.multi;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Controleur d'un multibloc industriel (dans une paroi laterale, facade vers l'exterieur) : FACING + ACTIVE, interface standard. Comparateur :
 * remplissage pour les reservoirs, 15 en marche pour les autres.
 */
public class ControllerBlock extends MachineBlock {

    public final IndustryMachine machine;

    public ControllerBlock(IndustryMachine machine, Properties properties) {
        super(properties, PiIndustry.CONTROLLER_TILE, ControllerTile::new);
        this.machine = machine;
    }

    /** Tier peint a la formation (equerres de couleur sur la facade). */
    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block,
          BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IndustryStyle.TIER);
    }

    /** 0.17.2 : la puce d'IA installee tombe avec le controleur. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ControllerTile tile && !tile.aiChip().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.takeChip());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ControllerTile tile ? tile.comparator() : 0;
    }
}
