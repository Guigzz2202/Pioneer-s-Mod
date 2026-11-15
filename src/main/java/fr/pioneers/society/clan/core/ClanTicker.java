package fr.pioneers.society.clan.core;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.treasury.ClanFinance;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.core.profile.PioneerApi;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : rythme des fondations des clans, sans calcul par tick : la file des votes une fois par seconde (sa tete seulement),
 * un balayage par minute (echeances des regimes, successions, rotations, clans vides a liberer, invitations expirees, derniere connexion des
 * joueurs connectes), le changement de jour de jeu (cotisations, dividende). Connexions, deconnexions, signature de la Charte sur le
 * fondateur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanTicker {

    private static long lastDay = Long.MIN_VALUE;

    private ClanTicker() {
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ClanVotes.rebuild(event.getServer());
        lastDay = PioneerApi.day(event.getServer());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ClanVotes.clear();
        ClanCore.clear();
        ClanClock.reset();
        lastDay = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || !ClanCore.enabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 20 == 0) {
            ClanVotes.poll(server);
        }
        if (t % 100 == 0) {
            long day = PioneerApi.day(server);
            if (lastDay == Long.MIN_VALUE) {
                lastDay = day;
            } else if (day != lastDay) {
                lastDay = day;
                ClanFinance.newDay(server, day);
            }
        }
        if (t % 1200 == 0) {
            sweep(server);
        }
    }

    /** Balayage d'une minute (jamais par tick). */
    static void sweep(MinecraftServer server) {
        long now = ClanClock.now();
        ClanData data = ClanData.get(server);
        for (Clan c : new ArrayList<>(data.clans())) {
            if (c.members.isEmpty()) {
                if (ClanCore.pendingDissolutions(server, now).contains(c)) {
                    ClanCore.finishDissolution(server, c);
                }
                continue;
            }
            ClanGov.tick(server, c, now);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Clan c = data.clanOf(p.getUUID());
            if (c != null) {
                ClanCore.touch(c, p, now);
            }
        }
        ClanCore.expireInvites(now);
        data.changed();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ClanApi.enabled()) {
            ClanCore.login(p);
            Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
            if (c != null && ClanCore.enabled()) {
                ClanFinance.collectDues(p.getServer(), c, p, PioneerApi.day(p.getServer()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ClanCore.logout(p);
        }
    }

    /** Clic droit sur le fondateur qui tient sa Charte ouverte : signer. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer signer)
            || !(event.getTarget() instanceof ServerPlayer founder) || !ClanCore.enabled()) {
            return;
        }
        if (Charter.signOnFounder(signer, founder)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
