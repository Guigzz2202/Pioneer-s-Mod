package fr.pioneers.client.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.economy.peddler.PeddlerTypes;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 3) : client du Colporteur (couche de la maquette et rendu). Les lamas sont des lamas de marchand vanilla. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PeddlerClient {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Pioneers.rl("peddler"), "main");

    private PeddlerClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PeddlerTypes.PEDDLER.get(), PeddlerRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, PeddlerModel::createBodyLayer);
    }
}
