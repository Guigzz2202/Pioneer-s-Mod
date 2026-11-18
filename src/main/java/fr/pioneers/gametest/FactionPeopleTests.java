package fr.pioneers.gametest;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanLogic;
import fr.pioneers.society.clan.faction.FactionGov;
import fr.pioneers.society.clan.faction.FactionRules;
import fr.pioneers.society.clan.faction.FactionSaved;
import fr.pioneers.society.clan.faction.Factions;
import fr.pioneers.society.clan.people.Peoples;
import fr.pioneers.society.clan.people.Rallying;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.society.reputation.ReputationApi;
import fr.pioneers.society.village.VillageData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 4, factions et peuples de joueurs : plafond de 40 % d'un clan dans un vote federal, chaque condition de la
 * secession, ralliement d'un village puis retour a la dissolution, declin puis dissolution d'un peuple.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class FactionPeopleTests {

    private static final BlockPos AT = new BlockPos(4, 2, 4);

    private FactionPeopleTests() {
    }

    /** Faction de trois clans (Representants connectes), {@code extra[i]} membres figurants (actifs, hors ligne) dans le clan i. */
    static final class Fed {

        final Gt.Scope s;
        final ServerPlayer ra, rb, rc;
        final Clan a, b, c;
        final Faction f;
        final String sfx;
        final List<UUID> fakes = new ArrayList<>();

        Fed(GameTestHelper h, String regime, int... extra) {
            s = Gt.begin(h);
            sfx = Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
            ra = s.player("gtfeda", "Arnaude", AT);
            rb = s.player("gtfedb", "Basile", AT);
            rc = s.player("gtfedc", "Celeste", AT);
            a = s.clan("GtFedA" + sfx, ra);
            b = s.clan("GtFedB" + sfx, rb);
            c = s.clan("GtFedC" + sfx, rc);
            long now = ClanClock.now();
            Clan[] all = {a, b, c};
            ClanData data = ClanData.get(s.server);
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < extra[i]; k++) {
                    UUID u = UUID.randomUUID();
                    ClanLogic.addMember(all[i], u, "gtfig" + i + k + sfx, "Figurant " + i + "-" + k, now, Integer.MAX_VALUE);
                    fakes.add(u);
                }
                data.reindex(all[i]);
            }
            CompoundTag pact = new CompoundTag();
            pact.putString("name", "GtFed" + sfx);
            pact.putString("tag", "GTF");
            pact.putInt("color", 9);
            pact.putString("regime", regime);
            f = Factions.create(s.server, pact, a, List.of(b, c), ra.getUUID());
            Gt.check(f.clans.size() == 3 && ra.getUUID().equals(f.representative), "faction de test mal fondee");
        }

        List<Clan> clans() {
            return List.of(a, b, c);
        }

        /** Toutes les conditions de la secession remplies (niveau, age, tresor ; clans et membres actifs viennent de la construction). */
        void ready() {
            FactionRules.Values v = FactionRules.get();
            a.points = v.levels()[Math.max(0, Math.min(v.levels().length - 1, v.secessionLevel() - 1))];
            f.founded = ClanClock.now() - v.secessionAgeMs() - FactionRules.DAY;
            f.treasury = v.secessionCost() + v.rallyCost() + 10_000;
        }

        void activity(long lastSeen) {
            for (Clan k : clans()) {
                k.members.values().forEach(m -> m.lastSeen = lastSeen);
            }
        }

        /** Nom de peuple neuf (lettres seulement). */
        String peopleName() {
            StringBuilder sb = new StringBuilder("Peuple ");
            for (char ch : sfx.toCharArray()) {
                sb.append((char) ('a' + Character.digit(ch, 16)));
            }
            return sb.toString();
        }

        /** Brouillon complet puis secession acquise (vote ouvert : effet applique comme a la cloture, le vote est retire). */
        String secede() {
            ready();
            FactionRules.Values v = FactionRules.get();
            Gt.equal((Object) null, Peoples.setDraft(ra, "name", peopleName()), "nom du peuple");
            Gt.equal((Object) null, Peoples.setDraft(ra, "tint", v.tints().get(0).id()), "teinte du peuple");
            Gt.equal((Object) null, Peoples.setDraft(ra, "doctrine", v.doctrines().keySet().iterator().next()), "doctrine du peuple");
            decided(FactionGov.decide(ra, f, "secession", new CompoundTag(), ""), "secession");
            Gt.check(f.people != null && !f.people.isEmpty(), "peuple non cree");
            PeopleApi.People pe = PeopleApi.get(s.server, f.people);
            Gt.check(pe != null && pe.player(), "peuple absent du registre des peuples de joueurs");
            return f.people;
        }

        /** Decision federale acquise : directe, ou vote ouvert dont l'effet est applique (comme a la cloture) puis le vote retire. */
        void decided(ClanGov.Result r, String decision) {
            Gt.check(r.outcome() != ClanGov.Outcome.REFUSED, decision + " refusee : " + r.error());
            if (r.outcome() == ClanGov.Outcome.PROPOSED) {
                CompoundTag data = proposal(f, r.proposal()).getCompound("data");
                Gt.check(ClanVotes.applyDirect(s.server, FactionGov.PREFIX + decision, null, f, ra.getUUID(), data), decision + " : effet refuse");
                ClanVotes.cancel(s.server, null, f, r.proposal(), "clan.pioneers.people.why.dissolved");
            }
        }
    }

    static CompoundTag proposal(Faction f, String id) {
        for (Tag t : f.proposals) {
            if (t instanceof CompoundTag c && id.equals(c.getString("id"))) {
                return c;
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("proposition " + id + " introuvable");
    }

    // ================================================================== vote federal
    /**
     * Democratie federale : un clan de 10 membres face a deux clans de 2 et 1 membres. Dans le vote ouvert (tous les membres votent), le
     * poids de chaque clan est plafonne a 40 % du total, et partage egalement entre ses membres.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_faction_plafond_vote", timeoutTicks = 40)
    public static void vote_federal_plafonne_a_40_pourcent(GameTestHelper h) {
        Fed x = new Fed(h, "democracy", 9, 1, 0);
        double cap = Factions.weightCap(x.f);
        Gt.check(cap > 0 && cap <= 0.4 + 1e-9, "plafond federal inattendu : " + cap);
        CompoundTag data = new CompoundTag();
        data.putInt("value", Math.max(0, Math.min(5, Factions.cap(x.f))));
        ClanGov.Result r = FactionGov.decide(x.ra, x.f, "contribution", data, "");
        Gt.equal(ClanGov.Outcome.PROPOSED, r.outcome(), "la contribution doit etre mise au vote des membres (" + r.error() + ")");
        CompoundTag p = proposal(x.f, r.proposal());
        Map<String, Double> byClan = new HashMap<>();
        Map<String, List<Double>> each = new HashMap<>();
        double total = 0;
        for (Tag t : p.getList("elect", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            byClan.merge(e.getString("g"), e.getDouble("w"), Double::sum);
            each.computeIfAbsent(e.getString("g"), k -> new ArrayList<>()).add(e.getDouble("w"));
            total += e.getDouble("w");
        }
        Gt.equal(13, p.getList("elect", Tag.TAG_COMPOUND).size(), "corps electoral (tous les membres)");
        Gt.check(total > 0, "poids nuls");
        for (Map.Entry<String, Double> e : byClan.entrySet()) {
            Gt.check(e.getValue() / total <= cap + 1e-6, "clan " + e.getKey() + " au-dessus du plafond : " + e.getValue() / total);
            List<Double> ws = each.get(e.getKey());
            Gt.check(ws.stream().allMatch(w -> Math.abs(w - ws.get(0)) < 1e-9), "poids inegaux dans un meme clan");
        }
        double share = byClan.getOrDefault(x.a.id.toString(), 0.0) / total;
        Gt.check(share < 10.0 / 13.0 - 0.1, "le gros clan n'a pas ete plafonne : " + share);
        ClanVotes.cancel(x.s.server, null, x.f, r.proposal(), "clan.pioneers.people.why.dissolved");
        x.s.done();
    }

    // ================================================================== secession
    static void refusedFor(Fed x, String key, String what) {
        Gt.equal(key, FactionRules.secession(FactionRules.get(), Peoples.conditions(x.s.server, x.f)), what + " (conditions)");
        ClanGov.Result r = FactionGov.decide(x.ra, x.f, "secession", new CompoundTag(), "");
        Gt.equal(ClanGov.Outcome.REFUSED, r.outcome(), what + " : la decision aurait du etre refusee");
        Gt.equal(key, r.error(), what + " (decision)");
    }

    /**
     * Chaque condition de la secession, cassee seule dans un vrai monde (niveau, age, nombre de clans, membres actifs, nombre de peuples,
     * cout), puis le brouillon manquant, puis la secession : peuple inscrit, cout preleve, schisme paye par les membres, plus de seconde.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_peuple_secession", timeoutTicks = 40)
    public static void secession_chaque_condition(GameTestHelper h) {
        Fed x = new Fed(h, "empire", 7, 7, 7);
        Gt.check(Factions.enabled(), "factions coupees");
        FactionRules.Values v = FactionRules.get();
        x.ready();
        Gt.equal((Object) null, FactionRules.secession(v, Peoples.conditions(x.s.server, x.f)), "conditions toutes remplies");
        long points = x.a.points;
        x.a.points = 0;
        refusedFor(x, "clan.pioneers.people.error.level", "niveau");
        x.a.points = points;
        long founded = x.f.founded;
        x.f.founded = ClanClock.now();
        refusedFor(x, "clan.pioneers.people.error.age", "age");
        x.f.founded = founded;
        x.f.clans.remove(x.c.id);
        refusedFor(x, "clan.pioneers.people.error.clans", "clans");
        x.f.clans.add(x.c.id);
        x.activity(0);
        refusedFor(x, "clan.pioneers.people.error.active", "membres actifs");
        x.activity(ClanClock.now());
        PeopleApi.PlayerPeoples pp = PeopleApi.PlayerPeoples.get(x.s.server);
        List<String> dummies = new ArrayList<>();
        for (int i = Peoples.count(x.s.server); i < Peoples.maxPeoples(); i++) {
            String id = "pp_gt" + i + x.sfx;
            pp.put(new PeopleApi.People(id, "lettered", 0, 0x888888, "gray", true, "Gtfictif" + i));
            dummies.add(id);
        }
        refusedFor(x, "clan.pioneers.people.error.max", "nombre de peuples");
        dummies.forEach(pp::remove);
        if (EconomyApi.enabled()) {
            long t = x.f.treasury;
            x.f.treasury = v.secessionCost() - 1;
            refusedFor(x, "clan.pioneers.people.error.cost", "cout");
            x.f.treasury = t;
        }
        //conditions remplies mais brouillon vide
        ClanGov.Result r = FactionGov.decide(x.ra, x.f, "secession", new CompoundTag(), "");
        Gt.equal("clan.pioneers.people.error.draft_name", r.error(), "brouillon vide");
        int before = ReputationApi.reputation(x.rb, v.schismPeople());
        long treasury = x.f.treasury;
        x.secede();
        if (EconomyApi.enabled()) {
            Gt.equal(treasury - v.secessionCost(), x.f.treasury, "cout de la secession");
        }
        Gt.equal(before + v.schism(), ReputationApi.reputation(x.rb, v.schismPeople()), "schisme paye par un membre connecte");
        FactionSaved fs = FactionSaved.get(x.s.server);
        Gt.check(fs.schisms.keySet().containsAll(x.fakes), "schisme des membres hors ligne non note");
        refusedFor(x, "clan.pioneers.people.error.already", "deja un peuple");
        x.s.done();
    }

    // ================================================================== ralliement
    static Map<UUID, Map<UUID, Integer>> copy(VillageData.Village v) {
        Map<UUID, Map<UUID, Integer>> out = new HashMap<>();
        v.register.forEach((k, m) -> out.put(k, new LinkedHashMap<>(m)));
        return out;
    }

    /**
     * Ralliement d'un vrai village (cloche et villageois) : conditions remplies, decision, trois jours, le village passe au peuple de joueurs
     * (affinites intactes, penalite chez le peuple d'origine) ; a la dissolution du peuple, il revient a son peuple d'origine.
     */
    @GameTest(template = Gt.LARGE, batch = "pioneers_peuple_ralliement", timeoutTicks = 100)
    public static void ralliement_puis_retour_a_la_dissolution(GameTestHelper h) {
        Fed x = new Fed(h, "empire", 7, 7, 7);
        BlockPos bellRel = new BlockPos(10, 2, 10);
        EconomyVillageTests.bell(x.s, bellRel);
        h.runAfterDelay(5, () -> {
            List<Villager> vs = new ArrayList<>();
            VillageData.Village village = EconomyVillageTests.village(x.s, bellRel, vs, 3);
            String origin = village.people;
            PeopleApi.People op = PeopleApi.get(x.s.server, origin);
            Gt.check(op != null && op.villagers(), "le village n'appartient pas a un peuple de villageois : " + origin);
            String people = x.secede();
            String dim = ClanApi.dim(x.s.level);
            x.s.claim(x.a, h.absolutePos(bellRel));
            for (Map<UUID, Integer> m : village.register.values()) {
                m.put(x.ra.getUUID(), 80);
            }
            Peoples.add(x.s.server, x.f, origin, 70, "gametest", true);
            FactionRules.Values v = FactionRules.get();
            Gt.equal((Object) null, FactionRules.rally(v, Rallying.conditions(x.s.server, x.f, dim, village)), "conditions du ralliement");
            Map<UUID, Map<UUID, Integer>> register = copy(village);
            long treasury = x.f.treasury;
            int gauge = Peoples.gauge(x.f, origin);
            CompoundTag data = new CompoundTag();
            data.putString("dim", dim);
            data.putLong("village", village.id);
            x.decided(FactionGov.decide(x.ra, x.f, "rally", data, ""), "rally");
            Gt.check(Rallying.inProgress(x.f), "ralliement non commence");
            if (EconomyApi.enabled()) {
                Gt.equal(treasury - v.rallyCost(), x.f.treasury, "cout du ralliement");
            }
            Rallying.dayChange(x.s.server, x.f.rallyUntil - 1);
            Gt.equal(origin, village.people, "village passe avant la fin du delai");
            Rallying.dayChange(x.s.server, x.f.rallyUntil);
            Gt.equal(people, village.people, "village non rallie au peuple de joueurs");
            Gt.equal(origin, village.previousPeople, "peuple d'origine non retenu");
            Gt.equal(gauge + v.rallyPenalty(), Peoples.gauge(x.f, origin), "penalite chez le peuple d'origine");
            Gt.equal(register, copy(village), "affinites changees par le ralliement");
            //dissolution : retour au peuple d'origine
            String name = PeopleApi.get(x.s.server, people).displayName();
            Peoples.dissolve(x.s.server, x.f, "clan.pioneers.people.reason.decline");
            Gt.equal(origin, village.people, "le village n'est pas revenu a son peuple d'origine");
            Gt.check(PeopleApi.get(x.s.server, people) == null, "peuple encore inscrit apres la dissolution");
            Gt.check(x.f.people == null || x.f.people.isEmpty(), "la faction garde son peuple");
            Gt.check(FactionSaved.get(x.s.server).reserved.containsKey(FactionRules.fold(name)), "nom du peuple non reserve");
            Gt.equal(register, copy(village), "affinites changees par le retour");
            x.s.done();
        });
    }

    // ================================================================== declin
    /**
     * Declin d'un peuple : sous 12 membres actifs, l'echeance de 14 jours est posee ; une reprise d'activite l'annule ; a l'echeance, le peuple
     * est dissous (registre, nom reserve), la faction demeure.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_peuple_declin", timeoutTicks = 40)
    public static void peuple_dissous_apres_le_declin(GameTestHelper h) {
        Fed x = new Fed(h, "empire", 7, 7, 7);
        String people = x.secede();
        FactionRules.Values v = FactionRules.get();
        long now = ClanClock.now();
        Peoples.tickMinute(x.s.server, x.f, now);
        Gt.equal(0L, x.f.declineUntil, "declin alors que la faction est active");
        x.activity(0);
        Peoples.tickMinute(x.s.server, x.f, now);
        Gt.check(x.f.declineUntil > now, "declin non pose sans membres actifs");
        Gt.check(Math.abs(x.f.declineUntil - now - v.declineMs()) < 60_000, "echeance du declin : " + (x.f.declineUntil - now));
        x.activity(ClanClock.now());
        Peoples.tickMinute(x.s.server, x.f, ClanClock.now());
        Gt.equal(0L, x.f.declineUntil, "declin non leve a la reprise d'activite");
        x.activity(0);
        Peoples.tickMinute(x.s.server, x.f, now);
        long until = x.f.declineUntil;
        Peoples.tickMinute(x.s.server, x.f, until - 1000);
        Gt.equal(people, x.f.people, "peuple dissous avant l'echeance");
        Peoples.tickMinute(x.s.server, x.f, until + 1);
        Gt.check(x.f.people == null || x.f.people.isEmpty(), "peuple non dissous a l'echeance");
        Gt.check(PeopleApi.get(x.s.server, people) == null, "peuple encore au registre");
        Gt.check(ClanData.get(x.s.server).faction(x.f.id) != null, "la faction a disparu avec son peuple");
        x.s.done();
    }
}
