package fr.pioneers.world.wild;

/**
 * 1.0.5-beta (lot 9, specification « Le monde vivant » §4 « Oceans et mers » et « Cotes et banquise ») : REGLES PURES de la mer et des
 * cotes (aucune classe du jeu : le banc du rendu, les GameTests et le controle les lisent aussi).
 * <ul>
 *     <li>cetaces : remontent RESPIRER quand leur souffle passe sous {@link #SURFACE_AT} ({@link #mustSurface}) ; en surface, ils
 *     SOUFFLENT un jet d'embruns ({@link #blows}) puis replongent ; l'orque chasse phoques, otaries, manchots, pingouins et requins
 *     ({@link #orcaPrey}), une proie toutes les {@link #ORCA_FED} ticks au plus ;</li>
 *     <li>requins : blanc et tigre attaquent le nageur a vue ({@link #sharkAttacks}) ; marteau et gris seulement un joueur qui SAIGNE
 *     ({@link #bleeding} : sous la moitie de sa vie, ou blesse depuis moins de {@link #BLEED_TICKS} ticks) ; le requin-baleine jamais ;
 *     ils chassent de temps en temps les poissons ({@link #huntsFish} : jamais dans un claim, jamais les derniers d'un coin) ;</li>
 *     <li>phoque, otarie, manchot, pingouin : PLONGENT a l'approche d'un joueur ({@link #dives}) ; se prelassent sur la cote ;</li>
 *     <li>manchot, pingouin : plongent PECHER ({@link #FISH_MIN} a {@link #FISH_MAX} ticks entre deux peches), rapportent le poisson au bec
 *     puis le mangent ({@link #EAT_TICKS}) ;</li>
 *     <li>mouette : vole UN aliment de la barre d'action d'un joueur proche, jamais l'objet en main ni l'armure ({@link #stealSlot}, reglage
 *     serveur {@code fauna.seagullTheft}, actif par defaut : decision 14) ;</li>
 *     <li>apparition : eau libre et profonde, jamais en riviere, au plus {@link #SEA_CAP} grands animaux marins dans {@link #SEA_ZONE}
 *     blocs (meme mesure que les poissons du mod : {@code PiFish.ZONE}, etendue d'eau {@code PiFish.BODY_SCAN}).</li>
 * </ul>
 */
public final class Wild9Rules {

    private Wild9Rules() {
    }

    /** Sortes (a la suite des lots 6 a 8). */
    public static final int BLUE_WHALE = 17, HUMPBACK = 18, ORCA = 19, SPERM_WHALE = 20, GREAT_WHITE = 21, HAMMERHEAD = 22, TIGER_SHARK = 23,
          WHALE_SHARK = 24, REEF_SHARK = 25, MANTA = 26, SEAL = 27, SEA_LION = 28, SEAGULL = 29, PENGUIN = 30, RAZORBILL = 31;
    /** Actions en plus de celles des lots 6 a 8 : nage, souffle en surface, plongee, morsure. */
    public static final int SWIM = 15, BLOW = 16, DIVE = 17, BITE = 18;

    public static boolean cetacean(int kind) {
        return kind >= BLUE_WHALE && kind <= SPERM_WHALE;
    }

    public static boolean shark(int kind) {
        return kind >= GREAT_WHITE && kind <= REEF_SHARK;
    }

    public static boolean pinniped(int kind) {
        return kind == SEAL || kind == SEA_LION;
    }

    public static boolean auk(int kind) {
        return kind == PENGUIN || kind == RAZORBILL;
    }

    /** Resistance au recul : les geants ne reculent pas sous les coups. */
    public static double knockbackResistance(int kind) {
        return switch (kind) {
            case BLUE_WHALE, HUMPBACK, SPERM_WHALE, WHALE_SHARK -> 1.0;
            case ORCA, MANTA -> 0.7;
            case GREAT_WHITE, TIGER_SHARK, HAMMERHEAD -> 0.5;
            default -> 0.0;
        };
    }

    /** Vitesse de virage (degres par tick) : les geants tournent lentement. */
    public static float turnRate(float length) {
        return Math.max(2.5F, 14F - length * 0.8F);
    }

    // ------------------------------------------------------------------ nage (1.0.5-beta lot 13)
    /*
     * 1.0.5-beta (lot 13, retour de Guillaume : « l'IA des baleines n'est pas terrible : souvent immobiles, deplacement bizarre ») : pilotage
     * PUR, partage par la commande de nage (MarineAnimal.SwimControl) et le banc hors jeu (tools/wild/SwimBench.java). Avant : cap tourne
     * a pleine vitesse vers la cible a chaque tick (orbites autour d'un point vise trop pres, rayon de 3 blocs pour une baleine de 14),
     * tangage jusqu'a 50 degres recalcule d'un coup (tremblement pres d'un point a la verticale), arret net a 0,8 bloc de chaque point.
     * Maintenant : vitesse de virage LISSEE (acceleration angulaire bornee), poussee reduite quand la cible est de cote ou derriere
     * (virage plus serre au lieu d'une orbite), tangage borne a 35 degres et calcule sur une distance au moins egale au corps, arrivee a
     * une fraction de la longueur (le but de croisiere enchaine alors le point suivant sans s'arreter).
     */

    /** Tangage maximal (degres) ; part de la vitesse de virage gagnee par tick ; gain du cap. */
    public static final float MAX_PITCH = 35F, TURN_ACCEL = 0.15F, YAW_GAIN = 0.08F;
    /** Poussee minimale (cible derriere) ; rayon d'arrivee = cette part de la longueur (au moins 0,8 bloc). */
    public static final float MIN_THRUST = 0.35F, ARRIVE = 0.35F;

    /**
     * Vitesse de virage maximale (degres par tick) d'une bete de longueur {@code length} qui croise a {@code cruise} blocs par tick : rayon de
     * virage d'environ 0,6 corps, borne entre 1,2 et la vitesse de virage de l'espece ({@link #turnRate}).
     */
    public static float maxTurn(float length, double cruise) {
        double deg = Math.toDegrees(cruise / Math.max(0.5, 0.6 * length));
        return (float) Math.max(1.2, Math.min(turnRate(length), deg));
    }

    /** Vitesse de croisiere (blocs par tick) : la poussee par tick sur un frein de l'eau de 10 % par tick. */
    public static double cruise(double accel) {
        return accel / 0.1;
    }

    /** Rayon d'arrivee sur un point vise (blocs). */
    public static double arrive(float length) {
        return Math.max(0.8, ARRIVE * length);
    }

    /**
     * 1.0.5-beta (lot 28, retour 38 : « le requin blanc a une animation saccadee quand il nage ») : rayon de freinage (blocs) : la poussee
     * baisse sous {@code SLOW} rayons d'arrivee. La croisiere choisit son point SUIVANT des qu'elle y entre ({@link #repick}) : avant, a 2
     * rayons d'arrivee, deja dans la zone de freinage (3 rayons) : la vitesse retombait a 67 % a chaque point de passage puis repartait (un
     * a-coup toutes les 1 a 3 s, bien visible sur le grand blanc).
     */
    public static final float SLOW = 3F;

    public static double slowRadius(float length) {
        return arrive(length) * SLOW;
    }

    /** Distance (blocs) a laquelle la croisiere enchaine le point suivant : juste avant la zone de freinage. */
    public static double repick(float length) {
        return slowRadius(length) + 0.5;
    }

    /**
     * PUR (lot 28) : POURSUITE d'une proie : vecteur vers la proie repousse, dans la meme direction, juste au-dela de la zone de freinage
     * (rien ne change plus loin) : ni arret ni creux de poussee pres d'elle.
     */
    public static double[] chaseAim(double dx, double dy, double dz, float length) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz), s = slowRadius(length) + 1.0;
        if (d >= s || d < 1E-4) {
            return new double[]{dx, dy, dz};
        }
        double k = s / d;
        return new double[]{dx * k, dy * k, dz * k};
    }

    /** Variation maximale de la poussee par tick (lot 28) : la bete n'alterne plus pleine poussee et poussee minimale en passant sa proie. */
    public static final float THRUST_STEP = 0.08F;

    /** PUR (lot 28) : poussee du tick, a partir de celle du tick precedent ({@code prev} &lt; 0 : premiere fois) et de celle voulue. */
    public static float easeThrust(float prev, float want) {
        return prev < 0F ? want : prev + clamp(want - prev, -THRUST_STEP, THRUST_STEP);
    }

    /** Poussee pleine jusqu'a {@link #ALIGN_FULL} degres d'ecart de cap, minimale au-dela de {@link #ALIGN_NONE} (lot 28 : avant, cosinus). */
    public static final float ALIGN_FULL = 45F, ALIGN_NONE = 135F;

    /** PUR : part de poussee selon l'ecart de cap (degres) : 1 jusqu'a 45, decroissance douce (lissee) jusqu'a {@link #MIN_THRUST} a 135. */
    public static float alignThrust(float errDeg) {
        float e = Math.abs(errDeg);
        float t = clamp((e - ALIGN_FULL) / (ALIGN_NONE - ALIGN_FULL), 0F, 1F);
        t = t * t * (3F - 2F * t);
        return MIN_THRUST + (1F - MIN_THRUST) * (1F - t);
    }

    /**
     * PUR : un tick de pilotage. Entree : cap {@code yaw} (degres, convention de Minecraft : 0 = +Z), tangage {@code pitch} (positif = pique),
     * vitesse de virage courante {@code yawRate}, vecteur vers la cible, longueur, virage maximal. Sortie : {cap, tangage, vitesse de virage,
     * poussee 0..1}.
     */
    public static float[] steer(float yaw, float pitch, float yawRate, double dx, double dy, double dz, float length, float maxTurn) {
        float want = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float err = wrap(want - yaw);
        float targetRate = clamp(err * YAW_GAIN * Math.max(1F, maxTurn), -maxTurn, maxTurn);
        float step = Math.max(0.05F, maxTurn * TURN_ACCEL);
        float rate = yawRate + clamp(targetRate - yawRate, -step, step);
        float nyaw = yaw + rate;
        double h = Math.sqrt(dx * dx + dz * dz);
        float wantPitch = clamp((float) -Math.toDegrees(Math.atan2(dy, Math.max(h, 0.6 * length + 2.0))), -MAX_PITCH, MAX_PITCH);
        float pstep = Math.max(0.6F, maxTurn * 0.5F);
        float npitch = pitch + clamp(wantPitch - pitch, -pstep, pstep);
        //1.0.5-beta (lot 28) : plateau de poussee (avant : cosinus de l'ecart, 80 % des 46 degres : creux a chaque nouveau point)
        float thrust = alignThrust(err);
        double d = Math.sqrt(h * h + dy * dy);
        double slow = slowRadius(length);
        if (d < slow) {
            thrust *= (float) Math.max(0.5, d / slow);
        }
        return new float[]{nyaw, npitch, rate, thrust};
    }

    /**
     * PUR : point de croisiere candidat (essai {@code attempt} : les 6 premiers devant, +- 46 degres, puis tout autour). Etape longue (2,2
     * corps, 10 blocs au moins) pour des trajectoires amples ; profondeur tiree dans la bande d'eau [lo, hi] (jamais collee au fond ni a la
     * surface) ; apres un souffle ({@code dive}), dans le quart bas de la bande. {@code rnd} : tirages 0..1. Renvoie {x, y, z}.
     */
    public static double[] waypoint(java.util.function.DoubleSupplier rnd, double x, double y, double z, float yawDeg, float length, double lo,
          double hi, boolean dive, int attempt) {
        double yaw = Math.toRadians(yawDeg + 90F) + (rnd.getAsDouble() - 0.5) * (attempt < 6 ? 1.6 : 6.28);
        double base = Math.max(10, length * 2.2);
        double dist = base * (0.6 + rnd.getAsDouble());
        double ty;
        if (hi <= lo) {
            ty = lo;
        } else if (dive) {
            ty = lo + (hi - lo) * 0.25 * rnd.getAsDouble();
        } else {
            //pas plus de 6 blocs (ou un corps) de denivele par etape : remontees et plongees douces
            double band = Math.max(6, length);
            double low = Math.max(lo, y - band), high = Math.min(hi, y + band);
            ty = low >= high ? Math.max(lo, Math.min(hi, y)) : low + (high - low) * (0.15 + 0.7 * rnd.getAsDouble());
        }
        return new double[]{x + Math.cos(yaw) * dist, ty, z + Math.sin(yaw) * dist};
    }

    static float wrap(float deg) {
        float d = deg % 360F;
        if (d >= 180F) {
            d -= 360F;
        }
        if (d < -180F) {
            d += 360F;
        }
        return d;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ------------------------------------------------------------------ caudale des cetaces (1.0.5-beta lot 28)
    /**
     * 1.0.5-beta (lot 28, retour 39 : « il faut ralentir l'animation de la queue de baleine ») : un battement de caudale fait avancer la bete
     * de {@link #FLUKE_STRIDE} longueur de corps (grands cetaces : environ une longueur par battement) ; a l'arret, la caudale bat encore a
     * {@link #FLUKE_IDLE} de la frequence de croisiere. Avant : 0,13 a 0,16 radian par tick quelle que soit la taille (un battement en 1,5 a
     * 1,9 s pour une baleine de 15 a 25 blocs) ; maintenant 3,6 s (baleine a bosse), 6,6 s (baleine bleue), 1,1 s (orque).
     */
    public static final float FLUKE_STRIDE = 0.7F, FLUKE_IDLE = 0.35F;

    /** PUR : vitesse de la phase de nage (radians par tick ; un battement = 2 pi) d'un cetace de longueur {@code length} a {@code speed} blocs par tick. */
    public static float flukeRate(float length, double speed, double cruise) {
        double v = Math.max(speed, FLUKE_IDLE * cruise);
        return (float) (2 * Math.PI * v / (FLUKE_STRIDE * Math.max(1F, length)));
    }

    /** PUR : periode d'un battement (ticks) a la vitesse de croisiere. */
    public static float flukePeriod(float length, double cruise) {
        return (float) (2 * Math.PI / flukeRate(length, cruise, cruise));
    }

    // ------------------------------------------------------------------ respiration
    /** Part du souffle sous laquelle un cetace (ou un phoque, un manchot) remonte respirer. */
    public static final double SURFACE_AT = 0.3;
    /** Hors de l'eau, un animal marin s'asseche : degats apres ce delai (branchies / mammiferes marins). */
    public static final int DRY_GILLS = 300, DRY_MAMMAL = 1200, DRY_PERIOD = 40;
    /** Souffle : action BLOW tenue en surface, puis plongee. */
    public static final int BLOW_TICKS = 50, DIVE_TICKS = 50;

    public static boolean mustSurface(int air, int maxAir) {
        return maxAir > 0 && air < maxAir * SURFACE_AT;
    }

    /** Une vraie respiration (souffle visible) : l'air etait entame d'au moins 10 %. */
    public static boolean blows(int airBefore, int maxAir) {
        return maxAir > 0 && airBefore < maxAir * 0.9;
    }

    // ------------------------------------------------------------------ requins
    public static final int BLEED_TICKS = 200, SHARK_GIVE_UP = 32, FISH_RANGE = 16, FISH_MIN_NEAR = 3, FISH_HUNT_MIN = 1200,
          FISH_HUNT_MAX = 3600, BITE_TICKS = 12;

    /** Un joueur qui SAIGNE : sous la moitie de sa vie, ou blesse depuis moins de {@link #BLEED_TICKS} ticks. */
    public static boolean bleeding(float health, float maxHealth, long ticksSinceHurt) {
        return health < maxHealth * 0.5F || ticksSinceHurt >= 0 && ticksSinceHurt < BLEED_TICKS;
    }

    /**
     * Le requin attaque-t-il ce joueur ? Il faut que le joueur soit DANS l'eau et a portee ({@code range} : charge de l'espece) ; blanc et
     * tigre a vue, marteau et gris seulement s'il saigne, requin-baleine jamais.
     */
    public static boolean sharkAttacks(int kind, int range, boolean inWater, boolean bleeding, double distance) {
        if (!inWater || range <= 0 || distance > range) {
            return false;
        }
        return switch (kind) {
            case GREAT_WHITE, TIGER_SHARK -> true;
            case HAMMERHEAD, REEF_SHARK -> bleeding;
            default -> false;
        };
    }

    /** Chasse aux poissons : jamais dans un claim (fermes, bassins), jamais s'il reste moins de {@link #FISH_MIN_NEAR} poissons. */
    public static boolean huntsFish(int kind, boolean inClaim, int fishNear, int cooldown) {
        return kind != WHALE_SHARK && !inClaim && cooldown <= 0 && fishNear >= FISH_MIN_NEAR;
    }

    // ------------------------------------------------------------------ orque
    public static final int ORCA_FED = 6000, ORCA_HUNT_RANGE = 24;

    /** Proies de l'orque : phoques, otaries, manchots, pingouins (tag {@code pioneers:orca_prey}) et requins (sortes). */
    public static boolean orcaPrey(boolean taggedPrey, int preyKind, int fedLeft) {
        return fedLeft <= 0 && (taggedPrey || shark(preyKind));
    }

    // ------------------------------------------------------------------ phoques, manchots
    /** Plonge (ou fuit vers l'eau) : joueur a moins de {@code flee} blocs (moitie s'il approche accroupi). */
    public static boolean dives(int flee, double distance, boolean crouching) {
        return flee > 0 && distance < (crouching ? flee * 0.5 : flee);
    }

    public static final int BASK_MIN = 600, BASK_MAX = 2400, SHORE_RANGE = 16, WATER_RANGE = 14;
    public static final int FISH_MIN = 1200, FISH_MAX = 3600, CATCH_TICKS = 80, EAT_TICKS = 60, CARRY_TICKS = 400;

    // ------------------------------------------------------------------ mouette
    public static final int THEFT_RANGE = 12, THEFT_REACH = 3, THEFT_MIN = 2400, THEFT_MAX = 6000, HOTBAR = 9;

    /**
     * Case de la barre d'action que la mouette vole : un ALIMENT ({@code food[i]}), jamais la case tenue en main ({@code selected}) ; tirage
     * parmi les cases permises ({@code roll} 0..1) ; -1 si aucune. L'armure et la main gauche ne sont jamais dans la barre d'action.
     */
    public static int stealSlot(boolean[] food, int selected, double roll) {
        int n = 0;
        for (int i = 0; i < Math.min(HOTBAR, food.length); i++) {
            if (food[i] && i != selected) {
                n++;
            }
        }
        if (n == 0) {
            return -1;
        }
        int pick = Math.min(n - 1, (int) Math.floor(roll * n));
        for (int i = 0; i < Math.min(HOTBAR, food.length); i++) {
            if (food[i] && i != selected && pick-- == 0) {
                return i;
            }
        }
        return -1;
    }

    /** La mouette vole-t-elle ? reglage actif, rien dans le bec, delai ecoule, joueur a portee de bec. */
    public static boolean seagullSteals(boolean enabled, boolean carrying, int cooldown, double distance) {
        return enabled && !carrying && cooldown <= 0 && distance <= THEFT_REACH;
    }

    // ------------------------------------------------------------------ apparition
    /**
     * Au plus {@link #SEA_CAP} grands animaux marins du lot dans {@link #SEA_ZONE} blocs (= 2 x PiFish.ZONE). 1.0.5-beta (lot 21, « groupes
     * marins trop gros et trop frequents ») : 8 -> 5.
     */
    public static final int SEA_CAP = 5, SEA_ZONE = 32;
}
