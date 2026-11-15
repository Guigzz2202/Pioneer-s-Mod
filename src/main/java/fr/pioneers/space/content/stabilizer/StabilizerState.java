package fr.pioneers.space.content.stabilizer;

/** Etat de l'astre du Stabilisateur spatial. */
public enum StabilizerState {
    /** Eteint : pas d'astre. */
    OFF,
    /** Sequence d'allumage : l'astre se forme pendant que le champ se met en place. */
    IGNITING,
    /** Astre capture par le champ : production. */
    STABLE;

    private static final StabilizerState[] VALUES = values();

    public static StabilizerState byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : OFF;
    }
}
