package fr.pioneers.pet;

/**
 * 1.0.5-beta (lot 5) : REGLES PURES des chiens et des chats (aucune classe du jeu : le controle {@code tools/check_p105_5.py} les relit).
 * Aptitudes de 0 a 1 ({@link GeneratedPets}) -> effets reels et moderes :
 * <ul>
 *   <li>chien : flair = portee de pistage en mode Chasse (24 a 64 blocs, x (1 + bonus du maitre)) ; vitesse = vitesse de marche (0,24 a
 *   0,36 ; le loup vanilla : 0,30) ; force = sante (16 a 30 PV) et morsure (2 a 6) ; endurance au froid = ralentissement dans le froid
 *   (jusqu'a -25 % sans endurance, rien pour le husky) ;</li>
 *   <li>chat : peche = frequence des parties de peche (de 8 a 3 minutes) et chance de prise (35 a 85 %) ; chasse aux rats = morsure (2 a 5)
 *   et portee de detection des rats (8 a 16 blocs) ; independance = espacement des visites au maitre en autonome (2 a 8 minutes) et
 *   distance a laquelle il commence a suivre (6 a 12 blocs).</li>
 * </ul>
 */
public final class PetRules {

    private PetRules() {
    }

    /**
     * 1.0.5-beta (lot 12) : un delai court ({@code gap} ticks) depuis {@code last} court-il encore a {@code now} ? Jamais si l'heure notee est
     * dans le futur (heure d'un autre monde en solo : sans cela, la liste de la roue ou le rappel restaient bloques jusqu'a ce que le temps du
     * nouveau monde rattrape l'ancien).
     */
    public static boolean waiting(@javax.annotation.Nullable Long last, long now, long gap) {
        return last != null && now >= last && now - last < gap;
    }

    /** Delai par defaut entre deux rappels (s) et rayon du territoire (blocs) : reglages serveur {@code pets.*}. */
    public static final int RECALL_COOLDOWN = 10, TERRITORY = 30;
    /** Maintien de la touche Rappel avant la roue (ticks client). */
    public static final int WHEEL_HOLD = 6;
    /** Rappel d'un animal pas charge : attente maximale du chargement de son troncon (ticks). */
    public static final int LOAD_WAIT = 200;
    /** Visite au maitre : duree (ticks) ; chien : intervalle fixe. */
    public static final int VISIT_MIN = 2400, VISIT_MAX = 4800, DOG_VISIT_INTERVAL = 6000;
    /** Sieste de jour : chance par seconde d'en commencer une, duree. */
    public static final double NAP_CHANCE = 1.0 / 240.0;
    public static final int NAP_MIN = 1200, NAP_MAX = 3000;
    /** Garde : aboiement contre un monstre ou un predateur dans le territoire, au plus toutes les (ticks). */
    public static final int BARK_GAP = 60;
    /** Chasse : le chien attend son maitre au-dela de (blocs) ; il s'arrete et marque l'arret a (blocs) du gibier. */
    public static final double HUNT_WAIT = 14.0, HUNT_POINT = 7.0;
    /** Peche : duree de l'affut au bord de l'eau (ticks), rayon des vrais poissons pris (blocs). */
    public static final int FISH_WAIT = 300;
    public static final double FISH_REACH = 6.0;
    /** Le panier : nombre de cases. */
    public static final int BASKET_SLOTS = 3;

    public static double scentRange(float scent, double bonus) {
        return (24.0 + 40.0 * scent) * (1.0 + bonus);
    }

    public static double dogSpeed(float speed) {
        return 0.30 * (0.8 + 0.4 * speed);
    }

    public static double dogHealth(float strength) {
        return 16.0 + 14.0 * strength;
    }

    public static double dogAttack(float strength) {
        return 2.0 + 4.0 * strength;
    }

    /** Part de vitesse perdue dans le froid (modificateur MULTIPLY_BASE negatif). */
    public static double coldPenalty(float cold) {
        return 0.25 * (1.0 - cold);
    }

    /** Froid : temperature du lieu (echelle du jeu, saison comprise) sous ce seuil (biomes enneiges, hiver froid). */
    public static final float COLD_BELOW = 0.15F;

    public static int catVisitInterval(float independence) {
        return (int) (2400 + 7200 * independence);
    }

    public static double catFollowStart(float independence) {
        return 6.0 + 6.0 * independence;
    }

    public static int fishInterval(float fishing) {
        return (int) (9600 - 6000 * fishing);
    }

    public static double fishChance(float fishing) {
        return 0.35 + 0.5 * fishing;
    }

    public static double catAttack(float ratting) {
        return 2.0 + 3.0 * ratting;
    }

    public static double ratRange(float ratting) {
        return 8.0 + 8.0 * ratting;
    }

    /** Dans le territoire (distance horizontale au panier). */
    public static boolean inTerritory(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Race d'un petit : meme race des deux parents -> elle ; sinon l'une des deux (comme les races d'elevage). */
    public static int childBreed(int a, int b, double roll) {
        return a == b ? a : roll < 0.5 ? a : b;
    }
}
