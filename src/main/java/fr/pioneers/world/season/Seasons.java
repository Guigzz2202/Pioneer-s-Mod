package fr.pioneers.world.season;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.world.level.Level;

/**
 * 1.0.5-beta (lot 1, specification « Le monde vivant » §1.1) : le CALENDRIER. Une seule date pour tout le serveur, tiree du temps du jour de
 * l'Overworld (le sommeil fait avancer le calendrier comme il avance les jours) moins un decalage memorise par monde ({@link SeasonData} :
 * 1er jour du printemps a la creation du monde, ou a sa premiere ouverture en 1.0.5). Quatre saisons de {@link #DEFAULT_LENGTH} jours
 * (reglable dans la config, {@link #MIN_LENGTH} a {@link #MAX_LENGTH}).
 * <p>
 * Le calcul ({@link #date}, {@link Date}, {@link #blend}) est PUR (aucun etat du jeu) : teste hors jeu par {@code tools/seasons/SeasonBench.java}.
 * L'etat (decalage et duree) est garde ici pour le serveur ({@link #setServer}) et pour le client ({@link #setClient}, recu par
 * {@link PacketSeason}) ; {@link #now} le lit du bon cote. Les autres dimensions (Nether, End, astres) n'ont pas de saisons : {@link #now}
 * y rend null ; le calendrier ({@link #calendar}) donne pourtant partout la date du serveur.
 */
public final class Seasons {

    /** Duree d'une saison par defaut (jours de jeu), bornes de la config. */
    public static final int DEFAULT_LENGTH = 10, MIN_LENGTH = 1, MAX_LENGTH = 30;
    /** Un jour de jeu (ticks). */
    public static final long DAY = 24_000L;
    /** Paliers d'une saison (teintes du lot 2, page du calendrier). */
    public static final int STEPS = 16;
    /** Jours de fondu de part et d'autre d'un changement de saison (2 derniers jours d'une saison, 2 premiers de la suivante). */
    public static final double BLEND_DAYS = 2;

    private Seasons() {
    }

    /** Les quatre saisons, dans l'ordre de l'annee. */
    public enum Season {
        SPRING, SUMMER, AUTUMN, WINTER;

        private static final Season[] ALL = values();

        public Season next() {
            return ALL[(ordinal() + 1) % 4];
        }

        public Season prev() {
            return ALL[(ordinal() + 3) % 4];
        }

        /** Cle de langue {@code season.pioneers.<nom>}. */
        public String key() {
            return "season.pioneers." + name().toLowerCase(Locale.ROOT);
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Season byId(String id) {
            for (Season s : ALL) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return null;
        }

        public static Season byIndex(int i) {
            return ALL[Math.floorMod(i, 4)];
        }
    }

    /**
     * Une date du calendrier.
     *
     * @param day      jour absolu depuis le depart (0 = 1er jour du printemps de l'annee 1), peut etre negatif apres une commande.
     * @param fraction avancement du jour (0 a 1).
     * @param length   duree d'une saison (jours).
     */
    public record Date(long day, double fraction, int length) {

        public long yearLength() {
            return 4L * length;
        }

        public Season season() {
            return Season.byIndex((int) (Math.floorMod(day, yearLength()) / length));
        }

        /** Jour de la saison, de 1 a {@link #length}. */
        public int dayOfSeason() {
            return (int) Math.floorMod(day, (long) length) + 1;
        }

        /** Position dans la saison, en jours (0 a {@link #length}, continue). */
        public double inSeason() {
            return Math.floorMod(day, (long) length) + fraction;
        }

        /** Fraction de l'annee (0 a 1, continue). */
        public double yearFraction() {
            return (Math.floorMod(day, yearLength()) + fraction) / yearLength();
        }

        /** Palier de la saison, 0 a {@link #STEPS} - 1. */
        public int step() {
            return Math.min(STEPS - 1, (int) Math.floor(inSeason() / length * STEPS));
        }

        /** Jours avant le premier jour de la saison suivante (1 = demain). */
        public int daysToNext() {
            return length - dayOfSeason() + 1;
        }

        /** Annee (1 = la premiere). */
        public long year() {
            return Math.floorDiv(day, yearLength()) + 1;
        }

        /** Meme calendrier, {@code days} jours plus tard (fractionnaires). */
        public Date plusDays(double days) {
            double t = day + fraction + days;
            long d = (long) Math.floor(t);
            return new Date(d, t - d, length);
        }
    }

    /** Date a partir du temps du jour de l'Overworld, du decalage du monde et de la duree d'une saison (bornee). */
    public static Date date(long dayTime, long offset, int length) {
        long t = dayTime - offset;
        return new Date(Math.floorDiv(t, DAY), Math.floorMod(t, DAY) / (double) DAY, clampLength(length));
    }

    public static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    /** Decalage qui fait de l'instant {@code dayTime} le jour {@code dayOfSeason} (1..) de {@code season}, la meme annee, meme heure. */
    public static long offsetFor(long dayTime, long offset, int length, Season season, int dayOfSeason) {
        Date now = date(dayTime, offset, length);
        int l = clampLength(length);
        long target = (now.year() - 1) * 4L * l + (long) season.ordinal() * l + Math.max(0, Math.min(l - 1, dayOfSeason - 1));
        return Math.floorDiv(dayTime, DAY) * DAY - target * DAY + Math.floorMod(offset, DAY);
    }

    /**
     * Fondu entre deux saisons : une grandeur saisonniere vaut {@code mix(v[from], v[to])} = v[from] (1 - t) + v[to] t. Pendant les
     * {@link #BLEND_DAYS} premiers jours d'une saison, on termine la transition depuis la precedente (t de 0,5 a 1) ; pendant les
     * {@link #BLEND_DAYS} derniers, on amorce la suivante (t de 0 a 0,5) ; au changement de saison, moitie-moitie des deux cotes : continu.
     * Saisons de moins de 4 jours : fenetre de la moitie de la saison.
     */
    public record Blend(Season from, Season to, double t) {

        public double mix(double[] bySeason) {
            return bySeason[from.ordinal()] * (1 - t) + bySeason[to.ordinal()] * t;
        }

        /** Poids d'une saison dans le melange (0 a 1). */
        public double weight(Season s) {
            return (from == s ? 1 - t : 0) + (to == s ? t : 0);
        }
    }

    public static Blend blend(Date d) {
        int l = d.length();
        double x = d.inSeason(), w = Math.min(BLEND_DAYS, l / 2.0);
        Season s = d.season();
        if (x < w) {
            return new Blend(s.prev(), s, 0.5 + 0.5 * x / w);
        }
        if (x > l - w) {
            return new Blend(s, s.next(), 0.5 * (x - (l - w)) / w);
        }
        return new Blend(s, s, 0);
    }

    /** Valeur saisonniere au jour {@code d} (table par saison, fondue). */
    public static double mix(double[] bySeason, Date d) {
        return blend(d).mix(bySeason);
    }

    // ------------------------------------------------------------------ etat (serveur, client)

    private static volatile long serverOffset;
    private static volatile int serverLength = DEFAULT_LENGTH;
    private static volatile boolean serverReady;
    private static volatile long clientOffset;
    private static volatile int clientLength = DEFAULT_LENGTH;
    private static volatile boolean clientKnown;

    public static void setServer(long offset, int length) {
        serverOffset = offset;
        serverLength = clampLength(length);
        serverReady = true;
    }

    public static void clearServer() {
        serverReady = false;
    }

    public static long serverOffset() {
        return serverOffset;
    }

    public static int serverLength() {
        return serverLength;
    }

    public static boolean serverReady() {
        return serverReady;
    }

    public static void setClient(long offset, int length) {
        clientOffset = offset;
        clientLength = clampLength(length);
        clientKnown = true;
    }

    public static void clearClient() {
        clientKnown = false;
    }

    /** Vrai si cette dimension a des saisons (l'Overworld seulement). */
    public static boolean hasSeasons(@Nullable Level level) {
        return level != null && level.dimension() == Level.OVERWORLD;
    }

    /** Date du serveur, lue du bon cote (null si inconnue : client pas encore synchronise, serveur pas demarre). Toutes les dimensions. */
    @Nullable
    public static Date calendar(@Nullable Level level) {
        if (level == null) {
            return null;
        }
        if (level.isClientSide) {
            return clientKnown ? date(level.getDayTime(), clientOffset, clientLength) : null;
        }
        return serverReady ? date(level.getDayTime(), serverOffset, serverLength) : null;
    }

    /** Date qui gouverne le climat et le temps ici : null hors de l'Overworld (pas de saisons) ou si elle est inconnue. */
    @Nullable
    public static Date now(@Nullable Level level) {
        return hasSeasons(level) ? calendar(level) : null;
    }
}
