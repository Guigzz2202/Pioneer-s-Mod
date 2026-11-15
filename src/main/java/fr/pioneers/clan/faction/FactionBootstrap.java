package fr.pioneers.clan.faction;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Faction;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanCore;
import fr.pioneers.clan.people.Peoples;
import fr.pioneers.clan.people.Rallying;
import fr.pioneers.clan.vote.ClanVotes;
import fr.pioneers.clan.vote.Proposal;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.village.Villages;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : demarrage des factions et des peuples de joueurs (lots 8 et 9) et leurs controles espaces, sans calcul par tick :
 * passage du jour de jeu (contribution federale, chunkloading federal, pourparlers de ralliement) verifie toutes les 5 secondes ; echeances
 * (preavis de depart, mandats, rotation, revocation, declin des peuples, Pactes et noms perimes) chaque minute ; tickets federaux remis au
 * demarrage. Actions reseau : {@code FACTION_ACTION} {op} : pact_open, pact_cancel, pact_sign, pact_seal (voir {@link FederalPact#edit}),
 * sheet {sheet} (reponse {@code FACTION_VIEW} sorte « sheet »), vote {proposal, choice ou candidat par jeton}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FactionBootstrap {

    private static long lastDay = Long.MIN_VALUE;

    private FactionBootstrap() {
    }

    /** Appele par {@code ClanBootstrap.init} : objets, reseau, decisions, crochets des villages. */
    public static void init() {
        FactionItems.init();
        FactionGov.install();
        Rallying.install();
        Villages.playerBanner = (level, id) -> {
            Faction f = Peoples.factionOf(level.getServer(), id);
            return f == null ? null : f.extra.getCompound(Peoples.META).getList("banner", Tag.TAG_COMPOUND);
        };
        Villages.workerPayer = FactionWorkers::pay;
        RpNet.onServer(RpActions.FACTION_ACTION, FactionBootstrap::action);
    }

    private static void action(ServerPlayer p, CompoundTag t) {
        String op = t.getString("op");
        if (op.startsWith("pact_")) {
            FederalPact.edit(p, t);
            return;
        }
        if ("vote".equals(op)) {
            vote(p, t);
            op = "sheet";
            t.putString("sheet", "government");
        }
        if ("sheet".equals(op)) {
            String sheet = FactionViews.SHEETS.contains(t.getString("sheet")) ? t.getString("sheet") : "faction";
            CompoundTag v = FactionViews.sheet(p, sheet);
            v.putString("kind", "sheet");
            RpNet.send(p, RpActions.FACTION_VIEW, v);
        }
    }

    /** Bulletin depuis l'interface : oui / non / abstention, ou candidat designe par son jeton de clan (jamais un identifiant de compte). */
    private static void vote(ServerPlayer p, CompoundTag t) {
        Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
        if (f == null) {
            return;
        }
        Proposal x = ClanVotes.find(f.proposals, t.getString("proposal"));
        String choice = t.getString("choice");
        if (x != null && x.election()) {
            String found = null;
            for (UUID u : x.candidates()) {
                fr.pioneers.clan.Clan c = ClanData.get(p.getServer()).clanOf(u);
                if (c != null && fr.pioneers.clan.core.ClanViews.token(c, u).equals(choice)) {
                    found = u.toString();
                }
            }
            if (found == null) {
                return;
            }
            choice = found;
        }
        String err = ClanVotes.vote(p, ClanVotes.Scope.FACTION, f.id, t.getString("proposal"), choice);
        CompoundTag n = new CompoundTag();
        n.putString("kind", err == null ? "ok" : "error");
        n.putString("op", "faction_vote");
        if (err != null) {
            n.putString("key", err);
        }
        RpNet.send(p, RpActions.CLAN_NOTICE, n);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        lastDay = PioneerApi.day(server);
        FactionZones.restore(server);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        lastDay = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || !ClanCore.enabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 100 == 37) {
            long day = PioneerApi.day(server);
            if (lastDay == Long.MIN_VALUE) {
                lastDay = day;
            } else if (day > lastDay) {
                //un saut de plusieurs jours ne compte qu'un passage
                lastDay = day;
                Factions.settleContributions(server);
                FactionZones.dayChange(server);
                Rallying.dayChange(server, day);
            } else if (day < lastDay) {
                lastDay = day;
            }
        }
        if (t % 1200 == 611) {
            Factions.tickMinute(server, ClanClock.now());
        }
    }
}
