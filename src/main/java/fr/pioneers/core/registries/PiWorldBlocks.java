package fr.pioneers.core.registries;

import fr.pioneers.core.registry.BlockRegistryObject;
import fr.pioneers.space.MeteoriteBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Blocs generes (worldjava.py) : nouveaux blocs naturels des astres (planets3.py), petits asteroides poses au sol (meteorites.py)
 * et variantes des six basaltes (basalts.py).
 */
public final class PiWorldBlocks {

    private PiWorldBlocks() {
    }

    /** Blocs naturels des astres, dans l'ordre de l'onglet creatif. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> NATURAL = new LinkedHashMap<>();
    /** Asteroides au sol. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> METEORITES = new LinkedHashMap<>();
    /** Variantes de construction des basaltes. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> BASALTS = new LinkedHashMap<>();

    private static BlockBehaviour.Properties basalt(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.BASALT);
    }

    /** A appeler avant l'enregistrement des blocs. */
    public static void init() {
    }

    static {
        NATURAL.put("ceres_clay", PiBlocks.BLOCKS.register("ceres_clay", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(0.9F, 6.0F).sound(SoundType.MUD_BRICKS))));
        NATURAL.put("ceres_ice", PiBlocks.BLOCKS.register("ceres_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("diogenite", PiBlocks.BLOCKS.register("diogenite", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("vesta_dark_regolith", PiBlocks.BLOCKS.register("vesta_dark_regolith", () -> new SandBlock(0x423D39, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("pallas_bright_regolith", PiBlocks.BLOCKS.register("pallas_bright_regolith", () -> new SandBlock(0xC5C9CF, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("hygiea_carbon_crust", PiBlocks.BLOCKS.register("hygiea_carbon_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.DEEPSLATE))));
        NATURAL.put("io_red_sulfur", PiBlocks.BLOCKS.register("io_red_sulfur", () -> new SandBlock(0xBC562A, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("io_so2_frost", PiBlocks.BLOCKS.register("io_so2_frost", () -> new SandBlock(0xECEAD6, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("europa_lineae_ice", PiBlocks.BLOCKS.register("europa_lineae_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("europa_salt", PiBlocks.BLOCKS.register("europa_salt", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).requiresCorrectToolForDrops().strength(0.8F, 6.0F).sound(SoundType.CALCITE))));
        NATURAL.put("ganymede_bright_ice", PiBlocks.BLOCKS.register("ganymede_bright_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS))));
        NATURAL.put("ganymede_frost", PiBlocks.BLOCKS.register("ganymede_frost", () -> new SandBlock(0xE3E9EE, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("callisto_ice", PiBlocks.BLOCKS.register("callisto_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS))));
        NATURAL.put("enceladus_blue_ice", PiBlocks.BLOCKS.register("enceladus_blue_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("titan_pebbles", PiBlocks.BLOCKS.register("titan_pebbles", () -> new SandBlock(0x9E978D, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.6F).sound(SoundType.GRAVEL))));
        NATURAL.put("titan_tholin", PiBlocks.BLOCKS.register("titan_tholin", () -> new SandBlock(0x71482A, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("miranda_banded_ice", PiBlocks.BLOCKS.register("miranda_banded_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("titania_frost", PiBlocks.BLOCKS.register("titania_frost", () -> new SandBlock(0xDEE2EA, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("triton_nitrogen_ice", PiBlocks.BLOCKS.register("triton_nitrogen_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("triton_dark_dust", PiBlocks.BLOCKS.register("triton_dark_dust", () -> new SandBlock(0x463F3D, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.4F).sound(SoundType.SAND))));
        NATURAL.put("pluto_nitrogen_ice", PiBlocks.BLOCKS.register("pluto_nitrogen_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("pluto_methane_ice", PiBlocks.BLOCKS.register("pluto_methane_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.GLASS))));
        NATURAL.put("pluto_water_ice", PiBlocks.BLOCKS.register("pluto_water_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("charon_tholin", PiBlocks.BLOCKS.register("charon_tholin", () -> new SandBlock(0x824938, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(0.5F).sound(SoundType.SAND))));
        METEORITES.put("rock_meteorite", PiBlocks.BLOCKS.register("rock_meteorite", () -> new MeteoriteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).requiresCorrectToolForDrops().strength(1.2F, 3.5999999999999996F).sound(SoundType.STONE))));
        METEORITES.put("iron_meteorite", PiBlocks.BLOCKS.register("iron_meteorite", () -> new MeteoriteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(2.5F, 7.5F).sound(SoundType.METAL))));
        METEORITES.put("ice_meteorite", PiBlocks.BLOCKS.register("ice_meteorite", () -> new MeteoriteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(0.8F, 2.4000000000000004F).sound(SoundType.GLASS))));
        METEORITES.put("volcanic_meteorite", PiBlocks.BLOCKS.register("volcanic_meteorite", () -> new MeteoriteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(1.5F, 4.5F).sound(SoundType.BASALT).lightLevel(state -> 3))));
        BASALTS.put("polished_lunar_basalt", PiBlocks.BLOCKS.register("polished_lunar_basalt", () -> new RotatedPillarBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_lunar_basalt", PiBlocks.BLOCKS.register("smooth_lunar_basalt", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_lunar_basalt_stairs", PiBlocks.BLOCKS.register("smooth_lunar_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_lunar_basalt").getBlock().defaultBlockState(), basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_lunar_basalt_slab", PiBlocks.BLOCKS.register("smooth_lunar_basalt_slab", () -> new SlabBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("lunar_basalt_bricks", PiBlocks.BLOCKS.register("lunar_basalt_bricks", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("lunar_basalt_brick_stairs", PiBlocks.BLOCKS.register("lunar_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("lunar_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.DEEPSLATE))));
        BASALTS.put("lunar_basalt_brick_slab", PiBlocks.BLOCKS.register("lunar_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("lunar_basalt_brick_wall", PiBlocks.BLOCKS.register("lunar_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.DEEPSLATE).forceSolidOn())));
        BASALTS.put("cracked_lunar_basalt_bricks", PiBlocks.BLOCKS.register("cracked_lunar_basalt_bricks", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("chiseled_lunar_basalt", PiBlocks.BLOCKS.register("chiseled_lunar_basalt", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("polished_mars_basalt", PiBlocks.BLOCKS.register("polished_mars_basalt", () -> new RotatedPillarBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_mars_basalt", PiBlocks.BLOCKS.register("smooth_mars_basalt", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_mars_basalt_stairs", PiBlocks.BLOCKS.register("smooth_mars_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_mars_basalt").getBlock().defaultBlockState(), basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_mars_basalt_slab", PiBlocks.BLOCKS.register("smooth_mars_basalt_slab", () -> new SlabBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("mars_basalt_bricks", PiBlocks.BLOCKS.register("mars_basalt_bricks", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("mars_basalt_brick_stairs", PiBlocks.BLOCKS.register("mars_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("mars_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("mars_basalt_brick_slab", PiBlocks.BLOCKS.register("mars_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("mars_basalt_brick_wall", PiBlocks.BLOCKS.register("mars_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.TERRACOTTA_BLACK).forceSolidOn())));
        BASALTS.put("cracked_mars_basalt_bricks", PiBlocks.BLOCKS.register("cracked_mars_basalt_bricks", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("chiseled_mars_basalt", PiBlocks.BLOCKS.register("chiseled_mars_basalt", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("polished_mercury_basalt", PiBlocks.BLOCKS.register("polished_mercury_basalt", () -> new RotatedPillarBlock(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("smooth_mercury_basalt", PiBlocks.BLOCKS.register("smooth_mercury_basalt", () -> new Block(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("smooth_mercury_basalt_stairs", PiBlocks.BLOCKS.register("smooth_mercury_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_mercury_basalt").getBlock().defaultBlockState(), basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("smooth_mercury_basalt_slab", PiBlocks.BLOCKS.register("smooth_mercury_basalt_slab", () -> new SlabBlock(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("mercury_basalt_bricks", PiBlocks.BLOCKS.register("mercury_basalt_bricks", () -> new Block(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("mercury_basalt_brick_stairs", PiBlocks.BLOCKS.register("mercury_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("mercury_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("mercury_basalt_brick_slab", PiBlocks.BLOCKS.register("mercury_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("mercury_basalt_brick_wall", PiBlocks.BLOCKS.register("mercury_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.COLOR_GRAY).forceSolidOn())));
        BASALTS.put("cracked_mercury_basalt_bricks", PiBlocks.BLOCKS.register("cracked_mercury_basalt_bricks", () -> new Block(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("chiseled_mercury_basalt", PiBlocks.BLOCKS.register("chiseled_mercury_basalt", () -> new Block(basalt(MapColor.COLOR_GRAY))));
        BASALTS.put("polished_venus_basalt", PiBlocks.BLOCKS.register("polished_venus_basalt", () -> new RotatedPillarBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_venus_basalt", PiBlocks.BLOCKS.register("smooth_venus_basalt", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_venus_basalt_stairs", PiBlocks.BLOCKS.register("smooth_venus_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_venus_basalt").getBlock().defaultBlockState(), basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("smooth_venus_basalt_slab", PiBlocks.BLOCKS.register("smooth_venus_basalt_slab", () -> new SlabBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("venus_basalt_bricks", PiBlocks.BLOCKS.register("venus_basalt_bricks", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("venus_basalt_brick_stairs", PiBlocks.BLOCKS.register("venus_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("venus_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("venus_basalt_brick_slab", PiBlocks.BLOCKS.register("venus_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("venus_basalt_brick_wall", PiBlocks.BLOCKS.register("venus_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.TERRACOTTA_BLACK).forceSolidOn())));
        BASALTS.put("cracked_venus_basalt_bricks", PiBlocks.BLOCKS.register("cracked_venus_basalt_bricks", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("chiseled_venus_basalt", PiBlocks.BLOCKS.register("chiseled_venus_basalt", () -> new Block(basalt(MapColor.TERRACOTTA_BLACK))));
        BASALTS.put("polished_io_basalt", PiBlocks.BLOCKS.register("polished_io_basalt", () -> new RotatedPillarBlock(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("smooth_io_basalt", PiBlocks.BLOCKS.register("smooth_io_basalt", () -> new Block(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("smooth_io_basalt_stairs", PiBlocks.BLOCKS.register("smooth_io_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_io_basalt").getBlock().defaultBlockState(), basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("smooth_io_basalt_slab", PiBlocks.BLOCKS.register("smooth_io_basalt_slab", () -> new SlabBlock(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("io_basalt_bricks", PiBlocks.BLOCKS.register("io_basalt_bricks", () -> new Block(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("io_basalt_brick_stairs", PiBlocks.BLOCKS.register("io_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("io_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("io_basalt_brick_slab", PiBlocks.BLOCKS.register("io_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("io_basalt_brick_wall", PiBlocks.BLOCKS.register("io_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.COLOR_BLACK).forceSolidOn())));
        BASALTS.put("cracked_io_basalt_bricks", PiBlocks.BLOCKS.register("cracked_io_basalt_bricks", () -> new Block(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("chiseled_io_basalt", PiBlocks.BLOCKS.register("chiseled_io_basalt", () -> new Block(basalt(MapColor.COLOR_BLACK))));
        BASALTS.put("polished_vesta_basalt", PiBlocks.BLOCKS.register("polished_vesta_basalt", () -> new RotatedPillarBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_vesta_basalt", PiBlocks.BLOCKS.register("smooth_vesta_basalt", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_vesta_basalt_stairs", PiBlocks.BLOCKS.register("smooth_vesta_basalt_stairs", () -> new StairBlock(() -> BASALTS.get("smooth_vesta_basalt").getBlock().defaultBlockState(), basalt(MapColor.DEEPSLATE))));
        BASALTS.put("smooth_vesta_basalt_slab", PiBlocks.BLOCKS.register("smooth_vesta_basalt_slab", () -> new SlabBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("vesta_basalt_bricks", PiBlocks.BLOCKS.register("vesta_basalt_bricks", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("vesta_basalt_brick_stairs", PiBlocks.BLOCKS.register("vesta_basalt_brick_stairs", () -> new StairBlock(() -> BASALTS.get("vesta_basalt_bricks").getBlock().defaultBlockState(), basalt(MapColor.DEEPSLATE))));
        BASALTS.put("vesta_basalt_brick_slab", PiBlocks.BLOCKS.register("vesta_basalt_brick_slab", () -> new SlabBlock(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("vesta_basalt_brick_wall", PiBlocks.BLOCKS.register("vesta_basalt_brick_wall", () -> new WallBlock(basalt(MapColor.DEEPSLATE).forceSolidOn())));
        BASALTS.put("cracked_vesta_basalt_bricks", PiBlocks.BLOCKS.register("cracked_vesta_basalt_bricks", () -> new Block(basalt(MapColor.DEEPSLATE))));
        BASALTS.put("chiseled_vesta_basalt", PiBlocks.BLOCKS.register("chiseled_vesta_basalt", () -> new Block(basalt(MapColor.DEEPSLATE))));
    }
}
