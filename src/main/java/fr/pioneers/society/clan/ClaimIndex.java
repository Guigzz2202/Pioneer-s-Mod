package fr.pioneers.society.clan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/**
 * 1.9 (MAJ RP, bloc 4) : index des claims, par dimension : chunk -> clan. Reconstruit depuis {@link ClanData} au demarrage du serveur et mis
 * a jour par les seules operations de claim. TOUTE verification de protection passe par ici : une lecture, aucune boucle.
 */
public final class ClaimIndex {

    private static final Map<String, Long2ObjectOpenHashMap<UUID>> INDEX = new HashMap<>();

    private ClaimIndex() {
    }

    @Nullable
    public static UUID owner(String dim, long chunk) {
        Long2ObjectOpenHashMap<UUID> m = INDEX.get(dim);
        return m == null ? null : m.get(chunk);
    }

    @Nullable
    public static UUID owner(String dim, ChunkPos pos) {
        return owner(dim, pos.toLong());
    }

    public static void set(String dim, long chunk, @Nullable UUID clan) {
        Long2ObjectOpenHashMap<UUID> m = INDEX.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>());
        if (clan == null) {
            m.remove(chunk);
        } else {
            m.put(chunk, clan);
        }
    }

    public static boolean any() {
        for (Long2ObjectOpenHashMap<UUID> m : INDEX.values()) {
            if (!m.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Reconstruit tout l'index (demarrage du serveur, commande d'administration). */
    public static void rebuild(MinecraftServer server) {
        INDEX.clear();
        for (Clan c : ClanData.get(server).clans()) {
            c.claims.forEach((dim, m) -> m.keySet().forEach(chunk -> set(dim, chunk, c.id)));
        }
    }

    public static void clear() {
        INDEX.clear();
    }
}
