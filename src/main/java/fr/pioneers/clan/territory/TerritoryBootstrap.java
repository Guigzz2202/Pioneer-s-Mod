package fr.pioneers.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.seal.ClanLevels;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.clan.seal.SealEvents;
import fr.pioneers.clan.seal.SealItems;
import fr.pioneers.core.profile.PioneerApi;
import java.util.function.ToIntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : demarrage du territoire, des Sceaux et des niveaux, et leurs controles espaces (aucun calcul par tick) : jour de
 * jeu toutes les secondes (cout du chunkloading, points du jour), bordures et Tours de guet toutes les 2 secondes, declin, liberations,
 * preavis et Sceaux dus chaque minute.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TerritoryBootstrap {

    private static int ticks;

    private TerritoryBootstrap() {
    }

    /** Appele par {@code ClanBootstrap.init} (enregistrements des registres et des paquets). */
    public static void init(IEventBus modBus) {
        SealItems.init();
        TerritoryNet.register();
        ToIntFunction<ServerPlayer> base = PioneerApi.brigadeRadius;
        PioneerApi.brigadeRadius = p -> ClanSeals.brigadeRadius(p, base.applyAsInt(p));
    }

    /** Apres la reconstruction de l'index par les fondations (priorite basse) : liberations en attente, Sceaux, tickets. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ClanTerritory.reindex(server);
        ClanSeals.rebuild(server);
        ClanChunkloading.reconcile(server);
        TerritorySaved saved = TerritorySaved.get(server);
        if (saved.day < 0) {
            saved.day = PioneerApi.day(server);
            saved.changed();
        }
        Protection.refresh();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ClanSeals.clear();
        TerritoryNet.clear();
        ticks = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ClanApi.enabled()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ticks++;
        if (ticks % 20 == 0) {
            TerritorySaved saved = TerritorySaved.get(server);
            long day = PioneerApi.day(server);
            if (saved.day >= 0 && day > saved.day) {
                //un saut d'horloge de plusieurs jours ne compte qu'un passage
                saved.day = day;
                saved.changed();
                ClanChunkloading.dayChange(server, ClanClock.now());
                ClanLevels.dayChange(server, day);
            } else if (day < saved.day) {
                saved.day = day;
                saved.changed();
            }
        }
        if (ticks % 40 == 0) {
            TerritoryNet.tick(server);
            SealEvents.watch(server, ClanClock.now());
        }
        if (ticks % 1200 == 0) {
            long now = ClanClock.now();
            ClanTerritory.tickMinute(server, now);
            ClanChunkloading.tickMinute(server, now);
            ClanSeals.tickMinute(server);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && ClanApi.enabled()) {
            ClanChunkloading.onSoloLogin(p);
        }
    }
}
