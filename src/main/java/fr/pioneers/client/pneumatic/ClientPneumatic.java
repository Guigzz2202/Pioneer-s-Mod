package fr.pioneers.client.pneumatic;

import fr.pioneers.Pioneers;
import fr.pioneers.pneumatic.network.PneumaticNet;
import fr.pioneers.pneumatic.network.TubeTravel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cote client des tubes pneumatiques : ouverture de l'ecran d'une station et voyageur dessine reduit, assis dans la cabine vitree (ses
 * pieds sont sous la capsule pour que ses yeux soient au centre du tube).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientPneumatic {

    /** Reduction du voyageur et remontee (blocs) pour le loger dans la cabine. */
    private static final float SCALE = 0.55F, LIFT = 0.74F;

    private ClientPneumatic() {
    }

    public static void openStation(PneumaticNet.StationData data) {
        Minecraft.getInstance().setScreen(new TubeStationScreen(data));
    }

    @SubscribeEvent
    public static void before(RenderPlayerEvent.Pre event) {
        if (TubeTravel.riding(event.getEntity())) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0, LIFT, 0);
            event.getPoseStack().scale(SCALE, SCALE, SCALE);
        }
    }

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        if (TubeTravel.riding(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }
}
