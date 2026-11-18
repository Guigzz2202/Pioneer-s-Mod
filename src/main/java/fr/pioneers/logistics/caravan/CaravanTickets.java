package fr.pioneers.logistics.caravan;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 1.0.4-beta (lot 10) : CHUNKS DES CARAVANES EN ROUTE (choix : la caravane charge elle-meme son chemin). Un ticket de region {@link
 * PiCaravan#TICKET} (rayon {@code TICKET_RADIUS} : 3 x 3 chunks ou les entites tournent, meme sans joueur proche) suit le chunk de la bete
 * de tete tant que la caravane roule ou s'arrete a un relais ; il est retire quand elle s'arrete, meurt ou disparait. Les caravanes actives
 * sont notees dans {@link CaravanWorld} : au redemarrage du serveur leurs tickets sont reposes (la tete revient dans le monde et reprend).
 * Balayage toutes les 30 s : une tete introuvable deux fois de suite rend son ticket.
 */
public final class CaravanTickets {

    private CaravanTickets() {
    }

    private record Held(ResourceKey<Level> dim, ChunkPos chunk) {
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();
    private static final Map<UUID, Integer> MISSES = new HashMap<>();

    /** Le ticket de cette tete suit son chunk. */
    public static void hold(ServerLevel level, UUID head, ChunkPos chunk) {
        Held h = HELD.get(head);
        if (h != null && h.dim == level.dimension() && h.chunk.equals(chunk)) {
            return;
        }
        if (h != null) {
            drop(level.getServer(), h);
        }
        level.getChunkSource().addRegionTicket(PiCaravan.TICKET, chunk, GeneratedCaravan.TICKET_RADIUS, chunk, true);
        HELD.put(head, new Held(level.dimension(), chunk));
        MISSES.remove(head);
        CaravanWorld.get(level.getServer()).note(head, level.dimension().location().toString(), chunk.toLong());
    }

    public static void release(MinecraftServer server, UUID head) {
        Held h = HELD.remove(head);
        MISSES.remove(head);
        if (h != null) {
            drop(server, h);
        }
        CaravanWorld.get(server).forget(head);
    }

    private static void drop(MinecraftServer server, Held h) {
        ServerLevel l = server.getLevel(h.dim);
        if (l != null) {
            l.getChunkSource().removeRegionTicket(PiCaravan.TICKET, h.chunk, GeneratedCaravan.TICKET_RADIUS, h.chunk, true);
        }
    }

    public static boolean holds(UUID head) {
        return HELD.containsKey(head);
    }

    public static int count() {
        return HELD.size();
    }

    /** Demarrage : tickets des caravanes actives notees. */
    public static void restore(MinecraftServer server) {
        for (var e : CaravanWorld.get(server).entries().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getValue().dim());
            ServerLevel l = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (l != null) {
                ChunkPos c = new ChunkPos(e.getValue().chunk());
                l.getChunkSource().addRegionTicket(PiCaravan.TICKET, c, GeneratedCaravan.TICKET_RADIUS, c, true);
                HELD.put(e.getKey(), new Held(l.dimension(), c));
            }
        }
    }

    /** Toutes les 30 s : tetes introuvables (deux fois de suite) ou caravanes arretees -> ticket rendu. */
    public static void sweep(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Held> e = it.next();
            ServerLevel l = server.getLevel(e.getValue().dim);
            var head = l == null ? null : l.getEntity(e.getKey());
            CaravanData d = PiCaravan.get(head);
            boolean gone = head == null || !head.isAlive();
            if (!gone && d != null && d.active()) {
                MISSES.remove(e.getKey());
                continue;
            }
            int miss = MISSES.merge(e.getKey(), 1, Integer::sum);
            if (!gone || miss >= 2) {
                drop(server, e.getValue());
                it.remove();
                MISSES.remove(e.getKey());
                CaravanWorld.get(server).forget(e.getKey());
            }
        }
    }

    /** Arret du serveur : tout oublier (les tickets meurent avec les mondes). */
    public static void clear() {
        HELD.clear();
        MISSES.clear();
    }
}
