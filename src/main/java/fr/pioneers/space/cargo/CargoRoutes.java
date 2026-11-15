package fr.pioneers.space.cargo;

import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.research.ResearchEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Distances entre spatioports : duree du vol et hydrogene consomme, selon l'ecart des orbites autour du Soleil.
 * <ul>
 *     <li>Meme astre (saut suborbital) : 10 s, 1 000 mB.</li>
 *     <li>Terre - Lune : 20 s, 2 000 mB.</li>
 *     <li>Terre - Mars : ~42 s, ~5 600 mB ; Terre - Mercure ou asteroides : ~65 s, ~9 500 mB.</li>
 * </ul>
 */
public final class CargoRoutes {

    private CargoRoutes() {
    }

    /** Plafond d'hydrogene d'un trajet logistique (avant les modules d'efficacite). */
    public static final int ROCKET_FUEL_CAPACITY = 12_000;

    @Nullable
    public static Body body(ResourceKey<Level> dimension) {
        return SolarSystem.fromDimension(dimension);
    }

    private static float gap(ResourceKey<Level> from, ResourceKey<Level> to) {
        Body a = body(from), b = body(to);
        if (a == null || b == null) {
            return 0;
        }
        return Math.abs(a.getPrimary().orbit - b.getPrimary().orbit);
    }

    /** Hydrogene liquide consomme au depart (mB). */
    public static int fuel(ResourceKey<Level> from, ResourceKey<Level> to) {
        int base = from == to ? 1_000 : Math.min(ROCKET_FUEL_CAPACITY, 2_000 + Math.round(4_000 * gap(from, to)));
        //Reservoirs cryogeniques : -25 %
        return (int) Math.ceil(base * ResearchEffects.hydrogenMultiplier());
    }

    /** Duree du trajet en orbite (ticks), sans la montee ni la descente. */
    public static int transitTicks(ResourceKey<Level> from, ResourceKey<Level> to) {
        int base = from == to ? 200 : 400 + Math.round(500 * gap(from, to));
        //Propulsion ionique : -30 %
        return (int) Math.round(base * ResearchEffects.cargoTransitMultiplier());
    }

}
