package fr.pioneers.logistics.rail.logistics;

/**
 * 0.20.5 : alerte du rail affichee sur la carte : demande sans train libre, train bloque (sans chemin), livraison abandonnee.
 * {@code pos} : panneau de gare ou tete du train (long de BlockPos).
 */
public record RailAlert(Type type, long pos, String subject, long first, long last) {

    public enum Type {
        NO_TRAIN, STUCK, ABANDONED;

        public static Type byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public RailAlert seen(long now) {
        return new RailAlert(type, pos, subject, first, now);
    }
}
