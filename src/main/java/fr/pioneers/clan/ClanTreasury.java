package fr.pioneers.clan;

import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.economy.EconomyApi;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : le tresor d'un clan, compte en jetons d'un type nouveau (non joueur), et l'impot d'activite preleve A LA SOURCE.
 * Le tresor ne fait que deplacer des jetons (jamais de creation) : {@code check_arbitrage} passe inchange. Chaque operation est inscrite au
 * journal du clan. Toujours appele sur le fil du serveur, verifications avant modifications.
 */
public final class ClanTreasury {

    /** 1.9 (MAJ RP, bloc 4) : recettes du clan depuis le dernier reglement de la contribution federale (cle de {@code Clan.extra}). */
    public static final String FED_BASE = "fed.base";

    private ClanTreasury() {
    }

    /** 1.9 (MAJ RP, bloc 4) : compte une recette dans la base de la contribution federale (seulement pour un clan d'une faction). */
    public static void income(Clan c, long amount) {
        if (c.faction != null && amount > 0) {
            c.extra.putLong(FED_BASE, c.extra.getLong(FED_BASE) + amount);
        }
    }

    /** Verse au tresor (dons, cotisations, impots, tribut). Refuse au-dela du plafond de solde. */
    public static boolean credit(MinecraftServer server, Clan clan, long amount, String reason, String who) {
        if (amount < 0 || clan.treasury + amount > EconomyApi.MAX) {
            return false;
        }
        clan.treasury += amount;
        income(clan, amount);
        clan.log(PioneerApi.day(server), "clan.pioneers.log.treasury_in", reason, String.valueOf(amount), who);
        ClanData.get(server).changed();
        return true;
    }

    /** Preleve au tresor si le solde le couvre (et que le tresor n'est pas gele). */
    public static boolean debit(MinecraftServer server, Clan clan, long amount, String reason, String who) {
        if (amount < 0 || clan.frozen || clan.treasury < amount) {
            return false;
        }
        clan.treasury -= amount;
        clan.log(PioneerApi.day(server), "clan.pioneers.log.treasury_out", reason, String.valueOf(amount), who);
        ClanData.get(server).changed();
        return true;
    }

    /**
     * Taxe de marche (%) d'un etal pose en {@code pos} (0 a 15 %, en plus de la taxe d'etal du jeu) : non nulle seulement dans le territoire
     * d'un clan, a moins de 32 blocs d'un de ses Marches (fonction de Sceau). Appelee par les etals ; voir {@code ClanFinance}.
     */
    public static int marketTaxFor(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return fr.pioneers.clan.treasury.ClanFinance.marketTaxFor(level, pos);
    }

    /** Idem, nulle pour un acheteur dont le clan a un accord commercial avec le clan proprietaire. */
    public static int marketTaxFor(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, @Nullable java.util.UUID buyer) {
        return fr.pioneers.clan.treasury.ClanFinance.marketTaxFor(level, pos, buyer);
    }

    /** Verse au tresor du clan proprietaire la taxe de marche deja prelevee sur une vente d'etal. */
    public static boolean collectMarketTax(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, long amount, String who) {
        return fr.pioneers.clan.treasury.ClanFinance.collectMarketTax(level, pos, amount, who);
    }

    /** Taux d'impot d'activite du clan de ce joueur (0 sans clan, clan solo compris). */
    public static int taxRate(ServerPlayer player) {
        if (!ClanApi.enabled()) {
            return 0;
        }
        Clan c = ClanData.get(player.getServer()).clanOf(player.getUUID());
        return c == null || c.solo ? 0 : Math.max(0, Math.min(100, c.activityTax));
    }

    /** Part de l'impot sur un gain (affichee avant la vente). */
    public static long taxOn(ServerPlayer player, long gross) {
        return gross * taxRate(player) / 100;
    }

    /**
     * Gain aupres d'un PNJ, reparti dans la MEME section entre le joueur et le tresor de son clan (impot d'activite). Tout est verifie avant
     * d'ecrire : plafonds des deux comptes. Renvoie le resultat du credit du joueur.
     */
    public static EconomyApi.Result creditWithTax(ServerPlayer player, long gross, EconomyApi.Entry entry) {
        long tax = taxOn(player, gross);
        @Nullable Clan c = tax > 0 ? ClanData.get(player.getServer()).clanOf(player.getUUID()) : null;
        if (c == null || tax <= 0) {
            return EconomyApi.credit(player, gross, entry);
        }
        EconomyApi.Result r = EconomyApi.canCredit(player, gross - tax);
        if (!r.ok() || c.treasury + tax > EconomyApi.MAX) {
            return r.ok() ? EconomyApi.Result.CAP : r;
        }
        EconomyApi.credit(player, gross - tax, entry);
        c.treasury += tax;
        income(c, tax);
        long day = PioneerApi.day(player.getServer());
        //meme section : l'impot du jour sert de base au dividende de la Cooperative (simple compteur)
        fr.pioneers.clan.treasury.ClanFinance.taxed(c, tax, day);
        c.log(day, "clan.pioneers.log.tax", EconomyApi.name(player), String.valueOf(tax));
        ClanData.get(player.getServer()).changed();
        return EconomyApi.Result.OK;
    }
}
