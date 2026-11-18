package fr.pioneers.society.clan.vote;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanEvent;
import fr.pioneers.society.clan.core.ClanLogic;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * 1.9 (MAJ RP, bloc 4) : « qui tranche quoi ». Chaque decision (taxes, roles, exclusion, depense, guerre, traite, regime, identite) suit la
 * constitution du regime : le Representant l'applique tout de suite, ou un vote s'ouvre (membres ou Conseil, a la majorite du regime). Le
 * meme effet (registre {@link ClanVotes#register}) sert dans les deux cas. S'y ajoutent les elections (Democratie : Representant pour 14 jours ;
 * Conseil : sieges pour 28 jours puis le Conseil elit le Representant ; Cooperative : porte-parole tournant), les contre-pouvoirs (revocation,
 * motion de defiance, Revolution de l'Empire) et le changement de regime (cout, delai de 7 jours, 30 jours avant le suivant).
 */
public final class ClanGov {

    public enum Outcome {
        APPLIED, PROPOSED, REFUSED
    }

    public record Result(Outcome outcome, @Nullable String error, @Nullable String proposal) {

        static Result refused(String key) {
            return new Result(Outcome.REFUSED, key, null);
        }

        public boolean ok() {
            return outcome != Outcome.REFUSED;
        }
    }

    /** Verification d'une decision avant de l'appliquer ou de la mettre au vote (cle de langue du refus, ou {@code null}). */
    @FunctionalInterface
    public interface Check {

        @Nullable
        String check(MinecraftServer server, Clan clan, @Nullable ServerPlayer actor, CompoundTag data);
    }

    /** Types de vote de gouvernement (ni decision du tableau, ni effet externe). */
    public static final List<String> GOVERNANCE = List.of("election", "council_election", "council_choice", "recall", "no_confidence", "revolution");
    public static final String TERM = "gov.term", COUNCIL_TERM = "gov.cterm", COUNTER = "gov.counter";

    private static final Map<String, Check> CHECKS = new HashMap<>();

    private ClanGov() {
    }

    /** Branche la verification prealable d'un type de decision (les agents de la guerre et de la diplomatie y ajoutent la leur). */
    public static synchronized void registerCheck(String type, Check check) {
        CHECKS.put(type, check);
    }

    /** Ligne du tableau « qui tranche quoi » d'un type de proposition, ou {@code null}. */
    @Nullable
    public static String decisionOf(String type) {
        return ClanDefs.DECISIONS.contains(type) ? type : null;
    }

    /** Permission qui permet d'engager la decision (proposer, ou decider quand on est le Representant). */
    public static Permission initiator(String type) {
        return switch (type) {
            case "taxes" -> Permission.SET_TAXES;
            case "roles" -> Permission.EDIT_ROLES;
            case "kick" -> Permission.KICK;
            case "spend" -> Permission.WITHDRAW;
            case "war" -> Permission.DECLARE_WAR;
            case "treaty" -> Permission.DIPLOMACY;
            case "identity" -> Permission.EDIT_IDENTITY;
            default -> Permission.PROPOSE;
        };
    }

    /** 1.0.4-beta (lot 8) : decisions du Representant qu'un role peut exercer par delegation (tout sauf le changement de regime). */
    public static boolean delegable(String type) {
        return !"regime".equals(type);
    }

    /** Regle effective : le clan solo decide comme un Empire. */
    @Nullable
    public static ClanDefs.Rule rule(Clan c, String decision) {
        ClanDefs.Constitution k = ClanDefs.get().regime(c.solo ? ClanDefs.SOLO : c.regime);
        return k == null ? null : k.rule(decision);
    }

    /**
     * Engage une decision : verification, puis application directe (Representant, si le regime la lui donne) ou ouverture d'un vote. Point
     * d'entree commun des commandes, du reseau et des autres agents (guerre : type « war », traites : « treaty »).
     */
    public static Result decide(ServerPlayer actor, Clan clan, String type, CompoundTag data, String text) {
        MinecraftServer server = actor.getServer();
        if (!ClanCore.enabled()) {
            return Result.refused("clan.pioneers.error.disabled");
        }
        UUID u = actor.getUUID();
        if (clan.member(u) == null) {
            return Result.refused("clan.pioneers.error.not_member");
        }
        String decision = decisionOf(type);
        ClanDefs.Rule r = decision == null ? null : rule(clan, decision);
        if (r == null) {
            return Result.refused("clan.pioneers.error.invalid");
        }
        boolean rep = u.equals(clan.representative);
        //1.0.4-beta (lot 8, Guillaume : « il manque les perms semi admin, notamment le budget et le territoire ») : une decision que le regime
        //donne au Representant seul est DELEGUEE aux roles qui portent sa permission (Bras droit, Tresorier...) : ils l'appliquent
        //directement en son nom ; jamais le changement de regime (ni la dissolution, ni le titre de Representant : hors de ces decisions)
        boolean delegated = !rep && delegable(type) && ClanRoles.has(clan, u, initiator(type));
        if (r.direct() || clan.solo) {
            if (!rep && !delegated) {
                return Result.refused(delegable(type) ? "clan.pioneers.error.permission" : "clan.pioneers.error.representative_only");
            }
        } else if (!ClanRoles.has(clan, u, initiator(type))) {
            return Result.refused("clan.pioneers.error.permission");
        }
        Check check = CHECKS.get(type);
        String err = check == null ? null : check.check(server, clan, actor, data);
        if (err != null) {
            return Result.refused(err);
        }
        if (r.direct() || clan.solo) {
            if (ClanVotes.effect(type) == null) {
                return Result.refused("clan.pioneers.error.invalid");
            }
            return ClanVotes.applyDirect(server, type, clan, null, u, data) ? new Result(Outcome.APPLIED, null, null)
                  : Result.refused("clan.pioneers.error.failed");
        }
        ClanVotes.Spec s = new ClanVotes.Spec(type, ClanVotes.Scope.CLAN, clan.id);
        s.majority = r.majority();
        s.electorateBase = r.electorateBase();
        s.by = r.by().id();
        electorate(clan, r.by(), s);
        s.data = data.copy();
        s.text = text == null ? "" : text;
        s.author = u;
        s.authorName = ClanMessages.character(actor);
        ClanVotes.Opened o = ClanVotes.open(server, s);
        return o.error() != null ? Result.refused(o.error()) : new Result(Outcome.PROPOSED, null, o.id());
    }

    /** Corps electoral : tous les membres, ou le Conseil (tous les membres tant qu'il n'est pas forme). */
    private static void electorate(Clan clan, ClanDefs.Decider by, ClanVotes.Spec s) {
        String g = clan.id.toString();
        if (by == ClanDefs.Decider.COUNCIL && !clan.council.isEmpty()) {
            clan.council.forEach(u -> s.electorate.put(u, g));
        } else {
            clan.members.keySet().forEach(u -> s.electorate.put(u, g));
        }
    }

    // ------------------------------------------------------------------ elections
    /**
     * Ouvre une election ({@code election} : les membres elisent le Representant ; {@code council_election} : les membres elisent les sieges
     * du Conseil ; {@code council_choice} : le Conseil elit le Representant). Bulletin secret. Reporte l'echeance du mandat pour ne pas la
     * rouvrir en boucle (le sortant reste en place si le quorum manque).
     */
    @Nullable
    public static String openElection(MinecraftServer server, Clan c, String type, boolean emergency) {
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        if (k == null || c.solo || c.members.isEmpty() || ClanVotes.openOfType(c.proposals, type) != null) {
            return "clan.pioneers.error.vote_open";
        }
        long now = ClanClock.now();
        ClanVotes.Spec s = new ClanVotes.Spec(type, ClanVotes.Scope.CLAN, c.id);
        s.secret = true;
        s.election = true;
        s.candidates.addAll(ClanLogic.oldestFirst(c, null));
        s.authorName = "#clan.pioneers.vote.system";
        s.data.putBoolean("emergency", emergency);
        switch (type) {
            case "council_election" -> {
                s.seats = Math.max(1, k.councilSeats(c.members.size()));
                electorate(c, ClanDefs.Decider.MEMBERS, s);
                s.by = "members";
                c.extra.putLong(COUNCIL_TERM, now + Math.max(1, k.councilTermDays()) * ClanDefs.DAY);
            }
            case "council_choice" -> {
                electorate(c, ClanDefs.Decider.COUNCIL, s);
                s.by = "council";
                c.extra.putLong(TERM, now + Math.max(1, k.termDays()) * ClanDefs.DAY);
            }
            default -> {
                electorate(c, ClanDefs.Decider.MEMBERS, s);
                s.by = "members";
                c.extra.putLong(TERM, now + Math.max(1, k.termDays()) * ClanDefs.DAY);
            }
        }
        if (!k.reelectable() && c.representative != null && !"council_election".equals(type)) {
            s.candidates.remove(c.representative);
        }
        ClanVotes.Opened o = ClanVotes.open(server, s);
        ClanData.get(server).changed();
        return o.error();
    }

    /**
     * Contre-pouvoir : {@code recall} (Democratie, revocation du Representant), {@code no_confidence} (Conseil, motion de defiance du
     * Conseil), {@code revolution} (Empire : une large majorite des membres impose la Democratie, une fois par 30 jours).
     */
    public static Result counter(ServerPlayer actor, Clan c, String type) {
        MinecraftServer server = actor.getServer();
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        UUID u = actor.getUUID();
        if (k == null || c.solo || k.counter().none() || !k.counter().type().equals(type)) {
            return Result.refused("clan.pioneers.error.no_counter");
        }
        if (u.equals(c.representative) || c.member(u) == null || c.representative == null) {
            return Result.refused("clan.pioneers.error.permission");
        }
        ClanDefs.Rule r = k.counter().rule();
        if (r.by() == ClanDefs.Decider.COUNCIL && !c.council.contains(u)) {
            return Result.refused("clan.pioneers.error.council_only");
        }
        if (r.by() == ClanDefs.Decider.MEMBERS && !"revolution".equals(type) && !ClanRoles.has(c, u, Permission.PROPOSE)) {
            return Result.refused("clan.pioneers.error.permission");
        }
        long now = ClanClock.now();
        long last = "revolution".equals(type) ? c.revolutionAt : c.extra.getLong(COUNTER);
        if (last > 0 && now - last < k.counter().cooldownDays() * ClanDefs.DAY) {
            return Result.refused("clan.pioneers.error.counter_cooldown");
        }
        ClanVotes.Spec s = new ClanVotes.Spec(type, ClanVotes.Scope.CLAN, c.id);
        s.majority = r.majority();
        s.electorateBase = r.electorateBase();
        s.by = r.by().id();
        electorate(c, r.by(), s);
        s.data.putUUID("target", c.representative);
        s.author = u;
        s.authorName = ClanMessages.character(actor);
        ClanVotes.Opened o = ClanVotes.open(server, s);
        if (o.error() != null) {
            return Result.refused(o.error());
        }
        if ("revolution".equals(type)) {
            c.revolutionAt = now;
        } else {
            c.extra.putLong(COUNTER, now);
        }
        ClanData.get(server).changed();
        return new Result(Outcome.PROPOSED, null, o.id());
    }

    // ------------------------------------------------------------------ regime
    /**
     * Changer de regime : un clan tout juste converti (solo -&gt; clan) le choisit librement une fois ; sinon c'est une decision « regime »
     * (cout, delai de 7 jours apres le vote, 30 jours avant le suivant).
     */
    public static Result changeRegime(ServerPlayer actor, Clan c, String regime) {
        MinecraftServer server = actor.getServer();
        if (!ClanDefs.REGIMES.contains(regime)) {
            return Result.refused("clan.pioneers.error.regime");
        }
        if (c.solo) {
            return Result.refused("clan.pioneers.error.solo");
        }
        if (c.extra.getBoolean(ClanLogic.CHOOSE_REGIME)) {
            if (!actor.getUUID().equals(c.representative)) {
                return Result.refused("clan.pioneers.error.representative_only");
            }
            c.extra.remove(ClanLogic.CHOOSE_REGIME);
            applyRegime(server, c, regime, ClanClock.now(), "clan.pioneers.reason.choice");
            return new Result(Outcome.APPLIED, null, null);
        }
        CompoundTag d = new CompoundTag();
        d.putString("regime", regime);
        return decide(actor, c, "regime", d, "");
    }

    /** Met le regime en vigueur (fin du delai, Revolution, choix d'un clan converti) et prepare ses elections. */
    public static void applyRegime(MinecraftServer server, Clan c, String regime, long now, String reasonKey) {
        String prev = c.regime;
        c.regime = regime;
        c.pendingRegime = "";
        c.regimeAt = 0;
        c.regimeChanged = now;
        for (String t : GOVERNANCE) {
            Proposal p = ClanVotes.openOfType(c.proposals, t);
            if (p != null) {
                ClanVotes.cancel(server, c, null, p.id(), "clan.pioneers.vote.why.regime");
            }
        }
        if (!ClanDefs.EMPIRE.equals(regime)) {
            c.heir = null;
        }
        if (!ClanDefs.COUNCIL.equals(regime)) {
            c.council.clear();
        }
        c.extra.remove(TERM);
        c.extra.remove(COUNCIL_TERM);
        setupRegime(server, c, now, false);
        c.log(PioneerApi.day(server), "clan.pioneers.log.regime", "#clan.pioneers.regime." + regime, "#" + reasonKey);
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.regime_now",
              Component.translatable("clan.pioneers.regime." + regime), Component.translatable(reasonKey))));
        ClanAudit.write(server, "regime", c, prev + " -> " + regime + " (" + reasonKey + ")");
        ClanData.get(server).changed();
        if (!prev.equals(regime)) {
            MinecraftForge.EVENT_BUS.post(new ClanEvent.RegimeChanged(server, c, prev));
        }
    }

    /**
     * Prepare un regime : a la FONDATION, le fondateur est le premier Representant (mandat qui commence) et le Conseil est forme des
     * signataires ; apres un CHANGEMENT, les elections s'ouvrent tout de suite.
     */
    public static void setupRegime(MinecraftServer server, Clan c, long now, boolean founding) {
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        if (k == null || c.solo) {
            return;
        }
        long term = Math.max(1, k.termDays()) * ClanDefs.DAY;
        switch (k.repMode()) {
            case "elected" -> {
                if (founding) {
                    c.extra.putLong(TERM, now + term);
                } else {
                    openElection(server, c, "election", false);
                }
            }
            case "council" -> {
                if (founding) {
                    c.council.clear();
                    int seats = k.councilSeats(c.members.size());
                    for (UUID u : ClanLogic.oldestFirst(c, null)) {
                        if (c.council.size() < seats) {
                            c.council.add(u);
                        }
                    }
                    c.extra.putLong(COUNCIL_TERM, now + Math.max(1, k.councilTermDays()) * ClanDefs.DAY);
                    c.extra.putLong(TERM, now + term);
                } else {
                    c.extra.putLong(TERM, now + term);
                    openElection(server, c, "council_election", false);
                }
            }
            case "rotating" -> c.rotateAt = now + term;
            default -> {
            }
        }
    }

    /** Empire : le Representant designe son heritier (un membre, ou personne). */
    @Nullable
    public static String setHeir(ServerPlayer actor, Clan c, @Nullable UUID heir) {
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        if (k == null || !"appointed".equals(k.repMode()) || c.solo) {
            return "clan.pioneers.error.no_heir";
        }
        if (!actor.getUUID().equals(c.representative)) {
            return "clan.pioneers.error.representative_only";
        }
        if (heir != null && (c.member(heir) == null || heir.equals(c.representative))) {
            return "clan.pioneers.error.not_member";
        }
        c.heir = heir;
        c.log(PioneerApi.day(actor.getServer()), "clan.pioneers.log.heir", heir == null ? "-" : ClanMessages.character(actor.getServer(), c, heir));
        ClanData.get(actor.getServer()).changed();
        return null;
    }

    // ------------------------------------------------------------------ balayage (une fois par minute)
    /** Echeances d'un clan : regime en attente, succession (30 jours d'absence), mandats, Conseil, rotation du porte-parole. */
    public static void tick(MinecraftServer server, Clan c, long now) {
        if (c.members.isEmpty()) {
            return;
        }
        if (!c.pendingRegime.isEmpty() && c.regimeAt > 0 && now >= c.regimeAt) {
            applyRegime(server, c, c.pendingRegime, now, "clan.pioneers.reason.vote");
        }
        ClanDefs.Rules rules = ClanDefs.get().rules;
        if (rules == null) {
            return;
        }
        if (ClanLogic.representativeAbsent(c, now, rules.absenceDays() * ClanDefs.DAY) && now - c.extra.getLong("gov.succ") > ClanDefs.DAY) {
            ClanCore.succession(server, c, now);
        }
        if (c.solo) {
            return;
        }
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        if (k == null) {
            return;
        }
        switch (k.repMode()) {
            case "elected" -> {
                if (c.extra.getLong(TERM) <= now) {
                    openElection(server, c, "election", false);
                }
            }
            case "council" -> {
                if (c.extra.getLong(COUNCIL_TERM) <= now || c.council.isEmpty()) {
                    openElection(server, c, "council_election", false);
                } else if (c.extra.getLong(TERM) <= now) {
                    openElection(server, c, "council_choice", false);
                }
            }
            case "rotating" -> {
                if (c.rotateAt <= now) {
                    UUID next = ClanLogic.nextSpokesperson(c, now, ClanCore.activeMs(), c.representative);
                    c.rotateAt = now + Math.max(1, k.termDays()) * ClanDefs.DAY;
                    if (next != null && !next.equals(c.representative)) {
                        ClanCore.setRepresentative(server, c, next, "clan.pioneers.reason.rotation");
                    }
                    ClanData.get(server).changed();
                }
            }
            default -> {
            }
        }
    }
}
