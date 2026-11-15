package fr.pioneers.warehouse;

/**
 * 1.0.5-beta (lot 22, retour 2 de Guillaume : « un meme objet peut occuper plusieurs types si le sien est plein mais que les autres types
 * sont disponibles ») : regle PURE (sans Minecraft) du DEBORDEMENT d'une caisse d'entrepot. La caisse a {@code maxTypes} CASES DE TYPE de
 * {@code cap} objets chacune (piles x taille de pile de l'objet). Un type occupe autant de cases que son nombre l'exige (au moins une) :
 * plein, il continue dans les cases libres ; vide, il les rend. Le stockage reste un index type -> quantite (sauvegardes inchangees) :
 * les cases occupees se DEDUISENT des quantites ({@link #slots}), jamais gardees.
 * <p>
 * Lecture faite seul (decision du lot) : les « types » de Guillaume sont les 256 cases de type de la caisse (la fiche disait « Types : n /
 * 256 ») ; un objet deborde dans n'importe quelle case libre, sans limite (une caisse peut ne contenir qu'un seul objet) ; un nouveau type
 * ne prend une case que s'il en reste une libre. Banc : {@code tools/warehouse_bench/overflow_bench.sh} (« DEBORDEMENT : fautes 0 »).
 */
public final class CrateRules {

    private CrateRules() {
    }

    /** Cases de type occupees par {@code count} objets d'un type de contenance {@code cap} par case (au moins une s'il y en a). */
    public static int slots(long count, long cap) {
        if (count <= 0) {
            return 0;
        }
        long c = Math.max(1, cap);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, (count + c - 1) / c));
    }

    /**
     * Place pour un type : ce qui reste dans ses propres cases, plus les cases libres de la caisse ({@code used} cases occupees sur
     * {@code maxTypes}) ; un type absent ({@code count} 0) ne prend une case que s'il en reste une.
     */
    public static long room(long count, long cap, int used, int maxTypes) {
        long c = Math.max(1, cap);
        long free = Math.max(0, (long) maxTypes - used);
        long own = count <= 0 ? 0 : Math.max(0, slots(count, c) * c - count);
        long more = free * c;
        return own > Long.MAX_VALUE - more ? Long.MAX_VALUE : own + more;
    }

    /** Remplissage 0..1 : objets ramenes en cases, sur les cases de la caisse. */
    public static double fill(double slotsWorth, int maxTypes) {
        return maxTypes <= 0 ? 0 : Math.max(0, Math.min(1, slotsWorth / maxTypes));
    }
}
