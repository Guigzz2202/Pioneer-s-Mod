package fr.pioneers.economy;

import fr.pioneers.Pioneers;
import fr.pioneers.economy.peddler.PeddlerTypes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 3) : chargement de l'economie (registres, donnees, regles). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class EconomyBootstrap {

    private EconomyBootstrap() {
    }

    public static void init(IEventBus modBus) {
        EconomyBlocks.init();
        PeddlerTypes.init(modBus);
        fr.pioneers.economy.peddler.PeddlerService.init();
        EconomyHooks.init();
        Trade.init();
        fr.pioneers.economy.stall.StallService.init();
        //1.0.4-beta (lot 7) : contrats entre joueurs au taux horaire, facture
        fr.pioneers.economy.contract.ContractService.init();
        //1.0.5-beta (lot 26) : documents du Carnet (onglet Documents : factures, contrats, lettres...) ; porte le registre des contrats
        fr.pioneers.economy.document.DocumentService.init();
        //1.0.4-beta (lot 29) : boite aux lettres et lettres (virements, contrats)
        fr.pioneers.economy.mail.PiMail.init();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reload(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(Values.INSTANCE);
        event.addListener(fr.pioneers.reputation.PeopleApi.INSTANCE);
        event.addListener(fr.pioneers.reputation.ReputationApi.INSTANCE);
    }
}
