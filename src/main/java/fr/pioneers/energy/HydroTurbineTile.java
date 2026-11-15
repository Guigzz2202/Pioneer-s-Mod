package fr.pioneers.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.45 : turbine de barrage. Elle se pose dans le mur d'une retenue : prise d'eau a l'ARRIERE (dans l'eau de la retenue), sortie a
 * l'AVANT (air ou eau libre, en aval). Toutes les {@link #SURVEY_TICKS} ticks, elle parcourt l'eau reliee a sa prise (au plus 1,5 fois le
 * volume voulu) : sources d'eau, surface la plus haute, et autres turbines puisant dans la meme retenue.
 * Facteur = min(chute, {@link EnergyKind#HYDRO_HEAD}) / 16 x min(1, sources par turbine / {@link EnergyKind#HYDRO_VOLUME}).
 * Si la sortie est bouchee, ou si l'eau devant appartient a la retenue (pas de barrage), rien.
 * <p>
 * Interface : 8 hauteur de chute, 9 sources d'eau de la retenue, 10 turbines sur la retenue, 11 etat (0 marche, 1 pas d'eau derriere,
 * 2 sortie bouchee, 3 pas de denivele).
 * <p>
 * API Machine (lot 3) : la logique est dans {@link HydroLogic} ; la tile la porte ({@link GeneratorTile}).
 */
public class HydroTurbineTile extends GeneratorTile {

    public static final int SURVEY_TICKS = 200;
    public static final int OK = 0, NO_WATER = 1, BLOCKED = 2, NO_HEAD = 3;

    public HydroTurbineTile(BlockPos pos, BlockState state) {
        super(PiEnergy.HYDRO_TILE.get(), pos, state, EnergyKind.HYDRO, a -> new HydroLogic(a, fr.pioneers.core.machine.TieredMachineBlock.tierOf(state)));
    }
}
