package fr.pioneers.core;


/**
 * Transfert reel ou simule (socle Pioneer's, sans dependance) : toute insertion ou extraction peut d'abord etre simulee.
 */
public enum Action {
    EXECUTE, SIMULATE;

    public boolean execute() {
        return this == EXECUTE;
    }

    public boolean simulate() {
        return this == SIMULATE;
    }

    public static Action get(boolean execute) {
        return execute ? EXECUTE : SIMULATE;
    }

}
