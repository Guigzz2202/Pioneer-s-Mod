package fr.pioneers.building;

import fr.pioneers.core.registry.BlockRegistryObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import fr.pioneers.core.registries.PiBlocks;

/**
 * Blocs de construction des astres (genere par buildings.py) : pour la roche, pave, roche polie, briques (craquelees, sculptees)
 * et roche lisse ; pour le sable, gres, gres taille, lisse, sculpte et briques de gres. Avec escaliers, dalles et murets.
 */
public final class PiBuildingBlocks {

    private PiBuildingBlocks() {
    }

    /** Tous les blocs, dans l'ordre de l'onglet creatif. */
    public static final Map<String, BlockRegistryObject<? extends Block, BlockItem>> ALL = new LinkedHashMap<>();

    private static BlockBehaviour.Properties props(MapColor color, boolean sand) {
        return BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(sand ? 0.8F : 1.5F, sand ? 0.8F : 6.0F).sound(SoundType.STONE);
    }

    /** A appeler avant l'enregistrement des blocs. */
    public static void init() {
    }

    static {
        ALL.put("cobbled_mars_rock", PiBlocks.BLOCKS.register("cobbled_mars_rock", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("cobbled_mars_rock_stairs", PiBlocks.BLOCKS.register("cobbled_mars_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_mars_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("cobbled_mars_rock_slab", PiBlocks.BLOCKS.register("cobbled_mars_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("cobbled_mars_rock_wall", PiBlocks.BLOCKS.register("cobbled_mars_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_RED, false).forceSolidOn())));
        ALL.put("polished_mars_rock", PiBlocks.BLOCKS.register("polished_mars_rock", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("polished_mars_rock_stairs", PiBlocks.BLOCKS.register("polished_mars_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_mars_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("polished_mars_rock_slab", PiBlocks.BLOCKS.register("polished_mars_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("polished_mars_rock_wall", PiBlocks.BLOCKS.register("polished_mars_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_RED, false).forceSolidOn())));
        ALL.put("mars_rock_bricks", PiBlocks.BLOCKS.register("mars_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("mars_rock_brick_stairs", PiBlocks.BLOCKS.register("mars_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("mars_rock_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("mars_rock_brick_slab", PiBlocks.BLOCKS.register("mars_rock_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("mars_rock_brick_wall", PiBlocks.BLOCKS.register("mars_rock_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_RED, false).forceSolidOn())));
        ALL.put("cracked_mars_rock_bricks", PiBlocks.BLOCKS.register("cracked_mars_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("chiseled_mars_rock_bricks", PiBlocks.BLOCKS.register("chiseled_mars_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("smooth_mars_rock", PiBlocks.BLOCKS.register("smooth_mars_rock", () -> new Block(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("smooth_mars_rock_slab", PiBlocks.BLOCKS.register("smooth_mars_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_RED, false))));
        ALL.put("mars_sandstone", PiBlocks.BLOCKS.register("mars_sandstone", () -> new Block(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_stairs", PiBlocks.BLOCKS.register("mars_sandstone_stairs", () -> new StairBlock(() -> ALL.get("mars_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_slab", PiBlocks.BLOCKS.register("mars_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_wall", PiBlocks.BLOCKS.register("mars_sandstone_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_ORANGE, true).forceSolidOn())));
        ALL.put("cut_mars_sandstone", PiBlocks.BLOCKS.register("cut_mars_sandstone", () -> new Block(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("cut_mars_sandstone_slab", PiBlocks.BLOCKS.register("cut_mars_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("smooth_mars_sandstone", PiBlocks.BLOCKS.register("smooth_mars_sandstone", () -> new Block(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("smooth_mars_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_mars_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_mars_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("smooth_mars_sandstone_slab", PiBlocks.BLOCKS.register("smooth_mars_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("chiseled_mars_sandstone", PiBlocks.BLOCKS.register("chiseled_mars_sandstone", () -> new Block(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_bricks", PiBlocks.BLOCKS.register("mars_sandstone_bricks", () -> new Block(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_brick_stairs", PiBlocks.BLOCKS.register("mars_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("mars_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_brick_slab", PiBlocks.BLOCKS.register("mars_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_ORANGE, true))));
        ALL.put("mars_sandstone_brick_wall", PiBlocks.BLOCKS.register("mars_sandstone_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_ORANGE, true).forceSolidOn())));
        ALL.put("cobbled_moon_rock", PiBlocks.BLOCKS.register("cobbled_moon_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("cobbled_moon_rock_stairs", PiBlocks.BLOCKS.register("cobbled_moon_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_moon_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("cobbled_moon_rock_slab", PiBlocks.BLOCKS.register("cobbled_moon_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("cobbled_moon_rock_wall", PiBlocks.BLOCKS.register("cobbled_moon_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("polished_moon_rock", PiBlocks.BLOCKS.register("polished_moon_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("polished_moon_rock_stairs", PiBlocks.BLOCKS.register("polished_moon_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_moon_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("polished_moon_rock_slab", PiBlocks.BLOCKS.register("polished_moon_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("polished_moon_rock_wall", PiBlocks.BLOCKS.register("polished_moon_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("moon_rock_bricks", PiBlocks.BLOCKS.register("moon_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("moon_rock_brick_stairs", PiBlocks.BLOCKS.register("moon_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("moon_rock_bricks").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("moon_rock_brick_slab", PiBlocks.BLOCKS.register("moon_rock_brick_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("moon_rock_brick_wall", PiBlocks.BLOCKS.register("moon_rock_brick_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("cracked_moon_rock_bricks", PiBlocks.BLOCKS.register("cracked_moon_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("chiseled_moon_rock_bricks", PiBlocks.BLOCKS.register("chiseled_moon_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_moon_rock", PiBlocks.BLOCKS.register("smooth_moon_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_moon_rock_slab", PiBlocks.BLOCKS.register("smooth_moon_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("moon_sandstone", PiBlocks.BLOCKS.register("moon_sandstone", () -> new Block(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_stairs", PiBlocks.BLOCKS.register("moon_sandstone_stairs", () -> new StairBlock(() -> ALL.get("moon_sandstone").getBlock().defaultBlockState(), props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_slab", PiBlocks.BLOCKS.register("moon_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_wall", PiBlocks.BLOCKS.register("moon_sandstone_wall", () -> new WallBlock(props(MapColor.COLOR_LIGHT_GRAY, true).forceSolidOn())));
        ALL.put("cut_moon_sandstone", PiBlocks.BLOCKS.register("cut_moon_sandstone", () -> new Block(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("cut_moon_sandstone_slab", PiBlocks.BLOCKS.register("cut_moon_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("smooth_moon_sandstone", PiBlocks.BLOCKS.register("smooth_moon_sandstone", () -> new Block(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("smooth_moon_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_moon_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_moon_sandstone").getBlock().defaultBlockState(), props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("smooth_moon_sandstone_slab", PiBlocks.BLOCKS.register("smooth_moon_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("chiseled_moon_sandstone", PiBlocks.BLOCKS.register("chiseled_moon_sandstone", () -> new Block(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_bricks", PiBlocks.BLOCKS.register("moon_sandstone_bricks", () -> new Block(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_brick_stairs", PiBlocks.BLOCKS.register("moon_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("moon_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_brick_slab", PiBlocks.BLOCKS.register("moon_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.COLOR_LIGHT_GRAY, true))));
        ALL.put("moon_sandstone_brick_wall", PiBlocks.BLOCKS.register("moon_sandstone_brick_wall", () -> new WallBlock(props(MapColor.COLOR_LIGHT_GRAY, true).forceSolidOn())));
        ALL.put("cobbled_mercury_rock", PiBlocks.BLOCKS.register("cobbled_mercury_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("cobbled_mercury_rock_stairs", PiBlocks.BLOCKS.register("cobbled_mercury_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_mercury_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("cobbled_mercury_rock_slab", PiBlocks.BLOCKS.register("cobbled_mercury_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("cobbled_mercury_rock_wall", PiBlocks.BLOCKS.register("cobbled_mercury_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("polished_mercury_rock", PiBlocks.BLOCKS.register("polished_mercury_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("polished_mercury_rock_stairs", PiBlocks.BLOCKS.register("polished_mercury_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_mercury_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("polished_mercury_rock_slab", PiBlocks.BLOCKS.register("polished_mercury_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("polished_mercury_rock_wall", PiBlocks.BLOCKS.register("polished_mercury_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("mercury_rock_bricks", PiBlocks.BLOCKS.register("mercury_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("mercury_rock_brick_stairs", PiBlocks.BLOCKS.register("mercury_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("mercury_rock_bricks").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("mercury_rock_brick_slab", PiBlocks.BLOCKS.register("mercury_rock_brick_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("mercury_rock_brick_wall", PiBlocks.BLOCKS.register("mercury_rock_brick_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("cracked_mercury_rock_bricks", PiBlocks.BLOCKS.register("cracked_mercury_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("chiseled_mercury_rock_bricks", PiBlocks.BLOCKS.register("chiseled_mercury_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_mercury_rock", PiBlocks.BLOCKS.register("smooth_mercury_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_mercury_rock_slab", PiBlocks.BLOCKS.register("smooth_mercury_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("mercury_sandstone", PiBlocks.BLOCKS.register("mercury_sandstone", () -> new Block(props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_stairs", PiBlocks.BLOCKS.register("mercury_sandstone_stairs", () -> new StairBlock(() -> ALL.get("mercury_sandstone").getBlock().defaultBlockState(), props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_slab", PiBlocks.BLOCKS.register("mercury_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_wall", PiBlocks.BLOCKS.register("mercury_sandstone_wall", () -> new WallBlock(props(MapColor.COLOR_GRAY, true).forceSolidOn())));
        ALL.put("cut_mercury_sandstone", PiBlocks.BLOCKS.register("cut_mercury_sandstone", () -> new Block(props(MapColor.COLOR_GRAY, true))));
        ALL.put("cut_mercury_sandstone_slab", PiBlocks.BLOCKS.register("cut_mercury_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_GRAY, true))));
        ALL.put("smooth_mercury_sandstone", PiBlocks.BLOCKS.register("smooth_mercury_sandstone", () -> new Block(props(MapColor.COLOR_GRAY, true))));
        ALL.put("smooth_mercury_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_mercury_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_mercury_sandstone").getBlock().defaultBlockState(), props(MapColor.COLOR_GRAY, true))));
        ALL.put("smooth_mercury_sandstone_slab", PiBlocks.BLOCKS.register("smooth_mercury_sandstone_slab", () -> new SlabBlock(props(MapColor.COLOR_GRAY, true))));
        ALL.put("chiseled_mercury_sandstone", PiBlocks.BLOCKS.register("chiseled_mercury_sandstone", () -> new Block(props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_bricks", PiBlocks.BLOCKS.register("mercury_sandstone_bricks", () -> new Block(props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_brick_stairs", PiBlocks.BLOCKS.register("mercury_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("mercury_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_brick_slab", PiBlocks.BLOCKS.register("mercury_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.COLOR_GRAY, true))));
        ALL.put("mercury_sandstone_brick_wall", PiBlocks.BLOCKS.register("mercury_sandstone_brick_wall", () -> new WallBlock(props(MapColor.COLOR_GRAY, true).forceSolidOn())));
        ALL.put("cobbled_venus_rock", PiBlocks.BLOCKS.register("cobbled_venus_rock", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("cobbled_venus_rock_stairs", PiBlocks.BLOCKS.register("cobbled_venus_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_venus_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("cobbled_venus_rock_slab", PiBlocks.BLOCKS.register("cobbled_venus_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("cobbled_venus_rock_wall", PiBlocks.BLOCKS.register("cobbled_venus_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_GRAY, false).forceSolidOn())));
        ALL.put("polished_venus_rock", PiBlocks.BLOCKS.register("polished_venus_rock", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("polished_venus_rock_stairs", PiBlocks.BLOCKS.register("polished_venus_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_venus_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("polished_venus_rock_slab", PiBlocks.BLOCKS.register("polished_venus_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("polished_venus_rock_wall", PiBlocks.BLOCKS.register("polished_venus_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_GRAY, false).forceSolidOn())));
        ALL.put("venus_rock_bricks", PiBlocks.BLOCKS.register("venus_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("venus_rock_brick_stairs", PiBlocks.BLOCKS.register("venus_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("venus_rock_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("venus_rock_brick_slab", PiBlocks.BLOCKS.register("venus_rock_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("venus_rock_brick_wall", PiBlocks.BLOCKS.register("venus_rock_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_GRAY, false).forceSolidOn())));
        ALL.put("cracked_venus_rock_bricks", PiBlocks.BLOCKS.register("cracked_venus_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("chiseled_venus_rock_bricks", PiBlocks.BLOCKS.register("chiseled_venus_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("smooth_venus_rock", PiBlocks.BLOCKS.register("smooth_venus_rock", () -> new Block(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("smooth_venus_rock_slab", PiBlocks.BLOCKS.register("smooth_venus_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_GRAY, false))));
        ALL.put("venus_sandstone", PiBlocks.BLOCKS.register("venus_sandstone", () -> new Block(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_stairs", PiBlocks.BLOCKS.register("venus_sandstone_stairs", () -> new StairBlock(() -> ALL.get("venus_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_slab", PiBlocks.BLOCKS.register("venus_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_wall", PiBlocks.BLOCKS.register("venus_sandstone_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_YELLOW, true).forceSolidOn())));
        ALL.put("cut_venus_sandstone", PiBlocks.BLOCKS.register("cut_venus_sandstone", () -> new Block(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("cut_venus_sandstone_slab", PiBlocks.BLOCKS.register("cut_venus_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("smooth_venus_sandstone", PiBlocks.BLOCKS.register("smooth_venus_sandstone", () -> new Block(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("smooth_venus_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_venus_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_venus_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("smooth_venus_sandstone_slab", PiBlocks.BLOCKS.register("smooth_venus_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("chiseled_venus_sandstone", PiBlocks.BLOCKS.register("chiseled_venus_sandstone", () -> new Block(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_bricks", PiBlocks.BLOCKS.register("venus_sandstone_bricks", () -> new Block(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_brick_stairs", PiBlocks.BLOCKS.register("venus_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("venus_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_brick_slab", PiBlocks.BLOCKS.register("venus_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_YELLOW, true))));
        ALL.put("venus_sandstone_brick_wall", PiBlocks.BLOCKS.register("venus_sandstone_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_YELLOW, true).forceSolidOn())));
        ALL.put("cobbled_phobos_rock", PiBlocks.BLOCKS.register("cobbled_phobos_rock", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("cobbled_phobos_rock_stairs", PiBlocks.BLOCKS.register("cobbled_phobos_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_phobos_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("cobbled_phobos_rock_slab", PiBlocks.BLOCKS.register("cobbled_phobos_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("cobbled_phobos_rock_wall", PiBlocks.BLOCKS.register("cobbled_phobos_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_BROWN, false).forceSolidOn())));
        ALL.put("polished_phobos_rock", PiBlocks.BLOCKS.register("polished_phobos_rock", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("polished_phobos_rock_stairs", PiBlocks.BLOCKS.register("polished_phobos_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_phobos_rock").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("polished_phobos_rock_slab", PiBlocks.BLOCKS.register("polished_phobos_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("polished_phobos_rock_wall", PiBlocks.BLOCKS.register("polished_phobos_rock_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_BROWN, false).forceSolidOn())));
        ALL.put("phobos_rock_bricks", PiBlocks.BLOCKS.register("phobos_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("phobos_rock_brick_stairs", PiBlocks.BLOCKS.register("phobos_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("phobos_rock_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("phobos_rock_brick_slab", PiBlocks.BLOCKS.register("phobos_rock_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("phobos_rock_brick_wall", PiBlocks.BLOCKS.register("phobos_rock_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_BROWN, false).forceSolidOn())));
        ALL.put("cracked_phobos_rock_bricks", PiBlocks.BLOCKS.register("cracked_phobos_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("chiseled_phobos_rock_bricks", PiBlocks.BLOCKS.register("chiseled_phobos_rock_bricks", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("smooth_phobos_rock", PiBlocks.BLOCKS.register("smooth_phobos_rock", () -> new Block(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("smooth_phobos_rock_slab", PiBlocks.BLOCKS.register("smooth_phobos_rock_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, false))));
        ALL.put("phobos_sandstone", PiBlocks.BLOCKS.register("phobos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_stairs", PiBlocks.BLOCKS.register("phobos_sandstone_stairs", () -> new StairBlock(() -> ALL.get("phobos_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_slab", PiBlocks.BLOCKS.register("phobos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_wall", PiBlocks.BLOCKS.register("phobos_sandstone_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_BROWN, true).forceSolidOn())));
        ALL.put("cut_phobos_sandstone", PiBlocks.BLOCKS.register("cut_phobos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("cut_phobos_sandstone_slab", PiBlocks.BLOCKS.register("cut_phobos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("smooth_phobos_sandstone", PiBlocks.BLOCKS.register("smooth_phobos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("smooth_phobos_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_phobos_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_phobos_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("smooth_phobos_sandstone_slab", PiBlocks.BLOCKS.register("smooth_phobos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("chiseled_phobos_sandstone", PiBlocks.BLOCKS.register("chiseled_phobos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_bricks", PiBlocks.BLOCKS.register("phobos_sandstone_bricks", () -> new Block(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_brick_stairs", PiBlocks.BLOCKS.register("phobos_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("phobos_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_brick_slab", PiBlocks.BLOCKS.register("phobos_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_BROWN, true))));
        ALL.put("phobos_sandstone_brick_wall", PiBlocks.BLOCKS.register("phobos_sandstone_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_BROWN, true).forceSolidOn())));
        ALL.put("cobbled_deimos_rock", PiBlocks.BLOCKS.register("cobbled_deimos_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("cobbled_deimos_rock_stairs", PiBlocks.BLOCKS.register("cobbled_deimos_rock_stairs", () -> new StairBlock(() -> ALL.get("cobbled_deimos_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("cobbled_deimos_rock_slab", PiBlocks.BLOCKS.register("cobbled_deimos_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("cobbled_deimos_rock_wall", PiBlocks.BLOCKS.register("cobbled_deimos_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("polished_deimos_rock", PiBlocks.BLOCKS.register("polished_deimos_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("polished_deimos_rock_stairs", PiBlocks.BLOCKS.register("polished_deimos_rock_stairs", () -> new StairBlock(() -> ALL.get("polished_deimos_rock").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("polished_deimos_rock_slab", PiBlocks.BLOCKS.register("polished_deimos_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("polished_deimos_rock_wall", PiBlocks.BLOCKS.register("polished_deimos_rock_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("deimos_rock_bricks", PiBlocks.BLOCKS.register("deimos_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("deimos_rock_brick_stairs", PiBlocks.BLOCKS.register("deimos_rock_brick_stairs", () -> new StairBlock(() -> ALL.get("deimos_rock_bricks").getBlock().defaultBlockState(), props(MapColor.STONE, false))));
        ALL.put("deimos_rock_brick_slab", PiBlocks.BLOCKS.register("deimos_rock_brick_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("deimos_rock_brick_wall", PiBlocks.BLOCKS.register("deimos_rock_brick_wall", () -> new WallBlock(props(MapColor.STONE, false).forceSolidOn())));
        ALL.put("cracked_deimos_rock_bricks", PiBlocks.BLOCKS.register("cracked_deimos_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("chiseled_deimos_rock_bricks", PiBlocks.BLOCKS.register("chiseled_deimos_rock_bricks", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_deimos_rock", PiBlocks.BLOCKS.register("smooth_deimos_rock", () -> new Block(props(MapColor.STONE, false))));
        ALL.put("smooth_deimos_rock_slab", PiBlocks.BLOCKS.register("smooth_deimos_rock_slab", () -> new SlabBlock(props(MapColor.STONE, false))));
        ALL.put("deimos_sandstone", PiBlocks.BLOCKS.register("deimos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_stairs", PiBlocks.BLOCKS.register("deimos_sandstone_stairs", () -> new StairBlock(() -> ALL.get("deimos_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_slab", PiBlocks.BLOCKS.register("deimos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_wall", PiBlocks.BLOCKS.register("deimos_sandstone_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_WHITE, true).forceSolidOn())));
        ALL.put("cut_deimos_sandstone", PiBlocks.BLOCKS.register("cut_deimos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("cut_deimos_sandstone_slab", PiBlocks.BLOCKS.register("cut_deimos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("smooth_deimos_sandstone", PiBlocks.BLOCKS.register("smooth_deimos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("smooth_deimos_sandstone_stairs", PiBlocks.BLOCKS.register("smooth_deimos_sandstone_stairs", () -> new StairBlock(() -> ALL.get("smooth_deimos_sandstone").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("smooth_deimos_sandstone_slab", PiBlocks.BLOCKS.register("smooth_deimos_sandstone_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("chiseled_deimos_sandstone", PiBlocks.BLOCKS.register("chiseled_deimos_sandstone", () -> new Block(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_bricks", PiBlocks.BLOCKS.register("deimos_sandstone_bricks", () -> new Block(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_brick_stairs", PiBlocks.BLOCKS.register("deimos_sandstone_brick_stairs", () -> new StairBlock(() -> ALL.get("deimos_sandstone_bricks").getBlock().defaultBlockState(), props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_brick_slab", PiBlocks.BLOCKS.register("deimos_sandstone_brick_slab", () -> new SlabBlock(props(MapColor.TERRACOTTA_WHITE, true))));
        ALL.put("deimos_sandstone_brick_wall", PiBlocks.BLOCKS.register("deimos_sandstone_brick_wall", () -> new WallBlock(props(MapColor.TERRACOTTA_WHITE, true).forceSolidOn())));
    }
}
