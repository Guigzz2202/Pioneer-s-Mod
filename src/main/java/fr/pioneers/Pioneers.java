package fr.pioneers;

import com.mojang.logging.LogUtils;
import fr.pioneers.core.common.PiClientConfig;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.common.look.LookCardRegistry;
import fr.pioneers.core.compat.mekanism.MekGases;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.PiCore;
import fr.pioneers.core.fluid.BucketCauldrons;
import fr.pioneers.core.machine.MachineBounds;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.core.data.PiDataTypes;
import fr.pioneers.tech.energy.PiEnergy;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.leisure.PiLeisure;
import fr.pioneers.tech.kinetics.ConfigCondition;
import fr.pioneers.tech.kinetics.KineticsConfig;
import fr.pioneers.tech.kinetics.kitchen.PiKitchenMachines;
import fr.pioneers.tech.materials.MetalArmor;
import fr.pioneers.tech.materials.PiMaterials;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.equipment.music.PiMusic;
import fr.pioneers.world.nature.PiNature;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.pneumatic.network.PiPneumaticNetwork;
import fr.pioneers.logistics.pneumatic.tools.PiPneumaticTools;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiBaseMachines;
import fr.pioneers.core.registries.PiBasicItems;
import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiBuildingBlocks;
import fr.pioneers.core.registries.PiChemistry;
import fr.pioneers.core.registries.PiCompute;
import fr.pioneers.core.registries.PiComputing;
import fr.pioneers.core.registries.PiCosmicBlocks;
import fr.pioneers.core.registries.PiCreativeTabs;
import fr.pioneers.core.registries.PiElectricGear;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiExoBlocks;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.core.registries.PiGear;
import fr.pioneers.core.registries.PiGiantBlocks;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.core.registries.PiMachineKit;
import fr.pioneers.core.registries.PiMachines;
import fr.pioneers.core.registries.PiMenus;
import fr.pioneers.core.registries.PiNaval;
import fr.pioneers.core.registries.PiNuclear;
import fr.pioneers.core.registries.PiParticles;
import fr.pioneers.core.registries.PiRail;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.core.registries.PiScienceMachines;
import fr.pioneers.core.registries.PiSounds;
import fr.pioneers.core.registries.PiSpaceMachines;
import fr.pioneers.core.registries.PiSpaceSim;
import fr.pioneers.core.registries.PiStabilizer;
import fr.pioneers.core.registries.PiStoneVariants;
import fr.pioneers.core.registries.PiTileEntityTypes;
import fr.pioneers.core.registries.PiTransport;
import fr.pioneers.core.registries.PiTriggers;
import fr.pioneers.core.registries.PiWorksMachines;
import fr.pioneers.core.registries.PiWorldBlocks;
import fr.pioneers.space.ChunkLoaderTickets;
import fr.pioneers.space.PiFeatures;
import fr.pioneers.space.environment.SurvivalHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * Point d'entree du mod Pioneers.
 */
@Mod(Pioneers.MODID)
public class Pioneers {

    public static final String MODID = "pioneers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Pioneers() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        PiCore.register(modEventBus);
        LookCardRegistry.init();             //1.4 : fiches au regard calculees par le serveur
        PiDataTypes.DATA_TYPES.register(modEventBus);
        if (MekanismCompat.LOADED) {
            MekGases.register(modEventBus);
        }
        PiBuildingBlocks.init();
        PiStoneVariants.init();
        PiExoBlocks.init();
        PiWorldBlocks.init();
        PiMachines.init();
        PiGiantBlocks.init();
        PiRobotics.init();
        PiTransport.init();
        PiMachineKit.init();
        PiSpaceMachines.init();
        PiScienceMachines.init();
        PiWorksMachines.init();
        PiBaseMachines.init();
        PiStabilizer.init();
        PiSpaceSim.init();
        MachineBounds.init();
        PiCosmicBlocks.init();
        PiFluids.init();
        PiBasicItems.init();
        PiMaterials.init();
        MetalArmor.init();
        PiVanillaMaterials.init();
        PiKinetics.init();
        PiMechanics.init();
        PiPneumaticTools.init();
        PiNature.init();
        PiFarm.init();
        PiKitchen.init(modEventBus);
        PiKitchenMachines.init();        //1.6 : postes mecaniques de la cuisine (presse, baratte, hachoir...)
        PiFishing.init();
        fr.pioneers.farm.animal.breed.PiBreeds.init();
        PiFarmDevices.init();
        fr.pioneers.building.home.PiHome.init();          //1.0.3-beta (lot 7) : sacs, meubles par bois, etageres par bois, crochets a corde
        fr.pioneers.logistics.warehouse.PiWarehouse.init();    //1.0.4-beta (lot 1) : caisses d'entrepot, poulie d'entrepot, comptoir
        fr.pioneers.logistics.transport.trash.PiTrash.init();  //1.0.5-beta (lot 22) : la poubelle (toutes les ressources sauf la donnee)
        fr.pioneers.building.lighting.PiLighting.init();      //1.0.4-beta (lot 5) : luminaires et interrupteurs d'eclairage
        fr.pioneers.farm.storage.PiCropStorage.init();   //1.0.4-beta (lot 6) : caisses, cagettes et sacs de recoltes
        fr.pioneers.farm.hemp.PiHemp.init();             //1.0.4-beta (lot 6) : tetes de chanvre, joint
        fr.pioneers.farm.cotton.PiCotton.init();         //1.0.4-beta (lot 28) : fil de coton
        fr.pioneers.building.home.beam.PiBeams.init();            //1.0.4-beta (lot 28) : poutres decoratives
        fr.pioneers.farm.aquarium.PiAquarium.init();     //1.0.4-beta (lot 28) : aquarium, seau de crustace
        fr.pioneers.core.block.PiCoverFrame.init();      //1.0.4-beta (lot 30) : cadre de cache des cables et des arbres
        fr.pioneers.farm.herd.HerdBreeds.init();         //1.0.4-beta (lot 28) : une entite par race (lapins, chevaux, anes, lamas)
        fr.pioneers.farm.herd.PiHerd.init();             //1.0.4-beta (lot 9) : lapins et chevaux (races), laine angora
        fr.pioneers.logistics.caravan.PiCaravan.init();            //1.0.4-beta (lot 10) : caravanes (bat, relais, feuille de route, dromadaire)
        fr.pioneers.world.season.PiSeasons.init();             //1.0.5-beta (lot 1) : calendrier (bloc mural et objet)
        fr.pioneers.farm.greenhouse.PiGreenhouse.init(); //1.0.5-beta (lot 3) : serre multibloc (vitres, cadres, trappes, regulateur)
        fr.pioneers.world.pet.PiPets.init();                   //1.0.5-beta (lot 5) : chiens et chats de race, niches, paniers
        fr.pioneers.world.wild.PiWild.init();                  //1.0.5-beta (lot 6) : gibier des forets et des plaines
        fr.pioneers.world.wild.PiWild7.init();                 //1.0.5-beta (lot 7) : predateurs et petite faune, piege a rat, nichoir a oiseaux
        fr.pioneers.world.wild.PiWild8.init();                 //1.0.5-beta (lot 8) : savane et jungle (girafe, lion, rhinoceros, elephant, singe, tigre)
        fr.pioneers.world.wild.PiWild9.init();                 //1.0.5-beta (lot 9) : mer et cotes (baleines, requins, raie manta, phoque, otarie, mouette, manchots)
        fr.pioneers.world.hunt.PiHunt.init();                  //1.0.5-beta (lot 10) : chasse (troupeaux, competence, armes, harpon, porte-fusil, trophees)
        fr.pioneers.tech.industry.construction.PiConstruction.init();   //1.0.4-beta (lot 21) : foyers, bobines de chauffe, salle blanche
        PiCrustaceans.init();            //1.4 : crustaces, filet, casier
        PiLeisure.init();                //1.5 : pipes, cigare, cendrier, instruments
        PiMusic.init();                  //1.6 : musiques de la Terre et disques Pioneer's
        PiEnergy.init();
        PiPneumaticNetwork.init();
        PiBronze.init();
        PiForge.init();
        PiIndustry.init();
        PiNuclear.init();
        PiChemistry.init();
        PiComputing.init();
        PiCompute.init();
        PiRail.init();
        PiNaval.init();
        PiGear.init();
        PiElectricGear.init();               //1.0.3-beta (lot 9) : set Voltaique, jetpack a hydrogene, lance-flammes
        fr.pioneers.equipment.cosmetic.PiCosmetics.init();      //1.12.0 (lot E4) : les 80 pieces cosmetiques
        fr.pioneers.core.profile.RpBootstrap.init(modEventBus); //1.9 : MAJ RP (pionnier, ruines, economie, villages, clans)
        PiTriggers.init();
        PiBlocks.BLOCKS.getAllBlocks(); //Charge les blocs (registres du socle)
        PiFluids.FLUID_TYPES.register(modEventBus);
        PiFluids.FLUIDS.register(modEventBus);
        PiFluids.BLOCKS.register(modEventBus);
        PiItems.ITEMS.register(modEventBus);
        PiFeatures.FEATURES.register(modEventBus);
        PiEntityTypes.ENTITY_TYPES.register(modEventBus);
        PiSounds.SOUNDS.register(modEventBus);
        PiParticles.PARTICLES.register(modEventBus);
        PiMenus.MENUS.register(modEventBus);
        PiTileEntityTypes.VANILLA_TILES.register(modEventBus);
        PiCreativeTabs.TABS.register(modEventBus);
        PiMechanics.register(modEventBus);
        PiRecipes.RECIPE_TYPES.register(modEventBus);
        PiRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        PiNetwork.register();
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
                CraftingHelper.register(ConfigCondition.Serializer.INSTANCE);
            }
        });
        SurvivalHandler.install();
        modEventBus.addListener((FMLCommonSetupEvent event) ->
              event.enqueueWork(() -> {
                  ChunkLoaderTickets.register();
                  PiTriggers.register();
                  PiNature.setup();
                  PiFarm.setup();
                  BucketCauldrons.register();
              }));
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PiClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PiCommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KineticsConfig.SPEC, "pioneers-mechanics.toml");
        LOGGER.info("Pioneer's charge.");
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
