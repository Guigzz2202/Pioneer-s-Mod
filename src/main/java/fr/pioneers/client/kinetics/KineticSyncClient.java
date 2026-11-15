package fr.pioneers.client.kinetics;

import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.network.PacketKineticSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 0.20.46 (audit) : reception de l'etat de rotation d'un reseau (voir {@link PacketKineticSync}). */
public final class KineticSyncClient {

    private KineticSyncClient() {
    }

    public static void apply(PacketKineticSync packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long[] positions = packet.positions();
        int[] entries = packet.entries();
        double[] speeds = packet.speeds(), phases = packet.phases();
        for (int i = 0; i < positions.length; i++) {
            int e = entries[i];
            if (e < 0 || e >= speeds.length) {
                continue;
            }
            BlockPos pos = BlockPos.of(positions[i]);
            if (!level.isLoaded(pos)) {
                continue;                                        //Chunk pas encore recu : il arrivera avec l'etat deja envoye
            }
            BlockEntity tile = level.getBlockEntity(pos);
            if (tile instanceof KineticTile kinetic) {
                kinetic.receiveRotation(speeds[e], phases[e], packet.time(), packet.jammed());
            }
        }
    }
}
