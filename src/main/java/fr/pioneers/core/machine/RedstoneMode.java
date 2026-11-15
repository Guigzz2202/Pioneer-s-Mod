package fr.pioneers.core.machine;

import java.util.Locale;

/** Commande redstone d'une machine du socle : ignoree, marche avec un signal, ou marche sans signal. */
public enum RedstoneMode {
    IGNORED,
    HIGH,
    LOW;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RedstoneMode byIndex(int index) {
        return index >= 0 && index < values().length ? values()[index] : IGNORED;
    }

    /** La machine peut-elle travailler avec ce signal ? */
    public boolean allows(boolean powered) {
        return this == IGNORED || (this == HIGH) == powered;
    }
}
