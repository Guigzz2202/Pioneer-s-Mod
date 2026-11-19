package fr.pioneers.core.registries;

import fr.pioneers.space.content.lightning.LightningConductorBlock;
import fr.pioneers.core.registry.BlockRegistryObject;
import fr.pioneers.core.registry.PiBlockRegister;
import fr.pioneers.space.DryIceBlock;
import fr.pioneers.space.LaunchPadBlock;
import fr.pioneers.space.LaunchPortBlock;
import fr.pioneers.space.LaunchTowerBlock;
import fr.pioneers.space.farm.ChargePadBlock;
import fr.pioneers.space.farm.GreenhousePortBlock;
import fr.pioneers.space.farm.HydroponicSubstrateBlock;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.farm.SpacePlantBlock;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.mining.MiningPortBlock;
import fr.pioneers.space.mining.ProspectingRadarBlock;
import fr.pioneers.space.mining.RoverDockBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Blocs du mod.
 */
public final class PiBlocks {

    private PiBlocks() {
    }

    public static final PiBlockRegister BLOCKS = new PiBlockRegister();

    // --- Stabilisateur spatial : voir PiStabilizer ---

    // --- Lune ---
    public static final BlockRegistryObject<SandBlock, BlockItem> MOON_SAND = BLOCKS.register("moon_sand", () -> new SandBlock(0x9C9C9C,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> MOON_ROCK = BLOCKS.register("moon_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));

    // --- Lune : biomes ---
    /** Regolithe sombre des mers lunaires. */
    public static final BlockRegistryObject<SandBlock, BlockItem> MARE_REGOLITH = BLOCKS.register("mare_regolith", () -> new SandBlock(0x46464A,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND)));
    /** Basalte des mers lunaires (sous le regolithe sombre). */
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> LUNAR_BASALT = BLOCKS.register("lunar_basalt", () -> new RotatedPillarBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.BASALT)));
    /** Regolithe gele des bassins d'ombre eternelle. */
    public static final BlockRegistryObject<SandBlock, BlockItem> FROZEN_REGOLITH = BLOCKS.register("frozen_regolith", () -> new SandBlock(0x96A0AC,
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.6F).sound(SoundType.SNOW)));
    /** Glace d'eau lunaire, piegee dans les bassins d'ombre (glissante). */
    public static final BlockRegistryObject<Block, BlockItem> LUNAR_ICE = BLOCKS.register("lunar_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS).friction(0.98F)));
    /** Minerai de lunarium dans le basalte des mers (plus abondant). */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> LUNARIUM_ORE_BASALT = BLOCKS.register("lunarium_ore_basalt",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                .requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.BASALT).lightLevel(state -> 4),
                UniformInt.of(2, 5)));

    /** Minerai de lunarium : uniquement dans la roche lunaire. */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> LUNARIUM_ORE = BLOCKS.register("lunarium_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.STONE).lightLevel(state -> 3),
                UniformInt.of(2, 5)));

    // --- Pas de tir (le coeur est dans PiBaseMachines) ---
    public static final BlockRegistryObject<LaunchPadBlock, BlockItem> LAUNCH_PAD = BLOCKS.register("launch_pad", () -> new LaunchPadBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(3.5F, 9.0F).sound(SoundType.METAL)));
    /** Port du complexe de lancement (remplace une plaque du disque). */
    public static final BlockRegistryObject<LaunchPortBlock, BlockItem> LAUNCH_PORT = BLOCKS.register("launch_port",
          () -> new LaunchPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops()
                .strength(3.5F, 9.0F).sound(SoundType.METAL)));
    public static final BlockRegistryObject<LaunchTowerBlock, BlockItem> LAUNCH_TOWER = BLOCKS.register("launch_tower", () -> new LaunchTowerBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.5F, 9.0F).sound(SoundType.METAL).noOcclusion()));

    // --- Recherche spatiale ---

    /** Conducteur du mat du paratonnerre. */
    public static final BlockRegistryObject<LightningConductorBlock, BlockItem> LIGHTNING_CONDUCTOR = BLOCKS.register("lightning_conductor",
          () -> new LightningConductorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops()
                .strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion()));

    // --- Machines d'analyse ---
    /** Analyseur de biomes : produit les donnees des biomes enregistres dans les cellules. */

    // --- Serre spatiale ---
    public static final BlockRegistryObject<GreenhousePortBlock, BlockItem> GREENHOUSE_PORT = BLOCKS.register("greenhouse_port",
          () -> new GreenhousePortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
                .requiresCorrectToolForDrops().strength(3.5F, 9.0F).sound(SoundType.METAL)));
    public static final BlockRegistryObject<HydroponicSubstrateBlock, BlockItem> HYDROPONIC_SUBSTRATE = BLOCKS.register("hydroponic_substrate",
          () -> new HydroponicSubstrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.0F)
                .sound(SoundType.ROOTED_DIRT)));
    public static final BlockRegistryObject<ChargePadBlock, BlockItem> CHARGE_PAD = BLOCKS.register("charge_pad",
          () -> new ChargePadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                .strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 6)));

    // --- Table d'assemblage ---

    // --- Mars ---
    public static final BlockRegistryObject<SandBlock, BlockItem> MARS_SAND = BLOCKS.register("mars_sand", () -> new SandBlock(0xB5532E,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> MARS_ROCK = BLOCKS.register("mars_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));

    // --- Venus ---
    public static final BlockRegistryObject<SandBlock, BlockItem> VENUS_REGOLITH = BLOCKS.register("venus_regolith", () -> new SandBlock(0xB0803E,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(0.6F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> VENUS_ROCK = BLOCKS.register("venus_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GRAY).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.BASALT)));

    // --- Planetes rocheuses et asteroides (regolithe en surface, roche dessous) ---
    public static final BlockRegistryObject<SandBlock, BlockItem> MERCURY_REGOLITH = BLOCKS.register("mercury_regolith", () -> new SandBlock(0x6E6A64,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> MERCURY_ROCK = BLOCKS.register("mercury_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> PHOBOS_REGOLITH = BLOCKS.register("phobos_regolith", () -> new SandBlock(0x5E554A,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> PHOBOS_ROCK = BLOCKS.register("phobos_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> DEIMOS_REGOLITH = BLOCKS.register("deimos_regolith", () -> new SandBlock(0x9E9282,
          BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> DEIMOS_ROCK = BLOCKS.register("deimos_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> CERES_REGOLITH = BLOCKS.register("ceres_regolith", () -> new SandBlock(0x5F5C58,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> CERES_ROCK = BLOCKS.register("ceres_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> VESTA_REGOLITH = BLOCKS.register("vesta_regolith", () -> new SandBlock(0xA49884,
          BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> VESTA_BASALT = BLOCKS.register("vesta_basalt", () -> new RotatedPillarBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.BASALT)));
    public static final BlockRegistryObject<SandBlock, BlockItem> PALLAS_REGOLITH = BLOCKS.register("pallas_regolith", () -> new SandBlock(0x8A8E92,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> PALLAS_ROCK = BLOCKS.register("pallas_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> HYGIEA_REGOLITH = BLOCKS.register("hygiea_regolith", () -> new SandBlock(0x45413E,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> HYGIEA_ROCK = BLOCKS.register("hygiea_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    // --- Systeme solaire externe : lunes des geantes, Pluton et Charon ---
    public static final BlockRegistryObject<SandBlock, BlockItem> IO_SULFUR = BLOCKS.register("io_sulfur", () -> new SandBlock(0xD8C24A,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> IO_BASALT = BLOCKS.register("io_basalt", () -> new RotatedPillarBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.BASALT)));
    public static final BlockRegistryObject<Block, BlockItem> EUROPA_ICE = BLOCKS.register("europa_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS).friction(0.98F)));
    public static final BlockRegistryObject<Block, BlockItem> EUROPA_DEEP_ICE = BLOCKS.register("europa_deep_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS)));
    public static final BlockRegistryObject<SandBlock, BlockItem> GANYMEDE_REGOLITH = BLOCKS.register("ganymede_regolith", () -> new SandBlock(0x8E8476,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> GANYMEDE_ROCK = BLOCKS.register("ganymede_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> CALLISTO_REGOLITH = BLOCKS.register("callisto_regolith", () -> new SandBlock(0x5A5048,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> CALLISTO_ROCK = BLOCKS.register("callisto_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> ENCELADUS_SNOW = BLOCKS.register("enceladus_snow", () -> new SandBlock(0xF2F6FA,
          BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.SNOW)));
    public static final BlockRegistryObject<Block, BlockItem> ENCELADUS_ICE = BLOCKS.register("enceladus_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS)));
    public static final BlockRegistryObject<SandBlock, BlockItem> TITAN_SAND = BLOCKS.register("titan_sand", () -> new SandBlock(0x8A6236,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> TITAN_ICE = BLOCKS.register("titan_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS)));
    public static final BlockRegistryObject<SandBlock, BlockItem> MIRANDA_REGOLITH = BLOCKS.register("miranda_regolith", () -> new SandBlock(0xA8A6A2,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> MIRANDA_ROCK = BLOCKS.register("miranda_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> TITANIA_REGOLITH = BLOCKS.register("titania_regolith", () -> new SandBlock(0xB0A69C,
          BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> TITANIA_ROCK = BLOCKS.register("titania_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> TRITON_FROST = BLOCKS.register("triton_frost", () -> new SandBlock(0xD8C4B8,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F).sound(SoundType.SNOW)));
    public static final BlockRegistryObject<Block, BlockItem> TRITON_ROCK = BLOCKS.register("triton_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> PLUTO_FROST = BLOCKS.register("pluto_frost", () -> new SandBlock(0xE8DCC8,
          BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.SNOW)));
    public static final BlockRegistryObject<Block, BlockItem> PLUTO_THOLIN = BLOCKS.register("pluto_tholin", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final BlockRegistryObject<SandBlock, BlockItem> CHARON_REGOLITH = BLOCKS.register("charon_regolith", () -> new SandBlock(0x9C9690,
          BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> CHARON_ROCK = BLOCKS.register("charon_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));

    /** Depots de sel brillants de Ceres. */
    public static final BlockRegistryObject<Block, BlockItem> CERES_SALT = BLOCKS.register("ceres_salt", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.CALCITE).lightLevel(state -> 2)));

    // --- Plantes spatiales sauvages ---
    public static final Map<SpacePlant, BlockRegistryObject<SpacePlantBlock, BlockItem>> SPACE_PLANTS =
          new EnumMap<>(SpacePlant.class);

    static {
        for (SpacePlant plant : SpacePlant.values()) {
            SPACE_PLANTS.put(plant, BLOCKS.register(plant.blockName, () -> new SpacePlantBlock(plant,
                  BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS)
                        .offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(state -> plant.light)
                        .pushReaction(PushReaction.DESTROY))));
        }
    }

    // --- Mars : biomes ---
    /** Basalte de la province volcanique de Tharsis. */
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> MARS_BASALT = BLOCKS.register("mars_basalt", () -> new RotatedPillarBlock(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BLACK).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.BASALT)));
    /** Sable basaltique sombre des mers de dunes et des flancs volcaniques. */
    public static final BlockRegistryObject<SandBlock, BlockItem> MARS_DARK_SAND = BLOCKS.register("mars_dark_sand", () -> new SandBlock(0x4A3430,
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.5F).sound(SoundType.SAND)));
    /** Roche stratifiee des falaises de Valles Marineris. */
    public static final BlockRegistryObject<Block, BlockItem> MARS_LAYERED_ROCK = BLOCKS.register("mars_layered_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    /** Roche ocre (strates claires des canyons). */
    public static final BlockRegistryObject<Block, BlockItem> MARS_OCHRE_ROCK = BLOCKS.register("mars_ochre_rock", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.STONE)));
    /** Argile du delta ancien : la trace d'une eau disparue. */
    public static final BlockRegistryObject<Block, BlockItem> MARS_CLAY = BLOCKS.register("mars_clay", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).strength(0.7F).sound(SoundType.GRAVEL)));
    /** Gypse : veines blanches deposees par l'eau dans les sediments. */
    public static final BlockRegistryObject<Block, BlockItem> MARS_GYPSUM = BLOCKS.register("mars_gypsum", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.CALCITE)));
    /** Pergelisol des hautes latitudes. */
    public static final BlockRegistryObject<Block, BlockItem> MARS_PERMAFROST = BLOCKS.register("mars_permafrost", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).strength(0.9F).sound(SoundType.GRAVEL)));
    /** Glace d'eau stratifiee des calottes polaires (glissante). */
    public static final BlockRegistryObject<Block, BlockItem> MARS_POLAR_ICE = BLOCKS.register("mars_polar_ice", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 3.0F).sound(SoundType.GLASS).friction(0.98F)));
    /** Glace carbonique : sublime en volutes blanches (glissante). */
    public static final BlockRegistryObject<DryIceBlock, BlockItem> DRY_ICE = BLOCKS.register("dry_ice",
          () -> new DryIceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.8F).sound(SoundType.SNOW).friction(0.98F)));
    /** Stromatolithe fossile : une trace de vie ancienne, cachee dans l'argile du delta. */
    public static final BlockRegistryObject<Block, BlockItem> STROMATOLITE = BLOCKS.register("stromatolite", () -> new Block(
          BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.CALCITE)));
    /** Minerai de marsium (roche martienne). */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> MARSIUM_ORE = BLOCKS.register("marsium_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED)
                .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.STONE), UniformInt.of(2, 5)));
    /** Minerai de tharsium : rares cristaux verts luminescents, dans le basalte profond de Tharsis. */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> THARSIUM_ORE = BLOCKS.register("tharsium_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD)
                .requiresCorrectToolForDrops().strength(4.5F, 9.0F).sound(SoundType.AMETHYST).lightLevel(state -> 7), UniformInt.of(4, 8)));
    /** Hematite : les "myrtilles" martiennes, concretions de fer. */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> HEMATITE_ORE = BLOCKS.register("hematite_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED)
                .requiresCorrectToolForDrops().strength(2.5F, 6.0F).sound(SoundType.STONE), UniformInt.of(0, 2)));
    /** Minerai de venusium (roche venusienne). */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> VENUSIUM_ORE = BLOCKS.register("venusium_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                .requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.BASALT).lightLevel(state -> 4), UniformInt.of(3, 6)));
    /** Minerai de mercurium (roche de Mercure). */
    public static final BlockRegistryObject<DropExperienceBlock, BlockItem> MERCURIUM_ORE = BLOCKS.register("mercurium_ore",
          () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.METAL), UniformInt.of(3, 7)));

    // --- Mercure, Venus, Phobos, Deimos (generes par planets2.py) ---
    public static final BlockRegistryObject<Block, BlockItem> MERCURY_DARK_ROCK = BLOCKS.register("mercury_dark_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<Block, BlockItem> HOLLOW_CRUST = BLOCKS.register("hollow_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8F, 6.0F).sound(SoundType.CALCITE).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> MERCURY_BASALT = BLOCKS.register("mercury_basalt", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.BASALT).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<Block, BlockItem> MERCURY_POLAR_ICE = BLOCKS.register("mercury_polar_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(1.0F, 6.0F).sound(SoundType.GLASS).requiresCorrectToolForDrops().friction(0.98F)));
    public static final BlockRegistryObject<RotatedPillarBlock, BlockItem> VENUS_BASALT = BLOCKS.register("venus_basalt", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BLACK).strength(2.0F, 6.0F).sound(SoundType.BASALT).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<Block, BlockItem> SULFUR_CRUST = BLOCKS.register("sulfur_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.0F, 6.0F).sound(SoundType.TUFF).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<Block, BlockItem> TESSERA_ROCK = BLOCKS.register("tessera_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<SandBlock, BlockItem> VENUS_ASH = BLOCKS.register("venus_ash", () -> new SandBlock(0x514B47, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F, 0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<SandBlock, BlockItem> PHOBOS_DUST = BLOCKS.register("phobos_dust", () -> new SandBlock(0x3D3731, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.4F, 0.4F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> PHOBOS_BRECCIA = BLOCKS.register("phobos_breccia", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<SandBlock, BlockItem> DEIMOS_BRIGHT_REGOLITH = BLOCKS.register("deimos_bright_regolith", () -> new SandBlock(0xCCC4B6, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F, 0.5F).sound(SoundType.SAND)));
    public static final BlockRegistryObject<Block, BlockItem> DEIMOS_BRECCIA = BLOCKS.register("deimos_breccia", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    // --- Minage planetaire ---
    public static final BlockRegistryObject<MiningPortBlock, BlockItem> MINING_PORT = BLOCKS.register("mining_port",
          () -> new MiningPortBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                .requiresCorrectToolForDrops().strength(3.5F, 9.0F).sound(SoundType.METAL)));
    public static final BlockRegistryObject<RoverDockBlock, BlockItem> ROVER_DOCK = BLOCKS.register("rover_dock",
          () -> new RoverDockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                .strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 6)));
    public static final BlockRegistryObject<ProspectingRadarBlock, BlockItem> PROSPECTING_RADAR = BLOCKS.register("prospecting_radar",
          () -> new ProspectingRadarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                .strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    // --- 1.0.7 (« Plus loin et au-dela », lot 4) : chantier de megastructure ---
    public static final BlockRegistryObject<fr.pioneers.space.mega.MegaSiteBlock, BlockItem> MEGASTRUCTURE_SITE = BLOCKS.register("megastructure_site",
          () -> new fr.pioneers.space.mega.MegaSiteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops()
                .strength(5.0F, 12.0F).sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> 9)));
    /** Blocs de stockage des metaux exotiques. */
    public static final Map<MiningMaterial, BlockRegistryObject<Block, BlockItem>> MATERIAL_BLOCKS =
          new EnumMap<>(MiningMaterial.class);
    /** Minerais naturels des 17 metaux des astres lointains, dans la roche de leur astre. */
    public static final Map<MiningMaterial, BlockRegistryObject<DropExperienceBlock, BlockItem>> WORLD_ORES =
          new EnumMap<>(MiningMaterial.class);
    /** Blocs de minerai brut (9 bruts), comme le fer brut. */
    public static final Map<MiningMaterial, BlockRegistryObject<Block, BlockItem>> RAW_BLOCKS =
          new EnumMap<>(MiningMaterial.class);

    static {
        for (MiningMaterial material : MiningMaterial.values()) {
            MATERIAL_BLOCKS.put(material, BLOCKS.register(material.id() + "_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                  .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL)
                  .lightLevel(state -> material == MiningMaterial.THARSIUM ? 6 : 0))));
            RAW_BLOCKS.put(material, BLOCKS.register("raw_" + material.id() + "_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                  .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.STONE))));
            if (material.hasWorldOre()) {
                int light = material.isCrystal() ? 5 : 0;
                float hardness = 3.0F + material.oreHardness() * 0.5F;
                WORLD_ORES.put(material, BLOCKS.register(material.id() + "_ore", () -> new DropExperienceBlock(
                      BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(hardness, 6.0F)
                            .sound(material.isCrystal() ? SoundType.AMETHYST : SoundType.STONE).lightLevel(state -> light),
                      UniformInt.of(1 + material.oreHardness() / 2, 3 + material.oreHardness()))));
            }
        }
    }

}
