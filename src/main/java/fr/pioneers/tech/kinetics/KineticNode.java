package fr.pioneers.tech.kinetics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Noeud du reseau de force rotative : arbre, engrenage, poulie, source ou machine. Chaque noeud tourne dans son propre repere (sens positif
 * de son axe, ou repere interne pour une boite a plusieurs axes) ; ses liens donnent le rapport des vitesses avec ses voisins.
 * <p>
 * Physique (unites SI, vitesses en tr/min) : une source pousse avec un couple {@link #sourceTorque()} qui decroit lineairement jusqu'a
 * s'annuler a {@link #sourceFreeSpeed()} ; une machine au travail demande un couple constant {@link #loadTorque()} ; frottement visqueux et
 * inertie ralentissent. Le reseau trouve seul sa vitesse (voir {@link KineticNetwork}).
 */
public interface KineticNode {

    /** Recepteur des liens d'un noeud : position du voisin et rapport (vitesse du voisin dans son repere / vitesse de ce noeud). */
    @FunctionalInterface
    interface LinkSink {

        void link(BlockPos other, double ratio);
    }

    BlockPos nodePos();

    /** Liens du noeud (le noeud voisin doit donner le lien inverse : rapport 1 / r). */
    void collectLinks(LinkSink sink);

    /**
     * Facteur de l'arbre qui sort par cette face : 0 = pas de bout d'arbre ; sinon k tel que la rotation autour de l'axe de la face (sens
     * positif de l'axe) vaille k x la vitesse du noeud. Deux bouts d'arbre face a face se lient avec le rapport k1 / k2.
     */
    double shaftFactor(Direction face);

    /** Couple de demarrage de la source (N.m, signe = sens d'entrainement dans le repere du noeud) ; 0 si ce n'est pas une source. */
    default double sourceTorque() {
        return 0;
    }

    /** Vitesse a vide de la source (tr/min, > 0). */
    default double sourceFreeSpeed() {
        return 1;
    }

    /** Couple demande par la machine (N.m, >= 0 ; s'oppose toujours au mouvement). */
    default double loadTorque() {
        return 0;
    }

    /** Frottement visqueux (N.m par tr/min). */
    default double friction() {
        return 0.01;
    }

    /** Moment d'inertie (kg.m2). */
    default double inertia() {
        return 0.2;
    }

    /** Regulateur (Watt) : raideur du freinage au-dessus de la vitesse visee (N.m par tr/min) ; 0 = pas de regulateur. */
    default double governorStiffness() {
        return 0;
    }

    /** Vitesse visee par le regulateur (tr/min, repere du noeud). */
    default double governorTarget() {
        return 0;
    }

    /**
     * 1.12.4 (lot F4) : generateur electrique a excitation adaptee (dynamo) : puissance mecanique maximale absorbable (W) ; 0 = pas un
     * generateur. Le reseau lui attribue a chaque tick un frottement visqueux « utile » (sa charge electrique) calcule pour tirer le plus de
     * puissance possible des sources (adaptation d'impedance : amortissement total des generateurs = pente des sources + frottements), partage
     * entre les generateurs au prorata de leur puissance, borne par cette puissance et par {@link #generatorTorque()}.
     */
    default double generatorRating() {
        return 0;
    }

    /** Couple nominal du generateur (N.m, son repere) : le reseau ne lui demande jamais plus. */
    default double generatorTorque() {
        return 1e9;
    }

    /** Excitation reglee (0..1) : part de la charge adaptee que le generateur prend (0 = coupe, reserve pleine...). */
    default double generatorExcitation() {
        return 1;
    }

    /** Amortissement attribue par le reseau pour ce tick (N.m par tr/min, repere du noeud) : puissance = c x vitesse x vitesse angulaire. */
    default void generatorDamping(double damping) {
    }

    /**
     * 1.12.4 (lot F4) : variateur (regulateur de rotation) : la piece fait passer la rotation de sa face d'entree a sa face de sortie avec un
     * rapport k (sortie / entree) que le reseau choisit a chaque tick pour que la vitesse de sortie ne depasse jamais {@link #variatorTarget()}
     * (k dans [{@link KineticNetwork#VARIATOR_MIN}, 1]). La puissance se conserve (aux rendements pres) : le couple de sortie vaut celui
     * d'entree / k. La piece tourne a la vitesse de son entree.
     */
    default boolean isVariator() {
        return false;
    }

    /** Variateur : vrai si {@code neighbour} est le voisin de la face de sortie. */
    default boolean variatorOutput(BlockPos neighbour) {
        return false;
    }

    /** Variateur : consigne de vitesse de sortie (tr/min, > 0). */
    default double variatorTarget() {
        return 1e9;
    }

    /** Variateur : dernier rapport connu (reprise d'un reseau reconstruit). */
    default double variatorRatio() {
        return 1;
    }

    /** Variateur : rapport choisi par le reseau a ce tick. */
    default void variatorRatioSet(double ratio) {
    }

    /** Rendement de la piece (engrenages 0,97, courroies 0,95...). */
    default double efficiency() {
        return 1;
    }

    /** Couple maximal transmissible (N.m) avant casse ou patinage. */
    double maxTorque();

    /** Vitesse maximale (tr/min) avant vibrations. */
    double maxSpeed();

    /** Appele a chaque tick par le reseau : vitesse du noeud (son repere), couple qu'il transmet, reseau bloque. */
    void onNetworkTick(KineticNetwork network, double speed, double torque, boolean jammed);

    /** Le noeud quitte son reseau (reconstruction) ; il garde sa derniere vitesse pour la continuite. */
    void leaveNetwork();

    /** Derniere vitesse connue (tr/min, repere du noeud). */
    double lastSpeed();

    /** 0.20.42 : derniere phase connue (degres, repere du noeud) ; NaN si inconnue. Sert a recaler la phase d'un reseau reconstruit. */
    default double lastPhase() {
        return Double.NaN;
    }

    /**
     * 0.20.42 : phase du noeud (degres, repere du noeud), tenue par le reseau : phase = rapport x phase de reference, si bien que deux pieces
     * voisines restent toujours en phase (arbres alignes, dents engrenees). {@code sync} : le reseau demande d'envoyer l'etat aux clients ce
     * tick-ci (tous les noeuds ensemble, pour qu'ils extrapolent depuis le meme instant). Appele juste avant {@link #onNetworkTick}.
     */
    default void networkPhase(double phase, boolean sync) {
    }

    /**
     * 0.20.46 (audit) : empreinte des liens relevee a la construction du reseau ({@link #collectLinks}, voir {@link #linkHash}) : un voisin
     * qui change sans toucher aux liens ne fait plus reconstruire le reseau.
     */
    default void linksBuilt(long signature) {
    }

    /** Graine de l'empreinte des liens. */
    long LINKS_SEED = 0x6A09E667F3BCC909L;

    /** Empreinte des liens : ajoute un lien (position du voisin, rapport), dans l'ordre ou {@link #collectLinks} les donne. */
    static long linkHash(long h, BlockPos other, double ratio) {
        h = (h ^ other.asLong()) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h = (h ^ Double.doubleToLongBits(ratio)) * 0xBF58476D1CE4E5B9L;
        return h ^ h >>> 31;
    }
}
