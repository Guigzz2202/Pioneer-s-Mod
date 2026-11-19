package fr.pioneers.core.network;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.animal.PacketAnimal;
import fr.pioneers.farm.leisure.PacketNote;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du mod (communication interface client -> serveur).
 */
public final class PiNetwork {

    private PiNetwork() {
    }

    private static final String VERSION = "23"; //1.0.7-beta : megastructures (PacketMegaState, PacketMegaDeliver ; une seule hausse pour toute la 1.0.7) ; //1.0.6-beta : menus des machines revus (jauges de ressources, moteur electrique, dynamo, chunkloads achetes ; une seule hausse pour toute la 1.0.6) ; //1.0.5-beta (lot 1) : calendrier des saisons (PacketSeason ; une seule hausse pour toute la 1.0.5) ; //1.0.4-beta (lot 1) : comptoir d entrepot (PacketWarehouse : demandes, liste de l entrepot ; une seule hausse pour toute la 1.0.4) ; //1.0.3-beta (lot 2) : gisements envoyes aux clients (PacketDeposits ; une seule hausse pour toute la 1.0.3) ; //Prochaine version (API Machine / Multibloc, lots 0 a 8c) : valeurs d interface NOMMEES, menus des machines a la geometrie v2, unites portees (une seule hausse pour toute la MAJ) ; //1.12.3 (lot F3) : piles de plus de 127 objets des buffers (PacketBigSlots) ; //1.12.1 (lot F1) : environnement de la minimap (PacketEnvironment) ; //1.12.0 (lot E4) : garde-robe cosmetique (CosmeticNet) ; //1.11 (D2b) : diagramme du cable logistique (PacketLogistic) ; //MAJ Recettes : verrou de recette (PacketRecipeLock) ; //1.9 : MAJ RP ; //1.6 : lots de la ferme (avant, 10 : notes des instruments ; 9 : fiches au regard ; 8 : elevage et peche ; 7 : chantiers en morceaux, synchro cinetique, ordinateurs)

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Pioneers.rl("main"),
          () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(PacketAssembler.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketAssembler::encode)
              .decoder(PacketAssembler::decode)
              .consumerMainThread(PacketAssembler::handle)
              .add();
        CHANNEL.messageBuilder(PacketSelectDestination.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketSelectDestination::encode)
              .decoder(PacketSelectDestination::decode)
              .consumerMainThread(PacketSelectDestination::handle)
              .add();
        CHANNEL.messageBuilder(PacketRocketLaunch.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRocketLaunch::encode)
              .decoder(PacketRocketLaunch::decode)
              .consumerMainThread(PacketRocketLaunch::handle)
              .add();
        CHANNEL.messageBuilder(PacketSetStationName.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketSetStationName::encode)
              .decoder(PacketSetStationName::decode)
              .consumerMainThread(PacketSetStationName::handle)
              .add();
        CHANNEL.messageBuilder(PacketCargoMap.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketCargoMap.Request::encode)
              .decoder(PacketCargoMap.Request::decode)
              .consumerMainThread(PacketCargoMap.Request::handle)
              .add();
        CHANNEL.messageBuilder(PacketCargoMap.Data.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketCargoMap.Data::encode)
              .decoder(PacketCargoMap.Data::decode)
              .consumerMainThread(PacketCargoMap.Data::handle)
              .add();
        CHANNEL.messageBuilder(PacketResearch.Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketResearch.Sync::encode)
              .decoder(PacketResearch.Sync::decode)
              .consumerMainThread(PacketResearch.Sync::handle)
              .add();
        CHANNEL.messageBuilder(PacketResearch.Completed.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketResearch.Completed::encode)
              .decoder(PacketResearch.Completed::decode)
              .consumerMainThread(PacketResearch.Completed::handle)
              .add();
        CHANNEL.messageBuilder(PacketResearch.Select.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketResearch.Select::encode)
              .decoder(PacketResearch.Select::decode)
              .consumerMainThread(PacketResearch.Select::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoverName.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRoverName::encode)
              .decoder(PacketRoverName::decode)
              .consumerMainThread(PacketRoverName::handle)
              .add();
        CHANNEL.messageBuilder(PacketSurvivalStatus.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketSurvivalStatus::encode)
              .decoder(PacketSurvivalStatus::decode)
              .consumerMainThread(PacketSurvivalStatus::handle)
              .add();
        CHANNEL.messageBuilder(PacketTileAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketTileAction::encode)
              .decoder(PacketTileAction::decode)
              .consumerMainThread(PacketTileAction::handle)
              .add();
        registerLate(id);
    }

    /** Paquets de la 0.10 (enregistres a la suite pour garder les numeros precedents). */
    private static void registerLate(int id) {
        CHANNEL.messageBuilder(PacketCapsuleInput.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketCapsuleInput::encode)
              .decoder(PacketCapsuleInput::decode)
              .consumerMainThread(PacketCapsuleInput::handle)
              .add();
        CHANNEL.messageBuilder(PacketSpaceEvent.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketSpaceEvent::encode)
              .decoder(PacketSpaceEvent::decode)
              .consumerMainThread(PacketSpaceEvent::handle)
              .add();
        CHANNEL.messageBuilder(PacketAbortMission.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketAbortMission::encode)
              .decoder(PacketAbortMission::decode)
              .consumerMainThread(PacketAbortMission::handle)
              .add();
        //0.11 : robotique
        CHANNEL.messageBuilder(PacketRobotics.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRobotics.Request::encode)
              .decoder(PacketRobotics.Request::decode)
              .consumerMainThread(PacketRobotics.Request::handle)
              .add();
        CHANNEL.messageBuilder(PacketRobotics.Sync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketRobotics.Sync::encode)
              .decoder(PacketRobotics.Sync::decode)
              .consumerMainThread(PacketRobotics.Sync::handle)
              .add();
        CHANNEL.messageBuilder(PacketRobotics.Edit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRobotics.Edit::encode)
              .decoder(PacketRobotics.Edit::decode)
              .consumerMainThread(PacketRobotics.Edit::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.Watch.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRoboticsMap.Watch::encode)
              .decoder(PacketRoboticsMap.Watch::decode)
              .consumerMainThread(PacketRoboticsMap.Watch::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.State.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketRoboticsMap.State::encode)
              .decoder(PacketRoboticsMap.State::decode)
              .consumerMainThread(PacketRoboticsMap.State::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.TileRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRoboticsMap.TileRequest::encode)
              .decoder(PacketRoboticsMap.TileRequest::decode)
              .consumerMainThread(PacketRoboticsMap.TileRequest::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.Tiles.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketRoboticsMap.Tiles::encode)
              .decoder(PacketRoboticsMap.Tiles::decode)
              .consumerMainThread(PacketRoboticsMap.Tiles::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.PointEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRoboticsMap.PointEdit::encode)
              .decoder(PacketRoboticsMap.PointEdit::decode)
              .consumerMainThread(PacketRoboticsMap.PointEdit::handle)
              .add();
        CHANNEL.messageBuilder(PacketRoboticsMap.HubAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRoboticsMap.HubAction::encode)
              .decoder(PacketRoboticsMap.HubAction::decode)
              .consumerMainThread(PacketRoboticsMap.HubAction::handle)
              .add();
        CHANNEL.messageBuilder(PacketRobotUnit.Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketRobotUnit.Info::encode)
              .decoder(PacketRobotUnit.Info::decode)
              .consumerMainThread(PacketRobotUnit.Info::handle)
              .add();
        CHANNEL.messageBuilder(PacketRobotUnit.Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRobotUnit.Action::encode)
              .decoder(PacketRobotUnit.Action::decode)
              .consumerMainThread(PacketRobotUnit.Action::handle)
              .add();
        CHANNEL.messageBuilder(PacketWorks.Submit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWorks.Submit::encode)
              .decoder(PacketWorks.Submit::decode)
              .consumerMainThread(PacketWorks.Submit::handle)
              .add();
        CHANNEL.messageBuilder(PacketWorks.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWorks.Request::encode)
              .decoder(PacketWorks.Request::decode)
              .consumerMainThread(PacketWorks.Request::handle)
              .add();
        CHANNEL.messageBuilder(PacketWorks.Data.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketWorks.Data::encode)
              .decoder(PacketWorks.Data::decode)
              .consumerMainThread(PacketWorks.Data::handle)
              .add();
        CHANNEL.messageBuilder(PacketTerminal.Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketTerminal.Action::encode)
              .decoder(PacketTerminal.Action::decode)
              .consumerMainThread(PacketTerminal.Action::handle)
              .add();
        CHANNEL.messageBuilder(PacketTerminal.Data.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketTerminal.Data::encode)
              .decoder(PacketTerminal.Data::decode)
              .consumerMainThread(PacketTerminal.Data::handle)
              .add();
        //0.17.1 : terminal des ordinateurs
        CHANNEL.messageBuilder(PacketComputer.Action.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketComputer.Action::encode)
              .decoder(PacketComputer.Action::decode)
              .consumerMainThread(PacketComputer.Action::handle)
              .add();
        CHANNEL.messageBuilder(PacketComputer.Content.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketComputer.Content::encode)
              .decoder(PacketComputer.Content::decode)
              .consumerMainThread(PacketComputer.Content::handle)
              .add();
        //0.20.0 : chemin de fer
        CHANNEL.messageBuilder(PacketRail.Info.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketRail.Info::encode)
              .decoder(PacketRail.Info::decode)
              .consumerMainThread(PacketRail.Info::handle)
              .add();
        CHANNEL.messageBuilder(PacketRail.TrainEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRail.TrainEdit::encode)
              .decoder(PacketRail.TrainEdit::decode)
              .consumerMainThread(PacketRail.TrainEdit::handle)
              .add();
        CHANNEL.messageBuilder(PacketRail.StationEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRail.StationEdit::encode)
              .decoder(PacketRail.StationEdit::decode)
              .consumerMainThread(PacketRail.StationEdit::handle)
              .add();
        //0.20.15 : equipement modulaire
        CHANNEL.messageBuilder(PacketGear.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketGear::encode)
              .decoder(PacketGear::decode)
              .consumerMainThread(PacketGear::handle)
              .add();
        //0.20.46 (audit) : console et fichiers d'un ordinateur, au seul joueur qui a l'interface ouverte
        CHANNEL.messageBuilder(PacketComputer.State.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketComputer.State::encode)
              .decoder(PacketComputer.State::decode)
              .consumerMainThread(PacketComputer.State::handle)
              .add();
        //0.20.46 (audit) : rotation d'un reseau de force rotative, un paquet par joueur (avant : un paquet de bloc par piece)
        CHANNEL.messageBuilder(PacketKineticSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketKineticSync::encode)
              .decoder(PacketKineticSync::decode)
              .consumerMainThread(PacketKineticSync::handle)
              .add();
        //1.3.0 : elevage (race, sexe, besoins)
        CHANNEL.messageBuilder(PacketAnimal.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketAnimal::encode)
              .decoder(PacketAnimal::decode)
              .consumerMainThread(PacketAnimal::handle)
              .add();
        //1.4 : fiches au regard calculees par le serveur
        CHANNEL.messageBuilder(PacketLookCard.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketLookCard.Request::encode)
              .decoder(PacketLookCard.Request::decode)
              .consumerMainThread(PacketLookCard.Request::handle)
              .add();
        CHANNEL.messageBuilder(PacketLookCard.Reply.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketLookCard.Reply::encode)
              .decoder(PacketLookCard.Reply::decode)
              .consumerMainThread(PacketLookCard.Reply::handle)
              .add();
        //1.5 : notes des instruments de musique
        CHANNEL.messageBuilder(PacketNote.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketNote::encode)
              .decoder(PacketNote::decode)
              .consumerMainThread(PacketNote::handle)
              .add();
        //1.6 : animation des musiciens chez les autres joueurs
        CHANNEL.messageBuilder(PacketNote.Fx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketNote.Fx::encode)
              .decoder(PacketNote.Fx::decode)
              .consumerMainThread(PacketNote.Fx::handle)
              .add();
        //MAJ Recettes (lot 7) : verrou de recette (case « Recette », JEI, terminal logistique)
        CHANNEL.messageBuilder(PacketRecipeLock.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketRecipeLock::encode)
              .decoder(PacketRecipeLock::decode)
              .consumerMainThread(PacketRecipeLock::handle)
              .add();
        //1.11 (D2b) : diagramme du cable logistique
        CHANNEL.messageBuilder(PacketLogistic.Snapshot.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketLogistic.Snapshot::encode)
              .decoder(PacketLogistic.Snapshot::decode)
              .consumerMainThread(PacketLogistic.Snapshot::handle)
              .add();
        CHANNEL.messageBuilder(PacketLogistic.Stats.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketLogistic.Stats::encode)
              .decoder(PacketLogistic.Stats::decode)
              .consumerMainThread(PacketLogistic.Stats::handle)
              .add();
        CHANNEL.messageBuilder(PacketLogistic.Edit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketLogistic.Edit::encode)
              .decoder(PacketLogistic.Edit::decode)
              .consumerMainThread(PacketLogistic.Edit::handle)
              .add();
        CHANNEL.messageBuilder(PacketLogistic.Close.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketLogistic.Close::encode)
              .decoder(PacketLogistic.Close::decode)
              .consumerMainThread(PacketLogistic.Close::handle)
              .add();
        //1.9 : MAJ RP (deux paquets generiques a actions : profil, ruines, economie, villages, clans)
        id = fr.pioneers.core.profile.RpNet.register(CHANNEL, id);
        //1.12.0 (lot E4) : garde-robe cosmetique (synchronisation, gestes du panneau de l'inventaire)
        CHANNEL.messageBuilder(PacketEnvironment.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketEnvironment::encode)
              .decoder(PacketEnvironment::decode)
              .consumerMainThread(PacketEnvironment::handle)
              .add();
        id = fr.pioneers.equipment.cosmetic.CosmeticNet.register(CHANNEL, id);
        //1.12.3 (lot F3) : piles de plus de 127 objets dans les cases d'un menu (buffers d'objets : plusieurs piles par case)
        CHANNEL.messageBuilder(PacketBigSlots.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketBigSlots::encode)
              .decoder(PacketBigSlots::decode)
              .consumerMainThread(PacketBigSlots::handle)
              .add();
        //1.0.2-beta : reglage a valeur d'une interface standard (curseurs de la boite de vitesses et du regulateur a boules) ; ajoute EN FIN
        //(les numeros des paquets d'avant ne bougent pas ; PiNetwork.VERSION deja monte pour cette version)
        CHANNEL.messageBuilder(PacketMachineSetting.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketMachineSetting::encode)
              .decoder(PacketMachineSetting::decode)
              .consumerMainThread(PacketMachineSetting::handle)
              .add();
        //1.0.3-beta (lot 2) : types de gisements solides et fluides du serveur vers le client (categories JEI « Gisements » vides sur serveur
        //dedie) ; ajoute EN FIN
        CHANNEL.messageBuilder(PacketDeposits.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketDeposits::encode)
              .decoder(PacketDeposits::decode)
              .consumerMainThread(PacketDeposits::handle)
              .add();
        //1.0.3-beta (lot 7) : clic droit main vide en l'air sur une corde a linge (reprendre une piece) ; ajoute EN FIN, VERSION inchangee
        CHANNEL.messageBuilder(PacketClothesline.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketClothesline::encode)
              .decoder(PacketClothesline::decode)
              .consumerMainThread(PacketClothesline::handle)
              .add();
        //1.0.4-beta (lot 1) : comptoir d'entrepot (demande d'objets, liste de l'entrepot) ; ajoutes EN FIN, VERSION 20
        CHANNEL.messageBuilder(PacketWarehouse.Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWarehouse.Request::encode)
              .decoder(PacketWarehouse.Request::decode)
              .consumerMainThread(PacketWarehouse.Request::handle)
              .add();
        CHANNEL.messageBuilder(PacketWarehouse.Contents.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketWarehouse.Contents::encode)
              .decoder(PacketWarehouse.Contents::decode)
              .consumerMainThread(PacketWarehouse.Contents::handle)
              .add();
        //1.0.4-beta (lot 3) : panier du comptoir (lignes, « Commander ») ; ajoute EN FIN, VERSION 20 inchangee
        CHANNEL.messageBuilder(PacketWarehouse.CartEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWarehouse.CartEdit::encode)
              .decoder(PacketWarehouse.CartEdit::decode)
              .consumerMainThread(PacketWarehouse.CartEdit::handle)
              .add();
        //1.0.4-beta (lot 5) : apercu des liaisons de l'eclairage (interrupteur -> lumieres) ; ajoutes EN FIN, VERSION 20 inchangee
        CHANNEL.messageBuilder(PacketLighting.Ask.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketLighting.Ask::encode)
              .decoder(PacketLighting.Ask::decode)
              .consumerMainThread(PacketLighting.Ask::handle)
              .add();
        CHANNEL.messageBuilder(PacketLighting.Links.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketLighting.Links::encode)
              .decoder(PacketLighting.Links::decode)
              .consumerMainThread(PacketLighting.Links::handle)
              .add();
        //1.0.4-beta (lot 9) : race et stats d'un lapin ou d'un cheval (robe, echelle, fiche) ; ajoute EN FIN, VERSION 20 inchangee
        CHANNEL.messageBuilder(fr.pioneers.farm.herd.PacketHerd.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.farm.herd.PacketHerd::encode)
              .decoder(fr.pioneers.farm.herd.PacketHerd::decode)
              .consumerMainThread(fr.pioneers.farm.herd.PacketHerd::handle)
              .add();
        //1.0.4-beta (lot 10) : vue de caravane d'une bete (bat, rabats, fiche) ; feuille de route (etapes) ; ajoutes EN FIN, VERSION 20 inchangee
        CHANNEL.messageBuilder(fr.pioneers.logistics.caravan.PacketCaravan.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.logistics.caravan.PacketCaravan::encode)
              .decoder(fr.pioneers.logistics.caravan.PacketCaravan::decode)
              .consumerMainThread(fr.pioneers.logistics.caravan.PacketCaravan::handle)
              .add();
        CHANNEL.messageBuilder(fr.pioneers.logistics.caravan.PacketCaravanRoute.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.logistics.caravan.PacketCaravanRoute::encode)
              .decoder(fr.pioneers.logistics.caravan.PacketCaravanRoute::decode)
              .consumerMainThread(fr.pioneers.logistics.caravan.PacketCaravanRoute::handle)
              .add();
        //1.0.5-beta (lot 1) : calendrier des saisons (decalage, duree) ; ajoute EN FIN, VERSION 21
        CHANNEL.messageBuilder(fr.pioneers.world.season.PacketSeason.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.world.season.PacketSeason::encode)
              .decoder(fr.pioneers.world.season.PacketSeason::decode)
              .consumerMainThread(fr.pioneers.world.season.PacketSeason::handle)
              .add();
        //1.0.5-beta (lot 5) : interface d'un animal, rappel (touche G) et liste de la roue ; ajoutes EN FIN, VERSION 21 inchangee
        CHANNEL.messageBuilder(fr.pioneers.world.pet.PacketPet.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.world.pet.PacketPet::encode)
              .decoder(fr.pioneers.world.pet.PacketPet::decode)
              .consumerMainThread(fr.pioneers.world.pet.PacketPet::handle)
              .add();
        CHANNEL.messageBuilder(fr.pioneers.world.pet.PacketPetRecall.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.world.pet.PacketPetRecall::encode)
              .decoder(fr.pioneers.world.pet.PacketPetRecall::decode)
              .consumerMainThread(fr.pioneers.world.pet.PacketPetRecall::handle)
              .add();
        CHANNEL.messageBuilder(fr.pioneers.world.pet.PacketPetList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.world.pet.PacketPetList::encode)
              .decoder(fr.pioneers.world.pet.PacketPetList::decode)
              .consumerMainThread(fr.pioneers.world.pet.PacketPetList::handle)
              .add();
        //1.0.5-beta (lot 17) : comptoir d'entrepot : stock par differences, recette de JEI au panier, reglages des stations (destination,
        //regles « garder N ») ; ajoutes EN FIN, VERSION 21 inchangee
        CHANNEL.messageBuilder(PacketWarehouse.Stock.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(PacketWarehouse.Stock::encode)
              .decoder(PacketWarehouse.Stock::decode)
              .consumerMainThread(PacketWarehouse.Stock::handle)
              .add();
        CHANNEL.messageBuilder(PacketWarehouse.Recipe.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWarehouse.Recipe::encode)
              .decoder(PacketWarehouse.Recipe::decode)
              .consumerMainThread(PacketWarehouse.Recipe::handle)
              .add();
        CHANNEL.messageBuilder(PacketWarehouse.DeskEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWarehouse.DeskEdit::encode)
              .decoder(PacketWarehouse.DeskEdit::decode)
              .consumerMainThread(PacketWarehouse.DeskEdit::handle)
              .add();
        //1.0.5-beta (lot 22) : comptoir d'entrepot : nombre de fabrications d'une recette demandee (molette) ; ajoute EN FIN, VERSION 21 inchangee
        CHANNEL.messageBuilder(PacketWarehouse.CraftEdit.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(PacketWarehouse.CraftEdit::encode)
              .decoder(PacketWarehouse.CraftEdit::decode)
              .consumerMainThread(PacketWarehouse.CraftEdit::handle)
              .add();
        //1.0.5-beta (lot 25) : aquarium : pose ou retrait d'un element de la composition du fond ; ajoute EN FIN, VERSION 21 inchangee
        CHANNEL.messageBuilder(fr.pioneers.farm.aquarium.PacketAquarium.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.farm.aquarium.PacketAquarium::encode)
              .decoder(fr.pioneers.farm.aquarium.PacketAquarium::decode)
              .consumerMainThread(fr.pioneers.farm.aquarium.PacketAquarium::handle)
              .add();
        //1.0.5-beta (lot 31) : sacs de rangement : gestes de l'ecran (prendre, deposer, choisir, filtre, ramassage) et vidage dans un conteneur
        //ouvert ; ajoute EN FIN, VERSION 21 inchangee
        CHANNEL.messageBuilder(fr.pioneers.building.home.bag.PacketBag.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.building.home.bag.PacketBag::encode)
              .decoder(fr.pioneers.building.home.bag.PacketBag::decode)
              .consumerMainThread(fr.pioneers.building.home.bag.PacketBag::handle)
              .add();
        //1.0.7 (lot 4) : megastructures : etat pour les clients (connexion, changements au plus une fois par seconde) et livraison au chantier
        CHANNEL.messageBuilder(fr.pioneers.space.mega.PacketMegaState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.space.mega.PacketMegaState::encode)
              .decoder(fr.pioneers.space.mega.PacketMegaState::decode)
              .consumerMainThread(fr.pioneers.space.mega.PacketMegaState::handle)
              .add();
        CHANNEL.messageBuilder(fr.pioneers.space.mega.PacketMegaDeliver.class, id++, NetworkDirection.PLAY_TO_SERVER)
              .encoder(fr.pioneers.space.mega.PacketMegaDeliver::encode)
              .decoder(fr.pioneers.space.mega.PacketMegaDeliver::decode)
              .consumerMainThread(fr.pioneers.space.mega.PacketMegaDeliver::handle)
              .add();
        //1.0.7 (« ciel grandiose ») : megastructures en fonctionnement (animations du ciel) ; ajoute EN FIN, VERSION 23 inchangee
        CHANNEL.messageBuilder(fr.pioneers.space.mega.PacketMegaWorking.class, id++, NetworkDirection.PLAY_TO_CLIENT)
              .encoder(fr.pioneers.space.mega.PacketMegaWorking::encode)
              .decoder(fr.pioneers.space.mega.PacketMegaWorking::decode)
              .consumerMainThread(fr.pioneers.space.mega.PacketMegaWorking::handle)
              .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
