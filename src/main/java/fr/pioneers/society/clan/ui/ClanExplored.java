package fr.pioneers.society.clan.ui;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : ce que chaque joueur a explore, pour la carte strategique (« on ne voit que ce qu'on a explore, plus ce que
 * partagent les allies »). Grain : la region de 4 x 4 chunks ; un joueur revele les regions a {@link #RADIUS} regions autour de lui (a peu
 * pres sa distance de vue). Releve toutes les 2 secondes, seulement quand le joueur change de region ; donnee de monde (Overworld), un
 * ensemble de regions par joueur et par dimension. Jamais envoye tel quel au client.
 */
public final class ClanExplored extends SavedData {

    public static final String NAME = "pioneers_clan_explored";
    public static final int VERSION = 1;
    /** Taille d'une region en chunks (decalage) et rayon revele autour du joueur (en regions). */
    public static final int SHIFT = 2, RADIUS = 2;

    private final Map<UUID, Map<String, LongOpenHashSet>> seen = new HashMap<>();
    /** Derniere region notee par joueur (memoire seulement). */
    private static final Map<UUID, String> LAST = new HashMap<>();

    public static ClanExplored get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ClanExplored::load, ClanExplored::new, NAME);
    }

    /** Region (cle) d'un chunk. */
    public static long region(int cx, int cz) {
        return ChunkPos.asLong(cx >> SHIFT, cz >> SHIFT);
    }

    /** Regions explorees d'un joueur dans une dimension (null : rien). */
    @Nullable
    public LongOpenHashSet of(UUID player, String dim) {
        Map<String, LongOpenHashSet> m = seen.get(player);
        return m == null ? null : m.get(dim);
    }

    static void tick(MinecraftServer server) {
        ClanExplored data = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String dim = p.level().dimension().location().toString();
            int rx = p.chunkPosition().x >> SHIFT, rz = p.chunkPosition().z >> SHIFT;
            String here = dim + "|" + rx + "|" + rz;
            if (here.equals(LAST.get(p.getUUID()))) {
                continue;
            }
            LAST.put(p.getUUID(), here);
            if (data == null) {
                data = get(server);
            }
            LongOpenHashSet set = data.seen.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).computeIfAbsent(dim, k -> new LongOpenHashSet());
            boolean added = false;
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    added |= set.add(ChunkPos.asLong(rx + dx, rz + dz));
                }
            }
            if (added) {
                data.setDirty();
            }
        }
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clear() {
        LAST.clear();
    }

    private static ClanExplored load(CompoundTag tag) {
        ClanExplored d = new ClanExplored();
        CompoundTag players = tag.getCompound("players");
        for (String k : players.getAllKeys()) {
            UUID u;
            try {
                u = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag dims = players.getCompound(k);
            Map<String, LongOpenHashSet> m = new HashMap<>();
            for (String dim : dims.getAllKeys()) {
                m.put(dim, new LongOpenHashSet(dims.getLongArray(dim)));
            }
            d.seen.put(u, m);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("version", VERSION);
        CompoundTag players = new CompoundTag();
        seen.forEach((u, m) -> {
            CompoundTag dims = new CompoundTag();
            m.forEach((dim, set) -> dims.putLongArray(dim, set.toLongArray()));
            players.put(u.toString(), dims);
        });
        tag.put("players", players);
        return tag;
    }
}
