package fr.pioneers.caravan;

/**
 * 1.0.4-beta (lot 10) : REGLES PURES des caravanes (sans Minecraft : banc et GameTests) : cases d'un bat d'apres la charge de bat de la bete
 * (lot 9 : {@code PiHerd.packKg}), vitesse selon la charge et la faim, endurance au desert, rations, ordre des etapes (boucle, aller-retour),
 * filtre du relais, condition de depart. Chiffres : {@link GeneratedCaravan} (source {@code tools/gc/caravan/caravan.py}).
 */
public final class CaravanRules {

    private CaravanRules() {
    }

    /** Ordres d'un relais (meme ordre que {@code ORDERS} de caravan.py). */
    public static final int LOAD = 0, UNLOAD = 1, BOTH = 2, PASS = 3, ORDERS = 4;
    /** Conditions de depart. */
    public static final int TIME = 0, FULL = 1, EMPTY = 2, CONDITIONS = 3;

    /**
     * 1.0.4-beta (lot 29, Guillaume : « les mobs marchent tres tres lentement puis se teleportent ») : vitesse de marche (blocs / s) d'un mob
     * terrestre qui suit un chemin, en fonction de {@code s} = facteur de navigation x attribut {@code MOVEMENT_SPEED} : la loi du jeu est
     * QUADRATIQUE. A chaque tick, {@code MoveControl} pose {@code speed = s} et {@code zza = s}, {@code aiStep} multiplie {@code zza} par
     * 0,98, {@code travel} ajoute {@code s x 0,98 s} a la vitesse (frottement du sol 0,6 : {@code 0,216 / 0,6^3 = 1}), deplace le mob de
     * cette vitesse puis la multiplie par {@code 0,6 x 0,91 = 0,546} : deplacement etabli = {@code 0,98 s^2 / 0,454} bloc par tick, soit
     * {@code WALK_K x s^2} blocs / s ({@code WALK_K} = 43,17 : le chiffre du joueur, dont l'entree vaut 1 et non {@code s}). Le lot 10 la
     * croyait LINEAIRE ({@code pas / (attribut x 42,16)}, la loi du joueur) : pour 3,2 blocs / s, il donnait s = 0,076, soit 0,25 bloc / s ;
     * faute de progres (un bloc par seconde exige), le secours teleportait la caravane toutes les 30 s. Verifie au banc
     * {@code tools/rptest/CaravanWalkBench.java} (memes pas de calcul que le jeu, tick par tick).
     */
    public static final double WALK_K = 20 * 0.98 / (1 - 0.6 * 0.91);

    /** Facteur de navigation pour marcher a {@code blocksPerSecond} avec cet attribut de vitesse (loi ci-dessus), borne a 0,05..8. */
    public static double navSpeed(double blocksPerSecond, double attribute) {
        double s = Math.sqrt(Math.max(0, blocksPerSecond) / WALK_K);
        return Math.max(0.05, Math.min(8, s / Math.max(0.01, attribute)));
    }

    /** Vitesse de marche etablie (blocs / s) d'un mob a ce facteur de navigation et cet attribut (sol ordinaire). */
    public static double walkSpeed(double navSpeed, double attribute) {
        double s = navSpeed * attribute;
        return WALK_K * s * s;
    }

    /**
     * Pas d'un suiveur (blocs / s) : celui de la tete, jusqu'a {@code CATCH_UP} fois plus quand il est en retard (ecart a son meneur au-dela
     * de {@code WAIT_GAP / 2}, au plus a {@code WAIT_GAP}) ; la laisse vanilla le mene a 2 blocs de son meneur.
     */
    public static double followerPace(double headPace, double gap) {
        double lo = GeneratedCaravan.WAIT_GAP / 2, hi = GeneratedCaravan.WAIT_GAP;
        double k = gap <= lo ? 1 : gap >= hi ? GeneratedCaravan.CATCH_UP : 1 + (GeneratedCaravan.CATCH_UP - 1) * (gap - lo) / (hi - lo);
        return headPace * k;
    }

    /** Cases du bat pour une charge de bat (kg) : une case par {@code KG_PER_SLOT} kg, bornee. */
    public static int slots(int packKg) {
        if (packKg <= 0) {
            return GeneratedCaravan.MIN_SLOTS;
        }
        int n = Math.round(packKg / (float) GeneratedCaravan.KG_PER_SLOT);
        return Math.max(GeneratedCaravan.MIN_SLOTS, Math.min(GeneratedCaravan.MAX_SLOTS, n));
    }

    /**
     * Facteur de vitesse de la caravane : bats remplis a {@code fill} (0..1) : jusqu'a - {@code LOAD_SLOW} (sauf chameaux au desert) ;
     * une bete affamee : - {@code HUNGRY_SLOW}.
     */
    public static float speedFactor(float fill, boolean hungry, boolean camelsInDesert) {
        float k = 1;
        if (!camelsInDesert) {
            k *= 1 - GeneratedCaravan.LOAD_SLOW * Math.max(0, Math.min(1, fill));
        }
        if (hungry) {
            k *= 1 - GeneratedCaravan.HUNGRY_SLOW;
        }
        return k;
    }

    /** Blocs parcourus avec une ration : chameaux x 2 au desert, autres betes / 1,5 au desert. */
    public static float fedBlocks(boolean camel, boolean desert) {
        float b = camel ? GeneratedCaravan.FED_BLOCKS_CAMEL : GeneratedCaravan.FED_BLOCKS;
        if (desert) {
            b = camel ? b * 2 : b / 1.5F;
        }
        return b;
    }

    /**
     * Etape suivante : {@code {index, sens}}. Boucle : 0 -> 1 -> ... -> n-1 -> 0 ; aller-retour : 0 -> ... -> n-1 -> n-2 -> ... -> 0 -> 1.
     */
    public static int[] next(int size, int index, int dir, boolean loop) {
        if (size <= 1) {
            return new int[]{0, 1};
        }
        if (loop) {
            return new int[]{(index + 1) % size, 1};
        }
        int d = dir >= 0 ? 1 : -1;
        int n = index + d;
        if (n >= size) {
            d = -1;
            n = size - 2;
        } else if (n < 0) {
            d = 1;
            n = 1;
        }
        return new int[]{n, d};
    }

    /** Distance horizontale entre deux etapes acceptable ({@code MAX_LEG} au plus) ? */
    public static boolean legOk(int ax, int az, int bx, int bz) {
        long dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz <= (long) GeneratedCaravan.MAX_LEG * GeneratedCaravan.MAX_LEG;
    }

    public static int legLength(int ax, int az, int bx, int bz) {
        return (int) Math.round(Math.sqrt((double) (ax - bx) * (ax - bx) + (double) (az - bz) * (az - bz)));
    }

    /** Le filtre (ids bruts, 0 = case vide) retient-il cet objet ? Filtre vide : tout. */
    public static boolean keeps(int[] filter, int itemId) {
        boolean any = false;
        for (int f : filter) {
            if (f != 0) {
                any = true;
                if (f == itemId) {
                    return true;
                }
            }
        }
        return !any;
    }

    /** Pour un ordre, un objet du bat part-il au conteneur ? */
    public static boolean unloads(int order, boolean kept) {
        return order == UNLOAD && kept || order == BOTH && !kept;
    }

    /** Pour un ordre, un objet du conteneur va-t-il au bat ? */
    public static boolean loads(int order, boolean kept) {
        return (order == LOAD || order == BOTH) && kept;
    }

    /**
     * Depart ? Toujours apres l'attente (au moins {@code MIN_STOP}) ; TIME : c'est tout ; FULL : bats pleins ; EMPTY : plus rien a decharger.
     */
    public static boolean ready(int condition, int stopTicks, int waitSeconds, boolean full, boolean nothingToUnload) {
        if (stopTicks < Math.max(GeneratedCaravan.MIN_STOP, waitSeconds) * 20) {
            return false;
        }
        return condition == FULL ? full : condition != EMPTY || nothingToUnload;
    }

    /** Attente bornee (secondes, pas de {@code WAIT_STEP}). */
    public static int clampWait(int s) {
        int v = Math.max(0, Math.min(GeneratedCaravan.WAIT_MAX, s));
        return v / GeneratedCaravan.WAIT_STEP * GeneratedCaravan.WAIT_STEP;
    }
}
