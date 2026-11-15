package fr.pioneers.client.pneumatic;

import fr.pioneers.Pioneers;
import fr.pioneers.pneumatic.network.PiPneumaticNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enregistrements client des tubes pneumatiques (bus du mod) : rendu de la capsule de voyageur et son modele de cabine.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientPneumaticSetup {

    private ClientPneumaticSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PiPneumaticNetwork.TUBE_CAPSULE.get(), TubeCapsuleRenderer::new);
        event.registerEntityRenderer(PiPneumaticNetwork.HYPERTUBE.get(), HypertubeRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(TubeCapsuleRenderer.CABIN);
    }
}
