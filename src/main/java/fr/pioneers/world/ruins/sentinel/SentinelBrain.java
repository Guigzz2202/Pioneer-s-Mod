package fr.pioneers.world.ruins.sentinel;

/**
 * 1.9 (MAJ RP, bloc 1) : machine a etats d'une Sentinelle, sans classe Minecraft (banc hors jeu {@code tools/rptest/SentinelBench.java}).
 * <ol>
 * <li>DORMANT : IA coupee ; seul un eveil ({@link #wake}) la fait sortir, jamais en Veille ;</li>
 * <li>ALERT : scene de reveil ({@code alert_ticks}, 1,5 s) puis COMBAT si une cible est la, sinon RETURN ;</li>
 * <li>COMBAT : tant qu'une cible valide existe dans la laisse ; Veille, perte de la cible ou laisse depassee : RETURN ;</li>
 * <li>RETURN : regagne son poste, reprend le combat si une cible revient, se rendort au poste apres {@code sleep_after} ticks sans cible
 * (aussitot en Veille) ; abandon apres {@code give_up} ticks (l'entite est alors replacee a son poste).</li>
 * </ol>
 */
public final class SentinelBrain {

    public enum State {
        DORMANT, ALERT, COMBAT, RETURN;

        private static final State[] ALL = values();

        public static State of(int i) {
            return i >= 0 && i < ALL.length ? ALL[i] : DORMANT;
        }
    }

    /** Ce qui vient de se passer pendant un tick (pour les sons, phrases et l'eveil du site). */
    public enum Event {
        NONE,
        /** Fin de la scene de reveil, cible presente : le combat commence (eveille tout le site). */
        ENGAGED,
        /** Cible perdue ou Veille : retour au poste. */
        DISENGAGED,
        /** Laisse depassee : retour au poste. */
        LEASH,
        /** Rendormie a son poste. */
        SLEPT,
        /** Rendormie hors de son poste apres l'abandon du retour : l'entite doit etre replacee. */
        GAVE_UP
    }

    private State state = State.DORMANT;
    private int timer;
    private int idle;
    private int returning;
    private boolean fought;

    public State state() {
        return state;
    }

    public boolean dormant() {
        return state == State.DORMANT;
    }

    public boolean awake() {
        return state != State.DORMANT;
    }

    /** Vrai si la Sentinelle a combattu depuis son eveil (phrase de retour au poste). */
    public boolean fought() {
        return fought;
    }

    /** Ticks sans cible depuis la fin du combat. */
    public int idle() {
        return idle;
    }

    /**
     * Demande d'eveil (joueur repere, bloc delabre casse, alarme, coup recu). Refusee en Veille. Une Sentinelle deja eveillee qui rentrait
     * a son poste repart a zero (ne se rendort pas). Vrai seulement si elle sortait du sommeil (scene de reveil).
     */
    public boolean wake(boolean standby) {
        if (standby) {
            return false;
        }
        if (state == State.RETURN) {
            idle = 0;
            returning = 0;
            return false;
        }
        if (state != State.DORMANT) {
            return false;
        }
        state = State.ALERT;
        timer = -1;
        idle = 0;
        returning = 0;
        fought = false;
        return true;
    }

    /** Rendort sur-le-champ (Veille posee alors que la Sentinelle est a son poste, chargement d'une sauvegarde). */
    public void sleep() {
        state = State.DORMANT;
        timer = 0;
        idle = 0;
        returning = 0;
    }

    /** Reprise apres chargement : une Sentinelle eveillee rentre a son poste. */
    public void restore(State saved) {
        if (saved == State.DORMANT) {
            sleep();
        } else {
            state = State.RETURN;
            idle = 0;
            returning = 0;
        }
    }

    /**
     * Un tick d'une Sentinelle eveillee.
     *
     * @param alertTicks  duree de la scene de reveil
     * @param sleepAfter  ticks sans cible avant de se rendormir au poste
     * @param giveUp      ticks de retour avant abandon
     * @param standby     site en Veille
     * @param hasTarget   une cible valide existe (joueur hors creatif, dans la laisse)
     * @param home        a son poste
     * @param beyondLeash plus loin que la laisse de son poste
     */
    public Event tick(int alertTicks, int sleepAfter, int giveUp, boolean standby, boolean hasTarget, boolean home, boolean beyondLeash) {
        switch (state) {
            case DORMANT:
                return Event.NONE;
            case ALERT:
                if (timer < 0) {
                    timer = alertTicks;
                }
                if (standby) {
                    toReturn();
                    return Event.DISENGAGED;
                }
                if (--timer > 0) {
                    return Event.NONE;
                }
                if (hasTarget && !beyondLeash) {
                    state = State.COMBAT;
                    fought = true;
                    return Event.ENGAGED;
                }
                toReturn();
                return Event.NONE;
            case COMBAT:
                if (beyondLeash) {
                    toReturn();
                    return Event.LEASH;
                }
                if (standby || !hasTarget) {
                    toReturn();
                    return Event.DISENGAGED;
                }
                return Event.NONE;
            case RETURN:
            default:
                if (!standby && hasTarget && !beyondLeash) {
                    state = State.COMBAT;
                    fought = true;
                    return Event.ENGAGED;
                }
                idle++;
                returning++;
                if (home && (standby || idle >= sleepAfter)) {
                    sleep();
                    return Event.SLEPT;
                }
                if (returning >= giveUp) {
                    sleep();
                    return Event.GAVE_UP;
                }
                return Event.NONE;
        }
    }

    private void toReturn() {
        state = State.RETURN;
        idle = 0;
        returning = 0;
    }

    /**
     * Laisse stricte : position ramenee sur la sphere de rayon {@code leash} autour du poste si elle en sort (null si elle est dedans).
     */
    public static double[] clamp(double x, double y, double z, double px, double py, double pz, double leash) {
        double dx = x - px, dy = y - py, dz = z - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= leash || d < 1.0E-6) {
            return null;
        }
        double k = leash / d;
        return new double[]{px + dx * k, py + dy * k, pz + dz * k};
    }
}
