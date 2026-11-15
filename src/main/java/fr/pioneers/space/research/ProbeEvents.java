package fr.pioneers.space.research;

import fr.pioneers.Pioneers;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fait "tourner" les sondes en orbite (production de donnees) a chaque tick serveur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ProbeEvents {

    private ProbeEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ProbeNetwork.get(event.getServer()).tick(event.getServer().overworld().getRandom());
        }
    }
}
