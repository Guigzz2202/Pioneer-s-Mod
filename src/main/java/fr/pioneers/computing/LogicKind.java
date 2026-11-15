package fr.pioneers.computing;

/**
 * Les deux automates de l'age industriel (0.17.0) : l'armoire a relais (4 regles, 2 liaisons, une evaluation par seconde, 64 FE/t) et
 * l'automate programmable (16 regles, 4 liaisons, 4 evaluations par seconde, minuteries, compteurs, bascules, 256 FE/t).
 */
public enum LogicKind {
    RELAY("relay_cabinet", 4, 2, 20, 64, false),
    PLC("plc", 16, 4, 5, 256, true);

    public static final int COUNTERS = 4;

    public final String id;
    public final int rules, links, period, power;
    /** Modes, delais, compteurs et bascules. */
    public final boolean advanced;

    LogicKind(String id, int rules, int links, int period, int power, boolean advanced) {
        this.id = id;
        this.rules = rules;
        this.links = links;
        this.period = period;
        this.power = power;
        this.advanced = advanced;
    }
}
