package fr.pioneers.pet;

/**
 * 1.0.5-beta (lot 28, retour 35 de Guillaume : « les chats et les chiens sont assez lents a la detente pour faire leur comportement : lents a
 * capter qu'il y avait un loup, les chats ne pechent pas ») : REGLES PURES des delais de reaction des animaux de compagnie (aucune classe du
 * jeu : le banc {@code tools/pets/react_bench.sh} les rejoue sur le vrai selecteur de buts, {@code tools/check_p105_28.py} les relit).
 * <p>
 * Causes mesurees (banc) et corrigees :
 * <ul>
 *     <li>{@code canUse} n'est evalue qu'UN tick sur deux ({@code Mob.serverAiStep} : compteur = temps du serveur + identifiant, pair = choix
 *     des buts), et l'age {@code tickCount} d'une bete garde une parite fixe par rapport a ce compteur : un filtre {@code tickCount % 20} dans
 *     {@code canUse} ne passe JAMAIS pour une bete sur deux (le chat ne chassait pas les rats, le requin ne voyait pas le nageur). Regle : dans
 *     un {@code canUse}, une heure de prochain essai ({@link #due}), jamais un modulo de l'age ;</li>
 *     <li>attaque : le {@code MeleeAttackGoal} vanilla ne tente {@code canUse} qu'une fois toutes les 20 ticks, apres la garde qui ne relevait
 *     les intrus que toutes les 20 ticks : jusqu'a 42 ticks entre l'arrivee du loup et la course du chien. Maintenant : releve toutes les
 *     {@link #GUARD_SCAN} ticks dans le tick de l'entite (toujours appele), cible prise au choix de buts suivant, attaque SANS delai
 *     ({@code PetGoals.Strike}) : au plus {@link #REACT_MAX} ticks ;</li>
 *     <li>garde seulement en autonome : en Suivre / Chasse, le chien ne reagissait qu'apres une morsure sur son maitre ; maintenant il defend
 *     a {@link #DEFEND_RANGE} blocs de lui ou de son maitre ;</li>
 *     <li>sieste : la chance « par seconde » etait tiree a chaque evaluation (10 fois par seconde) : le chat dormait presque toute la journee ;
 *     tirage toutes les {@link #NAP_ROLL} ticks ;</li>
 *     <li>peche : 40 colonnes tirees au hasard dans 61 x 61 (une mare de 3 x 3 trouvee 9 fois sur 100) et un echec qui coutait tout l'intervalle
 *     (3 a 8 minutes) ; maintenant la colonne d'eau la plus proche du panier ({@link #ring}, du centre vers le bord), gardee en memoire, et un
 *     nouvel essai {@link #FISH_RETRY} ticks apres un echec.</li>
 * </ul>
 */
public final class PetReact {

    private PetReact() {
    }

    /** Objectif : reaction a une menace a portee en au plus 20 ticks (1 s). */
    public static final int REACT_MAX = 20;
    /** Releve des menaces du chien (tick de l'entite) ; chasse aux rats du chat ; pistage du chien de chasse (au repos / en chasse). */
    public static final int GUARD_SCAN = 10, PREY_SCAN = 10, HUNT_SCAN = 20, HUNT_RESCAN = 40;
    /** En Suivre et en Chasse, le chien defend contre un predateur a cette distance de lui ou de son maitre (blocs). */
    public static final double DEFEND_RANGE = 16.0;
    /** Visite : maitre absent, nouvel essai apres (ticks) ; peche : nouvel essai apres un echec (pas d'eau, panier absent ou plein). */
    public static final int VISIT_RETRY = 100, FISH_RETRY = 600;
    /** Sieste : un tirage de la chance « par seconde » toutes les 20 ticks. */
    public static final int NAP_ROLL = 20;

    /** L'heure de prochain essai est-elle passee ? */
    public static boolean due(long now, long next) {
        return now >= next;
    }

    /** Releve dans le tick de l'ENTITE (appele a chaque tick, sans parite) : une fois toutes les {@code every} ticks, decale par identifiant. */
    public static boolean scanTick(int tickCount, int id, int every) {
        return Math.floorMod(tickCount + id, every) == 0;
    }

    private static int[][] ring;
    private static int ringRadius = -1;

    /**
     * Colonnes du territoire de rayon {@code r} (dx, dz) rangees du centre vers le bord (distance, puis angle : ordre stable) ; seules celles du
     * disque. Tableau partage (ne pas modifier).
     */
    public static synchronized int[][] ring(int r) {
        if (ringRadius == r && ring != null) {
            return ring;
        }
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (PetRules.inTerritory(dx, dz, r)) {
                    l.add(new int[]{dx, dz});
                }
            }
        }
        l.sort((a, b) -> {
            int c = Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]);
            return c != 0 ? c : Double.compare(Math.atan2(a[1], a[0]), Math.atan2(b[1], b[0]));
        });
        ring = l.toArray(new int[0][]);
        ringRadius = r;
        return ring;
    }
}
