package fr.pioneers.network;

import fr.pioneers.Pioneers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.46 (audit) : cadence des demandes envoyees par les clients (chaque demande coute une grosse reponse) : au plus une par intervalle,
 * par joueur et par genre de demande. Nettoie aussi, a la deconnexion, ce que les paquets gardent par joueur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PacketRate {

    /** Genres de demandes. */
    public static final int ROBOTICS = 0, CARGO_MAP = 1, WORKS = 2, MAP_WATCH = 3, LOOK = 4;
    /** 1.9 (MAJ RP, bloc 4) : fiches de l'onglet Clan, carte strategique. */
    public static final int CLAN_SHEET = 5, CLAN_MAP = 6;
    /** MAJ Recettes (lot 7) : pose et retrait du verrou de recette d'une machine. */
    public static final int RECIPE_LOCK = 7, KINDS = 8;
    /** Intervalle minimal entre deux demandes du meme genre (ms). */
    public static final long INTERVAL = 250;

    private static final Map<UUID, long[]> LAST = new HashMap<>();

    private PacketRate() {
    }

    /** La demande peut-elle etre servie (et elle est comptee) ? */
    public static boolean allow(ServerPlayer player, int kind) {
        long now = Util.getMillis();
        long[] last = LAST.computeIfAbsent(player.getUUID(), k -> new long[KINDS]);
        if (last[kind] != 0 && now - last[kind] < INTERVAL) {
            return false;
        }
        last[kind] = now;
        return true;
    }

    private static final Map<UUID, long[]> LAST_KEY = new HashMap<>();

    /**
     * Comme {@link #allow(ServerPlayer, int)}, mais une demande pour un AUTRE objet ({@code key}) passe toujours : seule la meme demande
     * repetee dans l'intervalle est ecartee (une demande unique, jamais renvoyee par le client, n'est donc jamais perdue).
     */
    public static boolean allowKeyed(ServerPlayer player, int kind, long key) {
        long[] keys = LAST_KEY.computeIfAbsent(player.getUUID(), k -> new long[KINDS]);
        long[] last = LAST.computeIfAbsent(player.getUUID(), k -> new long[KINDS]);
        if (keys[kind] != key) {
            keys[kind] = key;
            last[kind] = Util.getMillis();
            return true;
        }
        return allow(player, kind);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST.remove(id);
        LAST_KEY.remove(id);
        PacketComputer.forget(id);
        PacketWorks.forget(id);
        PacketRobotics.forget(id);
        fr.pioneers.transport.logistic.DiagramService.forget(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST.clear();
        LAST_KEY.clear();
        PacketComputer.forgetAll();
        PacketWorks.forgetAll();
        PacketRobotics.forgetAll();
    }
}
