package fr.pioneers.clan.core;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.treasury.ClanFinance;
import fr.pioneers.clan.vote.ClanGov;
import fr.pioneers.clan.vote.ClanVotes;
import fr.pioneers.clan.vote.Proposal;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.economy.EconomyApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : donnees des fiches de l'onglet Clan du Carnet, construites cote serveur pour UN joueur (l'onglet est dessine par un
 * autre lot) : {@code sheet} (Fiche), {@code members} (Membres), {@code treasury} (Tresor), {@code government} (Gouvernement), {@code journal}
 * (Journal, par pages de 50 lignes). Regle des identites : un membre n'est JAMAIS designe par son identifiant de compte (UUID) mais par un
 * jeton propre au clan ({@link #token}) ; le pseudo du compte n'est envoye qu'a qui a la permission de voir les identites (une Recrue ne voit
 * que les noms de personnage) ; rien de tout cela ne sort du clan. Chaque fiche tient sous 32 767 octets.
 */
public final class ClanViews {

    public static final List<String> SHEETS = List.of("sheet", "members", "treasury", "government", "journal");
    public static final int PAGE = 50;
    private static final List<String> TREASURY_KEYS = List.of("clan.pioneers.log.treasury_in", "clan.pioneers.log.treasury_out",
          "clan.pioneers.log.tax", "clan.pioneers.log.dues", "clan.pioneers.log.dividend");

    private ClanViews() {
    }

    /** Fiche demandee pour ce joueur (son propre clan) ; {@code error} si rien a montrer. */
    public static CompoundTag sheet(ServerPlayer p, String sheet) {
        return sheet(p, sheet, 0);
    }

    public static CompoundTag sheet(ServerPlayer p, String sheet, int page) {
        CompoundTag out = new CompoundTag();
        out.putString("sheet", sheet);
        //1.9 (MAJ RP, bloc 4) : horloge du serveur (les delais affiches par l'onglet Clan s'y rapportent)
        out.putLong("now", ClanClock.now());
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (!ClanCore.enabled()) {
            out.putString("error", "clan.pioneers.error.disabled");
            return out;
        }
        if (c == null) {
            out.putString("error", "clan.pioneers.error.no_clan");
            out.put("invites", invites(server, p));
            return out;
        }
        UUID me = p.getUUID();
        switch (sheet) {
            case "members" -> members(server, c, me, out);
            case "treasury" -> {
                if (!ClanRoles.has(c, me, Permission.TREASURY_VIEW)) {
                    out.putString("error", "clan.pioneers.error.permission");
                } else {
                    treasury(server, c, me, out);
                }
            }
            case "government" -> government(server, c, me, out);
            case "journal" -> journal(c, me, page, out);
            default -> fiche(server, c, me, out);
        }
        return out;
    }

    // ------------------------------------------------------------------ Fiche
    private static void fiche(MinecraftServer server, Clan c, UUID me, CompoundTag t) {
        ClanDefs defs = ClanDefs.get();
        long now = ClanClock.now();
        t.putString("token", token(c, me));
        t.putString("name", c.name);
        t.putString("solo_of", c.solo || c.name.isBlank() ? ClanMessages.character(server, c, c.representative) : "");
        t.putString("tag", c.tag);
        t.putInt("color", c.color);
        t.putString("motto", c.motto);
        t.putString("regime", c.solo ? ClanDefs.SOLO : c.regime);
        t.putBoolean("solo", c.solo);
        t.putBoolean("choose", c.extra.getBoolean(ClanLogic.CHOOSE_REGIME));
        t.putInt("level", c.level);
        t.putLong("points", c.points);
        ClanDefs.LevelDef next = defs.level(c.level + 1);
        t.putLong("next", next != null && next.level() > c.level ? next.points() : -1);
        t.putInt("members", c.members.size());
        t.putInt("max", ClanCore.maxMembers(c));
        t.putInt("active", ClanLogic.activeCount(c, now, ClanCore.activeMs()));
        t.putString("representative", ClanMessages.character(server, c, c.representative));
        t.putLong("founded", c.founded);
        t.putInt("claims", c.claimCount());
        ClanDefs.LevelDef lvl = defs.level(c.level);
        t.putInt("slots", lvl == null ? 0 : lvl.chunkload());
        Clan.Role r = c.roleOf(me);
        t.putString("role", r == null ? "" : r.id);
        t.putString("roleName", r == null ? "" : ClanRoles.displayName(r));
        t.putInt("rank", ClanRoles.rank(c, me));
        t.putBoolean("isRep", me.equals(c.representative));
        ListTag perms = new ListTag();
        for (Permission perm : Permission.values()) {
            if (ClanRoles.has(c, me, perm)) {
                perms.add(StringTag.valueOf(perm.id()));
            }
        }
        t.put("perms", perms);
        t.putInt("openVotes", ClanVotes.openOf(c.proposals).size());
        //1.9 (MAJ RP, bloc 4) : embleme (banniere de la Mairie) et echelle d'appartenance (faction, peuple) pour l'onglet Clan
        t.put("banner", c.banner.copy());
        fr.pioneers.clan.Faction f = ClanData.get(server).faction(c.faction);
        t.putString("faction", f == null ? "" : f.name);
        t.putString("people", f == null || f.people == null ? "" : f.people);
    }

    // ------------------------------------------------------------------ Membres
    private static void members(MinecraftServer server, Clan c, UUID me, CompoundTag t) {
        boolean ids = ClanRoles.has(c, me, Permission.VIEW_IDENTITIES);
        long now = ClanClock.now();
        ListTag l = new ListTag();
        for (UUID u : ClanLogic.oldestFirst(c, null)) {
            Clan.Member m = c.member(u);
            CompoundTag e = new CompoundTag();
            e.putString("token", token(c, u));
            e.putString("char", ClanMessages.character(server, c, u));
            if (ids) {
                e.putString("account", m.account);
            }
            Clan.Role r = c.roles.get(m.role);
            e.putString("role", m.role);
            e.putString("roleName", r == null ? "" : ClanRoles.displayName(r));
            e.putInt("rank", ClanRoles.rank(c, u));
            e.putInt("roleColor", r == null ? 0 : r.color);
            e.putLong("joined", m.joined);
            e.putLong("seen", m.lastSeen);
            e.putBoolean("active", now - m.lastSeen <= ClanCore.activeMs());
            e.putBoolean("online", server.getPlayerList().getPlayer(u) != null);
            e.putBoolean("rep", u.equals(c.representative));
            e.putBoolean("heir", u.equals(c.heir));
            e.putBoolean("council", c.council.contains(u));
            e.putBoolean("me", u.equals(me));
            l.add(e);
        }
        t.put("list", l);
        t.putBoolean("identities", ids);
        ListTag roles = new ListTag();
        for (Clan.Role r : ClanRoles.ordered(c)) {
            CompoundTag e = new CompoundTag();
            e.putString("id", r.id);
            e.putString("name", ClanRoles.displayName(r));
            e.putInt("rank", r.rank);
            e.putInt("color", r.color);
            e.putLong("cap", r.withdrawCap);
            ListTag ps = new ListTag();
            r.perms.forEach(p -> ps.add(StringTag.valueOf(p.id())));
            e.put("perms", ps);
            roles.add(e);
        }
        t.put("roles", roles);
        ListTag reserved = new ListTag();
        for (Permission p : Permission.values()) {
            if (ClanDefs.get().reserved(c.regime, p.id())) {
                reserved.add(StringTag.valueOf(p.id()));
            }
        }
        t.put("reserved", reserved);
        t.putInt("maxRoles", ClanCore.rules() == null ? 12 : ClanCore.rules().maxRoles());
        t.putInt("myRank", ClanRoles.rank(c, me));
    }

    // ------------------------------------------------------------------ Tresor
    private static void treasury(MinecraftServer server, Clan c, UUID me, CompoundTag t) {
        t.putBoolean("economy", EconomyApi.enabled());
        t.putLong("treasury", c.treasury);
        t.putBoolean("frozen", c.frozen);
        t.putInt("activity", c.activityTax);
        t.putInt("dues", c.dues);
        t.putInt("market", c.marketTax);
        t.putInt("capActivity", ClanFinance.cap(c, "activity"));
        t.putInt("capDues", ClanFinance.cap(c, "dues"));
        t.putInt("capMarket", ClanFinance.cap(c, "market"));
        ClanDefs.Rules r = ClanCore.rules();
        t.putDouble("spendShare", r == null ? 0.25 : r.spendShare());
        Clan.Member m = c.member(me);
        Clan.Role role = c.roleOf(me);
        long day = PioneerApi.day(server);
        t.putLong("myCap", role == null ? 0 : role.withdrawCap);
        t.putLong("myUsed", m != null && m.withdrawDay == day ? m.withdrawnToday : 0);
        t.putLong("pool", c.extra.getLong(ClanFinance.POOL));
        t.putLong("chunkloadStop", c.chunkloadStopAt);
        ListTag lines = new ListTag();
        List<CompoundTag> all = new ArrayList<>(c.journal);
        int from = Math.max(0, all.size() - PAGE);
        for (int i = all.size() - 1; i >= 0 && lines.size() < PAGE; i--) {
            if (TREASURY_KEYS.contains(all.get(i).getString("k"))) {
                lines.add(all.get(i).copy());
            }
        }
        t.put("lines", lines);
        t.putInt("from", from);
    }

    // ------------------------------------------------------------------ Gouvernement
    private static void government(MinecraftServer server, Clan c, UUID me, CompoundTag t) {
        ClanDefs defs = ClanDefs.get();
        long now = ClanClock.now();
        ClanDefs.Constitution k = defs.regime(c.solo ? ClanDefs.SOLO : c.regime);
        t.putString("regime", c.solo ? ClanDefs.SOLO : c.regime);
        t.putBoolean("choose", c.extra.getBoolean(ClanLogic.CHOOSE_REGIME));
        if (k != null) {
            CompoundTag dec = new CompoundTag();
            for (Map.Entry<String, ClanDefs.Rule> e : k.decisions().entrySet()) {
                CompoundTag d = new CompoundTag();
                d.putString("by", c.solo ? "representative" : e.getValue().by().id());
                d.putString("maj", e.getValue().majority().id());
                dec.put(e.getKey(), d);
            }
            t.put("decisions", dec);
            t.putString("repMode", k.repMode());
            t.putString("counter", c.solo ? "none" : k.counter().type());
            long last = "revolution".equals(k.counter().type()) ? c.revolutionAt : c.extra.getLong(ClanGov.COUNTER);
            t.putLong("counterReady", last <= 0 ? 0 : last + k.counter().cooldownDays() * ClanDefs.DAY);
        }
        t.putString("representative", ClanMessages.character(server, c, c.representative));
        t.putString("repToken", c.representative == null ? "" : token(c, c.representative));
        t.putString("heir", c.heir == null ? "" : ClanMessages.character(server, c, c.heir));
        ListTag council = new ListTag();
        c.council.forEach(u -> council.add(StringTag.valueOf(ClanMessages.character(server, c, u))));
        t.put("council", council);
        t.putLong("term", c.extra.getLong(ClanGov.TERM));
        t.putLong("councilTerm", c.extra.getLong(ClanGov.COUNCIL_TERM));
        t.putLong("rotateAt", c.rotateAt);
        t.putString("pending", c.pendingRegime);
        t.putLong("pendingAt", c.regimeAt);
        ClanDefs.Rules r = defs.rules;
        t.putLong("regimeReady", c.regimeChanged <= 0 || r == null ? 0 : c.regimeChanged + r.regimeCooldownDays() * ClanDefs.DAY);
        t.putLong("regimeCost", r == null ? 0 : r.regimeCost());
        t.putLong("now", now);
        //ouvertes : tout ; fermees : les 10 dernieres, sans la liste des candidats (la fiche tient sous 32 767 octets)
        ListTag open = new ListTag(), closed = new ListTag();
        for (int i = c.proposals.size() - 1; i >= 0; i--) {
            Proposal p = new Proposal(c.proposals.getCompound(i));
            if (p.open()) {
                open.add(0, proposal(server, c, p, me));
            } else if (closed.size() < 10) {
                CompoundTag e = proposal(server, c, p, me);
                e.remove("cands");
                closed.add(0, e);
            }
        }
        t.put("open", open);
        t.put("closed", closed);
    }

    private static CompoundTag proposal(MinecraftServer server, Clan c, Proposal p, UUID me) {
        CompoundTag e = new CompoundTag();
        e.putString("id", p.id());
        e.putString("type", p.type());
        e.putString("text", p.text());
        e.putString("by", p.tag.getString("by"));
        e.putString("maj", p.tag.getString("maj"));
        e.putString("status", p.status());
        e.putString("author", p.tag.getString("authorName"));
        e.putLong("opened", p.opened());
        e.putLong("closes", p.closes());
        e.putBoolean("secret", p.secret());
        e.putBoolean("election", p.election());
        e.putInt("seats", p.seats());
        e.putInt("ballots", p.ballots());
        e.putInt("electorate", p.electorate().size());
        e.putBoolean("elector", p.electorate().containsKey(me));
        e.putBoolean("voted", p.voted(me));
        Map<String, Double> tally = p.tally();
        //bulletin secret : aucun total tant que le vote est ouvert
        if (!p.secret() || !p.open()) {
            e.putDouble("yes", p.open() ? tally.getOrDefault("yes", 0.0) : p.tag.getDouble("r_yes"));
            e.putDouble("no", p.open() ? tally.getOrDefault("no", 0.0) : p.tag.getDouble("r_no"));
        }
        if (p.election()) {
            ListTag cands = new ListTag();
            for (UUID u : p.candidates()) {
                if (c.member(u) == null) {
                    continue;
                }
                CompoundTag ce = new CompoundTag();
                ce.putString("token", token(c, u));
                ce.putString("char", ClanMessages.character(server, c, u));
                if (!p.open()) {
                    ce.putDouble("votes", tally.getOrDefault(u.toString(), 0.0));
                }
                cands.add(ce);
            }
            e.put("cands", cands);
            ListTag won = new ListTag();
            for (Tag w : p.tag.getList("won", Tag.TAG_INT_ARRAY)) {
                won.add(StringTag.valueOf(ClanMessages.character(server, c, net.minecraft.nbt.NbtUtils.loadUUID(w))));
            }
            e.put("won", won);
        }
        CompoundTag d = p.data();
        if (d.hasUUID("target")) {
            e.putString("target", ClanMessages.character(server, c, d.getUUID("target")));
        }
        for (String k : List.of("kind", "value", "amount", "regime", "field", "op", "role")) {
            if (d.contains(k)) {
                e.put("d_" + k, d.get(k).copy());
            }
        }
        //1.9 (MAJ RP, bloc 4) : intitule traduisible des decisions de guerre et de traite (cle + arguments, "#" = cle de langue)
        if (d.contains("label")) {
            e.putString("label", d.getString("label"));
            e.put("labelArgs", d.getList("labelArgs", Tag.TAG_STRING).copy());
        }
        return e;
    }

    // ------------------------------------------------------------------ Journal
    private static void journal(Clan c, UUID me, int page, CompoundTag t) {
        boolean money = ClanRoles.has(c, me, Permission.TREASURY_VIEW);
        List<CompoundTag> all = new ArrayList<>();
        for (CompoundTag l : c.journal) {
            if (money || !TREASURY_KEYS.contains(l.getString("k"))) {
                all.add(l);
            }
        }
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        ListTag lines = new ListTag();
        int end = all.size() - pg * PAGE;
        for (int i = end - 1; i >= Math.max(0, end - PAGE); i--) {
            lines.add(all.get(i).copy());
        }
        t.put("lines", lines);
        t.putInt("page", pg);
        t.putInt("pages", pages);
    }

    // ------------------------------------------------------------------ invitations (joueur sans clan)
    private static ListTag invites(MinecraftServer server, ServerPlayer p) {
        ListTag l = new ListTag();
        for (ClanCore.Invite i : ClanCore.invites(p.getUUID())) {
            Clan c = ClanData.get(server).clan(i.clan());
            if (c == null) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putString("clan", c.name);
            e.putString("tag", c.tag);
            e.putInt("color", c.color);
            e.putString("by", i.inviterName());
            e.putLong("expires", i.expires());
            l.add(e);
        }
        return l;
    }

    // ------------------------------------------------------------------ jetons
    /** Jeton stable d'un membre dans un clan (le client ne recoit jamais d'UUID de compte). */
    public static String token(Clan c, UUID u) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((c.id + "/" + u).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", h[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString((c.id + "/" + u).hashCode());
        }
    }

    /** Membre designe par un jeton, ou {@code null}. */
    @Nullable
    public static UUID member(Clan c, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        for (UUID u : c.members.keySet()) {
            if (token(c, u).equals(token)) {
                return u;
            }
        }
        return null;
    }
}
