package fr.pioneers.tech.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.45 : panneau solaire (soleil, meteo, lumiere de l'astre, ciel ouvert ; les panneaux voisins mettent leur energie en commun). API
 * Machine (lot 3) : la logique est dans {@link SolarLogic} ; la tile la porte ({@link GeneratorTile}).
 */
public class SolarPanelTile extends GeneratorTile {

    public SolarPanelTile(BlockPos pos, BlockState state) {
        super(PiEnergy.SOLAR_TILE.get(), pos, state, EnergyKind.SOLAR, a -> new SolarLogic(a, fr.pioneers.core.machine.TieredMachineBlock.tierOf(state)));
    }

    /** Lumiere recue sur l'astre ou se trouve le panneau, relative a la Terre ({@link SolarLogic#irradiance}). */
    public static double irradiance(ServerLevel level, double x) {
        return SolarLogic.irradiance(level, x);
    }
}
