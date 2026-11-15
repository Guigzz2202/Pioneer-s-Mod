package fr.pioneers.space.cargo;

import fr.pioneers.Pioneers;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fait avancer les fusees cargo en vol (arrivees, atterrissages).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class CargoEvents {

    private CargoEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CargoNetwork.get(event.getServer()).tick(event.getServer());
        }
    }
}
