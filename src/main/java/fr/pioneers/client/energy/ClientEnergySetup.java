package fr.pioneers.client.energy;

import fr.pioneers.Pioneers;
import fr.pioneers.energy.PiEnergy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 0.20.45 : enregistrements client des generateurs renouvelables. 1.9.2 : le rotor de l'eolienne est dessine par son propre bloc. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEnergySetup {

    private ClientEnergySetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiEnergy.ROTOR_TILE.get(), WindRotorRenderer::new);
    }
}
