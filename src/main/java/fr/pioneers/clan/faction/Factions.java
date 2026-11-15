package fr.pioneers.clan.faction;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanCore;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.territory.BorderSplits;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.clan.vote.ClanVotes;
import fr.pioneers.clan.vote.Proposal;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.economy.EconomyApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : les factions cote serveur, federations de 2 a 8 clans ({@link Faction} dans {@code ClanData}). Un clan n'est que
 * dans une faction ; il la quitte par une decision de son regime (preavis de 24 heures reelles) ou en est exclu par la decision federale ;
 * 7 jours reels avant une autre faction. {@code Faction.founder} = clan fondateur, {@code Faction.representative} = Representant federal
 * (joueur). Delegues = les Representants des clans. Niveau 1..5 aux points cumules des clans. Tresor federal : contribution de chaque clan
 * (pourcentage de ses recettes du jour de jeu, plafond du regime federal), reglee au passage du jour. Journal federal de 200 lignes.
 */
public final class Factions {

    /** Cles de {@code Faction.extra} : departs annonces (clan -&gt; fin du preavis, ms), demandes d'entree (clan -&gt; ms). */
    public static final String LEAVING = "fed.leaving", REQUESTS = "fed.requests", CREATED_BY = "fed.createdBy";

    private Factions() {
    }

    public static boolean enabled() {
        return ClanCore.enabled();
    }

    // ------------------------------------------------------------------ lecture
    @Nullable
    public static Faction of(MinecraftServer server, @Nullable Clan c) {
        return c == null ? null : ClanData.get(server).faction(c.faction);
    }

    @Nullable
    public static Faction ofPlayer(MinecraftServer server, UUID player) {
        return of(server, ClanData.get(server).clanOf(player));
    }

    @Nullable
    public static Faction byName(MinecraftServer server, String name) {
        for (Faction f : ClanData.get(server).factions()) {
            if (f.name.equalsIgnoreCase(name.trim()) || f.tag.equalsIgnoreCase(name.trim())) {
                return f;
            }
        }
        return null;
    }

    /** Clans de la faction, dans l'ordre d'entree (les identifiants orphelins sont ignores). */
    public static List<Clan> clans(MinecraftServer server, Faction f) {
        ClanData data = ClanData.get(server);
        List<Clan> out = new ArrayList<>();
        for (UUID id : f.clans) {
            Clan c = data.clan(id);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    public static long points(MinecraftServer server, Faction f) {
        long p = 0;
        for (Clan c : clans(server, f)) {
            p += Math.max(0, c.points);
        }
        return p;
    }

    public static int level(MinecraftServer server, Faction f) {
        return FactionRules.level(FactionRules.get(), points(server, f));
    }

    public static int members(MinecraftServer server, Faction f) {
        int n = 0;
        for (Clan c : clans(server, f)) {
            n += c.members.size();
        }
        return n;
    }

    public static int active(MinecraftServer server, Faction f, long now) {
        int n = 0;
        for (Clan c : clans(server, f)) {
            n += c.activeCount(now);
        }
        return n;
    }

    /** Le joueur est-il Delegue (Representant d'un clan de la faction) ? */
    public static boolean delegate(MinecraftServer server, Faction f, UUID player) {
        for (Clan c : clans(server, f)) {
            if (player.equals(c.representative)) {
                return true;
            }
        }
        return false;
    }

    /** Le joueur fait-il partie de la faction (membre d'un de ses clans) ? */
    public static boolean member(MinecraftServer server, Faction f, UUID player) {
        Clan c = ClanData.get(server).clanOf(player);
        return c != null && f.id.equals(c.faction);
    }

    /** Nom de personnage d'un membre de la faction (jamais le pseudo du compte). */
    public static String character(MinecraftServer server, @Nullable UUID u) {
        if (u == null) {
            return "?";
        }
        Clan c = ClanData.get(server).clanOf(u);
        return c == null ? "?" : ClanMessages.character(server, c, u);
    }

    public static String delegateName(MinecraftServer server, Faction f) {
        return character(server, f.representative);
    }

    // ------------------------------------------------------------------ messages, journal
    /** En-tete « «ETQ» » a la couleur de la faction devant un message. */
    public static MutableComponent tagged(Faction f, Component msg) {
        String tag = f.tag == null || f.tag.isBlank() ? "" : "«" + f.tag + "» ";
        return Component.literal(tag).withStyle(ClanMessages.dye(f.color)).append(msg.copy().withStyle(ChatFormatting.GRAY));
    }

    /** Message a tous les membres connectes des clans de la faction. */
    public static void tell(MinecraftServer server, Faction f, Component msg) {
        Component m = tagged(f, msg);
        for (Clan c : clans(server, f)) {
            ClanMessages.members(server, c, null, m);
        }
    }

    /** Message aux seuls Delegues connectes. */
    public static void tellDelegates(MinecraftServer server, Faction f, Component msg) {
        Component m = tagged(f, msg);
        for (Clan c : clans(server, f)) {
            ServerPlayer p = c.representative == null ? null : server.getPlayerList().getPlayer(c.representative);
            if (p != null) {
                p.sendSystemMessage(m);
            }
        }
    }

    /** Annonce a tout le serveur (fondation, secession, dissolution). */
    public static void announce(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg.copy().withStyle(ChatFormatting.GOLD), false);
    }

    public static void log(MinecraftServer server, Faction f, String key, String... args) {
        CompoundTag t = new CompoundTag();
        t.putLong("t", ClanClock.now());
        t.putLong("d", PioneerApi.day(server));
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
        ClanData.get(server).changed();
    }

    // ------------------------------------------------------------------ tresor federal
    /** Verse au tresor federal (contribution, tribut) ; refuse au-dela du plafond de solde. */
    public static boolean credit(MinecraftServer server, Faction f, long amount, String reason, String who) {
        if (amount < 0 || f.treasury + amount > EconomyApi.MAX) {
            return false;
        }
        f.treasury += amount;
        log(server, f, "clan.pioneers.faction.log.treasury_in", reason, String.valueOf(amount), who);
        return true;
    }

    /** Preleve au tresor federal si le solde le couvre (les jetons sont detruits si personne ne les recoit). */
    public static boolean debit(MinecraftServer server, Faction f, long amount, String reason, String who) {
        if (amount < 0 || f.treasury < amount) {
            return false;
        }
        f.treasury -= amount;
        log(server, f, "clan.pioneers.faction.log.treasury_out", reason, String.valueOf(amount), who);
        return true;
    }

    /** Depense federale decidee : du tresor federal vers le tresor d'un clan de la faction (ce versement n'est pas une recette contribuable). */
    public static boolean spendTo(MinecraftServer server, Faction f, Clan c, long amount, String who) {
        if (amount <= 0 || !f.clans.contains(c.id) || f.treasury < amount || c.treasury + amount > EconomyApi.MAX) {
            return false;
        }
        f.treasury -= amount;
        c.treasury += amount;
        long day = PioneerApi.day(server);
        log(server, f, "clan.pioneers.faction.log.spend", c.name, String.valueOf(amount), who);
        c.log(day, "clan.pioneers.log.treasury_in", "#clan.pioneers.faction.reason.federal", String.valueOf(amount), f.name);
        ClanAudit.write(server, "faction", c, "depense federale " + amount + " de " + f.name + " (" + f.id + ")");
        ClanData.get(server).changed();
        return true;
    }

    /**
     * Contribution federale, au passage du jour de jeu : chaque clan verse le pourcentage fixe de ses recettes depuis le dernier reglement
     * (compteur tenu par {@link ClanTreasury}), jamais plus que son tresor (jamais de dette), tresor gele exempte.
     */
    public static void settleContributions(MinecraftServer server) {
        ClanData data = ClanData.get(server);
        for (Faction f : new ArrayList<>(data.factions())) {
            for (Clan c : clans(server, f)) {
                long base = c.extra.getLong(ClanTreasury.FED_BASE);
                c.extra.remove(ClanTreasury.FED_BASE);
                if (!EconomyApi.enabled() || c.frozen) {
                    continue;
                }
                long due = FactionRules.contributionDue(base, Math.min(f.contribution, cap(f)), c.treasury);
                due = Math.min(due, EconomyApi.MAX - f.treasury);
                if (due <= 0) {
                    continue;
                }
                c.treasury -= due;
                f.treasury += due;
                long day = PioneerApi.day(server);
                c.log(day, "clan.pioneers.log.treasury_out", "#clan.pioneers.faction.reason.contribution", String.valueOf(due), f.name);
                log(server, f, "clan.pioneers.faction.log.contribution", c.name, String.valueOf(due), String.valueOf(base));
            }
        }
        data.changed();
    }

    /** Plafond de contribution du regime federal (Democratie 10, Empire 20, Conseil 12, Cooperative 5 %). */
    public static int cap(Faction f) {
        ClanDefs.Constitution k = ClanDefs.get().regime(f.regime);
        return k == null ? 0 : k.federalContributionMax();
    }

    public static double weightCap(Faction f) {
        ClanDefs.Constitution k = ClanDefs.get().regime(f.regime);
        return k == null ? 0.4 : k.federalWeightCap();
    }

    // ------------------------------------------------------------------ composition
    /** Fonde la faction (Pacte scelle, tout deja verifie et paye) : le clan fondateur et les signataires y entrent. */
    public static Faction create(MinecraftServer server, CompoundTag pact, Clan founder, List<Clan> signers, @Nullable UUID by) {
        ClanData data = ClanData.get(server);
        long now = ClanClock.now();
        Faction f = new Faction(UUID.randomUUID());
        f.name = pact.getString("name");
        f.tag = pact.getString("tag");
        f.motto = pact.getString("motto");
        f.color = pact.getInt("color") & 15;
        f.regime = pact.getString("regime");
        f.founded = now;
        f.founder = founder.id;
        if (by != null) {
            f.extra.putUUID(CREATED_BY, by);
        }
        data.put(f);
        FactionRules.join(f, founder);
        for (Clan c : signers) {
            FactionRules.join(f, c);
        }
        f.representative = founder.representative;
        FactionGov.setup(server, f, now);
        long day = PioneerApi.day(server);
        log(server, f, "clan.pioneers.faction.log.founded", f.name, founder.name);
        for (Clan c : clans(server, f)) {
            c.log(day, "clan.pioneers.faction.log.clan_joined", f.name);
            BorderSplits.clanChanged(server, c);
        }
        ClanAudit.write(server, "faction", founder, "faction fondee : " + f.name + " (" + f.id + "), " + f.clans.size() + " clans");
        announce(server, Component.translatable("clan.pioneers.faction.founded", Component.literal(f.name).withStyle(ClanMessages.dye(f.color)),
              f.clans.size()));
        data.changed();
        return f;
    }

    /** Admission (decision federale acquise, demande du clan presente) : le clan entre, alliances automatiques refaites. */
    @Nullable
    public static String admit(MinecraftServer server, Faction f, Clan c) {
        String err = FactionRules.canEnter(FactionRules.get(), c, f, ClanClock.now());
        if (err != null) {
            return err;
        }
        FactionRules.join(f, c);
        CompoundTag req = f.extra.getCompound(REQUESTS);
        req.remove(c.id.toString());
        f.extra.put(REQUESTS, req);
        log(server, f, "clan.pioneers.faction.log.admitted", c.name);
        c.log(PioneerApi.day(server), "clan.pioneers.faction.log.clan_joined", f.name);
        tell(server, f, Component.translatable("clan.pioneers.faction.admitted", c.name));
        ClanAudit.write(server, "faction", c, "entre dans la faction " + f.name + " (" + f.id + ")");
        BorderSplits.clanChanged(server, c);
        ClanData.get(server).changed();
        return null;
    }

    /**
     * Retire un clan ({@code reason} : leave, exclude, solo, dissolved, admin) : 7 jours avant une autre faction, zones federales qu'il tenait
     * passees a un autre clan, propositions qui le visaient annulees ; sous 2 clans, la faction se dissout (sauf un peuple en declin).
     */
    public static void remove(MinecraftServer server, Faction f, Clan c, String reason) {
        if (!f.clans.contains(c.id)) {
            return;
        }
        long now = ClanClock.now();
        FactionRules.part(FactionRules.get(), f, c, now);
        CompoundTag leaving = f.extra.getCompound(LEAVING);
        leaving.remove(c.id.toString());
        f.extra.put(LEAVING, leaving);
        FactionZones.holderLeft(server, f, c);
        long day = PioneerApi.day(server);
        log(server, f, "clan.pioneers.faction.log.removed", c.name, "#clan.pioneers.faction.reason." + reason);
        c.log(day, "clan.pioneers.faction.log.clan_left", f.name, "#clan.pioneers.faction.reason." + reason);
        ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.faction.left_you", f.name,
              Component.translatable("clan.pioneers.faction.reason." + reason))));
        tell(server, f, Component.translatable("clan.pioneers.faction.left", c.name, Component.translatable("clan.pioneers.faction.reason." + reason)));
        ClanAudit.write(server, "faction", c, "quitte la faction " + f.name + " (" + reason + ")");
        for (Proposal p : ClanVotes.openOf(f.proposals)) {
            CompoundTag d = p.data();
            if (d.hasUUID("clan") && d.getUUID("clan").equals(c.id)) {
                ClanVotes.cancel(server, null, f, p.id(), "clan.pioneers.faction.why.clan_left");
            }
        }
        BorderSplits.clanChanged(server, c);
        if (f.founder != null && f.founder.equals(c.id)) {
            List<Clan> rest = clans(server, f);
            f.founder = rest.isEmpty() ? null : rest.get(0).id;
            FactionGov.founderChanged(server, f, now);
        }
        if (f.representative != null && c.members.containsKey(f.representative)) {
            FactionGov.representativeGone(server, f, now);
        }
        if (f.clans.size() < FactionRules.get().minClans() && (f.people == null || f.people.isEmpty())) {
            dissolve(server, f, "clan.pioneers.faction.reason.too_few");
        }
        ClanData.get(server).changed();
    }

    /**
     * Dissout la faction : propositions annulees, zones et chunkloading federaux leves, tresor federal reparti a parts egales entre les clans
     * restants (rien ne se perd s'il en reste un), guerres federales finies par la donnee (les guerres sans camp sont closes par la guerre).
     */
    public static void dissolve(MinecraftServer server, Faction f, String reasonKey) {
        ClanData data = ClanData.get(server);
        if (data.faction(f.id) == null) {
            return;
        }
        if (f.people != null && !f.people.isEmpty()) {
            fr.pioneers.clan.people.Peoples.dissolve(server, f, "clan.pioneers.people.reason.faction");
        }
        for (Proposal p : ClanVotes.openOf(f.proposals)) {
            ClanVotes.cancel(server, null, f, p.id(), "clan.pioneers.faction.why.dissolved");
        }
        FactionZones.releaseAll(server, f);
        List<Clan> rest = clans(server, f);
        long now = ClanClock.now();
        if (!rest.isEmpty() && f.treasury > 0) {
            long share = f.treasury / rest.size(), left = f.treasury - share * rest.size();
            for (int i = 0; i < rest.size(); i++) {
                Clan c = rest.get(i);
                long give = Math.min(share + (i == 0 ? left : 0), EconomyApi.MAX - c.treasury);
                if (give > 0) {
                    c.treasury += give;
                    c.log(PioneerApi.day(server), "clan.pioneers.log.treasury_in", "#clan.pioneers.faction.reason.dissolved", String.valueOf(give), f.name);
                }
            }
        }
        f.treasury = 0;
        for (Clan c : rest) {
            f.clans.remove(c.id);
            c.faction = null;
            c.factionCooldown = now + FactionRules.get().rejoinMs();
            c.log(PioneerApi.day(server), "clan.pioneers.faction.log.clan_left", f.name, "#clan.pioneers.faction.reason.dissolved");
            BorderSplits.clanChanged(server, c);
            ClanMessages.members(server, c, null, ClanMessages.tagged(c, Component.translatable("clan.pioneers.faction.dissolved", f.name,
                  Component.translatable(reasonKey))));
        }
        ClanAudit.write(server, "faction", null, "faction dissoute : " + f.name + " (" + f.id + ") : " + reasonKey);
        data.remove(f);
        data.changed();
    }

    /** Depart annonce (decision du clan acquise) : effectif apres le preavis de 24 heures. */
    public static void scheduleLeave(MinecraftServer server, Faction f, Clan c) {
        long at = ClanClock.now() + FactionRules.get().leaveNoticeMs();
        CompoundTag leaving = f.extra.getCompound(LEAVING);
        leaving.putLong(c.id.toString(), at);
        f.extra.put(LEAVING, leaving);
        long hours = Math.round(FactionRules.get().leaveNoticeMs() / (double) FactionRules.HOUR);
        log(server, f, "clan.pioneers.faction.log.leaving", c.name, String.valueOf(hours));
        tell(server, f, Component.translatable("clan.pioneers.faction.leaving", c.name, hours));
        ClanData.get(server).changed();
    }

    /** Demande d'entree d'un clan (decision du clan acquise) : la faction l'admet ensuite par sa decision federale. */
    public static void request(MinecraftServer server, Faction f, Clan c) {
        CompoundTag req = f.extra.getCompound(REQUESTS);
        req.putLong(c.id.toString(), ClanClock.now());
        f.extra.put(REQUESTS, req);
        log(server, f, "clan.pioneers.faction.log.request", c.name);
        tellDelegates(server, f, Component.translatable("clan.pioneers.faction.requested", c.name));
        ClanData.get(server).changed();
    }

    public static boolean requested(Faction f, Clan c) {
        return f.extra.getCompound(REQUESTS).contains(c.id.toString());
    }

    // ------------------------------------------------------------------ controle (une fois par minute)
    /** Preavis de depart echus, demandes d'entree perimees (7 jours), echeances du gouvernement federal, declin des peuples. */
    public static void tickMinute(MinecraftServer server, long now) {
        ClanData data = ClanData.get(server);
        for (Faction f : new ArrayList<>(data.factions())) {
            CompoundTag leaving = f.extra.getCompound(LEAVING);
            for (String k : new ArrayList<>(leaving.getAllKeys())) {
                if (leaving.getLong(k) <= now) {
                    Clan c = data.clan(UUID.fromString(k));
                    leaving.remove(k);
                    f.extra.put(LEAVING, leaving);
                    if (c != null) {
                        remove(server, f, c, "leave");
                    }
                }
            }
            if (data.faction(f.id) == null) {
                continue;
            }
            CompoundTag req = f.extra.getCompound(REQUESTS);
            for (String k : new ArrayList<>(req.getAllKeys())) {
                if (now - req.getLong(k) > FactionRules.get().rejoinMs()) {
                    req.remove(k);
                }
            }
            f.extra.put(REQUESTS, req);
            //clans disparus des donnees (dissous) : retires
            for (UUID id : new ArrayList<>(f.clans)) {
                if (data.clan(id) == null) {
                    f.clans.remove(id);
                }
            }
            if (f.clans.size() < FactionRules.get().minClans() && (f.people == null || f.people.isEmpty())) {
                dissolve(server, f, "clan.pioneers.faction.reason.too_few");
                continue;
            }
            FactionZones.prune(server, f);
            FederalTreaties.tick(server, f, now);
            FactionGov.tick(server, f, now);
            fr.pioneers.clan.people.Peoples.tickMinute(server, f, now);
        }
        FactionSaved saved = FactionSaved.get(server);
        if (saved.reserved.values().removeIf(t -> t <= now) | saved.pacts.values().removeIf(p -> now - p.getLong("opened")
              > FactionRules.get().pactExpireMs())) {
            saved.changed();
        }
        data.changed();
    }
}
