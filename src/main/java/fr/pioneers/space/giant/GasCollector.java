package fr.pioneers.space.giant;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.GeneratedMegastructures;
import fr.pioneers.space.mega.Megastructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 7) : le COLLECTEUR DE GAZ (multibloc MKV {@code gas_collector}, Kind DRILL ; brief §7). Dresse dans
 * une geante gazeuse (sur un aerostat), il capte en continu le melange de gaz propre a la geante (table generee
 * {@link GeneratedMegastructures#GAS_BODIES}, source T:au_dela/multiblocs.py GAS_MIX) : un gaz par reservoir de sortie. Aucune reserve :
 * l'atmosphere d'une geante ne s'epuise pas. Sa fabrication demande la membrane de captage, que seule la Cite des Nuages fabrique. Les
 * aerostats et les drones collecteurs restent : le collecteur s'y ajoute. Le travail par tick est dans
 * {@code MultiProcess.tickGasCollector}.
 */
public final class GasCollector {

    public static final String KEY = "gas_collector";
    /** Duree d'une operation au tier basique (ticks). */
    public static final int TIME = 100;
    /** Gaz resolus au premier usage (registre fige). */
    private static Gas[][] gases;

    private GasCollector() {
    }

    public static boolean is(String machineKey) {
        return KEY.equals(machineKey);
    }

    /** Ligne de la table de la geante du monde, ou -1 (hors d'une geante). */
    public static int row(Level level) {
        return row(Megastructures.bodyOf.apply(level));
    }

    public static int row(@Nullable Body body) {
        if (body != null) {
            for (int i = 0; i < GeneratedMegastructures.GAS_BODIES.length; i++) {
                if (GeneratedMegastructures.GAS_BODIES[i].equals(body.name())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Gaz captes par la ligne (dans l'ordre des reservoirs de sortie). */
    public static Gas[] gases(int row) {
        if (gases == null) {
            Gas[][] all = new Gas[GeneratedMegastructures.GAS_IDS.length][];
            for (int i = 0; i < all.length; i++) {
                String[] ids = GeneratedMegastructures.GAS_IDS[i];
                all[i] = new Gas[ids.length];
                for (int k = 0; k < ids.length; k++) {
                    all[i][k] = PiGasTypes.REGISTRY.get().getValue(ResourceLocation.tryParse(ids[k]));
                }
            }
            gases = all;
        }
        return gases[row];
    }

    /** mB de chaque gaz par operation (au tier MKV, avant le parallelisme). */
    public static int[] amounts(int row) {
        return GeneratedMegastructures.GAS_AMOUNTS[row];
    }
}
