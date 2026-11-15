package fr.pioneers.society.clan.vote;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.ClanTreasury;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanLogic;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanNames;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.society.clan.treasury.ClanFinance;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.economy.EconomyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * 1.9 (MAJ RP, bloc 4) : effets et verifications des decisions des fondations (taxes, roles, exclusion, depense, regime, identite) et des
 * votes de gouvernement (elections, Conseil, revocation, motion de defiance, Revolution). Guerre et traites sont branches par leurs agents
 * ({@code ClanVotes.register("war", ...)}, {@code ClanGov.registerCheck("war", ...)}).
 */
public final class VoteEffects {

    private VoteEffects() {
    }

    public static void init() {
        // ------------------------------------------------------------ taxes
        ClanGov.registerCheck("taxes", (server, c, actor, d) -> {
            String kind = d.getString("kind");
            int cap = ClanFinance.cap(c, kind);
            int v = d.getInt("value");
            if (cap < 0) {
                return "clan.pioneers.error.invalid";
            }
            if (c.solo && !"market".equals(kind)) {
                return "clan.pioneers.error.solo";
            }
            return v < 0 || v > cap ? "clan.pioneers.error.tax_cap" : null;
        });
        ClanVotes.register("taxes", (server, d) -> {
            Clan c = d.clan();
            String kind = d.data().getString("kind");
            int v = d.data().getInt("value");
            int cap = ClanFinance.cap(c, kind);
            if (c == null || cap < 0 || v < 0 || v > cap) {
                return false;
            }
            switch (kind) {
                case "activity" -> c.activityTax = v;
                case "dues" -> c.dues = v;
                default -> c.marketTax = v;
            }
            c.log(PioneerApi.day(server), "clan.pioneers.log.tax_set", "#clan.pioneers.tax." + kind, String.valueOf(v));
            ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.tax_now",
                  Component.translatable("clan.pioneers.tax." + kind), v)));
            ClanAudit.write(server, "tax", c, kind + " = " + v);
            return true;
        });
        // ------------------------------------------------------------ roles
        ClanGov.registerCheck("roles", (server, c, actor, d) -> ClanRoles.check(c, d, ClanDefs.get(), actor == null ? null : actor.getUUID()));
        ClanVotes.register("roles", (server, d) -> {
            Clan c = d.clan();
            String err = c == null ? "x" : ClanRoles.apply(c, d.data(), ClanDefs.get());
            if (err != null) {
                return false;
            }
            c.log(PioneerApi.day(server), "clan.pioneers.log.roles", "#clan.pioneers.roles.op." + d.data().getString("op"), d.data().getString("role"));
            return true;
        });
        // ------------------------------------------------------------ exclusion
        ClanGov.registerCheck("kick", (server, c, actor, d) -> {
            if (!d.hasUUID("target") || c.member(d.getUUID("target")) == null) {
                return "clan.pioneers.error.not_member";
            }
            UUID t = d.getUUID("target");
            if (t.equals(c.representative) || actor != null && (actor.getUUID().equals(t) || !ClanRoles.above(c, actor.getUUID(),
                  ClanRoles.rank(c, t)))) {
                return "clan.pioneers.error.rank";
            }
            return null;
        });
        ClanVotes.register("kick", (server, d) -> d.clan() != null && d.data().hasUUID("target") && ClanCore.kick(server, d.clan(),
              d.data().getUUID("target"), d.author()));
        // ------------------------------------------------------------ depense
        ClanGov.registerCheck("spend", (server, c, actor, d) -> {
            long a = d.getLong("amount");
            if (!d.hasUUID("target") || c.member(d.getUUID("target")) == null) {
                return "clan.pioneers.error.not_member";
            }
            if (a <= 0) {
                return "clan.pioneers.error.amount";
            }
            if (c.frozen) {
                return "clan.pioneers.error.frozen";
            }
            return a > c.treasury ? "clan.pioneers.error.treasury_low" : null;
        });
        ClanVotes.register("spend", (server, d) -> {
            Clan c = d.clan();
            String reason = d.data().getString("reason");
            return c != null && d.data().hasUUID("target") && ClanFinance.spend(server, c, d.data().getUUID("target"), d.data().getLong("amount"),
                  reason.isEmpty() ? "#clan.pioneers.reason.spend" : reason, d.author() == null ? "#clan.pioneers.vote.system"
                        : ClanMessages.character(server, c, d.author()));
        });
        // ------------------------------------------------------------ regime
        ClanGov.registerCheck("regime", (server, c, actor, d) -> {
            String r = d.getString("regime");
            ClanDefs.Rules rules = ClanCore.rules();
            if (!ClanDefs.REGIMES.contains(r) || rules == null) {
                return "clan.pioneers.error.regime";
            }
            if (r.equals(c.regime) || !c.pendingRegime.isEmpty()) {
                return "clan.pioneers.error.regime_same";
            }
            if (c.regimeChanged > 0 && ClanClock.now() - c.regimeChanged < rules.regimeCooldownDays() * ClanDefs.DAY) {
                return "clan.pioneers.error.regime_cooldown";
            }
            return EconomyApi.enabled() && (c.treasury < rules.regimeCost() || c.frozen) ? "clan.pioneers.error.treasury_low" : null;
        });
        ClanVotes.register("regime", (server, d) -> {
            Clan c = d.clan();
            ClanDefs.Rules rules = ClanCore.rules();
            String r = d.data().getString("regime");
            if (c == null || rules == null || !ClanDefs.REGIMES.contains(r) || r.equals(c.regime) || !c.pendingRegime.isEmpty()) {
                return false;
            }
            if (EconomyApi.enabled() && !ClanTreasury.debit(server, c, rules.regimeCost(), "#clan.pioneers.reason.regime", "#clan.pioneers.vote.system")) {
                return false;
            }
            long now = ClanClock.now();
            c.pendingRegime = r;
            c.regimeAt = now + rules.regimeDelayDays() * ClanDefs.DAY;
            c.log(PioneerApi.day(server), "clan.pioneers.log.regime_pending", "#clan.pioneers.regime." + r, String.valueOf(rules.regimeDelayDays()));
            ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.regime_pending",
                  Component.translatable("clan.pioneers.regime." + r), rules.regimeDelayDays())));
            ClanAudit.write(server, "regime", c, "changement vote vers " + r + ", effectif dans " + rules.regimeDelayDays() + " jours");
            return true;
        });
        // ------------------------------------------------------------ identite
        ClanGov.registerCheck("identity", (server, c, actor, d) -> identityError(server, c, d));
        ClanVotes.register("identity", (server, d) -> {
            Clan c = d.clan();
            if (c == null || identityError(server, c, d.data()) != null) {
                return false;
            }
            String f = d.data().getString("field"), v = d.data().getString("value");
            switch (f) {
                case "name" -> c.name = ClanNames.cleanName(v);
                case "tag" -> c.tag = ClanNames.cleanTag(v);
                case "motto" -> c.motto = ClanNames.cleanMotto(v);
                default -> c.color = d.data().getInt("color");
            }
            c.log(PioneerApi.day(server), "clan.pioneers.log.identity", "#clan.pioneers.identity." + f, "color".equals(f) ? "#color.minecraft."
                  + net.minecraft.world.item.DyeColor.byId(c.color).getName() : v);
            ClanAudit.write(server, "identity", c, f + " = " + ("color".equals(f) ? String.valueOf(c.color) : v));
            if ("color".equals(f)) {
                ClanCore.tabards(server, c); //1.9 (MAJ RP, bloc 4) : les tabards prennent la nouvelle couleur
            }
            return true;
        });
        // ------------------------------------------------------------ elections et contre-pouvoirs
        ClanVotes.register("election", (server, d) -> elected(server, d, "clan.pioneers.reason.elected"));
        ClanVotes.register("council_choice", (server, d) -> elected(server, d, "clan.pioneers.reason.council"));
        ClanVotes.register("council_election", (server, d) -> {
            Clan c = d.clan();
            ClanDefs.Constitution k = c == null ? null : ClanDefs.get().regime(c.regime);
            if (k == null || !k.hasCouncil()) {
                return false;
            }
            List<UUID> seats = new ArrayList<>();
            for (UUID u : d.winners()) {
                if (c.member(u) != null) {
                    seats.add(u);
                }
            }
            //sieges vacants (trop peu de candidats elus) : les plus anciens membres actifs, pour que le Conseil tienne
            int want = Math.min(c.members.size(), Math.max(k.councilMin(), Math.min(seats.size(), k.councilSeats(c.members.size()))));
            for (UUID u : ClanLogic.oldestFirst(c, null)) {
                if (seats.size() >= want) {
                    break;
                }
                if (!seats.contains(u)) {
                    seats.add(u);
                }
            }
            c.council.clear();
            c.council.addAll(seats);
            List<String> names = new ArrayList<>();
            seats.forEach(u -> names.add(ClanMessages.character(server, c, u)));
            c.log(PioneerApi.day(server), "clan.pioneers.log.council", String.join(", ", names));
            if ("council".equals(k.repMode()) && ClanVotes.openOfType(c.proposals, "council_choice") == null) {
                ClanGov.openElection(server, c, "council_choice", false);
            }
            return true;
        });
        ClanVotes.register("recall", (server, d) -> removed(server, d, "election"));
        ClanVotes.register("no_confidence", (server, d) -> removed(server, d, "council_choice"));
        ClanVotes.register("revolution", (server, d) -> {
            Clan c = d.clan();
            if (c == null || c.solo) {
                return false;
            }
            ClanGov.applyRegime(server, c, ClanDefs.DEMOCRACY, ClanClock.now(), "clan.pioneers.reason.revolution");
            return true;
        });
    }

    private static boolean elected(MinecraftServer server, ClanVotes.Decision d, String reason) {
        Clan c = d.clan();
        if (c == null || d.winners().isEmpty() || c.member(d.winners().get(0)) == null) {
            return false;
        }
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        ClanCore.setRepresentative(server, c, d.winners().get(0), reason);
        if (k != null) {
            c.extra.putLong(ClanGov.TERM, ClanClock.now() + Math.max(1, k.termDays()) * ClanDefs.DAY);
        }
        return true;
    }

    /** Revocation ou defiance acquise : le Representant vise perd son titre (interim), puis election. */
    private static boolean removed(MinecraftServer server, ClanVotes.Decision d, String election) {
        Clan c = d.clan();
        if (c == null || !d.data().hasUUID("target") || !d.data().getUUID("target").equals(c.representative)) {
            return false;
        }
        long now = ClanClock.now();
        UUID out = c.representative;
        UUID interim = "election".equals(election) ? ClanLogic.highest(c, now, ClanCore.activeMs(), out)
              : firstCouncillor(c, now, out);
        if (interim != null) {
            ClanCore.setRepresentative(server, c, interim, "clan.pioneers.reason.removed");
        }
        ClanGov.openElection(server, c, election, true);
        ClanData.get(server).changed();
        return true;
    }

    private static UUID firstCouncillor(Clan c, long now, UUID except) {
        for (UUID u : ClanLogic.oldestFirst(c, except)) {
            if (c.council.contains(u) && ClanLogic.active(c, u, now, ClanCore.activeMs())) {
                return u;
            }
        }
        return ClanLogic.highest(c, now, ClanCore.activeMs(), except);
    }

    private static String identityError(MinecraftServer server, Clan c, CompoundTag d) {
        ClanDefs.Rules r = ClanCore.rules();
        if (r == null) {
            return "clan.pioneers.error.defs";
        }
        String f = d.getString("field"), v = d.getString("value");
        return switch (f) {
            case "name" -> {
                String n = ClanNames.cleanName(v);
                String e = ClanNames.checkName(n, r);
                yield e != null ? e : ClanCore.nameTaken(server, n, c.id) ? "clan.pioneers.error.name_taken" : null;
            }
            case "tag" -> ClanNames.checkTag(ClanNames.cleanTag(v), r);
            case "motto" -> ClanNames.checkMotto(ClanNames.cleanMotto(v), r);
            case "color" -> ClanNames.checkColor(d.getInt("color"));
            default -> "clan.pioneers.error.invalid";
        };
    }
}
