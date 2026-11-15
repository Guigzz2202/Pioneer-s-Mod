package fr.pioneers.society.economy.stall;

/**
 * 1.9 (MAJ RP, bloc 3) : regles des etals SANS aucune classe de Minecraft (banc hors jeu {@code tools/rptest/StallBench.java}) : bornes du
 * prix d'un lot (25 % a 400 % de la valeur de base, en jetons entiers), taxe, nombre d'annonces, memoire des 60 dernieres ventes d'une annonce
 * (anti-boucle de l'XP) et deroulement d'un achat en une seule transaction : tout est verifie, puis tout est modifie ; rien de partiel.
 *
 * <p>Le prix choisi par le proprietaire est celui d'un LOT (quantite par lot de 1 a {@link #MAX_LOT}) en jetons entiers : un prix entier a
 * l'unite rendrait invendables les objets de moins de 0,25 jeton (ble, pierre, bois...). Avec un lot de 1, c'est le prix a l'unite.
 */
public final class StallRules {

    /** Taille de la memoire des acheteurs d'une annonce. */
    public static final int HISTORY = 60;
    public static final int MAX_LOT = 64;
    /** Lots achetables d'un coup. */
    public static final int MAX_LOTS = 999;
    /** Quantites par lot proposees (choix automatique du plus petit lot qui admet un prix entier). */
    public static final int[] LOT_STEPS = {1, 2, 4, 8, 16, 32, 64};

    private StallRules() {
    }

    // ------------------------------------------------------------------ prix
    /** Prix minimal d'un lot (jetons entiers, au moins 1) : {@code minPct} de la valeur de base du lot ({@code value} en centiemes par unite). */
    public static long minPrice(long value, int lot, double minPct) {
        double raw = value * (double) lot * minPct / 100.0;
        return Math.max(1, (long) Math.ceil(raw - 1e-9));
    }

    /** Prix maximal d'un lot (jetons entiers) : {@code maxPct} de la valeur de base du lot. */
    public static long maxPrice(long value, int lot, double maxPct) {
        return (long) Math.floor(value * (double) lot * maxPct / 100.0 + 1e-9);
    }

    /** Le lot admet-il au moins un prix entier dans les bornes ? */
    public static boolean priceable(long value, int lot, double minPct, double maxPct) {
        return value > 0 && lot >= 1 && lot <= MAX_LOT && maxPrice(value, lot, maxPct) >= minPrice(value, lot, minPct);
    }

    /** Le prix d'un lot est-il dans les bornes ? */
    public static boolean valid(long price, long value, int lot, double minPct, double maxPct) {
        return priceable(value, lot, minPct, maxPct) && price >= minPrice(value, lot, minPct) && price <= maxPrice(value, lot, maxPct);
    }

    /** Ramene un prix dans les bornes (le lot doit etre {@link #priceable}). */
    public static long clamp(long price, long value, int lot, double minPct, double maxPct) {
        return Math.max(minPrice(value, lot, minPct), Math.min(maxPrice(value, lot, maxPct), price));
    }

    /** Pourcentage de la valeur de base (entier arrondi). */
    public static int percent(long price, long value, int lot) {
        if (value <= 0 || lot <= 0) {
            return 0;
        }
        return (int) Math.min(100_000, Math.round(price * 10_000.0 / (value * (double) lot)));
    }

    /** Prix propose quand une annonce est creee ou que son lot change : 100 % de la valeur, arrondi, ramene dans les bornes. */
    public static long defaultPrice(long value, int lot, double minPct, double maxPct) {
        return clamp(Math.round(value * (double) lot / 100.0), value, lot, minPct, maxPct);
    }

    /** Plus petit lot proposable qui admet un prix entier (au moins {@code wanted}), sinon -1. */
    public static int smallestLot(long value, int wanted, double minPct, double maxPct) {
        for (int lot : LOT_STEPS) {
            if (lot >= wanted && priceable(value, lot, minPct, maxPct)) {
                return lot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ taxe et annonces
    /** Taux de la taxe en pour mille : taxe de base (fraction, 0.05) plus le bonus de Commerce (points, -2.5 au niveau 10), borne a 0..500. */
    public static int taxPermille(double baseRate, double bonusPoints) {
        return (int) Math.max(0, Math.min(500, Math.round(baseRate * 1000 + bonusPoints * 10)));
    }

    /** Taxe d'une vente : arrondie au jeton superieur (le puits l'emporte : decouper une vente n'evite pas la taxe). */
    public static long tax(long total, int permille) {
        if (total <= 0 || permille <= 0) {
            return 0;
        }
        return Math.min(total, (total * permille + 999) / 1000);
    }

    /** Annonces en plus grace au skill Commerce : partie entiere du modificateur (3 au niveau 10), triplee au Comptoir. */
    public static int listingBonus(double modifier, boolean counter) {
        int b = (int) Math.floor(Math.max(0, modifier) + 1e-9);
        return counter ? b * 3 : b;
    }

    /** Tranches de 100 jetons pour l'XP de vente d'un etal. */
    public static int xpTranches(long total) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total / 100));
    }

    // ------------------------------------------------------------------ memoire des acheteurs (anti-boucle)
    /** Les {@link #HISTORY} derniers acheteurs d'une annonce (empreinte de l'UUID), du plus ancien au plus recent. */
    public static final class History {

        private final long[] ring = new long[HISTORY];
        private int size, head;

        public boolean contains(long buyer) {
            for (int i = 0; i < size; i++) {
                if (ring[i] == buyer) {
                    return true;
                }
            }
            return false;
        }

        public void add(long buyer) {
            ring[head] = buyer;
            head = (head + 1) % HISTORY;
            size = Math.min(HISTORY, size + 1);
        }

        /** Inscrit la vente ; vrai si elle compte pour l'XP (acheteur absent des 60 dernieres ventes de l'annonce). */
        public boolean record(long buyer) {
            boolean fresh = !contains(buyer);
            add(buyer);
            return fresh;
        }

        public int size() {
            return size;
        }

        public void clear() {
            size = 0;
            head = 0;
        }

        /** Du plus ancien au plus recent. */
        public long[] toArray() {
            long[] out = new long[size];
            int start = size < HISTORY ? 0 : head;
            for (int i = 0; i < size; i++) {
                out[i] = ring[(start + i) % HISTORY];
            }
            return out;
        }

        public static History of(long[] buyers) {
            History h = new History();
            int from = Math.max(0, buyers.length - HISTORY);
            for (int i = from; i < buyers.length; i++) {
                h.add(buyers[i]);
            }
            return h;
        }
    }

    // ------------------------------------------------------------------ transaction
    /** Resultat d'un achat ; seul {@link #OK} a modifie quelque chose. */
    public enum Outcome {
        OK, CLOSED, SELF, NO_OWNER, NO_LISTING, BAD_PRICE, NO_STOCK, BROKE, NO_ROOM, FAILED;

        /** Cle de langue du message (sauf OK). */
        public String key() {
            return "stall.pioneers.result." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** L'acheteur : solde, debit (faux = refus, rien change), remboursement, place pour l'objet (unites), remise des objets. */
    public interface Buyer {

        long balance();

        boolean debit(long amount);

        void refund(long amount);

        int room();

        void give(int units);
    }

    /** Le stock de l'etal pour l'objet de l'annonce : quantite, retrait (faux = rien retire). */
    public interface Shelf {

        int stock();

        boolean take(int units);
    }

    /** Le proprietaire (connecte ou non) : paiement de sa part, apres taxe. */
    public interface Payee {

        void pay(long share);
    }

    /** Devis d'un achat : lots, unites, total paye, taxe detruite, part du proprietaire. */
    public record Deal(int lots, int units, long total, long tax, long share) {
    }

    public static Deal quote(long price, int lot, int lots, int permille) {
        long total = price * lots;
        long t = tax(total, permille);
        return new Deal(lots, lot * lots, total, t, total - t);
    }

    /**
     * Verifie tout puis execute : aucune ecriture avant que tout soit verifie (etal ouvert, pas son propre etal, proprietaire connu, annonce,
     * prix dans les bornes, stock, solde, place). Ecritures : debit de l'acheteur, retrait du stock (rembourse si le retrait echoue malgre la
     * verification), remise des objets, part du proprietaire.
     */
    public static Outcome execute(Deal deal, boolean open, boolean self, boolean owned, boolean listing, boolean priceOk, Buyer buyer, Shelf shelf,
          Payee owner) {
        if (!open) {
            return Outcome.CLOSED;
        }
        if (self) {
            return Outcome.SELF;
        }
        if (!owned) {
            return Outcome.NO_OWNER;
        }
        if (!listing || deal.lots() <= 0 || deal.units() <= 0) {
            return Outcome.NO_LISTING;
        }
        if (!priceOk || deal.total() <= 0) {
            return Outcome.BAD_PRICE;
        }
        if (shelf.stock() < deal.units()) {
            return Outcome.NO_STOCK;
        }
        if (buyer.balance() < deal.total()) {
            return Outcome.BROKE;
        }
        if (buyer.room() < deal.units()) {
            return Outcome.NO_ROOM;
        }
        //tout est verifie : on modifie
        if (!buyer.debit(deal.total())) {
            return Outcome.BROKE;
        }
        if (!shelf.take(deal.units())) {
            buyer.refund(deal.total());
            return Outcome.FAILED;
        }
        buyer.give(deal.units());
        owner.pay(deal.share());
        return Outcome.OK;
    }
}
