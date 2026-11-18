package fr.pioneers.space.environment;

import fr.pioneers.core.registry.BlockRegistryObject;
import fr.pioneers.space.giant.GasCloudBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import fr.pioneers.core.registries.PiBlocks;

/**
 * Blocs generes (gc/prog40/exojava40.py) : blocs naturels des exoplanetes (Alpha du Centaure, TRAPPIST-1) et nuages
 * traversables des geantes gazeuses.
 */
public final class PiExoBlocks {

    private PiExoBlocks() {
    }

    /** Blocs naturels des exoplanetes, dans l'ordre de l'onglet creatif. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> NATURAL = new LinkedHashMap<>();
    /** Nuages des geantes gazeuses. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> CLOUDS = new LinkedHashMap<>();

    /** A appeler avant l'enregistrement des blocs. */
    public static void init() {
    }

    static {
        NATURAL.put("verdigris_rock", PiBlocks.BLOCKS.register("verdigris_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("toliman_soil", PiBlocks.BLOCKS.register("toliman_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6F).sound(SoundType.ROOTED_DIRT))));
        NATURAL.put("toliman_moss", PiBlocks.BLOCKS.register("toliman_moss", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).sound(SoundType.MOSS))));
        NATURAL.put("toliman_silt", PiBlocks.BLOCKS.register("toliman_silt", () -> new SandBlock(0x929B7C, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("bloomstone", PiBlocks.BLOCKS.register("bloomstone", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 8))));
        NATURAL.put("proxima_rock", PiBlocks.BLOCKS.register("proxima_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("proxima_twilight_soil", PiBlocks.BLOCKS.register("proxima_twilight_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).strength(0.6F).sound(SoundType.ROOTED_DIRT))));
        NATURAL.put("proxima_glacier_ice", PiBlocks.BLOCKS.register("proxima_glacier_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("proxima_scoria", PiBlocks.BLOCKS.register("proxima_scoria", () -> new SandBlock(0x412521, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("proxima_frost", PiBlocks.BLOCKS.register("proxima_frost", () -> new SandBlock(0xCEB0C2, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("proxima_d_rock", PiBlocks.BLOCKS.register("proxima_d_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("proxima_d_slag", PiBlocks.BLOCKS.register("proxima_d_slag", () -> new SandBlock(0x382B2A, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("flare_crystal", PiBlocks.BLOCKS.register("flare_crystal", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(1.4F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 11))));
        NATURAL.put("trappist_1b_rock", PiBlocks.BLOCKS.register("trappist_1b_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.BASALT))));
        NATURAL.put("trappist_1b_obsidian_crust", PiBlocks.BLOCKS.register("trappist_1b_obsidian_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(2.4F, 6.0F).sound(SoundType.DEEPSLATE))));
        NATURAL.put("trappist_1b_ash", PiBlocks.BLOCKS.register("trappist_1b_ash", () -> new SandBlock(0x605A58, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("trappist_1d_rock", PiBlocks.BLOCKS.register("trappist_1d_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("trappist_1d_sand", PiBlocks.BLOCKS.register("trappist_1d_sand", () -> new SandBlock(0xCAC2A1, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("trappist_1d_shale", PiBlocks.BLOCKS.register("trappist_1d_shale", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.MUD_BRICKS))));
        NATURAL.put("trappist_1e_rock", PiBlocks.BLOCKS.register("trappist_1e_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("trappist_1e_soil", PiBlocks.BLOCKS.register("trappist_1e_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.ROOTED_DIRT))));
        NATURAL.put("trappist_1e_glowmoss", PiBlocks.BLOCKS.register("trappist_1e_glowmoss", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).sound(SoundType.MOSS).lightLevel(state -> 5))));
        NATURAL.put("glowcap_stem", PiBlocks.BLOCKS.register("glowcap_stem", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.0F).sound(SoundType.WOOD))));
        NATURAL.put("glowcap_cap", PiBlocks.BLOCKS.register("glowcap_cap", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).sound(SoundType.WART_BLOCK).lightLevel(state -> 13))));
        NATURAL.put("trappist_1f_rock", PiBlocks.BLOCKS.register("trappist_1f_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("trappist_1f_ice", PiBlocks.BLOCKS.register("trappist_1f_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("trappist_1f_snow", PiBlocks.BLOCKS.register("trappist_1f_snow", () -> new SandBlock(0xE7EDF1, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("trappist_1f_blue_ice", PiBlocks.BLOCKS.register("trappist_1f_blue_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        CLOUDS.put("jupiter_cloud_cream", PiBlocks.BLOCKS.register("jupiter_cloud_cream", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.SAND), false)));
        CLOUDS.put("jupiter_cloud_ochre", PiBlocks.BLOCKS.register("jupiter_cloud_ochre", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.TERRACOTTA_ORANGE), false)));
        CLOUDS.put("jupiter_cloud_red", PiBlocks.BLOCKS.register("jupiter_cloud_red", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.TERRACOTTA_RED), false)));
        CLOUDS.put("jupiter_cloud_white", PiBlocks.BLOCKS.register("jupiter_cloud_white", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.SNOW), false)));
        CLOUDS.put("saturn_cloud_gold", PiBlocks.BLOCKS.register("saturn_cloud_gold", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.SAND), false)));
        CLOUDS.put("saturn_cloud_pale", PiBlocks.BLOCKS.register("saturn_cloud_pale", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.TERRACOTTA_WHITE), false)));
        CLOUDS.put("saturn_cloud_hex", PiBlocks.BLOCKS.register("saturn_cloud_hex", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_LIGHT_GRAY), false)));
        CLOUDS.put("uranus_cloud", PiBlocks.BLOCKS.register("uranus_cloud", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_LIGHT_BLUE), false)));
        CLOUDS.put("uranus_haze", PiBlocks.BLOCKS.register("uranus_haze", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_LIGHT_GRAY), false)));
        CLOUDS.put("neptune_cloud", PiBlocks.BLOCKS.register("neptune_cloud", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_BLUE), false)));
        CLOUDS.put("neptune_cloud_dark", PiBlocks.BLOCKS.register("neptune_cloud_dark", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_BLUE), false)));
        CLOUDS.put("neptune_cloud_white", PiBlocks.BLOCKS.register("neptune_cloud_white", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.SNOW), false)));
        CLOUDS.put("storm_cloud", PiBlocks.BLOCKS.register("storm_cloud", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_GRAY), true)));
        CLOUDS.put("deep_haze", PiBlocks.BLOCKS.register("deep_haze", () -> new GasCloudBlock(GasCloudBlock.properties(MapColor.COLOR_BLACK), true)));
    }
}
