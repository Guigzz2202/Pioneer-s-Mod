package fr.pioneers.api.machine.unit;

import fr.pioneers.core.machine.Averager;
import fr.pioneers.core.machine.MachineTile;

/**
 * API Machine (lot 3) : UNITE DE GENERATEUR (conception § 1.3 : « production, poussee aux voisins »), sortie des tiles des generateurs :
 * elle produit l'energie dans la reserve de sa machine, la pousse par les faces de sortie et tient la production moyenne (FE/t). Les
 * chiffres restent dans leur source unique : {@code industry/GeneratorOutput} (combustibles), {@code energy/EnergyKind} (renouvelables),
 * les constantes des tiles.
 * <ul>
 *     <li>{@link FuelGenerator} : combustible (generateurs a combustion solide et liquide, turbine a gaz compacte : 1.0.3-beta lot 5) ;</li>
 *     <li>{@link RenewableUnit} : renouvelables (facteur de l'environnement : soleil, vent, chute, profondeur, matiere organique).</li>
 * </ul>
 */
public abstract class GeneratorUnit extends MachineUnit {

    /** Tier de la machine (0 = MKI). */
    public final int tier;
    protected final Averager produced = new Averager();

    protected GeneratorUnit(UnitHost machine, int tier) {
        super(machine);
        this.tier = tier;
    }

    /** Production moyenne (FE/t). */
    public double output() {
        return produced.get();
    }
}
