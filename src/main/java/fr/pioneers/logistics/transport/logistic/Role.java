package fr.pioneers.logistics.transport.logistic;

/**
 * 1.11 : role d'un point (raccord x canal). Entree = de la machine vers le cable ; sortie = du cable vers la machine. Pour la chaleur et la
 * pression, le role est un clapet (entree : la chaleur ou l'air ne va que de la machine au cable ; sortie : l'inverse ; les deux : libre).
 */
public enum Role {
    NONE, INPUT, OUTPUT, BOTH;

    private static final Role[] VALUES = values();

    /** La machine donne au cable. */
    public boolean in() {
        return this == INPUT || this == BOTH;
    }

    /** Le cable livre a la machine. */
    public boolean out() {
        return this == OUTPUT || this == BOTH;
    }

    public static Role byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
