package fr.pioneers.tech.bronze.steam;

/**
 * 1.0.2-beta (lot 3, notes de Guillaume, Age de bronze : « supprimer la notion de pression ; la chaudiere explose si elle n'a plus d'eau ;
 * les machines consomment de la vapeur ») : SEULE SOURCE des chiffres de la vapeur, sans Minecraft (lue par le jeu, l'interface, le guide et
 * le banc {@code tools/bronze/SteamBench.java}).
 * <ul>
 *     <li>La vapeur est une QUANTITE (mB de gaz {@code pioneers:steam}) : la chaudiere en produit tant que sa reserve n'est pas pleine ;
 *     chaque machine a une reserve et prend un debit FIXE quand elle travaille (au moins ce debit dans sa reserve, sinon « pas de vapeur »).</li>
 *     <li>L'eau bout a {@link #BOIL_C} : 2,26 kJ par mB d'eau, 1 mB d'eau = {@link #STEAM_PER_WATER} mB de vapeur ; tant qu'il reste de
 *     l'eau, la chaudiere ne depasse pas l'ebullition (le surplus de chaleur d'une reserve pleine se perd).</li>
 *     <li>Seul danger : chauffer A SEC (eau sous {@link #DRY_WATER}) : usure, puis explosion (config {@code bronze.boilerExplosion}).</li>
 *     <li>Deux reserves collees s'equilibrent par leur remplissage (de la plus pleine a la plus vide), au plus le debit de la face ;
 *     la chaudiere est une source : on y puise, on ne la remplit jamais.</li>
 * </ul>
 */
public final class SteamRules {

    /** Ebullition (degres C) : toujours 100, plus de pression. */
    public static final double BOIL_C = 100;
    /** 1 mB d'eau vaporisee = 8 mB de vapeur. */
    public static final double STEAM_PER_WATER = 8;
    /** Chaleur latente (kJ par mB d'eau), chaleur du feu (kJ par tick, x tirage), chaleur massique de l'eau et d'un corps (kJ/K). */
    public static final double LATENT = 2.26, HEAT = 6, WATER_C = 0.0042, SHELL_C = 8;
    /** Reserve de vapeur des pieces de chaudiere (mB) : corps de cuivre, corps d'acier, dome. */
    public static final int COPPER_SHELL_STEAM = 12_000, STEEL_SHELL_STEAM = 32_000, DOME_STEAM = 8_000;
    /** Chaudiere « sans eau » (mB) ; alarme d'eau basse (part de la contenance) ; usure qui la fait sauter. */
    public static final double DRY_WATER = 1, LOW_WATER = 0.15, BURST_DAMAGE = 100;
    /** Debit d'echange entre deux reserves collees (mB par tick et par face, par defaut). */
    public static final double FACE_FLOW = 200;

    // ------------------------------------------------------------------ consommations (debit fixe quand la machine travaille)
    /** Machines de traitement (compresseur, extracteur, broyeur, fondeur, four, trefileuse, laminoir, scie) : mB/t ; reserve. */
    public static final double PROCESS_USE = 3, PROCESS_RESERVE = 3_200;
    /** Ateliers a vapeur (marteau-pilon, presse) : un coup dure {@link #STROKE_TICKS} ticks ; vapeur par coup dans la fiche ; reserve. */
    public static final int STROKE_TICKS = 40;
    public static final double WORKSHOP_RESERVE = 3_200;
    /** Soufflerie : souffle de 3 soufflets pour 17 mB/t ; reserve. */
    public static final double BLOWER_AIR = 3, BLOWER_USE = 17, BLOWER_RESERVE = 2_400;
    /** Machine a vapeur : couple de demarrage, vitesse a vide, vapeur par tick et par tr/min, prise minimale (10 tr/min) ; reserve du coffre. */
    public static final double ENGINE_TORQUE = 12_000, ENGINE_FREE = 120, ENGINE_USE = 0.48, ENGINE_MIN_RPM = 10, ENGINE_RESERVE = 4_000;
    // 1.0.4-beta (lot 27) : injecteur retire (ses constantes : 1 mB de vapeur pour 12 mB d'eau, 40 mB/t, reserve 800 mB)
    /** Foreuse a vapeur : vapeur par tick, ticks par minerai ; reserve. */
    public static final double DRILL_USE = 24, DRILL_RESERVE = 8_000;
    public static final int DRILL_TICKS = 60;

    /** 1.0.3-beta (lot 14) : une coupure de surete vaut pour ce tick et les {@code CUT_HOLD} suivants (la surete la redit chaque tick). */
    public static final long CUT_HOLD = 2;

    private SteamRules() {
    }

    /**
     * 1.0.3-beta (lot 14) : foyer coupe par une surete ? {@code cutoffAt} = dernier tick de coupure, {@link Long#MIN_VALUE} = jamais. Ecrit SANS
     * soustraction qui deborde (le bug du lot 7 : {@code now - Long.MIN_VALUE} negatif rendait toute chaudiere coupee).
     */
    public static boolean cutActive(long now, long cutoffAt) {
        return cutoffAt != Long.MIN_VALUE && cutoffAt <= now && cutoffAt >= now - CUT_HOLD;
    }

    /** Debit (mB, positif de a vers b) entre deux reserves collees : vers la moins remplie, sans depasser l'equilibre ni {@code flow}. */
    public static double exchange(double steamA, double capA, double steamB, double capB, double flow) {
        double fa = steamA / Math.max(1, capA), fb = steamB / Math.max(1, capB), d = fa - fb;
        if (Math.abs(d) < 1e-6) {
            return 0;
        }
        double equal = Math.abs(d) / (1 / Math.max(1, capA) + 1 / Math.max(1, capB));
        return Math.signum(d) * Math.min(equal * 0.5, flow);
    }

    /**
     * Un tick de la chaudiere (sans Minecraft) : {@code state} = {temperature C, eau mB, vapeur mB} ; {@code heat} kJ apportes ; renvoie la
     * vapeur produite (mB). La temperature monte, l'eau bout a 100 C tant que la reserve {@code reserve} a de la place ; pleine, la chaleur en
     * trop s'en va (reste a 100 C) ; sans eau, la temperature monte sans limite (danger : {@link #dry}).
     */
    public static double boil(double[] state, double heat, int shells, double reserve) {
        double water = state[1];
        double c = water * WATER_C + shells * SHELL_C + 2;
        double loss = (state[0] - 20) * 0.0015 * Math.max(1, shells);
        state[0] += (heat - loss) / c;
        double made = 0;
        if (water > 0 && state[0] > BOIL_C) {
            double excess = (state[0] - BOIL_C) * c;
            double room = Math.max(0, reserve - state[2]) / STEAM_PER_WATER;
            double evaporated = Math.min(water, Math.min(room, excess / LATENT));
            state[1] = water - evaporated;
            made = evaporated * STEAM_PER_WATER;
            state[2] += made;
            if (state[1] > 0) {
                state[0] = BOIL_C;                                  //Il reste de l'eau : elle tient la chaudiere a l'ebullition
            } else {
                state[0] = BOIL_C + (excess - evaporated * LATENT) / c;
            }
        }
        state[0] = Math.max(20, state[0]);
        return made;
    }

    /** Chaudiere a sec : formee, feu allume, (presque) plus d'eau. */
    public static boolean dry(double water, boolean fire) {
        return fire && water < DRY_WATER;
    }

    /** Usure d'un tick a sec (100 = explosion) : le feu en ronge d'autant plus qu'il est vif ({@code heat} kJ/t). */
    public static double wear(double heat) {
        return heat / 6;
    }

    /** Vapeur que prend la machine a vapeur ce tick (mB) a la vitesse {@code rpm}. */
    public static double engineNeed(double rpm) {
        return ENGINE_USE * Math.max(ENGINE_MIN_RPM, Math.abs(rpm));
    }

    /** Puissance mecanique de la machine a vapeur (W) a la vitesse {@code rpm} (couple tau0 (1 - w / w libre)). */
    public static double enginePower(double rpm) {
        double w = Math.abs(rpm);
        return ENGINE_TORQUE * Math.max(0, 1 - w / ENGINE_FREE) * w * 2 * Math.PI / 60;
    }

    // ------------------------------------------------------------------ 1.0.3-beta (lot 14) : chiffres de la chaudiere de l'age du bronze
    /** Tirage : sans cheminee x0,4, +0,15 par bloc de la plus haute cheminee, x1,6 au plus (8 blocs). */
    public static final double DRAFT_BASE = 0.4, DRAFT_PER_CHIMNEY = 0.15, DRAFT_MAX = 1.6;

    /** Tirage d'une chaudiere dont la plus haute cheminee a {@code chimney} blocs (le feu brule et chauffe d'autant). */
    public static double draft(int chimney) {
        return Math.min(DRAFT_MAX, DRAFT_BASE + DRAFT_PER_CHIMNEY * Math.max(0, chimney));
    }

    /** Chaleur du feu (kJ par tick) pour un tirage et un bonus de combustible (%). */
    public static double fireHeat(double draft, double bonus) {
        return HEAT * draft * (1 + bonus / 100);
    }

    /** Pertes de la chaudiere a l'ebullition (kJ par tick) : un corps de plus = un peu plus de surface qui rayonne. */
    public static double boilingLoss(int shells) {
        return (BOIL_C - 20) * 0.0015 * Math.max(1, shells);
    }

    /** Vapeur produite en regime (mB/t, a 100 C, reserve pas pleine) : (feu - pertes) / chaleur latente x 8. */
    public static double boilerNet(double draft, double bonus, int shells) {
        return Math.max(0, fireHeat(draft, bonus) - boilingLoss(shells)) / LATENT * STEAM_PER_WATER;
    }

    /**
     * Vapeur d'UN objet de combustible (mB, en regime, sans les pertes) : depuis le lot 14 le foyer brule a la cadence exacte du tirage
     * ({@code Hearth.tickExact}) : la chaleur d'un objet ne depend plus de la cheminee, seulement de sa duree et de son bonus.
     */
    public static double steamPerFuel(int burnTicks, double bonus) {
        return burnTicks * HEAT * (1 + bonus / 100) / LATENT * STEAM_PER_WATER;
    }

    /** Temps de chauffe (ticks) de 20 C a l'ebullition, par le vrai {@link #boil} ; {@code water} mB, {@code heat} kJ/t ; -1 si jamais. */
    public static int heatUpTicks(int shells, double water, double heat) {
        double[] st = {20, water, 0};
        for (int t = 0; t < 200_000; t++) {
            if (boil(st, heat, shells, Double.MAX_VALUE) > 0 || st[0] >= BOIL_C) {
                return t;
            }
        }
        return -1;
    }

    /** Vapeur produite au plus par la chaudiere (mB/t) pour un tirage et un bonus de foyer (%). */
    public static double boilerMax(double draft, double bonus) {
        return HEAT * draft * (1 + bonus / 100) / LATENT * STEAM_PER_WATER;
    }
}
