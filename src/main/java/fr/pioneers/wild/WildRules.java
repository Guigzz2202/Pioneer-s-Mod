package fr.pioneers.wild;

/**
 * 1.0.5-beta (lot 6, specification « Le monde vivant » §4, §7) : REGLES PURES du gibier (aucune classe du jeu : le banc du rendu et les
 * GameTests les lisent aussi).
 * <ul>
 *     <li>bois du cerf ({@link #antlerGrowth}) : ils repoussent au printemps (0 -> 1 sur les 4/5 de la saison), sont portes l'ete et
 *     l'automne, et TOMBENT a un moment propre a chaque cerf entre les 3/4 de l'automne et le milieu de l'hiver ; aucun bois ensuite
 *     jusqu'au printemps ;</li>
 *     <li>pelage du lievre ({@link #winterCoat}) : blanc l'hiver, et toujours dans un biome enneige ;</li>
 *     <li>fuite en zigzag du lievre ({@link #zigzag}), charge du sanglier, defense du troupeau de bisons, alerte de la harde.</li>
 * </ul>
 */
public final class WildRules {

    private WildRules() {
    }

    /** Sortes (index dans {@link GeneratedWild#SPECIES}). */
    public static final int BOAR = 0, DEER = 1, TURKEY = 2, HARE = 3, BISON = 4;
    /** Sexes. */
    public static final int MALE = 0, FEMALE = 1;
    /** Actions synchronisees (pose du modele) : repos debout, couche, broute / fouille, parade (roue, brame), aux aguets, charge, fuite. */
    public static final int IDLE = 0, REST = 1, GRAZE = 2, DISPLAY = 3, ALERT = 4, CHARGE = 5, FLEE = 6;
    /** Harde : alerte des congeneres a 16 blocs, alerte qui dure 5 s ; cohesion au-dela de 10 blocs du centre (rayon de recherche 24). */
    public static final int HERD_RADIUS = 16, ALARM_TICKS = 100, HERD_KEEP = 10, HERD_SEARCH = 24;
    /** Bisons : tout le troupeau a 20 blocs defend celui qui est attaque, pendant 20 s au plus, jusqu'a 24 blocs de distance. */
    public static final int DEFEND_RADIUS = 20, ANGER_TICKS = 400, GIVE_UP_DISTANCE = 24;
    /** Sanglier : la charge dure 5 s au plus ; apres un coup, 4 s sans charger (il s'enfuit). */
    public static final int CHARGE_TICKS = 100, CHARGE_COOLDOWN = 80;
    /** Lievre : un crochet tous les 10 ticks, de 0,7 rad de part et d'autre de la ligne de fuite ; cerf : un bond tous les 14 ticks. */
    public static final int ZIG_TICKS = 10, LEAP_TICKS = 14;
    public static final double ZIG_ANGLE = 0.7;
    /** Bois du cerf : repousse sur les 4/5 du printemps ; chute entre 2,75 et 3,25 saisons (de la fin de l'automne au milieu de l'hiver). */
    public static final double REGROW = 0.8, SHED_FROM = 2.75, SHED_SPAN = 0.5;

    /**
     * Taille des bois (0 a 1) a la position {@code season + inSeason} dans l'annee (en saisons : 0 debut du printemps .. 4), pour un cerf
     * de graine {@code seed} (0 a 1, propre a la bete).
     */
    public static double antlerGrowth(int season, double inSeason, double seed) {
        double t = Math.floorMod(season, 4) + Math.max(0, Math.min(0.999999, inSeason));
        double shed = shedPoint(seed);
        if (t < REGROW) {
            return t / REGROW;
        }
        return t < shed ? 1 : 0;
    }

    /** Moment de la chute des bois (en saisons depuis le debut du printemps). */
    public static double shedPoint(double seed) {
        return SHED_FROM + SHED_SPAN * Math.max(0, Math.min(1, seed));
    }

    /** Graine (0 a 1) d'une bete d'apres son identifiant. */
    public static double seed(long most, long least) {
        long h = most * 0x9E3779B97F4A7C15L ^ least;
        h ^= h >>> 31;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** Pelage d'hiver du lievre : l'hiver, ou toujours dans un biome enneige. */
    public static boolean winterCoat(int season, boolean snowyBiome) {
        return snowyBiome || Math.floorMod(season, 4) == 3;
    }

    /** Cap de fuite du lievre au crochet {@code leg} : ligne de fuite + / - {@link #ZIG_ANGLE}, en alternance. */
    public static double zigzag(double awayYaw, int leg) {
        return awayYaw + ((leg & 1) == 0 ? ZIG_ANGLE : -ZIG_ANGLE);
    }

    /** Distance de fuite : divisee par deux pour un joueur accroupi (approche discrete). */
    public static double fleeDistance(int flee, boolean sneaking) {
        return sneaking ? flee * 0.5 : flee;
    }

    /** Charge du sanglier : un joueur a moins de {@code charge} blocs (ou une blessure). */
    public static boolean charges(double distance, int charge, boolean hurt) {
        return hurt || charge > 0 && distance < charge;
    }

    // ------------------------------------------------------------------ sanglier et cultures (1.0.5-beta lot 13)
    /*
     * 1.0.5-beta (lot 13, retour de Guillaume : « les sangliers cassent les champs non proteges par des barrieres : ils ne sont pas attires
     * par les champs mais ont une probabilite de vouloir les detruire (faim) ») : toutes les BOAR_HUNGER_CHECK ticks, un sanglier adulte a
     * faim avec la probabilite du reglage fauna.boarCropChance (par minute de jeu) ; affame, il fouille (action GRAZE) puis detruit au plus
     * BOAR_CROPS_PER_MEAL cultures a BOAR_CROP_RANGE blocs, jamais dans un claim, jamais dans un champ CLOS (barrieres du tag
     * pioneers:boar_barriers : clotures, murets, portillons), et seulement si la regle mobGriefing le permet.
     */
    /** Faim : tirage toutes les 200 ticks ; duree de la faim ; portee de recherche ; cultures detruites par repas ; fouille (ticks). */
    public static final int BOAR_HUNGER_CHECK = 200, BOAR_HUNGER_TICKS = 1200, BOAR_CROP_RANGE = 12, BOAR_CROPS_PER_MEAL = 3, BOAR_ROOT_TICKS = 60;
    /** Rayon de l'enclos cherche autour d'une culture (au-dela : champ ouvert). */
    public static final int BOAR_ENCLOSURE_RADIUS = 24;
    /** Probabilite par minute de jeu (defaut du reglage). */
    public static final double BOAR_CROP_CHANCE = 0.08;

    /** Probabilite d'avoir faim a un tirage (toutes les {@link #BOAR_HUNGER_CHECK} ticks) pour une probabilite par minute. */
    public static double boarHungerRoll(double perMinute) {
        double p = Math.max(0, Math.min(1, perMinute));
        return 1 - Math.pow(1 - p, BOAR_HUNGER_CHECK / 1200.0);
    }

    /** Le sanglier peut-il detruire cette culture ? */
    public static boolean boarMayRoot(boolean enabled, boolean griefing, boolean baby, boolean claimed, boolean enclosed) {
        return enabled && griefing && !baby && !claimed && !enclosed;
    }

    /**
     * PUR : la culture en (0, 0) est-elle dans un ENCLOS ? Parcours en largeur des cases franchissables ({@code open(dx, dz)}) a partir de la
     * culture : si on atteint la case du sanglier ({@code bx}, {@code bz}) ou le bord du rayon, le champ est ouvert ; sinon il est clos.
     */
    public static boolean enclosed(java.util.function.BiPredicate<Integer, Integer> open, int bx, int bz, int radius) {
        return enclosed(open, bx, bz, radius, new java.util.HashSet<>());
    }

    /** Idem, en notant les cases parcourues dans {@code seen} (cles {@link #cell}, relatives a la culture). */
    public static boolean enclosed(java.util.function.BiPredicate<Integer, Integer> open, int bx, int bz, int radius, java.util.Set<Long> seen) {
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{0, 0});
        seen.add(cell(0, 0));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == bx && c[1] == bz || Math.abs(c[0]) >= radius || Math.abs(c[1]) >= radius) {
                return false;
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], z = c[1] + d[1];
                if (seen.add(cell(x, z)) && open.test(x, z)) {
                    queue.add(new int[]{x, z});
                }
            }
        }
        return true;
    }

    /** Cle d'une case (x, z). */
    public static long cell(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
