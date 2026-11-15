package fr.pioneers.logistics.naval;

import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.registries.PiNaval;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.11 : bloc du port (MKI a MKIV), pose sur la rive. Clic droit : reserves et faces (interface standard, bouton « Fiche » : nom,
 * limite, logistique). Le cote mer se trouve seul (derriere la facade d'abord).
 * <p>
 * 0.20.14 : c'est la capitainerie du port multibloc : elle se pose dans une rangee de dalles de quai le long de l'eau, avec les equipements
 * de quai ({@link HarborEquipment}) ; Maj + clic droit main vide projette le plan du quai ({@link HarborBlueprint}).
 */
public class HarborBlock extends TieredMachineBlock {

    public HarborBlock(int tier, Properties properties) {
        super(tier, properties, PiNaval.HARBOR_TILE, HarborTile::new);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            NavalWorld.get(sl).removeHarbor(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            QuayBlock.refreshAround(level, pos);
        }
    }

    /** 0.20.25 : les dalles en diagonale et dessous se reconnectent. */
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(state.getBlock())) {
            QuayBlock.refreshAround(level, pos);
        }
    }
}
