package fr.pioneers.wild;

/**
 * 1.0.5-beta (lot 7, specification « Le monde vivant » §4, §7, §9, §11 n° 9, 13, 14) : REGLES PURES des predateurs et de la petite faune
 * (aucune classe du jeu : le banc du rendu, les GameTests et le controle les lisent aussi).
 * <ul>
 *     <li>vrai loup : meutes de 3 a 5 ; la NUIT il s'en prend au betail a {@link #WOLF_HUNT_RANGE} blocs et au joueur ISOLE (aucun autre joueur
 *     a {@link #ISOLATED_RADIUS} blocs) a la meme distance ; le JOUR, seulement a un joueur isole trop proche ({@link #WOLF_DAY_RANGE}) ; il
 *     CRAINT LE FEU : un joueur qui tient une torche ou une lanterne a {@link #FEAR_TORCH_RANGE} blocs, un feu ou un feu de camp allume a
 *     {@link #FEAR_FIRE_RANGE} blocs le font fuir ({@link #wolfAttacksPlayer}, {@link #isolated}) ;</li>
 *     <li>ours : charge qui s'approche a moins de 6 blocs, l'ourse defend ses petits a {@link #CUB_GUARD} blocs ; cherche les ruches du mod a
 *     {@link #HIVE_RANGE} blocs ; dans un claim il menace sans casser ({@link #bearBreaks}) ; actif en hiver ;</li>
 *     <li>rat : vole un objet dans un coffre ou un tonneau ouvert ({@link #ratMayRob}) toutes les {@link #THEFT_MIN}..{@link #THEFT_MAX} ticks,
 *     jamais dans une caisse d'entrepot ni une machine ; pullule lentement pres des reserves de nourriture ({@link #ratBirth}) ;</li>
 *     <li>serpent : mord a moins de {@link #SNAKE_BITE} blocs (poison leger), chasse les rats ;</li>
 *     <li>piege a rat, nichoir a oiseaux ; variantes (robes) selon le biome ({@link #wolfCoat}, {@link #snakeCoat}...).</li>
 * </ul>
 */
public final class Wild7Rules {

    private Wild7Rules() {
    }

    /** Sortes (a la suite du gibier du lot 6 : {@link WildRules#BOAR} .. {@link WildRules#BISON}). */
    public static final int WOLF = 5, BEAR = 6, WOODPECKER = 7, RAT = 8, PIGEON = 9, SNAKE = 10;
    /** Actions en plus de celles du gibier : en vol, pose (accroche a un tronc, juche sur un toit). */
    public static final int FLY = 7, PERCH = 8;

    // ------------------------------------------------------------------ loup
    public static final int WOLF_HUNT_RANGE = 24, WOLF_DAY_RANGE = 6, ISOLATED_RADIUS = 24, PACK_RADIUS = 16;
    public static final int FEAR_TORCH_RANGE = 10, FEAR_FIRE_RANGE = 5, FEAR_TICKS = 100;

    /** Un joueur est isole s'il n'a aucun autre joueur (vivant, pas spectateur) a {@link #ISOLATED_RADIUS} blocs. */
    public static boolean isolated(int othersWithinRadius) {
        return othersWithinRadius == 0;
    }

    /** Le loup s'en prend-il a ce joueur ? (la nuit : isole a 24 blocs ; le jour : isole et a moins de 6 blocs ; jamais s'il a peur) */
    public static boolean wolfAttacksPlayer(boolean night, double distance, boolean isolated, boolean afraid) {
        if (afraid || !isolated) {
            return false;
        }
        return distance < (night ? WOLF_HUNT_RANGE : WOLF_DAY_RANGE);
    }

    /** Le loup chasse-t-il le betail ? (la nuit seulement, sans peur) */
    public static boolean wolfHuntsLivestock(boolean night, double distance, boolean afraid) {
        return night && !afraid && distance < WOLF_HUNT_RANGE;
    }

    /** Robe du loup : blanc dans la neige, noir une fois sur 8 ailleurs, gris sinon (0 gris, 1 blanc, 2 noir). */
    public static int wolfCoat(boolean snowy, double roll) {
        return snowy ? 1 : roll < 0.125 ? 2 : 0;
    }

    // ------------------------------------------------------------------ ours
    public static final int BEAR_CHARGE = 6, CUB_GUARD = 12, HIVE_RANGE = 24, HIVE_COOLDOWN_MIN = 1200, HIVE_COOLDOWN_MAX = 2400;
    public static final int BEAR_RAGE_TICKS = 200, MENACE_TICKS = 60;

    /** L'ours casse la ruche (hors claim, si les degats des creatures sont permis) ; sinon il menace (se dresse, rugit) et s'en va. */
    public static boolean bearBreaks(boolean claimed, boolean mobGriefing) {
        return !claimed && mobGriefing;
    }

    /** L'ours charge : blesse, joueur a moins de 6 blocs, ou a moins de 12 blocs d'une ourse qui garde ses petits. */
    public static boolean bearCharges(double distance, boolean hurt, boolean guardingCub) {
        return hurt || distance < (guardingCub ? CUB_GUARD : BEAR_CHARGE);
    }

    /** Robe de l'ours : noir une fois sur 3 dans les forets de feuillus, brun sinon (0 brun, 1 noir). */
    public static int bearCoat(boolean broadleaf, double roll) {
        return broadleaf && roll < 0.34 ? 1 : 0;
    }

    // ------------------------------------------------------------------ rat
    public static final int THEFT_RANGE = 12, THEFT_MIN = 6000, THEFT_MAX = 12000, THEFT_REACH = 2;
    public static final int FOOD_RANGE = 8, RAT_LOCAL_MAX = 6, RAT_BREED_PERIOD = 1200;
    public static final double RAT_BREED_CHANCE = 0.08;

    /** Le rat peut-il voler dans ce contenant ? Coffre ou tonneau vanilla seulement, sans verrou, hors claim, vol autorise (reglage). */
    public static boolean ratMayRob(boolean theftEnabled, boolean chestOrBarrel, boolean locked, boolean claimed) {
        return theftEnabled && chestOrBarrel && !locked && !claimed;
    }

    /** Naissance d'un rat pres d'une reserve de nourriture : reserve a portee, moins de 6 rats autour, tirage sous 8 % (par minute). */
    public static boolean ratBirth(boolean foodNear, int ratsAround, double roll) {
        return foodNear && ratsAround < RAT_LOCAL_MAX && roll < RAT_BREED_CHANCE;
    }

    // ------------------------------------------------------------------ serpent
    public static final double SNAKE_BITE = 2.0;
    public static final int SNAKE_PREY_RANGE = 12, SNAKE_CALM = 60;

    /** Le serpent mord : un joueur a moins de 2 blocs, ou l'agresseur. */
    public static boolean snakeBites(double distance, boolean hurt) {
        return hurt || distance < SNAKE_BITE;
    }

    /** Robe du serpent selon la famille du biome : 0 couleuvre (plaines, forets), 1 vipere (desert), 2 verte (jungle), 3 annele (savane). */
    public static int snakeCoat(boolean dry, boolean jungle, boolean savanna) {
        return dry ? 1 : jungle ? 2 : savanna ? 3 : 0;
    }

    // ------------------------------------------------------------------ oiseaux
    public static final int PERCH_RANGE = 12, ROOF_RANGE = 16, PERCH_MIN = 200, PERCH_MAX = 600, DRUM_BURST = 14;

    /** Robe du pigeon (tirage 0..1) : bleu barre 50 %, ecaille 25 %, rouge 15 %, blanc 10 %. */
    public static int pigeonCoat(double roll) {
        return roll < 0.5 ? 0 : roll < 0.75 ? 1 : roll < 0.9 ? 2 : 3;
    }

    // ------------------------------------------------------------------ piege a rat, nichoir
    public static final int TRAP_LURE = 8, TRAP_PERIOD = 20;
    public static final double TRAP_CATCH = 1.0;
    /** Nichoir : une chance de plume par tick aleatoire (x2 si un oiseau est pres), au plus 3 plumes au sol dessous ; arrivee d'un oiseau. */
    public static final int FEATHER_ODDS = 8, FEATHER_MAX = 3, HOUSE_PERIOD = 2400, HOUSE_BIRDS = 2, HOUSE_RADIUS = 32;

    /** Le piege attrape-t-il ce rat ? (arme, rat a moins de 1 bloc) */
    public static boolean trapCatches(boolean armed, double distance) {
        return armed && distance < TRAP_CATCH;
    }

    /** Plume sous le nichoir a ce tick aleatoire ? ({@code roll} tire dans 0..FEATHER_ODDS-1 ; deux chances si un oiseau est pres) */
    public static boolean featherDrop(int roll, boolean birdNear, int feathersBelow) {
        return feathersBelow < FEATHER_MAX && roll >= 0 && roll < (birdNear ? 2 : 1);
    }
}
