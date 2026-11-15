package fr.pioneers.clan.core;

/**
 * 1.9 (MAJ RP, bloc 4) : horloge REELLE du serveur pour les delais des clans (votes, invitations, preavis, succession, delais de ralliement).
 * Un saut de l'horloge systeme est borne : aucun delai ne se raccourcit de plus de {@code max_jump_minutes} (une heure) d'un coup, et
 * l'horloge ne recule jamais. Entre deux lectures, le temps ecoule est mesure par {@link System#nanoTime()} (monotone) ; l'ecart avec
 * l'horloge systeme au-dela du saut permis est absorbe dans un decalage. Les autres agents lisent {@link #now()} au lieu de
 * {@code System.currentTimeMillis()}.
 */
public final class ClanClock {

    private static long offset;
    private static long lastClock = Long.MIN_VALUE, lastNano;

    private ClanClock() {
    }

    /** Heure des clans (ms). */
    public static synchronized long now() {
        long wall = System.currentTimeMillis(), nano = System.nanoTime();
        if (lastClock == Long.MIN_VALUE) {
            lastClock = wall;
            lastNano = nano;
            return wall;
        }
        long elapsed = Math.max(0, (nano - lastNano) / 1_000_000L);
        long clock = bound(lastClock, wall + offset, elapsed, maxJump());
        offset = clock - wall;
        lastClock = clock;
        lastNano = nano;
        return clock;
    }

    /**
     * Calcul pur (banc) : nouvelle heure a partir de la precedente, de l'heure lue (systeme + decalage) et du temps reellement ecoule. Jamais
     * de recul ; une avance au-dela du temps ecoule est bornee a {@code maxJump}.
     */
    public static long bound(long previous, long read, long elapsed, long maxJump) {
        long expected = previous + elapsed;
        if (read < previous) {
            return expected;
        }
        if (read > expected + maxJump) {
            return expected + maxJump;
        }
        return Math.max(read, previous);
    }

    private static long maxJump() {
        ClanDefs.Rules r = ClanDefs.get().rules;
        return (r == null ? 60 : r.maxJumpMinutes()) * ClanDefs.MINUTE;
    }

    /** Remet l'horloge a zero (arret du serveur). */
    public static synchronized void reset() {
        lastClock = Long.MIN_VALUE;
        offset = 0;
    }
}
