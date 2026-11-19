package fr.pioneers.space.mega;

import fr.pioneers.space.mega.client.MegaClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * 1.0.7 (« ciel grandiose ») : megastructures en fonctionnement (bit i : un port de la megastructure i a travaille recemment,
 * {@link MegaActivity}) ; envoye a la connexion et a chaque changement, au plus une fois par seconde. Le ciel en tire ses animations.
 */
public record PacketMegaWorking(long mask) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(mask);
    }

    public static PacketMegaWorking decode(FriendlyByteBuf buffer) {
        return new PacketMegaWorking(buffer.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MegaClient.setWorking(mask)));
        context.get().setPacketHandled(true);
    }
}
