package fr.pioneers.space.mega;

/**
 * 1.0.7 (« ciel grandiose ») : megastructures EN FONCTIONNEMENT, cote serveur : un port qui travaille (un tick de son lot) note l'heure
 * ({@link #mark}) ; une megastructure est « en marche » tant qu'un de ses ports a travaille dans les {@link #HOLD} derniers ticks. Le masque
 * part aux clients a son changement (au plus une fois par seconde, {@link MegaEvents}) : le ciel anime alors la megastructure.
 * Aucune allocation : un tableau fixe d'heures.
 */
public final class MegaActivity {

    /** Duree (ticks) pendant laquelle un tick de travail garde la megastructure en marche (un lot attend parfois ses entrees). */
    public static final long HOLD = 200;
    private static final long[] LAST = new long[Math.max(1, Megastructures.COUNT)];
    private static long sent = -1;

    private MegaActivity() {
    }

    /** Un port de la megastructure {@code i} vient de travailler. */
    public static void mark(int i, long gameTime) {
        if (i >= 0 && i < LAST.length) {
            LAST[i] = gameTime + 1;
        }
    }

    /** Masque des megastructures en marche a l'heure {@code gameTime}. */
    public static long mask(long gameTime) {
        long m = 0;
        for (int i = 0; i < LAST.length && i < 64; i++) {
            if (LAST[i] > 0 && gameTime - (LAST[i] - 1) < HOLD) {
                m |= 1L << i;
            }
        }
        return m;
    }

    /** Masque a envoyer s'il a change depuis le dernier envoi, sinon -1. */
    static long changed(long gameTime) {
        long m = mask(gameTime);
        if (m == sent) {
            return -1;
        }
        sent = m;
        return m;
    }

    /** Demarrage du serveur : rien en marche, prochain masque envoye. */
    static void reset() {
        java.util.Arrays.fill(LAST, 0);
        sent = -1;
    }
}
