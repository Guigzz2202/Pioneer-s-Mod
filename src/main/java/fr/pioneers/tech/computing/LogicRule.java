package fr.pioneers.tech.computing;

import net.minecraft.nbt.CompoundTag;

/**
 * Regle d'une armoire a relais ou d'un automate (0.17.0) : SI [source].[grandeur] [comparateur] [seuil] ALORS [action].
 * <p>
 * Source : une machine liee (0..n-1) ou l'interne de l'automate (n : compteurs, horloge). Mode (automate seulement) : direct (l'action
 * dure tant que la condition est vraie), impulsion (une fois au front montant), bascule (reste vraie jusqu'a une regle « liberer les
 * bascules »). Delai (automate) : la condition doit tenir ce nombre de secondes. Actions : sortie redstone sur une face, commande d'une
 * machine liee, compteur +1, compteur a zero, liberer les bascules.
 */
public final class LogicRule {

    public static final int GT = 0, LT = 1, EQ = 2, GE = 3, LE = 4, NE = 5, COMPARATORS = 6;
    public static final String[] SYMBOLS = {">", "<", "=", "≥", "≤", "≠"};
    public static final int DIRECT = 0, PULSE = 1, LATCH = 2, MODES = 3;
    public static final int REDSTONE = 0, COMMAND = 1, COUNT = 2, COUNT_RESET = 3, UNLATCH = 4, ACTIONS = 5;
    public static final String[] MODE_KEYS = {"direct", "pulse", "latch"};
    public static final String[] ACTION_KEYS = {"redstone", "command", "count", "count_reset", "unlatch"};

    public boolean enabled;
    public int source;
    public String key = "";
    public int comparator = GT;
    public double threshold;
    public int mode = DIRECT;
    /** Secondes pendant lesquelles la condition doit tenir. */
    public int delay;
    public int action = REDSTONE;
    /** Face (redstone : 0..5), machine liee (commande) ou compteur (0..3). */
    public int target;
    /** Niveau redstone (0..15) ou valeur de la commande. */
    public int level = 15;
    public String command = "";

    // etat d'execution (le drapeau « vraie » est montre par l'interface)
    public boolean active;
    boolean last, latched;
    int held;

    public boolean test(double value) {
        return switch (comparator) {
            case GT -> value > threshold;
            case LT -> value < threshold;
            case EQ -> Math.abs(value - threshold) < 0.5;
            case GE -> value >= threshold;
            case LE -> value <= threshold;
            default -> Math.abs(value - threshold) >= 0.5;
        };
    }

    public void clear() {
        enabled = false;
        source = 0;
        key = "";
        comparator = GT;
        threshold = 0;
        mode = DIRECT;
        delay = 0;
        action = REDSTONE;
        target = 0;
        level = 15;
        command = "";
        active = last = latched = false;
        held = 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("on", enabled);
        tag.putInt("src", source);
        tag.putString("key", key);
        tag.putInt("cmp", comparator);
        tag.putDouble("thr", threshold);
        tag.putInt("mode", mode);
        tag.putInt("delay", delay);
        tag.putInt("act", action);
        tag.putInt("tgt", target);
        tag.putInt("lvl", level);
        tag.putString("cmd", command);
        tag.putBoolean("active", active);
        tag.putBoolean("latched", latched);
        return tag;
    }

    public void load(CompoundTag tag) {
        enabled = tag.getBoolean("on");
        source = tag.getInt("src");
        key = tag.getString("key");
        comparator = Math.floorMod(tag.getInt("cmp"), COMPARATORS);
        threshold = tag.getDouble("thr");
        mode = Math.floorMod(tag.getInt("mode"), MODES);
        delay = Math.max(0, tag.getInt("delay"));
        action = Math.floorMod(tag.getInt("act"), ACTIONS);
        target = tag.getInt("tgt");
        level = tag.getInt("lvl");
        command = tag.getString("cmd");
        active = tag.getBoolean("active");
        latched = tag.getBoolean("latched");
    }
}
