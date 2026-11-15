package fr.pioneers.client;

import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 29, retour 40) : avance {@link RenderClock} a la fin de chaque tick du client, avec l'heure du monde du client (que le
 * serveur remet a son heure : les sauts sont les observations de l'heure du serveur). Rien en pause (le monde ne tourne pas non plus).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class RenderClockTicker {

    private RenderClockTicker() {
    }

    @SubscribeEvent
    public static void renderClockTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) {
            RenderClock.tick(mc.level, mc.level.getGameTime());
        }
    }
}
