package fr.pioneers.clan.war;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.Wars;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.core.ClanRoles;
import fr.pioneers.clan.stats.ClanStats;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 1.9 (MAJ RP, bloc 4) : la guerre entre clans. Declaration (500 jetons du tresor, motif de 100 caracteres au plus, preavis de 24 heures
 * reelles annonce aux deux clans ; refusee si un clan a moins de 3 jours, si l'agresseur pese plus de quatre fois le defenseur, si un pacte
 * de non-agression ou une treve court ; une guerre sortante a la fois, trois au total ; jamais contre ni par un clan solo), creneaux de siege
 * (deux de deux heures par jour, heure reelle du serveur, changement effectif apres 7 jours ; hors creneau le territoire est inviolable),
 * PvP entre les deux camps seulement, fin (cessez-le-feu des deux cotes, capitulation avec tribut, 7 jours sans capture) et treve de 3 jours.
 * Le format des guerres ({@link War}) garde celui que lit {@code Wars.atWar} ; un camp peut etre une faction (guerre federale).
 */
public final class ClanWar {

    /** Creneau de siege ouvert, par clan defenseur (memoire, pour l'annonce d'ouverture). */
    private static final Map<UUID, Boolean> WINDOW_OPEN = new HashMap<>();

    private ClanWar() {
    }

    public static boolean enabled() {
        return ClanApi.enabled() && PioneerApi.hasFeature("war");
    }

    // ------------------------------------------------------------------ lecture
    public static List<War> wars(MinecraftServer server) {
        List<War> out = new ArrayList<>();
        for (Tag t : ClanData.get(server).wars) {
            out.add(new War((CompoundTag) t));
        }
        return out;
    }

    /** Guerres d'un clan : en preavis, ouvertes, ou finies dont la treve court. */
    public static List<War> warsOf(MinecraftServer server, Clan c) {
        long now = ClanClock.now();
        List<War> out = new ArrayList<>();
        for (War w : wars(server)) {
            if (w.involves(c) && (w.live() || w.inTruce(now))) {
                out.add(w);
            }
        }
        return out;
    }

    /** Guerre non finie (preavis ou ouverte) entre les deux clans, dans un sens ou dans l'autre. */
    @Nullable
    public static War live(MinecraftServer server, Clan a, Clan b) {
        for (War w : wars(server)) {
            if (w.live() && (w.attacker(a) && w.defender(b) || w.attacker(b) && w.defender(a))) {
                return w;
            }
        }
        return null;
    }

    /** Guerre ouverte ou {@code attacker} assiege {@code defender}. */
    @Nullable
    public static War openAgainst(MinecraftServer server, Clan attacker, Clan defender, long now) {
        for (War w : wars(server)) {
            if (w.open(now) && w.attacker(attacker) && w.defender(defender)) {
                return w;
            }
        }
        return null;
    }

    @Nullable
    public static War byId(MinecraftServer server, UUID id) {
        for (War w : wars(server)) {
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    /** Treve en cours entre les deux clans (apres toute fin de guerre). */
    public static boolean inTruce(MinecraftServer server, Clan a, Clan b) {
        long now = ClanClock.now();
        for (War w : wars(server)) {
            if (w.inTruce(now) && (w.attacker(a) && w.defender(b) || w.attacker(b) && w.defender(a))) {
                return true;
            }
        }
        return false;
    }

    /** Guerres non finies d'un clan : sortantes, et au total. */
    static int[] counts(MinecraftServer server, Clan c) {
        int out = 0, all = 0;
        for (War w : wars(server)) {
            if (w.live() && w.involves(c)) {
                all++;
                if (w.attacker(c)) {
                    out++;
                }
            }
        }
        return new int[]{out, all};
    }

    /**
     * PvP de guerre : vrai si les deux clans sont en guerre ouverte. Les membres des deux camps se battent partout ; personne d'autre n'est
     * concerne (le PvP vanilla hors clans ne change pas). La protection des territoires laisse passer les coups quand ceci est vrai.
     */
    public static boolean pvpAllowed(@Nullable Clan a, @Nullable Clan b) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && a != null && b != null && !a.id.equals(b.id) && enabled() && Wars.atWar(server, a, b);
    }

    public static boolean pvpAllowed(ServerPlayer x, ServerPlayer y) {
        ClanData data = ClanData.get(x.getServer());
        return pvpAllowed(data.clanOf(x.getUUID()), data.clanOf(y.getUUID()));
    }

    /** Les clans d'un camp : le clan lui-meme, ou tous les clans de la faction (guerre federale). */
    public static List<Clan> side(MinecraftServer server, @Nullable UUID id, boolean federal) {
        List<Clan> out = new ArrayList<>();
        if (id == null) {
            return out;
        }
        ClanData data = ClanData.get(server);
        if (federal) {
            Faction f = data.faction(id);
            if (f != null) {
                for (UUID c : f.clans) {
                    Clan x = data.clan(c);
                    if (x != null) {
                        out.add(x);
                    }
                }
            }
        } else {
            Clan c = data.clan(id);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** Nom d'un camp (clan ou faction). */
    public static String sideName(MinecraftServer server, @Nullable UUID id, boolean federal) {
        if (id == null) {
            return "?";
        }
        ClanData data = ClanData.get(server);
        if (federal) {
            Faction f = data.faction(id);
            return f == null ? "?" : f.name;
        }
        Clan c = data.clan(id);
        return c == null ? "?" : c.name;
    }

    // ------------------------------------------------------------------ temps reel du serveur
    private static ZonedDateTime local(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());
    }

    public static int minuteOfDay(long now) {
        ZonedDateTime z = local(now);
        return z.getHour() * 60 + z.getMinute();
    }

    /** Jour reel (date locale du serveur) : plafond de captures par jour et par guerre. */
    public static long realDay(long now) {
        return local(now).toLocalDate().toEpochDay();
    }

    /** Creneaux en vigueur d'un clan (le changement demande s'applique 7 jours apres). */
    public static int[] windows(Clan c, long now) {
        return WarRules.effectiveWindows(c.siegeWindows, c.pendingWindows, c.windowsFrom, now);
    }

    /** Le territoire du clan est-il assiegeable maintenant (dans l'un de ses creneaux) ? */
    public static boolean inWindow(Clan c, long now) {
        return WarRules.inWindow(windows(c, now), minuteOfDay(now), WarRules.get().windowHours());
    }

    /** Fixe les creneaux (deux heures de debut) : effectifs 7 jours plus tard. Permission de siege ou de guerre. Cle de refus ou null. */
    @Nullable
    public static String setWindows(ServerPlayer by, Clan clan, int first, int second) {
        if (!ClanRoles.has(clan, by.getUUID(), Permission.SIEGE) && !ClanRoles.has(clan, by.getUUID(), Permission.DECLARE_WAR)) {
            return "clan.pioneers.error.permission";
        }
        if (clan.solo) {
            return "clan.pioneers.war.windows.solo";
        }
        WarRules.Values v = WarRules.get();
        int[] w = first <= second ? new int[]{first, second} : new int[]{second, first};
        String err = WarRules.validateWindows(w, v.windowCount(), v.windowHours());
        if (err != null) {
            return err;
        }
        long now = ClanClock.now();
        clan.pendingWindows = w;
        clan.windowsFrom = now + v.windowChangeMs();
        MinecraftServer server = by.getServer();
        clan.log(PioneerApi.day(server), "clan.pioneers.log.windows", hour(w[0]), hour(w[1]));
        ClanMessages.members(server, clan, null, ClanMessages.tagged(clan, Component.translatable("clan.pioneers.war.windows.set", hour(w[0]),
              hour(w[0] + v.windowHours()), hour(w[1]), hour(w[1] + v.windowHours()), Math.round(v.windowChangeMs() / (double) WarRules.DAY))));
        ClanData.get(server).changed();
        return null;
    }

    static String hour(int h) {
        return String.format(java.util.Locale.ROOT, "%02d:00", Math.floorMod(h, 24));
    }

    // ------------------------------------------------------------------ declaration
    /** Declare la guerre (permission de guerre ; decision {@code war} du regime). */
    public static void declare(ServerPlayer by, Clan attacker, Clan defender, String reason) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.DECLARE);
        d.putUUID("target", defender.id);
        d.putString("reason", reason.trim());
        String err = checkDeclare(by.getServer(), attacker, d);
        if (err != null) {
            refuse(by, err);
            return;
        }
        WarDecisions.submit(by, attacker, d, defender.name + " : " + reason.trim());
    }

    @Nullable
    private static Clan target(MinecraftServer server, CompoundTag data) {
        return data.hasUUID("target") ? ClanData.get(server).clan(data.getUUID("target")) : null;
    }

    @Nullable
    static String checkDeclare(MinecraftServer server, Clan clan, CompoundTag data) {
        if (!enabled()) {
            return "clan.pioneers.war.disabled";
        }
        Clan target = target(server, data);
        if (target == null) {
            return "clan.pioneers.error.no_clan";
        }
        if (target.id.equals(clan.id)) {
            return "clan.pioneers.war.refuse.self";
        }
        WarRules.Values v = WarRules.get();
        long now = ClanClock.now();
        int[] mine = counts(server, clan), theirs = counts(server, target);
        int pa = WarRules.ratioPotential(ClanStats.clanPotential(server, clan), clan.activeCount(now), v.memberFloor());
        int pd = WarRules.ratioPotential(ClanStats.clanPotential(server, target), target.activeCount(now), v.memberFloor());
        String reason = data.getString("reason");
        boolean economy = EconomyApi.enabled();
        return WarRules.refuse(v, new WarRules.Declaration(clan.solo, target.solo, now - clan.founded, now - target.founded, pa, pd, mine[0],
              mine[1], theirs[1], ClanDiplomacy.nonAggression(clan, target), inTruce(server, clan, target), live(server, clan, target) != null,
              ClanApi.relation(server, clan, target) == ClanApi.Relation.ALLY, reason.length(), economy ? clan.treasury : Long.MAX_VALUE,
              economy && clan.frozen));
    }

    static boolean applyDeclare(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan target = target(server, data);
        if (target == null) {
            return false;
        }
        WarRules.Values v = WarRules.get();
        String who = data.hasUUID("by") ? ClanMessages.character(server, clan, data.getUUID("by")) : "#clan.pioneers.vote.system";
        if (EconomyApi.enabled() && v.declareCost() > 0 && !ClanTreasury.debit(server, clan, v.declareCost(), "#clan.pioneers.reason.war", who)) {
            return false;
        }
        long now = ClanClock.now();
        String reason = data.getString("reason");
        War w = War.create(clan.id, false, target.id, false, reason, now, now + v.noticeMs());
        w.tag.putInt("bSize", target.claimCount());
        ClanData.get(server).wars.add(w.tag);
        ClanDiplomacy.warDeclared(server, clan, target);
        long day = PioneerApi.day(server);
        clan.log(day, "clan.pioneers.log.war_declared", clan.name, target.name, reason);
        target.log(day, "clan.pioneers.log.war_declared", clan.name, target.name, reason);
        long hours = Math.round(v.noticeMs() / (double) WarRules.HOUR);
        Component msg = Component.translatable("clan.pioneers.war.declared", clan.name, target.name, reason, hours);
        tellClan(server, clan, msg, RpSounds.SIEGE_HORN.get());
        tellClan(server, target, msg, RpSounds.SIEGE_HORN.get());
        ClanAudit.write(server, "war", clan, "declaration de guerre a " + target.name + " (" + target.id + ") : " + reason);
        ClanData.get(server).changed();
        return true;
    }

    // ------------------------------------------------------------------ cessez-le-feu, capitulation
    /** Propose ou accepte un cessez-le-feu (decision {@code treaty}). La guerre finit quand les deux camps l'ont accepte. */
    public static void ceasefire(ServerPlayer by, Clan clan, Clan other) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.CEASEFIRE);
        d.putUUID("target", other.id);
        String err = checkCeasefire(by.getServer(), clan, d);
        if (err != null) {
            refuse(by, err);
            return;
        }
        WarDecisions.submit(by, clan, d, other.name);
    }

    /** Guerre non finie ou le clan est un camp a lui seul (une faction decide pour ses clans). */
    @Nullable
    private static War ownWar(MinecraftServer server, Clan clan, @Nullable Clan other) {
        if (other == null) {
            return null;
        }
        War w = live(server, clan, other);
        if (w == null) {
            return null;
        }
        boolean mineA = clan.id.equals(w.a()), mineB = clan.id.equals(w.b());
        return mineA || mineB ? w : null;
    }

    @Nullable
    static String checkCeasefire(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = target(server, data);
        War w = other == null ? null : live(server, clan, other);
        if (w == null) {
            return "clan.pioneers.war.none";
        }
        if (ownWar(server, clan, other) == null) {
            return "clan.pioneers.war.federal";
        }
        boolean a = clan.id.equals(w.a());
        if (w.tag.getBoolean(a ? "cfA" : "cfB")) {
            return "clan.pioneers.war.ceasefire_already";
        }
        return null;
    }

    static boolean applyCeasefire(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = target(server, data);
        War w = ownWar(server, clan, other);
        if (w == null) {
            return false;
        }
        boolean a = clan.id.equals(w.a());
        w.tag.putBoolean(a ? "cfA" : "cfB", true);
        long day = PioneerApi.day(server);
        if (w.tag.getBoolean("cfA") && w.tag.getBoolean("cfB")) {
            end(server, w, War.CEASEFIRE);
        } else {
            clan.log(day, "clan.pioneers.log.ceasefire_offered", other.name);
            other.log(day, "clan.pioneers.log.ceasefire_received", clan.name);
            tellClan(server, clan, Component.translatable("clan.pioneers.war.ceasefire_offered", other.name), null);
            tellClan(server, other, Component.translatable("clan.pioneers.war.ceasefire_received", clan.name, clan.name), null);
        }
        ClanData.get(server).changed();
        return true;
    }

    /** Capitule (decision {@code war} du regime du vaincu) : tribut de 5 % du tresor, 5 000 jetons au plus, verse au vainqueur. */
    public static void surrender(ServerPlayer by, Clan clan, Clan other) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.SURRENDER);
        d.putUUID("target", other.id);
        String err = checkSurrender(by.getServer(), clan, d);
        if (err != null) {
            refuse(by, err);
            return;
        }
        WarDecisions.submit(by, clan, d, other.name);
    }

    @Nullable
    static String checkSurrender(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = target(server, data);
        if ((other == null ? null : live(server, clan, other)) == null) {
            return "clan.pioneers.war.none";
        }
        return ownWar(server, clan, other) == null ? "clan.pioneers.war.federal" : null;
    }

    static boolean applySurrender(MinecraftServer server, Clan clan, CompoundTag data) {
        Clan other = target(server, data);
        War w = ownWar(server, clan, other);
        if (w == null) {
            return false;
        }
        long tribute = EconomyApi.enabled() ? WarRules.tribute(WarRules.get(), clan.treasury) : 0;
        if (tribute > 0 && ClanTreasury.debit(server, clan, tribute, "#clan.pioneers.reason.tribute", clan.name)) {
            if (!ClanTreasury.credit(server, other, tribute, "#clan.pioneers.reason.tribute", clan.name)) {
                //tresor du vainqueur plein : le tribut revient au vaincu (rien ne se perd, rien ne se cree)
                ClanTreasury.credit(server, clan, tribute, "#clan.pioneers.reason.tribute", other.name);
                tribute = 0;
            }
        } else {
            tribute = 0;
        }
        w.tag.putLong("tribute", tribute);
        w.tag.putUUID("loser", clan.id);
        end(server, w, War.SURRENDER);
        return true;
    }

    /** Fin d'une guerre : issue, treve de 3 jours, drapeaux tombes, annonces aux deux camps. */
    public static void end(MinecraftServer server, War w, String result) {
        if (w.ended()) {
            return;
        }
        WarRules.Values v = WarRules.get();
        long now = ClanClock.now();
        w.finish(result, now, v.truceMs());
        Siege.dropFlagsOf(server, w.id(), "clan.pioneers.siege.fell_war");
        String an = sideName(server, w.a(), w.federalA()), bn = sideName(server, w.b(), w.federalB());
        long day = PioneerApi.day(server);
        long truceDays = Math.round(v.truceMs() / (double) WarRules.DAY);
        Component how = Component.translatable("clan.pioneers.war.result." + result, w.tag.getLong("tribute"));
        Component msg = Component.translatable("clan.pioneers.war.ended", an, bn, how, truceDays);
        for (boolean attacker : new boolean[]{true, false}) {
            for (Clan c : side(server, attacker ? w.a() : w.b(), attacker ? w.federalA() : w.federalB())) {
                c.log(day, "clan.pioneers.log.war_ended", attacker ? bn : an, "#clan.pioneers.war.result_short." + result);
                tellClan(server, c, msg, RpSounds.TRUCE.get());
            }
        }
        ClanAudit.write(server, "war", null, "fin de la guerre " + an + " contre " + bn + " : " + result + (w.tag.getLong("tribute") > 0
              ? ", tribut " + w.tag.getLong("tribute") : ""));
        ClanData.get(server).changed();
    }

    // ------------------------------------------------------------------ administration
    /** Leve une guerre (operateur) : elle finit comme les autres (treve comprise). */
    public static boolean adminEnd(MinecraftServer server, Clan a, Clan b) {
        War w = live(server, a, b);
        if (w == null) {
            return false;
        }
        end(server, w, War.ADMIN);
        return true;
    }

    /** Leve une treve (operateur). */
    public static boolean adminLiftTruce(MinecraftServer server, Clan a, Clan b) {
        long now = ClanClock.now();
        boolean done = false;
        for (War w : wars(server)) {
            if (w.inTruce(now) && (w.attacker(a) && w.defender(b) || w.attacker(b) && w.defender(a))) {
                w.tag.putLong("truce", now);
                done = true;
            }
        }
        if (done) {
            ClanAudit.write(server, "war", a, "treve levee par un operateur avec " + b.name);
            ClanData.get(server).changed();
        }
        return done;
    }

    // ------------------------------------------------------------------ controle (une fois par seconde, hors tick des entites)
    /** Fins de preavis, expirations, treves, chunks pris hors budget, creneaux et traites. Borne : quelques guerres, quelques clans. */
    public static void tick(MinecraftServer server) {
        long now = ClanClock.now();
        ClanData data = ClanData.get(server);
        WarRules.Values v = WarRules.get();
        long day = PioneerApi.day(server);
        List<Tag> drop = new ArrayList<>();
        for (Tag t : new ArrayList<>(data.wars)) {
            War w = new War((CompoundTag) t);
            List<Clan> as = side(server, w.a(), w.federalA()), bs = side(server, w.b(), w.federalB());
            if (!w.ended()) {
                if (as.isEmpty() || bs.isEmpty()) {
                    end(server, w, War.DISSOLVED);
                    continue;
                }
                if (!w.tag.getBoolean("opened") && w.start() <= now) {
                    w.tag.putBoolean("opened", true);
                    if (!w.federalB() && bs.size() == 1) {
                        w.tag.putInt("bSize", bs.get(0).claimCount());
                    }
                    String an = sideName(server, w.a(), w.federalA()), bn = sideName(server, w.b(), w.federalB());
                    Component msg = Component.translatable("clan.pioneers.war.opened", an, bn);
                    for (Clan c : as) {
                        c.log(day, "clan.pioneers.log.war_opened", bn);
                        tellClan(server, c, msg, RpSounds.SIEGE_HORN.get());
                    }
                    for (Clan c : bs) {
                        c.log(day, "clan.pioneers.log.war_opened", an);
                        tellClan(server, c, msg, RpSounds.SIEGE_HORN.get());
                    }
                    data.changed();
                } else if (w.open(now) && WarRules.expired(v, w.start(), w.lastCapture(), now)) {
                    end(server, w, War.EXPIRED);
                }
            } else {
                if (w.truce() <= now && !w.tag.getBoolean("truceOver")) {
                    w.tag.putBoolean("truceOver", true);
                    String an = sideName(server, w.a(), w.federalA()), bn = sideName(server, w.b(), w.federalB());
                    for (Clan c : as) {
                        tellClan(server, c, Component.translatable("clan.pioneers.war.truce_over", bn), null);
                    }
                    for (Clan c : bs) {
                        tellClan(server, c, Component.translatable("clan.pioneers.war.truce_over", an), null);
                    }
                    data.changed();
                }
                if (now > w.truce() + 7 * WarRules.DAY && w.tag.getList("free", Tag.TAG_COMPOUND).isEmpty()) {
                    drop.add(t);
                }
            }
            freeProvisional(server, w, now);
        }
        if (!drop.isEmpty()) {
            data.wars.removeAll(drop);
            data.changed();
        }
        windowsTick(server, data, now);
    }

    /** Chunks pris hors budget : tenus le temps des scelles (24 h), puis rendus libres si le budget ne les couvre toujours pas. */
    private static void freeProvisional(MinecraftServer server, War w, long now) {
        ListTag free = w.tag.getList("free", Tag.TAG_COMPOUND);
        if (free.isEmpty()) {
            return;
        }
        ClanData data = ClanData.get(server);
        boolean changed = false;
        for (int i = free.size() - 1; i >= 0; i--) {
            CompoundTag f = free.getCompound(i);
            if (f.getLong("at") > now) {
                continue;
            }
            free.remove(i);
            changed = true;
            Clan c = f.hasUUID("clan") ? data.clan(f.getUUID("clan")) : null;
            String dim = f.getString("dim");
            long chunk = f.getLong("chunk");
            Clan.Claim claim = c == null ? null : c.claim(dim, chunk);
            if (claim == null) {
                continue;
            }
            if (c.claimCount() <= ClanTerritory.budget(c, now)) {
                claim.declineUntil = 0;
            } else {
                ClanTerritory.drop(server, c, dim, chunk, false);
                c.log(PioneerApi.day(server), "clan.pioneers.log.capture_freed", ClanTerritory.coords(chunk));
            }
        }
        if (changed) {
            w.tag.put("free", free);
            data.changed();
        }
    }

    /** Changements de creneaux arrives a terme, et annonce de l'ouverture d'un creneau d'un clan en guerre. */
    private static void windowsTick(MinecraftServer server, ClanData data, long now) {
        WarRules.Values v = WarRules.get();
        int minute = minuteOfDay(now);
        for (Clan c : data.clans()) {
            if (c.pendingWindows != null && c.windowsFrom > 0 && now >= c.windowsFrom) {
                c.siegeWindows = c.pendingWindows;
                c.pendingWindows = null;
                c.windowsFrom = 0;
                c.log(PioneerApi.day(server), "clan.pioneers.log.windows_active", hour(c.siegeWindows[0]),
                      hour(c.siegeWindows.length > 1 ? c.siegeWindows[1] : c.siegeWindows[0]));
                data.changed();
            }
        }
        for (War w : wars(server)) {
            if (!w.open(now)) {
                continue;
            }
            for (Clan d : side(server, w.b(), w.federalB())) {
                boolean open = WarRules.inWindow(windows(d, now), minute, v.windowHours());
                Boolean before = WINDOW_OPEN.put(d.id, open);
                if (open && before != null && !before) {
                    int end = WarRules.windowEnd(windows(d, now), minute, v.windowHours());
                    Component msg = Component.translatable("clan.pioneers.war.window_open", d.name, hour(end / 60));
                    tellClan(server, d, msg, RpSounds.SIEGE_HORN.get());
                    for (Clan a : side(server, w.a(), w.federalA())) {
                        tellClan(server, a, msg, null);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ messages
    static void tellClan(MinecraftServer server, Clan c, Component msg, @Nullable SoundEvent sound) {
        Component line = ClanMessages.tagged(c, msg);
        for (UUID u : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                p.sendSystemMessage(line);
                if (sound != null) {
                    p.playNotifySound(sound, SoundSource.PLAYERS, 0.9F, 1.0F);
                }
            }
        }
    }

    static void refuse(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    /** Vue des guerres d'un clan (onglet Clan) : autre camp, sens, etat, dates, captures, treve, motif. */
    public static ListTag view(MinecraftServer server, Clan clan) {
        long now = ClanClock.now();
        ListTag out = new ListTag();
        for (War w : warsOf(server, clan)) {
            boolean att = w.attacker(clan);
            CompoundTag c = new CompoundTag();
            c.putString("other", att ? sideName(server, w.b(), w.federalB()) : sideName(server, w.a(), w.federalA()));
            c.putBoolean("attacker", att);
            c.putBoolean("federal", w.federalA() || w.federalB());
            c.putString("state", w.ended() ? "truce" : w.notice(now) ? "notice" : "open");
            c.putLong("start", w.start());
            c.putLong("truce", w.truce());
            c.putInt("captures", w.captures());
            c.putString("reason", w.reason());
            c.putString("result", w.result());
            c.putBoolean("ceasefireOffered", w.tag.getBoolean(att ? "cfA" : "cfB"));
            c.putBoolean("ceasefireReceived", w.tag.getBoolean(att ? "cfB" : "cfA"));
            out.add(c);
        }
        return out;
    }
}
