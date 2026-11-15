package fr.pioneers.client.home;

import fr.pioneers.Pioneers;
import fr.pioneers.home.PiHome;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Patch 1.0.3-beta, lot 7 : client de la maison : ecran des sacs, siege invisible, rendu de la corde a linge, sac a dos sur le dos ; 1.0.3-beta (lot 11b) : tas du panier. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HomeClient {

    private HomeClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PiHome.BAG_MENU.get(), BagScreen::new);
            //1.0.5-beta (lot 31) : sacs de rangement
            MenuScreens.register(PiHome.STORAGE_BAG_MENU.get(), StorageBagScreen::new);
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PiHome.SEAT.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(PiHome.HOOK_TILE.get(), HookRenderer::new);
        event.registerBlockEntityRenderer(PiHome.BASKET_TILE.get(), BasketRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackpackLayer(renderer));
            }
        }
    }
}
