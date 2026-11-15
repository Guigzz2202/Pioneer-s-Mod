package fr.pioneers.space.mining;

import java.util.Locale;

/**
 * Modules du rover (3 emplacements dans le controleur ; un module de chaque type compte une seule fois).
 */
public enum RoverModule {
    /** Batteries au lunarium : autonomie doublee. */
    BATTERY(MiningMaterial.LUNARIUM),
    /** Benne renforcee au marsium : capacite doublee. */
    CARGO(MiningMaterial.MARSIUM),
    /** Sondeur au venusium : prospection plus large, plus de gisements. */
    SCANNER(MiningMaterial.VENUSIUM),
    /** Surcadencage au tharsium : forage 60 % plus rapide. */
    OVERCLOCK(MiningMaterial.THARSIUM),
    /** Raffinerie embarquee au mercurium : 30 % de chances de minerai supplementaire. */
    REFINERY(MiningMaterial.MERCURIUM);

    public final MiningMaterial material;

    RoverModule(MiningMaterial material) {
        this.material = material;
    }

    public String id() {
        return "rover_module_" + name().toLowerCase(Locale.ROOT);
    }
}
