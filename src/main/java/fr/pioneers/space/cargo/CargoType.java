package fr.pioneers.space.cargo;

/**
 * Sortes de marchandises des soutes logistiques (modules de fusee) : objets, fluides, gaz, donnees.
 */
public enum CargoType {
    ITEMS(0xF0A030, "items"),
    FLUIDS(0x3C8CFF, "fluids"),
    GAS(0xF0E050, "gas"),
    DATA(0x9B6BFF, "data");

    /** Couleur de la soute (et des routes sur la carte). */
    public final int color;
    public final String id;

    CargoType(int color, String id) {
        this.color = color;
        this.id = id;
    }

    public String getTranslationKey() {
        return "cargo.pioneers.type." + id;
    }

    public static CargoType byIndex(int index) {
        CargoType[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
