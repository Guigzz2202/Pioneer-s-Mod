package fr.pioneers.society.clan.vote;

import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * 1.9 (MAJ RP, bloc 4) : moteur de votes des clans, reutilisable par les factions (portee {@link Scope#FACTION}, ponderation plafonnee par
 * clan). Une proposition a un type, un texte, une duree (24 h reelles par defaut), un corps electoral fige a l'ouverture ; un clan n'a qu'une
 * proposition ouverte par type ; un type refuse ne se represente pas avant 24 h. Les propositions sont rangees dans une FILE triee par date de
 * fermeture, verifiee une fois par seconde ({@link #poll}) : jamais de balayage des clans par tick ; chaque bulletin reevalue sa proposition
 * (fermeture anticipee des que le resultat est acquis). A la fermeture, l'EFFET du type est execute par un registre ({@link #register}) : les
 * autres agents y branchent la guerre, les traites, les depenses federales. Tout resultat est inscrit au journal du clan et a l'audit.
 */
public final class ClanVotes {

    public enum Scope {
        CLAN, FACTION
    }

    /** Une decision a appliquer (vote acquis, ou decision directe du Representant quand le regime la lui donne). */
    public record Decision(String type, Scope scope, @Nullable Clan clan, @Nullable Faction faction, @Nullable UUID author, CompoundTag data,
                           boolean voted, @Nullable Proposal proposal, List<UUID> winners) {
    }

    /** Effet d'un type de proposition : renvoie faux si la decision ne peut plus s'appliquer (le vote est alors marque « echoue »). */
    @FunctionalInterface
    public interface Effect {

        boolean apply(MinecraftServer server, Decision d);
    }

    /** Ce qu'on ouvre : type, portee, proprietaire (clan ou faction), corps electoral (electeur -&gt; groupe), majorite, options. */
    public static final class Spec {

        public final String type;
        public final Scope scope;
        public final UUID owner;
        public ClanDefs.Majority majority = ClanDefs.Majority.SIMPLE;
        public boolean electorateBase;
        public final Map<UUID, String> electorate = new LinkedHashMap<>();
        /** Part maximale des voix d'un groupe (factions : 0,4) ; 0 = un joueur une voix. */
        public double weightCap;
        public boolean secret, election;
        public int seats = 1;
        public final List<UUID> candidates = new ArrayList<>();
        public CompoundTag data = new CompoundTag();
        public String text = "";
        @Nullable
        public UUID author;
        public String authorName = "";
        /** Qui vote : « members », « council », « delegates » (affichage). */
        public String by = "members";
        /** Duree (ms) ; 0 = celle des regles (24 h). */
        public long duration;

        public Spec(String type, Scope scope, UUID owner) {
            this.type = type;
            this.scope = scope;
            this.owner = owner;
        }
    }

    public record Opened(@Nullable String error, @Nullable String id) {
    }

    private record Entry(long closes, Scope scope, UUID owner, String id) {
    }

    private static final Map<String, Effect> EFFECTS = new HashMap<>();
    private static final PriorityQueue<Entry> QUEUE = new PriorityQueue<>((a, b) -> Long.compare(a.closes, b.closes));

    private ClanVotes() {
    }

    // ------------------------------------------------------------------ registre des effets
    /** Branche l'effet d'un type de proposition (remplace l'effet precedent du meme type). */
    public static synchronized void register(String type, Effect effect) {
        EFFECTS.put(type, effect);
    }

    @Nullable
    public static synchronized Effect effect(String type) {
        return EFFECTS.get(type);
    }

    /** Decision directe (le regime la donne au Representant) : l'effet s'applique tout de suite, journal et audit compris. */
    public static boolean applyDirect(MinecraftServer server, String type, @Nullable Clan clan, @Nullable Faction faction, @Nullable UUID author,
                                      CompoundTag data) {
        Effect e = effect(type);
        if (e == null) {
            return false;
        }
        boolean ok = e.apply(server, new Decision(type, faction != null ? Scope.FACTION : Scope.CLAN, clan, faction, author, data, false, null,
              List.of()));
        ClanAudit.write(server, "decision", clan, type + (ok ? " appliquee" : " refusee") + " par " + author + " " + data);
        ClanData.get(server).changed();
        return ok;
    }

    // ------------------------------------------------------------------ ouverture
    public static Opened open(MinecraftServer server, Spec s) {
        ClanDefs.Rules rules = ClanDefs.get().rules;
        if (rules == null) {
            return new Opened("clan.pioneers.error.defs", null);
        }
        ClanData data = ClanData.get(server);
        Clan clan = s.scope == Scope.CLAN ? data.clan(s.owner) : null;
        Faction faction = s.scope == Scope.FACTION ? data.faction(s.owner) : null;
        if (clan == null && faction == null) {
            return new Opened("clan.pioneers.error.no_clan", null);
        }
        ListTag list = clan != null ? clan.proposals : faction.proposals;
        Map<String, Long> refused = clan != null ? clan.refused : faction.refused;
        long now = ClanClock.now();
        for (Tag t : list) {
            Proposal p = new Proposal((CompoundTag) t);
            if (p.open() && p.type().equals(s.type)) {
                return new Opened("clan.pioneers.error.vote_open", null);
            }
        }
        Long last = refused.get(s.type);
        if (last != null && now - last < rules.retryHours() * ClanDefs.HOUR) {
            return new Opened("clan.pioneers.error.vote_retry", null);
        }
        if (s.electorate.isEmpty()) {
            return new Opened("clan.pioneers.error.vote_empty", null);
        }
        CompoundTag t = new CompoundTag();
        String id;
        do {
            id = Integer.toHexString(0x100000 + ThreadLocalRandom.current().nextInt(0xF00000));
        } while (find(list, id) != null);
        t.putString("id", id);
        t.putString("type", s.type);
        t.putString("scope", s.scope == Scope.FACTION ? "faction" : "clan");
        t.putUUID("owner", s.owner);
        t.putString("by", s.by);
        t.putString("maj", s.majority.id());
        t.putBoolean("base", s.electorateBase);
        t.putBoolean("secret", s.secret);
        t.putBoolean("election", s.election);
        t.putInt("seats", Math.max(1, s.seats));
        String text = s.text == null ? "" : s.text.trim();
        if (text.length() > rules.voteTextMax()) {
            text = text.substring(0, rules.voteTextMax());
        }
        t.putString("text", text);
        t.put("data", s.data.copy());
        if (s.author != null) {
            t.putUUID("author", s.author);
        }
        t.putString("authorName", s.authorName);
        t.putLong("opened", now);
        long closes = now + (s.duration > 0 ? s.duration : rules.voteMs());
        t.putLong("closes", closes);
        t.putString("status", Proposal.OPEN);
        Map<UUID, Double> weights = VoteMath.weights(s.electorate, s.weightCap);
        ListTag el = new ListTag();
        s.electorate.forEach((u, g) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("u", u);
            e.putString("g", g);
            e.putDouble("w", weights.getOrDefault(u, 1.0));
            el.add(e);
        });
        t.put("elect", el);
        ListTag cands = new ListTag();
        s.candidates.forEach(u -> cands.add(NbtUtils.createUUID(u)));
        t.put("cands", cands);
        list.add(t);
        synchronized (QUEUE) {
            QUEUE.add(new Entry(closes, s.scope, s.owner, id));
        }
        long day = PioneerApi.day(server);
        Component typeName = Component.translatable("clan.pioneers.vote.type." + s.type);
        Component msg = text.isEmpty() ? Component.translatable("clan.pioneers.vote.opened_plain", typeName, rules.voteHours())
              : Component.translatable("clan.pioneers.vote.opened", typeName, text, rules.voteHours());
        if (clan != null) {
            logLine(clan, null, day, "clan.pioneers.log.vote_open", s.type, text, s.authorName);
            ClanMessages.members(server, clan, null, ClanMessages.tagged(clan, msg));
        } else {
            logLine(null, faction, day, "clan.pioneers.log.vote_open", s.type, text, s.authorName);
            ClanMessages.delegates(server, faction, msg);
        }
        ClanAudit.write(server, "vote", clan, "ouverture " + id + " " + s.type + " (" + s.by + ", " + s.majority.id() + ") par " + s.author
              + (faction != null ? " faction " + faction.id : "") + " : " + text);
        data.changed();
        return new Opened(null, id);
    }

    // ------------------------------------------------------------------ bulletins
    /**
     * Un electeur vote ({@code choice} : yes, no, abstain, ou l'identifiant d'un candidat). Renvoie la cle de langue du refus, ou {@code null}.
     */
    @Nullable
    public static String vote(ServerPlayer player, Scope scope, UUID owner, String id, String choice) {
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        Clan clan = scope == Scope.CLAN ? data.clan(owner) : null;
        Faction faction = scope == Scope.FACTION ? data.faction(owner) : null;
        if (clan == null && faction == null) {
            return "clan.pioneers.error.no_clan";
        }
        Proposal p = find(clan != null ? clan.proposals : faction.proposals, id);
        if (p == null || !p.open()) {
            return "clan.pioneers.error.vote_closed";
        }
        UUID u = player.getUUID();
        Map<UUID, Double> el = p.electorate();
        if (!el.containsKey(u) || !eligible(data, p, u)) {
            return "clan.pioneers.error.vote_not_elector";
        }
        if (p.voted(u)) {
            return "clan.pioneers.error.vote_twice";
        }
        if (p.election()) {
            boolean ok = false;
            for (UUID c : p.candidates()) {
                ok |= c.toString().equals(choice);
            }
            if (!ok) {
                return "clan.pioneers.error.vote_choice";
            }
        } else if (!choice.equals("yes") && !choice.equals("no") && !choice.equals("abstain")) {
            return "clan.pioneers.error.vote_choice";
        }
        long now = ClanClock.now();
        p.cast(u, choice, el.get(u));
        Clan mine = data.clanOf(u);
        if (mine != null && mine.member(u) != null) {
            mine.member(u).lastSeen = now;
        }
        player.level().playSound(null, player.blockPosition(), RpSounds.BALLOT.get(), SoundSource.PLAYERS, 0.8F, 1F);
        ClanAudit.write(server, "vote", clan, "bulletin " + id + " " + p.type() + (p.secret() ? " (secret)" : " : " + player.getGameProfile().getName()
              + " -> " + choice));
        evaluate(server, clan, faction, p, now, false);
        data.changed();
        return null;
    }

    /** L'electeur fait-il encore partie du corps (toujours membre du clan, ou de son clan dans la faction) ? */
    private static boolean eligible(ClanData data, Proposal p, UUID u) {
        String g = group(p, u);
        Clan c = data.clanOf(u);
        if (c == null || g == null) {
            return false;
        }
        if (p.scope() == Scope.CLAN) {
            return c.id.toString().equals(g);
        }
        return c.id.toString().equals(g) && c.faction != null && p.tag.hasUUID("owner") && c.faction.equals(p.tag.getUUID("owner"));
    }

    @Nullable
    private static String group(Proposal p, UUID u) {
        for (Tag t : p.tag.getList("elect", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.getUUID("u").equals(u)) {
                return e.getString("g");
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ evaluation et fermeture
    private static void evaluate(MinecraftServer server, @Nullable Clan clan, @Nullable Faction faction, Proposal p, long now, boolean deadline) {
        ClanDefs.Rules rules = ClanDefs.get().rules;
        if (rules == null || !p.open()) {
            return;
        }
        ClanData data = ClanData.get(server);
        long activeMs = rules.activeDays() * ClanDefs.DAY;
        int eligible = 0, active = 0;
        double remaining = 0;
        for (Map.Entry<UUID, Double> e : p.electorate().entrySet()) {
            UUID u = e.getKey();
            if (!eligible(data, p, u)) {
                continue;
            }
            eligible++;
            Clan c = data.clanOf(u);
            Clan.Member m = c == null ? null : c.member(u);
            if (m != null && now - m.lastSeen <= activeMs) {
                active++;
            }
            if (!p.voted(u)) {
                remaining += e.getValue();
            }
        }
        Map<String, Double> tally = p.tally();
        int ballots = p.ballots();
        if (p.election()) {
            Map<UUID, Double> votes = new LinkedHashMap<>();
            for (UUID c : p.candidates()) {
                votes.put(c, tally.getOrDefault(c.toString(), 0.0));
            }
            List<UUID> won = VoteMath.elect(votes, p.seats(), p.candidates(), ballots, active, remaining, rules.quorum(), deadline);
            if (won != null) {
                finish(server, clan, faction, p, won.isEmpty() ? Proposal.REJECTED : Proposal.PASSED, now, won);
            }
            return;
        }
        ClanDefs.Majority m = ClanDefs.Majority.of(p.tag.getString("maj"));
        VoteMath.Outcome o = VoteMath.evaluate(m == null ? ClanDefs.Majority.SIMPLE : m, p.tag.getBoolean("base"), tally.getOrDefault("yes", 0.0),
              tally.getOrDefault("no", 0.0), ballots, eligible, active, remaining, rules.quorum(), deadline);
        if (o != VoteMath.Outcome.OPEN) {
            finish(server, clan, faction, p, o == VoteMath.Outcome.PASSED ? Proposal.PASSED : Proposal.REJECTED, now, List.of());
        }
    }

    private static void finish(MinecraftServer server, @Nullable Clan clan, @Nullable Faction faction, Proposal p, String status, long now,
                               List<UUID> winners) {
        p.close(status, now);
        Map<String, Double> tally = p.tally();
        p.tag.putDouble("r_yes", tally.getOrDefault("yes", 0.0));
        p.tag.putDouble("r_no", tally.getOrDefault("no", 0.0));
        p.tag.putInt("r_ballots", p.ballots());
        ListTag won = new ListTag();
        winners.forEach(u -> won.add(NbtUtils.createUUID(u)));
        p.tag.put("won", won);
        Map<String, Long> refused = clan != null ? clan.refused : faction.refused;
        String result = status;
        if (Proposal.PASSED.equals(status)) {
            Effect e = effect(p.type());
            boolean ok = false;
            try {
                ok = e != null && e.apply(server, new Decision(p.type(), p.scope(), clan, faction, p.author(), p.data(), true, p, winners));
            } catch (RuntimeException ex) {
                fr.pioneers.Pioneers.LOGGER.warn("Clans : effet du vote {} ({}) en echec : {}", p.id(), p.type(), ex.toString());
            }
            if (!ok) {
                p.close(Proposal.FAILED, now);
                result = Proposal.FAILED;
            }
        } else if (!p.election()) {
            refused.put(p.type(), now);
        }
        long day = PioneerApi.day(server);
        String key = switch (result) {
            case Proposal.PASSED -> "clan.pioneers.log.vote_passed";
            case Proposal.REJECTED -> "clan.pioneers.log.vote_rejected";
            default -> "clan.pioneers.log.vote_failed";
        };
        String counts = p.election() ? winners.isEmpty() ? "#clan.pioneers.vote.no_quorum" : names(server, clan, winners)
              : fmt(p.tag.getDouble("r_yes")) + " / " + fmt(p.tag.getDouble("r_no"));
        Object shown = ClanMessages.arg(counts);
        Component type = Component.translatable("clan.pioneers.vote.type." + p.type());
        Component msg = (p.text().isEmpty() ? Component.translatable("clan.pioneers.vote." + result + "_plain", type, shown)
              : Component.translatable("clan.pioneers.vote." + result, type, p.text(), shown))
              .withStyle(Proposal.PASSED.equals(result) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        logLine(clan, faction, day, key, p.type(), p.text(), counts);
        if (clan != null) {
            ClanMessages.members(server, clan, null, ClanMessages.tagged(clan, msg));
        } else {
            ClanMessages.delegates(server, faction, msg);
        }
        ClanAudit.write(server, "vote", clan, "fermeture " + p.id() + " " + p.type() + " : " + result + " (" + counts + ", " + p.ballots()
              + " bulletins)" + (faction != null ? " faction " + faction.id : ""));
        trim(clan != null ? clan.proposals : faction.proposals);
        ClanData.get(server).changed();
    }

    private static String names(MinecraftServer server, @Nullable Clan clan, List<UUID> winners) {
        ClanData data = ClanData.get(server);
        List<String> out = new ArrayList<>();
        for (UUID u : winners) {
            Clan c = clan != null ? clan : data.clanOf(u);
            out.add(c == null ? "?" : ClanMessages.character(server, c, u));
        }
        return String.join(", ", out);
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Garde les propositions ouvertes et les N dernieres fermees. */
    private static void trim(ListTag list) {
        ClanDefs.Rules rules = ClanDefs.get().rules;
        int keep = rules == null ? 20 : rules.voteHistory();
        int closed = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Proposal p = new Proposal(list.getCompound(i));
            if (!p.open() && ++closed > keep) {
                list.remove(i);
            }
        }
    }

    /** Annule une proposition ouverte (clan dissous, retour au solo, regime change, cible partie...). */
    public static void cancel(MinecraftServer server, @Nullable Clan clan, @Nullable Faction faction, String id, String reasonKey) {
        ListTag list = clan != null ? clan.proposals : faction != null ? faction.proposals : null;
        Proposal p = list == null ? null : find(list, id);
        if (p == null || !p.open()) {
            return;
        }
        long now = ClanClock.now();
        p.close(Proposal.CANCELLED, now);
        p.tag.putString("why", reasonKey);
        logLine(clan, faction, PioneerApi.day(server), "clan.pioneers.log.vote_cancelled", p.type(), p.text(), "#" + reasonKey);
        ClanAudit.write(server, "vote", clan, "annulation " + id + " " + p.type() + " (" + reasonKey + ")");
        trim(list);
        ClanData.get(server).changed();
    }

    /** Annule toutes les propositions ouvertes d'un clan (sauf celles dont le type est dans {@code keep}). */
    public static void cancelAll(MinecraftServer server, Clan clan, String reasonKey, String... keep) {
        for (Proposal p : openOf(clan.proposals)) {
            if (!java.util.Arrays.asList(keep).contains(p.type())) {
                cancel(server, clan, null, p.id(), reasonKey);
            }
        }
    }

    // ------------------------------------------------------------------ file
    /** Une fois par seconde : ferme les propositions arrivees a echeance (la tete de la file seulement). */
    public static void poll(MinecraftServer server) {
        long now = ClanClock.now();
        ClanData data = null;
        while (true) {
            Entry e;
            synchronized (QUEUE) {
                e = QUEUE.peek();
                if (e == null || e.closes > now) {
                    return;
                }
                QUEUE.poll();
            }
            if (data == null) {
                data = ClanData.get(server);
            }
            Clan clan = e.scope == Scope.CLAN ? data.clan(e.owner) : null;
            Faction faction = e.scope == Scope.FACTION ? data.faction(e.owner) : null;
            if (clan == null && faction == null) {
                continue;
            }
            Proposal p = find(clan != null ? clan.proposals : faction.proposals, e.id);
            if (p != null && p.open()) {
                evaluate(server, clan, faction, p, now, true);
            }
        }
    }

    /** Reconstruit la file au demarrage du serveur depuis les propositions ouvertes. */
    public static void rebuild(MinecraftServer server) {
        synchronized (QUEUE) {
            QUEUE.clear();
            ClanData data = ClanData.get(server);
            for (Clan c : data.clans()) {
                for (Proposal p : openOf(c.proposals)) {
                    QUEUE.add(new Entry(p.closes(), Scope.CLAN, c.id, p.id()));
                }
            }
            for (Faction f : data.factions()) {
                for (Proposal p : openOf(f.proposals)) {
                    QUEUE.add(new Entry(p.closes(), Scope.FACTION, f.id, p.id()));
                }
            }
        }
    }

    public static void clear() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static Proposal find(ListTag list, String id) {
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            if (c.getString("id").equals(id)) {
                return new Proposal(c);
            }
        }
        return null;
    }

    public static List<Proposal> openOf(ListTag list) {
        List<Proposal> out = new ArrayList<>();
        for (Tag t : list) {
            Proposal p = new Proposal((CompoundTag) t);
            if (p.open()) {
                out.add(p);
            }
        }
        return out;
    }

    @Nullable
    public static Proposal openOfType(ListTag list, String type) {
        for (Proposal p : openOf(list)) {
            if (p.type().equals(type)) {
                return p;
            }
        }
        return null;
    }

    /** Ligne de journal d'un vote : type, texte (variante « _plain » sans texte), dernier argument. */
    private static void logLine(@Nullable Clan clan, @Nullable Faction faction, long day, String key, String type, String text, String last) {
        String t = "#clan.pioneers.vote.type." + type;
        if (clan != null) {
            if (text.isEmpty()) {
                clan.log(day, key + "_plain", t, last);
            } else {
                clan.log(day, key, t, text, last);
            }
        } else if (faction != null) {
            if (text.isEmpty()) {
                log(faction, day, key + "_plain", t, last);
            } else {
                log(faction, day, key, t, text, last);
            }
        }
    }

    private static void log(Faction f, long day, String key, String... args) {
        CompoundTag t = new CompoundTag();
        t.putLong("t", ClanClock.now());
        t.putLong("d", day);
        t.putString("k", key);
        ListTag a = new ListTag();
        for (String s : args) {
            a.add(StringTag.valueOf(s));
        }
        t.put("a", a);
        f.journal.add(t);
        while (f.journal.size() > Clan.JOURNAL) {
            f.journal.remove(0);
        }
    }
}
