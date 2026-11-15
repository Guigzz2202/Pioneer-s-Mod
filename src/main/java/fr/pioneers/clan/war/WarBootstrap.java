package fr.pioneers.clan.war;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.rep.ClanReputation;
import fr.pioneers.clan.stats.ClanStats;
import fr.pioneers.clan.core.ClanClock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : chargement de la diplomatie, de la guerre, des statistiques et de la reputation de clan : registres (Drapeau de
 * siege), donnees ({@code data/pioneers/clans/war|stats|reputation.json}), commandes ({@code /clan treaty|war|ceasefire|surrender|windows},
 * {@code /clan admin war}), crochets (cascade de reputation, production, decisions du regime). Un seul controle par seconde (fins de
 * preavis, treves, creneaux, traites, potentiels, contributions) et une agregation toutes les 10 minutes : rien par tick des entites.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class WarBootstrap {

    private static int ticks;

    private WarBootstrap() {
    }

    public static void init(IEventBus modBus) {
        WarBlocks.init();
        WarDecisions.install();
        ClanStats.install();
        ClanReputation.install();
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(WarData.INSTANCE);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        WarCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ClanApi.enabled()) {
            return;
        }
        if (++ticks % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ClanWar.tick(server);
        ClanStats.tick(server);
        if (ticks % 100 == 0) {
            ClanDiplomacy.tick(server, ClanClock.now());
        }
    }

    /** Tir allie coupe : deux joueurs de clans allies (traite d'alliance ou meme faction) ne se blessent pas, sauf en guerre. */
    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !ClanApi.enabled()) {
            return;
        }
        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        ClanData data = ClanData.get(victim.getServer());
        var a = data.clanOf(attacker.getUUID());
        var b = data.clanOf(victim.getUUID());
        if (a != null && b != null && !a.id.equals(b.id) && ClanApi.relation(victim.getServer(), a, b) == ClanApi.Relation.ALLY) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        Siege.clear();
        ClanStats.clear();
        ticks = 0;
    }
}
