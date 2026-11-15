package fr.pioneers.village;

import fr.pioneers.Pioneers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 3) : chargement des villages (registres, donnees, regles). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class VillageBootstrap {

    private VillageBootstrap() {
    }

    public static void init(IEventBus modBus) {
        VillageBlocks.init();
        VillageItems.init();
        Villagers.init();
        Villages.init();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reload(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(VillageCatalog.VillageListener.INSTANCE);
        event.addListener(VillageCatalog.DialogueListener.INSTANCE);
        event.addListener(VillageCatalog.DictonListener.INSTANCE);
    }
}
