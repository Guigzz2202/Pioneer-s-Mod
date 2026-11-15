package fr.pioneers.core.network;

import fr.pioneers.tech.kinetics.client.KineticSyncClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 0.20.46 (audit) : etat de rotation d'un reseau de force rotative (serveur -> client), un paquet par joueur et par synchronisation au lieu
 * d'un paquet de bloc par piece. Instant serveur et blocage communs ; palette des couples (vitesse, phase) distincts (les pieces d'un meme
 * rapport partagent le leur) ; chaque piece = position + indice dans la palette.
 */
public record PacketKineticSync(long time, boolean jammed, double[] speeds, double[] phases, long[] positions, int[] entries) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(time);
        buffer.writeBoolean(jammed);
        buffer.writeVarInt(speeds.length);
        for (int i = 0; i < speeds.length; i++) {
            buffer.writeDouble(speeds[i]);
            buffer.writeDouble(phases[i]);
        }
        buffer.writeVarInt(positions.length);
        for (int i = 0; i < positions.length; i++) {
            buffer.writeLong(positions[i]);
            buffer.writeVarInt(entries[i]);
        }
    }

    public static PacketKineticSync decode(FriendlyByteBuf buffer) {
        long time = buffer.readLong();
        boolean jammed = buffer.readBoolean();
        int n = buffer.readVarInt();
        if (n < 0 || n > 65_536) {
            throw new IllegalArgumentException("palette de rotation invalide : " + n);
        }
        double[] speeds = new double[n], phases = new double[n];
        for (int i = 0; i < n; i++) {
            speeds[i] = buffer.readDouble();
            phases[i] = buffer.readDouble();
        }
        int m = buffer.readVarInt();
        if (m < 0 || m > 65_536) {
            throw new IllegalArgumentException("pieces de rotation invalides : " + m);
        }
        long[] positions = new long[m];
        int[] entries = new int[m];
        for (int i = 0; i < m; i++) {
            positions[i] = buffer.readLong();
            entries[i] = buffer.readVarInt();
        }
        return new PacketKineticSync(time, jammed, speeds, phases, positions, entries);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KineticSyncClient.apply(this));
    }
}
