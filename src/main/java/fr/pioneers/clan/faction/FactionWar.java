package fr.pioneers.clan.faction;

import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.ClanTreasury;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.stats.ClanStats;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.clan.war.ClanDiplomacy;
import fr.pioneers.clan.war.ClanWar;
import fr.pioneers.clan.war.War;
import fr.pioneers.clan.war.WarRules;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * 1.9 (MAJ RP, bloc 4) : guerre federale. Une faction declare la guerre a une autre faction ou a un clan (500 jetons du tresor federal) ;
 * la ligne est ecrite au format de {@code ClanData.wars} ({@link War} : camp {@code a} = la faction, {@code fa} vrai) que lisent
 * {@code Wars.atWar}, les sieges et la fin de guerre : tous ses clans y sont engages, chacun avec ses creneaux. Memes refus qu'une guerre de
 * clan ({@link WarRules#refuse}), sur les valeurs cumulees des clans du camp. Cessez-le-feu et capitulation d'une guerre de faction : decision
 * federale ({@code peace}) ; le tribut d'une capitulation est pris au tresor federal.
 */
public final class FactionWar {

    private FactionWar() {
    }

    /** Camp vise : une faction (par nom ou etiquette) ou un clan (par nom). */
    public record Target(UUID id, boolean faction, String name) {
    }

    @Nullable
    public static Target target(MinecraftServer server, String name) {
        Faction f = Factions.byName(server, name);
        if (f != null) {
            return new Target(f.id, true, f.name);
        }
        Clan c = ClanData.get(server).byName(name.trim());
        return c == null ? null : new Target(c.id, false, c.name);
    }

    /** Clans d'un camp. */
    static List<Clan> side(MinecraftServer server, UUID id, boolean faction) {
        return ClanWar.side(server, id, faction);
    }

    private static boolean sameFaction(Faction f, MinecraftServer server, UUID id, boolean faction) {
        if (faction) {
            return f.id.equals(id);
        }
        Clan c = ClanData.get(server).clan(id);
        return c != null && f.id.equals(c.faction);
    }

    // ------------------------------------------------------------------ declaration
    @Nullable
    static String checkDeclare(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag d) {
        if (!ClanWar.enabled()) {
            return "clan.pioneers.war.disabled";
        }
        if (!d.hasUUID("target")) {
            return "clan.pioneers.error.no_clan";
        }
        UUID tid = d.getUUID("target");
        boolean tf = d.getBoolean("targetFaction");
        List<Clan> mine = Factions.clans(server, f), theirs = side(server, tid, tf);
        if (theirs.isEmpty()) {
            return "clan.pioneers.error.no_clan";
        }
        if (sameFaction(f, server, tid, tf)) {
            return "clan.pioneers.war.refuse.self";
        }
        WarRules.Values v = WarRules.get();
        long now = ClanClock.now();
        int pa = 0, pd = 0;
        boolean solo = false, nap = false, truce = false, live = false, allied = false;
        long ageA = Long.MAX_VALUE, ageB = Long.MAX_VALUE;
        for (Clan a : mine) {
            pa += WarRules.ratioPotential(ClanStats.clanPotential(server, a), a.activeCount(now), v.memberFloor());
            ageA = Math.min(ageA, now - a.founded);
        }
        for (Clan b : theirs) {
            pd += WarRules.ratioPotential(ClanStats.clanPotential(server, b), b.activeCount(now), v.memberFloor());
            ageB = Math.min(ageB, now - b.founded);
            solo |= b.solo;
            for (Clan a : mine) {
                nap |= ClanDiplomacy.nonAggression(a, b);
                truce |= ClanWar.inTruce(server, a, b);
                live |= ClanWar.live(server, a, b) != null;
                allied |= ClanDiplomacy.alliance(a, b);
            }
        }
        int out = 0, all = 0, theirsAll = 0;
        for (War w : ClanWar.wars(server)) {
            if (!w.live()) {
                continue;
            }
            if (f.id.equals(w.a())) {
                out++;
            }
            if (f.id.equals(w.a()) || f.id.equals(w.b())) {
                all++;
            }
            if (tid.equals(w.a()) || tid.equals(w.b())) {
                theirsAll++;
            }
        }
        boolean economy = EconomyApi.enabled();
        return WarRules.refuse(v, new WarRules.Declaration(false, solo, ageA, ageB, pa, pd, out, all, theirsAll, nap, truce, live, allied,
              d.getString("reason").length(), economy ? f.treasury : Long.MAX_VALUE, false));
    }

    static boolean applyDeclare(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag d) {
        if (checkDeclare(server, f, null, d) != null) {
            return false;
        }
        UUID tid = d.getUUID("target");
        boolean tf = d.getBoolean("targetFaction");
        WarRules.Values v = WarRules.get();
        long cost = EconomyApi.enabled() ? FactionRules.get().warCost() : 0;
        if (cost > 0 && !Factions.debit(server, f, cost, "#clan.pioneers.faction.reason.war", Factions.character(server, author))) {
            return false;
        }
        long now = ClanClock.now();
        String reason = d.getString("reason");
        War w = War.create(f.id, true, tid, tf, reason, now, now + v.noticeMs());
        List<Clan> theirs = side(server, tid, tf);
        int size = 0;
        for (Clan b : theirs) {
            size += b.claimCount();
        }
        w.tag.putInt("bSize", size);
        ClanData.get(server).wars.add(w.tag);
        String an = f.name, bn = ClanWar.sideName(server, tid, tf);
        long day = PioneerApi.day(server);
        long hours = Math.round(v.noticeMs() / (double) WarRules.HOUR);
        Component msg = Component.translatable("clan.pioneers.faction.war_declared", an, bn, reason, hours);
        Factions.log(server, f, "clan.pioneers.faction.log.war", bn, reason);
        for (Clan c : Factions.clans(server, f)) {
            c.log(day, "clan.pioneers.log.war_declared", an, bn, reason);
            tell(server, c, msg);
        }
        for (Clan c : theirs) {
            c.log(day, "clan.pioneers.log.war_declared", an, bn, reason);
            tell(server, c, msg);
        }
        ClanAudit.write(server, "war", null, "guerre federale : " + an + " (" + f.id + ") contre " + bn + " (" + tid + ") : " + reason);
        ClanData.get(server).changed();
        return true;
    }

    private static void tell(MinecraftServer server, Clan c, Component msg) {
        for (UUID u : c.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                p.sendSystemMessage(msg);
                p.level().playSound(null, p.blockPosition(), RpSounds.SIEGE_HORN.get(), SoundSource.PLAYERS, 0.8F, 1F);
            }
        }
    }

    // ------------------------------------------------------------------ cessez-le-feu, capitulation
    /** Guerre non finie ou la faction est un camp. */
    @Nullable
    static War warOf(MinecraftServer server, Faction f, UUID other) {
        for (War w : ClanWar.wars(server)) {
            if (!w.live()) {
                continue;
            }
            if (f.id.equals(w.a()) && other.equals(w.b()) || f.id.equals(w.b()) && other.equals(w.a())) {
                return w;
            }
        }
        return null;
    }

    @Nullable
    static String checkPeace(MinecraftServer server, Faction f, @Nullable ServerPlayer actor, CompoundTag d) {
        if (!d.hasUUID("target")) {
            return "clan.pioneers.war.none";
        }
        War w = warOf(server, f, d.getUUID("target"));
        if (w == null) {
            return "clan.pioneers.war.none";
        }
        boolean a = f.id.equals(w.a());
        if (!d.getBoolean("surrender") && w.tag.getBoolean(a ? "cfA" : "cfB")) {
            return "clan.pioneers.war.ceasefire_already";
        }
        return null;
    }

    static boolean applyPeace(MinecraftServer server, Faction f, @Nullable UUID author, CompoundTag d) {
        if (!d.hasUUID("target")) {
            return false;
        }
        War w = warOf(server, f, d.getUUID("target"));
        if (w == null) {
            return false;
        }
        boolean a = f.id.equals(w.a());
        UUID other = a ? w.b() : w.a();
        boolean otherFaction = a ? w.federalB() : w.federalA();
        if (d.getBoolean("surrender")) {
            long tribute = EconomyApi.enabled() ? WarRules.tribute(WarRules.get(), f.treasury) : 0;
            if (tribute > 0 && Factions.debit(server, f, tribute, "#clan.pioneers.reason.tribute", f.name)) {
                if (!payTribute(server, other, otherFaction, tribute, f.name)) {
                    //tresor du vainqueur plein : le tribut revient (rien ne se perd, rien ne se cree)
                    Factions.credit(server, f, tribute, "#clan.pioneers.reason.tribute", f.name);
                    tribute = 0;
                }
            } else {
                tribute = 0;
            }
            w.tag.putLong("tribute", tribute);
            w.tag.putUUID("loser", f.id);
            ClanWar.end(server, w, War.SURRENDER);
            return true;
        }
        w.tag.putBoolean(a ? "cfA" : "cfB", true);
        if (w.tag.getBoolean("cfA") && w.tag.getBoolean("cfB")) {
            ClanWar.end(server, w, War.CEASEFIRE);
        } else {
            String otherName = ClanWar.sideName(server, other, otherFaction);
            Factions.log(server, f, "clan.pioneers.faction.log.ceasefire", otherName);
            Factions.tell(server, f, Component.translatable("clan.pioneers.faction.ceasefire_offered", otherName));
            //l'autre camp repond par sa propre decision : une faction par /faction, un clan par /clan en nommant un clan de la faction
            List<Clan> ours = Factions.clans(server, f);
            Component msg = otherFaction ? Component.translatable("clan.pioneers.faction.ceasefire_received_faction", f.name, f.name)
                  : Component.translatable("clan.pioneers.faction.ceasefire_received_clan", f.name, ours.isEmpty() ? f.name : ours.get(0).name);
            for (Clan c : side(server, other, otherFaction)) {
                for (UUID u : c.members.keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null) {
                        p.sendSystemMessage(msg);
                    }
                }
            }
        }
        ClanData.get(server).changed();
        return true;
    }

    private static boolean payTribute(MinecraftServer server, UUID id, boolean faction, long amount, String from) {
        if (faction) {
            Faction o = ClanData.get(server).faction(id);
            return o != null && Factions.credit(server, o, amount, "#clan.pioneers.reason.tribute", from);
        }
        Clan c = ClanData.get(server).clan(id);
        return c != null && ClanTreasury.credit(server, c, amount, "#clan.pioneers.reason.tribute", from);
    }
}
