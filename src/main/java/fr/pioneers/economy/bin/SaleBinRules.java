package fr.pioneers.economy.bin;

import fr.pioneers.economy.Values;

/**
 * 1.10.1 (Guillaume : « un bloc pour le centre du village qui sert de bac de vente (en gros un double coffre) ; a minuit le bac est vide et
 * converti en jetons pour le joueur ; la reputation augmente de plusieurs points selon les ventes (plafond) ; les trades directs aux
 * villageois rapportent plus et donnent plus de reputation mais sont plus fastidieux ») : regles PURES du bac de vente (aucun monde : banc et
 * GameTest les appellent telles quelles). Les nombres viennent de {@code data/pioneers/economy/partners.json} (cle {@code bin}, ecrite par
 * {@code tools/gc/economy/gen_values.py}) ; les valeurs ci-dessous sont les replis.
 * <ul>
 * <li>Prix : {@link #SHARE} (70 %) du prix d'un villageois (50 % de la valeur), soit 35 % de la valeur, sans bonus de reputation, d'affinite
 * ni de Marchandage : vendre a la main rapporte toujours plus.</li>
 * <li>Ce que le bac prend : ce qu'un Colporteur acheterait (tout objet de la table sauf composants et machines), intact (ni abime, ni nomme,
 * ni enchante). Le reste reste dans le bac.</li>
 * <li>Limite par nuit et par sorte d'objet : {@link #QUOTA} fois le quota d'un villageois (raw 64 -> 128, crafted 16 -> 32...).</li>
 * <li>Reputation du village (moyenne des affinites) : +1 par tranche de {@link #PER_POINT} jetons, au plus {@link #MAX_POINTS} par nuit ;
 * commerce direct : +1 d'affinite par echange d'au moins 100 jetons, jusqu'a 5 par villageois et par jour, et les actes des peuples.</li>
 * </ul>
 */
public final class SaleBinRules {

    /** Cases du bac (un double coffre). */
    public static final int SLOTS = 54;
    public static final double SHARE = 0.7;
    public static final int QUOTA = 2;
    public static final long PER_POINT = 200;
    public static final int MAX_POINTS = 3;
    /** Distance horizontale maximale a la cloche du village (le centre de la place). */
    public static final double CENTER_RADIUS = 32;
    /** Affinite moyenne du village en dessous de laquelle il refuse le bac (comme le commerce : -40). */
    public static final int REFUSE_AT = -40;

    private SaleBinRules() {
    }

    /** Numero de la nuit : change a minuit (heure 18 000 du jour du monde). */
    public static long night(long dayTime) {
        return Math.floorDiv(dayTime + 6_000L, 24_000L);
    }

    public static double share() {
        return Values.partner("bin", "share", SHARE);
    }

    public static int quotaFactor() {
        return (int) Math.round(Values.partner("bin", "quota", QUOTA));
    }

    public static long perPoint() {
        return Math.max(1, Math.round(Values.partner("bin", "per_point", PER_POINT)));
    }

    public static int maxPoints() {
        return (int) Math.round(Values.partner("bin", "max_points", MAX_POINTS));
    }

    /** Prix unitaire en centiemes : valeur x part du villageois x part du bac, au moins 1. */
    public static long unit(long base, double villagerBuys, double share) {
        return Math.max(1, Math.round(base * villagerBuys * share));
    }

    /** Points de reputation d'une nuit de ventes. */
    public static int points(long tokens, long perPoint, int max) {
        return (int) Math.max(0, Math.min(max, tokens / Math.max(1, perPoint)));
    }

    /** Unites d'un objet que le bac prend encore cette nuit (limite = quota du villageois x facteur). */
    public static int room(int villagerQuota, int factor, int alreadySold, int offered) {
        return Math.max(0, Math.min(offered, villagerQuota * factor - alreadySold));
    }
}
