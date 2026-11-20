package fr.pioneers.space.environment;

import fr.pioneers.core.registries.PiBlocks;
import fr.pioneers.core.registry.BlockRegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * GENERE par T:core/p107_lot11.py (ne pas modifier a la main) : blocs naturels des planetes des trois systemes lointains (1.0.7, lot 11 ;
 * donnees T:au_dela/far_systems.py), meme fabrique que le bloc de leur gabarit ({@link PiExoBlocks}).
 */
public final class GeneratedFarBlocks {

    private GeneratedFarBlocks() {
    }

    /** Blocs naturels, dans l'ordre des planetes. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> NATURAL = new LinkedHashMap<>();

    /** A appeler avant l'enregistrement des blocs. */
    public static void init() {
    }

    static {
        NATURAL.put("draugr_rock", PiBlocks.BLOCKS.register("draugr_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.BASALT))));
        NATURAL.put("draugr_glass_crust", PiBlocks.BLOCKS.register("draugr_glass_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(2.4F, 6.0F).sound(SoundType.DEEPSLATE))));
        NATURAL.put("draugr_iron_dust", PiBlocks.BLOCKS.register("draugr_iron_dust", () -> new SandBlock(0x785343, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("poltergeist_rock", PiBlocks.BLOCKS.register("poltergeist_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("poltergeist_ion_soil", PiBlocks.BLOCKS.register("poltergeist_ion_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PINK).strength(0.6F).sound(SoundType.ROOTED_DIRT))));
        NATURAL.put("poltergeist_storm_ice", PiBlocks.BLOCKS.register("poltergeist_storm_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("poltergeist_ash", PiBlocks.BLOCKS.register("poltergeist_ash", () -> new SandBlock(0x302C40, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("poltergeist_frost", PiBlocks.BLOCKS.register("poltergeist_frost", () -> new SandBlock(0xA090C0, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("phobetor_rock", PiBlocks.BLOCKS.register("phobetor_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("phobetor_pulse_ice", PiBlocks.BLOCKS.register("phobetor_pulse_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("phobetor_snow", PiBlocks.BLOCKS.register("phobetor_snow", () -> new SandBlock(0xB0BCCC, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("phobetor_streak_ice", PiBlocks.BLOCKS.register("phobetor_streak_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("van_maanen_b_rock", PiBlocks.BLOCKS.register("van_maanen_b_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("van_maanen_b_graphite_sand", PiBlocks.BLOCKS.register("van_maanen_b_graphite_sand", () -> new SandBlock(0x303138, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("van_maanen_b_diamond_crystal", PiBlocks.BLOCKS.register("van_maanen_b_diamond_crystal", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(1.4F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 11))));
        NATURAL.put("van_maanen_c_rock", PiBlocks.BLOCKS.register("van_maanen_c_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.BASALT))));
        NATURAL.put("van_maanen_c_iron_crust", PiBlocks.BLOCKS.register("van_maanen_c_iron_crust", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(2.4F, 6.0F).sound(SoundType.DEEPSLATE))));
        NATURAL.put("van_maanen_c_filings", PiBlocks.BLOCKS.register("van_maanen_c_filings", () -> new SandBlock(0x706358, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("van_maanen_d_rock", PiBlocks.BLOCKS.register("van_maanen_d_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("van_maanen_d_nitrogen_ice", PiBlocks.BLOCKS.register("van_maanen_d_nitrogen_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("van_maanen_d_nitrogen_snow", PiBlocks.BLOCKS.register("van_maanen_d_nitrogen_snow", () -> new SandBlock(0xBCC3C8, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F).sound(SoundType.SNOW))));
        NATURAL.put("van_maanen_d_fallen_ice", PiBlocks.BLOCKS.register("van_maanen_d_fallen_ice", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(1.2F, 6.0F).sound(SoundType.GLASS).friction(0.98F))));
        NATURAL.put("gaia_bh1_b_rock", PiBlocks.BLOCKS.register("gaia_bh1_b_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("gaia_bh1_b_soil", PiBlocks.BLOCKS.register("gaia_bh1_b_soil", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.ROOTED_DIRT))));
        NATURAL.put("gaia_bh1_b_glowmoss", PiBlocks.BLOCKS.register("gaia_bh1_b_glowmoss", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).sound(SoundType.MOSS).lightLevel(state -> 5))));
        NATURAL.put("gaia_bh1_b_lens_stem", PiBlocks.BLOCKS.register("gaia_bh1_b_lens_stem", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.0F).sound(SoundType.WOOD))));
        NATURAL.put("gaia_bh1_b_lens_cap", PiBlocks.BLOCKS.register("gaia_bh1_b_lens_cap", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).sound(SoundType.WART_BLOCK).lightLevel(state -> 13))));
        NATURAL.put("gaia_bh1_c_rock", PiBlocks.BLOCKS.register("gaia_bh1_c_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("gaia_bh1_c_tidal_slag", PiBlocks.BLOCKS.register("gaia_bh1_c_tidal_slag", () -> new SandBlock(0x382320, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("gaia_bh1_c_horizon_crystal", PiBlocks.BLOCKS.register("gaia_bh1_c_horizon_crystal", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(1.4F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 11))));
        NATURAL.put("gaia_bh1_d_rock", PiBlocks.BLOCKS.register("gaia_bh1_d_rock", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE))));
        NATURAL.put("gaia_bh1_d_sand", PiBlocks.BLOCKS.register("gaia_bh1_d_sand", () -> new SandBlock(0x888898, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F).sound(SoundType.SAND))));
        NATURAL.put("gaia_bh1_d_shale", PiBlocks.BLOCKS.register("gaia_bh1_d_shale", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.0F, 6.0F).sound(SoundType.MUD_BRICKS))));
    }
}
