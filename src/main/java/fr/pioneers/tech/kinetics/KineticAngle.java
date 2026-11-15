package fr.pioneers.tech.kinetics;

/**
 * 1.0.5-beta (lot 29, retour 40) : ANGLE AFFICHE d'une piece tournante, cote client. Classe PURE (le banc {@code tools/perf/FluidityAngleBench}
 * l'appelle telle quelle).
 * <p>
 * Reference envoyee par le serveur : phase {@code phase} (degres) a l'instant serveur {@code time}, vitesse {@code speed} (tr/min). L'angle
 * NOMINAL a l'heure serveur estimee {@code s} ({@link fr.pioneers.core.RenderClock}, la meme pour toutes les pieces) est
 * {@code phase + speed x DEG_PER_TICK x (s - time)} : deux pieces d'un meme reseau, recues a des moments differents, restent en phase (le
 * serveur avance la phase de tout le reseau d'un meme pas a chaque tick).
 * <p>
 * Un nouvel etat recu (changement de vitesse, blocage, reconstruction du reseau) ne fait JAMAIS sauter l'angle : l'ecart entre l'angle affiche
 * a l'instant de la reception et le nouvel angle nominal ({@code offset}, ramene a -180..180) est garde puis resorbe au fil de la rotation :
 * il diminue de {@link #BLEND} degre par degre parcouru. La piece tourne donc entre 0,7 et 1,3 fois sa vitesse le temps du raccord (jamais a
 * l'envers), et reste immobile a l'arret (l'ecart attend le redemarrage). Les pieces d'un meme reseau recoivent le meme paquet au meme
 * instant : leurs ecarts sont proportionnels a leurs rapports, l'engrenement tient pendant le raccord.
 */
public final class KineticAngle {

    /** Part de l'ecart resorbee par degre parcouru (vitesse affichee entre 1 - BLEND et 1 + BLEND fois la vitesse). */
    public static final double BLEND = 0.3;

    private double speed, phase, time = Double.NaN;
    private double offset, offsetAt;

    /** Vitesse de la reference (tr/min). */
    public double speed() {
        return speed;
    }

    /** Vrai une fois une reference recue. */
    public boolean known() {
        return !Double.isNaN(time);
    }

    /** Angle nominal (degres, non ramene a 0..360) a l'heure serveur estimee {@code s}. */
    public double nominal(double s) {
        return phase + speed * KineticTile.DEG_PER_TICK * (s - time);
    }

    /** Angle affiche (degres, non ramene a 0..360) a l'heure serveur estimee {@code s}. */
    public double angle(double s) {
        if (Double.isNaN(time)) {
            return speed * KineticTile.DEG_PER_TICK * s;
        }
        double n = nominal(s);
        if (offset == 0) {
            return n;
        }
        double left = 1 - Math.abs(n - offsetAt) * BLEND / Math.abs(offset);
        return left > 0 ? n + offset * left : n;
    }

    /**
     * Nouvelle reference recue a l'heure serveur estimee {@code now} (instant de la derniere image) : sans saut (l'ecart est garde puis resorbe),
     * sauf la toute premiere (piece qui arrive : rien n'etait affiche).
     */
    public void receive(double speed, double phase, double time, double now) {
        receive(speed, phase, time, now, true);
    }

    /** Idem ; {@code smooth} faux : pas de raccord (horloge pas encore lancee : l'angle affiche avant n'a pas de sens). */
    public void receive(double speed, double phase, double time, double now, boolean smooth) {
        boolean had = smooth && !Double.isNaN(this.time);
        double before = had ? angle(now) : 0;
        this.speed = speed;
        this.phase = phase;
        this.time = time;
        if (!had) {
            offset = 0;
            return;
        }
        double n = nominal(now);
        double d = ((before - n) % 360.0 + 540.0) % 360.0 - 180.0;
        offset = Math.abs(d) < 1e-9 ? 0 : d;
        offsetAt = n;
    }

    /** Ecart en cours de raccord (degres ; banc d'essai). */
    public double offset() {
        return offset;
    }
}
