package fr.pioneers.transport.logistic;

import net.minecraft.nbt.CompoundTag;

/**
 * 1.11 : ce que le moteur du reseau logistique sait faire avec un gestionnaire d'un type (objets, fluides, gaz, donnees) : lire une case,
 * extraire, inserer, filtrer, juger physiquement. Le moteur ({@link LogisticNetwork}) ne connait que cette interface : les implementations
 * Minecraft sont dans {@link Accesses} (capacites Forge et du socle), le banc hors jeu a les siennes (faux gestionnaires). L'energie a son
 * acces sans boite ({@link EnergyAccess}).
 *
 * @param <H> gestionnaire (capacite)
 * @param <R> pile de ressource
 */
public interface Access<H, R> {

    ChannelKind kind();

    /** Nombre de cases (reservoirs) du gestionnaire. */
    int slots(H handler);

    /** Contenu d'une case (pile vide sinon) ; jamais modifie par l'appelant. */
    R peek(H handler, int slot);

    /** Contenance d'une case (mode Au plus juste ; 0 : inconnue). */
    long capacity(H handler, int slot);

    /** Extrait jusqu'a {@code max} de la sorte de {@code like} dans la case {@code slot}. */
    R extract(H handler, int slot, R like, long max, boolean simulate);

    /** Insere ; rend la quantite acceptee. {@code resource} n'est jamais modifiee. */
    long insert(H handler, R resource, boolean simulate);

    long amount(R resource);

    /** Copie de {@code resource} avec une autre quantite (allocation : seulement a l'insertion effective). */
    R withAmount(R resource, long amount);

    boolean same(R a, R b);

    boolean isEmpty(R resource);

    /** Cle de cache des filtres (objet, fluide, gaz, type de donnees : instance du registre). */
    Object filterKey(R resource);

    /** L'entree de filtre reconnait-elle la ressource (id, tag, mod, NBT) ? */
    boolean matches(Filter.Entry entry, R resource);

    /** Temperature de la ressource (K ; fluides). */
    default double temperature(R resource) {
        return 300;
    }

    /** Corrosif ou cryogenique : gaine PTFE requise (MKII et mieux). */
    default boolean lining(R resource) {
        return false;
    }

    /** Instantane pour le rendu des cables transparents (une unite ; vide si rien a montrer). */
    default CompoundTag snapshot(R resource) {
        return new CompoundTag();
    }

    /** Quantite totale de la sorte de {@code like} dans le gestionnaire (garder en stock, remplir jusqu'a). */
    default long count(H handler, R like) {
        long total = 0;
        int slots = slots(handler);
        for (int i = 0; i < slots; i++) {
            R in = peek(handler, i);
            if (!isEmpty(in) && same(in, like)) {
                total += amount(in);
            }
        }
        return total;
    }

    /** Remplissage 0..1 (contenu / contenance) pour le mode Au plus juste. */
    default double fill(H handler) {
        long total = 0, cap = 0;
        int slots = slots(handler);
        for (int i = 0; i < slots; i++) {
            R in = peek(handler, i);
            total += isEmpty(in) ? 0 : amount(in);
            cap += capacity(handler, i);
        }
        return cap <= 0 ? 0 : Math.min(1, (double) total / cap);
    }

    /** Le niveau du reseau porte-t-il cette ressource (temperature, revetement) ? */
    default boolean physical(CableLevel level, R resource) {
        return level.accepts(kind(), temperature(resource), lining(resource));
    }
}
