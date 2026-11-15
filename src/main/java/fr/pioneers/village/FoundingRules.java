package fr.pioneers.village;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * 1.0.3-beta (lot 11b, Guillaume : « si le joueur pose les blocs destines aux villages, alors il cree un village a son nom et les villageois
 * alentour seront les siens et il aura de meilleurs prix et un gain plus rapide de reputation ») : REGLES du village fonde par un joueur
 * (pures, sans Minecraft ; appliquees par {@link VillageFounding}, ecrites dans le guide par {@code T:rp/village_founding.py}).
 * <ul>
 *   <li>bloc coeur : le Tableau des besoins ; rayon {@link #RADIUS} blocs autour de lui, +{@link #PER_BLOCK} par autre bloc de village
 *   (tableau, fontaine, lampadaire) pose dans le rayon, au plus {@link #MAX_RADIUS} ;</li>
 *   <li>decision a la pose : pas de village dans le rayon = nouveau village ; un village sans fondateur = il devient celui du joueur ; le
 *   village du joueur = il s'agrandit ; le village d'un autre joueur = refus (le tableau reste un tableau de ce village) ;</li>
 *   <li>effets, pour le FONDATEUR seulement et dans SON village : achats {@link #BUY_FACTOR} (-15 %), ventes {@link #SELL_FACTOR} (+15 %),
 *   gains de reputation (affinite) x{@link #REP_FACTOR} arrondis au-dessus ; les pertes ne changent pas ; rien ne change pour les autres.</li>
 * </ul>
 */
public final class FoundingRules {

    public static final int RADIUS = 64, PER_BLOCK = 4, MAX_RADIUS = 96;
    public static final double BUY_FACTOR = 0.85, SELL_FACTOR = 1.15, REP_FACTOR = 1.5;

    /** Ce que fait la pose d'un Tableau des besoins. */
    public enum Decision {
        /** Aucun village dans le rayon : un village nait, au nom du joueur. */
        FOUND,
        /** Village sans fondateur dans le rayon : il devient celui du joueur. */
        CLAIM,
        /** Le village du joueur : le tableau l'agrandit. */
        GROW,
        /** Le village d'un autre joueur : pas de fondation. */
        REFUSED
    }

    private FoundingRules() {
    }

    /** Rayon d'un village fonde qui compte {@code blocks} blocs de village (le tableau coeur compris). */
    public static int radius(int blocks) {
        return Math.min(MAX_RADIUS, RADIUS + PER_BLOCK * Math.max(0, blocks - 1));
    }

    /**
     * @param villageNear un village (connu) est-il dans le rayon de la pose ?
     * @param owner       son fondateur (null : aucun)
     */
    public static Decision decide(boolean villageNear, @Nullable UUID owner, UUID player) {
        if (!villageNear) {
            return Decision.FOUND;
        }
        if (owner == null) {
            return Decision.CLAIM;
        }
        return owner.equals(player) ? Decision.GROW : Decision.REFUSED;
    }

    /** Variation d'affinite appliquee : un gain du fondateur dans son village est multiplie (arrondi au-dessus) ; une perte est inchangee. */
    public static int repDelta(int delta, boolean founder) {
        return founder && delta > 0 ? (int) Math.ceil(delta * REP_FACTOR) : delta;
    }

    /** Facteur de prix du fondateur dans son village (achat ou vente), 1 sinon. */
    public static double priceFactor(boolean founder, boolean buy) {
        if (!founder) {
            return 1;
        }
        return buy ? BUY_FACTOR : SELL_FACTOR;
    }
}
