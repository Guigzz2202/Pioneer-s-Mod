package fr.pioneers.tech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.45 : centrale geothermique. De l'eau injectee dans la roche chaude revient en vapeur et fait tourner sa turbine : plus elle est
 * posee profond, plus elle rend (rien au-dessus de y 48, plein a y -60) ; dans le Nether, toujours au mieux. Chaque bloc de lave (ou de
 * magma) qui la touche ajoute 10 % (30 % au plus). Elle consomme {@link EnergyKind#GEO_WATER} mB d'eau par tick a pleine puissance
 * (l'eau entre par toutes les faces) ; sans assez d'eau, la production suit l'eau disponible.
 * <p>
 * Interface : 8 profondeur x1000, 9 eau (mB), 10 reservoir (mB), 11 lave voisine (blocs).
 * <p>
 * API Machine (lot 3) : la logique (et le reservoir d'eau) est dans {@link GeothermalLogic} ; la tile la porte ({@link GeneratorTile}).
 */
public class GeothermalPlantTile extends GeneratorTile {

    public GeothermalPlantTile(BlockPos pos, BlockState state) {
        super(PiEnergy.GEOTHERMAL_TILE.get(), pos, state, EnergyKind.GEOTHERMAL,
              a -> new GeothermalLogic(a, fr.pioneers.core.machine.TieredMachineBlock.tierOf(state)));
    }
}
