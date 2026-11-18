package fr.pioneers.society.clan;

import fr.pioneers.society.clan.territory.ZonePerms;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 4) : un clan (ou le clan solo d'un Solitaire). Toute la verite est ici, dans {@link ClanData} : identite, regime, roles
 * et membres, tresor et taxes, territoire (claims par dimension, zones, declin, scelles), chunkloading, fonctions de Sceau, propositions,
 * diplomatie et guerres, reputation de clan, journal. Les delais en temps REEL sont en millisecondes du serveur ; les points et cotisations
 * suivent le jour de jeu.
 */
public final class Clan {

    public static final int JOURNAL = 200, REP_LOG = 30;

    // ------------------------------------------------------------------ elements
    public enum Zone {
        MEMBERS, PRIVATE, OPEN
    }

    /** Un role : identifiant, nom affiche, couleur (teinture), rang 1..100, permissions, plafond de retrait journalier (jetons, -1 = aucun). */
    public static final class Role {

        public final String id;
        public String name;
        public int color;
        public int rank;
        public final Set<Permission> perms = EnumSet.noneOf(Permission.class);
        public long withdrawCap;

        public Role(String id, String name, int color, int rank, long withdrawCap) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.rank = rank;
            this.withdrawCap = withdrawCap;
        }

        public boolean has(Permission p) {
            return perms.contains(p);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putString("name", name);
            t.putInt("color", color);
            t.putInt("rank", rank);
            t.putLong("cap", withdrawCap);
            ListTag p = new ListTag();
            perms.forEach(x -> p.add(StringTag.valueOf(x.id())));
            t.put("perms", p);
            return t;
        }

        static Role load(CompoundTag t) {
            Role r = new Role(t.getString("id"), t.getString("name"), t.getInt("color"), t.getInt("rank"), t.getLong("cap"));
            for (Tag x : t.getList("perms", Tag.TAG_STRING)) {
                Permission p = Permission.byId(x.getAsString());
                if (p != null) {
                    r.perms.add(p);
                }
            }
            return r;
        }
    }

    /** Un membre : role, entree (ms), derniere connexion (ms), pseudo du compte (montre seulement selon la permission), retraits du jour. */
    public static final class Member {

        public final UUID id;
        public String role;
        public long joined, lastSeen;
        public String account = "";
        public String character = "";
        public long withdrawDay = -1, withdrawnToday;
        public long duesDay = -1;

        public Member(UUID id, String role, long now) {
            this.id = id;
            this.role = role;
            this.joined = now;
            this.lastSeen = now;
        }

        /** Actif : connecte dans les 7 derniers jours reels. */
        public boolean active(long now) {
            return now - lastSeen <= 7L * 24 * 3600 * 1000;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("role", role);
            t.putLong("joined", joined);
            t.putLong("seen", lastSeen);
            t.putString("account", account);
            t.putString("char", character);
            t.putLong("wd", withdrawDay);
            t.putLong("wt", withdrawnToday);
            t.putLong("dd", duesDay);
            return t;
        }

        static Member load(CompoundTag t) {
            Member m = new Member(t.getUUID("id"), t.getString("role"), t.getLong("joined"));
            m.lastSeen = t.getLong("seen");
            m.account = t.getString("account");
            m.character = t.getString("char");
            m.withdrawDay = t.getLong("wd");
            m.withdrawnToday = t.getLong("wt");
            m.duesDay = t.contains("dd") ? t.getLong("dd") : -1;
            return m;
        }
    }

    /** Un chunk reclame : zone (et rang minimal d'une zone privee), avant-poste, declin, scelles apres une capture, zone federale. */
    public static final class Claim {

        public Zone zone = Zone.MEMBERS;
        public int minRank;
        public boolean outpost, federal;
        /** Fin du declin (ms) ; 0 = pas en declin. */
        public long declineUntil;
        /** Conteneurs scelles jusqu'a (ms) au profit de l'ancien proprietaire. */
        public long sealedUntil;
        @Nullable
        public UUID sealedFor;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("zone", zone.name());
            t.putInt("min", minRank);
            t.putBoolean("out", outpost);
            t.putBoolean("fed", federal);
            t.putLong("decl", declineUntil);
            t.putLong("seal", sealedUntil);
            if (sealedFor != null) {
                t.putUUID("sealFor", sealedFor);
            }
            return t;
        }

        static Claim load(CompoundTag t) {
            Claim c = new Claim();
            try {
                c.zone = Zone.valueOf(t.getString("zone"));
            } catch (IllegalArgumentException e) {
                c.zone = Zone.MEMBERS;
            }
            c.minRank = t.getInt("min");
            c.outpost = t.getBoolean("out");
            c.federal = t.getBoolean("fed");
            c.declineUntil = t.getLong("decl");
            c.sealedUntil = t.getLong("seal");
            c.sealedFor = t.hasUUID("sealFor") ? t.getUUID("sealFor") : null;
            return c;
        }
    }

    // ------------------------------------------------------------------ champs
    public final UUID id;
    public String name = "", tag = "", motto = "";
    /** Teinture (0..15). */
    public int color;
    /** Regime : identifiant de constitution (democracy, empire, council, cooperative ; « solo » pour un clan solo). */
    public String regime = "empire";
    public boolean solo;
    public long founded;
    public int level = 1;
    public long points;
    public long pointsDay = -1;
    public final Map<String, Long> pointsToday = new HashMap<>();
    @Nullable
    public UUID representative, heir;
    public final List<UUID> council = new ArrayList<>();
    /** Porte-parole de la Cooperative : prochaine rotation (ms). */
    public long rotateAt;
    public final LinkedHashMap<String, Role> roles = new LinkedHashMap<>();
    public final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
    public long treasury;
    public boolean frozen;
    /** Impot d'activite (%), cotisation (jetons par membre et par jour de jeu), taxe de marche (%). */
    public int activityTax, dues, marketTax;
    /** Tresor vide : arret du chunkloading apres ce moment (ms), 0 = pas de preavis. */
    public long chunkloadStopAt;
    /** dimension -> (chunk -> claim). */
    public final Map<String, Map<Long, Claim>> claims = new HashMap<>();
    /** 1.0.3-beta (lot 16) : claims achetes au tresor (ajoutes au budget, jamais rendus : abandonner un chunk ne les rembourse pas). */
    public int boughtClaims;
    /**
     * 1.0.6 (Guillaume, 17/11/2026 : « acheter comme on le souhaite des claims et des chunkloads pour preparer le stock ») : emplacements de
     * chunkloading achetes au tresor (stock ; un chunk charge en prend un, le liberer le rend). Monde d'avant : au moins les emplacements tenus.
     */
    public int boughtChunkloads;
    /** 1.0.3-beta (lot 16) : permissions des visiteurs par zone (allies, neutres, etrangers, ennemis) ; defauts = l'ancienne regle. */
    public ZonePerms zonePerms = ZonePerms.DEFAULTS;
    /** Emplacements de chunkloading actifs : « dimension|chunk ». */
    public final List<String> chunkload = new ArrayList<>();
    /** Fonctions de Sceau : « dimension|position » -> fonction. */
    public final Map<String, String> functions = new LinkedHashMap<>();
    /** Propositions ouvertes et fermees recentes (le moteur de votes les gere). */
    public final ListTag proposals = new ListTag();
    /** Dernier refus par type de proposition (ms). */
    public final Map<String, Long> refused = new HashMap<>();
    /** Diplomatie : autre clan -> traites (NBT : type, fin, rupture). */
    public final Map<UUID, CompoundTag> treaties = new HashMap<>();
    /** Creneaux de siege (heure de debut 0..23, deux creneaux de 2 h) et changement en attente (effet apres 7 jours). */
    public int[] siegeWindows = {19, 21};
    public int[] pendingWindows;
    public long windowsFrom;
    /** Changement de regime vote, effectif apres 7 jours ; dernier changement (30 jours). */
    public String pendingRegime = "";
    public long regimeAt, regimeChanged;
    /** Revolution (Empire) : derniere tentative (ms). */
    public long revolutionAt;
    /** Banniere de la Mairie (motif = embleme). */
    public CompoundTag banner = new CompoundTag();
    @Nullable
    public UUID faction;
    /** Faction quittee : pas d'autre faction avant (ms). */
    public long factionCooldown;
    /** Reputation de clan (micro-peuple) : peuple -> -300..300, plafond journalier, journal. */
    public final Map<String, Integer> reputation = new HashMap<>();
    public long repDay = -1;
    public final Map<String, Integer> repToday = new HashMap<>();
    public final Deque<CompoundTag> repLog = new ArrayDeque<>();
    public final Deque<CompoundTag> journal = new ArrayDeque<>();
    /** Clan vide : dissolution des chunks apres (ms). */
    public long emptySince;
    /** Donnees libres des lots suivants (statistiques agregees, etc.). */
    public CompoundTag extra = new CompoundTag();

    public Clan(UUID id) {
        this.id = id;
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public Member member(UUID player) {
        return members.get(player);
    }

    @Nullable
    public Role roleOf(UUID player) {
        Member m = members.get(player);
        return m == null ? null : roles.get(m.role);
    }

    public int rankOf(UUID player) {
        Role r = roleOf(player);
        return r == null ? 0 : r.rank;
    }

    public boolean can(UUID player, Permission p) {
        Role r = roleOf(player);
        return r != null && r.has(p);
    }

    public int activeCount(long now) {
        int n = 0;
        for (Member m : members.values()) {
            if (m.active(now)) {
                n++;
            }
        }
        return n;
    }

    public int claimCount() {
        int n = 0;
        for (Map<Long, Claim> m : claims.values()) {
            for (Claim c : m.values()) {
                if (!c.federal) {
                    n += c.outpost ? 2 : 1;
                }
            }
        }
        return n;
    }

    @Nullable
    public Claim claim(String dim, long chunk) {
        Map<Long, Claim> m = claims.get(dim);
        return m == null ? null : m.get(chunk);
    }

    public void log(long day, String key, String... args) {
        CompoundTag t = new CompoundTag();
        t.putLong("t", System.currentTimeMillis());
        t.putLong("d", day);
        t.putString("k", key);
        ListTag a = new ListTag();
        for (String s : args) {
            a.add(StringTag.valueOf(s));
        }
        t.put("a", a);
        journal.addLast(t);
        while (journal.size() > JOURNAL) {
            journal.pollFirst();
        }
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("tag", tag);
        t.putString("motto", motto);
        t.putInt("color", color);
        t.putString("regime", regime);
        t.putBoolean("solo", solo);
        t.putLong("founded", founded);
        t.putInt("level", level);
        t.putLong("points", points);
        t.putLong("pday", pointsDay);
        CompoundTag pt = new CompoundTag();
        pointsToday.forEach(pt::putLong);
        t.put("ptoday", pt);
        if (representative != null) {
            t.putUUID("rep", representative);
        }
        if (heir != null) {
            t.putUUID("heir", heir);
        }
        t.put("council", uuids(council));
        t.putLong("rotate", rotateAt);
        ListTag rl = new ListTag();
        roles.values().forEach(r -> rl.add(r.save()));
        t.put("roles", rl);
        ListTag ml = new ListTag();
        members.values().forEach(m -> ml.add(m.save()));
        t.put("members", ml);
        t.putLong("treasury", treasury);
        t.putBoolean("frozen", frozen);
        t.putInt("atax", activityTax);
        t.putInt("dues", dues);
        t.putInt("mtax", marketTax);
        t.putLong("clStop", chunkloadStopAt);
        CompoundTag cl = new CompoundTag();
        claims.forEach((dim, m) -> {
            ListTag l = new ListTag();
            m.forEach((chunk, c) -> {
                CompoundTag x = c.save();
                x.putLong("c", chunk);
                l.add(x);
            });
            cl.put(dim, l);
        });
        t.put("claims", cl);
        t.putInt("bought", boughtClaims);
        t.putInt("boughtCl", boughtChunkloads);
        if (!zonePerms.isDefault()) {
            t.putIntArray("zperm", zonePerms.toArray());
        }
        ListTag ld = new ListTag();
        chunkload.forEach(s -> ld.add(StringTag.valueOf(s)));
        t.put("load", ld);
        CompoundTag fn = new CompoundTag();
        functions.forEach(fn::putString);
        t.put("functions", fn);
        t.put("proposals", proposals.copy());
        CompoundTag rf = new CompoundTag();
        refused.forEach(rf::putLong);
        t.put("refused", rf);
        ListTag tr = new ListTag();
        treaties.forEach((u, c) -> {
            CompoundTag x = c.copy();
            x.putUUID("with", u);
            tr.add(x);
        });
        t.put("treaties", tr);
        t.putIntArray("windows", siegeWindows);
        if (pendingWindows != null) {
            t.putIntArray("pwindows", pendingWindows);
        }
        t.putLong("wfrom", windowsFrom);
        t.putString("pregime", pendingRegime);
        t.putLong("regimeAt", regimeAt);
        t.putLong("regimeChanged", regimeChanged);
        t.putLong("revolution", revolutionAt);
        t.put("banner", banner.copy());
        if (faction != null) {
            t.putUUID("faction", faction);
        }
        t.putLong("fcool", factionCooldown);
        CompoundTag rep = new CompoundTag();
        reputation.forEach(rep::putInt);
        t.put("rep", rep);
        t.putLong("repDay", repDay);
        CompoundTag rt = new CompoundTag();
        repToday.forEach(rt::putInt);
        t.put("repToday", rt);
        ListTag rlog = new ListTag();
        rlog.addAll(repLog);
        t.put("repLog", rlog);
        ListTag j = new ListTag();
        j.addAll(journal);
        t.put("journal", j);
        t.putLong("empty", emptySince);
        t.put("extra", extra.copy());
        return t;
    }

    public static Clan load(CompoundTag t) {
        Clan c = new Clan(t.getUUID("id"));
        c.name = t.getString("name");
        c.tag = t.getString("tag");
        c.motto = t.getString("motto");
        c.color = t.getInt("color");
        c.regime = t.getString("regime");
        c.solo = t.getBoolean("solo");
        c.founded = t.getLong("founded");
        c.level = Math.max(1, t.getInt("level"));
        c.points = t.getLong("points");
        c.pointsDay = t.getLong("pday");
        CompoundTag pt = t.getCompound("ptoday");
        pt.getAllKeys().forEach(k -> c.pointsToday.put(k, pt.getLong(k)));
        c.representative = t.hasUUID("rep") ? t.getUUID("rep") : null;
        c.heir = t.hasUUID("heir") ? t.getUUID("heir") : null;
        c.council.addAll(readUuids(t.getList("council", Tag.TAG_INT_ARRAY)));
        c.rotateAt = t.getLong("rotate");
        for (Tag x : t.getList("roles", Tag.TAG_COMPOUND)) {
            Role r = Role.load((CompoundTag) x);
            c.roles.put(r.id, r);
        }
        for (Tag x : t.getList("members", Tag.TAG_COMPOUND)) {
            Member m = Member.load((CompoundTag) x);
            c.members.put(m.id, m);
        }
        c.treasury = Math.max(0, t.getLong("treasury"));
        c.frozen = t.getBoolean("frozen");
        c.activityTax = t.getInt("atax");
        c.dues = t.getInt("dues");
        c.marketTax = t.getInt("mtax");
        c.chunkloadStopAt = t.getLong("clStop");
        CompoundTag cl = t.getCompound("claims");
        for (String dim : cl.getAllKeys()) {
            Map<Long, Claim> m = new HashMap<>();
            for (Tag x : cl.getList(dim, Tag.TAG_COMPOUND)) {
                CompoundTag ct = (CompoundTag) x;
                m.put(ct.getLong("c"), Claim.load(ct));
            }
            c.claims.put(dim, m);
        }
        c.boughtClaims = Math.max(0, t.getInt("bought"));
        c.zonePerms = t.contains("zperm") ? ZonePerms.of(t.getIntArray("zperm"))
              : ZonePerms.DEFAULTS;
        for (Tag x : t.getList("load", Tag.TAG_STRING)) {
            c.chunkload.add(x.getAsString());
        }
        c.boughtChunkloads = Math.max(Math.max(0, t.getInt("boughtCl")), c.chunkload.size());
        CompoundTag fn = t.getCompound("functions");
        fn.getAllKeys().forEach(k -> c.functions.put(k, fn.getString(k)));
        c.proposals.addAll(t.getList("proposals", Tag.TAG_COMPOUND));
        CompoundTag rf = t.getCompound("refused");
        rf.getAllKeys().forEach(k -> c.refused.put(k, rf.getLong(k)));
        for (Tag x : t.getList("treaties", Tag.TAG_COMPOUND)) {
            CompoundTag ct = (CompoundTag) x;
            c.treaties.put(ct.getUUID("with"), ct);
        }
        if (t.contains("windows")) {
            c.siegeWindows = t.getIntArray("windows");
        }
        c.pendingWindows = t.contains("pwindows") ? t.getIntArray("pwindows") : null;
        c.windowsFrom = t.getLong("wfrom");
        c.pendingRegime = t.getString("pregime");
        c.regimeAt = t.getLong("regimeAt");
        c.regimeChanged = t.getLong("regimeChanged");
        c.revolutionAt = t.getLong("revolution");
        c.banner = t.getCompound("banner");
        c.faction = t.hasUUID("faction") ? t.getUUID("faction") : null;
        c.factionCooldown = t.getLong("fcool");
        CompoundTag rep = t.getCompound("rep");
        rep.getAllKeys().forEach(k -> c.reputation.put(k, rep.getInt(k)));
        c.repDay = t.getLong("repDay");
        CompoundTag rt = t.getCompound("repToday");
        rt.getAllKeys().forEach(k -> c.repToday.put(k, rt.getInt(k)));
        for (Tag x : t.getList("repLog", Tag.TAG_COMPOUND)) {
            c.repLog.add((CompoundTag) x);
        }
        for (Tag x : t.getList("journal", Tag.TAG_COMPOUND)) {
            c.journal.add((CompoundTag) x);
        }
        c.emptySince = t.getLong("empty");
        c.extra = t.getCompound("extra");
        return c;
    }

    static ListTag uuids(List<UUID> list) {
        ListTag l = new ListTag();
        list.forEach(u -> l.add(net.minecraft.nbt.NbtUtils.createUUID(u)));
        return l;
    }

    static List<UUID> readUuids(ListTag l) {
        List<UUID> out = new ArrayList<>();
        for (Tag x : l) {
            out.add(net.minecraft.nbt.NbtUtils.loadUUID(x));
        }
        return out;
    }
}
