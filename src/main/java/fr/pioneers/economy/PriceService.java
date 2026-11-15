package fr.pioneers.economy;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.reputation.ReputationApi;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9 (MAJ RP, bloc 3) : le SEUL calculateur de prix. {@link #quote} applique, dans cet ordre : valeur de base, pourcentage du partenaire
 * (et bonus de doctrine), variations (palier de reputation du peuple, palier d'affinite du villageois, Marchandage du Commercant) additionnees
 * puis bornees (jamais moins de 85 % de la base a l'achat, jamais plus de 120 % du prix de vente a la vente), evenement, facteur du fondateur
 * (1.0.3-beta lot 11b, {@code FoundingRules}), puis quota. Les
 * interfaces demandent un apercu ; le serveur recalcule au moment de l'echange. Montants en centiemes, total arrondi au jeton inferieur.
 */
public final class PriceService {

    private PriceService() {
    }

    /** BUY : le joueur achete au partenaire ; SELL : le joueur vend au partenaire. */
    public enum Side {
        BUY, SELL
    }

    public enum Kind {
        VILLAGER, PEDDLER
    }

    /**
     * Partenaire PNJ : sorte, peuple, affinite du villageois envers le joueur (0 pour un Colporteur), facteur d'evenement (penurie x2 a
     * l'achat par le PNJ, surplus 0,6 a la vente), facteur de quota (services, evenement), bonus de doctrine (papier et encre x1,25...).
     */
    public record Partner(Kind kind, String people, int affinity, double eventFactor, double quotaFactor, double doctrineFactor, boolean founder) {

        /** Partenaire ordinaire (pas dans un village fonde par ce joueur). */
        public Partner(Kind kind, String people, int affinity, double eventFactor, double quotaFactor, double doctrineFactor) {
            this(kind, people, affinity, eventFactor, quotaFactor, doctrineFactor, false);
        }
    }

    /**
     * Devis : prix unitaire (centiemes), total (jetons), pourcentage applique par rapport a la base, quantite retenue (apres quota), reste du
     * quota du jour (-1 : sans quota), refus (cle de langue) ou null.
     */
    public record Quote(long unit, long total, int percent, int count, int quotaLeft, @Nullable String refusal) {

        public boolean ok() {
            return refusal == null && count > 0;
        }

        static Quote refuse(String key) {
            return new Quote(0, 0, 0, 0, 0, key);
        }
    }

    /** Remise d'affinite a l'achat, par palier (palier 2 : -3 %, 3 : -5 %, 4 : -7 %, 5 : -8 %). */
    public static double affinityDiscount(int affinity) {
        if (affinity >= 80) {
            return -0.08;
        }
        if (affinity >= 60) {
            return -0.07;
        }
        if (affinity >= 40) {
            return -0.05;
        }
        if (affinity >= 20) {
            return -0.03;
        }
        return 0;
    }

    /** Quota du jour d'un PNJ pour cet objet (avant ce qu'il a deja achete) : classe de quota x reglage x skill Commerce x services. */
    public static int quotaLimit(ServerPlayer player, ItemStack stack, double quotaFactor) {
        Values.Entry e = Values.entry(stack);
        if (e == null) {
            return 0;
        }
        double m = PiCommonConfig.get(RpConfig.QUOTA_MULTIPLIER, 1.0) * quotaFactor * (1 + Math.max(0, PioneerApi.modifier(player, "trade.quota")));
        return (int) Math.floor(Values.quota(e.quota()) * m);
    }

    /**
     * Devis d'un echange de {@code wanted} unites. {@code soldToday} = unites de cet objet deja achetees aujourd'hui par ce PNJ (quota, SELL
     * seulement).
     */
    public static Quote quote(ServerPlayer player, Partner partner, ItemStack stack, int wanted, Side side, int soldToday) {
        if (!EconomyApi.enabled()) {
            return Quote.refuse("economy.pioneers.disabled");
        }
        long base = Values.value(stack);
        if (base <= 0) {
            return Quote.refuse("economy.pioneers.no_value");
        }
        String who = partner.kind() == Kind.PEDDLER ? "peddler" : "villager";
        ReputationApi.Tier rep = ReputationApi.tier(player, partner.people());
        if (rep.refuse()) {
            return Quote.refuse("economy.pioneers.refused_reputation");
        }
        double pct = side == Side.BUY ? Values.partner(who, "sells", partner.kind() == Kind.PEDDLER ? 1.3 : 1.2)
              : Values.partner(who, "buys", partner.kind() == Kind.PEDDLER ? 0.6 : 0.5);
        if (side == Side.SELL) {
            pct *= partner.doctrineFactor();
        }
        //variations additionnees : reputation du peuple, affinite du villageois, Marchandage
        double mods;
        if (side == Side.BUY) {
            mods = rep.buy() + (partner.kind() == Kind.VILLAGER ? affinityDiscount(partner.affinity()) : 0) + PioneerApi.modifier(player, "trader.buy");
        } else {
            mods = rep.sell() + PioneerApi.modifier(player, "trader.sell");
        }
        double unit = base * pct * (1 + mods);
        if (side == Side.BUY) {
            unit = Math.max(unit, base * Values.setting("buy_min", Values.partner("cap", "buy_min", 0.85)));
        } else {
            unit = Math.min(unit, base * pct * Values.partner("cap", "sell_max", 1.2));
        }
        //evenement (hors plafonds)
        unit *= partner.eventFactor();
        //1.0.3-beta (lot 11b) : fondateur du village du villageois : -15 % a l'achat, +15 % a la vente (hors plafonds, comme l'evenement ;
        //au pire achat 0,85 x 0,85 = 72 % de la base contre vente 0,5 x 1,2 x 1,15 = 69 % : aucune boucle de gain)
        unit *= fr.pioneers.village.FoundingRules.priceFactor(partner.founder(), side == Side.BUY);
        long u = Math.max(1, Math.round(unit));
        int count = Math.max(0, wanted);
        int left = -1;
        if (side == Side.SELL) {
            int limit = quotaLimit(player, stack, partner.quotaFactor());
            left = Math.max(0, limit - soldToday);
            count = Math.min(count, left);
            if (left == 0) {
                return new Quote(u, 0, (int) Math.round(100.0 * u / base), 0, 0, "economy.pioneers.quota_reached");
            }
        }
        long total = u * count / 100;
        return new Quote(u, total, (int) Math.round(100.0 * u / base), count, left, null);
    }
}
