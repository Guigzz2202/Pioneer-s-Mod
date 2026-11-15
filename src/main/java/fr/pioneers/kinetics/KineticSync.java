package fr.pioneers.kinetics;

import fr.pioneers.network.PacketKineticSync;
import fr.pioneers.network.PiNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

/**
 * 0.20.46 (audit) : envoi de l'etat de rotation d'un reseau aux clients : un paquet par joueur qui suit au moins un chunk du reseau, avec les
 * pieces de ces chunks (avant : un paquet de bloc par piece, 2 000 arbres = 2 000 paquets a chaque synchronisation). Les chunks touches sont
 * marques a sauvegarder (la vitesse sauvee sert a la reprise d'un reseau), comme le faisait le setChanged de chaque piece.
 */
final class KineticSync {

    private KineticSync() {
    }

    private record Key(double speed, double phase) {
    }

    static void send(ServerLevel level, KineticNetwork network) {
        List<KineticNode> nodes = network.nodes;
        int n = nodes.size();
        if (n == 0) {
            return;
        }
        //Palette des etats distincts et pieces par chunk
        Map<Key, Integer> palette = new HashMap<>();
        List<Key> keys = new ArrayList<>();
        long[] positions = new long[n];
        int[] entries = new int[n];
        Map<Long, List<Integer>> byChunk = new HashMap<>();
        long time = 0;
        boolean jammed = false;
        int count = 0;
        for (KineticNode node : nodes) {
            if (!(node instanceof KineticTile tile) || tile.isRemoved() || !tile.hasSentState()) {
                continue;
            }
            Key key = new Key(tile.sentSpeedValue(), tile.sentPhaseValue());
            Integer index = palette.get(key);
            if (index == null) {
                index = keys.size();
                palette.put(key, index);
                keys.add(key);
            }
            BlockPos pos = tile.getBlockPos();
            positions[count] = pos.asLong();
            entries[count] = index;
            time = tile.sentTimeValue();
            jammed = tile.sentJamValue();
            byChunk.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), c -> new ArrayList<>()).add(count);
            count++;
        }
        if (count == 0) {
            return;
        }
        double[] speeds = new double[keys.size()], phases = new double[keys.size()];
        for (int i = 0; i < speeds.length; i++) {
            speeds[i] = keys.get(i).speed();
            phases[i] = keys.get(i).phase();
        }
        //Joueurs qui suivent chaque chunk
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Map<ServerPlayer, List<Integer>> byPlayer = new IdentityHashMap<>();
        for (Map.Entry<Long, List<Integer>> e : byChunk.entrySet()) {
            ChunkPos chunk = new ChunkPos(e.getKey());
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
            if (loaded != null) {
                loaded.setUnsaved(true);
            }
            for (ServerPlayer player : chunkMap.getPlayers(chunk, false)) {
                byPlayer.computeIfAbsent(player, p -> new ArrayList<>()).addAll(e.getValue());
            }
        }
        PacketKineticSync whole = null;
        for (Map.Entry<ServerPlayer, List<Integer>> e : byPlayer.entrySet()) {
            List<Integer> mine = e.getValue();
            PacketKineticSync packet;
            if (mine.size() == count) {
                if (whole == null) {
                    whole = new PacketKineticSync(time, jammed, speeds, phases, count == n ? positions : Arrays.copyOf(positions, count),
                          count == n ? entries : Arrays.copyOf(entries, count));
                }
                packet = whole;
            } else {
                long[] p = new long[mine.size()];
                int[] q = new int[mine.size()];
                for (int i = 0; i < p.length; i++) {
                    p[i] = positions[mine.get(i)];
                    q[i] = entries[mine.get(i)];
                }
                packet = new PacketKineticSync(time, jammed, speeds, phases, p, q);
            }
            ServerPlayer player = e.getKey();
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
