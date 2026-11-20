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

    /** 1.0.7 : astre d'une destination (spatioport : celui de son monde ; chantier de megastructure : celui de la megastructure). */
    @Nullable
    public static Body bodyOf(CargoNetwork.Station station) {
        return station.mega >= 0 ? fr.pioneers.space.mega.Megastructures.body(station.mega) : body(station.dimension);
    }

    private static float gap(@Nullable Body a, @Nullable Body b) {
        if (a == null || b == null) {
            return 0;
        }
        return Math.abs(a.getPrimary().orbit - b.getPrimary().orbit);
    }

    /** Hydrogene liquide consomme au depart (mB). */
    public static int fuel(ResourceKey<Level> from, ResourceKey<Level> to) {
        return fuel(from == to, body(from), body(to));
    }

    /** Hydrogene liquide consomme au depart (mB), d'un astre a l'autre (meme astre ou inconnu : saut suborbital). */
    public static int fuel(@Nullable Body from, @Nullable Body to) {
        return fuel(from == to, from, to);
    }

    private static int fuel(boolean same, @Nullable Body from, @Nullable Body to) {
        int base = same ? 1_000 : Math.min(ROCKET_FUEL_CAPACITY, 2_000 + Math.round(4_000 * gap(from, to)));
        //Reservoirs cryogeniques : -25 %
        return (int) Math.ceil(base * ResearchEffects.hydrogenMultiplier());
    }

    /** Duree du trajet en orbite (ticks), sans la montee ni la descente. */
    public static int transitTicks(ResourceKey<Level> from, ResourceKey<Level> to) {
        return transitTicks(from == to, body(from), body(to));
    }

    /** Duree du trajet en orbite (ticks), d'un astre a l'autre. */
    public static int transitTicks(@Nullable Body from, @Nullable Body to) {
        return transitTicks(from == to, from, to);
    }

    private static int transitTicks(boolean same, @Nullable Body from, @Nullable Body to) {
        int base = same ? 200 : 400 + Math.round(500 * gap(from, to));
        //Propulsion ionique : -30 %
        return (int) Math.round(base * ResearchEffects.cargoTransitMultiplier());
    }

}
