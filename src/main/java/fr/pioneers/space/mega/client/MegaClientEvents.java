package fr.pioneers.space.mega.client;

import fr.pioneers.Pioneers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.0.7 (lot 4) : a la deconnexion, l'etat des megastructures d'un serveur ne reste pas dans le ciel d'un autre. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MegaClientEvents {

    private MegaClientEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MegaClient.clear();
    }
}
