package fr.pioneers.society.paperwork.client;

import fr.pioneers.Pioneers;
import fr.pioneers.society.paperwork.PiPaperwork;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 1.0.5-beta (lot 16) : ecran des bacs a lettres et d'archives (menu {@code PiPaperwork.MENU}). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PaperworkClient {

    private PaperworkClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(PiPaperwork.MENU.get(), PaperBinScreen::new));
    }
}
