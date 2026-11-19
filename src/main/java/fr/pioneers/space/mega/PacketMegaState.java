package fr.pioneers.space.mega;

import fr.pioneers.space.mega.client.MegaClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : l'etat des megastructures pour les clients (debloquees, livraisons des chantiers, ouvreurs).
 * Quelques centaines d'octets ; envoye a la connexion et a chaque changement, au plus une fois par seconde ({@link MegaEvents}). Le ciel
 * lit le masque des debloquees ({@link MegaClient#unlockedMask()}), l'ecran du chantier les livraisons.
 */
public record PacketMegaState(long unlocked, int[][] delivered, String[] openers) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(unlocked);
        buffer.writeVarInt(delivered.length);
        for (int i = 0; i < delivered.length; i++) {
            buffer.writeUtf(openers[i], 64);
            buffer.writeVarIntArray(delivered[i]);
        }
    }

    public static PacketMegaState decode(FriendlyByteBuf buffer) {
        long unlocked = buffer.readLong();
        int n = Math.min(64, buffer.readVarInt());
        int[][] delivered = new int[n][];
        String[] openers = new String[n];
        for (int i = 0; i < n; i++) {
            openers[i] = buffer.readUtf(64);
            delivered[i] = buffer.readVarIntArray(64);
        }
        return new PacketMegaState(unlocked, delivered, openers);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MegaClient.set(this)));
        context.get().setPacketHandled(true);
    }
}
