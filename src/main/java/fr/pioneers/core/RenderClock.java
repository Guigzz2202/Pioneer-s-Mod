package fr.pioneers.core;

/**
 * 1.0.5-beta (lot 29, retour 40 : « le systeme mecanique [...] sur la beta il commence a saccader de facon tres severe ») : HORLOGE DE RENDU
 * du client, en ticks DU SERVEUR, continue et monotone. Classe PURE (aucune classe du jeu) : avancee par {@code client/RenderClockTicker} a
 * la fin de chaque tick du client (jamais en pause), lue par les rendus (angle des pieces tournantes, bras du manege, girouette...).
 * <p>
 * Pourquoi : l'heure du monde du client ({@code level.getGameTime()}) avance d'un tick a chaque tick du CLIENT (20 par seconde), mais le
 * serveur la REMET a son heure toutes les 20 ticks du serveur ({@code ClientPacketListener.handleSetTime}). Un serveur dedie charge (plusieurs
 * joueurs, grands reseaux : 12 a 18 ticks / s, ticks irreguliers de 50 a 120 ms) la fait RECULER de 2 a 13 ticks d'un coup, chaque seconde :
 * a 64 tr/min (19,2 degres / tick), un engrenage reculait jusqu'a 250 degres d'un coup. En solo (serveur integre a 20 ticks / s) on ne le
 * voyait presque pas.
 * <p>
 * Ici : une ESTIMATION LISSEE de l'heure du serveur, qui n'avance qu'avec les ticks du client (plus la fraction de l'image) a une cadence
 * {@code rate} (ticks du serveur par tick du client) : chaque remise a l'heure du serveur observee (saut de l'heure du monde du client) mesure
 * la cadence reelle du serveur (moyenne glissante) et l'ecart restant est rattrape en douceur en ajustant la cadence (jamais en sautant), dans
 * les bornes [{@link #MIN_RATE}, {@link #MAX_RATE}]. L'horloge ne recule jamais ; elle ne saute qu'a l'entree dans un monde ou apres un ecart
 * de plus de {@link #SNAP} ticks (changement de dimension, serveur fige longtemps). Toutes les pieces lisent la MEME horloge : deux engrenages
 * d'un meme reseau restent en phase.
 */
public final class RenderClock {

    private RenderClock() {
    }

    /** Ecart (ticks) au-dela duquel l'horloge est recalee d'un coup (entree dans un monde, dimension, serveur fige des secondes). */
    public static final double SNAP = 100;
    /** Cadence minimale (ticks du serveur par tick du client) : serveur fige, l'horloge ralentit sans s'arreter ni reculer. */
    public static final double MIN_RATE = 0.02;
    /** Cadence maximale : rattrapage d'un serveur qui va plus vite (rattrape son retard) ; 1,15 x la vitesse au plus. */
    public static final double MAX_RATE = 1.15;
    /** Ticks du client pour rattraper un ecart (l'ecart / CATCH est ajoute a la cadence mesuree). */
    public static final double CATCH = 40;
    /** Sans saut de l'heure du monde pendant ce nombre de ticks, le serveur va a 20 ticks / s : l'heure du monde sert d'observation. */
    public static final int QUIET = 40;
    /** Poids d'une nouvelle mesure dans la cadence moyenne du serveur. */
    public static final double AVERAGE = 0.25;

    private static Object world;
    private static boolean started;
    private static long ticks, lastWorld, obsTick, quiet;
    private static double base, rate = 1, average = 1, obsTime;
    private static boolean observed;
    private static double lastServed;
    private static int snaps;

    /**
     * Fin d'un tick du client (pas en pause) : {@code level} = le monde du client (un autre monde = recalage), {@code worldTime} = son heure
     * ({@code getGameTime()}, avancee par le client et remise a l'heure par le serveur).
     */
    public static void tick(Object level, long worldTime) {
        if (!started || level != world) {
            world = level;
            started = true;
            ticks++;
            base = worldTime;
            rate = average = 1;
            lastWorld = worldTime;
            observed = false;
            quiet = 0;
            lastServed = base;
            snaps++;
            return;
        }
        ticks++;
        base += rate;
        boolean reset = worldTime != lastWorld + 1;
        lastWorld = worldTime;
        quiet++;
        if (reset || quiet >= QUIET) {
            observe(worldTime);
        }
    }

    /** Heure du serveur observee (remise a l'heure, ou heure du monde quand le serveur suit) : cadence mesuree et rattrapage. */
    private static void observe(long serverTime) {
        if (observed) {
            long dt = ticks - obsTick;
            if (dt >= 5) {
                double measured = Math.max(0.05, Math.min(2.0, (serverTime - obsTime) / dt));
                average += AVERAGE * (measured - average);
            }
        }
        observed = true;
        obsTime = serverTime;
        obsTick = ticks;
        quiet = 0;
        double error = serverTime - base;
        if (Math.abs(error) > SNAP) {
            base = serverTime;
            rate = Math.max(MIN_RATE, Math.min(MAX_RATE, average));
            lastServed = base;
            snaps++;
            return;
        }
        rate = Math.max(MIN_RATE, Math.min(MAX_RATE, average + error / CATCH));
    }

    /** Heure du serveur estimee (ticks) a l'image courante ({@code partial} : fraction du tick de l'image, 0..1). Monotone. */
    public static double time(float partial) {
        double t = base + rate * Math.max(0, Math.min(1, partial));
        if (t > lastServed) {
            lastServed = t;
        }
        return t;
    }

    /**
     * Compteur des ticks du client depuis le lancement (jamais remis a l'heure, arrete en pause) : animations integrees tick par tick sur le
     * client (regime et phase des machines des multiblocs...), qui ne doivent ni se figer ni bondir quand le serveur remet l'heure du monde.
     */
    public static long clientTicks() {
        return ticks;
    }

    /** Heure de la derniere image dessinee (ou du dernier tick) : instant d'un changement d'etat recu par le reseau. */
    public static double now() {
        return Math.max(base, lastServed);
    }

    /**
     * Vrai une fois l'horloge calee sur le serveur (un monde client a tourne et une heure du serveur a ete observee) : avant, l'heure estimee
     * n'a pas de sens (arrivee dans un monde : l'heure du monde du client part de 0) et un etat recu ne se raccorde pas (KineticAngle).
     */
    public static boolean started() {
        return started && observed;
    }

    /** Cadence courante (ticks du serveur par tick du client ; banc d'essai, fiche de debogage). */
    public static double rate() {
        return rate;
    }

    /** Nombre de recalages d'un coup depuis le lancement (banc d'essai). */
    public static int snaps() {
        return snaps;
    }

    /** Banc d'essai : remise a zero complete. */
    public static void reset() {
        world = null;
        started = false;
        ticks = lastWorld = obsTick = quiet = 0;
        base = obsTime = lastServed = 0;
        rate = average = 1;
        observed = false;
        snaps = 0;
    }
}
