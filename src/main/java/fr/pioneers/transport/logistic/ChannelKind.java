package fr.pioneers.transport.logistic;

import java.util.Locale;

/**
 * 1.11 (cable logistique) : type d'un canal. L'ordre est fixe (couloirs des cables transparents, rosace, diagramme) : objets, fluides, gaz,
 * energie, chaleur, pression, donnees.
 * <p>
 * Cadences (spec §7.1) : le credit s'accumule entre deux passages, le debit moyen ne change pas. Attente croissante au repos : 2, 4, 8 ...
 * {@link #maxIdle} ticks (0 : jamais d'attente, l'energie lit la demande a chaque tick).
 */
public enum ChannelKind {
    ITEM(10, 40),
    FLUID(2, 20),
    GAS(2, 20),
    ENERGY(1, 0),
    HEAT(1, 20),
    PRESSURE(1, 20),
    DATA(5, 20);

    private static final ChannelKind[] VALUES = values();
    /** Nombre de types (taille des tableaux indexes par l'ordinal). */
    public static final int COUNT = VALUES.length;

    /** Ticks entre deux passages d'un canal actif. */
    public final int cadence;
    /** Attente maximale d'un canal qui n'a rien deplace (ticks). */
    public final int maxIdle;

    ChannelKind(int cadence, int maxIdle) {
        this.cadence = cadence;
        this.maxIdle = maxIdle;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Canal « a corps » : un corps thermique ou un volume d'air commun, echanges physiques (pas de repartition). */
    public boolean body() {
        return this == HEAT || this == PRESSURE;
    }

    /** Debit exprime par seconde (objets) plutot que par tick. */
    public boolean perSecond() {
        return this == ITEM;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static ChannelKind byIndex(int index) {
        return VALUES[Math.floorMod(index, COUNT)];
    }

    public static ChannelKind byId(String id) {
        for (ChannelKind kind : VALUES) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return ITEM;
    }

    public static ChannelKind[] all() {
        return VALUES;
    }
}
