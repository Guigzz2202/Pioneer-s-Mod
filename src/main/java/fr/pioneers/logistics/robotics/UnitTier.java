package fr.pioneers.logistics.robotics;

/**
 * Tiers des unites : chacun demande le metal d'un astre plus lointain (Lune, Venus, Saturne, Pluton). Un tier plus eleve va plus vite,
 * emporte plus et travaille mieux, mais consomme nettement plus d'energie.
 */
public enum UnitTier {
    T1("lunarium", 0x8C78EC, 1.0, 1.0, 1.0, 1.0, 32),
    T2("venusium", 0xF6C446, 1.25, 1.5, 1.35, 1.7, 48),
    T3("krakenite", 0xD89A3A, 1.5, 2.0, 1.75, 2.8, 64),
    T4("sputnikite", 0xF0E0C0, 1.8, 3.0, 2.3, 4.5, 96);

    /** Metal de fabrication (tag forge:ingots/...). */
    public final String material;
    /** Couleur des liseres (rendu, interface). */
    public final int color;
    public final double speed, capacity, work, energy;
    /** Portee d'un hub equipe de ce tier (blocs autour du hub). */
    public final int range;

    UnitTier(String material, int color, double speed, double capacity, double work, double energy, int range) {
        this.material = material;
        this.color = color;
        this.speed = speed;
        this.capacity = capacity;
        this.work = work;
        this.energy = energy;
        this.range = range;
    }

    public int level() {
        return ordinal() + 1;
    }

    public static UnitTier byIndex(int index) {
        UnitTier[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }
}
