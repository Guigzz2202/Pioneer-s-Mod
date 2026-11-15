package fr.pioneers.society.clan.treasury;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.ClanTreasury;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanLogic;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.economy.EconomyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, bloc 4) : operations du tresor d'un clan. Dons libres ; retraits plafonnes par role et par jour de jeu (au-dela, ou au-dessus
 * de 25 % du tresor : proposition de depense selon le regime) ; primes aux membres (meme plafond) ; taxes fixees selon le regime sous ses
 * plafonds ; cotisation par jour de jeu prelevee sur le solde des membres CONNECTES ce jour-la (jamais de dette) ; dividende egal de la
 * Cooperative (impot d'activite du jour redistribue aux membres actifs) ; taxe de marche des etals poses dans le territoire pres d'un Marche.
 * Toute operation verifie tout avant d'ecrire, ne cree jamais de jetons, est inscrite au journal du clan et a l'audit.
 */
public final class ClanFinance {

    /** Cles de {@link Clan#extra} : impot d'activite du jour (dividende de la Cooperative). */
    public static final String POOL = "fin.pool", POOL_DAY = "fin.poolDay";

    public record Done(@Nullable String error, boolean proposed, long amount) {

        static Done refused(String key) {
            return new Done(key, false, 0);
        }
    }

    private ClanFinance() {
    }

    // ------------------------------------------------------------------ calculs purs (banc)
    /** Cotisation prelevee : au plus le solde (jamais de dette), au plus la place du tresor. */
    public static long duesTaken(long balance, long dues, long treasury) {
        return Math.max(0, Math.min(Math.min(balance, dues), EconomyApi.MAX - treasury));
    }

    /** Ce qu'un role peut encore retirer aujourd'hui (-1 : sans plafond). */
    public static long roomToday(long cap, long usedToday) {
        return cap < 0 ? Long.MAX_VALUE : Math.max(0, cap - usedToday);
    }

    /** Part d'un dividende egal et reste (gardes au tresor). */
    public static long[] dividend(long pool, int members) {
        if (members <= 0 || pool <= 0) {
            return new long[]{0, Math.max(0, pool)};
        }
        long share = pool / members;
        return new long[]{share, pool - share * members};
    }

    // ------------------------------------------------------------------ dons
    @Nullable
    public static String donate(ServerPlayer p, long amount) {
        if (!EconomyApi.enabled()) {
            return "clan.pioneers.error.economy";
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (amount <= 0) {
            return "clan.pioneers.error.amount";
        }
        if (EconomyApi.balance(p) < amount) {
            return "clan.pioneers.error.balance";
        }
        if (c.treasury + amount > EconomyApi.MAX) {
            return "clan.pioneers.error.treasury_cap";
        }
        if (!EconomyApi.debit(p, amount, EconomyApi.Entry.of("clan_donation", name(p.getServer(), c))).ok()) {
            return "clan.pioneers.error.balance";
        }
        ClanTreasury.credit(p.getServer(), c, amount, "#clan.pioneers.reason.donation", ClanMessages.character(p));
        ClanAudit.write(p.getServer(), "donate", c, p.getGameProfile().getName() + " : " + amount);
        return null;
    }

    // ------------------------------------------------------------------ retraits, primes, depenses
    /** Retrait pour soi : dans le plafond du role et sous 25 % du tresor, sinon proposition de depense (selon le regime). */
    public static Done withdraw(ServerPlayer p, long amount) {
        return pay(p, p.getUUID(), amount, Permission.WITHDRAW, "#clan.pioneers.reason.withdraw");
    }

    /** Prime a un membre : permission des primes, meme plafond journalier que les retraits de celui qui verse. */
    public static Done bonus(ServerPlayer p, UUID target, long amount) {
        return pay(p, target, amount, Permission.BONUSES, "#clan.pioneers.reason.bonus");
    }

    private static Done pay(ServerPlayer p, UUID target, long amount, Permission perm, String reason) {
        MinecraftServer server = p.getServer();
        if (!EconomyApi.enabled()) {
            return Done.refused("clan.pioneers.error.economy");
        }
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (c == null) {
            return Done.refused("clan.pioneers.error.no_clan");
        }
        if (amount <= 0) {
            return Done.refused("clan.pioneers.error.amount");
        }
        if (c.member(target) == null) {
            return Done.refused("clan.pioneers.error.not_member");
        }
        if (!ClanRoles.has(c, p.getUUID(), perm)) {
            return Done.refused("clan.pioneers.error.permission");
        }
        if (c.frozen) {
            return Done.refused("clan.pioneers.error.frozen");
        }
        if (amount > c.treasury) {
            return Done.refused("clan.pioneers.error.treasury_low");
        }
        Clan.Member m = c.member(p.getUUID());
        Clan.Role role = c.roles.get(m.role);
        long day = PioneerApi.day(server);
        long used = m.withdrawDay == day ? m.withdrawnToday : 0;
        long cap = role == null ? 0 : role.withdrawCap;
        ClanDefs.Rules rules = ClanCore.rules();
        boolean large = rules != null && amount > rules.spendShare() * c.treasury;
        if (amount > roomToday(cap, used) || large) {
            //au-dela du plafond, ou plus de 25 % du tresor : decision « spend » (Representant ou vote selon le regime)
            CompoundTag d = new CompoundTag();
            d.putUUID("target", target);
            d.putLong("amount", amount);
            d.putString("reason", reason);
            ClanGov.Result r = ClanGov.decide(p, c, "spend", d, "");
            if (!r.ok()) {
                return Done.refused("clan.pioneers.error.representative_only".equals(r.error()) ? "clan.pioneers.error.spend_large" : r.error());
            }
            return new Done(null, r.outcome() == ClanGov.Outcome.PROPOSED, amount);
        }
        if (!transfer(server, c, target, amount, reason, ClanMessages.character(p))) {
            return Done.refused("clan.pioneers.error.balance_cap");
        }
        m.withdrawDay = day;
        m.withdrawnToday = used + amount;
        return new Done(null, false, amount);
    }

    /** Effet d'une depense decidee : du tresor vers un membre (connecte : tout de suite ; hors ligne : a sa prochaine connexion). */
    public static boolean spend(MinecraftServer server, Clan c, UUID target, long amount, String reason, String who) {
        if (amount <= 0 || c.frozen || c.treasury < amount || c.member(target) == null) {
            return false;
        }
        return transfer(server, c, target, amount, reason, who);
    }

    private static boolean transfer(MinecraftServer server, Clan c, UUID target, long amount, String reason, String who) {
        ServerPlayer t = server.getPlayerList().getPlayer(target);
        if (t != null && !EconomyApi.canCredit(t, amount).ok()) {
            return false;
        }
        if (!ClanTreasury.debit(server, c, amount, reason, who)) {
            return false;
        }
        EconomyApi.Entry e = EconomyApi.Entry.of("clan_payment", name(server, c));
        if (t != null) {
            EconomyApi.credit(t, amount, e);
        } else {
            EconomyApi.creditLater(server, target, amount, e);
        }
        ClanAudit.write(server, "withdraw", c, amount + " -> " + target + " (" + reason + ", par " + who + ")");
        ClanDefs.Rules rules = ClanCore.rules();
        if (rules != null && amount >= rules.alertMin() && amount >= rules.alertShare() * (c.treasury + amount)) {
            ClanAudit.alert(server, c, "clan.pioneers.alert.withdraw", name(server, c), amount);
        }
        return true;
    }

    // ------------------------------------------------------------------ taxes
    /** Fixe un impot ({@code kind} : activity, dues, market), selon le regime et sous ses plafonds. */
    public static ClanGov.Result setTax(ServerPlayer p, Clan c, String kind, int value) {
        CompoundTag d = new CompoundTag();
        d.putString("kind", kind);
        d.putInt("value", value);
        return ClanGov.decide(p, c, "taxes", d, "");
    }

    /** Plafond d'un impot dans le regime du clan. */
    public static int cap(Clan c, String kind) {
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        ClanDefs.Rules r = ClanCore.rules();
        return switch (kind) {
            case "activity" -> k == null ? 0 : k.activityTaxMax();
            case "dues" -> k == null ? 0 : k.duesMax();
            case "market" -> r == null ? 0 : r.marketTaxMax();
            default -> -1;
        };
    }

    // ------------------------------------------------------------------ cotisations, dividende
    /** Cotisation du jour d'un membre connecte (une fois par jour de jeu, jamais de dette). */
    public static void collectDues(MinecraftServer server, Clan c, ServerPlayer p, long day) {
        Clan.Member m = c.member(p.getUUID());
        if (m == null || c.dues <= 0 || c.solo || m.duesDay == day || !EconomyApi.enabled()) {
            return;
        }
        m.duesDay = day;
        long take = duesTaken(EconomyApi.balance(p), c.dues, c.treasury);
        if (take > 0 && EconomyApi.debit(p, take, EconomyApi.Entry.of("clan_dues", name(server, c))).ok()) {
            c.treasury += take;
            //1.9 (MAJ RP, bloc 4) : base de la contribution federale
            ClanTreasury.income(c, take);
            c.log(day, "clan.pioneers.log.dues", ClanMessages.character(p), String.valueOf(take));
        }
        if (take < c.dues) {
            ClanMessages.tell(p, "clan.pioneers.dues_partial", take, c.dues);
        }
        ClanData.get(server).changed();
    }

    /** Impot d'activite verse au tresor (appele par {@link ClanTreasury#creditWithTax}, dans la meme section) : base du dividende. */
    public static void taxed(Clan c, long tax, long day) {
        if (c.extra.getLong(POOL_DAY) != day) {
            c.extra.putLong(POOL_DAY, day);
            c.extra.putLong(POOL, 0);
        }
        c.extra.putLong(POOL, c.extra.getLong(POOL) + tax);
    }

    /** Nouveau jour de jeu : cotisations des membres connectes, dividende de la Cooperative (impot de la veille). */
    public static void newDay(MinecraftServer server, long day) {
        ClanData data = ClanData.get(server);
        for (Clan c : new ArrayList<>(data.clans())) {
            if (c.members.isEmpty() || c.solo) {
                continue;
            }
            for (UUID u : new ArrayList<>(c.members.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) {
                    collectDues(server, c, p, day);
                }
            }
            ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
            if (k != null && k.dividend() && c.extra.getLong(POOL_DAY) < day && c.extra.getLong(POOL) > 0) {
                payDividend(server, c, day);
            }
        }
    }

    private static void payDividend(MinecraftServer server, Clan c, long day) {
        long pool = Math.min(c.extra.getLong(POOL), c.frozen ? 0 : c.treasury);
        c.extra.putLong(POOL, 0);
        c.extra.putLong(POOL_DAY, day);
        long now = ClanClock.now();
        List<UUID> active = new ArrayList<>();
        for (UUID u : c.members.keySet()) {
            if (ClanLogic.active(c, u, now, ClanCore.activeMs())) {
                active.add(u);
            }
        }
        long[] sh = dividend(pool, active.size());
        if (sh[0] <= 0) {
            return;
        }
        for (UUID u : active) {
            spend(server, c, u, sh[0], "#clan.pioneers.reason.dividend", "#clan.pioneers.vote.system");
        }
        c.log(day, "clan.pioneers.log.dividend", String.valueOf(sh[0]), String.valueOf(active.size()));
    }

    // ------------------------------------------------------------------ taxe de marche
    /** Taxe de marche (%) d'un etal pose en {@code pos} : 0 hors territoire ou loin d'un Marche du clan proprietaire. */
    public static int marketTaxFor(Level level, BlockPos pos) {
        return marketTaxFor(level, pos, null);
    }

    /** Idem, nulle pour un acheteur dont le clan a un accord commercial (traite « trade ») avec le clan proprietaire. */
    public static int marketTaxFor(Level level, BlockPos pos, @Nullable UUID buyer) {
        Clan c = marketOwner(level, pos);
        if (c == null || c.marketTax <= 0) {
            return 0;
        }
        if (buyer != null && level.getServer() != null) {
            //1.9 (MAJ RP, bloc 4) : lecture unique des traites (fin propre a chaque clause : tradeEnd)
            if (fr.pioneers.society.clan.war.ClanDiplomacy.tradeAgreement(c, ClanData.get(level.getServer()).clanOf(buyer))) {
                return 0;
            }
        }
        return Math.max(0, Math.min(cap(c, "market"), c.marketTax));
    }

    /** Verse la taxe de marche prelevee sur une vente (deja retiree au vendeur ou a l'acheteur par l'etal) au tresor du clan. */
    public static boolean collectMarketTax(Level level, BlockPos pos, long amount, String who) {
        Clan c = marketOwner(level, pos);
        return c != null && amount > 0 && level.getServer() != null && ClanTreasury.credit(level.getServer(), c, amount,
              "#clan.pioneers.reason.market", who);
    }

    /** Clan proprietaire du chunk, s'il a un Marche (fonction de Sceau « market ») assez pres. */
    @Nullable
    static Clan marketOwner(Level level, BlockPos pos) {
        if (level.isClientSide || level.getServer() == null || !ClanApi.enabled()) {
            return null;
        }
        String dim = ClanApi.dim(level);
        UUID id = ClaimIndex.owner(dim, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        Clan c = id == null ? null : ClanData.get(level.getServer()).clan(id);
        if (c == null || c.members.isEmpty()) {
            return null;
        }
        ClanDefs.Rules r = ClanCore.rules();
        int radius = r == null ? 32 : r.marketRadius();
        for (Map.Entry<String, String> f : c.functions.entrySet()) {
            if (!"market".equals(f.getValue())) {
                continue;
            }
            BlockPos at = functionPos(f.getKey(), dim);
            if (at != null && at.distSqr(pos) <= (double) radius * radius) {
                return c;
            }
        }
        return null;
    }

    /** Position d'une fonction de Sceau (« dimension|position » : position en long, ou « x,y,z »). */
    @Nullable
    static BlockPos functionPos(String key, String dim) {
        int bar = key.lastIndexOf('|');
        if (bar < 0 || !key.substring(0, bar).equals(dim)) {
            return null;
        }
        String p = key.substring(bar + 1).trim();
        try {
            return BlockPos.of(Long.parseLong(p));
        } catch (NumberFormatException e) {
            String[] xyz = p.split("[,; ]+");
            if (xyz.length == 3) {
                try {
                    return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    /** Nom du clan pour les journaux des comptes (le nom du Solitaire pour un clan solo sans nom). */
    static String name(MinecraftServer server, Clan c) {
        return c.name != null && !c.name.isBlank() ? c.name : ClanMessages.character(server, c, c.representative);
    }

    /** Message au joueur pour un resultat de retrait ou de prime. */
    public static Component describe(Done d) {
        if (d.error() != null) {
            return Component.translatable(d.error());
        }
        return Component.translatable(d.proposed() ? "clan.pioneers.spend_proposed" : "clan.pioneers.paid", d.amount());
    }
}
