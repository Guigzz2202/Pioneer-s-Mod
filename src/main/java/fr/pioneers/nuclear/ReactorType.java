package fr.pioneers.nuclear;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Les trois reacteurs (0.17.1) : fission MKI (eau pressurisee), neutrons rapides MKII (sodium, surgenerateur), fragments de fission MKIII
 * (plasma poussiereux, electricite directe). Parametres du combustible et objets de chaque type.
 */
public enum ReactorType {
    /** Assemblages d'uranium enrichi : 40 kJ/t, 1 heure de jeu. */
    MK1("fission_reactor", 1, "fuel_assembly", "spent_fuel_assembly_hot", 40_000, 72_000, "nuclear_criticality"),
    /** Assemblages de MOX : 120 kJ/t, 80 minutes ; couvertures surgeneratrices. */
    MK2("fast_reactor", 2, "mox_assembly", "spent_mox_assembly_hot", 120_000, 96_000, "nuclear_criticality_mk2"),
    /** Cartouches de nanopoudre d'uranium hautement enrichi : 600 kJ/t, 100 minutes ; 80 % en electricite. */
    MK3("fragment_reactor", 3, "fragment_fuel_cartridge", "spent_fragment_cartridge", 600_000, 120_000, "nuclear_criticality_mk3");

    public final String machine;
    public final int mark;
    public final String fuel, spent, criticalEvent;
    /** Puissance thermique d'un combustible a pleine puissance (J/t) et duree de vie a pleine puissance (ticks). */
    public final double fuelPower, fuelLife;

    ReactorType(String machine, int mark, String fuel, String spent, double fuelPower, double fuelLife, String criticalEvent) {
        this.machine = machine;
        this.mark = mark;
        this.fuel = fuel;
        this.spent = spent;
        this.fuelPower = fuelPower;
        this.fuelLife = fuelLife;
        this.criticalEvent = criticalEvent;
    }

    /** Le reacteur accepte-t-il des cibles d'irradiation (lithium, neptunium) ? */
    public boolean targets() {
        return this != MK3;
    }

    /** Couvertures surgeneratrices (uranium appauvri, thorium) ? */
    public boolean blankets() {
        return this == MK2;
    }

    /** Barres de commande sur des mecanismes de grappe (le MKIII regle l'injection de combustible) ? */
    public boolean rodDrives() {
        return this != MK3;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static ReactorType of(String machineKey) {
        for (ReactorType t : values()) {
            if (t.machine.equals(machineKey)) {
                return t;
            }
        }
        return null;
    }
}
