package fr.pioneers.client.farm.aquarium;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.aquarium.PiAquarium;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 1.0.5-beta (lot 25) : ecran de composition de l'aquarium (menu {@code PiAquarium.MENU}). Nom court unique (piege de l'EventBus). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AquariumClient {

    private AquariumClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(PiAquarium.MENU.get(), AquariumScreen::new));
    }
}
