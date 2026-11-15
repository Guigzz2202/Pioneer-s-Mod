package fr.pioneers.core;

import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.registry.PiCoreRegistries;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Socle de Pioneer's, independant de Mekanism : gaz (registre et capacite), energie en FE, donnees, et primitives communes (transfert
 * simule ou reel, origine d'un transfert, faces relatives). Objets et fluides passent par les capacites de Forge.
 */
public final class PiCore {

    private PiCore() {
    }

    public static void register(IEventBus modEventBus) {
        PiGasTypes.GASES.register(modEventBus);
        PiCoreRegistries.register(modEventBus);
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.register(IGasHandler.class);
            event.register(IDataHandler.class);
            event.register(IHeatHandler.class);
            event.register(IPressureHandler.class);
        });
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(MekanismCompat::init));
    }
}
