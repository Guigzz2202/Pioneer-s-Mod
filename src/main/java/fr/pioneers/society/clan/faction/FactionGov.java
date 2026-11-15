package fr.pioneers.society.clan.faction;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanEvent;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.society.clan.vote.Proposal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : gouvernement federal, sur le MEME moteur de votes que les clans (portee {@code FACTION}, ponderation plafonnee :
 * aucun clan au-dessus de 40 % des voix). Democratie : tous les membres des clans votent, un joueur une voix, le Representant federal est elu
 * par eux pour 14 jours (parmi les Delegues) ; Empire : le Representant federal est celui du clan fondateur et decide seul, les Delegues
 * peuvent le revoquer aux trois quarts ; Conseil : les Delegues votent (une voix par clan) et elisent le Representant federal ; Cooperative :
 * les Delegues votent aux deux tiers, le porte-parole tourne tous les 14 jours. Les decisions des CLANS sur leur appartenance (signer le
 * Pacte, demander l'entree, quitter) suivent la ligne « traite » de leur propre regime.
 */
@Mod.EventBusSubscriber(modid = fr.pioneers.Pioneers.MODID)
public final class FactionGov {

    /** Cles de {@code Faction.extra} : fin du mandat (ms), derniere revocation tentee (ms), dernier changement de regime (ms). */
    public static final String TERM = "fed.term", RECALL = "fed.recallAt", REGIME_AT = "fed.regimeAt";
    /** Types de vote federaux (prefixe « fed_ ») et de clan (appartenance). */
    public static final String PREFIX = "fed_", ELECTION = "fed_election", RECALL_VOTE = "fed_recall", SIGN = "faction_sign", JOIN = "faction_join",
          LEAVE = "faction_leave";
    public static final List<String> CLAN_TYPES = List.of(SIGN, JOIN, LEAVE);

    /** Verification prealable d'une decision federale (cle du refus, ou {@code null}). */
    @FunctionalInterface
    public interface Check {

        @Nullable
        String check(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag data);
    }

    /** Effet d'une decision federale acquise. */
    @FunctionalInterface
    public interface Effect {

        boolean apply(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag data);
    }

    /** Effet d'une decision de clan sur son appartenance. */
    @FunctionalInterface
    public interface ClanEffect {

        boolean apply(MinecraftServer server, Clan c, @Nullable UUID author, CompoundTag data);
    }

    private static final Map<String, Check> CHECKS = new HashMap<>();

    private FactionGov() {
    }

    // ------------------------------------------------------------------ registre
    /** Branche une decision federale (verification et effet) : type de vote {@code fed_<decision>}. */
    public static void register(String decision, @Nullable Check check, Effect effect) {
        if (check != null) {
            CHECKS.put(decision, check);
        }
        ClanVotes.register(PREFIX + decision, (server, d) -> d.faction() != null && effect.apply(server, d.faction(), d.author(), d.data()));
    }

    /** Branche une decision de clan sur son appartenance a une faction. */
    public static void registerClan(String type, @Nullable ClanGov.Check check, ClanEffect effect) {
        if (check != null) {
            ClanGov.registerCheck(type, check);
        }
        ClanVotes.register(type, (server, d) -> d.clan() != null && effect.apply(server, d.clan(), d.author(), d.data()));
    }

    // ------------------------------------------------------------------ decisions federales
    /**
     * Engage une decision federale : verification, puis application directe (Representant federal, Empire) ou ouverture d'un vote (membres
     * ou Delegues, a la majorite du regime federal). Un Delegue ou le Representant federal l'engage.
     */
    public static ClanGov.Result decide(ServerPlayer actor, Faction f, String decision, CompoundTag data, String text) {
        MinecraftServer server = actor.getServer();
        if (!Factions.enabled()) {
            return refused("clan.pioneers.error.disabled");
        }
        UUID u = actor.getUUID();
        if (!Factions.member(server, f, u)) {
            return refused("clan.pioneers.faction.error.not_member");
        }
        FactionRules.FedRule r = FactionRules.rule(FactionRules.get(), ClanDefs.get(), f.regime, decision);
        if (r == null) {
            return refused("clan.pioneers.error.invalid");
        }
        boolean rep = u.equals(f.representative);
        if (r.voters() == FactionRules.Voters.REPRESENTATIVE ? !rep : !rep && !Factions.delegate(server, f, u)) {
            return refused(r.voters() == FactionRules.Voters.REPRESENTATIVE ? "clan.pioneers.faction.error.representative_only"
                  : "clan.pioneers.faction.error.delegate_only");
        }
        Check check = CHECKS.get(decision);
        String err = check == null ? null : check.check(server, f, actor, data);
        if (err != null) {
            return refused(err);
        }
        String type = PREFIX + decision;
        if (r.voters() == FactionRules.Voters.REPRESENTATIVE) {
            return ClanVotes.applyDirect(server, type, null, f, u, data) ? new ClanGov.Result(ClanGov.Outcome.APPLIED, null, null)
                  : refused("clan.pioneers.error.failed");
        }
        ClanVotes.Spec s = new ClanVotes.Spec(type, ClanVotes.Scope.FACTION, f.id);
        s.majority = r.majority();
        s.electorateBase = r.electorateBase();
        s.by = r.voters() == FactionRules.Voters.MEMBERS ? "members" : "delegates";
        s.electorate.putAll(FactionRules.electorate(Factions.clans(server, f), r.voters()));
        s.weightCap = Factions.weightCap(f);
        s.data = data.copy();
        s.text = text == null ? "" : text;
        s.author = u;
        s.authorName = ClanMessages.character(actor);
        ClanVotes.Opened o = ClanVotes.open(server, s);
        return o.error() != null ? refused(o.error()) : new ClanGov.Result(ClanGov.Outcome.PROPOSED, null, o.id());
    }

    private static ClanGov.Result refused(String key) {
        return new ClanGov.Result(ClanGov.Outcome.REFUSED, key, null);
    }

    // ------------------------------------------------------------------ decisions des clans (appartenance)
    /**
     * Decision d'un clan sur son appartenance (signer un Pacte, demander l'entree, quitter) : la ligne « traite » de son regime (Representant
     * en Empire, vote ailleurs). La permission de diplomatie engage le vote ; un clan solo n'entre dans aucune faction.
     */
    public static ClanGov.Result clanDecide(ServerPlayer actor, Clan clan, String type, CompoundTag data, String text) {
        MinecraftServer server = actor.getServer();
        if (!Factions.enabled()) {
            return refused("clan.pioneers.error.disabled");
        }
        UUID u = actor.getUUID();
        if (clan.member(u) == null) {
            return refused("clan.pioneers.error.not_member");
        }
        if (clan.solo) {
            return refused("clan.pioneers.faction.error.solo");
        }
        ClanDefs.Rule r = ClanGov.rule(clan, "treaty");
        if (r == null) {
            return refused("clan.pioneers.error.invalid");
        }
        boolean rep = u.equals(clan.representative);
        if (r.direct() ? !rep : !ClanRoles.has(clan, u, Permission.DIPLOMACY)) {
            return refused(r.direct() ? "clan.pioneers.error.representative_only" : "clan.pioneers.error.permission");
        }
        ClanGov.Check check = checkOf(type);
        String err = check == null ? null : check.check(server, clan, actor, data);
        if (err != null) {
            return refused(err);
        }
        if (r.direct()) {
            return ClanVotes.applyDirect(server, type, clan, null, u, data) ? new ClanGov.Result(ClanGov.Outcome.APPLIED, null, null)
                  : refused("clan.pioneers.error.failed");
        }
        ClanVotes.Spec s = new ClanVotes.Spec(type, ClanVotes.Scope.CLAN, clan.id);
        s.majority = r.majority();
        s.electorateBase = r.electorateBase();
        s.by = r.by().id();
        String g = clan.id.toString();
        if (r.by() == ClanDefs.Decider.COUNCIL && !clan.council.isEmpty()) {
            clan.council.forEach(x -> s.electorate.put(x, g));
        } else {
            clan.members.keySet().forEach(x -> s.electorate.put(x, g));
        }
        s.data = data.copy();
        s.text = text == null ? "" : text;
        s.author = u;
        s.authorName = ClanMessages.character(actor);
        ClanVotes.Opened o = ClanVotes.open(server, s);
        return o.error() != null ? refused(o.error()) : new ClanGov.Result(ClanGov.Outcome.PROPOSED, null, o.id());
    }

    private static final Map<String, ClanGov.Check> CLAN_CHECKS = new HashMap<>();

    @Nullable
    private static ClanGov.Check checkOf(String type) {
        return CLAN_CHECKS.get(type);
    }

    /** Verification d'une decision de clan (gardee ici : {@code ClanGov.registerCheck} ne sert qu'aux types du tableau). */
    public static void registerClanCheck(String type, ClanGov.Check check) {
        CLAN_CHECKS.put(type, check);
    }

    // ------------------------------------------------------------------ Representant federal
    /** A la fondation et apres un changement de regime : Representant, mandat, rotation. */
    public static void setup(MinecraftServer server, Faction f, long now) {
        FactionRules.Values v = FactionRules.get();
        Clan founder = ClanData.get(server).clan(f.founder);
        switch (f.regime) {
            case ClanDefs.EMPIRE -> {
                if (founder != null && founder.representative != null) {
                    f.representative = founder.representative;
                }
                f.extra.remove(TERM);
            }
            case ClanDefs.COOPERATIVE -> {
                if (f.representative == null || !Factions.delegate(server, f, f.representative)) {
                    UUID first = nextSpokesperson(server, f, null);
                    if (first != null) {
                        f.representative = first;
                    }
                }
                f.rotateAt = now + v.rotationMs();
                f.extra.remove(TERM);
            }
            default -> {
                if (f.representative == null && founder != null) {
                    f.representative = founder.representative;
                }
                f.extra.putLong(TERM, now + v.termMs());
            }
        }
        ClanData.get(server).changed();
    }

    /** Porte-parole suivant de la Cooperative : le Delegue du clan qui suit celui du porte-parole actuel. */
    @Nullable
    static UUID nextSpokesperson(MinecraftServer server, Faction f, @Nullable UUID current) {
        List<Clan> clans = Factions.clans(server, f);
        if (clans.isEmpty()) {
            return null;
        }
        int at = -1;
        for (int i = 0; i < clans.size(); i++) {
            if (current != null && current.equals(clans.get(i).representative)) {
                at = i;
            }
        }
        for (int k = 1; k <= clans.size(); k++) {
            Clan c = clans.get(Math.floorMod(at + k, clans.size()));
            if (c.representative != null) {
                return c.representative;
            }
        }
        return null;
    }

    /** Change de Representant federal (journal, annonce, audit). */
    public static void setRepresentative(MinecraftServer server, Faction f, @Nullable UUID next, String reasonKey) {
        if (next == null || next.equals(f.representative)) {
            return;
        }
        f.representative = next;
        String name = Factions.character(server, next);
        Factions.log(server, f, "clan.pioneers.faction.log.representative", name, "#" + reasonKey);
        Factions.tell(server, f, Component.translatable("clan.pioneers.faction.representative", name, Component.translatable(reasonKey)));
        ClanAudit.write(server, "faction", null, f.name + " : Representant federal " + next + " (" + reasonKey + ")");
        ClanData.get(server).changed();
    }

    /** Le Representant federal n'est plus dans la faction : remplacant selon le regime (election tout de suite en Democratie et Conseil). */
    static void representativeGone(MinecraftServer server, Faction f, long now) {
        Clan founder = ClanData.get(server).clan(f.founder);
        switch (f.regime) {
            case ClanDefs.COOPERATIVE -> setRepresentative(server, f, nextSpokesperson(server, f, null), "clan.pioneers.faction.reason.rotation");
            case ClanDefs.EMPIRE -> setRepresentative(server, f, founder == null ? null : founder.representative,
                  "clan.pioneers.faction.reason.founder");
            default -> {
                setRepresentative(server, f, founder == null ? null : founder.representative, "clan.pioneers.faction.reason.interim");
                openElection(server, f, now);
            }
        }
    }

    /** Le clan fondateur a change (depart, revocation) : en Empire, son Representant devient le Representant federal. */
    static void founderChanged(MinecraftServer server, Faction f, long now) {
        if (ClanDefs.EMPIRE.equals(f.regime)) {
            Clan founder = ClanData.get(server).clan(f.founder);
            setRepresentative(server, f, founder == null ? null : founder.representative, "clan.pioneers.faction.reason.founder");
        }
    }

    /** Election du Representant federal (Democratie : les membres ; Conseil : les Delegues) parmi les Delegues, a bulletin secret. */
    @Nullable
    public static String openElection(MinecraftServer server, Faction f, long now) {
        if (!ClanDefs.DEMOCRACY.equals(f.regime) && !ClanDefs.COUNCIL.equals(f.regime)) {
            return "clan.pioneers.faction.error.no_election";
        }
        if (ClanVotes.openOfType(f.proposals, ELECTION) != null) {
            return "clan.pioneers.error.vote_open";
        }
        f.extra.putLong(TERM, now + FactionRules.get().termMs());
        ClanVotes.Spec s = new ClanVotes.Spec(ELECTION, ClanVotes.Scope.FACTION, f.id);
        s.secret = true;
        s.election = true;
        FactionRules.Voters voters = ClanDefs.DEMOCRACY.equals(f.regime) ? FactionRules.Voters.MEMBERS : FactionRules.Voters.DELEGATES;
        List<Clan> clans = Factions.clans(server, f);
        s.electorate.putAll(FactionRules.electorate(clans, voters));
        s.weightCap = Factions.weightCap(f);
        s.by = voters == FactionRules.Voters.MEMBERS ? "members" : "delegates";
        for (Clan c : clans) {
            if (c.representative != null) {
                s.candidates.add(c.representative);
            }
        }
        s.authorName = "#clan.pioneers.vote.system";
        ClanVotes.Opened o = ClanVotes.open(server, s);
        ClanData.get(server).changed();
        return o.error();
    }

    /** Empire : un Delegue lance la revocation du Representant federal (trois quarts des autres Delegues, une tentative par 7 jours). */
    public static ClanGov.Result recall(ServerPlayer actor, Faction f) {
        MinecraftServer server = actor.getServer();
        UUID u = actor.getUUID();
        if (!ClanDefs.EMPIRE.equals(f.regime)) {
            return refused("clan.pioneers.faction.error.no_recall");
        }
        if (!Factions.delegate(server, f, u) || u.equals(f.representative)) {
            return refused("clan.pioneers.faction.error.delegate_only");
        }
        long now = ClanClock.now();
        if (now - f.extra.getLong(RECALL) < 7 * FactionRules.DAY) {
            return refused("clan.pioneers.error.counter_cooldown");
        }
        ClanVotes.Spec s = new ClanVotes.Spec(RECALL_VOTE, ClanVotes.Scope.FACTION, f.id);
        s.majority = FactionRules.get().recallMajority();
        s.electorateBase = true;
        s.by = "delegates";
        for (Clan c : Factions.clans(server, f)) {
            if (c.representative != null && !c.representative.equals(f.representative)) {
                s.electorate.put(c.representative, c.id.toString());
            }
        }
        s.weightCap = Factions.weightCap(f);
        if (f.representative != null) {
            s.data.putUUID("target", f.representative);
        }
        s.author = u;
        s.authorName = ClanMessages.character(actor);
        ClanVotes.Opened o = ClanVotes.open(server, s);
        if (o.error() != null) {
            return refused(o.error());
        }
        f.extra.putLong(RECALL, now);
        return new ClanGov.Result(ClanGov.Outcome.PROPOSED, null, o.id());
    }

    /** Nouveau regime federal (decision acquise) : effet immediat, propositions de gouvernement annulees, contribution ramenee au plafond. */
    static boolean applyRegime(MinecraftServer server, Faction f, String regime) {
        if (!ClanDefs.REGIMES.contains(regime) || regime.equals(f.regime)) {
            return false;
        }
        String prev = f.regime;
        long now = ClanClock.now();
        f.regime = regime;
        f.extra.putLong(REGIME_AT, now);
        for (String t : List.of(ELECTION, RECALL_VOTE)) {
            Proposal p = ClanVotes.openOfType(f.proposals, t);
            if (p != null) {
                ClanVotes.cancel(server, null, f, p.id(), "clan.pioneers.vote.why.regime");
            }
        }
        f.contribution = Math.min(f.contribution, Factions.cap(f));
        setup(server, f, now);
        Factions.log(server, f, "clan.pioneers.faction.log.regime", "#clan.pioneers.regime." + prev, "#clan.pioneers.regime." + regime);
        Factions.tell(server, f, Component.translatable("clan.pioneers.faction.regime_now", Component.translatable("clan.pioneers.regime." + regime)));
        ClanAudit.write(server, "faction", null, f.name + " : regime federal " + prev + " -> " + regime);
        return true;
    }

    // ------------------------------------------------------------------ echeances (une fois par minute)
    public static void tick(MinecraftServer server, Faction f, long now) {
        if (f.representative == null || !Factions.member(server, f, f.representative)) {
            representativeGone(server, f, now);
        }
        switch (f.regime) {
            case ClanDefs.EMPIRE -> {
                Clan founder = ClanData.get(server).clan(f.founder);
                if (founder != null && founder.representative != null && !founder.representative.equals(f.representative)) {
                    setRepresentative(server, f, founder.representative, "clan.pioneers.faction.reason.founder");
                }
            }
            case ClanDefs.COOPERATIVE -> {
                if (f.rotateAt <= now) {
                    f.rotateAt = now + FactionRules.get().rotationMs();
                    setRepresentative(server, f, nextSpokesperson(server, f, f.representative), "clan.pioneers.faction.reason.rotation");
                    ClanData.get(server).changed();
                }
            }
            default -> {
                if (f.extra.getLong(TERM) <= now) {
                    openElection(server, f, now);
                }
            }
        }
    }

    // ------------------------------------------------------------------ evenements des clans
    /** Le Representant d'un clan a change : le Delegue suit ; en Empire, celui du clan fondateur devient le Representant federal. */
    @SubscribeEvent
    public static void onRepresentative(ClanEvent.RepresentativeChanged e) {
        Faction f = Factions.of(e.server, e.clan);
        if (f == null) {
            return;
        }
        if (ClanDefs.EMPIRE.equals(f.regime) && e.clan.id.equals(f.founder)) {
            setRepresentative(e.server, f, e.current, "clan.pioneers.faction.reason.founder");
        } else if (ClanDefs.COOPERATIVE.equals(f.regime) && e.previous != null && e.previous.equals(f.representative)) {
            setRepresentative(e.server, f, e.current, "clan.pioneers.faction.reason.rotation");
        }
    }

    /** Un clan redevenu solo, ou vide, quitte sa faction aussitot. */
    @SubscribeEvent
    public static void onLeft(ClanEvent.MemberLeft e) {
        Faction f = Factions.of(e.server, e.clan);
        if (f != null && (e.backToSolo || e.empty)) {
            Factions.remove(e.server, f, e.clan, e.empty ? "dissolved" : "solo");
        }
    }

    @SubscribeEvent
    public static void onEmptied(ClanEvent.Emptied e) {
        Faction f = Factions.of(e.server, e.clan);
        if (f != null) {
            Factions.remove(e.server, f, e.clan, "dissolved");
        }
    }

    @SubscribeEvent
    public static void onDissolved(ClanEvent.Dissolved e) {
        Faction f = Factions.of(e.server, e.clan);
        if (f != null) {
            Factions.remove(e.server, f, e.clan, "dissolved");
        }
    }

    // ------------------------------------------------------------------ effets
    /** Branche toutes les decisions des factions (appele par {@link FactionBootstrap#init}). */
    static void install() {
        register("admit", (server, f, actor, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            if (c == null || !Factions.requested(f, c)) {
                return "clan.pioneers.faction.error.no_request";
            }
            return FactionRules.canEnter(FactionRules.get(), c, f, ClanClock.now());
        }, (server, f, author, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            return c != null && Factions.requested(f, c) && Factions.admit(server, f, c) == null;
        });
        register("exclude", (server, f, actor, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            return c == null || !f.clans.contains(c.id) ? "clan.pioneers.faction.error.not_in" : null;
        }, (server, f, author, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            if (c == null || !f.clans.contains(c.id)) {
                return false;
            }
            Factions.remove(server, f, c, "exclude");
            return true;
        });
        register("contribution", (server, f, actor, d) -> FactionRules.contribution(d.getInt("value"), Factions.cap(f)) < 0
              ? "clan.pioneers.faction.error.contribution_cap" : null, (server, f, author, d) -> {
            int v = FactionRules.contribution(d.getInt("value"), Factions.cap(f));
            if (v < 0) {
                return false;
            }
            f.contribution = v;
            Factions.log(server, f, "clan.pioneers.faction.log.contribution_set", String.valueOf(v));
            Factions.tell(server, f, Component.translatable("clan.pioneers.faction.contribution_now", v));
            return true;
        });
        register("spend", (server, f, actor, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            long a = d.getLong("amount");
            if (c == null || !f.clans.contains(c.id)) {
                return "clan.pioneers.faction.error.not_in";
            }
            return a <= 0 ? "clan.pioneers.error.amount" : a > f.treasury ? "clan.pioneers.faction.error.treasury_low" : null;
        }, (server, f, author, d) -> {
            Clan c = ClanData.get(server).clan(d.hasUUID("clan") ? d.getUUID("clan") : null);
            return c != null && Factions.spendTo(server, f, c, d.getLong("amount"), Factions.character(server, author));
        });
        register("regime", (server, f, actor, d) -> {
            String r = d.getString("regime");
            if (!ClanDefs.REGIMES.contains(r)) {
                return "clan.pioneers.error.regime";
            }
            if (r.equals(f.regime)) {
                return "clan.pioneers.faction.error.same_regime";
            }
            long last = f.extra.getLong(REGIME_AT);
            return last > 0 && ClanClock.now() - last < FactionRules.get().regimeCooldownMs() ? "clan.pioneers.faction.error.regime_cooldown" : null;
        }, (server, f, author, d) -> applyRegime(server, f, d.getString("regime")));
        register("war", FactionWar::checkDeclare, FactionWar::applyDeclare);
        //traites federaux (la faction envers un clan ou une faction) et traites d'un clan avec une faction
        register(FederalTreaties.FED_TYPE, FederalTreaties::checkFederal, FederalTreaties::applyFederal);
        registerClanCheck(FederalTreaties.CLAN_TYPE, FederalTreaties::checkClan);
        registerClan(FederalTreaties.CLAN_TYPE, null, FederalTreaties::applyClan);
        //ouvriers de village payes par le tresor federal (oui ou non)
        register("workers", null, (server, f, author, d) -> {
            boolean on = d.getBoolean("on");
            if (on == f.extra.getBoolean(FactionWorkers.KEY)) {
                return false;
            }
            f.extra.putBoolean(FactionWorkers.KEY, on);
            Factions.log(server, f, on ? "clan.pioneers.faction.log.workers_on" : "clan.pioneers.faction.log.workers_off");
            Factions.tell(server, f, Component.translatable(on ? "clan.pioneers.faction.workers_on" : "clan.pioneers.faction.workers_off"));
            return true;
        });
        register("peace", FactionWar::checkPeace, FactionWar::applyPeace);
        //secession, doctrine et ralliement : branches par le paquet des peuples
        fr.pioneers.society.clan.people.Peoples.install();
        //elections et revocation
        ClanVotes.register(ELECTION, (server, d) -> {
            Faction f = d.faction();
            if (f == null || d.winners().isEmpty()) {
                return false;
            }
            UUID w = d.winners().get(0);
            if (!Factions.member(server, f, w)) {
                return false;
            }
            setRepresentative(server, f, w, "clan.pioneers.faction.reason.elected");
            return true;
        });
        ClanVotes.register(RECALL_VOTE, (server, d) -> {
            Faction f = d.faction();
            if (f == null || !ClanDefs.EMPIRE.equals(f.regime)) {
                return false;
            }
            //le clan fondateur passe au clan suivant (le plus ancien apres lui) : son Representant devient le Representant federal
            List<Clan> clans = new ArrayList<>(Factions.clans(server, f));
            clans.removeIf(c -> c.id.equals(f.founder));
            if (clans.isEmpty()) {
                return false;
            }
            f.founder = clans.get(0).id;
            setRepresentative(server, f, clans.get(0).representative, "clan.pioneers.faction.reason.recalled");
            return true;
        });
        //appartenance des clans
        registerClanCheck(SIGN, FederalPact::checkSign);
        registerClan(SIGN, null, FederalPact::applySign);
        registerClanCheck(JOIN, (server, c, actor, d) -> {
            Faction f = ClanData.get(server).faction(d.hasUUID("faction") ? d.getUUID("faction") : null);
            if (f == null) {
                return "clan.pioneers.faction.error.unknown";
            }
            if (Factions.requested(f, c)) {
                return "clan.pioneers.faction.error.requested";
            }
            return FactionRules.canEnter(FactionRules.get(), c, f, ClanClock.now());
        });
        registerClan(JOIN, null, (server, c, author, d) -> {
            Faction f = ClanData.get(server).faction(d.hasUUID("faction") ? d.getUUID("faction") : null);
            if (f == null || FactionRules.canEnter(FactionRules.get(), c, f, ClanClock.now()) != null) {
                return false;
            }
            Factions.request(server, f, c);
            return true;
        });
        registerClanCheck(LEAVE, (server, c, actor, d) -> {
            Faction f = Factions.of(server, c);
            if (f == null) {
                return "clan.pioneers.faction.error.none";
            }
            return f.extra.getCompound(Factions.LEAVING).contains(c.id.toString()) ? "clan.pioneers.faction.error.leaving" : null;
        });
        registerClan(LEAVE, null, (server, c, author, d) -> {
            Faction f = Factions.of(server, c);
            if (f == null) {
                return false;
            }
            Factions.scheduleLeave(server, f, c);
            return true;
        });
    }

    /** Raccourci : le joueur est-il le Representant federal ? */
    public static boolean isRepresentative(Faction f, UUID u) {
        return u.equals(f.representative);
    }

    /** Pour la fiche : faut-il encore un vote pour cette decision, et par qui ? */
    @Nullable
    public static FactionRules.FedRule ruleOf(Faction f, String decision) {
        return FactionRules.rule(FactionRules.get(), ClanDefs.get(), f.regime, decision);
    }

    static boolean active(MinecraftServer server) {
        return ClanCore.enabled();
    }
}
