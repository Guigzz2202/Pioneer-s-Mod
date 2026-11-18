package fr.pioneers.logistics.transport.client;

import fr.pioneers.Pioneers;
import fr.pioneers.logistics.transport.trash.PiTrash;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 1.0.5-beta (lot 22) : ecran de la poubelle (menu {@code PiTrash.MENU}). Nom court unique (classes d'abonnes : CLAUDE.md, piege de l'EventBus). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TrashClient {

    private TrashClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(PiTrash.MENU.get(), TrashCanScreen::new));
    }
}
