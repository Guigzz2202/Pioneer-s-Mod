package fr.pioneers.society.clan.core;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.ProfileSync;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.society.economy.EconomyApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * 1.9 (MAJ RP, bloc 4) : fondations des clans cote serveur : clan solo du Solitaire, fondation par la Charte, invitations (10 minutes),
 * entree (le clan solo d'un Solitaire qui rejoint est ABSORBE : claims, fonctions, chunkloading, points et tresor le suivent), depart,
 * exclusion, 3 jours reels avant un autre clan, conversion solo -&gt; clan ordinaire et retour, succession, clan vide (tresor detruit, claims
 * liberes apres 7 jours), cache du profil ({@code PioneerProfile.clan}) et derniere connexion des membres. Toute la logique pure est dans
 * {@link ClanLogic} et {@link ClanRoles} ; ici : donnees de monde, index, messages, journal, audit, evenements.
 */
public final class ClanCore {

    /** Invitation en attente (memoire seulement : 10 minutes). */
    public record Invite(UUID clan, UUID inviter, String inviterName, long expires) {
    }

    private static final Map<UUID, List<Invite>> INVITES = new HashMap<>();

    private ClanCore() {
    }

    /** Clans actifs (interrupteur) ET definitions chargees. */
    public static boolean enabled() {
        return ClanApi.enabled() && ClanDefs.get().ready();
    }

    public static ClanDefs.Rules rules() {
        return ClanDefs.get().rules;
    }

    public static long activeMs() {
        ClanDefs.Rules r = rules();
        return (r == null ? 7 : r.activeDays()) * ClanDefs.DAY;
    }

    public static int maxMembers(Clan c) {
        return ClanLogic.maxMembers(c, ClanDefs.get(), PiCommonConfig.get(RpConfig.MAX_MEMBERS, 40));
    }

    // ------------------------------------------------------------------ clan solo
    /**
     * Clan du joueur ; s'il n'en a aucun, cree a la demande son CLAN SOLO (Solitaire : sans Charte, sans cout, regime « solo » qui decide
     * comme un Empire). Appele par le territoire au premier claim. {@code null} si les clans sont coupes.
     */
    @Nullable
    public static Clan soloFor(ServerPlayer player) {
        if (!enabled()) {
            return null;
        }
        MinecraftServer server = player.getServer();
        ClanData data = ClanData.get(server);
        Clan c = data.clanOf(player.getUUID());
        if (c != null) {
            return c;
        }
        long now = ClanClock.now();
        Clan s = new Clan(UUID.randomUUID());
        s.solo = true;
        s.regime = ClanDefs.SOLO;
        s.founded = now;
        s.level = 1;
        ClanRoles.installDefaults(s, ClanDefs.get());
        ClanLogic.addMember(s, player.getUUID(), player.getGameProfile().getName(), PioneerCap.get(player).name, now, Integer.MAX_VALUE);
        data.put(s);
        cache(player, s);
        s.log(PioneerApi.day(server), "clan.pioneers.log.solo_created", ClanMessages.character(player));
        ClanAudit.write(server, "solo", s, "clan solo cree pour " + player.getGameProfile().getName());
        MinecraftForge.EVENT_BUS.post(new ClanEvent.Created(server, s));
        return s;
    }

    // ------------------------------------------------------------------ fondation (Charte)
    /** Le joueur peut-il fonder ou signer (pas dans un clan ordinaire, pas en delai de ralliement) ? Cle du refus ou {@code null}. */
    @Nullable
    public static String canEnter(ServerPlayer p) {
        ClanData data = ClanData.get(p.getServer());
        Clan c = data.clanOf(p.getUUID());
        if (c != null && !c.solo) {
            return "clan.pioneers.error.already_member";
        }
        Long until = data.cooldown.get(p.getUUID());
        if (until != null && until > ClanClock.now()) {
            return "clan.pioneers.error.cooldown";
        }
        return null;
    }

    /** Nombre de clans ordinaires (les clans solo ne comptent pas dans le plafond du serveur). */
    public static int ordinaryClans(MinecraftServer server) {
        int n = 0;
        for (Clan c : ClanData.get(server).clans()) {
            if (!c.solo && !c.members.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Nom deja pris (insensible a la casse, clans vides compris tant qu'ils ne sont pas dissous) ? */
    public static boolean nameTaken(MinecraftServer server, String name, @Nullable UUID except) {
        for (Clan c : ClanData.get(server).clans()) {
            if (!c.id.equals(except) && c.name != null && !c.name.isBlank() && c.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fonde le clan (Charte scellee, tout deja verifie et paye) : le clan solo du fondateur devient le clan (ses claims le suivent), les
     * signataires entrent (leurs clans solo sont absorbes). Le fondateur est le premier Representant.
     */
    public static Clan found(MinecraftServer server, ServerPlayer founder, List<ServerPlayer> signers, String name, String tag, int color,
                             String motto, String regime) {
        ClanData data = ClanData.get(server);
        ClanDefs defs = ClanDefs.get();
        long now = ClanClock.now();
        Clan c = data.clanOf(founder.getUUID());
        if (c == null) {
            c = new Clan(UUID.randomUUID());
            ClanRoles.installDefaults(c, defs);
            ClanLogic.addMember(c, founder.getUUID(), founder.getGameProfile().getName(), PioneerCap.get(founder).name, now, Integer.MAX_VALUE);
            data.put(c);
        }
        c.solo = false;
        c.name = name;
        c.tag = tag;
        c.color = color;
        c.motto = motto;
        c.regime = regime;
        c.founded = now;
        c.extra.remove(ClanLogic.CHOOSE_REGIME);
        cache(founder, c);
        long day = PioneerApi.day(server);
        c.log(day, "clan.pioneers.log.founded", name, ClanMessages.character(founder));
        for (ServerPlayer s : signers) {
            join(server, c, s, "charter");
        }
        ClanGov.setupRegime(server, c, now, true);
        data.changed();
        tabards(server, c); //1.9 (MAJ RP, bloc 4) : un clan solo converti garde son id, son tabard change de couleur
        ClanAudit.write(server, "found", c, "fonde par " + founder.getGameProfile().getName() + " (" + regime + ")");
        MinecraftForge.EVENT_BUS.post(new ClanEvent.Created(server, c));
        Component msg = Component.translatable("clan.pioneers.founded_broadcast", Component.literal(name).withStyle(ClanMessages.dye(color)),
              Component.translatable("clan.pioneers.regime." + regime)).withStyle(ChatFormatting.GRAY);
        server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
        return c;
    }

    // ------------------------------------------------------------------ invitations
    /** Invite un joueur connecte (permission d'inviter, place libre). Cle du refus ou {@code null}. */
    @Nullable
    public static String invite(ServerPlayer actor, ServerPlayer target) {
        if (!enabled()) {
            return "clan.pioneers.error.disabled";
        }
        ClanData data = ClanData.get(actor.getServer());
        Clan c = data.clanOf(actor.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (!ClanRoles.has(c, actor.getUUID(), Permission.INVITE)) {
            return "clan.pioneers.error.permission";
        }
        if (target == actor || c.members.containsKey(target.getUUID())) {
            return "clan.pioneers.error.already_member";
        }
        String err = canEnter(target);
        if (err != null) {
            return err.equals("clan.pioneers.error.already_member") ? "clan.pioneers.error.target_in_clan" : "clan.pioneers.error.target_cooldown";
        }
        if (c.members.size() >= maxMembers(c)) {
            return "clan.pioneers.error.full";
        }
        long now = ClanClock.now();
        long expires = now + rules().inviteMinutes() * ClanDefs.MINUTE;
        List<Invite> l = INVITES.computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
        l.removeIf(i -> i.clan.equals(c.id) || i.expires <= now);
        l.add(new Invite(c.id, actor.getUUID(), ClanMessages.character(actor), expires));
        target.sendSystemMessage(ClanMessages.tagged(c, Component.translatable("clan.pioneers.invited", ClanMessages.character(actor),
              ClanMessages.name(actor.getServer(), c), rules().inviteMinutes())));
        c.log(PioneerApi.day(actor.getServer()), "clan.pioneers.log.invited", ClanMessages.character(target), ClanMessages.character(actor));
        return null;
    }

    /** Invitations encore valables d'un joueur. */
    public static List<Invite> invites(UUID player) {
        long now = ClanClock.now();
        List<Invite> l = INVITES.getOrDefault(player, List.of());
        List<Invite> out = new ArrayList<>();
        for (Invite i : l) {
            if (i.expires > now) {
                out.add(i);
            }
        }
        return out;
    }

    /** Accepte une invitation ({@code clanName} nul : la seule, ou la plus recente). */
    @Nullable
    public static String accept(ServerPlayer p, @Nullable String clanName) {
        if (!enabled()) {
            return "clan.pioneers.error.disabled";
        }
        MinecraftServer server = p.getServer();
        ClanData data = ClanData.get(server);
        Invite chosen = null;
        for (Invite i : invites(p.getUUID())) {
            Clan c = data.clan(i.clan);
            if (c != null && (clanName == null || c.name.equalsIgnoreCase(clanName))) {
                chosen = i;
            }
        }
        if (chosen == null) {
            return "clan.pioneers.error.no_invite";
        }
        String err = canEnter(p);
        if (err != null) {
            return err;
        }
        Clan c = data.clan(chosen.clan);
        if (c == null || c.members.isEmpty()) {
            return "clan.pioneers.error.no_clan";
        }
        if (c.members.size() >= maxMembers(c)) {
            return "clan.pioneers.error.full";
        }
        INVITES.remove(p.getUUID());
        join(server, c, p, "invite");
        return null;
    }

    public static boolean decline(ServerPlayer p) {
        return INVITES.remove(p.getUUID()) != null;
    }

    // ------------------------------------------------------------------ entree, depart
    /** Fait entrer un joueur (son clan solo eventuel est absorbe). Tout est deja verifie. */
    public static void join(MinecraftServer server, Clan c, ServerPlayer p, String how) {
        ClanData data = ClanData.get(server);
        Clan prev = data.clanOf(p.getUUID());
        if (prev == c || c.members.size() >= maxMembers(c) && !c.members.isEmpty()) {
            return;
        }
        if (prev != null) {
            if (!prev.solo) {
                return;
            }
            merge(server, prev, c, p.getUUID());
        }
        long now = ClanClock.now();
        ClanLogic.Change ch = ClanLogic.addMember(c, p.getUUID(), p.getGameProfile().getName(), PioneerCap.get(p).name, now, maxMembers(c));
        if (ch == ClanLogic.Change.REFUSED) {
            return;
        }
        data.reindex(c);
        data.cooldown.remove(p.getUUID());
        cache(p, c);
        long day = PioneerApi.day(server);
        c.log(day, "clan.pioneers.log.joined", ClanMessages.character(p));
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.joined", ClanMessages.character(p))));
        if (ch == ClanLogic.Change.CONVERTED) {
            c.log(day, "clan.pioneers.log.converted");
            ServerPlayer rep = c.representative == null ? null : server.getPlayerList().getPlayer(c.representative);
            if (rep != null) {
                ClanMessages.tell(rep, "clan.pioneers.converted");
            }
        }
        ClanAudit.write(server, "join", c, p.getGameProfile().getName() + " (" + how + ")" + (ch == ClanLogic.Change.CONVERTED ? " : clan solo converti"
              : ""));
        MinecraftForge.EVENT_BUS.post(new ClanEvent.MemberJoined(server, c, p.getUUID(), ch == ClanLogic.Change.CONVERTED));
    }

    /** Absorbe le clan solo d'un Solitaire dans le clan qu'il rejoint : claims, fonctions, chunkloading, points, tresor. */
    static void merge(MinecraftServer server, Clan solo, Clan into, UUID player) {
        ClanData data = ClanData.get(server);
        solo.claims.forEach((dim, m) -> m.forEach((chunk, claim) -> {
            into.claims.computeIfAbsent(dim, k -> new HashMap<>()).putIfAbsent(chunk, claim);
            ClaimIndex.set(dim, chunk, into.id);
        }));
        solo.functions.forEach(into.functions::putIfAbsent);
        for (String s : solo.chunkload) {
            if (!into.chunkload.contains(s)) {
                into.chunkload.add(s);
            }
        }
        into.points += solo.points;
        into.level = Math.max(into.level, Math.max(solo.level, ClanDefs.get().levelFor(into.points)));
        long room = Math.max(0, EconomyApi.MAX - into.treasury);
        long moved = Math.min(room, solo.treasury);
        into.treasury += moved;
        long day = PioneerApi.day(server);
        if (moved > 0) {
            into.log(day, "clan.pioneers.log.treasury_in", "#clan.pioneers.reason.merge", String.valueOf(moved), solo.members.isEmpty() ? "?"
                  : ClanMessages.character(server, solo, player));
        }
        into.log(day, "clan.pioneers.log.merged", ClanMessages.character(server, solo, player), String.valueOf(solo.claimCount()));
        ClanAudit.write(server, "merge", into, "clan solo " + solo.id + " absorbe (" + solo.claimCount() + " chunks, " + moved + " jetons)");
        solo.members.clear();
        data.remove(solo);
        data.reindex(into);
        MinecraftForge.EVENT_BUS.post(new ClanEvent.SoloMerged(server, into, solo, player));
    }

    /** Quitter son clan. */
    @Nullable
    public static String leave(ServerPlayer p) {
        if (!ClanApi.enabled()) {
            return "clan.pioneers.error.disabled";
        }
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        if (c == null) {
            return "clan.pioneers.error.no_clan";
        }
        removeMember(server, c, p.getUUID(), "leave");
        return null;
    }

    /** Effet d'une exclusion decidee (vote ou Representant). */
    public static boolean kick(MinecraftServer server, Clan c, UUID target, @Nullable UUID by) {
        if (!c.members.containsKey(target) || target.equals(c.representative)) {
            return false;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(target);
        if (p != null) {
            ClanMessages.refuse(p, "clan.pioneers.kicked", ClanMessages.name(server, c));
        }
        removeMember(server, c, target, "kick");
        ClanAudit.write(server, "kick", c, "exclusion de " + target + (by == null ? "" : " (initiee par " + by + ")"));
        return true;
    }

    /** Retire un membre ({@code reason} : leave, kick, admin) : cache, delai de 3 jours, retour au solo, clan vide, succession. */
    public static void removeMember(MinecraftServer server, Clan c, UUID u, String reason) {
        ClanData data = ClanData.get(server);
        long now = ClanClock.now();
        boolean wasSolo = c.solo, wasRep = u.equals(c.representative);
        String name = ClanMessages.character(server, c, u);
        ClanLogic.Change ch = ClanLogic.removeMember(c, u, now);
        if (ch == ClanLogic.Change.REFUSED) {
            return;
        }
        data.reindex(c);
        if (!wasSolo && !"admin".equals(reason)) {
            data.cooldown.put(u, now + (rules() == null ? 3 : rules().rejoinDays()) * ClanDefs.DAY);
        }
        ServerPlayer p = server.getPlayerList().getPlayer(u);
        if (p != null) {
            cache(p, null);
        }
        long day = PioneerApi.day(server);
        c.log(day, "kick".equals(reason) ? "clan.pioneers.log.kicked" : "clan.pioneers.log.left", name);
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("kick".equals(reason) ? "clan.pioneers.member_kicked"
              : "clan.pioneers.member_left", name)));
        ClanAudit.write(server, "kick".equals(reason) ? "kick" : "leave", c, u + " (" + reason + ")");
        switch (ch) {
            case EMPTY -> emptied(server, c);
            case BACK_TO_SOLO -> {
                ClanVotes.cancelAll(server, c, "clan.pioneers.vote.why.solo");
                c.log(day, "clan.pioneers.log.back_to_solo");
                ClanMessages.members(server, c, null, Component.translatable("clan.pioneers.back_to_solo"));
            }
            default -> {
                if (wasRep) {
                    succession(server, c, now);
                }
            }
        }
        cancelTargeting(server, c, u);
        data.changed();
        MinecraftForge.EVENT_BUS.post(new ClanEvent.MemberLeft(server, c, u, reason, ch == ClanLogic.Change.BACK_TO_SOLO,
              ch == ClanLogic.Change.EMPTY));
    }

    /** Annule les propositions qui visent un membre parti (exclusion, depense en sa faveur). */
    private static void cancelTargeting(MinecraftServer server, Clan c, UUID u) {
        for (var p : ClanVotes.openOf(c.proposals)) {
            if (p.data().hasUUID("target") && p.data().getUUID("target").equals(u)) {
                ClanVotes.cancel(server, c, null, p.id(), "clan.pioneers.vote.why.target_left");
            }
        }
    }

    // ------------------------------------------------------------------ clan vide, dissolution
    /** Plus aucun membre : tresor detruit, propositions annulees ; les claims tiennent encore le delai de dissolution (7 jours). */
    static void emptied(MinecraftServer server, Clan c) {
        long destroyed = c.treasury;
        c.treasury = 0;
        c.representative = null;
        c.heir = null;
        c.council.clear();
        if (c.emptySince == 0) {
            c.emptySince = ClanClock.now();
        }
        ClanVotes.cancelAll(server, c, "clan.pioneers.vote.why.empty");
        c.log(PioneerApi.day(server), "clan.pioneers.log.emptied", String.valueOf(destroyed));
        ClanAudit.write(server, "dissolve", c, "clan vide, tresor detruit (" + destroyed + " jetons), claims liberes dans "
              + (rules() == null ? 7 : rules().dissolveDays()) + " jours");
        MinecraftForge.EVENT_BUS.post(new ClanEvent.Emptied(server, c));
    }

    /** Dissolution d'administration : tous les membres sortent (sans delai de ralliement), puis clan vide. */
    public static void dissolve(MinecraftServer server, Clan c, String by) {
        for (UUID u : new ArrayList<>(c.members.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                ClanMessages.refuse(p, "clan.pioneers.dissolved", ClanMessages.name(server, c));
            }
            removeMember(server, c, u, "admin");
        }
        if (!c.members.isEmpty()) {
            return;
        }
        ClanAudit.write(server, "admin", c, "dissolution par " + by);
    }

    /** Clans vides dont le delai de dissolution est ecoule (le territoire peut les liberer lui-meme par {@link #finishDissolution}). */
    public static List<Clan> pendingDissolutions(MinecraftServer server, long now) {
        List<Clan> out = new ArrayList<>();
        long delay = rules() == null ? 7 * ClanDefs.DAY : rules().dissolveDays() * ClanDefs.DAY;
        for (Clan c : ClanData.get(server).clans()) {
            if (c.members.isEmpty() && c.emptySince > 0 && now - c.emptySince >= delay) {
                out.add(c);
            }
        }
        return out;
    }

    /** Fin d'un clan vide : claims liberes (index compris), clan retire des donnees. Sans effet sur un clan qui a des membres. */
    public static void finishDissolution(MinecraftServer server, Clan c) {
        if (!c.members.isEmpty()) {
            return;
        }
        ClanData data = ClanData.get(server);
        int freed = 0;
        for (Map.Entry<String, Map<Long, Clan.Claim>> e : c.claims.entrySet()) {
            for (Long chunk : e.getValue().keySet()) {
                if (c.id.equals(ClaimIndex.owner(e.getKey(), chunk))) {
                    ClaimIndex.set(e.getKey(), chunk, null);
                    freed++;
                }
            }
        }
        c.claims.clear();
        c.chunkload.clear();
        c.functions.clear();
        data.remove(c);
        ClanAudit.write(server, "dissolve", c, "clan dissous, " + freed + " chunks liberes");
        MinecraftForge.EVENT_BUS.post(new ClanEvent.Dissolved(server, c));
    }

    // ------------------------------------------------------------------ Representant
    /** Change le Representant (journal, message, audit, evenement). */
    public static void setRepresentative(MinecraftServer server, Clan c, UUID next, String reasonKey) {
        if (!c.members.containsKey(next)) {
            return;
        }
        UUID prev = c.representative;
        ClanRoles.setRepresentative(c, next);
        String name = ClanMessages.character(server, c, next);
        c.log(PioneerApi.day(server), "clan.pioneers.log.representative", name, "#" + reasonKey);
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.representative", name,
              Component.translatable(reasonKey))));
        ClanAudit.write(server, "representative", c, prev + " -> " + next + " (" + reasonKey + ")");
        ClanData.get(server).changed();
        if (!next.equals(prev)) {
            MinecraftForge.EVENT_BUS.post(new ClanEvent.RepresentativeChanged(server, c, prev, next));
        }
    }

    /** Succession selon le regime (Representant parti, absent 30 jours, revoque) : interim tout de suite, puis vote si le regime le veut. */
    public static void succession(MinecraftServer server, Clan c, long now) {
        if (c.members.isEmpty()) {
            return;
        }
        ClanDefs.Constitution k = ClanDefs.get().regime(c.regime);
        ClanLogic.Succession s = ClanLogic.successor(c, k, now, activeMs());
        c.extra.putLong("gov.succ", now);
        if (s.who() != null && !s.who().equals(c.representative)) {
            setRepresentative(server, c, s.who(), "clan.pioneers.reason.succession");
        }
        if ("election".equals(s.kind())) {
            ClanGov.openElection(server, c, "election", true);
        } else if ("council".equals(s.kind())) {
            ClanGov.openElection(server, c, "council_choice", true);
        } else if (k != null && "rotating".equals(k.repMode())) {
            c.rotateAt = now + k.termDays() * ClanDefs.DAY;
        }
    }

    // ------------------------------------------------------------------ profil, connexions
    /** Met a jour le cache {@code PioneerProfile.clan} (identifiant du clan, vide sans clan) et le renvoie au client. */
    public static void cache(ServerPlayer p, @Nullable Clan c) {
        String id = c == null ? "" : c.id.toString();
        var profile = PioneerCap.get(p);
        if (!id.equals(profile.clan)) {
            profile.clan = id;
            profile.touch();
            ProfileSync.full(p);
            ProfileSync.identity(p); //1.9 (MAJ RP, bloc 4) : couleur du tabard de clan
        }
    }

    /** 1.9 (MAJ RP, bloc 4) : renvoie l'identite des membres connectes (couleur de leur tabard de clan) a ceux qui les voient. */
    public static void tabards(MinecraftServer server, Clan c) {
        for (UUID u : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                ProfileSync.identity(p);
            }
        }
    }

    /** Connexion : derniere connexion, pseudo et personnage a jour, cache, invitations en attente. */
    public static void login(ServerPlayer p) {
        if (!ClanApi.enabled()) {
            return;
        }
        MinecraftServer server = p.getServer();
        Clan c = ClanData.get(server).clanOf(p.getUUID());
        cache(p, c);
        if (c != null) {
            touch(c, p, ClanClock.now());
            ClanData.get(server).changed();
        }
        for (Invite i : invites(p.getUUID())) {
            Clan ic = ClanData.get(server).clan(i.clan);
            if (ic != null) {
                p.sendSystemMessage(ClanMessages.tagged(ic, Component.translatable("clan.pioneers.invited", i.inviterName, ClanMessages.name(server, ic),
                      Math.max(1, (i.expires - ClanClock.now()) / ClanDefs.MINUTE))));
            }
        }
    }

    public static void logout(ServerPlayer p) {
        if (!ClanApi.enabled()) {
            return;
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c != null) {
            touch(c, p, ClanClock.now());
            ClanData.get(p.getServer()).changed();
        }
    }

    static void touch(Clan c, ServerPlayer p, long now) {
        Clan.Member m = c.member(p.getUUID());
        if (m != null) {
            m.lastSeen = now;
            m.account = p.getGameProfile().getName();
            String n = PioneerCap.get(p).name;
            if (n != null && !n.isBlank()) {
                m.character = n;
            }
        }
    }

    /** Invitations expirees retirees (balayage d'une minute). */
    static void expireInvites(long now) {
        for (Iterator<List<Invite>> it = INVITES.values().iterator(); it.hasNext(); ) {
            List<Invite> l = it.next();
            l.removeIf(i -> i.expires <= now);
            if (l.isEmpty()) {
                it.remove();
            }
        }
    }

    static void clear() {
        INVITES.clear();
    }
}
