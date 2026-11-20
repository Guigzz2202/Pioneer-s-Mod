package fr.pioneers.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.tech.bronze.client.AccessoryRenderer;
import fr.pioneers.tech.bronze.client.AutoMoldRenderer;
import fr.pioneers.tech.bronze.client.BlastFurnaceRenderer;
import fr.pioneers.tech.bronze.client.CastingChannelRenderer;
import fr.pioneers.tech.bronze.client.CastingTableRenderer;
import fr.pioneers.tech.bronze.client.CrucibleFurnaceRenderer;
import fr.pioneers.tech.bronze.client.DrillRenderer;
import fr.pioneers.farm.client.leisure.LeisureClient;
import fr.pioneers.farm.client.leisure.SmokeRingParticle;
import fr.pioneers.space.giant.client.GasPocketRenderer;
import fr.pioneers.space.giant.client.HarvesterDroneRenderer;
import fr.pioneers.space.giant.client.MeteorRenderer;
import fr.pioneers.client.gui.core.GuiMachine;
import fr.pioneers.guide.client.GuideBook;
import fr.pioneers.tech.kinetics.client.HandSieveRenderer;
import fr.pioneers.tech.kinetics.client.WoodenFluidBufferRenderer;
import fr.pioneers.tech.kinetics.client.KineticRenderer;
import fr.pioneers.tech.kinetics.client.VaneRenderer;
import fr.pioneers.client.model.ConnectedModelLoader;
import fr.pioneers.client.model.ConnectedTexturesModel;
import fr.pioneers.client.model.CoverModel;
import fr.pioneers.client.model.IndustrialBodyModel;
import fr.pioneers.logistics.naval.client.HarborEquipmentRenderer;
import fr.pioneers.logistics.naval.client.ShipRenderer;
import fr.pioneers.tech.nuclear.client.RenderReactorCore;
import fr.pioneers.tech.bronze.client.particle.SteamPlumeParticle;
import fr.pioneers.logistics.rail.client.CarRenderer;
import fr.pioneers.logistics.rail.client.CarScreen;
import fr.pioneers.logistics.rail.client.RailBakedModel;
import fr.pioneers.logistics.rail.client.StationRenderer;
import fr.pioneers.client.render.ChamberEffectRenderer;
import fr.pioneers.client.render.DataPortRenderer;
import fr.pioneers.client.render.RenderAssemblyTable;
import fr.pioneers.client.render.RenderBuffer;
import fr.pioneers.client.render.RenderDataBank;
import fr.pioneers.client.render.RenderDataPorts;
import fr.pioneers.client.render.RenderForceField;
import fr.pioneers.client.render.RenderSolarAbsorber;
import fr.pioneers.client.render.RenderSpaceSimulator;
import fr.pioneers.client.render.RenderStabilizerCore;
import fr.pioneers.logistics.robotics.client.GuiRoboticHub;
import fr.pioneers.logistics.robotics.client.GuiRoboticRelay;
import fr.pioneers.logistics.robotics.client.RenderLogisticMarker;
import fr.pioneers.logistics.robotics.client.RenderRoboticBay;
import fr.pioneers.logistics.robotics.client.RenderRoboticHub;
import fr.pioneers.logistics.robotics.client.RobotUnitModels;
import fr.pioneers.logistics.robotics.client.RobotUnitRenderer;
import fr.pioneers.space.sky.client.Celestial;
import fr.pioneers.space.client.CapsuleModel;
import fr.pioneers.space.client.CapsuleRenderer;
import fr.pioneers.space.client.CapsuleScreen;
import fr.pioneers.space.client.ExoSkyEffects;
import fr.pioneers.space.client.GeneratedRocketModels;
import fr.pioneers.space.client.LaunchVisualRenderer;
import fr.pioneers.space.client.MarsSkyEffects;
import fr.pioneers.space.client.MoonSkyEffects;
import fr.pioneers.space.client.OrbitSkyEffects;
import fr.pioneers.space.client.ParachuteModel;
import fr.pioneers.space.client.RenderAssemblyHall;
import fr.pioneers.space.client.RenderDataAntenna;
import fr.pioneers.space.client.RenderGreenhouse;
import fr.pioneers.space.client.RenderLaunchPad;
import fr.pioneers.space.client.RenderMiningOutpost;
import fr.pioneers.space.client.RenderResearchTable;
import fr.pioneers.space.client.RocketRenderer;
import fr.pioneers.space.client.RocketScreen;
import fr.pioneers.space.client.VenusSkyEffects;
import fr.pioneers.tech.computing.client.ComputerScreen;
import fr.pioneers.tech.computing.client.ComputingScreen;
import fr.pioneers.tech.computing.client.RenderSupervisionScreen;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.item.GasCapsuleItem;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.hemp.PiHemp;
import fr.pioneers.farm.leisure.Instrument;
import fr.pioneers.farm.leisure.PiLeisure;
import fr.pioneers.farm.leisure.Smoking;
import fr.pioneers.farm.leisure.SmokingPipeItem;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.core.registries.PiBasicItems;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.computing.PiComputing;
import fr.pioneers.space.content.PiCosmicBlocks;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.core.registries.PiFluids;
import fr.pioneers.equipment.gear.PiGear;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.kinetics.PiKinetics;
import fr.pioneers.core.registries.PiMachines;
import fr.pioneers.core.registries.PiMenus;
import fr.pioneers.logistics.naval.PiNaval;
import fr.pioneers.core.registries.PiParticles;
import fr.pioneers.logistics.rail.PiRail;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.tile.PiSpaceMachines;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.content.PiStabilizer;
import fr.pioneers.core.registries.PiStackVisuals;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.space.assembly.PiWorksMachines;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.space.RocketTier;
import java.util.Locale;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Enregistrements uniquement cote client (ecrans, modeles, shaders, rendus).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PiClientRegistration {

    private PiClientRegistration() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerContainers(RegisterEvent event) {
        event.register(Registries.MENU, helper -> {
            MenuScreens.register(PiMenus.CAPSULE.get(), CapsuleScreen::new);
            MenuScreens.register(PiMenus.ROCKET.get(), RocketScreen::new);
            MenuScreens.register(PiRobotics.HUB_MENU.get(), GuiRoboticHub::new);
            MenuScreens.register(PiRobotics.RELAY_MENU.get(), GuiRoboticRelay::new);
            MenuScreens.register(PiTransport.MACHINE_MENU.get(), GuiMachine::new);
            MenuScreens.register(PiComputing.MENU.get(), ComputingScreen::new);
            MenuScreens.register(PiComputing.COMPUTER_MENU.get(), ComputerScreen::new);
            MenuScreens.register(PiRail.CAR_MENU.get(), CarScreen::new);
        });
    }

    /** Fusee : modele et rendu. */
    /** Visuel de pile des items du standard (predicat pioneers:stack_level, voir PiStackVisuals). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        //1.11.2 : infobulles unifiees (core/tooltip/Tooltips) : etat de la touche Maj fourni par le client
        Tooltips.shiftKey(Screen::hasShiftDown);
        event.enqueueWork(() -> PiStackVisuals.items().forEach(item ->
              ItemProperties.register(item.get(), PiStackVisuals.STACK_LEVEL,
                    (stack, level, entity, seed) -> PiStackVisuals.level(stack))));
        //1.5 : pipe allumee (modele avec la braise)
        event.enqueueWork(() -> PiLeisure.PIPES.forEach(pipe -> ItemProperties.register(pipe.get(), Pioneers.rl("lit"),
              (stack, level, entity, seed) -> SmokingPipeItem.lit(stack, level != null ? level.getGameTime() : Smoking.clientNow) ? 1 : 0)));
        //1.6 : pipe et cigare a la bouche pendant qu'on tire (comme le cor de chevre) ; flute devant la bouche pendant qu'on joue
        event.enqueueWork(() -> {
            ItemPropertyFunction smoking = (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0;
            PiLeisure.PIPES.forEach(pipe -> ItemProperties.register(pipe.get(), Pioneers.rl("smoking"), smoking));
            ItemProperties.register(PiLeisure.LIT_CIGAR.get(), Pioneers.rl("smoking"), smoking);
            //1.0.4-beta (lot 6) : joint de chanvre allume, meme geste que le cigare
            ItemProperties.register(PiHemp.LIT_HEMP_JOINT.get(), Pioneers.rl("smoking"), smoking);
            ItemProperties.register(PiLeisure.INSTRUMENTS.get(Instrument.FLUTE).get(), Pioneers.rl("playing"),
                  (stack, level, entity, seed) -> entity != null && LeisureClient.playing(entity, 0) ? 1 : 0);
        });
        event.enqueueWork(() -> ItemProperties.register(PiBasicItems.GAS_CAPSULE.get(),
              Pioneers.rl("filled"),
              (stack, level, entity, seed) -> GasCapsuleItem.getGas(stack) == null ? 0 : 1));
        event.enqueueWork(() -> ItemProperties.register(PiBasicItems.GAS_CAPSULE.get(),
              Pioneers.rl("gas_kind"), (stack, level, entity, seed) -> GasCapsuleItem.gasKind(stack)));
        //1.0 (lot 3) : fluides du mod translucides
        event.enqueueWork(() -> PiFluids.SimpleFluid.ALL.forEach(f -> {
            ItemBlockRenderTypes.setRenderLayer(f.source.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(f.flowing.get(), RenderType.translucent());
        }));
    }

    /** Capsule de gaz : la couche 1 (remplissage) prend la couleur du gaz contenu. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            Gas gas = GasCapsuleItem.getGas(stack);
            //Capsules animees : texture deja en couleur, pas de teinte
            return gas == null || GasCapsuleItem.gasKind(stack) != 0 ? 0xFFFFFFFF : 0xFF000000 | gas.getTint();
        }, PiBasicItems.GAS_CAPSULE.get());
    }

    /** 0.20.3 : remblai des pentes de voie : la teinte est celle du bloc qui porte la voie (herbe, feuilles...). */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(RailBakedModel::tint, RailBakedModel.trackBlocks());
        //1.0.4-beta (lot 27) : teintes de la cache (herbe, feuilles) sur un cable ou un arbre cache
        CoverModel.colors(event);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PiKeys.SOLAR_MAP);
        event.register(PiKeys.PARACHUTE);
        event.register(PiKeys.AUTOPILOT);
        event.register(PiKeys.RESEARCH);
        event.register(PiKeys.ROBOTICS);
        event.register(PiKeys.GEAR);
        event.register(PiKeys.PET_RECALL);   //1.0.5-beta (lot 5) : rappel des animaux
        event.register(PiKeys.JETPACK_MODE);
        event.register(PiKeys.GUIDE);
        event.register(PiKeys.JOURNEY);
        event.register(PiKeys.RECIPE_SQUARE);
        event.register(PiKeys.MINIMAP);
        event.register(PiKeys.WORLD_MAP);
        event.register(PiKeys.WAYPOINT);
        event.register(PiKeys.MINIMAP_ZOOM_IN);
        event.register(PiKeys.MINIMAP_ZOOM_OUT);
        event.register(PiKeys.MINIMAP_SETTINGS);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (int tier = 1; tier <= RocketTier.values().length; tier++) {
            int level = tier;
            event.registerLayerDefinition(GeneratedRocketModels.layer(level),
                  () -> GeneratedRocketModels.create(level));
        }
        event.registerLayerDefinition(ParachuteModel.LAYER, ParachuteModel::createLayer);
        event.registerLayerDefinition(CapsuleModel.LAYER, CapsuleModel::createLayer);
        for (UnitKind kind : UnitKind.values()) {
            event.registerLayerDefinition(RobotUnitModels.layer(kind), () -> RobotUnitModels.create(kind));
        }
        event.registerLayerDefinition(RobotUnitModels.HUB_TOP, RobotUnitModels::hubTopLayer);
        event.registerLayerDefinition(RobotUnitModels.ROVER_DRILL, RobotUnitModels::roverDrillLayer);
    }

    /** Modeles supplementaires de la table d'assemblage (rendus par son BlockEntityRenderer). */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(RenderDataAntenna.DISH);
        event.register(RenderDataAntenna.MAST_TOP);
        event.register(RenderMiningOutpost.DISH);
        //1.0 : quarts de carcasse du corps connecte des ports et controleurs industriels
        IndustrialBodyModel.register(event::register);
        //Prises de donnees, affichees selon la configuration des faces
        DataPortRenderer.register(event::register, "research_desk",
              RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
        DataPortRenderer.register(event::register, "data_antenna",
              RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT,
              RelativeSide.BOTTOM);
        DataPortRenderer.register(event::register, "data_bank",
              RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP,
              RelativeSide.BOTTOM);
        DataPortRenderer.register(event::register, "biome_analyzer",
              RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP,
              RelativeSide.BOTTOM);
        for (String analyzer : new String[]{"asteroid_crusher", "geo_spectrometer", "bio_sequencer"}) {
            DataPortRenderer.register(event::register, analyzer, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BOTTOM);
        }
    }

    /**
     * Ciels : chaque astre sans atmosphere montre ce qu'on y verrait vraiment (voir SkyScene) ; Mars garde son ciel
     * caramel avec Phobos et Deimos ; Venus et Titan restent sous leurs nuages.
     */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Pioneers.rl("mars"), new MarsSkyEffects());
        event.register(Pioneers.rl("venus"), new VenusSkyEffects());
        event.register(Pioneers.rl("titan"), new VenusSkyEffects());
        event.register(Pioneers.rl("orbit"), new OrbitSkyEffects());
        //0.10 : exoplanetes et geantes gazeuses
        event.register(Pioneers.rl("toliman_b"), ExoSkyEffects.tolimanB());
        event.register(Pioneers.rl("proxima_b"), ExoSkyEffects.proximaB());
        event.register(Pioneers.rl("proxima_d"), ExoSkyEffects.proximaD());
        event.register(Pioneers.rl("trappist_1b"), ExoSkyEffects.trappist(88, false, "trappist_1c", 0.9, 120, 40, "trappist_1d", 0.45, 300, 55));
        event.register(Pioneers.rl("trappist_1d"), ExoSkyEffects.trappist(52, true, "trappist_1c", 0.7, 110, 35, "trappist_1e", 0.95, 300, 48,
              "trappist_1f", 0.45, 20, 62));
        event.register(Pioneers.rl("trappist_1e"), ExoSkyEffects.trappist(36, true, "trappist_1d", 0.85, 115, 38, "trappist_1f", 0.75, 300, 56,
              "trappist_1g", 0.38, 30, 70, "trappist_1c", 0.3, 160, 22));
        event.register(Pioneers.rl("trappist_1f"), ExoSkyEffects.trappist(44, true, "trappist_1e", 0.9, 120, 42, "trappist_1g", 0.8, 300, 58,
              "trappist_1h", 0.35, 40, 66, "trappist_1d", 0.35, 170, 25));
        //1.0.7 (lot 11) : systemes lointains (pulsar, naine blanche, trou noir en 3D ; T:au_dela/far_systems.py)
        int pulsar = fr.pioneers.space.sky.client.mega.PhenomenonModels.PULSAR, dwarf = fr.pioneers.space.sky.client.mega.PhenomenonModels.WHITE_DWARF, hole = fr.pioneers.space.sky.client.mega.PhenomenonModels.BLACK_HOLE;
        event.register(Pioneers.rl("draugr"), ExoSkyEffects.far(pulsar, "lich", 0xCFE6FF, false, 0, 15, false, "poltergeist", 0.5, 120, -10, "phobetor", 0.3, 230,
              20));
        event.register(Pioneers.rl("poltergeist"), ExoSkyEffects.far(pulsar, "lich", 0xCFE6FF, false, 0, 10, true, "draugr", 0.25, 60, 12, "phobetor", 0.45,
              200, -18));
        event.register(Pioneers.rl("phobetor"), ExoSkyEffects.far(pulsar, "lich", 0xCFE6FF, false, 0, 5, false, "poltergeist", 0.6, 150, 15));
        event.register(Pioneers.rl("van_maanen_b"), ExoSkyEffects.far(dwarf, "van_maanen", 0xF4F8FF, true, 240, 30, false, "van_maanen_c", 0.4, 90, 20,
              "van_maanen_d", 0.3, 300, -10));
        event.register(Pioneers.rl("van_maanen_c"), ExoSkyEffects.far(dwarf, "van_maanen", 0xF4F8FF, false, 0, 12, false, "van_maanen_b", 0.5, 140, 15,
              "van_maanen_d", 0.45, 220, -15));
        event.register(Pioneers.rl("van_maanen_d"), ExoSkyEffects.far(dwarf, "van_maanen", 0xF4F8FF, false, 0, 8, false, "van_maanen_b", 0.3, 60, 10,
              "van_maanen_c", 0.35, 110, -5));
        event.register(Pioneers.rl("gaia_bh1_b"), ExoSkyEffects.far(hole, "gaia_bh1", 0xB088FF, true, 110, 24, true, "gaia_bh1_c", 0.5, 200, 18, "gaia_bh1_d",
              0.3, 300, -10));
        event.register(Pioneers.rl("gaia_bh1_c"), ExoSkyEffects.far(hole, "gaia_bh1", 0xB088FF, false, 0, 18, false, "gaia_bh1_b", 0.7, 120, 15, "gaia_bh1_d",
              0.4, 240, -12));
        event.register(Pioneers.rl("gaia_bh1_d"), ExoSkyEffects.far(hole, "gaia_bh1", 0xB088FF, false, 0, 10, true, "gaia_bh1_c", 0.45, 160, 12, "gaia_bh1_b",
              0.35, 200, -8));
        event.register(Pioneers.rl("gas_giant"), ExoSkyEffects.gasGiant());
        for (Celestial.Body body : new Celestial.Body[]{
              Celestial.Body.MOON, Celestial.Body.MERCURY,
              Celestial.Body.PHOBOS, Celestial.Body.DEIMOS,
              Celestial.Body.CERES, Celestial.Body.VESTA,
              Celestial.Body.PALLAS, Celestial.Body.HYGIEA,
              Celestial.Body.IO, Celestial.Body.EUROPA,
              Celestial.Body.GANYMEDE, Celestial.Body.CALLISTO,
              Celestial.Body.ENCELADUS, Celestial.Body.MIRANDA,
              Celestial.Body.TITANIA, Celestial.Body.TRITON,
              Celestial.Body.PLUTO, Celestial.Body.CHARON}) {
            event.register(Pioneers.rl(body.name().toLowerCase(Locale.ROOT)), new MoonSkyEffects(body));
        }
    }

    /** 0.20.26 : particules du mod. */
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PiParticles.STEAM_PLUME.get(), SteamPlumeParticle.Provider::new);
        //1.5 : fumee de pipe et de cigare
        event.registerSpriteSet(PiLeisure.SMOKE_RING.get(), SmokeRingParticle.RingProvider::new);
        event.registerSpriteSet(PiLeisure.PIPE_SMOKE.get(), SmokeRingParticle.WispProvider::new);
    }

    /** Le guide se recharge avec les ressources (langue, pack de ressources). */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager ->
              GuideBook.invalidate());
    }

    /** 1.0 : corps connecte des ports et controleurs industriels (bordure du tier le long des cotes libres, comme les carcasses). */
    @SubscribeEvent
    public static void wrapIndustrialBodies(ModelEvent.ModifyBakingResult event) {
        RailBakedModel.wrap(event);
        IndustrialBodyModel.wrap(event);
        //1.0.4-beta (lot 23) : textures connectees dessinees d'apres les voisins reels (etats perimes)
        ConnectedTexturesModel.wrap(event);
        //1.0.4-beta (lot 27) : cables et arbres caches, dessines comme leur bloc de cache
        CoverModel.wrap(event);
    }

    @SubscribeEvent
    public static void industrialBodiesBaked(ModelEvent.BakingCompleted event) {
        IndustrialBodyModel.reset();
        //0.20.0 : quads du rail recuits avec le nouvel atlas (apres son envoi, sur le fil principal)
        RailBakedModel.reset();
        CarRenderer.reset();
    }

    /** Textures connectees : modeles "pioneers:connected". */
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("connected", ConnectedModelLoader.INSTANCE);
    }

    /** Shader de lentille gravitationnelle. En cas d'echec, le trou noir s'affiche sans deformation. */
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Pioneers.rl("black_hole_lens"), DefaultVertexFormat.POSITION),
                  ChamberEffectRenderer::setLensShader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Pioneers.rl("sun_haze"), DefaultVertexFormat.POSITION),
                  ChamberEffectRenderer::setHazeShader);
        } catch (Exception e) {
            Pioneers.LOGGER.error("Impossible de charger les shaders des astres (lentille / chaleur), effets desactives", e);
        }
    }

    /** Rendu du trou noir, attache au bloc maitre du multibloc (qui peut etre un boitier ou un port). */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var rocket : PiEntityTypes.ROCKETS.values()) {
            event.registerEntityRenderer(rocket.get(), RocketRenderer::new);
        }
        event.registerEntityRenderer(PiEntityTypes.CAPSULE.get(), CapsuleRenderer::new);
        event.registerEntityRenderer(PiEntityTypes.LAUNCH_VISUAL.get(), LaunchVisualRenderer::new);
        event.registerEntityRenderer(PiEntityTypes.GAS_POCKET.get(), GasPocketRenderer::new);
        event.registerEntityRenderer(PiEntityTypes.HARVESTER_DRONE.get(), HarvesterDroneRenderer::new);
        for (UnitKind kind : UnitKind.values()) {
            event.registerEntityRenderer(PiRobotics.unitEntity(kind), context -> new RobotUnitRenderer(context, kind));
        }
        event.registerBlockEntityRenderer(PiRobotics.casingTile().get(), RenderRoboticHub::new);
        //Jauges des buffers de fluide et de gaz
        event.registerBlockEntityRenderer(PiTransport.BUFFER_TILE.get(), RenderBuffer::new);
        event.registerBlockEntityRenderer(PiIndustry.CONTROLLER_TILE.get(), RenderReactorCore::new);
        event.registerBlockEntityRenderer(PiComputing.SCREEN_TILE.get(), RenderSupervisionScreen::new);
        //1.11 : contenu des cables logistiques transparents
        event.registerBlockEntityRenderer(PiRobotics.bayTile().get(), RenderRoboticBay::new);
        event.registerBlockEntityRenderer(PiRobotics.MARKER_TILE.get(), RenderLogisticMarker::new);
        event.registerEntityRenderer(PiEntityTypes.METEOR.get(), MeteorRenderer::new);
        //0.20.0 : chemin de fer
        event.registerEntityRenderer(PiRail.CAR_ENTITY.get(), CarRenderer::new);
        event.registerEntityRenderer(PiNaval.SHIP_ENTITY.get(), ShipRenderer::new);
        event.registerEntityRenderer(PiGear.ENERGY_BOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
        event.registerBlockEntityRenderer(PiNaval.EQUIPMENT_TILE.get(), HarborEquipmentRenderer::new);
        event.registerBlockEntityRenderer(PiRail.STATION_TILE.get(), StationRenderer::new);
        //1.9.2 : multiblocs d'assemblage (baie robotique, hall des fusees)
        for (var tile : PiWorksMachines.HALL_TILES.values()) {
            event.registerBlockEntityRenderer(tile.get(), RenderAssemblyHall::new);
        }
        event.registerBlockEntityRenderer(PiScienceMachines.DATA_ANTENNA_TILE.get(), RenderDataAntenna::new);
        event.registerBlockEntityRenderer(PiBaseMachines.GREENHOUSE_CONTROLLER_TILE.get(), RenderGreenhouse::new);
        event.registerBlockEntityRenderer(PiSpaceMachines.FORCE_FIELD_TILE.get(), RenderForceField::new);
        event.registerBlockEntityRenderer(PiBaseMachines.MINING_OUTPOST_CONTROLLER_TILE.get(), RenderMiningOutpost::new);
        event.registerBlockEntityRenderer(PiBaseMachines.LAUNCH_PAD_CORE_TILE.get(), RenderLaunchPad::new);
        event.registerBlockEntityRenderer(PiStabilizer.CORE_TILE.get(), RenderStabilizerCore::new);
        //1.0.4-beta (lot 1) : colis du comptoir d'entrepot, battants des trappes de caisse
        event.registerBlockEntityRenderer(fr.pioneers.logistics.warehouse.PiWarehouse.COUNTER_TILE.get(), fr.pioneers.logistics.warehouse.client.CounterRenderer::new);
        //1.0.5-beta (lot 17) : interfaces d'entree et de sortie : leurs colis, comme ceux du comptoir
        event.registerBlockEntityRenderer(fr.pioneers.logistics.warehouse.PiWarehouse.INLET_TILE.get(), fr.pioneers.logistics.warehouse.client.CounterRenderer::new);
        event.registerBlockEntityRenderer(fr.pioneers.logistics.warehouse.PiWarehouse.OUTLET_TILE.get(), fr.pioneers.logistics.warehouse.client.CounterRenderer::new);
        event.registerBlockEntityRenderer(fr.pioneers.logistics.warehouse.PiWarehouse.HATCH_TILE.get(), fr.pioneers.logistics.warehouse.client.HatchRenderer::new);
        event.registerBlockEntityRenderer(PiScienceMachines.RESEARCH_TABLE_TILE.get(), RenderResearchTable::new);
        event.registerBlockEntityRenderer(PiScienceMachines.BIOME_ANALYZER_TILE.get(), context -> new RenderDataPorts<>("biome_analyzer"));
        event.registerBlockEntityRenderer(PiScienceMachines.ASTEROID_CRUSHER_TILE.get(), context -> new RenderDataPorts<>("asteroid_crusher"));
        event.registerBlockEntityRenderer(PiScienceMachines.GEO_SPECTROMETER_TILE.get(), context -> new RenderDataPorts<>("geo_spectrometer"));
        event.registerBlockEntityRenderer(PiScienceMachines.BIO_SEQUENCER_TILE.get(), context -> new RenderDataPorts<>("bio_sequencer"));
        for (var tile : PiWorksMachines.ASSEMBLY_TABLE_TILES.values()) {
            event.registerBlockEntityRenderer(tile.get(), RenderAssemblyTable::new);
        }
        event.registerBlockEntityRenderer(PiSpaceSim.CONTROLLER_TILE.get(), RenderSpaceSimulator::new);
        event.registerBlockEntityRenderer(PiCosmicBlocks.ABSORBER_CONTROLLER_TILE.get(), RenderSolarAbsorber::new);
        for (var tile : PiMachines.DATA_BANK_TILES.values()) {
            event.registerBlockEntityRenderer(tile.get(), RenderDataBank::new);
        }
        //Age mecanique : pieces tournantes
        for (var tile : PiKinetics.TILES) {
            registerKinetic(event, tile.get());
        }
        event.registerBlockEntityRenderer(PiKinetics.VANE_TILE.get(), VaneRenderer::new);
        event.registerBlockEntityRenderer(PiMechanics.HAND_SIEVE_TILE.get(), HandSieveRenderer::new);
        event.registerBlockEntityRenderer(PiMechanics.WOODEN_FLUID_BUFFER_TILE.get(), WoodenFluidBufferRenderer::new);
        //Age du bronze
        event.registerBlockEntityRenderer(PiBronze.CASTING_TABLE_TILE.get(), CastingTableRenderer::new);
        event.registerBlockEntityRenderer(PiBronze.CRUCIBLE_FURNACE_TILE.get(), CrucibleFurnaceRenderer::new);
        event.registerBlockEntityRenderer(PiBronze.BLAST_FURNACE_TILE.get(), BlastFurnaceRenderer::new);
        event.registerBlockEntityRenderer(PiBronze.ACCESSORY_TILE.get(), AccessoryRenderer::new);
        //1.0.5-beta (lot 38) : plus de pilon ni de plateau animes (marteau-pilon retire, presse a vapeur devenue machine de traitement)
        event.registerBlockEntityRenderer(PiBronze.STEAM_DRILL_TILE.get(), DrillRenderer::new);
        //1.0.2-beta (lot 4) : fonderie automatique : metal qui coule dans les conduits, metal qui fige dans les moules automatiques
        event.registerBlockEntityRenderer(PiBronze.CASTING_CHANNEL_TILE.get(), CastingChannelRenderer::new);
        event.registerBlockEntityRenderer(PiBronze.AUTO_MOLD_TILE.get(), AutoMoldRenderer::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerKinetic(EntityRenderersEvent.RegisterRenderers event, BlockEntityType type) {
        event.registerBlockEntityRenderer(type, KineticRenderer::new);
    }
}
