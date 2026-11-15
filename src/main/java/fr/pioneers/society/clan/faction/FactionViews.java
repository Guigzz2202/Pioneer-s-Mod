package fr.pioneers.society.clan.faction;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanViews;
import fr.pioneers.society.clan.people.Peoples;
import fr.pioneers.society.clan.people.Rallying;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.society.clan.vote.Proposal;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.society.reputation.ReputationApi;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : fiches de la faction pour l'onglet Clan du Carnet (dessine par un autre lot), construites cote serveur pour UN
 * joueur : {@code faction} (fiche : identite, regime, niveau, Representant federal, tresor, contribution, budgets, peuple), {@code clans}
 * (clans membres et Delegues ; un membre de la faction voit la liste des PERSONNAGES de tous ses clans), {@code government} (propositions
 * federales ouvertes et recentes, qui decide quoi), {@code journal} (journal federal), {@code people} (peuple de joueurs : doctrine, jauges,
 * villages rallies, declin, conditions de secession), {@code directory} (annuaire public des factions) et {@code peoples} (annuaire des
 * peuples). Regle des identites : jamais de pseudo de compte ni d'identifiant de compte ; les membres sont designes par leur nom de
 * personnage. Chaque fiche tient sous 32 767 octets.
 */
public final class FactionViews {

    public static final List<String> SHEETS = List.of("faction", "clans", "government", "journal", "people", "directory", "peoples");
    private static final int LIST_MAX = 40;

    private FactionViews() {
    }

    /** Fiche demandee ({@code error} si rien a montrer). Hors de toute faction : l'annuaire et les fiches publiques seulement. */
    public static CompoundTag sheet(ServerPlayer p, String sheet) {
        MinecraftServer server = p.getServer();
        CompoundTag out = new CompoundTag();
        out.putString("sheet", sheet);
        if (!Factions.enabled()) {
            out.putString("error", "clan.pioneers.error.disabled");
            return out;
        }
        switch (sheet) {
            case "directory" -> {
                directory(server, out);
                return out;
            }
            case "peoples" -> {
                out.merge(Peoples.directory(server));
                return out;
            }
            default -> {
            }
        }
        Faction f = Factions.ofPlayer(server, p.getUUID());
        if (f == null) {
            out.putString("error", "clan.pioneers.faction.error.none");
            return out;
        }
        UUID me = p.getUUID();
        switch (sheet) {
            case "clans" -> clans(server, f, out);
            case "government" -> government(server, f, me, out);
            case "journal" -> journal(f, out);
            case "people" -> people(server, f, out);
            default -> fiche(server, f, me, out);
        }
        return out;
    }

    // ------------------------------------------------------------------ fiche
    private static void fiche(MinecraftServer server, Faction f, UUID me, CompoundTag t) {
        FactionRules.Values v = FactionRules.get();
        long now = ClanClock.now();
        int level = Factions.level(server, f);
        long points = Factions.points(server, f);
        t.putString("name", f.name);
        t.putString("tag", f.tag);
        t.putInt("color", f.color);
        t.putString("motto", f.motto);
        t.putString("regime", f.regime);
        t.putLong("founded", f.founded);
        t.putInt("level", level);
        t.putLong("points", points);
        t.putLong("next", level < FactionRules.maxLevel(v) ? v.levels()[level] : -1);
        t.putInt("clans", f.clans.size());
        t.putInt("maxClans", v.maxClans());
        t.putInt("members", Factions.members(server, f));
        t.putInt("active", Factions.active(server, f, now));
        t.putString("representative", Factions.delegateName(server, f));
        t.putBoolean("isRep", me.equals(f.representative));
        t.putBoolean("isDelegate", Factions.delegate(server, f, me));
        t.putLong("treasury", f.treasury);
        t.putInt("contribution", f.contribution);
        t.putInt("contributionMax", Factions.cap(f));
        t.putInt("zones", FactionZones.zones(f).size());
        t.putInt("zoneBudget", FactionZones.budget(server, f));
        t.putInt("loaded", FactionZones.loaded(f).size());
        t.putInt("slots", FactionZones.slots(server, f));
        t.putBoolean("loadSuspended", f.extra.getBoolean(FactionZones.SUSPENDED));
        t.putLong("term", f.extra.getLong(FactionGov.TERM));
        t.putLong("rotate", f.rotateAt);
        t.putString("people", f.people == null ? "" : f.people);
        PeopleApi.People pe = Peoples.people(server, f);
        if (pe != null) {
            t.putString("peopleName", pe.displayName());
            t.putInt("peopleColor", pe.color());
        }
        t.putInt("openVotes", ClanVotes.openOf(f.proposals).size());
        t.put("treaties", FederalTreaties.view(server, f));
        t.putBoolean("workers", f.extra.getBoolean(FactionWorkers.KEY));
        Clan mine = ClanData.get(server).clanOf(me);
        if (mine != null) {
            long leaving = f.extra.getCompound(Factions.LEAVING).getLong(mine.id.toString());
            if (leaving > 0) {
                t.putLong("leaving", leaving);
            }
        }
    }

    // ------------------------------------------------------------------ clans et personnages
    private static void clans(MinecraftServer server, Faction f, CompoundTag t) {
        long now = ClanClock.now();
        ListTag list = new ListTag();
        CompoundTag leaving = f.extra.getCompound(Factions.LEAVING);
        for (Clan c : Factions.clans(server, f)) {
            CompoundTag x = new CompoundTag();
            x.putString("name", c.name);
            x.putString("tag", c.tag);
            x.putInt("color", c.color);
            x.putString("regime", c.regime);
            x.putInt("level", c.level);
            x.putLong("points", c.points);
            x.putInt("members", c.members.size());
            x.putInt("active", c.activeCount(now));
            x.putBoolean("founder", c.id.equals(f.founder));
            x.putString("delegate", ClanMessages.character(server, c, c.representative));
            x.putLong("leaving", leaving.getLong(c.id.toString()));
            //allie : la liste des personnages (jamais les pseudos)
            ListTag chars = new ListTag();
            for (UUID u : c.members.keySet()) {
                if (chars.size() < LIST_MAX) {
                    chars.add(StringTag.valueOf(ClanMessages.character(server, c, u)));
                }
            }
            x.put("characters", chars);
            list.add(x);
        }
        t.put("clans", list);
        ListTag req = new ListTag();
        CompoundTag r = f.extra.getCompound(Factions.REQUESTS);
        for (String k : r.getAllKeys()) {
            Clan c = ClanData.get(server).clan(UUID.fromString(k));
            if (c != null) {
                req.add(StringTag.valueOf(c.name));
            }
        }
        t.put("requests", req);
    }

    // ------------------------------------------------------------------ gouvernement
    private static void government(MinecraftServer server, Faction f, UUID me, CompoundTag t) {
        t.putString("regime", f.regime);
        ListTag rules = new ListTag();
        for (String d : FactionRules.FEDERAL) {
            FactionRules.FedRule r = FactionGov.ruleOf(f, d);
            if (r == null) {
                continue;
            }
            CompoundTag x = new CompoundTag();
            x.putString("decision", d);
            x.putString("by", r.voters().name().toLowerCase(java.util.Locale.ROOT));
            x.putString("majority", r.majority().id());
            rules.add(x);
        }
        FactionRules.FedRule sec = FactionGov.ruleOf(f, "secession");
        if (sec != null) {
            CompoundTag x = new CompoundTag();
            x.putString("decision", "secession");
            x.putString("by", sec.voters().name().toLowerCase(java.util.Locale.ROOT));
            x.putString("majority", sec.majority().id());
            rules.add(x);
        }
        t.put("rules", rules);
        ListTag props = new ListTag();
        for (Tag x : f.proposals) {
            Proposal p = new Proposal((CompoundTag) x);
            CompoundTag c = new CompoundTag();
            c.putString("id", p.id());
            c.putString("type", p.type());
            c.putString("status", p.status());
            c.putString("text", p.text());
            c.putLong("closes", p.closes());
            c.putBoolean("secret", p.secret());
            c.putBoolean("election", p.election());
            c.putBoolean("elector", p.electorate().containsKey(me));
            c.putBoolean("voted", p.voted(me));
            c.putString("by", p.tag.getString("by"));
            c.putString("majority", p.tag.getString("maj"));
            if (!p.secret()) {
                p.tally().forEach((k, val) -> c.putDouble("t_" + k, val));
            }
            if (p.election()) {
                ListTag cands = new ListTag();
                for (UUID u : p.candidates()) {
                    Clan cc = ClanData.get(server).clanOf(u);
                    if (cc == null) {
                        continue;
                    }
                    CompoundTag k = new CompoundTag();
                    k.putString("token", ClanViews.token(cc, u));
                    k.putString("name", ClanMessages.character(server, cc, u));
                    k.putString("clan", cc.name);
                    cands.add(k);
                }
                c.put("candidates", cands);
            }
            props.add(c);
        }
        t.put("proposals", props);
    }

    // ------------------------------------------------------------------ journal
    private static void journal(Faction f, CompoundTag t) {
        ListTag l = new ListTag();
        int from = Math.max(0, f.journal.size() - ClanViews.PAGE);
        for (int i = from; i < f.journal.size(); i++) {
            l.add(f.journal.get(i).copy());
        }
        t.put("lines", l);
    }

    // ------------------------------------------------------------------ peuple
    private static void people(MinecraftServer server, Faction f, CompoundTag t) {
        PeopleApi.People pe = Peoples.people(server, f);
        FactionRules.Values v = FactionRules.get();
        if (pe == null) {
            //conditions de la secession et brouillon
            FactionRules.Secession s = Peoples.conditions(server, f);
            t.putInt("level", s.level());
            t.putInt("needLevel", v.secessionLevel());
            t.putLong("ageDays", s.ageMs() / FactionRules.DAY);
            t.putLong("needDays", v.secessionAgeMs() / FactionRules.DAY);
            t.putInt("clans", s.clans());
            t.putInt("needClans", v.secessionClans());
            t.putInt("active", s.activeMembers());
            t.putInt("needActive", v.secessionActive());
            t.putLong("treasury", s.treasury());
            t.putLong("cost", v.secessionCost());
            t.putInt("peoples", s.playerPeoples());
            t.putInt("maxPeoples", s.maxPeoples());
            String err = FactionRules.secession(v, s);
            if (err != null) {
                t.putString("blocked", err);
            }
            t.put("draft", f.extra.getCompound(Peoples.DRAFT).copy());
            return;
        }
        CompoundTag meta = f.extra.getCompound(Peoples.META);
        t.putString("id", pe.id());
        t.putString("name", pe.displayName());
        t.putInt("color", pe.color());
        t.putString("doctrine", pe.doctrine());
        t.putString("motto", meta.getString("motto"));
        t.putString("salute", meta.getString("salute"));
        t.putLong("founded", meta.getLong("founded"));
        t.putLong("doctrineChanged", f.doctrineChanged);
        t.putLong("doctrineNext", f.doctrineChanged + v.doctrineCooldownMs());
        t.putLong("decline", f.declineUntil);
        ListTag gauges = new ListTag();
        for (PeopleApi.People o : PeopleApi.all(server)) {
            if (o.id().equals(pe.id())) {
                continue;
            }
            CompoundTag g = new CompoundTag();
            g.putString("people", o.id());
            g.putString("name", Component.Serializer.toJson(ReputationApi.peopleName(server, o.id())));
            int val = Peoples.gauge(f, o.id());
            g.putInt("value", val);
            g.putString("tier", ReputationApi.tierOf(val).id());
            gauges.add(g);
        }
        t.put("gauges", gauges);
        ListTag rl = new ListTag();
        for (CompoundTag e : Rallying.rallied(f)) {
            rl.add(e.copy());
        }
        t.put("rallied", rl);
        t.putInt("rallyMax", v.rallyMax());
        if (Rallying.inProgress(f)) {
            t.putLong("rallyVillage", f.rallyVillage);
            t.putLong("rallyLeft", Math.max(0, f.rallyUntil - PioneerApi.day(server)));
        }
        t.putLong("lastRally", f.lastRally);
    }

    // ------------------------------------------------------------------ annuaire des factions
    private static void directory(MinecraftServer server, CompoundTag t) {
        ListTag list = new ListTag();
        for (Faction f : ClanData.get(server).factions()) {
            CompoundTag x = new CompoundTag();
            x.putString("name", f.name);
            x.putString("tag", f.tag);
            x.putInt("color", f.color);
            x.putString("motto", f.motto);
            x.putString("regime", f.regime);
            x.putInt("level", Factions.level(server, f));
            x.putInt("clans", f.clans.size());
            x.putInt("members", Factions.members(server, f));
            x.putString("representative", Factions.delegateName(server, f));
            x.putString("people", f.people == null ? "" : f.people);
            ListTag cl = new ListTag();
            for (Clan c : Factions.clans(server, f)) {
                cl.add(StringTag.valueOf(c.name));
            }
            x.put("clanNames", cl);
            list.add(x);
        }
        t.put("factions", list);
    }
}
