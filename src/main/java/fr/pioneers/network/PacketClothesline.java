package fr.pioneers.network;

import fr.pioneers.home.line.LineEvents;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Patch 1.0.3-beta, lot 7 : clic droit MAIN VIDE en l'air sur une corde a linge (Minecraft ne previent le serveur d'aucun clic dans le vide) :
 * le serveur refait lui-meme la visee depuis les yeux du joueur ({@link LineEvents#interact}), a portee de main ; le paquet ne porte rien.
 */
public record PacketClothesline() {

    public void encode(FriendlyByteBuf buffer) {
    }

    public static PacketClothesline decode(FriendlyByteBuf buffer) {
        return new PacketClothesline();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null && !player.isSpectator() && player.getMainHandItem().isEmpty()) {
            LineEvents.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, Double.MAX_VALUE);
        }
    }
}
