package fr.pioneers.fauna;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0.5-beta (lot 4, specification « Le monde vivant » §3, §7, §8, decision 15) : REGLES PURES de la faune persistante et du repeuplement
 * par zone. Aucune classe du jeu : le jeu ({@link FaunaZones}, {@link FaunaSpawner}), le banc hors jeu {@code tools/fauna/FaunaBench.java}
 * et les GameTests passent par elles.
 * <ul>
 *     <li>zones de {@link #ZONE_SIZE} x {@link #ZONE_SIZE} blocs (8 x 8 troncons), cle = (x >> 7, z >> 7) empaquetee ;</li>
 *     <li>densite de depart d'un groupe : fixee au premier passage sur la zone, = max(compte observe, estimation du biome), bornee au
 *     plafond ({@link #start}) ;</li>
 *     <li>cible du moment = depart x facteur de saison (les oiseaux plus nombreux au printemps et en ete, §7 : {@link #SEASON_FACTOR}) ;</li>
 *     <li>un groupe passe sous {@link #THRESHOLD} (25 %) de sa cible : un petit groupe reapparait ({@link #plan}, {@link #groupSize}) ;
 *     zone d'un monde existant : au plus {@link #LEGACY_GROUPS_PER_PASS} groupe par passage (§8) ;</li>
 *     <li>IA allegee loin des joueurs ({@link #aiGate}) ;</li>
 *     <li>1.0.5-beta (lot 6) : REPRODUCTION SAUVAGE LENTE ({@link #breedRoom}, {@link #births}) : entre le seuil et la densite de depart,
 *     un petit nait de temps en temps pres d'un adulte, jamais au-dela de la densite de depart.</li>
 * </ul>
 */
public final class FaunaRules {

    private FaunaRules() {
    }

    /** Zone : 128 x 128 blocs (spec §3). */
    public static final int ZONE_SHIFT = 7, ZONE_SIZE = 1 << ZONE_SHIFT, ZONE_CHUNKS = ZONE_SIZE / 16, CHUNKS_PER_ZONE = ZONE_CHUNKS * ZONE_CHUNKS;
    /** Passage du repeuplement : toutes les 10 minutes de jeu (12 000 ticks ; reglage {@code fauna.periodTicks}). */
    public static final int PERIOD = 12_000;
    /** Decision 15 : repeuplement des qu'un groupe passe sous 25 % de sa densite de depart (reglage {@code fauna.threshold}). */
    public static final double THRESHOLD = 0.25;
    /** Distances (blocs) : apparition a plus de 64 blocs de tout joueur, jamais a moins de 48 d'un village ; IA allegee au-dela de 48. */
    public static final int PLAYER_DISTANCE = 64, VILLAGE_DISTANCE = 48, AI_DISTANCE = 48;
    /** IA allegee : les decisions (choix des buts) 4 fois moins souvent. */
    public static final int AI_SLOWDOWN = 4;
    /** Hors de la vue d'un joueur : hors du cone de 70 degres autour de son regard, ou masque par un bloc. */
    public static final double VIEW_HALF_ANGLE = 70;
    /** Zone d'un monde existant : au plus un groupe par passage (§8). */
    public static final int LEGACY_GROUPS_PER_PASS = 1;
    /** Une zone est « neuve » (peuplee a la generation) si au moins la moitie de ses troncons ont ete generes depuis la 1.0.5. */
    public static final int FRESH_CHUNKS = CHUNKS_PER_ZONE / 2;
    /** Essais de colonnes pour placer un groupe dans une zone. */
    public static final int SPAWN_TRIES = 16;
    /** Budgets d'un troncon a la generation (bandes par troncon, tirage geometrique comme le vanilla) : eau et grottes (la terre suit la
     *  probabilite de creatures du biome, 0,1 le plus souvent). 1.0.5-beta (lot 13, « trop peu de poissons ») : eau 0,12 -> 0,25. */
    public static final double WATER_CHANCE = 0.25, CAVE_CHANCE = 0.04;
    /** Part des bandes qui trouvent leur place (estimation de la densite d'une zone deja exploree). */
    public static final double SUCCESS_LAND = 0.75, SUCCESS_WATER = 0.6, SUCCESS_CAVE = 0.25;
    /** Corbeau : poids x4 dans le repeuplement des oiseaux d'une zone qui contient des champs (spec §3). */
    public static final double CROW_FIELD_BOOST = 4;
    /** Facteur de saison [groupe][printemps, ete, automne, hiver] (spec §7 : oiseaux plus nombreux au printemps et en ete). */
    public static final double[][] SEASON_FACTOR = {
          {1, 1, 1, 1},             // gibier
          {1, 1, 1, 1},             // predateurs
          {1.25, 1.25, 1, 0.6},     // oiseaux
          {1, 1, 1, 1},             // faune marine
          {1, 1, 1, 1}};            // poissons

    // ------------------------------------------------------------------ zones

    /** Cle de la zone du bloc (x, z). */
    public static long zoneKey(int blockX, int blockZ) {
        return pack(blockX >> ZONE_SHIFT, blockZ >> ZONE_SHIFT);
    }

    /** Cle de la zone du troncon (cx, cz). */
    public static long zoneOfChunk(int cx, int cz) {
        return pack(cx >> (ZONE_SHIFT - 4), cz >> (ZONE_SHIFT - 4));
    }

    public static long pack(int zx, int zz) {
        return (long) zx << 32 | zz & 0xFFFFFFFFL;
    }

    public static int zoneX(long key) {
        return (int) (key >> 32);
    }

    public static int zoneZ(long key) {
        return (int) key;
    }

    /** Premier bloc (x ou z) de la zone d'indice {@code z}. */
    public static int origin(int zoneIndex) {
        return zoneIndex << ZONE_SHIFT;
    }

    // ------------------------------------------------------------------ densites

    /** Densite de depart : max(observe, estimation arrondie), bornee a [0, plafond]. */
    public static int start(int observed, double estimate, int cap) {
        return Math.max(0, Math.min(cap, Math.max(observed, (int) Math.round(estimate))));
    }

    public static double seasonFactor(FaunaGroup g, int season) {
        return SEASON_FACTOR[g.ordinal()][Math.floorMod(season, 4)];
    }

    /** Cible du moment : depart x facteur de saison (au moins 1 si le depart n'est pas nul). */
    public static int target(int start, FaunaGroup g, int season) {
        if (start <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(start * seasonFactor(g, season)));
    }

    /** Sous le seuil : compte < seuil x cible (strictement). */
    public static boolean needs(int count, int target, double threshold) {
        return target > 0 && count < threshold * target;
    }

    /**
     * Groupes a repeupler a ce passage, du plus appauvri au moins appauvri (part du compte dans la cible) ; un groupe au plafond n'est
     * jamais repris ; zone d'un monde existant : le premier seulement.
     */
    public static List<FaunaGroup> plan(int[] counts, int[] starts, int[] caps, int season, boolean legacy, double threshold) {
        List<FaunaGroup> out = new ArrayList<>();
        for (FaunaGroup g : FaunaGroup.ALL) {
            int i = g.ordinal(), t = target(starts[i], g, season);
            if (needs(counts[i], t, threshold) && counts[i] < caps[i]) {
                out.add(g);
            }
        }
        out.sort((a, b) -> Double.compare(ratio(counts, starts, a, season), ratio(counts, starts, b, season)));
        if (legacy && out.size() > LEGACY_GROUPS_PER_PASS) {
            return new ArrayList<>(out.subList(0, LEGACY_GROUPS_PER_PASS));
        }
        return out;
    }

    private static double ratio(int[] counts, int[] starts, FaunaGroup g, int season) {
        int t = target(starts[g.ordinal()], g, season);
        return t <= 0 ? 1 : counts[g.ordinal()] / (double) t;
    }

    /**
     * Taille du petit groupe qui reapparait : tiree entre {@code min} et {@code max} ({@code u} dans [0, 1[), jamais au-dela du plafond
     * (cap - compte) ni de ce qui manque pour revenir a la cible (au moins 1 tant que le plafond le permet).
     */
    public static int groupSize(int min, int max, int count, int target, int cap, double u) {
        int lo = Math.max(1, min), hi = Math.max(lo, max);
        int n = lo + Math.min(hi - lo, (int) Math.floor(u * (hi - lo + 1)));
        n = Math.min(n, Math.max(1, target - count));
        return Math.max(0, Math.min(n, cap - count));
    }

    /** Tirage pondere : indice tel que la somme des poids precedents depasse u x total (u dans [0, 1[) ; -1 si tout est nul. */
    public static int pick(double[] weights, double u) {
        double total = 0;
        for (double w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return -1;
        }
        double r = u * total;
        int last = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            last = i;
            r -= weights[i];
            if (r < 0) {
                return i;
            }
        }
        return last;
    }

    /** Nombre moyen de bandes par troncon pour un budget p (tirage « tant que hasard < p », comme le vanilla) : p / (1 - p). */
    public static double expectedBands(double p) {
        double q = Math.max(0, Math.min(0.95, p));
        return q / (1 - q);
    }

    /** Betes attendues par troncon : bandes x part du poids x taille moyenne x part placee. */
    public static double expected(double p, double weight, double totalWeight, int min, int max, double success) {
        if (totalWeight <= 0 || weight <= 0) {
            return 0;
        }
        return expectedBands(p) * weight / totalWeight * (min + max) / 2.0 * success;
    }

    // ------------------------------------------------------------------ reproduction sauvage lente (1.0.5-beta, lot 6)

    /** Chance qu'un adulte d'un groupe fasse un petit a chaque passage (reglage {@code fauna.wildBreedChance}). */
    public static final double WILD_BREED_CHANCE = 0.12;
    /** Naissance a plus de 32 blocs de tout joueur et hors de sa vue (reglage {@code fauna.wildBreedDistance}). */
    public static final int WILD_BREED_DISTANCE = 32;
    /** Au plus 4 naissances par groupe et par passage dans une zone. */
    public static final int WILD_BREED_MAX = 4;

    /**
     * Place pour des naissances sauvages : un groupe ENTRE le seuil ({@code threshold} x cible, compris) et sa densite de depart peut
     * remonter jusqu'a min(depart, cible du moment, plafond) ; sous le seuil, c'est le repeuplement qui agit (0) ; a la densite de depart
     * ou au-dessus : 0.
     */
    public static int breedRoom(int count, int start, int target, int cap, double threshold) {
        int limit = Math.min(Math.min(start, target), cap);
        if (limit <= 0 || count >= limit || count < threshold * target) {
            return 0;
        }
        return limit - count;
    }

    /** Naissances de ce passage : chaque adulte a sa chance (tirages {@code u}), au plus {@code room} et {@link #WILD_BREED_MAX}. */
    public static int births(int adults, int room, double chance, java.util.function.DoubleSupplier u) {
        int max = Math.min(room, WILD_BREED_MAX), n = 0;
        for (int i = 0; i < adults && n < max; i++) {
            if (u.getAsDouble() < chance) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ sauvegarde compacte

    /** Bits par densite de depart (5 x 12 = 60 bits) et drapeaux (4 bits) dans un long par zone. */
    public static final int START_BITS = 12, START_MAX = (1 << START_BITS) - 1;
    /** Drapeau : zone d'un monde existant (densite estimee ; un groupe par passage). */
    public static final int FLAG_LEGACY = 1;

    public static long packZone(int[] starts, int flags) {
        long v = 0;
        for (int i = 0; i < FaunaGroup.COUNT; i++) {
            v |= (long) Math.max(0, Math.min(START_MAX, starts[i])) << (i * START_BITS);
        }
        return v | (long) (flags & 0xF) << (FaunaGroup.COUNT * START_BITS);
    }

    public static int[] starts(long packed) {
        int[] s = new int[FaunaGroup.COUNT];
        for (int i = 0; i < FaunaGroup.COUNT; i++) {
            s[i] = (int) (packed >>> (i * START_BITS) & START_MAX);
        }
        return s;
    }

    public static int flags(long packed) {
        return (int) (packed >>> (FaunaGroup.COUNT * START_BITS) & 0xF);
    }

    // ------------------------------------------------------------------ IA allegee

    /**
     * Compteur de {@code Mob#serverAiStep} (temps du serveur + identifiant : le vanilla choisit ses buts quand il est pair, un tick sur deux).
     * Bete inscrite loin de tout joueur : on ne le laisse pair qu'un tick pair sur {@link #AI_SLOWDOWN} (decisions tous les 8 ticks au lieu
     * de 2) ; les autres ticks pairs deviennent impairs (les buts deja choisis continuent de tourner).
     */
    public static int aiGate(int counter, boolean far) {
        if (!far || (counter & 1) != 0) {
            return counter;
        }
        return Math.floorMod(counter >> 1, AI_SLOWDOWN) == 0 ? counter : counter | 1;
    }
}
