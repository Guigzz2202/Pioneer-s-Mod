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
    /** 1.0.7 (« ciel grandiose ») : megastructures en fonctionnement (recu), forcees (captures) et activite affichee (0..1, lissee). */
    private static long working, forced;
    private static final float[] ACTIVITY = new float[Math.max(1, Megastructures.COUNT)];

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

    public static void setWorking(long mask) {
        working = mask;
    }

    /** Captures du ciel : megastructures montrees en marche (bit i), les autres au repos, tout de suite (0 : rien de force). */
    public static void force(long mask) {
        forced = mask;
        for (int i = 0; i < ACTIVITY.length && i < 64; i++) {
            ACTIVITY[i] = (mask & (1L << i)) != 0 ? 1 : 0;
        }
    }

    public static boolean working(int i) {
        return ((working | forced) & (1L << i)) != 0;
    }

    /** Activite affichee de la megastructure (0 au repos, 1 en marche) : glisse en quelques secondes. */
    public static float activity(int i) {
        return i >= 0 && i < ACTIVITY.length ? ACTIVITY[i] : 0;
    }

    /** Un tick client : l'activite affichee suit l'etat. */
    public static void tick() {
        for (int i = 0; i < ACTIVITY.length; i++) {
            float target = working(i) ? 1 : 0;
            float a = ACTIVITY[i];
            ACTIVITY[i] = Math.abs(target - a) < 0.004F ? target : a + (target - a) * 0.025F;
        }
    }

    /** Deconnexion : plus rien de debloque dans le ciel. */
    public static void clear() {
        working = 0;
        java.util.Arrays.fill(ACTIVITY, 0);
        unlocked = 0;
        delivered = new int[0][];
        openers = new String[0];
    }
}
