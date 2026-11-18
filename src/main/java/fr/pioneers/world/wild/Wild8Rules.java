package fr.pioneers.world.wild;

/**
 * 1.0.5-beta (lot 8, specification « Le monde vivant » §4 « Savane » et « Jungle et foret tropicale ») : REGLES PURES de la savane et de la
 * jungle (aucune classe du jeu : le banc du rendu, les GameTests et le controle les lisent aussi).
 * <ul>
 *     <li>girafe : broute la cime des acacias ({@link #BROWSE_RANGE} blocs autour, feuilles de {@link #BROWSE_LOW} a {@link #BROWSE_HIGH}
 *     blocs au-dessus de ses sabots) ; paisible ; frappee a moins de {@link #KICK_RANGE} blocs, elle RUE ({@link #giraffeKicks}) ;</li>
 *     <li>lion : FAINEANT, couche la plupart du temps ; un joueur qui RESTE a moins de {@link #LION_RANGE} blocs le fait gronder
 *     ({@link #LION_WARN} ticks) puis attaquer ({@link #LION_PATIENCE} ticks : {@link #lionAttacks}) ; la LIONNE chasse le gibier de la
 *     savane a l'aube, au crepuscule et la nuit, une fois par jour au plus ({@link #lionessHunts}) ;</li>
 *     <li>rhinoceros : CHARGE A VUE ce qui passe a moins de {@link #RHINO_SIGHT} blocs ({@link #rhinoCharges}), cuirasse, recul fort ;</li>
 *     <li>elephant : troupeaux familiaux ; un joueur a moins de {@link #CALF_GUARD} blocs d'un petit, ou toute attaque contre un petit ou un
 *     adulte, fait charger TOUT le troupeau a {@link #HERD_DEFEND} blocs ({@link #elephantDefends}) ;</li>
 *     <li>singe : cueille les bananes (bananiers du mod a {@link #FORAGE_RANGE} blocs, sinon il en trouve dans la canopee de la jungle) ;
 *     l'EPLUCHE, la MANGE et JETTE LA PEAU ({@link #eatStage}) ; VOLE parfois la banane tenue par un joueur proche, jamais autre chose
 *     ({@link #monkeySteals}, reglage serveur {@code fauna.monkeyTheft}) ;</li>
 *     <li>tigre : chasse le joueur A VUE ({@link #TIGER_SIGHT} blocs), A L'AFFUT (tapi, lent) puis BONDIT a {@link #POUNCE_RANGE} blocs
 *     ({@link #tigerStep}) ; craint le feu comme le loup ({@link #TIGER_FEAR_RANGE} blocs).</li>
 * </ul>
 */
public final class Wild8Rules {

    private Wild8Rules() {
    }

    /** Sortes (a la suite des lots 6 et 7). */
    public static final int GIRAFFE = 11, LION = 12, RHINO = 13, ELEPHANT = 14, MONKEY = 15, TIGER = 16;
    /** Actions en plus de celles des lots 6 et 7 : broute en hauteur, ruade, affut, bond, escalade, repas du singe. */
    public static final int BROWSE = 9, KICK = 10, STALK = 11, POUNCE = 12, CLIMB = 13, EAT = 14;

    /** Durete d'armure (rhinoceros, elephant). */
    public static double toughness(int kind) {
        return kind == RHINO ? 6 : kind == ELEPHANT ? 4 : 0;
    }

    /** Resistance au recul : les grands animaux ne reculent presque pas sous les coups. */
    public static double knockbackResistance(int kind) {
        return switch (kind) {
            case RHINO -> 0.85;
            case ELEPHANT -> 0.95;
            case GIRAFFE -> 0.6;
            case LION, TIGER -> 0.3;
            default -> 0.0;
        };
    }

    // ------------------------------------------------------------------ girafe
    public static final int BROWSE_RANGE = 8, BROWSE_LOW = 3, BROWSE_HIGH = 4, BROWSE_MIN = 120, BROWSE_MAX = 260;
    public static final double KICK_RANGE = 3.5;
    public static final int KICK_TICKS = 14, KICK_HIT = 7, KICK_COOLDOWN = 40;
    public static final float KICK_DAMAGE = 6F, KICK_KNOCK = 1.6F;

    /** La girafe rue-t-elle contre son agresseur ? (assez pres, pas deja en train de ruer, pas un petit) */
    public static boolean giraffeKicks(double distance, int cooldown, boolean baby) {
        return !baby && cooldown <= 0 && distance <= KICK_RANGE;
    }

    // ------------------------------------------------------------------ lion
    public static final int LION_RANGE = 6, LION_WARN = 20, LION_PATIENCE = 60, LION_GIVE_UP = 16, LION_RAGE = 200;
    public static final int LIONESS_HUNT_RANGE = 24, LIONESS_FED = 12000, PRIDE_RADIUS = 16;

    /** Le lion attaque-t-il ce joueur ? seulement s'il est reste a moins de 6 blocs assez longtemps. */
    public static boolean lionAttacks(double distance, int nearTicks) {
        return distance < LION_RANGE && nearTicks >= LION_PATIENCE;
    }

    /** Le lion gronde-t-il ? (avertissement : le joueur s'attarde) */
    public static boolean lionWarns(double distance, int nearTicks) {
        return distance < LION_RANGE && nearTicks >= LION_WARN && nearTicks < LION_PATIENCE;
    }

    /** Compteur de presence : +1 par tick sous 6 blocs, sinon il redescend 2 fois plus vite. */
    public static int lionNear(int near, double distance) {
        return distance < LION_RANGE ? Math.min(near + 1, LION_PATIENCE * 4) : Math.max(0, near - 2);
    }

    /**
     * Heure de chasse de la lionne : de la fin d'apres-midi a l'aube (temps du jour du jeu : 11 000 a 23 500 ; le jour, elle se repose). A
     * jeun ({@code fedLeft} 0), proie a moins de 24 blocs.
     */
    public static boolean lionessHunts(long dayTime, double distance, int fedLeft) {
        long t = Math.floorMod(dayTime, 24000L);
        return fedLeft <= 0 && distance < LIONESS_HUNT_RANGE && (t >= 11000 && t <= 23500);
    }

    // ------------------------------------------------------------------ rhinoceros
    public static final int RHINO_SIGHT = 8, RHINO_CALM = 80, RHINO_GIVE_UP = 16, RHINO_CHARGE_TICKS = 160;

    /** Charge a vue : a moins de 8 blocs ET visible (ligne de vue), pas pendant le calme d'apres un coup. */
    public static boolean rhinoCharges(double distance, boolean seen, int calm) {
        return seen && calm <= 0 && distance < RHINO_SIGHT;
    }

    // ------------------------------------------------------------------ elephant
    public static final int CALF_GUARD = 5, HERD_DEFEND = 24, ELEPHANT_RAGE = 300, ELEPHANT_GIVE_UP = 28;

    /** Le troupeau defend-il ? Petit approche de trop pres, petit ou adulte attaque. */
    public static boolean elephantDefends(double distanceToCalf, boolean calfHurt, boolean adultHurt) {
        return calfHurt || adultHurt || distanceToCalf < CALF_GUARD;
    }

    // ------------------------------------------------------------------ singe
    public static final int FORAGE_RANGE = 12, STEAL_RANGE = 3, STEAL_COOLDOWN_MIN = 2400, STEAL_COOLDOWN_MAX = 4800;
    public static final int EAT_TICKS = 100, PEEL_END = 35, EAT_END = 75, THROW_AT = 82, CANOPY_FIND = 4;

    /** Le singe vole-t-il ? reglage actif, le joueur tient une BANANE (rien d'autre), le singe a les mains vides, pas de delai en cours. */
    public static boolean monkeySteals(boolean theftEnabled, boolean holdsBanana, boolean carrying, int cooldown, double distance) {
        return theftEnabled && holdsBanana && !carrying && cooldown <= 0 && distance <= STEAL_RANGE;
    }

    /** Etape du repas a {@code t} ticks : 0 epluche, 1 mange, 2 jette la peau, 3 fini. */
    public static int eatStage(int t) {
        return t < PEEL_END ? 0 : t < EAT_END ? 1 : t < EAT_TICKS - 8 ? 2 : 3;
    }

    // ------------------------------------------------------------------ tigre
    public static final int TIGER_SIGHT = 24, POUNCE_RANGE = 5, POUNCE_TICKS = 16, POUNCE_COOLDOWN = 60, TIGER_FEAR_RANGE = 6;
    public static final double STALK_SPEED = 0.55, POUNCE_MIN = 2.0;

    /** Le tigre chasse-t-il ce joueur ? a vue, a 24 blocs, sans peur du feu. */
    public static boolean tigerHunts(double distance, boolean seen, boolean afraid) {
        return seen && !afraid && distance < TIGER_SIGHT;
    }

    /** Pas de la chasse du tigre : 0 affut (tapi, lent), 1 bond, 2 corps a corps. */
    public static int tigerStep(double distance, boolean onGround, int pounceCooldown) {
        if (distance > POUNCE_RANGE) {
            return 0;
        }
        return onGround && pounceCooldown <= 0 && distance >= POUNCE_MIN ? 1 : 2;
    }

    // ------------------------------------------------------------------ variantes
    /** Girafe : masai sur les plateaux une fois sur deux, reticulee sinon (0 reticulee, 1 masai). */
    public static int giraffeCoat(boolean plateau, double roll) {
        return plateau && roll < 0.5 || !plateau && roll < 0.2 ? 1 : 0;
    }

    /** Rhinoceros : blanc 70 %, noir 30 %. Elephant : gris 60 %, poudre de terre rouge 40 %. Singe : brun 70 %, dore 30 %. */
    public static int coat(int kind, double roll) {
        return switch (kind) {
            case RHINO, MONKEY -> roll < 0.7 ? 0 : 1;
            case ELEPHANT -> roll < 0.6 ? 0 : 1;
            case TIGER -> roll < 0.05 ? 1 : 0;
            default -> 0;
        };
    }
}
