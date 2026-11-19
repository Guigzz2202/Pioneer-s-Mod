package fr.pioneers.space.mega.client;

import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mega.PacketMegaState;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : l'etat des megastructures cote CLIENT (recu par {@link PacketMegaState}) : masque des
 * debloquees (parametre du calcul du ciel, lu a chaque image sans allocation), livraisons des chantiers et ouvreurs (ecran du chantier).
 */
public final class MegaClient {

    private static long unlocked;
    private static int[][] delivered = new int[0][];
    private static String[] openers = new String[0];

    private MegaClient() {
    }

    public static void set(PacketMegaState state) {
        unlocked = state.unlocked();
        delivered = state.delivered();
        openers = state.openers();
    }

    /** Megastructures debloquees (bit i) : parametre du ciel. */
    public static long unlockedMask() {
        return unlocked;
    }

    public static boolean unlocked(int i) {
        return (unlocked & (1L << i)) != 0;
    }

    public static int delivered(int i, int k) {
        return i < delivered.length && k < delivered[i].length ? delivered[i][k] : 0;
    }

    public static int deliveredTotal(int i) {
        int t = 0;
        for (int k = 0; k < Megastructures.ingredientCount(i); k++) {
            t += Math.min(Megastructures.needed(i, k), delivered(i, k));
        }
        return t;
    }

    public static String opener(int i) {
        return i < openers.length ? openers[i] : "";
    }

    /** Deconnexion : plus rien de debloque dans le ciel. */
    public static void clear() {
        unlocked = 0;
        delivered = new int[0][];
        openers = new String[0];
    }
}
