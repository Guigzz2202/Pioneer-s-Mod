package fr.pioneers.core.profile;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 1.9 (MAJ RP) : charge les registres et les traitements reseau des quatre blocs de la MAJ, depuis le constructeur du mod (avant
 * l'enregistrement des registres differes). Chaque bloc ajoute ici son {@code init()}.
 */
public final class RpBootstrap {

    private RpBootstrap() {
    }

    public static void init(IEventBus modBus) {
        RpSounds.init();
        RpItems.init();
        ProfileEvents.init();
        KnownPlayers.init(); //1.0.5-beta (lot 16) : selecteur de joueur deja vu
        Reconversion.init();
        SkillHooks.init();
        Diagnosis.init();
        WaypointTeleport.init();                       //1.0.4-beta (lot 29) : teleportation des administrateurs aux waypoints
        fr.pioneers.guide.party.PartyService.init();   //1.0.4-beta (lot 29) : mode partie du guide
        fr.pioneers.ruins.RuinsBootstrap.init(modBus);
        fr.pioneers.economy.EconomyBootstrap.init(modBus);
        fr.pioneers.village.VillageBootstrap.init(modBus);
        fr.pioneers.clan.ClanBootstrap.init(modBus);
    }
}
