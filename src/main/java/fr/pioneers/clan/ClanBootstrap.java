package fr.pioneers.clan;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.core.ClanDefs;
import fr.pioneers.clan.core.ClanRoles;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 4) : chargement des clans (index des claims reconstruit au demarrage, vide a l'arret). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanBootstrap {

    private ClanBootstrap() {
    }

    public static void init(IEventBus modBus) {
        //fondations (bloc 4, lots 0 a 2) : Charte, actions reseau, effets des votes
        fr.pioneers.clan.core.ClanItems.init();
        fr.pioneers.clan.core.ClanNetwork.init();
        fr.pioneers.clan.vote.VoteEffects.init();
        //1.9 (MAJ RP, bloc 4) : territoire, protection, chunkloading, Sceaux de fonction et niveaux (lots 3 et 4)
        fr.pioneers.clan.territory.TerritoryBootstrap.init(modBus);
        //1.9 (MAJ RP, bloc 4) : diplomatie et guerre, reputation de clan, statistiques (lots 5 a 7)
        fr.pioneers.clan.war.WarBootstrap.init(modBus);
        //1.9 (MAJ RP, bloc 4) : onglet Clan du Carnet (fiches, carte strategique, annuaires ; crochet de la Mairie)
        fr.pioneers.clan.ui.ClanUi.init();
        //1.9 (MAJ RP, bloc 4) : factions et peuples de joueurs (lots 8 et 9) : Pacte federal, gouvernement federal, secession, ralliement
        fr.pioneers.clan.faction.FactionBootstrap.init();
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ClaimIndex.rebuild(event.getServer());
        //1.0.4-beta (lot 8) : roles proposes ajoutes aux clans d'avant (Arpenteur, Recruteur), une fois ; aucun droit retire
        ClanDefs defs = ClanDefs.get();
        if (defs != null && !defs.roles.isEmpty()) {
            boolean changed = false;
            for (Clan c : ClanData.get(event.getServer()).clans()) {
                changed |= ClanRoles.migrate(c, defs);
            }
            if (changed) {
                ClanData.get(event.getServer()).changed();
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ClaimIndex.clear();
    }
}
