package fr.pioneers.client.clan.war;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.war.WarBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 4) : guerre cote client : rendu de la banniere du Drapeau de siege. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WarClient {

    private WarClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WarBlocks.SIEGE_FLAG_TILE.get(), SiegeFlagRenderer::new);
    }
}
