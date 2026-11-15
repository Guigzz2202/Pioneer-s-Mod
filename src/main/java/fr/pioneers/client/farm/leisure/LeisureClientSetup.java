package fr.pioneers.client.farm.leisure;

import fr.pioneers.Pioneers;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 1.6 : enregistrements client des instruments (bus du mod) : couche du joueur et modeles en plus du tambour. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LeisureClientSetup {

    private LeisureClientSetup() {
    }

    /** Poses des bras des instruments : a creer avant tout rendu (voir LeisureClient.initPoses). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LeisureClient::initPoses);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new InstrumentLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(LeisureClient.DRUM_WORN);
        event.register(LeisureClient.DRUM_STICK);
    }
}
