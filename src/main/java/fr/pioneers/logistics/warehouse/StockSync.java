package fr.pioneers.logistics.warehouse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 1.0.5-beta (lot 17, Guillaume : « fluide et performant ») : SYNCHRO PAR DIFFERENCES de la liste de l'entrepot, pour UN joueur qui a le
 * comptoir ouvert (calcul PUR, cles quelconques : {@link StockKey} au jeu). Avant, la liste ENTIERE partait a chaque changement (un gros
 * entrepot de 5 000 types : plusieurs centaines de Ko toutes les demi-secondes, et le client la coupait a 4 096 lignes). Maintenant on garde
 * ce que le client A DEJA ({@link #sent}) et on n'envoie que les lignes qui changent (quantite 0 = type disparu), au plus {@code max} par
 * paquet : la premiere ouverture d'un gros entrepot arrive en quelques paquets successifs ({@link #pending}). Une passe sur le stock
 * ({@link #compute}, O(types)) seulement quand l'empreinte des caisses change ; les paquets suivants se vident de la file ({@link #drain}).
 */
public final class StockSync<K> {

    /** Une ligne qui change : la cle et sa nouvelle quantite (0 : retiree). */
    public record Change<K>(K key, long count) {}

    private final Map<K, Long> sent = new HashMap<>();
    /** Differences calculees pas encore envoyees (une passe sur le stock, puis des paquets de {@code max} lignes). */
    private final ArrayDeque<Change<K>> queue = new ArrayDeque<>();
    private boolean reset = true;
    /** Empreinte du stock au dernier calcul ({@code Long.MIN_VALUE} : jamais). */
    private long stamp = Long.MIN_VALUE;

    /** Calcule les differences entre {@code now} (le stock) et ce que le client a deja, puis en rend au plus {@code max}. */
    public List<Change<K>> delta(Map<K, Long> now, int max) {
        compute(now);
        return drain(max);
    }

    /** Toutes les differences entre {@code now} et ce que le client a deja (une passe sur chaque table : O(types)). */
    public void compute(Map<K, Long> now) {
        queue.clear();
        for (Map.Entry<K, Long> e : now.entrySet()) {
            long n = e.getValue() == null ? 0 : e.getValue();
            Long old = sent.get(e.getKey());
            if (n > 0 && (old == null || old != n)) {
                queue.add(new Change<>(e.getKey(), n));
            }
        }
        for (K k : sent.keySet()) {
            Long n = now.get(k);
            if (n == null || n <= 0) {
                queue.add(new Change<>(k, 0));
            }
        }
    }

    /** Au plus {@code max} differences calculees, comptees comme envoyees ; {@link #pending} : il en reste. */
    public List<Change<K>> drain(int max) {
        List<Change<K>> out = new ArrayList<>(Math.min(max, queue.size()));
        while (out.size() < max && !queue.isEmpty()) {
            Change<K> c = queue.poll();
            if (c.count() > 0) {
                sent.put(c.key(), c.count());
            } else {
                sent.remove(c.key());
            }
            out.add(c);
        }
        return out;
    }

    /** Premier paquet (le client repart d'une liste vide) ? Vrai une seule fois, puis apres {@link #restart}. */
    public boolean takeReset() {
        boolean r = reset;
        reset = false;
        return r;
    }

    /** Le client repart de zero (interface rouverte, reseau change) : tout sera renvoye. */
    public void restart() {
        sent.clear();
        queue.clear();
        reset = true;
        stamp = Long.MIN_VALUE;
    }

    /** Reste-t-il des differences a envoyer (paquet precedent plein) ? */
    public boolean pending() {
        return !queue.isEmpty();
    }

    /** Le stock a-t-il change depuis le dernier calcul (empreinte) ? */
    public boolean stale(long stamp) {
        return this.stamp != stamp;
    }

    public void stamp(long stamp) {
        this.stamp = stamp;
    }

    /** Lignes que le client connait. */
    public int size() {
        return sent.size();
    }

    /** Ce que le client connait de cette cle (banc, controle). */
    public long known(K key) {
        return Objects.requireNonNullElse(sent.get(key), 0L);
    }
}
